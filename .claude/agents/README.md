# Agentes do Gestor Financeiro

Este documento descreve os agentes de projeto disponiveis no Claude Code para o Gestor Financeiro e o
ciclo recomendado de uso.

## Como chamar cada agente

Os agentes sao invocados pelo Claude Code via `--agent` flag ou referenciados em sessao:

```bash
claude --agent backend-engineer
claude --agent frontend-engineer
claude --agent database-engineer
claude --agent security-auditor
claude --agent lgpd-auditor
claude --agent quality-reviewer
claude --agent test-engineer
claude --agent docs-reporter
```

**Pre-requisito:** estar no diretorio raiz do projeto (`/Users/Zero/Projetos/gestor-financeiro`). O
`.claude/agents/` e `.claude/settings.json` na raiz sao lidos automaticamente.

## Resumo dos agentes

| Agente | Tipo | Modelo | Pode implementar? | Dominio |
|---|---|---|---|---|
| `backend-engineer` | executor | sonnet | Sim | `backend/` |
| `frontend-engineer` | executor | sonnet | Sim | `frontend/`, `mobile/` |
| `database-engineer` | executor | sonnet | Sim | Entidades JPA, repositorios |
| `security-auditor` | auditor (read-only) | opus | Nao | Seguranca |
| `lgpd-auditor` | auditor (read-only) | opus | Nao | Privacidade e LGPD |
| `quality-reviewer` | revisor (read-only) | sonnet | Nao | Revisao de PR e qualidade |
| `test-engineer` | executor | sonnet | Sim | Testes |
| `docs-reporter` | documentador | sonnet | Sim (docs/) | Documentacao e rastreabilidade |

## Ciclo recomendado de uso dos agentes

### 1. Para implementacao

1. **Agente da area:** `backend-engineer`, `frontend-engineer`, `database-engineer` ou `test-engineer`,
   conforme a area.
2. **`quality-reviewer`** (obrigatorio) — revisao de qualidade do que foi implementado.
3. **`security-auditor`** (obrigatorio se envolver autenticacao, autorizacao, dados sensiveis ou
   multi-tenant) — auditoria de seguranca.
4. **`lgpd-auditor`** (obrigatorio se envolver dados pessoais) — auditoria de privacidade e LGPD.
5. **`docs-reporter`** (obrigatorio) — registro de tudo nos arquivos de documentacao.

```
backend-engineer ──► quality-reviewer ──► security-auditor ──► lgpd-auditor ──► docs-reporter
      ou                    (obrigatorio)     (se pertinente)    (se pertinente)   (obrigatorio)
frontend-engineer
database-engineer
test-engineer
```

### 2. Para revisao

1. **`quality-reviewer`** (obrigatorio) — revisao de qualidade.
2. **`security-auditor`** (se houver risco de seguranca) — auditoria de seguranca.
3. **`lgpd-auditor`** (se houver dados pessoais) — auditoria de privacidade e LGPD.
4. **`docs-reporter`** (obrigatorio) — registro de achados e ressalvas.

```
quality-reviewer ──► security-auditor ──► lgpd-auditor ──► docs-reporter
  (obrigatorio)       (se pertinente)     (se pertinente)   (obrigatorio)
```

### 3. Para bug

1. **Agente da area responsavel:** `backend-engineer`, `frontend-engineer` ou `database-engineer`.
2. **`test-engineer`** (obrigatorio) — teste de regressao.
3. **`quality-reviewer`** (obrigatorio) — revisao da correcao.
4. **`docs-reporter`** (obrigatorio) — registro do bug corrigido e ressalvas.

```
area-engineer ──► test-engineer ──► quality-reviewer ──► docs-reporter
                     (obrigatorio)     (obrigatorio)       (obrigatorio)
```

## Regra obrigatoria

**Nenhum achado relevante pode ficar apenas no chat.** Todo problema aberto, bug corrigido, ressalva,
decisao importante ou proximo passo deve ser registrado pelo `docs-reporter` nos arquivos apropriados
em `docs/`.

## Quando usar cada agente

