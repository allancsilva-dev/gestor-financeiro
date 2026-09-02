# ADR-0021 — Ingestão automática: o que entra, o que duplica e o que fazer quando diverge

- **Status:** ACEITO
- **Data:** 2026-09-01
- **Relacionado:** Fase 6 (PR-F6-00), ADR-0010 (caixa canônica), ADR-0015 (reconciliação e migração),
  ADR-0019 (conector pelo pipeline canônico), ADR-0009 (ledger)

## Contexto

O pipeline da Fase 4 foi desenhado para **arquivo**, e arquivo é fotografia: o titular escolhe o
momento, revisa a prévia e decide. A Fase 6 troca isso por uma fonte que chega sozinha, repetidas
vezes, sobre janelas que se sobrepõem, e cujos fatos podem mudar depois de publicados.

Três propriedades do pipeline atual, todas corretas para arquivo, ficam perigosas quando a fonte é
automática:

1. `ImportDeduplicationService` compara o lote novo **apenas contra registros `COMMITTED`**
   (`ImportRecordRepository`, consultas de identidade externa e de impressão). O que ainda está em
   revisão não é fato consumado, e por isso não conta.
2. A heurística de fingerprint marca `DUPLICATE` mas **nunca vira constraint**, por decisão
   deliberada: dois cafés iguais no mesmo dia são dois fatos reais, e quem decide é o titular.
3. A conciliação de saldo do lote compara `abertura + Σ movimentos == fechamento` e classifica
   `MATCH`/`MISMATCH`/`UNAVAILABLE`.

A sincronização incremental precisa de sobreposição de janela — bancos publicam com atraso, e sem
overlap perde-se lançamento. Mas overlap somado a (1) tem uma consequência que só aparece no segundo
dia de uso: com o commit automático desligado, cada sincronização recria os mesmos dias como
registros novos, indefinidamente, porque nada do lote anterior chegou a `COMMITTED`. O titular
receberia uma enxurrada de pendentes e abandonaria o recurso.

Há ainda o caso que arquivo nunca teve: um fato **muda**. Uma autorização vira lançamento efetivado,
com data e às vezes valor diferentes; um lançamento efetivado sofre estorno parcial.

## Decisão

### Só entra fato efetivado

Lançamento pendente ou autorização **não é ingerido**. O fetcher descarta antes de escrever o
snapshot. Isso é coerente com o ADR-0010: o ledger recebe caixa consumada, não expectativa.

Fato já efetivado que muda depois **nunca é reescrito**. Fonte externa não faz `UPDATE` em
lançamento existente. A correção é reversão auditável do lote ou do registro
(`ImportReversalService`) seguida de reimportação da janela. Um caminho automático de substituição
(`supersedes_record_id`) fica registrado como possibilidade futura, fora do escopo desta fase.

### Commit automático é exceção, não padrão

`contas_conectadas.auto_commit` nasce `false`. O lote de sincronização para em revisão, exatamente
como um CSV.

Quando o titular liga `auto_commit`, o commit só dispara se **todas** as condições valerem: zero
registros `INVALID`, zero `PENDING_REVIEW`, zero `DUPLICATE` heurístico e conciliação de saldo em
`MATCH`. Qualquer outra combinação exige olho humano. Automação não grava operação definitiva sobre
dúvida.

### A deduplicação passa a olhar além de `COMMITTED`

Três mudanças na identidade forte, aplicáveis também a CSV e OFX:

1. **Instituição canônica.** A comparação deixa de ser textual sobre `institution_code` e passa a
   usar `instituicao_id` do catálogo, com fallback textual. Sem isso, o código `<FI><ORG>` de um
   arquivo OFX e o código do parceiro para o mesmo banco são strings diferentes, e o mesmo fato
   entra duas vezes por rotas diferentes.
2. **Lotes não finalizados contam.** A identidade forte também compara contra registros de lotes do
   mesmo titular em estado não terminal, marcando com razão própria (`DUPLICADO_EM_REVISAO`). É o que
   impede a enxurrada descrita no contexto.
3. **`REVERSED` conta.** Sem isso, o titular reverte um lote errado e a sincronização seguinte, pelo
   overlap, traz tudo de volta como válido — desfazendo silenciosamente uma decisão explícita. Os
   registros revertidos são marcados com razão distinta, para que reincluí-los seja escolha
   consciente.

