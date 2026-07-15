# ADR-0001 — Backend como fonte unica de regra financeira

- **Status:** Accepted (2026-07-15)
- **Contexto:** A auditoria (`docs/15 07 2026 - MetaDoNexosFinancas.md`) encontrou regra financeira
  duplicada em cliente: o onboarding web orquestra criacao de carteira, conta, categorias, renda e
  meta em chamadas independentes, divergindo do fluxo mobile e do endpoint transacional canonico.
  O produto adota a estrategia progressiva (Opcao D): consolidar nucleo manual confiavel antes de
  captura automatica, patrimonio, canal conversacional e Open Finance.
- **Decisao:** Toda regra financeira (criacao, calculo, validacao, transicao de estado, agregacao)
  vive exclusivamente no backend. Clientes web e mobile validam formato de entrada e apresentam
  resultado; nunca orquestram sequencias de escrita que componham uma operacao de negocio, nunca
  calculam saldo/estado localmente como fonte de verdade. Fases 2-6 do roadmap permanecem marcos e
  nao iniciam durante o congelamento (que termina somente apos PR-4, testes globais e evidencias).
- **Consequencias:** Onboarding web migra para o caso de uso canonico (ADR-0002). Toda feature nova
  declara sua fonte de verdade no backend antes de UI. Revisoes de PR rejeitam duplicacao de regra
  em cliente.
