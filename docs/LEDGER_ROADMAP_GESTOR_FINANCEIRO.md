# Ledger — Roadmap Técnico de Evolução do Gestor Financeiro

**Projeto:** Gestor Financeiro  
**Arquivo:** `LEDGER_ROADMAP_GESTOR_FINANCEIRO.md`  
**Objetivo:** definir, em ordem de PRs, tudo que precisa mudar para substituir o modelo frágil de saldo mutável por um modelo financeiro rastreável, auditável e preparado para crescimento.  
**Status:** plano técnico para execução.  
**Escopo central:** carteira, saldo, transações, parcelas, contas fixas, metas, faturas, projeção, testes reais em PostgreSQL, contrato de API e regras de integridade.

---

## 1. Decisão central

O sistema deve evoluir para um modelo baseado em **Ledger**, aqui chamado de `MovimentoCarteira` ou `LedgerEntry`.

Hoje, o risco estrutural é tratar saldo como estado mutável direto. Operações como adicionar dinheiro, remover dinheiro, criar transação, pagar parcela, atualizar meta, pagar fatura ou excluir registro financeiro podem alterar números acumulados diretamente. Mesmo com `@Transactional` e `@Version`, isso ainda deixa o domínio frágil, porque cada service precisa lembrar manualmente como compensar o saldo.

A decisão correta é:

> Nenhum saldo financeiro deve mudar sem gerar um movimento rastreável.

A carteira pode continuar tendo campo `saldo` por performance, mas esse campo passa a ser **saldo materializado**, não a fonte absoluta da verdade. A fonte de verdade passa a ser o conjunto de movimentos financeiros vinculados à carteira.

---

## 2. Diferença entre modelo atual e modelo com Ledger

### 2.1 Modelo atual

No modelo atual, a aplicação tende a operar assim:

```text
Operação financeira → altera saldo da Carteira / Conta / Meta → salva entidade
```

Problemas desse modelo:

- o motivo da mudança de saldo não fica completo e padronizado;
- edição ou exclusão exige compensação manual;
- retry de rede pode duplicar impacto;
- falha parcial pode deixar estado inconsistente;
- bugs de saldo são difíceis de explicar;
- fatura, parcela, recorrência e importação bancária ficam acopladas a regras duplicadas;
- não existe base confiável para reconciliação, auditoria ou IA financeira.

### 2.2 Modelo recomendado

No modelo com Ledger, a aplicação passa a operar assim:

```text
Operação financeira → cria MovimentoCarteira imutável → atualiza saldo materializado da Carteira dentro da mesma transação
```

O saldo da carteira pode ser consultado rapidamente, mas sempre deve ser possível explicar esse saldo pela soma dos movimentos.

```text
Carteira.saldo ≈ SUM(MovimentoCarteira.valor_assinado)
```

O símbolo `≈` é usado porque pode haver movimento inicial de abertura ou backfill. Após a implantação correta, a reconciliação deve provar igualdade exata entre saldo materializado e saldo derivado do ledger para cada carteira.

---

## 3. O que a IA precisa analisar antes de mudar qualquer código

Antes de qualquer PR que toque saldo, transação, parcela, fatura, conta fixa ou meta, a IA executora deve fazer uma auditoria read-only.

Ela deve responder objetivamente:

1. Quais entidades possuem campo monetário acumulado?
2. Quais services alteram saldo, gasto, valor acumulado, valor pago ou status financeiro?
3. Quais endpoints disparam essas alterações?
4. Quais métodos usam `findById()` sem ownership?
5. Quais métodos de escrita estão sem `@Transactional`?
6. Quais fluxos editam ou deletam registros financeiros?
7. Quais operações recalculam dashboard, projeção ou fatura?
8. Quais testes existem para sucesso, erro, concorrência e reversão?
9. Quais migrations já existem e qual a próxima versão livre?
10. O banco real PostgreSQL já foi validado com Flyway em banco limpo?

A IA não deve implementar nada se não conseguir mapear os pontos acima. Nesse caso, o PR deve parar como `BLOCKED_AUDITORIA_INCOMPLETA`.

---

## 4. Regra de execução por PR

Cada PR deve seguir esta estrutura obrigatória:

1. Declarar objetivo.
2. Declarar escopo permitido.
3. Declarar escopo proibido.
4. Auditar estado atual antes de alterar código.
5. Mapear arquivos afetados.
6. Implementar a menor mudança coesa.
7. Criar ou ajustar testes.
8. Validar com comandos reais.
9. Atualizar documentação.
10. Registrar alteração no `BUGFIX_LOG.md` quando corrigir problema existente.
11. Não marcar como `PASS` sem evidência.

Mudança financeira sem teste não deve ser aceita.

---

## 5. Ordem correta dos PRs

A sequência abaixo evita refatoração profunda depois. Não pule etapas.

---

# PR-LEDGER-00 — Auditoria read-only do domínio financeiro

**Status de execução:** `PASS` em 2026-07-08.
**Evidência:** auditoria registrada em `CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md`.
**Resultado:** `APTO_PARA_PR_LEDGER_01`.
**Próxima migration livre identificada na auditoria:** `V11` — consumida pelo PR-LEDGER-02. Próxima atual: `V12`.

## Objetivo

Mapear todos os pontos do sistema que criam, alteram, removem ou projetam dinheiro antes de introduzir o Ledger.

## Escopo permitido

Somente leitura. A IA pode abrir arquivos, buscar referências, gerar relatório e listar riscos.

## Escopo proibido

Não alterar código, migration, testes, configuração, package, documentação existente ou banco.

## O que analisar

A IA deve mapear pelo menos:

- `Carteira`;
- `Conta`;
- `Transacao`;
- `Parcela`;
- `ContaFixa`;
- `Meta`;
- `Fatura`;
- `Dashboard`;
- `Relatorio`;
- `Export`;
- controllers que expõem operações financeiras;
- repositories que buscam por ID;
- services que alteram valores;
- migrations existentes;
- testes existentes.

