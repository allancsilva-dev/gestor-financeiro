# PR-F2-19 — contract de cartões e V41 (implementação local)

**Data:** 2026-07-16

**Branch:** `pr-f2-19-contract-migracao-segura`

**Base:** `f3f9d18`

## Resultado

O contract canônico foi implementado no backend, web e mobile. `Conta` passou a ser somente a
configuração interna de cartão, pareada 1:1 com `Carteira PASSIVO/CARTAO`; o saldo devedor vem do
ledger. Controllers, DTOs, serviços e enums públicos dos modelos genéricos antigos foram removidos.

Contratos públicos finais:

- cartões em `/api/v1/cartoes`;
- contas financeiras em `/api/v1/contas-financeiras`;
- faturas em `/api/v1/faturas/cartao/{cartaoId}`;
- transações com `cartaoId` na entrada e `cartao` na resposta;
- onboarding com objeto `cartao` e `carteira.subtipo`;
- aliases antigos de cartão rejeitados com HTTP 400 e rotas removidas com HTTP 404.

A V41 preserva os nomes físicos `contas` e `carteiras`, migra configurações não-cartão pelos
caminhos `TRANSACAO`, `REUSO_NOME` ou `CRIADA`, executa guards de saldo, ledger, fatura, ownership,
pareamento e contagens, compara as nove métricas em três momentos e remove as quatro colunas
legadas. O teste `ContractV41MigrationIT` parte de V40 real e cobre os três caminhos, ambiguidades,
ownership divergente, saldo legado, ledger, passivo, pareamento e tipo desconhecido, comprovando
rollback integral nos cenários negativos.

Os scripts pre/postflight agora exigem diretório explícito fora do repositório. O preflight gera
mapeamento previsto, manifesto e checksums; o postflight recebe o diretório preflight e falha por
divergência de métricas, contagens, saldos preexistentes ou quantidade prevista de contas.

## Evidências locais

- backend `./mvnw -q test`: PASS;
- PostgreSQL/Testcontainers V40→V41 e schema vazio: PASS;
- web lint, testes, build e Playwright onboarding: PASS;
- mobile typecheck, lint, Jest e Expo Doctor: PASS (`18/18`);
- npm audit: web sem vulnerabilidades; mobile sem alta/crítica (15 moderadas, 1 baixa);
- OWASP Dependency-Check com limite CVSS 7: PASS;
- scripts `bash -n` e `git diff --check`: PASS;
- buscas estáticas: nenhuma chamada HTTP aos contratos removidos; `contaId` residual pertence
  somente ao domínio legítimo de relatório.

## Gate operacional aberto

O `PROB-0081` permanece **REABERTO**. Este ambiente não possui remote off-host, configuração rclone
nem destinatário/chave pública GPG sob o modelo de custódia exigido. Não houve backup off-host,
restore drill externo, dry-run em snapshot de produção, implantação, postflight de produção ou
reinstalação da aplicação mobile. Portanto a V41 não está autorizada para produção e o PR-F2-19
não deve ser aceito/mergeado como concluído até o gate operacional ser comprovado.

O stash `WIP PR-F2-20 reconciliacao global antes PR-F2-16A` permaneceu intacto e não foi aplicado.
