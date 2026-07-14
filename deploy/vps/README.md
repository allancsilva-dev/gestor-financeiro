# Deploy VPS Hostinger

Arquivos:

- `docker-compose.production.yml`: stack de producao usando PostgreSQL existente e Nginx Proxy Manager.
- `docker-compose.vps.yml`: stack completa com PostgreSQL/Nginx/Certbot, util para ambiente isolado/homolog.
- `deploy/vps/Dockerfile.nginx`: build do frontend Vite e imagem Nginx.
- `deploy/vps/Dockerfile.nginx-npm`: build do frontend Vite para uso atras do Nginx Proxy Manager.
- `deploy/vps/nginx.conf.template`: HTTPS, SPA fallback e proxy `/api` para Spring Boot.
- `deploy/vps/nginx.npm.conf`: SPA fallback e proxy `/api` para a API na rede Docker `proxy`.
- `deploy/vps/.env.vps.example`: modelo de variaveis e secrets.
- `deploy/vps/.env.vps.production.example`: modelo de producao.
- `deploy/vps/init-letsencrypt.sh`: emissao inicial do certificado TLS.

## Producao com Nginx Proxy Manager

Containers:

- `GestorFinanceiro-API`: Spring Boot, porta interna `8080`; Flyway roda no startup.
  Fica nas redes `internal` (fala com o Web) e do banco. **Fora da rede `proxy`**: o NPM
  nao alcanca a API diretamente, so o Web.
- `GestorFinanceiro-Web`: Nginx interno nas redes `proxy` (NPM) e `internal` (API);
  serve frontend e faz proxy `/api`.
- `GestorFinanceiro-Backup`: `pg_dump -Fc` diario em `./backups/postgres`.

Redes:

- `proxy` (externa): rede do Nginx Proxy Manager. So o `Web` participa.
- `internal` (criada pelo compose): rede privada `Web` <-> `API`.
- `dbnexos-gestor-financeiro_default` (externa): rede do PostgreSQL existente.

## Rate limit e IP real do cliente (X-Forwarded-For)

O rate limit de autenticacao (login, forgot/reset-password, register) chaveia por IP real
do cliente. A API usa `server.forward-headers-strategy=native` (Tomcat `RemoteIpValve`), que
resolve o IP percorrendo o `X-Forwarded-For` da direita para a esquerda e descartando os
proxies confiaveis (loopback + faixas privadas Docker). O primeiro IP publico e o cliente.

**Premissa de seguranca (obrigatoria):** o proxy frontal (NPM) DEVE *anexar* seu proprio
`$remote_addr` ao `X-Forwarded-For`, nunca apenas repassar o header recebido do cliente. E o
padrao do NPM. Se for reconfigurado para confiar no XFF do cliente, um atacante volta a
forjar o IP e anular o rate limit (brute force / bomba de reset). O `nginx.npm.conf` mantem
o XFF em modo append; o `nginx.conf.template` (standalone, 1 hop) *sobrescreve* com o peer real.

## Primeira Subida Em Producao

```sh
cp deploy/vps/.env.vps.production.example deploy/vps/.env.vps.production
chmod 600 deploy/vps/.env.vps.production
```

Edite `deploy/vps/.env.vps.production`:

- `DB_PASSWORD` com a senha real do PostgreSQL existente.
- `JWT_SECRET` com valor forte.
- `CORS_ALLOWED_ORIGINS=https://financas.nexostech.com.br`.
- `SMTP_USERNAME=allan@nexostech.com.br` para autenticar na caixa principal.
- `SMTP_PASSWORD` com a senha da caixa `allan@nexostech.com.br`; mantenha o arquivo fora do Git.
- `MAIL_FROM=contato@nexostech.com.br` para enviar usando o alias.

No Microsoft 365 Admin Center, habilite **Authenticated SMTP** para a caixa
`allan@nexostech.com.br`. A API usa `smtp.office365.com:587` com STARTTLS.

Suba aplicacao:

```sh
docker compose --env-file deploy/vps/.env.vps.production -f docker-compose.production.yml up -d --build
docker compose --env-file deploy/vps/.env.vps.production -f docker-compose.production.yml ps
```

No Nginx Proxy Manager, crie/edite o Proxy Host:

- Domain Names: `financas.nexostech.com.br`
- Scheme: `http`
- Forward Hostname / IP: `GestorFinanceiro-Web`
- Forward Port: `80`
- SSL: emitir/usar certificado Let's Encrypt, Force SSL ativo.

## Verificacao

```sh
curl -i https://financas.nexostech.com.br/actuator/health
curl -i -X POST https://financas.nexostech.com.br/api/auth/validate-token \
  -H 'Content-Type: application/json' -d '{"token":"x"}'
```

O `POST /api/auth/validate-token` com token invalido responde erro de negocio (nao `2xx`),
confirmando proxy ate a API. O antigo `GET` foi removido e responde `405`.

## Backup e restore

Backups ficam em `./backups/postgres/*.dump`.

Restore em banco vazio:

```sh
docker compose --env-file deploy/vps/.env.vps.production -f docker-compose.production.yml exec -T postgres-backup \
  sh -lc 'pg_restore --clean --if-exists -U "$PGUSER" -d "$PGDATABASE"' \
  < backups/postgres/ARQUIVO.dump
```

## Firewall VPS

Abrir:

- `22/tcp`: SSH.
- `80/tcp`: HTTP/Nginx e desafio ACME.
- `443/tcp`: HTTPS/Nginx.

Nao expor `5432` nem `8080` publicamente.