## Entrega esperada

Um relatório com:

- lista de arquivos lidos;
- tabela de fluxos que alteram dinheiro;
- tabela de endpoints impactados;
- tabela de métodos que precisam gerar movimento;
- lista de riscos antes da mudança;
- próxima versão de migration disponível;
- recomendação final: `APTO_PARA_PR_LEDGER_01` ou `BLOCKED`.

## Critério de aceite

O PR só passa se ficar claro onde o saldo é alterado hoje e quais fluxos serão migrados para Ledger.

---

# PR-LEDGER-01 — Testcontainers PostgreSQL e validação real de migrations

**Status de execução:** `PASS_COM_RESSALVA` em 2026-07-08.
**Evidência:** `backend/pom.xml`, `PostgresMigrationIT`, `application-postgres-it.properties` e CI com `mvn verify -Pintegration-test`.
**Validação VPS:** concluída em 2026-07-08 com usuário `dbnexos_gestor`; PostgreSQL 17.10; Flyway validou 14 migrations; Hibernate `ddl-auto=validate` inicializou.
**Ressalva:** execução local do Testcontainers não foi concluída porque o Docker daemon estava desligado (`Cannot connect to the Docker daemon`). Unitários passaram: `./mvnw -q test` -> PASS. Testcontainers local segue opcional para CI/dev com Docker.

## Objetivo

Garantir que toda mudança de Ledger rode contra PostgreSQL real, com Flyway aplicando migrations em banco limpo.

## Justificativa

O sistema financeiro não deve validar schema, constraints, locking e queries críticas apenas com H2. O Ledger depende de integridade transacional e constraints reais. Portanto, antes de criar a tabela de movimentos, o projeto precisa de teste de integração com PostgreSQL real.

## Escopo permitido

- adicionar Testcontainers para PostgreSQL;
- criar profile de teste de integração;
- fazer Flyway rodar em banco limpo durante teste;
- validar startup da aplicação contra schema versionado;
- manter testes unitários leves quando fizer sentido.

## Escopo proibido

- alterar regra de negócio financeira;
- criar `MovimentoCarteira`;
- mudar endpoints;
- alterar saldo ou fatura.

## Como deve ser feito

Criar configuração de teste com PostgreSQL via Testcontainers. O teste mínimo precisa subir o contexto Spring, aplicar Flyway e validar que o Hibernate aceita o schema com `ddl-auto=validate`.

A suíte deve separar claramente:

```text
Unit tests → rápidos, sem container
Integration tests → PostgreSQL real + Flyway
```

## Arquivos prováveis

- `pom.xml`;
- `src/test/...`;
- `application-test.properties` ou profile equivalente;
- configuração base de teste;
- pipeline de CI, se já existir.

## Testes mínimos

- aplicação sobe com PostgreSQL limpo;
- Flyway aplica todas as migrations;
- Hibernate valida o schema;
- uma operação simples de repository funciona no PostgreSQL.

## Critério de aceite

- testes de integração executam com PostgreSQL real;
- migrations são exercitadas em ambiente limpo;
- H2 não é mais usado como prova de integridade financeira;
- nenhuma mudança de regra de negócio foi feita nesse PR.

---

# PR-LEDGER-02 — Schema do Ledger e modelo `MovimentoCarteira`

**Status de execução:** `PASS_COM_RESSALVA` em 2026-07-08.
**Evidência:** `V11__movimento_carteira.sql`, entidade `MovimentoCarteira`, repository e testes registrados em `CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md`.
**Validação VPS:** concluída em 2026-07-08. BUG-0010 corrigiu divergência `movimentos_carteira.moeda` (`CHAR(3)` no PostgreSQL, mapeamento JPA antes validando como `VARCHAR(3)`).
**Ressalva:** Testcontainers local não rodou porque Docker daemon estava desligado. Validação PostgreSQL real foi coberta pelo smoke VPS.
**Próxima migration livre atual:** `V12`.

## Objetivo

Criar a estrutura persistente do Ledger sem ainda trocar todos os fluxos de negócio.

## Decisão de modelagem

Criar uma entidade imutável chamada `MovimentoCarteira` ou `LedgerEntry`.

Nome recomendado para o projeto atual: `MovimentoCarteira`, porque conversa melhor com o domínio já existente. Internamente, a documentação pode citar Ledger.

## Campos mínimos

```text
id
usuario_id
carteira_id
tipo
valor
valor_assinado
origem
referencia_tipo
referencia_id
descricao
data_movimento
saldo_resultante
idempotency_key
created_at
```

## Campos explicados

`usuario_id` mantém o modelo single-user atual e garante ownership direto.

`carteira_id` define qual carteira foi impactada.

`tipo` representa a direção ou natureza do movimento. Exemplos:

```text
ENTRADA
SAIDA
AJUSTE_MANUAL
TRANSFERENCIA_ENTRADA
TRANSFERENCIA_SAIDA
ESTORNO
```

`valor` guarda sempre o valor absoluto positivo.

`valor_assinado` guarda o impacto matemático no saldo. Entrada positiva, saída negativa.

`origem` identifica de onde veio o movimento. Exemplos:

```text
CARTEIRA_AJUSTE
TRANSACAO
PARCELA
CONTA_FIXA
FATURA_CARTAO
META
TRANSFERENCIA
BACKFILL
```

`referencia_tipo` e `referencia_id` ligam o movimento ao registro de origem sem obrigar FK polimórfica rígida.

`saldo_resultante` grava o saldo da carteira depois do movimento. Isso facilita auditoria e explicação ao usuário.

`idempotency_key` prepara o backend contra retry, duplo clique e repetição de requisição.

## Constraints obrigatórias

