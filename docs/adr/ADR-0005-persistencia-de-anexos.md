# ADR-0005 — Persistencia de anexos: volume agora, object storage depois

- **Status:** Accepted (2026-07-15)
- **Contexto:** Anexos (comprovantes) sao gravados no filesystem local do container da API
  (`AnexoService`, `app.upload.dir`), e nenhum compose monta volume de uploads (P1-6). Recriar o
  container perde comprovantes; backup do PostgreSQL nao os inclui.
- **Decisao:** Curto prazo: named volume `backend_uploads:/app/uploads` nos composes de production
  e VPS, com `APP_UPLOAD_DIR=/app/uploads`; o compose dev possui apenas PostgreSQL e o ambiente
  local segue com diretorio host configuravel. Uploads entram no escopo do backup (ADR-0006).
  Nao extrair interface de storage agora. Gatilho para migrar a object storage S3-compativel
  (privado, criptografia em repouso, URLs assinadas curtas): mais de um no de API, necessidade de
  CDN/replicacao, exigencia de retencao/verificacao antivirus, ou volume de anexos que inviabilize
  backup por tar.
- **Consequencias:** Drill obrigatorio com evidencia: upload → recriar container → download OK →
  exclusao do titular → arquivos removidos. Falha de remocao fisica pos-commit permanece risco
  operacional registrado ate storage duravel com exclusao transacional.
