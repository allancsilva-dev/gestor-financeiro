# Teste funcional de metas — iOS Simulator

**Data:** 2026-07-15
**Escopo:** mobile + backend/PostgreSQL local
**Resultado:** PASS após correção de regressão iOS
**Base:** `2026-07-15_full-system_implementacao_correcao-auditoria-fase-1.md`

## Restrições preservadas

- worktree preexistente preservado, sem descarte de mudanças;
- `V30__meta_status.sql` não alterada;
- `PROB-0081` continua `REABERTO`;
- nenhum commit criado.

## Ambiente executado

- macOS, Xcode 26.6 (`17F113`), iOS Simulator 26.5;
- iPhone 17 Pro `5E26657D-DEAC-4871-9ED2-EF712BB4BBBA` bootado;
- Java 21.0.11;
- Node 22.23.1 via NVM (Node 20 da `.nvmrc` não estava instalado; Node 22 atende o requisito Node 18+ e o fallback já documentado pela Fase 1);
- Docker 29.5.3 / Compose 5.1.4;
- PostgreSQL 17.10, container `gf-postgres`, healthy;
- backend Spring Boot 3.5.16 em `http://127.0.0.1:8080`, health/DB `UP`;
- Metro em `http://localhost:8081`;
- app `com.gestorfinanceiro.mobile` compilado, instalado e aberto no Simulator;
- Maestro 2.6.1.

Backend foi movido de 8081 para 8080 durante o teste porque React Native debug usa 8081 para Metro. Build local recebeu `EXPO_PUBLIC_API_BASE_URL=http://127.0.0.1:8080/api`.

## Dados controlados

Usuário local descartável `auditoria.metas@local.test`, onboarding concluído, uma carteira com saldo e metas nos estados `ATIVA`, `CONCLUIDA` e `ARQUIVADA`. Dados existem somente no PostgreSQL local.

## Regressão encontrada e corrigida

No detalhe de uma meta, tocar **Adicionar valor** ou **Retirar valor** não abria o segundo modal no iOS. O detalhe continuava visível. Causa: tentativa de apresentar um `Modal` enquanto o modal de detalhe permanecia apresentado.

Correção em `mobile/app/(app)/metas.tsx`:

1. fecha detalhe antes de apresentar modal de movimentação;
2. rastreia origem da movimentação;
3. após sucesso ou cancelamento, fecha movimentação e reabre detalhe;
4. mantém `metaSelecionada` com resposta canônica do backend, exibindo valor/status atualizados.

## Cenários finais

| Cenário | Resultado |
|---|---|
| filtro Ativas envia/exibe `ATIVA` | PASS |
| filtro Concluídas envia/exibe `CONCLUIDA` | PASS |
| filtro Arquivadas envia/exibe `ARQUIVADA` | PASS |
| arquivada sem Editar/Adicionar/Retirar/Excluir | PASS |
| concluída com Editar e Retirar, sem Adicionar/Excluir | PASS |
| ativa com reserva com Editar/Adicionar/Retirar, sem Excluir | PASS |
| aporte de R$ 100 atualiza detalhe de R$ 200 para R$ 300 | PASS |
| resgate de R$ 50 atualiza detalhe de R$ 300 para R$ 250 | PASS |
| aporte zero mostra `Valor deve ser positivo.` | PASS |
| resgate maior que reserva mostra `Valor maior que o reservado.` | PASS |
| editar objetivo de R$ 1.000 para R$ 1.200 reabre concluída | PASS |
| meta reaberta sai de Concluídas, aparece em Ativas e detalhe mostra 1.000/1.200 | PASS |
| criação de `Meta Fluxo Audit` com objetivo R$ 100 | PASS |
| aporte integral conclui meta e remove Adicionar/Excluir | PASS |
| resgate de concluída reabre meta e restaura Adicionar | PASS |

## Evidências visuais

- [Ativas](evidence/2026-07-15_mobile-metas-ios/gf-evidence-02-ativas.png)
- [Ações da ativa](evidence/2026-07-15_mobile-metas-ios/gf-evidence-03-ativa-actions.png)
- [Concluídas](evidence/2026-07-15_mobile-metas-ios/gf-evidence-04-concluidas.png)
- [Ações da concluída](evidence/2026-07-15_mobile-metas-ios/gf-evidence-05-concluida-actions.png)
- [Arquivadas](evidence/2026-07-15_mobile-metas-ios/gf-evidence-06-arquivadas.png)
- [Arquivada read-only](evidence/2026-07-15_mobile-metas-ios/gf-evidence-07-arquivada-readonly.png)
- [Erro de aporte](evidence/2026-07-15_mobile-metas-ios/gf-evidence-08-add-error.png)
- [Detalhe após aporte](evidence/2026-07-15_mobile-metas-ios/gf-evidence-09-add-refresh.png)
- [Erro de resgate](evidence/2026-07-15_mobile-metas-ios/gf-evidence-10-withdraw-error.png)
- [Detalhe após resgate](evidence/2026-07-15_mobile-metas-ios/gf-evidence-11-withdraw-refresh.png)
- [Filtro após edição/reabertura](evidence/2026-07-15_mobile-metas-ios/gf-evidence-12-edit-reopened-filter.png)
- [Detalhe após edição/reabertura](evidence/2026-07-15_mobile-metas-ios/gf-evidence-13-edit-reopened-detail.png)
- [Meta criada](evidence/2026-07-15_mobile-metas-ios/gf-evidence-14-created.png)
- [Meta concluída por aporte](evidence/2026-07-15_mobile-metas-ios/gf-evidence-15-created-completed.png)
- [Meta reaberta por resgate](evidence/2026-07-15_mobile-metas-ios/gf-evidence-16-withdraw-reopened.png)

## Gates repetidos após correção

```text
npm run typecheck  PASS
npm run lint       PASS, zero warnings
npm run test       PASS, 6 suites / 17 testes
git diff --check   PASS
```

Warnings operacionais não bloqueantes:

- Expo sugere `expo@~54.0.36`; instalado `54.0.35`;
- build iOS emitiu warning preexistente de deployment target do pod Sentry;
- `devicectl` informou incompatibilidade de versão JSON apenas para descoberta de device físico; Simulator funcionou.

## Estado final

PostgreSQL, backend, Metro, Simulator e aplicativo permanecem em execução. Critérios funcionais de metas deste ciclo: `PASS`.