- `valor > 0`;
- `valor_assinado <> 0`;
- `carteira_id` obrigatório;
- `usuario_id` obrigatório;
- FK para carteira;
- índice por `usuario_id, carteira_id, data_movimento`;
- índice por `origem, referencia_tipo, referencia_id`;
- unique parcial ou composta para idempotência quando `idempotency_key` existir.

## Sobre moeda

Adicionar `moeda CHAR(3) DEFAULT 'BRL'` é recomendado neste PR ou no PR imediatamente seguinte. Mesmo que o sistema comece em BRL, registrar moeda nos movimentos evita backfill doloroso no futuro.

## Escopo proibido

- não alterar cálculo de saldo ainda;
- não refatorar fatura;
- não mudar projeção;
- não mexer no frontend;
- não alterar contrato público sem necessidade.

## Testes mínimos

- migration cria a tabela;
- constraints rejeitam valor zero ou negativo;
- FK rejeita carteira inexistente;
- repository salva movimento válido;
- repository lista movimentos por usuário e carteira.

## Critério de aceite

O schema do Ledger existe, está versionado por Flyway e não quebrou nenhum fluxo atual. Para fechamento sem ressalva, `PostgresMigrationIT` precisa passar em ambiente com Docker ativo.

---

# PR-LEDGER-03 — `LedgerService` e escrita atômica de movimento + saldo materializado

**Status de execução:** `PASS_COM_RESSALVA` em 2026-07-08.
**Pré-condição:** `PR-LEDGER-02` concluído e validado contra PostgreSQL VPS real em 2026-07-08.
**Evidência:** `LedgerService`, `RegistrarMovimentoCommand`, lock pessimista em `CarteiraRepository`, `CarteiraService` usando Ledger para saldo e `LedgerServiceTest`.
**Ressalva:** Testcontainers PostgreSQL não rodou localmente porque Docker daemon estava desligado; smoke VPS validou Flyway/schema real.

## Objetivo

Criar o serviço central responsável por registrar movimento financeiro e atualizar saldo da carteira na mesma transação.

## Decisão técnica

Nenhum service de domínio deve alterar `Carteira.saldo` diretamente depois deste PR. Toda alteração deve passar por um método central do Ledger.

Exemplo conceitual:

```java
@Transactional
public MovimentoCarteira registrarMovimento(RegistrarMovimentoCommand command) {
    Carteira carteira = carteiraRepository.findByIdAndUsuarioIdForUpdate(
        command.carteiraId(),
        command.usuarioId()
    ).orElseThrow(...);

    BigDecimal novoSaldo = carteira.getSaldo().add(command.valorAssinado());
    carteira.setSaldo(novoSaldo);

    MovimentoCarteira movimento = MovimentoCarteira.criar(
        command,
        novoSaldo
    );

    movimentoRepository.save(movimento);
    carteiraRepository.save(carteira);
    return movimento;
}
```

A implementação pode usar optimistic locking já existente ou lock pessimista pontual no repository. Para saldo financeiro, a decisão precisa ser explícita. Se o projeto já usa `@Version`, manter optimistic locking é aceitável desde que conflito retorne 409 e seja testado. Para operações de alta concorrência, considerar `SELECT ... FOR UPDATE` na carteira.

## Escopo permitido

- criar `LedgerService`;
- criar command/DTO interno para registro de movimento;
- centralizar cálculo de `valor_assinado`;
- centralizar atualização de saldo materializado;
- tratar conflito de concorrência;
- criar exceções de domínio específicas.

## Escopo proibido

- migrar todos os fluxos ainda;
- mudar endpoints;
- criar telas;
- refatorar fatura/projeção.

## Regras obrigatórias

- `valor` sempre positivo;
- `valor_assinado` calculado pelo domínio, não recebido livremente do frontend;
- movimento financeiro é append-only;
- movimento não deve ser editado;
- correção deve ser feita por estorno ou novo ajuste;
- ownership deve ser validado por `usuario_id`;
- toda escrita deve ser `@Transactional`;
- erro de concorrência deve virar 409 padronizado.

## Testes mínimos

- registrar entrada aumenta saldo;
- registrar saída reduz saldo;
- saída acima do saldo deve seguir a regra atual do produto: bloquear ou permitir saldo negativo explicitamente;
- tentativa com carteira de outro usuário retorna erro;
- concorrência em duas saídas simultâneas não corrompe saldo;
- movimento salvo contém `saldo_resultante` correto.

## Critério de aceite

Existe um único caminho técnico aprovado para alterar saldo de carteira.

---

# PR-LEDGER-04 — Reconciliação de saldo

## Objetivo

Criar mecanismo para comparar o saldo materializado da carteira com o saldo derivado dos movimentos.

## Por que vem antes da migração dos fluxos

Antes de trocar transação, parcela, fatura e meta, o sistema precisa conseguir detectar divergência. Sem reconciliação, bugs continuam silenciosos.

## Escopo permitido

- criar serviço de reconciliação;
- criar query agregada por carteira;
- criar endpoint administrativo ou comando interno de validação;
- criar teste de divergência;
- criar logs seguros, sem PII desnecessária.

## Escopo proibido

- não corrigir automaticamente divergência em produção sem confirmação;
- não recalcular saldo de todos os usuários em rotina automática sem plano;
- não expor dados financeiros em log.

## Resultado esperado

A reconciliação deve responder:

```text
carteira_id
usuario_id
saldo_materializado
saldo_ledger
diferenca
status: OK | DIVERGENTE
```

## Testes mínimos

- carteira sem movimentos retorna status esperado;
- carteira com movimentos consistentes retorna OK;
- carteira com saldo adulterado artificialmente retorna DIVERGENTE;
- query roda no PostgreSQL real.

## Critério de aceite

A aplicação consegue provar se o saldo materializado bate com o Ledger.

---

# PR-LEDGER-05 — Backfill inicial de movimentos para carteiras existentes

## Objetivo

Gerar movimentos iniciais para carteiras já existentes, preservando o saldo atual sem tentar reconstruir histórico que não existe.

