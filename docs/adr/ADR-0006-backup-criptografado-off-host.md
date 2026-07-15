# ADR-0006 — Backup criptografado off-host com restore drill

- **Status:** Accepted (2026-07-15)
- **Contexto:** Estrategias divergem: `docker-compose.production.yml` roda `pg_dump -Fc` sem
  criptografia; `docker-compose.vps.yml` roda `pg_dump | gzip` + GPG simetrico; dev nao tem backup
  (P1-7). Backup no proprio host nao protege contra perda do host. Uploads (ADR-0005) nao sao
  cobertos. Ja existem `scripts/backup-db.sh`, `scripts/restore-db.sh` e
  `scripts/restore-drill-db.sh` — a evolucao acontece neles, sem duplicar ferramenta.
- **Decisao:** Politica canonica unica:
  - formato `pg_dump -Fc` (restore seletivo/paralelo via `pg_restore`; compressao nativa);
  - bundle = dump + tar do volume de uploads + manifesto com checksums;
  - criptografia GPG **assimetrica**: servidor guarda somente a chave publica; a privada fica
    off-host sob custodia do responsavel do produto;
  - envio obrigatorio via `rclone` a um remote externo; deploy falha fechado se recipient GPG ou
    remote nao configurados;
  - execucao one-shot em janela curta de manutencao: pausar escrita da API, gerar DB + uploads,
    enviar, validar checksum e religar API via `trap`;
  - retencao: 7 diarios + 4 semanais, local e remota; staging local removido apos upload
    confirmado;
  - production e VPS usam a mesma imagem/script, acionados por timer do host;
  - dev permanece sem backup (decisao explicita).
- **Consequencias:** Restore drill obrigatorio e documentado com evidencia: PostgreSQL limpo,
  restauracao de volume, validacao de checksums, migrations, contagens e download real de anexo.
  Nenhum release declara P1-7 fechado sem drill registrado.
