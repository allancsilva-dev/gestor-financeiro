# Implementacao de Hardening do Release Mobile

**Data:** 2026-07-13

**Baseline:** `main` em `807e777`

**Escopo:** app mobile e automações mobile
**Implementação:** commit `2db9b58` (`feat(mobile): harden release readiness`)

## Objetivo

Avançar os itens 0073 e 0075–0079 apenas no produto mobile, preservando o
frontend web e o backend. Não promover status sem evidência reproduzível.

## Implementado

### Release e proveniência

- Android Release e iOS Simulator Release gerados a partir do SHA aprovado;
- artifacts recebem o SHA no nome;
- workflow só segue após CI verde em `main` ou despacho manual;
- release exige DSN/token/organização/projeto Sentry;
- CI mobile executa audit, Expo Doctor, TypeScript, lint a11y e testes.

### Observabilidade

- Sentry React Native com release SHA e ambiente;
- PII padrão desativada;
- `user`, `request`, `extra` e dados de breadcrumbs removidos antes do envio;
- tracing desativado;
- plugin/source maps condicionado a credenciais completas, sem quebrar build local.

### LGPD e acessibilidade

- política nativa versão `2026-07`, acessível antes do consentimento;
- checkbox com role/state, link independente e alvo de 44pt;
- erros de auth/onboarding e fields anunciados;
- controles conhecidos abaixo de 44pt corrigidos;
- inputs diretos receberam labels;
- dashboard mobile perdeu gradiente hero, decoração e sombra promocional;
- ESLint React Native a11y bloqueia warnings; TypeScript permanece gate separado.

### Automação funcional

- Maestro Android/iOS protegido pelo environment `staging`;
- CLI fixada e instalador verificado por SHA-256;
- JUnit e diagnósticos publicados como artifacts;
- flows: login, entrada na recuperação e política/consentimento.

## Evidências locais

| Gate | Resultado |
|---|---|
| `npm run typecheck` | PASS |
| `npm run lint` | PASS, zero warnings |
| `npm test -- --runInBand` | PASS, 11/11 |
| `npx expo-doctor` | PASS, 18/18 |
| `npm audit --omit=dev --audit-level=high` | PASS, zero high/critical |
| Android `assembleRelease` | PASS, 472 tasks |
| iOS Simulator Release | `BUILD SUCCEEDED` |
| YAML dos workflows | PASS |
| `git diff --check` | PASS |

O primeiro Android Release expôs falha de engenharia no upload Sentry sem
organização/projeto. O build somente passou após tornar a integração de build
condicional a credenciais completas; não foi usado skip ou bypass.

## Matriz de rastreabilidade

| Backlog | Entregue nesta rodada | Continua pendente |
|---|---|---|
| 0073 | build/proveniência mobile por SHA; commit `2db9b58` | CI remoto, assinatura/store, hardware |
| 0075 | workflow Maestro e 3 smokes | jornadas financeiras completas e execução staging |
| 0076 | navegação/erros acessíveis | SMTP, deep link e single-use E2E |
| 0077 | política e consentimento nativos | revisão jurídica e direitos E2E |
| 0078 | targets, labels, alerts, lint, UI | VoiceOver/TalkBack/fonte/contraste em hardware |
| 0079 | Sentry mobile sem PII | projeto/secrets/evento externo e alertas operacionais |

## Fora do escopo

- frontend web;
- backend, Actuator, SMTP e banco;
- deploy da API/VPS, backup, maintenance e V27;
- alterações de produção ou contas SaaS.

## Próximo gate

1. configurar environments/secrets Sentry e staging;
2. confirmar CI e artifacts no SHA candidato;
3. executar Maestro Android/iOS;
4. concluir VoiceOver/TalkBack e smoke em hardware físico;
5. só então reavaliar status e nota.