## Decisão importante

Se o sistema já tem dados, não é seguro inventar histórico retroativo. O correto é criar um movimento de abertura:

```text
origem = BACKFILL
referencia_tipo = CARTEIRA
referencia_id = carteira_id
valor_assinado = saldo_atual
saldo_resultante = saldo_atual
```

Se o saldo atual for positivo, o movimento é entrada de abertura. Se for negativo e o produto permitir saldo negativo, o movimento é saída/ajuste de abertura. Se o produto não permitir saldo negativo, a auditoria deve bloquear e exigir decisão manual.

## Escopo permitido

- migration ou rotina idempotente de backfill;
- registrar movimento inicial por carteira existente;
- garantir que a rotina não duplica movimentos;
- validar reconciliação após backfill.

## Escopo proibido

- tentar recriar todo histórico antigo a partir de transações;
- alterar saldo atual;
- apagar transações antigas;
- modificar faturas ou parcelas.

## Idempotência obrigatória

O backfill precisa ser seguro para rodar mais de uma vez. Deve haver constraint ou verificação que impeça dois movimentos `BACKFILL` para a mesma carteira.

## Testes mínimos

- carteira existente recebe um único movimento de abertura;
- rodar backfill duas vezes não duplica;
- saldo após backfill reconcilia;
- carteira de outro usuário não é misturada.

## Critério de aceite

Todas as carteiras existentes passam a ter base inicial no Ledger sem alteração de saldo.

---

# PR-LEDGER-06 — Carteira: substituir adicionar/remover saldo direto por ajuste manual via Ledger

## Objetivo

Migrar operações diretas de carteira para movimentos do tipo `AJUSTE_MANUAL`.

## Mudança de comportamento interno

Antes:

```text
POST /carteiras/{id}/adicionar → Carteira.saldo += valor
POST /carteiras/{id}/remover → Carteira.saldo -= valor
```

Depois:

```text
POST /carteiras/{id}/adicionar → LedgerService.registrarMovimento(AJUSTE_MANUAL, +valor)
POST /carteiras/{id}/remover → LedgerService.registrarMovimento(AJUSTE_MANUAL, -valor)
```

O contrato externo pode ser mantido temporariamente para não quebrar frontend/mobile, mas a implementação interna deve mudar.

## Recomendação de contrato futuro

Criar endpoint mais explícito:

```text
POST /api/v1/carteiras/{id}/ajustes
```

Payload:

```json
{
  "tipo": "ENTRADA" ou "SAIDA",
  "valor": 100.00,
  "descricao": "Correção manual de saldo"
}
```

Os endpoints antigos podem virar alias deprecado por uma versão.

## Escopo permitido

- alterar `CarteiraService` para usar `LedgerService`;
- manter compatibilidade com endpoints existentes;
- adicionar idempotência se o padrão já estiver disponível;
- atualizar testes de carteira.

## Escopo proibido

- migrar transação, parcela, fatura ou meta neste PR;
- mudar layout web/mobile;
- remover endpoints antigos sem plano de compatibilidade.

## Testes mínimos

- adicionar dinheiro cria movimento e aumenta saldo;
- remover dinheiro cria movimento e reduz saldo;
- carteira de outro usuário retorna erro;
- erro de concorrência retorna 409;
- reconciliação continua OK após ajustes.

## Critério de aceite

Não existe mais alteração manual de saldo de carteira fora do Ledger.

---

# PR-LEDGER-07 — Transações: criação, edição e exclusão com Ledger

## Objetivo

Fazer `Transacao` impactar saldo por movimentos rastreáveis.

## Fluxos que precisam ser tratados

### Criar transação

Entrada:

```text
cria Transacao → cria MovimentoCarteira positivo
```

Saída:

```text
cria Transacao → cria MovimentoCarteira negativo
```

### Editar transação

Editar transação não deve sobrescrever movimento antigo. O padrão correto é compensar a diferença.

Exemplo:

```text
Transação original: SAIDA 100
Transação editada: SAIDA 150
Movimento novo: SAIDA 50
```

Se mudar de saída para entrada:

```text
Transação original: SAIDA 100
Transação editada: ENTRADA 100
Movimento compensatório: ENTRADA 200
```

### Excluir transação

Excluir deve gerar estorno, não apagar o impacto histórico.

```text
Transação SAIDA 100 excluída → Movimento ESTORNO +100
```

A transação pode ser marcada como cancelada/inativa, mas o movimento original permanece.

## Decisão recomendada

Evitar hard delete para transações que já impactaram saldo. Preferir status:

```text
ATIVA
CANCELADA
```

Ou campo equivalente `ativa=false`, desde que o histórico fique claro.

## Escopo permitido

- alterar `TransacaoService`;
- criar movimentos para criar/editar/cancelar;
- impedir hard delete com impacto financeiro;
- atualizar testes;
- atualizar documentação de contrato se houver mudança.

## Escopo proibido

- refatorar parcelas neste PR, exceto se transação parcelada já for criada no mesmo fluxo;
- alterar dashboard sem necessidade;
- alterar fatura.

## O que a IA deve analisar antes

- como transação parcelada é criada hoje;
- se transação tem carteira obrigatória ou conta/cartão;
- se transação de cartão impacta carteira imediatamente ou apenas fatura;
- se exclusão atual remove parcelas junto;
- se dashboard conta transações canceladas.

## Testes mínimos

- criar entrada aumenta saldo;
- criar saída reduz saldo;
- editar valor gera movimento de diferença;
- editar tipo gera compensação correta;
- cancelar gera estorno;
- não é possível usar categoria/conta/carteira de outro usuário;
- reconciliação continua OK.

## Critério de aceite

Transação deixa rastro financeiro completo e reversível.

---

# PR-LEDGER-08 — Parcelas e compras parceladas com impacto financeiro correto

## Objetivo