### backend-engineer
Use para: implementar ou alterar APIs REST, regras de negocio, validacoes, DTOs, tratamento de erros,
autenticacao, autorizacao, integracoes ou qualquer codigo em `backend/`.

**Nao use para:** frontend, mobile, banco de dados, infraestrutura (sem reportar no relatorio).

### frontend-engineer
Use para: implementar ou alterar UI web (React) ou mobile (React Native), componentes, hooks, estado,
consumo de API, UX de loading/erro/vazio, formularios, acessibilidade.

**Nao use para:** backend, banco de dados, criacao de endpoints.

### database-engineer
Use para: alterar entidades JPA, repositorios, schema, indices, constraints, queries, performance SQL.

**Nao use para:** regras de negocio (exceto para preservar consistencia de dados), frontend, mobile.

### security-auditor
Use para: revisar seguranca de qualquer PR ou mudanca. Autenticacao, autorizacao, JWT, tokens, CSRF,
CORS, rate limit, secrets, logs, validacao de entrada, isolamento de tenant.

**Nao use para:** implementar correcoes (ele e read-only).

### lgpd-auditor
Use para: revisar privacidade e conformidade LGPD. Minimizacao, consentimento, retencao, exclusao,
exposicao de dados pessoais, logs, telas publicas.

**Nao use para:** dar parecer juridico definitivo, implementar correcoes (read-only).

### quality-reviewer
Use para: revisao final de qualquer PR antes de conclusao. Regressao, escopo, testes, lint, arquitetura,
duplicacao, legibilidade, performance, seguranca, contratos.

**Nao use para:** implementar correcoes (read-only para codigo, pode rodar testes/lint via Bash).

### test-engineer
Use para: implementar testes unitarios, de integracao ou e2e. Backend (JUnit 5 + MockMvc) ou frontend
(Vitest + Testing Library).

**Nao use para:** alterar codigo de producao (exceto adicionar metodos para testabilidade, devidamente
reportados).

### docs-reporter
Use para: documentar problemas encontrados, bugs corrigidos, decisoes, backlog, proximos passos, ressalvas
de revisao e visao geral do sistema. Acionado obrigatoriamente ao final de diagnosticos, implementacoes,
revisoes, correcoes e auditorias.

**Nao use para:** alterar codigo de aplicacao, migrations, secrets, testes, package files. Atua somente
em `docs/` e neste README.

## Arquivos de documentacao mantidos pelo docs-reporter

| Arquivo | Conteudo |
|---|---|
| `docs/PROBLEM_LEDGER.md` | Registro de problemas encontrados |
| `docs/BUGFIX_LOG.md` | Registro de bugs corrigidos |
| `docs/BACKLOG.md` | Proximos passos e itens pendentes |
| `docs/SYSTEM_OVERVIEW.md` | Visao geral do sistema |
| `docs/DIAGRAMS.md` | Diagramas e topologia |
| `docs/REVIEW_REPORTS/` | Relatorios de revisao e auditoria |

## Documentos canonicos (intocaveis por agentes executores)

- `backend/API.md` — contrato da API REST
- `docs/CHANGELOG.md` — historico de versoes
- `docs/DEPLOY.md` — guia de deploy
- `docs/PROXIMOS_PASSOS.md` — roadmap

Divergencias com documentos canonicos devem ser reportadas, nunca alteradas silenciosamente.

## Regras globais (aplicaveis a todos os agentes)

1. Diagnostico read-only antes de qualquer alteracao.
2. Respeitar arquitetura e documentos canonicos (`backend/API.md`, `docs/*`).
3. Atuar somente no proprio dominio.
4. Declarar claramente o que verificou, o que nao verificou e quais comandos executou.
5. Preferir mudancas pequenas, rastreaveis e compativeis.
6. Seguranca, qualidade, performance, resiliencia e velocidade como principios simultaneos.
7. Evitar overengineering e abstracoes desnecessarias.
8. Usar scripts existentes do projeto para validacao.
9. Reportar riscos residuais.
10. **Nunca** fazer commit, push, deploy, migration destrutiva ou alteracao de secrets sem autorizacao.
