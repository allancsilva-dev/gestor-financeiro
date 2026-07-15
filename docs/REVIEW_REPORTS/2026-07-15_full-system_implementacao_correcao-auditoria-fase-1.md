# Correção da Auditoria — Fase 1: implementação e rastreabilidade

**Data:** 2026-07-15
**Área:** full-system (backend, web, mobile, banco, backup e CI)
**Tipo:** implementação corretiva
**Status final:** PASS_COM_RESSALVA
**Worktree:** alterações implementadas e validadas, ainda não commitadas neste registro

---

## 1. Objetivo original

Implementar a primeira fase de correções da auditoria, cobrindo:

- integridade do ciclo de vida das metas;
- contrato HTTP, exportação e políticas de ações nos clientes;
- consistência operacional do backup, restore e timezone;
- gates de CI e regressões críticas.

A migration `V30__meta_status.sql` já havia sido aplicada e não poderia ser alterada. Toda
correção de banco deveria entrar em uma nova `V31`.

O problema `PROB-0081` deveria ser reaberto e só poderia ser encerrado depois de um drill real
com API comprovadamente parada, remote externo off-host, checksum e restauração completa.

### Ordem lógica recebida

```text
PR-A Metas/backend
  └─ PR-B Clientes/API/exportação
PR-C Backup/timezone
  └─ PR-D CI, testes e evidências
```

### Critérios de aceite recebidos

- backend, web e mobile verdes;
- PostgreSQL real executando V30, V31, LGPD e backfills;
- Playwright automático no CI;
- metas nunca persistindo estado incompatível após edição;
- mobile sem oferecer ações proibidas;
- exportação preservando o status canônico;
- backup comprovando API parada, banco/uploads, GPG, off-host, checksum, restore PG17,
  anexo baixável e API saudável;
- `git diff --check`, compose config e scripts shell válidos.

---

## 2. Estado encontrado no início

O worktree estava limpo. Parte da fase já existia no código:

- `V30__meta_status.sql` e o enum `StatusMeta` já estavam presentes;
- o backend já filtrava metas por status e usava `ATIVA` como default;
- aportes concluíam metas e resgates podiam reabri-las;
- `BusinessException` já era convertida em HTTP 422;
- web e mobile já tinham filtros `ATIVA`, `CONCLUIDA` e `ARQUIVADA`;
- havia backup assimétrico, bundle com uploads e restore drill;
- existiam testes iniciais de lifecycle e backfill de metas.

Lacunas confirmadas:

- editar `valorTotal` não recalculava o lifecycle;
- não havia constraint garantindo equivalência entre `status` e `ativa`;
- status inválido caía no handler genérico;
- a exportação não distinguia `CONCLUIDA` de `ARQUIVADA` por status canônico;
- mobile ainda oferecia aporte em meta concluída e ações em arquivadas;
- `metaSelecionada` podia permanecer obsoleta após mutations;
- o container de backup tentava controlar a API via Docker;
- upload remoto era validado somente por tamanho;
- restore drill usava PostgreSQL 16 e não conferia cada caminho persistido de anexo;
- o runbook ensinava uma chave Ed25519 inadequada para criptografia direta;
- não havia job Playwright no CI nem `.nvmrc`;
- `PROB-0081` estava marcado como corrigido apesar de o drill ter usado remote local.

---

## 3. Implementação realizada

## 3.1 PR-A — Integridade de metas

### Migration V31

Criada `backend/src/main/resources/db/migration/V31__sincronizar_meta_status_ativa.sql`:

1. recalcula todas as flags com `ativa = (status = 'ATIVA')`;
2. define `ativa` como `NOT NULL`;
3. cria `ck_metas_status_ativa`, impedindo divergência entre status e boolean legado.

`V30` permaneceu inalterada.

### Transição centralizada no domínio

Adicionado `Meta.recalcularEstado(LocalDate hoje)`:

- `valorReservado >= valorTotal` → `CONCLUIDA`;
- `valorReservado < valorTotal` → `ATIVA`;
- mantém `dataConclusao` quando a meta continua concluída;
- preenche a data na primeira conclusão;
- limpa a data ao reabrir;
- não altera uma meta `ARQUIVADA`.

O método passou a ser usado em:

- aporte;
- resgate;
- edição de meta após alterar o objetivo.

O campo JPA `ativa` também foi marcado como `nullable = false`.

### Testes adicionados/fortalecidos

`MetaLifecycleTest` agora cobre:

- redução de objetivo concluindo a meta;
- aumento de objetivo reabrindo e limpando `dataConclusao`;
- edição que mantém a conclusão preservando a data;
- exportação distinguindo concluída de arquivada.

`MetaStatusBackfillIT` agora:

- migra até V29;
- semeia dados legados;
- aplica V30 e valida o primeiro backfill;
- cria divergências deliberadas depois da V30;
- aplica V31;
- valida correção, `NOT NULL` e rejeição de novas divergências.

O script PostgreSQL canônico passou a incluir `MetaStatusBackfillIT`.

---

## 3.2 PR-B — Contrato, exportação e clientes

### Contrato HTTP

- mantido HTTP 422 `BUSINESS_ERROR` para exclusão de meta com reserva;
- adicionado handler de `MethodArgumentTypeMismatchException`;
- status/parâmetro inválido agora retorna HTTP 400 `INVALID_PARAMETER`;
- criado `MetaControllerContractTest` para `status=PAUSADA`.

### Documentação da API e ADR

`backend/API.md` passou a documentar:

- `?status=ATIVA|CONCLUIDA|ARQUIVADA`;
- default `ATIVA`;
- campo canônico `status`;
- `ativa` como campo deprecado;
- HTTP 422 na exclusão com reserva;
- HTTP 400 `INVALID_PARAMETER` para filtro inválido.

`ADR-0004` foi corrigido de HTTP 400 para HTTP 422.

### Exportação

A seção de metas da exportação completa agora contém:

```text
...,Data Conclusão,Status,Ativa
```

A coluna legada `Ativa` foi preservada, e `Status` diferencia explicitamente
`ATIVA`, `CONCLUIDA` e `ARQUIVADA`.

### Política de ações web/mobile

A política foi centralizada em funções testáveis `acoesDaMeta` nos dois clientes.

| Estado | Editar | Adicionar | Resgatar | Excluir |
|---|---:|---:|---:|---:|
| ATIVA sem reserva | sim | sim | não | sim |
| ATIVA com reserva | sim | sim | sim | não |
| CONCLUIDA | sim | não | sim | não |
| ARQUIVADA | não | não | não | não |

No mobile:

- o atalho de aporte também some em metas concluídas;
- o detalhe de arquivadas fica read-only;
- mutations de aporte, resgate e edição atualizam `metaSelecionada`;
- exclusão continua exibindo a mensagem instrutiva retornada pelo backend.

Na web, a mesma política foi aplicada às ações de cada meta.

### Testes de clientes

Foram adicionados testes para:

- política de ações nos três estados, web e mobile;
- envio dos três filtros de status pelo serviço web;
- envio dos três filtros de status pelo serviço mobile.

---

## 3.3 PR-C — Backup e timezone

### Coordenador host-side

Criado `scripts/run-backup.sh`, executado no host.

O coordenador:

1. valida Docker e Compose v2;
2. valida recipient GPG, chave pública e remote;
3. valida os serviços `api` e `postgres-backup` no compose;
4. exige que a API esteja em execução e saudável antes da janela;
5. para o serviço `api`;
6. executa `postgres-backup` como container one-shot;
7. religa a API via `trap`, inclusive em falhas;
8. aguarda o healthcheck;
9. retorna erro se o backup ou a recuperação da API falhar.

O container `backup-db.sh` não controla mais Docker e não recebe Docker socket.

### Checksum remoto

A validação baseada somente no tamanho foi removida. Depois de `rclone copyto`, o backup usa:

```text
rclone check --download --one-way ...
```

Assim, o conteúdo criptografado remoto é conferido mesmo quando o provider não oferece um hash
compatível diretamente.

### Restore drill

`scripts/restore-drill-db.sh` agora:

- sobe `postgres:17-alpine` por default;
- mantém a validação do manifesto e checksums internos;
- rejeita caminhos de anexo vazios ou com traversal;
- mapeia cada caminho `/app/uploads/...` para o diretório restaurado;
- exige que cada arquivo referenciado exista e não esteja vazio.

### GPG e runbook

O runbook agora orienta:

1. chave primária Ed25519 para certificação/assinatura;
2. subchave Cv25519 dedicada a criptografia.

Cron/systemd deve chamar `scripts/run-backup.sh`, não `docker compose run` diretamente.

### Timezone

Adicionado aos serviços de API nos dois composes:

```text
APP_BUSINESS_TIMEZONE=${APP_BUSINESS_TIMEZONE:-America/Sao_Paulo}
```

### PROB-0081

O ledger foi corrigido para `REABERTO`. A evidência anterior é explicitamente histórica porque
usou um remote local e não comprovou a janela real com o novo coordenador.

O problema só pode voltar a `CORRIGIDO` após o drill off-host descrito na seção 8.

---

## 3.4 PR-D — Gates e regressões

### Node e Playwright