Garantir que parcelas impactem o Ledger somente no momento correto, sem duplicar saída.

## Decisão que precisa ser tomada antes da implementação

A IA deve identificar o modelo atual:

1. A transação parcelada já reduz o saldo total no momento da compra?
2. Cada parcela reduz saldo quando marcada como paga?
3. Parcela é apenas agenda futura ou é lançamento efetivo?
4. Compra parcelada no cartão entra na fatura ou na carteira?

Sem essa resposta, não implementar.

## Modelo recomendado

Para gestor financeiro pessoal, existem dois casos:

### Parcela fora do cartão

A parcela deve ser uma obrigação futura. Ela só impacta saldo quando for paga ou confirmada, dependendo da regra do produto.

```text
Criar compra parcelada → cria parcelas futuras, sem reduzir saldo total imediatamente
Pagar parcela → cria MovimentoCarteira de saída
Desmarcar pagamento → cria estorno
```

### Parcela no cartão de crédito

A parcela não deve reduzir a carteira no dia da compra. Ela compõe fatura. O impacto na carteira acontece quando a fatura é paga.

```text
Compra parcelada no cartão → lançamento em faturas futuras
Pagar fatura → saída única da carteira
```

## Escopo permitido

- corrigir impacto de pagamento/despagamento de parcela;
- gerar movimento via Ledger;
- evitar duplicidade;
- atualizar status de parcela de forma transacional;
- criar testes de simetria.

## Escopo proibido

- modelar fatura completa se ainda não for este PR;
- alterar UX;
- misturar regra de cartão com parcela comum sem decisão explícita.

## Testes mínimos

- pagar parcela gera saída uma vez;
- pagar a mesma parcela duas vezes não duplica movimento;
- desmarcar pagamento gera estorno;
- editar parcela paga exige compensação correta ou bloqueio;
- reconciliação continua OK.

## Critério de aceite

Parcelas deixam de ser fonte de duplicidade ou esquecimento de saldo.

---

# PR-LEDGER-09 — Contas fixas e recorrências reais

## Objetivo

Separar previsão recorrente de pagamento efetivo e garantir que apenas pagamento confirmado gere movimento no Ledger.

## Problema a evitar

Conta fixa não deve ser apenas um registro que muda status mensalmente sem histórico. Isso destrói projeção e auditoria.

## Modelo recomendado

```text
ContaFixa → regra recorrente
OcorrenciaContaFixa → ocorrência mensal prevista
Pagamento da ocorrência → MovimentoCarteira
```

Se o projeto ainda não tiver `OcorrenciaContaFixa`, o PR pode implementar uma versão mínima ou, no mínimo, garantir que o pagamento de conta fixa gere movimento rastreável com referência ao registro pago.

## Escopo permitido

- ajustar pagamento de conta fixa para Ledger;
- impedir pagamento duplicado;
- criar estorno ao desfazer pagamento;
- preservar histórico mensal;
- preparar projeção futura.

## Escopo proibido

- criar motor complexo de recorrência anual/semanal se não for necessário agora;
- alterar dashboard completo;
- criar IA financeira.

## Testes mínimos

- pagar conta fixa gera saída;
- pagar novamente não duplica;
- desfazer pagamento gera estorno;
- conta fixa de outro usuário é bloqueada;
- projeção diferencia previsto de pago.

## Critério de aceite

Conta fixa passa a ter impacto financeiro auditável e previsível.

---

# PR-LEDGER-10 — Metas financeiras com Ledger ou subledger próprio

## Objetivo

Padronizar depósitos e retiradas de metas para que também sejam rastreáveis.

## Decisão de domínio

Meta pode ser tratada de duas formas:

1. Como apenas objetivo lógico, sem mover dinheiro real.
2. Como reserva real vinculada a uma carteira.

A IA deve analisar o comportamento atual antes de mudar. Se `Meta.adicionarValor()` hoje altera apenas valor acumulado da meta, mas não mexe na carteira, então não deve inventar saída de carteira sem decisão de produto.

## Modelo recomendado

Para MVP, usar subledger de meta é suficiente:

```text
MovimentoMeta
meta_id
usuario_id
tipo
valor
origem
created_at
```

Mas se a meta representar dinheiro separado de uma carteira, então o movimento de meta precisa ser acoplado a uma transferência de carteira.

## Escopo permitido

- tornar adição/remoção de valor da meta rastreável;
- impedir valor acumulado negativo se a regra exigir;
- gerar histórico de meta;
- manter compatibilidade com UI.

## Escopo proibido

- misturar meta com carteira sem decisão explícita;
- criar investimento;
- criar recomendação automática.

## Testes mínimos

- adicionar valor registra histórico;
- remover valor registra histórico;
- operação inválida não altera metade do estado;
- meta de outro usuário é bloqueada.

## Critério de aceite

Meta passa a ser explicável historicamente.

---

# PR-LEDGER-11 — Fatura de cartão idempotente e sem write-on-GET

## Objetivo

Corrigir fatura para que ela não seja criada de forma insegura ao consultar dados e para que o pagamento da fatura gere movimento financeiro correto.

## Problema

Criar fatura automaticamente em GET é um `write-on-read`. Isso quebra expectativa de idempotência, gera risco de duplicidade e cria comportamento difícil de testar.

## Mudança recomendada

- `GET` apenas consulta;
- criação de fatura deve ocorrer por comando explícito ou rotina idempotente;
- banco deve ter constraint única por cartão/competência;
- pagamento de fatura deve gerar um único movimento de saída no Ledger;
- reabrir/despagar fatura deve gerar estorno ou bloquear conforme regra.

## Constraint obrigatória

```text
UNIQUE(conta_id, mes, ano)
```

Se houver ciclo real de cartão, preferir competência baseada em fechamento/vencimento, não apenas mês-calendário.

## Campos necessários em cartão de crédito

A IA deve verificar se existem:

```text
dia_fechamento
dia_vencimento
limite
```

Sem `dia_fechamento` e `dia_vencimento`, fatura por mês-calendário fica errada para cartões reais.

## Escopo permitido

- remover criação insegura em GET;
- adicionar constraint única;
- tornar criação de fatura idempotente;
- fazer pagamento passar pelo Ledger;
- ajustar testes.

## Escopo proibido

- criar integração bancária;
- criar Open Finance;
- refatorar toda conta/cartão além do necessário.

## Testes mínimos

- GET não cria fatura;
- duas requisições simultâneas não criam fatura duplicada;
- pagar fatura gera uma saída;
- pagar fatura duas vezes não duplica;
- cancelar pagamento gera estorno ou erro controlado;
- fatura de cartão de outro usuário é bloqueada.

## Critério de aceite

Fatura deixa de ser fonte de duplicidade e passa a impactar saldo de forma rastreável.

---

# PR-LEDGER-12 — Projeção de caixa baseada em eventos previstos e Ledger realizado

## Objetivo

Corrigir projeção para diferenciar dinheiro já realizado de compromissos futuros.

## Problema atual a evitar

Projeção que considera apenas saldo atual, contas fixas pendentes e parcelas futuras é incompleta. Ela precisa considerar também entradas previstas, faturas em aberto e regras de vencimento.

## Modelo recomendado

A projeção deve partir de:

```text
saldo atual materializado
+ entradas previstas
- contas fixas previstas
- parcelas previstas fora do cartão
- faturas previstas/em aberto
+/- outros eventos futuros confirmados
```

O Ledger representa o realizado. A projeção representa o futuro. Não misturar os dois como se fossem a mesma coisa.

## Escopo permitido

- criar DTO tipado para projeção;
- remover `Map<String, Object>` se existir nesse contrato;
- corrigir cálculo mensal;
- incluir entradas recorrentes previstas se já existirem no onboarding/domínio;
- incluir faturas no mês de vencimento;
- criar testes com cenário fechado.

## Escopo proibido

- criar IA preditiva;
- criar recomendação automática;
- criar importação bancária.

## Testes mínimos

- projeção com salário recorrente soma entrada;
- projeção com conta fixa subtrai saída;
- projeção com fatura subtrai no vencimento;
- compra no cartão não é contada duas vezes;
- parcelas futuras aparecem no mês correto;
- cenário conhecido bate exatamente com valor esperado.

## Critério de aceite

A projeção passa a ser numericamente confiável e explicável.

---

# PR-LEDGER-13 — Idempotência em POSTs financeiros

## Objetivo

Impedir duplicidade causada por retry de rede, duplo clique, instabilidade mobile ou reenvio acidental.

## Decisão técnica

Toda operação financeira que cria impacto deve aceitar `Idempotency-Key`.

Endpoints candidatos:

```text
POST /carteiras/{id}/ajustes
POST /transacoes
POST /parcelas/{id}/pagar
POST /contas-fixas/{id}/pagar
POST /faturas/{id}/pagar
POST /metas/{id}/adicionar
POST /metas/{id}/remover
```

## Modelo recomendado

Criar tabela:

```text
idempotency_keys
usuario_id
key
endpoint
request_hash
response_status
response_body_hash
status
created_at
expires_at
```

Para MVP, também é aceitável começar com unique no próprio `MovimentoCarteira.idempotency_key`, desde que cubra as operações críticas. Porém, a tabela dedicada é mais escalável.

## Escopo permitido

- criar filtro/interceptor ou serviço de idempotência;
- aplicar em operações financeiras;
- tratar requisição em andamento;
- retornar resposta consistente para repetição válida;
- rejeitar mesma chave com payload diferente.

## Escopo proibido

- aplicar em todos os endpoints do sistema de uma vez;
- criar cache distribuído sem necessidade imediata;
- mudar UI além de enviar chave.

## Testes mínimos

- mesma chave + mesmo payload não duplica movimento;
- mesma chave + payload diferente retorna conflito;
- duas requisições simultâneas com mesma chave não duplicam;
- movimento financeiro continua reconciliado.

## Critério de aceite

Retry de operação financeira não cria dinheiro duplicado nem saída duplicada.

---

# PR-LEDGER-14 — Ownership centralizado e bloqueio arquitetural contra `findById()` inseguro

## Objetivo

Reduzir o risco de novas falhas IDOR em fluxos financeiros.

## Problema

Ownership manual espalhado depende de disciplina humana. Com o domínio crescendo, algum PR futuro pode voltar a usar `findById()` puro em recurso financeiro.

## Modelo recomendado para agora

Criar padrão obrigatório:

```text
findByIdAndUsuarioId(...)
existsByIdAndUsuarioId(...)
deleteByIdAndUsuarioId(...)
```

E adicionar teste de arquitetura com ArchUnit ou verificação equivalente para impedir uso inseguro em services financeiros.

## Escopo permitido

- criar interface/base repository para entidades owned;
- revisar services financeiros;
- adicionar ArchUnit;
- documentar exceções permitidas.

## Escopo proibido

- introduzir multi-tenant completo;
- criar Workspace/Membership agora;
- refatorar autenticação inteira.

## Testes mínimos

- teste arquitetural falha se service financeiro usar `repository.findById(id)` em entidade owned;
- fluxos existentes continuam passando;
- usuário A não acessa recurso do usuário B.

## Critério de aceite

A segurança de ownership deixa de depender apenas de memória do desenvolvedor.

---

# PR-LEDGER-15 — Política uniforme de arquivamento e cancelamento

## Objetivo

Evitar perda de histórico financeiro por hard delete.

## Regra de produto

Entidade que já foi referenciada por movimento, transação, parcela, fatura ou relatório não deve ser apagada fisicamente.

## Política recomendada