Além da deduplicação, uma trava de fluxo: o agendador **não enfileira** nova sincronização para uma
conta que já tenha lote de conector em estado não terminal. A conta fica "aguardando sua revisão" e
o titular é avisado.

A heurística de fingerprint continua **não** sendo constraint. Isso não muda.

### Data e moeda são convertidas explicitamente

O parceiro devolve instante com offset. A conversão para data de negócio usa o `Clock` do ADR-0003 —
nunca `LocalDate.now()` nem o fuso do servidor. Um erro de um dia move a transação de mês e quebra
fatura, orçamento e conciliação de saldo de uma vez só.

Transação em moeda diferente da conta vinculada vira `INVALID` com razão própria. O sistema **não**
converte por taxa que ele mesmo escolheu.

### O saldo de referência é o contábil

Bancos publicam pelo menos dois saldos: o disponível (já líquido de pendentes e limites) e o
atual/contábil. Como a ingestão aceita só fato efetivado, conciliar contra o saldo disponível
produziria divergência permanente — o commit automático nunca dispararia e o titular veria
divergência eterna sem causa real.

A referência é o **saldo contábil**. `saldos_declarados_instituicao` guarda os dois campos, porque a
diferença entre eles é justamente o diagnóstico de "há pendente lá que aqui ainda não existe".

### Divergência não se corrige sozinha

Invariante `SALDO_INSTITUICAO` na reconciliação global: último snapshot por conta conectada contra o
saldo da carteira vinculada. Snapshot mais velho que o limite de frescor **não** é divergência — é
verificação não realizada. Confundir os dois faria a saúde do sistema ficar vermelha por causa de
rede. Na primeira versão a invariante entra no resumo mas não no cômputo do estado divergente, atrás
de flag.

Ao divergir, nesta ordem, sem correção automática (ADR-0015):

1. carimba `divergente_desde` na conta conectada;
2. notifica o titular com a diferença e o período provável;
3. oferece ressincronizar a janela com overlap ampliado — resolve o caso "faltou lançamento";
4. se persistir, oferece ajuste de conciliação explícito, lançado **pelo titular**, com descrição
   obrigatória e `OrigemDadosConta.AJUSTE`;
5. nunca sobrescreve saldo de carteira e nunca apaga transação. Lote errado se corrige por reversão.

Uma carteira vinculada que também recebe lançamentos manuais diverge por construção. A interface
avisa disso no vínculo e distingue "faltou sincronizar" de "lançamento manual em conta conectada".

## Consequências

- O titular precisa revisar lotes para que a sincronização continue. É atrito deliberado: a
  alternativa é gravar no ledger sem confirmação, contra o ADR-0001 e contra a regra de que
  automação não grava operação definitiva sem contrato validado.
- Descartar lançamento pendente significa que o app mostra menos que o extrato do banco no mesmo
  instante. É a troca correta: o ledger é caixa, não previsão, e um pendente que vira efetivado com
  outro valor produziria correção retroativa que o modelo não aceita.
- Estender a deduplicação a lotes não finalizados e a `REVERSED` muda o comportamento também de CSV e
  OFX. É mudança de contrato observável: um arquivo reenviado antes da revisão do anterior passa a
  vir marcado como duplicado em vez de válido. Considerado correção, não regressão — mas exige
  regressão completa do pipeline existente antes do merge.
- A dependência de estabilidade do identificador externo do parceiro fica exposta: se o parceiro não
  garantir `transactionId` estável entre chamadas, a identidade forte não funciona e tudo cai na
  heurística, que nunca vira constraint — e aí o commit automático é impossível. Por isso a
  estabilidade do identificador é critério eliminatório na escolha do parceiro; se for instável, o
  fetcher sintetiza identificador derivado determinístico, com razão visível na prévia.

## Alternativa descartada

**Sincronizar sem sobreposição de janela, confiando na data de publicação do parceiro.** Elimina de
uma vez o problema de duplicado pendente e dispensa as três mudanças de deduplicação. Descartada
porque bancos publicam com atraso variável e reordenam lançamentos perto da virada do dia: sem
overlap, um lançamento publicado depois do avanço do cursor **nunca mais é buscado** e some para
sempre, sem sinal nenhum. Perder silenciosamente um fato financeiro é pior que marcar um duplicado a
mais — o duplicado o titular vê e resolve; a ausência ele não vê.
