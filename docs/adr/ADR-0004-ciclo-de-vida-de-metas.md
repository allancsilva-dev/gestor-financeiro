# ADR-0004 — Ciclo de vida de metas e valor reservado

- **Status:** Accepted (2026-07-15)
- **Contexto:** Meta que atinge 100% recebe `ativa=false` e some da listagem (que so retorna
  ativas); exclusao tambem apenas desativa, mesmo com `valorReservado > 0`. Dinheiro ja debitado da
  carteira fica invisivel e sem caminho normal de resgate (P0-3). Principio do produto: nenhum dado
  financeiro desaparece por arquivamento, conclusao ou deploy.
- **Decisao:** Meta ganha `status` canonico com tres estados e regras:
  - criacao → `ATIVA`;
  - reserva atinge ou supera o objetivo → `CONCLUIDA` (permanece acessivel em historico);
  - resgate que caia abaixo do objetivo → volta a `ATIVA`;
  - exclusao com `valorReservado > 0` → **bloqueada** com HTTP 400 instrutivo (resgatar antes);
  - exclusao sem reserva → `ARQUIVADA` (soft-delete);
  - `ARQUIVADA` nao aceita edicao nem movimentacao;
  - repetir conclusao/arquivamento nao duplica efeitos (idempotente).
  O boolean `ativa` permanece no banco e no JSON por compatibilidade com clientes publicados,
  sincronizado pelo modelo/service, e deprecado. Migration `V30__meta_status.sql` faz backfill:
  `dataConclusao` presente → `CONCLUIDA`; `ativa=false` sem `dataConclusao` → `ARQUIVADA`; caso
  contrario `ATIVA`.
- **Consequencias:** API ganha filtro `?status=` em `/api/v1/metas/minhas` (ausencia = `ATIVA`,
  compat total). Web e mobile exibem abas/filtros de ativas, concluidas e arquivadas. Estorno
  automatico de reserva na exclusao e melhoria futura (pos-Fase 1). Metricas de patrimonio que
  somam reservas ficam para a Fase 2.