```text
Categoria → arquivar/inativar
Carteira → arquivar se tiver movimento; hard delete só se vazia
Conta → arquivar se tiver histórico
Meta → arquivar/concluir/cancelar
Transacao → cancelar/estornar, não apagar impacto
Fatura → cancelar/reabrir conforme regra, não apagar histórico
```

## Escopo permitido

- padronizar status `ativa`, `arquivada` ou equivalente;
- bloquear hard delete com vínculos;
- retornar erro de domínio claro;
- ajustar frontend se necessário para esconder arquivados por padrão.

## Escopo proibido

- apagar histórico financeiro;
- usar cascade delete em registros com dinheiro;
- quebrar relatórios históricos.

## Testes mínimos

- carteira com movimento não pode ser hard deleted;
- categoria usada em transação é arquivada, não removida;
- transação cancelada mantém rastreabilidade;
- listagens ocultam arquivados por padrão quando aplicável.

## Critério de aceite

Histórico financeiro não é perdido por exclusão operacional.

---

# PR-LEDGER-16 — Dashboard e relatórios reconciliados

## Objetivo

Garantir que dashboard e relatórios usem dados consistentes depois da implantação do Ledger.

## Decisão técnica

Dashboard de saldo atual pode usar `Carteira.saldo` materializado. Relatórios de movimentação devem usar o Ledger quando a pergunta for fluxo financeiro real.

Exemplos:

```text
Saldo atual → Carteira.saldo
Extrato da carteira → MovimentoCarteira
Fluxo mensal realizado → MovimentoCarteira por data
Gastos por categoria → Transacao/Fatura conforme regra de competência
Projeção futura → eventos previstos, não Ledger realizado
```

## Escopo permitido

- ajustar queries de dashboard;
- criar endpoint de extrato por carteira;
- paginar movimentos;
- adicionar filtros por período, tipo e origem;
- garantir performance com índices.

## Escopo proibido

- calcular relatório grande em memória;
- expor movimento de outro usuário;
- retornar lista sem paginação.

## Testes mínimos

- extrato pagina corretamente;
- dashboard bate com saldo materializado;
- fluxo realizado bate com movimentos;
- filtros por período funcionam;
- usuário não acessa extrato de carteira alheia.

## Critério de aceite

O usuário consegue entender por que o saldo está naquele valor.

---

# PR-LEDGER-17 — Contrato de API e documentação OpenAPI

## Objetivo

Padronizar contrato após mudanças de Ledger e remover ambiguidades de versão/rotas.

## Decisões obrigatórias

- usar `/api/v1` de forma consistente;
- evitar rotas com `usuarioId` no path para dados do usuário autenticado;
- usar DTOs tipados, não `Map<String, Object>`;
- documentar erros padronizados;
- documentar idempotência em POSTs financeiros;
- gerar contrato via OpenAPI/SpringDoc sempre que possível.

## Rotas recomendadas

```text
GET  /api/v1/carteiras/{id}/movimentos
POST /api/v1/carteiras/{id}/ajustes
GET  /api/v1/carteiras/{id}/reconciliacao
GET  /api/v1/dashboard/projecao
```

Endpoints antigos podem ser mantidos como aliases deprecados temporariamente, se web/mobile ainda dependem deles.

## Escopo permitido

- atualizar OpenAPI;
- atualizar documentação;
- marcar endpoints antigos como deprecated;
- alinhar frontend/mobile com `/api/v1`.

## Escopo proibido

- remover rota usada por cliente sem migração;
- criar versão v2 sem necessidade.

## Testes mínimos

- contrato documenta `Idempotency-Key`;
- endpoints financeiros retornam erro padronizado;
- rotas sem `usuarioId` funcionam pelo token;
- aliases deprecados continuam funcionais quando necessários.

## Critério de aceite

API fica previsível para web, mobile e futuras integrações.

---

# PR-LEDGER-18 — UX de confiança para movimentos financeiros

## Objetivo

Atualizar web/mobile para refletir o novo modelo sem expor complexidade desnecessária.

## Telas impactadas

- carteira;
- transações;
- parcelas;
- contas fixas;
- faturas;
- metas;
- dashboard;
- projeção;
- relatórios.

## Mudanças esperadas

- botão financeiro desabilita durante envio;
- cliente envia `Idempotency-Key`;
- conflitos 409 mostram mensagem compreensível;
- extrato da carteira mostra movimentos;
- cancelamento/estorno tem confirmação;
- tela mostra estado vazio, loading e erro;
- valores de saldo mostram última atualização quando útil.

## Escopo proibido

- redesenhar todo o produto sem necessidade;
- esconder erro financeiro real com mensagem genérica;
- permitir duplo clique em operação financeira.

## Testes mínimos

- duplo clique não duplica operação;
- erro 409 aparece de forma compreensível;
- extrato lista movimentos paginados;
- cancelamento pede confirmação.

## Critério de aceite

A interface ajuda o usuário a confiar no saldo e entender alterações.

---

# PR-LEDGER-19 — Observabilidade, logs seguros e alertas de divergência

## Objetivo

Detectar problemas financeiros em tempo de execução sem vazar dados sensíveis.

## Escopo permitido

- log estruturado de erro financeiro sem PII sensível;
- métrica de divergência de reconciliação;
- métrica de conflito de concorrência;
- métrica de idempotência reutilizada;
- health check que não exponha dados;
- alerta interno para divergência crítica.

## Escopo proibido

- logar token, senha, e-mail sensível, payload completo financeiro ou dados pessoais desnecessários;
- expor extrato em log;
- corrigir saldo automaticamente sem regra.

## Testes mínimos

- divergência gera evento interno;
- logs não contêm token/senha;
- erro financeiro preserva requestId;
- health check não expõe dados de usuário.

## Critério de aceite

Falhas financeiras deixam de ser silenciosas e continuam seguras.

---

# PR-LEDGER-20 — Fechamento da fundação Ledger

## Objetivo

Executar revisão final da fundação financeira antes de avançar para features de alto nível, IA, importação bancária ou Open Finance.

