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
- `GestorFinanceiro-Web`: Nginx interno na rede `proxy`; serve frontend e proxy `/api`.
- `GestorFinanceiro-Backup`: `pg_dump -Fc` diario em `./backups/postgres`.

Redes externas esperadas:

- `proxy`: rede do Nginx Proxy Manager.
- `dbnexos-gestor-financeiro_default`: rede do PostgreSQL existente.

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
curl -i https://financas.nexostech.com.br/api/auth/validate-token
```

`/api/auth/validate-token` deve responder `401` sem token. Isto confirma proxy ate API.

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