- criada `.nvmrc` com Node 20, igual ao CI;
- `scripts/e2e-web.sh` tenta carregar NVM quando Node não está no `PATH`;
- se a versão da `.nvmrc` não estiver instalada, usa uma versão Node já instalada pelo NVM;
- falha cedo com mensagens claras quando Node ou Playwright não existem;
- cleanup do backend e PostgreSQL efêmero foi preservado.

### Artefatos

Adicionados ao `.gitignore`:

- `frontend/test-results/`;
- `frontend/playwright-report/`.

O arquivo rastreado `frontend/test-results/.last-run.json` foi removido.

### CI

Adicionado job `playwright` em `.github/workflows/ci.yml` com:

- Java 17;
- Node da `.nvmrc`;
- dependências frontend;
- Chromium e dependências do sistema;
- PostgreSQL efêmero, backend e Vite via `scripts/e2e-web.sh`;
- upload de relatório/logs em caso de falha.

### Regressões fortalecidas

- quatro finalizações concorrentes de onboarding precisam terminar com sucesso;
- continua existindo apenas uma criação de cada recurso;
- renda criada no onboarding precisa aparecer positivamente no horizonte de projeção;
- LGPD prova rollback em falha intermediária por trigger PostgreSQL deliberada;
- dados e arquivo do titular permanecem depois do rollback;
- teste limpa o grafo preservado para não contaminar outros métodos da IT;
- status inválido retorna HTTP 400;
- exportação distingue concluída de arquivada.

---

## 4. Arquivos alterados

### Banco e backend

- `backend/src/main/resources/db/migration/V31__sincronizar_meta_status_ativa.sql` — novo;
- `backend/src/main/java/com/gestor/financeiro/model/Meta.java`;
- `backend/src/main/java/com/gestor/financeiro/service/MetaService.java`;
- `backend/src/main/java/com/gestor/financeiro/service/ExportService.java`;
- `backend/src/main/java/com/gestor/financeiro/exception/GlobalExceptionHandler.java`;
- `backend/src/test/java/com/gestor/financeiro/MetaLifecycleTest.java`;
- `backend/src/test/java/com/gestor/financeiro/MetaStatusBackfillIT.java`;
- `backend/src/test/java/com/gestor/financeiro/MetaControllerContractTest.java` — novo;
- `backend/src/test/java/com/gestor/financeiro/OnboardingAtomicidadeTest.java`;
- `backend/src/test/java/com/gestor/financeiro/UsuarioExclusaoLgpdIT.java`;
- `backend/API.md`.

### Web

- `frontend/src/pages/Metas.tsx`;
- `frontend/src/domain/metaPolicy.ts` — novo;
- `frontend/src/domain/metaPolicy.test.ts` — novo;
- `frontend/src/services/metaService.test.ts` — novo;
- `frontend/test-results/.last-run.json` — removido.

### Mobile

- `mobile/app/(app)/metas.tsx`;
- `mobile/src/domain/metaPolicy.ts` — novo;
- `mobile/src/__tests__/metaPolicy.test.ts` — novo;
- `mobile/src/__tests__/metaService.test.ts` — novo.

### Infraestrutura e CI

- `scripts/run-backup.sh` — novo e executável;
- `scripts/backup-db.sh`;
- `scripts/restore-drill-db.sh`;
- `scripts/e2e-web.sh`;
- `scripts/verify-postgres-migrations.sh`;
- `docker-compose.production.yml`;
- `docker-compose.vps.yml`;
- `.github/workflows/ci.yml`;
- `.nvmrc` — novo;
- `.gitignore`.

### Documentação

- `docs/PROBLEM_LEDGER.md`;
- `docs/RUNBOOK_BACKUP_RESTORE.md`;
- `docs/adr/ADR-0004-ciclo-de-vida-de-metas.md`;
- `docs/adr/ADR-0006-backup-criptografado-off-host.md`;
- este relatório.

---

## 5. Validações executadas e evidências

| Comando/gate | Resultado |
|---|---|
| `./mvnw -q -Dtest='MetaLifecycleTest,MetaControllerContractTest,OnboardingAtomicidadeTest' test` | PASS — 13 testes |
| `./mvnw -q test` | PASS — suíte backend completa |
| `./mvnw -q -DskipTests compile` | PASS após o último ajuste JPA |
| `scripts/verify-postgres-migrations.sh` | PASS em PostgreSQL real 16.14 |
| V29 → V30 → V31 no `MetaStatusBackfillIT` | PASS |
| `PostgresMigrationIT` | PASS — 5 testes |
| `UsuarioExclusaoLgpdIT` | PASS — 3 testes, incluindo rollback intermediário |
| frontend `npm run lint` | PASS com 83 warnings preexistentes e 0 erros |
| frontend `npm run build` | PASS |
| frontend `npm run test` | PASS — 21 testes |
| mobile `npm run typecheck` | PASS |
| mobile `npm run lint` | PASS com `--max-warnings=0` |
| mobile `npm run test` | PASS — 17 testes |
| `./scripts/e2e-web.sh` | PASS — Playwright Chromium 1/1 |
| `bash -n` nos scripts alterados | PASS |
| compose config production | PASS |
| compose config VPS | PASS |
| `git diff --check` | PASS |

