#!/usr/bin/env sh
# Emissao inicial do certificado Let's Encrypt para a stack VPS.
# Resolve a ordem: cert dummy -> nginx :80/:443 -> cert real via webroot -> reload.
# Rode uma vez depois que DNS A do DOMAIN apontar para a VPS.
set -eu

COMPOSE_ENV_FILE="${COMPOSE_ENV_FILE:-deploy/vps/.env.vps}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.vps.yml}"
DATA_PATH="./deploy/vps/certbot"
STAGING="${STAGING:-0}"   # STAGING=1 usa ambiente de teste do Let's Encrypt.

compose() {
  docker compose --env-file "$COMPOSE_ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

env_get() {
  grep -E "^$1=" "$COMPOSE_ENV_FILE" | tail -n1 | cut -d= -f2-
}

DOMAIN="$(env_get DOMAIN)"
EMAIL="$(env_get LETSENCRYPT_EMAIL)"

[ -n "$DOMAIN" ] || { echo "ERRO: DOMAIN vazio em $COMPOSE_ENV_FILE"; exit 1; }
[ -n "$EMAIL" ]  || { echo "ERRO: LETSENCRYPT_EMAIL vazio em $COMPOSE_ENV_FILE"; exit 1; }

echo "Dominio: $DOMAIN"
echo "E-mail:  $EMAIL"

if [ ! -e "$DATA_PATH/conf/options-ssl-nginx.conf" ] || [ ! -e "$DATA_PATH/conf/ssl-dhparams.pem" ]; then
  echo "Baixando parametros TLS recomendados..."
  mkdir -p "$DATA_PATH/conf"
  curl -fsSL https://raw.githubusercontent.com/certbot/certbot/main/certbot-nginx/src/certbot_nginx/_internal/tls_configs/options-ssl-nginx.conf \
    > "$DATA_PATH/conf/options-ssl-nginx.conf"
  curl -fsSL https://raw.githubusercontent.com/certbot/certbot/main/certbot/certbot/ssl-dhparams.pem \
    > "$DATA_PATH/conf/ssl-dhparams.pem"
fi

echo "Criando certificado dummy para $DOMAIN..."
mkdir -p "$DATA_PATH/conf/live/$DOMAIN"
compose run --rm --entrypoint sh certbot -c "\
  openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout '/etc/letsencrypt/live/$DOMAIN/privkey.pem' \
    -out    '/etc/letsencrypt/live/$DOMAIN/fullchain.pem' \
    -subj '/CN=localhost'"

echo "Subindo nginx..."
compose up -d --build nginx

echo "Removendo certificado dummy..."
compose run --rm --entrypoint sh certbot -c "\
  rm -Rf /etc/letsencrypt/live/$DOMAIN && \
  rm -Rf /etc/letsencrypt/archive/$DOMAIN && \
  rm -Rf /etc/letsencrypt/renewal/$DOMAIN.conf"

echo "Solicitando certificado Let's Encrypt..."
STAGING_ARG=""
[ "$STAGING" != "0" ] && STAGING_ARG="--staging"
compose run --rm --entrypoint certbot certbot certonly \
  --webroot -w /var/www/certbot \
  $STAGING_ARG \
  --email "$EMAIL" \
  -d "$DOMAIN" \
  --rsa-key-size 4096 \
  --agree-tos \
  --no-eff-email \
  --force-renewal

echo "Recarregando nginx..."
compose exec nginx nginx -s reload
compose up -d certbot

echo "Pronto. HTTPS ativo em https://$DOMAIN"
