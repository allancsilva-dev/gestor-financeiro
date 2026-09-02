# ADR-0020 — Consentimento, credenciais de terceiro e revogação

- **Status:** ACEITO
- **Data:** 2026-09-01
- **Relacionado:** Fase 6 (PR-F6-00), ADR-0007 (exclusão LGPD ordenada), ADR-0006 (backup off-host),
  ADR-0019 (conector pelo pipeline canônico), PROB-0081

## Contexto

Até hoje o sistema nunca guardou credencial de terceiro em nome do titular. O único consentimento
existente é o de cadastro (`usuarios.politica_versao` e `usuarios.consentimento_em`, V19), e o único
precedente de criptografia de payload é `WhatsappCrypto` (AES-GCM, chave de 32 bytes em variável de
ambiente, `key_version`).

A Fase 6 muda isso em três frentes ao mesmo tempo: passa a existir um consentimento **por
instituição**, com prazo e renovação; passa a existir um token de acesso de terceiro guardado no
banco; e passa a existir um fluxo de autorização que termina num callback HTTPS. Cada uma dessas
frentes tem um modo de falha próprio, e nenhuma tem precedente no repositório.

Duas confusões precisam ser resolvidas antes de qualquer código, porque decidi-las depois seria
decidi-las por acidente:

1. **Revogar é a mesma coisa que excluir?** A intuição do usuário diz que sim ("desliguei o banco,
   sumiu tudo"). A regra do produto diz que não: "nenhum dado financeiro pode desaparecer por
   arquivamento, conclusão ou deploy".
2. **O parceiro empurra dados por webhook?** Agregadores costumam oferecer isso. Um webhook sem
   verificação de assinatura é uma rota pública de escrita no ledger de qualquer titular.

## Decisão

### Consentimento é append-only

`consentimentos_open_finance` registra escopo, criação, concessão, expiração, renovação
(auto-referência a `renovado_de_id`), revogação e quem revogou (`TITULAR`, `INSTITUICAO`, `SISTEMA`,
`EXPIRACAO`). **Revogar muda status, nunca apaga linha** — a linha é a prova de conformidade. Guarda
também `politica_versao` no instante do aceite e `evidencia_hash` (SHA-256 do texto exibido ao
titular), para que seja possível provar depois o que exatamente foi consentido.

Índice parcial `ux_consentimentos_ativo_por_conexao ON (conexao_id) WHERE status = 'ATIVO'`: duas
renovações concorrentes não podem produzir dois consentimentos ativos.

Nenhuma sincronização roda sem consentimento `ATIVO`, e nenhum recurso é buscado sem o escopo
correspondente presente no consentimento.

### Credencial fica em tabela separada

`conexao_credenciais` guarda `access_token` e `refresh_token` cifrados, em tabela **distinta** da
conexão. O motivo é operacional: a credencial pode ser expurgada e rotacionada sem tocar no
histórico da conexão, e é a primeira coisa que a revogação apaga.

Cifra no molde de `WhatsappCrypto`: AES-GCM com tag de 128 bits, IV de 12 bytes sorteado por
operação com `SecureRandom`, chave de 32 bytes em Base64 vinda **só** de variável de ambiente,
`key_version` gravado na linha. `token_hmac` permite localizar e detectar reuso sem guardar o valor
em claro. Um `OpenFinanceConfigurationGuard` no molde de `AssistantExternalConfigurationGuard`
derruba o boot em profile `prod`/`vps` se a feature estiver ligada sem chave, sem política aceita ou
sem provedor configurado.

`key_version` só tem valor com procedimento de rotação escrito: re-cifra em lote, leitura tolerante
a duas versões durante a transição, corte quando a contagem da versão antiga chegar a zero. Sem esse
procedimento a coluna é decoração, e fica registrado aqui que ela não é.

Endpoint e segredo do provedor vivem em property; o banco guarda apenas `config_ref`, o nome do
prefixo. **O banco nunca guarda URL**, e nenhum host de destino vem de dado do titular.

### Vínculo de conta exige `state` de uso único

O fluxo de autorização termina num callback HTTPS. Sem um `state` ligado à sessão, esse callback é
vulnerável a CSRF: um atacante induz o titular a concluir um fluxo iniciado pelo atacante e vincula
uma conexão sob controle alheio ao perfil da vítima — injetando transações no ledger de outra
pessoa. Portanto:

- `state` opaco de uso único, gerado com `SecureRandom`, guardado server-side com TTL curto e
  vinculado ao `usuario_id`; comparação em tempo constante (`MessageDigest.isEqual`); consumido na
  primeira validação;
- PKCE `S256` sempre que o parceiro suportar;
- `redirect_uri` fixa em property e validada contra allowlist — **nunca** vinda do request;
- o callback não escreve no ledger: valida, grava consentimento e enfileira job;
- rate limit próprio no callback e nos endpoints de conexão.

### Revogar não é excluir

Revogar apaga a credencial imediatamente, encerra o consentimento com `revogado_em`/`revogado_por`,
desativa as contas conectadas — e **mantém no ledger todas as transações já lançadas**. Elas são
fatos financeiros do titular, não propriedade do parceiro.

A revogação também precisa acontecer **no parceiro**, não só localmente. O job
`OF_CONNECTION_REVOKE` confirma a revogação remota; se falhar, reenfileira. A conexão fica
`REVOGADA` localmente enquanto o job persiste, para que o sistema pare de usar a credencial
imediatamente, mas a revogação só é considerada completa com confirmação remota.

Exclusão do titular (ADR-0007) é outra coisa: o manifesto ordenado recebe as tabelas novas antes de
`import_batches` e `carteiras`, nesta ordem — `saldos_declarados_instituicao`, `sync_execucoes`,
`sync_cursores`, `contas_conectadas`, `conexao_credenciais`, `consentimentos_open_finance`,
`conexoes_open_finance`. Todas carregam `usuario_id` denormalizado, mesmo quando redundante, para
que cada entrada do manifesto seja um `DELETE ... WHERE usuario_id = :id` simples.

### Webhook fica fora do escopo desta fase

A ingestão é **só por polling** na Fase 6. Nenhum endpoint público recebe dado do parceiro.

Se um parceiro futuro tornar o webhook necessário, ele entra sob condições fixadas aqui, e não por
conveniência de implementação: verificação de assinatura HMAC sobre o **corpo cru** no molde de
`WhatsappCrypto.validSignature` (comparação em tempo constante), janela de replay, idempotência por
id de evento, e a regra de que o webhook apenas **enfileira job** — jamais escreve no ledger.

### Retenção

`OpenFinanceRetentionService`, no molde horário de `AssistantRetentionService`: credenciais de
conexões `REVOGADA`/`EXPIRADA`/`ERRO` são apagadas após 24 h; `sync_execucoes` após 180 dias;
consentimentos encerrados seguem o prazo de guarda de conformidade. `sync_execucoes` guarda
contadores e código de erro, **nunca** conteúdo de transação. O snapshot NDJSON nunca é persistido.

Descrição de transação, máscara de conta, `external_id` e qualquer token são proibidos em log.

## Consequências

- O sistema passa a guardar segredo de terceiro, e com isso herda uma classe de risco que não tinha.
  A chave de cifra vira item de runbook **separado do dump**: restaurar um backup sem ela deixa todas
  as conexões inutilizáveis, ainda que o banco esteja íntegro. Isso amarra esta fase ao `PROB-0081`.
- O titular que revoga e reconecta a mesma instituição não perde histórico, mas também não recupera
  a credencial antiga — precisa consentir de novo. É o comportamento correto e precisa estar claro na
  interface, senão vira chamado de suporte.
- Consentimento append-only faz a tabela crescer sem expurgo agressivo. Aceito: são poucas linhas por
  titular por ano, e é o registro que sustenta qualquer questionamento de conformidade.
- Ficar só em polling na Fase 6 significa latência maior para refletir uma transação nova. Aceito em
  troca de não abrir rota pública de escrita antes de haver parceiro definido.
- O catálogo de provedores e instituições não tem rota de escrita: é populado por migration. Não há
  autorização por papel no sistema (`CustomUserDetailsService` devolve authorities vazias), então
  qualquer tela administrativa fica bloqueada até existir modelo de permissões de verdade.

## Alternativa descartada

**Guardar o token na própria linha da conexão, sem tabela separada.** Menos uma tabela e menos um
join. Descartada porque acopla o ciclo de vida do segredo ao da conexão: expurgar a credencial na
revogação passaria a exigir `UPDATE` com colunas nulas numa linha que também guarda histórico, a
rotação de chave teria de reescrever a tabela inteira em vez de só os segredos, e um `SELECT *`
descuidado em qualquer consulta de conexão passaria a carregar material cifrado para dentro de logs
e respostas. A separação é o que torna "apagar o segredo" uma operação de uma linha, auditável.