### Observações da execução

- a primeira tentativa local do Maven dentro do sandbox falhou porque o Mockito não pôde criar
  o agente temporário; a mesma suíte foi repetida fora dessa restrição e passou;
- a primeira execução E2E não encontrou Node 20 local; o script foi melhorado para usar uma
  versão já instalada via NVM e o Playwright passou;
- a renda de onboarding pode começar no mês seguinte quando o dia de vencimento do mês atual já
  passou; o teste valida presença positiva no horizonte de dois meses;
- warnings de lint do frontend já existiam e não são erros do gate.

---

## 6. Decisões importantes para um novo chat

1. Não editar ou renomear `V30`; a correção é exclusivamente `V31`.
2. `status` é a fonte canônica; `ativa` existe só para compatibilidade.
3. Exclusão com reserva é HTTP 422, não HTTP 400.
4. Arquivadas são imutáveis/read-only nos clientes e no backend.
5. Concluídas permitem edição e resgate, mas não aporte nem exclusão.
6. O backup financeiro continua em `backup-db.sh`; orquestração Docker pertence ao host em
   `run-backup.sh`.
7. Não montar Docker socket no container de backup.
8. Não marcar `PROB-0081` como corrigido usando remote local, apenas tamanho ou simulação.
9. O restore drill canônico usa PostgreSQL 17.
10. As alterações atuais ainda precisam ser revisadas/stageadas/commitadas conforme o fluxo da
    equipe; nenhum commit foi criado por esta execução.

---

## 7. Estado final dos critérios de aceite

| Critério | Estado |
|---|---|
| Backend verde | PASS |
| Web verde | PASS |
| Mobile verde | PASS |
| PostgreSQL real V30/V31/LGPD/backfills | PASS |
| Playwright executado localmente | PASS |
| Playwright automático no CI | IMPLEMENTADO |
| Integridade de meta após edição | PASS |
| Mobile sem ações proibidas | PASS |
| Exportação com status canônico e flag legada | PASS |
| Scripts shell, compose e diff | PASS |
| Backup com remote externo real e restore/download completo | PENDENTE EXTERNO |

Resultado global: `PASS_COM_RESSALVA`, exclusivamente pela evidência operacional externa ainda
não executada. O código não deve ocultar essa ressalva.

---

## 8. Pendência operacional obrigatória — PROB-0081

Executar em ambiente autorizado com chave pública real e remote externo real:

```bash
cd /opt/gestor-financeiro
BACKUP_COMPOSE_FILE=docker-compose.production.yml \
BACKUP_GPG_RECIPIENT='<recipient-real>' \
RCLONE_REMOTE='<remote-externo>:gf-backups/prod' \
./scripts/run-backup.sh
```

Coletar evidência de:

1. API saudável antes da janela;
2. API efetivamente parada durante DB/uploads;
3. bundle criptografado enviado ao provider externo;
4. `rclone check --download` aprovado;
5. API novamente saudável;
6. download do bundle a partir do remote externo;
7. `scripts/restore-drill-db.sh` em PostgreSQL 17 limpo;
8. checksums internos aprovados;
9. caminhos e arquivos de anexos aprovados;
10. API apontada para o restore e download HTTP autenticado de um anexo íntegro.

Somente depois dessas dez evidências:

- atualizar `docs/PROBLEM_LEDGER.md` com data, comandos e resultados;
- alterar `PROB-0081` de `REABERTO` para `CORRIGIDO`;
- registrar qual provider/remote foi usado sem expor credenciais.

---

## 9. Prompt sugerido para retomar em um novo chat

```text
Leia docs/REVIEW_REPORTS/2026-07-15_full-system_implementacao_correcao-auditoria-fase-1.md.
As correções da Fase 1 estão implementadas e os gates locais passaram. Revise o worktree atual,
não altere V30 e não feche PROB-0081 sem o drill off-host real. Continue a partir das pendências
e decisões documentadas, preservando as mudanças existentes.
```

---

## 10. Recomendação final

Revisar o diff, criar o commit/PR da Fase 1 e agendar o drill externo. O merge pode considerar
os gates de código verdes, mas o readiness operacional de backup permanece condicionado à
evidência real descrita acima.
