# Agentes do Gestor Financeiro

Este documento descreve os agentes de projeto disponíveis no Claude Code para o Gestor Financeiro.

## Como chamar cada agente

Os agentes são invocados pelo Claude Code via `--agent` flag ou referenciados em sessão:

```bash
claude --agent backend-engineer
claude --agent frontend-engineer
claude --agent database-engineer
claude --agent security-auditor
claude --agent lgpd-auditor
claude --agent quality-reviewer
claude --agent test-engineer
```

**Pré-requisito:** estar no diretório raiz do projeto (`/Users/Zero/Projetos/gestor-financeiro`). O
`.claude/agents/` e `.claude/settings.json` na raiz são lidos automaticamente.

## Resumo dos agentes

| Agente | Tipo | Modelo | Pode implementar? |
|---|---|---|---|
| `backend-engineer` | executor | sonnet | Sim (backend apenas) |
| `frontend-engineer` | executor | sonnet | Sim (frontend + mobile) |
| `database-engineer` | executor | sonnet | Sim (entidades/repos) |
| `security-auditor` | auditor (read-only) | opus | Não |
| `lgpd-auditor` | auditor (read-only) | opus | Não |
| `quality-reviewer` | revisor (read-only) | sonnet | Não |
| `test-engineer` | executor | sonnet | Sim (testes apenas) |

## Quando usar cada agente

### backend-engineer
Use para: implementar ou alterar APIs REST, regras de negócio, validações, DTOs, tratamento de erros,
autenticação, autorização, integrações ou qualquer código em `backend/`.

**Não use para:** frontend, mobile, banco de dados, infraestrutura (sem reportar no relatório).

### frontend-engineer
Use para: implementar ou alterar UI web (React) ou mobile (React Native), componentes, hooks, estado,
consumo de API, UX de loading/erro/vazio, formulários, acessibilidade.

**Não use para:** backend, banco de dados, criação de endpoints.

### database-engineer
Use para: alterar entidades JPA, repositórios, schema, índices, constraints, queries, performance SQL.

**Não use para:** regras de negócio (exceto para preservar consistência de dados), frontend, mobile.

### security-auditor
Use para: revisar segurança de qualquer PR ou mudança. Autenticação, autorização, JWT, tokens, CSRF,
CORS, rate limit, secrets, logs, validação de entrada, isolamento de tenant.

**Não use para:** implementar correções (ele é read-only).

### lgpd-auditor
Use para: revisar privacidade e conformidade LGPD. Minimização, consentimento, retenção, exclusão,
exposição de dados pessoais, logs, telas públicas.

**Não use para:** dar parecer jurídico definitivo, implementar correções (read-only).

### quality-reviewer
Use para: revisão final de qualquer PR antes de conclusão. Regressão, escopo, testes, lint, arquitetura,
duplicação, legibilidade, performance, segurança, contratos.

**Não use para:** implementar correções (read-only para código, pode rodar testes/lint via Bash).

### test-engineer
Use para: implementar testes unitários, de integração ou e2e. Backend (JUnit 5 + MockMvc) ou frontend
(Vitest + Testing Library).

**Não use para:** alterar código de produção (exceto adicionar métodos para testabilidade, devidamente
reportados).

## Ordem recomendada para uma tarefa comum

Para uma feature nova (ex: adicionar campo `limiteGasto` em Categoria):

1. **quality-reviewer** (diagnóstico inicial) — O que existe hoje? Quais testes cobrem?
2. **database-engineer** (se houver mudança de schema) — Adicionar campo na entidade JPA.
3. **backend-engineer** — Alterar DTOs, service, controller.
4. **test-engineer** — Escrever/atualizar testes de backend.
5. **frontend-engineer** — Atualizar UI para usar o novo campo (web + mobile).
6. **test-engineer** — Escrever/atualizar testes de frontend.
7. **security-auditor** — Revisar se a mudança introduz risco de segurança.
8. **lgpd-auditor** — Revisar se a mudança afeta privacidade de dados (se pertinente).
9. **quality-reviewer** (revisão final) — Revisar tudo, rodar lint/test, aprovar ou solicitar mudanças.

Para um bug fix:

1. **quality-reviewer** — Identificar escopo do bug e arquivos afetados.
2. **backend-engineer** ou **frontend-engineer** — Corrigir.
3. **test-engineer** — Adicionar teste de regressão.
4. **quality-reviewer** — Revisão final.

## Regras globais (aplicáveis a todos os agentes)

1. Diagnóstico read-only antes de qualquer alteração.
2. Respeitar arquitetura e documentos canônicos (`backend/API.md`, `docs/*`).
3. Atuar somente no próprio domínio.
4. Declarar claramente o que verificou, o que não verificou e quais comandos executou.
5. Preferir mudanças pequenas, rastreáveis e compatíveis.
6. Segurança, qualidade, performance, resiliência e velocidade como princípios simultâneos.
7. Evitar overengineering e abstrações desnecessárias.
8. Usar scripts existentes do projeto para validação.
9. Reportar riscos residuais.
10. **Nunca** fazer commit, push, deploy, migration destrutiva ou alteração de secrets sem autorização.

## Documentos canônicos (intocáveis por agentes executores)

- `backend/API.md` — contrato da API REST
- `docs/CHANGELOG.md` — histórico de versões
- `docs/DEPLOY.md` — guia de deploy
- `docs/PROXIMOS_PASSOS.md` — roadmap

Divergências com documentos canônicos devem ser reportadas, nunca alteradas silenciosamente.
