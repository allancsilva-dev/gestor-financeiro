# ADR-0015 — Padrao obrigatorio de reconciliacao e migracao

- **Status:** Accepted (2026-07-15, plano Fase 2 rev. 3 aprovado pelo responsavel do produto)
- **Contexto:** a Fase 2 altera dados financeiros reais na VPS. Flyway nao tem undo; padroes de
  guard ja usados (V12, V27-contract, V30, V31) provaram o modelo. Exigencia do doc mestre: toda
  migration financeira comprova reconciliacao antes/depois e caminho de recuperacao.
- **Decisao:**
  - **expand -> migrate -> contract** em schema e API; contract so promovido apos validacao em
    PostgreSQL real (Testcontainers) + dry-run na VPS;
  - toda data-migration: snapshot de saldos/totais pre e pos (baseline do PR-F2-01), invariantes
    executaveis em SQL por bloco e guard `RAISE EXCEPTION` que aborta em divergencia;
  - **recuperacao = backup/restore verificado + migration compensatoria**; migration aplicada
    nunca e desfeita manualmente; restore drill antes de cada data-migration;
  - reconciliacao a centavo; residuo de arredondamento vira lancamento AJUSTE explicito, nunca
    correcao silenciosa;
  - backfill e sempre idempotente (padrao `NOT EXISTS` por origem) e nunca inventa historia:
    saldo de abertura com origem BACKFILL nao afeta resultado mensal nem variacao patrimonial
    economica;
  - migrations que inserem/alteram `carteiras` sao serializadas entre blocos; so trabalho de
    codigo sem conflito ocorre em paralelo;
  - invariantes permanentes verificadas por reconciliacao global automatizada (PR-F2-20):
    `saldo materializado == SUM(lancamentos)` por conta CAIXA/COFRE/CARTAO;
    `passivo do cartao == soma assinada dos lancamentos de faturas nao pagas`;
    `meta.valorReservado == saldo do COFRE` por meta; zero registro incompleto sem estado
    explicito.
- **Consequencias:** nenhum PR de Fase 2 com data-migration recebe PASS sem evidencia de
  reconciliacao antes/depois e drill de restore registrados (PROTOCOLO 10 blocos).
