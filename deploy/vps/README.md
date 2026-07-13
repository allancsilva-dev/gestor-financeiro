# Deploy VPS Hostinger

Arquivos:

- `docker-compose.vps.yml`: stack Docker Compose para teste em VPS.
- `deploy/vps/Dockerfile.nginx`: build do frontend Vite e imagem Nginx.
- `deploy/vps/nginx.conf.template`: HTTPS, SPA fallback e proxy `/api` para Spring Boot.
- `deploy/vps/.env.vps.example`: modelo de variaveis e secrets.
- `deploy/vps/init-letsencrypt.sh`: emissao inicial do certificado TLS.

## Containers

- `gf-postgres`: PostgreSQL 17, volume `gf_postgres_data`.
- `gf-api`: Spring Boot, porta interna `8080`; Flyway roda no startup.
- `gf-nginx`: entrada publica `80`/`443`, serve frontend e proxy para API.
- `gf-certbot`: renovacao automatica do certificado Let's Encrypt.
- `gf-postgres-backup`: `pg_dump -Fc` diario em `./backups/postgres`.

## DNS

Aponte registro `A` de `DOMAIN` para o IP publico da VPS antes de emitir certificado.
Sem DNS resolvendo, desafio ACME falha.

## Primeira subida

```sh
cp deploy/vps/.env.vps.example deploy/vps/.env.vps
chmod 600 deploy/vps/.env.vps
```

Edite `deploy/vps/.env.vps`:

- `DOMAIN` e `LETSENCRYPT_EMAIL`.
- `POSTGRES_PASSWORD` e `JWT_SECRET` com valores fortes.
- `CORS_ALLOWED_ORIGINS=https://SEU_DOMINIO`.
- `SMTP_PASSWORD` com a senha da caixa `contato@nexostech.com.br`; mantenha o arquivo fora do Git.

No Microsoft 365 Admin Center, habilite **Authenticated SMTP** para a caixa
`contato@nexostech.com.br`. A API usa `smtp.office365.com:587` com STARTTLS.

Suba banco e API para validar migrations:

```sh
docker compose --env-file deploy/vps/.env.vps -f docker-compose.vps.yml up -d --build postgres api
docker compose --env-file deploy/vps/.env.vps -f docker-compose.vps.yml ps
```

Emita HTTPS e suba Nginx:

```sh
COMPOSE_ENV_FILE=deploy/vps/.env.vps sh deploy/vps/init-letsencrypt.sh
```

Teste sem gastar cota do Let's Encrypt:

```sh
STAGING=1 COMPOSE_ENV_FILE=deploy/vps/.env.vps sh deploy/vps/init-letsencrypt.sh
```

Depois rode sem `STAGING` para certificado valido.

## Verificacao

```sh
docker compose --env-file deploy/vps/.env.vps -f docker-compose.vps.yml ps
curl -i https://SEU_DOMINIO/actuator/health
curl -i https://SEU_DOMINIO/api/auth/validate-token
```

`/api/auth/validate-token` deve responder `401` sem token. Isto confirma proxy ate API.

## Backup e restore

Backups ficam em `./backups/postgres/*.dump`.

Restore em banco vazio:

```sh
docker compose --env-file deploy/vps/.env.vps -f docker-compose.vps.yml exec -T postgres \
  sh -lc 'pg_restore --clean --if-exists -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < backups/postgres/ARQUIVO.dump
```

## Firewall VPS

Abrir:

- `22/tcp`: SSH.
- `80/tcp`: HTTP/Nginx e desafio ACME.
- `443/tcp`: HTTPS/Nginx.

Nao expor `5432` nem `8080` publicamente.
