# Runbook — Backup e Restore (ADR-0006)

Política canônica: bundle `gf_backup_<timestamp>[_weekly].tar.gpg` contendo
`db.dump` (`pg_dump -Fc`), `uploads.tar.gz` (anexos) e `manifest.txt` (sha256),
criptografado com **GPG assimétrico** e enviado **off-host** via rclone.
Ferramentas: `scripts/run-backup.sh` (coordenador no host), `scripts/backup-db.sh`,
`scripts/restore-db.sh`, `scripts/restore-drill-db.sh`
(as mesmas em dev, production e VPS — nada de scripts paralelos).

## Chaves (custódia)

- O **servidor guarda somente a chave pública** (`deploy/backup/backup-public.asc`, fora do git).
- A **chave privada de restore fica off-host**, sob custódia do responsável do produto
  (gerenciador de senhas + cópia offline). Sem ela não há restore — teste o drill ao rotacionar.
- Gerar chave primária: `gpg --quick-gen-key "backup@nexosfinancas" ed25519 cert,sign never`;
- Adicionar subchave de criptografia: `gpg --quick-add-key <FINGERPRINT> cv25519 encr never`;
  exportar pública: `gpg --export --armor backup@nexosfinancas > backup-public.asc`.

## Configuração no host (production e VPS)

1. `deploy/backup/backup-public.asc` — chave pública.
2. `deploy/backup/rclone/rclone.conf` — remote configurado (`rclone config`), ex. B2/S3/Drive.
3. `.env`: `BACKUP_GPG_RECIPIENT=backup@nexosfinancas`, `RCLONE_REMOTE=<remote>:gf-backups/<ambiente>`.
   **Fail-closed:** sem recipient ou remote o backup aborta com erro — de propósito.

## Execução (timer do host, one-shot)

Janela curta de manutenção: o coordenador host-side valida compose/chave/remote, para a API,
executa o container one-shot e religa a API via `trap`, aguardando o healthcheck. O container
gera dump + tar de anexos, criptografa, envia, valida o conteúdo remoto por checksum com
`rclone check --download` e remove o staging.

Cron do host (production; na VPS troque o arquivo compose e o nome do container da API):

```cron
30 3 * * * cd /opt/gestor-financeiro && BACKUP_COMPOSE_FILE=docker-compose.production.yml \
  ./scripts/run-backup.sh \
  >> /var/log/gf-backup.log 2>&1
```

O coordenador roda no host; o container de backup não recebe Docker socket nem controla outros
containers. Falha no backup ou na recuperação saudável da API faz a execução retornar erro.

- Retenção: 7 diários + 4 semanais (domingo gera `_weekly`), local e remota.
- Dev (`docker-compose.yml`) **não tem backup** — decisão explícita do ADR-0006.

## Restore

```bash
# produção (exige chave privada importada no keyring local)
./scripts/restore-db.sh backups/postgres/gf_backup_20260715_033000.tar.gpg "$DATABASE_URL"
RESTORE_UPLOADS_DIR=/caminho/uploads ./scripts/restore-db.sh <bundle> "$DATABASE_URL"
```

Valida checksums do manifesto antes do `pg_restore --clean --if-exists`. Bundles legados
(`*.sql.gz[.gpg]`) continuam aceitos.

## Restore drill (obrigatório antes de fechar release)

```bash
./scripts/restore-drill-db.sh backups/postgres/gf_backup_<ts>.tar.gpg
```

Sem URL o drill sobe um PostgreSQL 17 efêmero via docker, restaura, valida checksums,
`flyway_schema_history`, contagens (`usuarios`, `transacoes`, `anexos`) e confere que o número
de arquivos de anexo extraídos cobre os registros do banco e valida cada caminho persistido.
Depois do drill, suba a API
apontando para o banco restaurado e faça o download real de um anexo (evidência no
PROBLEM_LEDGER). Nenhum release declara PROB-0081 fechado sem drill registrado.