## Checklist obrigatório

O projeto só pode sair da fase Ledger se todos os itens abaixo estiverem verdadeiros:

```text
[ ] PostgreSQL real validando Flyway nos testes de integração
[x] MovimentoCarteira criado por migration versionada
[ ] Carteira.saldo é saldo materializado
[ ] Nenhum service altera saldo direto fora do LedgerService
[ ] Backfill inicial idempotente executado/testado
[ ] Reconciliação detecta divergência
[ ] Carteira manual usa ajuste via Ledger
[ ] Transação cria/edita/cancela com movimentos corretos
[ ] Parcela paga/despaga sem duplicidade
[ ] Conta fixa paga/despaga com rastreabilidade
[ ] Meta possui histórico coerente
[ ] Fatura não é criada por GET
[ ] Fatura possui constraint única por competência
[ ] Pagamento de fatura gera uma saída rastreável
[ ] Projeção diferencia realizado de previsto
[ ] POSTs financeiros críticos têm idempotência
[ ] Ownership está centralizado ou protegido por teste arquitetural
[ ] Hard delete financeiro está controlado
[ ] Dashboard e relatórios usam fonte correta
[ ] API documentada e versionada
[ ] Web/mobile impedem duplo clique financeiro
[ ] Logs não vazam PII, token ou senha
```

## Status final esperado

```text
PASS — Fundação Ledger concluída
```

Se qualquer item crítico estiver pendente, o status deve ser:

```text
PASS_COM_RESSALVA
```

Se houver risco de corrupção de saldo, duplicidade financeira, IDOR ou migration não validada, o status deve ser:

```text
FAIL
```

---

## 6. Ordem resumida para execução

A ordem recomendada é:

```text
PR-LEDGER-00  Auditoria read-only
PR-LEDGER-01  Testcontainers PostgreSQL + Flyway real
PR-LEDGER-02  Schema MovimentoCarteira
PR-LEDGER-03  LedgerService transacional
PR-LEDGER-04  Reconciliação de saldo
PR-LEDGER-05  Backfill inicial
PR-LEDGER-06  Carteira com ajuste manual via Ledger
PR-LEDGER-07  Transações com Ledger
PR-LEDGER-08  Parcelas com Ledger
PR-LEDGER-09  Contas fixas com Ledger
PR-LEDGER-10  Metas rastreáveis
PR-LEDGER-11  Fatura idempotente sem write-on-GET
PR-LEDGER-12  Projeção de caixa corrigida
PR-LEDGER-13  Idempotência em POSTs financeiros
PR-LEDGER-14  Ownership centralizado / ArchUnit
PR-LEDGER-15  Arquivamento em vez de hard delete
PR-LEDGER-16  Dashboard e relatórios reconciliados
PR-LEDGER-17  Contrato API / OpenAPI
PR-LEDGER-18  UX de confiança
PR-LEDGER-19  Observabilidade segura
PR-LEDGER-20  Revisão final da fundação
```

---

## 7. O que não deve ser feito agora

Não iniciar IA financeira agora. IA em cima de saldo inconsistente apenas gera recomendação errada com aparência inteligente.

Não iniciar Open Finance agora. Importação bancária exige reconciliação e Ledger.

Não iniciar investimentos agora. Investimento exige movimentos, posição, preço, histórico e reconciliação.

Não priorizar deploy público antes de fechar integridade financeira. Deploy de sistema financeiro com risco conhecido de saldo inconsistente gera perda de confiança.

Não refatorar para multiusuário familiar agora. O sistema pode continuar single-user por `usuario_id`, desde que ownership fique centralizado e não bloqueie futura evolução para `finance_profile_id` ou `workspace_id`.

---

## 8. Prompt base para cada PR

Use este bloco como base para executar cada PR com IA/coding agent:

```text
Você é o executor de um único PR do projeto Gestor Financeiro.

PR: <NOME_DO_PR>

Objetivo:
<OBJETIVO_EXATO>

Regras obrigatórias:
1. Leia antes: SYSTEM_OVERVIEW.md, PROBLEM_LEDGER.md, BACKLOG.md, BUGFIX_LOG.md, CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md, LOCAL_POSTGRES_VALIDATION.md e LEDGER_ROADMAP_GESTOR_FINANCEIRO.md.
2. Não implemente nada antes de auditar o estado atual do código.
3. Declare arquivos que serão alterados antes de alterar.
4. Não faça mudança fora do escopo do PR.
5. Toda escrita financeira deve ser transacional.
6. Todo recurso financeiro por ID deve validar ownership.
7. Nenhuma alteração de saldo pode ocorrer fora do LedgerService depois que ele existir.
8. Adicione ou ajuste testes obrigatórios do PR.
9. Execute validações reais e registre comandos/resultados.
10. Atualize documentação e BUGFIX_LOG.md quando aplicável.
11. Não faça commit automático.

Entregue:
- resumo do diagnóstico;
- arquivos alterados;
- decisão técnica tomada;
- testes adicionados;
- comandos executados;
- resultado final: PASS, PASS_COM_RESSALVA, FAIL ou BLOCKED.
```

---

## 9. Definição final de alto nível para este projeto

O Gestor Financeiro só deve ser considerado estruturalmente preparado para crescer quando conseguir responder, com dados rastreáveis:

```text
Por que este saldo existe?
Qual operação mudou este saldo?
Quem era o dono do recurso?
Quando a mudança aconteceu?
Qual registro originou o movimento?
Se houve erro, como detectar divergência?
Se houve retry, por que não duplicou dinheiro?
Se houve cancelamento, onde está o estorno?
```

Enquanto essas perguntas dependerem de leitura manual de services ou de compensações espalhadas, o projeto ainda não tem fundação financeira de alto nível.

A implantação do Ledger é a mudança que transforma o sistema de um CRUD financeiro funcional em um produto financeiro confiável.
