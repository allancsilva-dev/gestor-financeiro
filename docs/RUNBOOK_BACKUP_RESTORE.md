# Runbook — Backup e Restore (ADR-0006)

Política canônica: bundle `gf_backup_<timestamp>[_weekly].tar.gpg` contendo
`db.dump` (`pg_dump -Fc`), `uploads.tar.gz` (anexos) e `manifest.txt` (sha256),
criptografado com **GPG assimétrico** e enviado **off-host** via rclone.
Ferramentas: `scripts/backup-db.sh`, `scripts/restore-db.sh`, `scripts/restore-drill-db.sh`
(as mesmas em dev, production e VPS — nada de scripts paralelos).

## Chaves (custódia)

- O **servidor guarda somente a chave pública** (`deploy/backup/backup-public.asc`, fora do git).
- A **chave privada de restore fica off-host**, sob custódia do responsável do produto
  (gerenciador de senhas + cópia offline). Sem ela não há restore — teste o drill ao rotacionar.
- Gerar par: `gpg --quick-gen-key "backup@nexosfinancas" ed25519 cert,sign+encr never`;
  exportar pública: `gpg --export --armor backup@nexosfinancas > backup-public.asc`.

## Configuração no host (production e VPS)

1. `deploy/backup/backup-public.asc` — chave pública.
2. `deploy/backup/rclone/rclone.conf` — remote configurado (`rclone config`), ex. B2/S3/Drive.
3. `.env`: `BACKUP_GPG_RECIPIENT=backup@nexosfinancas`, `RCLONE_REMOTE=<remote>:gf-backups/<ambiente>`.
   **Fail-closed:** sem recipient ou remote o backup aborta com erro — de propósito.

## Execução (timer do host, one-shot)

Janela curta de manutenção: o script para a API, gera dump + tar de anexos, religa a API
(via `trap`, mesmo em falha), criptografa, envia, valida tamanho remoto e remove o staging.

Cron do host (production; na VPS troque o arquivo compose e o nome do container da API):

```cron
30 3 * * * cd /opt/gestor-financeiro && API_CONTAINER=GestorFinanceiro-API \
  docker compose -f docker-compose.production.yml --profile backup run --rm postgres-backup \
  >> /var/log/gf-backup.log 2>&1
```

Observação: `API_CONTAINER` só surte efeito se o docker CLI estiver acessível ao processo
(timer no host com wrapper, ou socket montado — decisão consciente de segurança). Sem ele o
backup roda sem janela: o `pg_dump -Fc` continua consistente (snapshot transacional), apenas
anexos enviados durante o dump podem ficar de fora até o próximo ciclo.

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

Sem URL o drill sobe um PostgreSQL efêmero via docker, restaura, valida checksums,
`flyway_schema_history`, contagens (`usuarios`, `transacoes`, `anexos`) e confere que o número
de arquivos de anexo extraídos cobre os registros do banco. Depois do drill, suba a API
apontando para o banco restaurado e faça o download real de um anexo (evidência no
PROBLEM_LEDGER). Nenhum release declara PROB-0081 fechado sem drill registrado.
