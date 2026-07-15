# ADR-0002 — Onboarding canonico via `/finalizar`

- **Status:** Accepted (2026-07-15)
- **Contexto:** Existem dois caminhos de onboarding. Mobile usa
  `POST /api/v1/onboarding/finalizar` (`OnboardingService.finalizar()`, `@Transactional`). Web faz
  5 POSTs independentes (carteira, conta, categorias, renda, meta) e depois `/completar` — falha
  intermediaria deixa cadastro parcial e retry duplica etapas (P0-4). Alem disso a renda herda o
  default `SAIDA` de `ContaFixa` (P0-1).
- **Decisao:** `POST /api/v1/onboarding/finalizar` e o unico caminho de onboarding para web e
  mobile. Contrato: idempotente por usuario — o service adquire lock pessimista na linha do usuario,
  verifica `onboardingCompleto` e, se ja concluido, retorna 200 com estado atual sem recriar dados;
  falha intermediaria reverte tudo (transacao unica). A renda inicial e criada como
  `TipoTransacao.ENTRADA` com categoria "Renda" (criada/reutilizada de forma idempotente). Os
  endpoints granulares continuam existindo para uso pos-onboarding, mas clientes nao os usam para
  compor a jornada inicial.
- **Consequencias:** `frontend/src/pages/Onboarding.tsx` deixa de persistir por etapa; etapas
  validam e guardam estado local, e a confirmacao envia um unico `OnboardingFinalizarRequest`.
  Jornada principal ganha E2E (Playwright no web; Maestro segue no mobile). O fallback `SAIDA` em
  `ContaFixaService` permanece por compatibilidade com clientes publicados, com log WARN para medir
  uso residual.
