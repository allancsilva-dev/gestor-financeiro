# Bugfix Log — Gestor Financeiro

Registro de bugs corrigidos. Mantido pelo `docs-reporter`.

---

## BUG-0103 — Migration V41 abortava com cartão legado sem dia de fechamento/vencimento

- **Data:** 2026-08-29
- **Area:** backend, migrations, cartões
- **Origem:** deploy da VPS; a API não subia e o web ficava parado por depender dela.
- **Sintoma:** Flyway falhava em `V41__contract_contas_financeiras_cartoes.sql` com
  "V41 abortada: 1 cartao(oes) com campo canonico nulo"; Spring Boot não inicializava e o
  healthcheck marcava a API unhealthy. Migration é transacional, nada ficou aplicado.
- **Causa raiz:** `V1__baseline_schema.sql` criou `contas.dia_fechamento`, `dia_vencimento`,
  `limite_total` e `ativo` como NULLABLE e `V20` só validou `IS NULL OR BETWEEN 1 AND 31`.
  O cartão "Cartão Principal" foi criado nesse modelo antigo, com os dois dias nulos. A V41 exige
  `SET NOT NULL` nessas colunas e tem um guard que aborta com nulo — **sem backfill entre os dois**.
  O bug é da migration, não do dado; nada obrigava o usuário a ter preenchido esses campos.
- **Correção:** nova seção `2b` na V41, antes do guard, preenchendo registros legados com os mesmos
  defaults que o código já aplicava para nulo — `dia_fechamento = 31` (equivalente exato ao "último
  dia do mês" de `FaturaDatas.diaValidoOuFimDoMes`, que clampa para `lengthOfMonth`),
  `dia_vencimento = 10` (`FaturaDatas.vencimento`), `limite_total = 0` e `ativo = true` (DEFAULT do
  V1). Comportamento anterior preservado; o usuário ajusta os dois dias pelo app depois.
  Nenhum valor foi escolhido no lugar do usuário e nenhuma métrica da seção 1 usa essas colunas.
- **Nota operacional:** editar a V41 muda o checksum. A VPS estava parada na V40, então não é
  afetada. Base que **já passou** pela V41 (dev local) precisa de `flyway repair` — seguro, porque
  ali o guard já provou que não havia nulos e o `UPDATE` novo seria no-op.
- **Testes:** `ContractV41MigrationIT.cartaoLegadoComCamposNulosRecebeBackfillEMigra` (novo),
  suíte `ContractV41MigrationIT` 9 verdes e `PostgresMigrationIT` 7 verdes.

---

## BUG-0102 — App pedia permissão de notificação em simulador e travava qualquer automação de UI

- **Data:** 2026-08-29
- **Area:** mobile, notificações
- **Origem:** execução do E2E local iOS da Fase 5; o diálogo do sistema cobria a tela e o Maestro
  parava de achar elementos.
- **Sintoma:** ao entrar na área logada em simulador, aparecia "O app Nexos Finanças deseja enviar
  notificações", que não deveria existir ali — simulador não tem serviço de push.
- **Causa raiz:** `src/notificacoes/push.ts` guardava a chamada com `Constants.isDevice ?? true`.
  O campo `isDevice` foi removido do `expo-constants@18` (migrou para `expo-device`), então a
  expressão resolvia sempre `true` e o app se tratava como aparelho real.
- **Correção:** dependência `expo-device` adicionada e guarda trocada por `Device.isDevice`, que o
  módulo nativo resolve como `false` em simulador. `pod install` passa a ser obrigatório junto.
- **Testes:** `src/__tests__/push.test.ts` (6) com mock de `expo-device`; suíte mobile 434 verdes.
- **Ver também:** PROB-0087.

---

## BUG-0101 — Assistente respondia fatura em vez de registrar compra no cartão

- **Data:** 2026-08-29
- **Area:** backend, assistente
- **Origem:** implementação de parcelamento; a frase de teste nunca virava rascunho.
- **Sintoma:** "comprei 300,00 no mercado hoje no Cartao Nubank em 3x" devolvia `NOT_FINANCIAL` com
  uma resposta de consulta de faturas. Registrar compra no cartão pelo assistente era impossível.
- **Causa raiz:** `FinancialQuestionClassifier` classificava por presença de termo — qualquer texto
  com "cartao" ou "fatura" virava intent `INVOICES`. Como `AssistantService.receive()` consulta o
  classificador antes do parser, a frase nunca chegava ao caminho de lançamento.
- **Correção:** o classificador desiste do intent quando a frase não pergunta (sem "?", "quanto",
  "qual", "quais", "quando", "quantos", "mostra", "resumo", "como esta") e há indício de lançamento
  — verbo de gasto ou valor monetário.
- **Testes:** `AssistantParcelamentoTest` (3), `FinancialQuestionClassifierTest` (4), suíte backend
  470 verdes; E2E `artifacts/fase5/run-20/proof-assistant-parcelado.json`.
- **Ver também:** PROB-0086.

---

## BUG-0100 — Reenviar o mesmo arquivo derrubava a importação com violação de CHECK

- **Data:** 2026-08-27
- **Area:** backend, importação
- **Origem:** verificação em runtime do pipeline canônico (stack local, PostgreSQL descartável,
  backend na 8081). Primeira importação passa; o **reenvio do mesmo arquivo depois do lançamento**
  respondia `422 IMPORT_PARSING_FAILED` com `failureCode=PARSE_FAILED`, e o lote ficava `FAILED`.
- **Sintoma no banco:** `ERROR: new row for relation "import_batches" violates check constraint
  "ck_import_batches_counts"`, com a linha `total=4, valid=3, pending=1, duplicate=3` — soma 7 para
  4 registros.
- **Causa:** `ImportDeduplicationService.marcarDuplicados` aplicava os contadores um a um na
  entidade e **consultava entre um `set` e outro**. Cada consulta dispara auto-flush do contexto de
  persistência, então o flush intermediário gravava `duplicate` já atualizado com `valid` ainda
  antigo — estado incoerente que o CHECK recusa. Em H2 o defeito não aparece: o CHECK só existe no
  PostgreSQL, e o teste unitário montava os contadores à mão.
- **Correção:** contar os três status **antes** de tocar na entidade e aplicar os valores de uma vez.
- **Arquivos alterados:**
  `backend/src/main/java/com/gestor/financeiro/service/importacao/ImportDeduplicationService.java`,
  `backend/src/test/java/com/gestor/financeiro/ImportReenvioIT.java` (novo).
- **Testes/validacoes executadas:** `ImportReenvioIT` reproduz o defeito (falha antes, passa depois),
  cobrindo enviar → lançar → reenviar contra PostgreSQL real. Suíte: 358 unitários e 45 de
  integração verdes. Runtime: reenvio passou a marcar 3 duplicados e 0 válidos; reversão devolveu o
  saldo de 5333,60 para 2500,00 e a reconciliação global ficou em zero divergências.
- **Resultado:** PASS
- **Licao:** invariante que só existe no banco de produção precisa de teste no banco de produção.
  O caminho feliz rodava em H2 desde o início e escondeu a incoerência por três PRs.

---

## BUG-0073 — Rodada de Maestro no simulador: quatro defeitos de UI achados e corrigidos

- **Data:** 2026-08-21
- **Area:** mobile
- **Origem:** primeira execução real de Maestro em simulador (iPhone 17 Pro, iOS 26.5, Release,
  backend local na 8090 com banco descartável `gf_e2e`) — a pendência acumulada desde o Bloco B da
  Fase 3. Os defeitos abaixo só aparecem em aparelho: nenhum deles quebra teste unitário.
- **Sintomas e correções:**
  1. **FAB atrás da tab bar** — `ui/Fab` usava `bottom: 24`, abaixo do painel flutuante de navegação
     (69 de altura + 15 de margem + safe area): em Categorias e Recorrências o botão "+" ficava
     encoberto e o toque não chegava nele. Agora usa `useTabBarSpace()` e as cores do tema
     (`fabFrom`/`fabTo`/`fabGlow`) no lugar do violeta cru do protótipo antigo. `more/carteiras.tsx`
     tinha um FAB inline com o mesmo `bottom: 24` — passou a usar o `ui/Fab`.
  2. **"Carteira" na home abria "Contas"** — `onCarteira` do `SaldoCard` apontava para
     `/more/carteiras` (tela Contas). No hub de Ajustes e no checklist, "Carteira" é a tela de
     cartões e faturas (`/more/faturas`); o destino foi alinhado ao rótulo.
  3. **Formulários sem encadeamento de foco** — trocar de campo com o teclado aberto é impreciso
     (o layout sobe); no simulador, o texto do campo seguinte caía no campo anterior. Ganharam
     `returnKeyType` + `onSubmitEditing`: cadastro (nome→e-mail, senha→confirmação), login
     (e-mail→senha), criação de meta (nome→valor→mensal→data→descrição) e Nova Transação
     (valor→descrição→data). O formulário de cartão já encadeava; ganhou apenas `testID`s.
  4. **Medidor de senha empurrava o layout** — o `CampoSenha` só desenhava a régua depois do
     primeiro caractere, deslocando o campo de confirmação para baixo enquanto o usuário digitava.
     Agora o espaço é reservado desde o campo vazio, com a dica da regra no lugar do rótulo.
- **Arquivos alterados:** `mobile/src/components/ui/{Fab,CampoSenha,PassosProgresso,TelaFluxo}.tsx`,
  `mobile/app/(app)/(inicio)/index.tsx`, `mobile/app/(app)/metas.tsx`,
  `mobile/app/(app)/more/{carteiras,faturas}.tsx`, `mobile/app/(auth)/{login,register}.tsx`,
  `mobile/app/onboarding.tsx`, `mobile/src/components/NovaTransacaoModal.tsx`, `.maestro/*.yaml`
- **Testes/validacoes executadas:** Maestro no simulador — `smoke-auth`, `privacy-consent` e
  `recovery-navigation` **PASS**; `financial-critical` avança 119 comandos (cadastro em 3 passos →
  onboarding em 6 → categoria → cartão → meta → primeira transação) e ainda falha na segunda
  transação (compra no cartão). Jest 197/197, `tsc --noEmit` e `npm run lint` limpos.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** `financial-critical` continua vermelho no trecho final (compra no cartão) — ver
  BACKLOG. Os flows tocavam vários elementos por texto que não existe na árvore de acessibilidade
  (o texto dentro de `Touchable` com `accessibilityLabel` próprio não sobe); foram trocados por
  `testID` ou pelo label real.

---

## BUG-0074 — Onboarding não dizia onde o usuário estava nem o que era opcional

- **Data:** 2026-08-21
- **Area:** mobile, UX
- **Sintoma:** relato do dono do produto após ver o fluxo rodando ("dá para se perder no
  registro"). Confirmado nas capturas: a barra de progresso do cadastro (3 segmentos) reiniciava
  como 6 segmentos no onboarding, sem nenhum marco de "conta criada"; e nada indicava que os passos
  de renda, categorias, cartão e meta eram opcionais — só o botão "Pular por agora" sugeria isso.
- **Correcao aplicada:** `ui/PassosProgresso` passou a exibir "Passo X de N" em texto ao lado das
  barras; `ui/TelaFluxo` ganhou um selo acima do título; o onboarding marca o passo 1 com **CONTA
  CRIADA** (e diz na abertura que os próximos podem ser pulados) e os passos 2–5 com **OPCIONAL**.
- **Arquivos alterados:** `mobile/src/components/ui/{PassosProgresso,TelaFluxo}.tsx`,
  `mobile/app/onboarding.tsx`
- **Resultado:** PASS

---

## BUG-0075 — Passo de cartão do onboarding não mostrava a identidade do banco

- **Data:** 2026-08-21
- **Area:** mobile, UX
- **Sintoma:** no onboarding, o cartão era só um formulário de texto; o usuário só descobria a cor
  e o monograma do banco depois de salvar e abrir a Carteira. O catálogo `src/domain/emissores.ts`
  (com Itaú, Nubank, Bradesco, BB, Caixa, Inter, C6, XP, BTG e outros) já existia e não era usado
  ali.
- **Correcao aplicada:** o passo mostra o mesmo `CartaoFisico` da Carteira, reagindo ao que está
  sendo digitado — "Itaú" traz laranja `#FF6200`, rótulo ITAÚ e monograma; nome livre ("Nubank
  Roxinho") também resolve; banco fora do catálogo recebe cor determinística derivada do nome, com
  contraste garantido. O titular exibido é o nome real do usuário. **Sem lista fechada de bancos**:
  uma primeira versão com chips de sugestão foi descartada a pedido do dono do produto, porque
  limitaria às opções listadas.
- **Arquivos alterados:** `mobile/app/onboarding.tsx`, `mobile/src/__tests__/emissores.test.ts`
- **Testes/validacoes executadas:** testes novos em `emissores.test.ts` (reconhecimento por nome
  livre, cor determinística e contraste AA para banco desconhecido, identidade do Itaú) e captura
  no simulador dos três estados (vazio, Itaú, Nubank Roxinho).
- **Resultado:** PASS

---

## BUG-0076 — Investimentos sem idempotência: reenvio/duplo clique duplicava movimentação

- **Problema relacionado:** BACKLOG-0081
- **Data:** 2026-08-21
- **Area:** backend
- **Sintoma:** `POST /api/v1/investimentos/{ativoId}/movimentacoes` não aceitava header
  `Idempotency-Key`, ao contrário de outros fluxos financeiros sensíveis a duplo clique/retry
  (pagamento de fatura, BUG-0052; pagamento de parcela, BUG-0060) — reenvio da mesma requisição
  podia duplicar compra/venda/dividendo na posição do ativo.
- **Causa raiz:** `InvestimentoService.adicionarMovimentacao` não tinha overload com chave de
  idempotência. A "proteção" existente derivava a chave do ledger/da operação de `mov.getId()`,
  gerado só depois de persistir — dois cliques produziam ids diferentes e nunca colidiam; era uma
  proteção falsa.
- **Correção aplicada:** `InvestimentoController` passa a aceitar o header `Idempotency-Key`
  (mesmo padrão de `FaturaController`). `InvestimentoService.adicionarMovimentacao` ganhou
  sobrecarga com a chave, faz exists-check por `findByUsuarioIdAndIdempotencyKey` antes de
  qualquer efeito colateral e persiste a chave em `movimentacoes_ativo.idempotency_key`. Migration
  nova `V44__movimentacao_ativo_idempotency.sql` cria a coluna e o índice único parcial
  `ux_movimentacoes_ativo_usuario_idempotency` (molde de V11). Clientes passaram a enviar o header:
  `mobile/src/services/investimentoService.ts` + `mobile/app/(app)/more/investimentos.tsx` (chave
  por abertura do formulário) e `frontend/src/services/investimentoService.ts` +
  `frontend/src/pages/Investimentos.tsx` (chave mantida até a movimentação entrar).
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/controller/InvestimentoController.java`,
  `backend/src/main/java/com/gestor/financeiro/service/InvestimentoService.java`,
  `backend/src/main/resources/db/migration/V44__movimentacao_ativo_idempotency.sql`,
  `mobile/src/services/investimentoService.ts`, `mobile/app/(app)/more/investimentos.tsx`,
  `frontend/src/services/investimentoService.ts`, `frontend/src/pages/Investimentos.tsx`
- **Testes/validacoes executadas:** `./mvnw test` — 292 testes, 0 falhas, incluindo
  `InvestimentoIdempotenciaPaginacaoTest` (5, novo). Migration V44 validada em runtime: PostgreSQL
  16 efêmero, boot com `SPRING_PROFILES_ACTIVE=dev` e `ddl-auto=validate` — Flyway aplicou 43
  migrations até v44 e a aplicação subiu; `\d movimentacoes_ativo` mostra o índice único parcial.
  Contrato HTTP verificado: dois `POST` com o mesmo `Idempotency-Key` retornaram o mesmo `id` e uma
  única movimentação. Mobile: `npx tsc --noEmit` limpo, Jest 200/200. Frontend: `npx tsc --noEmit`
  limpo.
- **Resultado:** PASS
- **Ressalvas:** nenhuma verificação manual em app/browser real nesta rodada — validado via `curl`
  contra a instância efêmera e pelos testes automatizados.
- **Commit:** pendente

---

## BUG-0077 — Investimentos sem paginação: listagem sem limite de página

- **Problema relacionado:** BACKLOG-0082
- **Data:** 2026-08-21
- **Area:** backend
- **Sintoma:** `GET /api/v1/investimentos` e `GET /api/v1/investimentos/{ativoId}/movimentacoes`
  devolviam lista completa sem paginação, ao contrário de outras listagens do sistema
  (`TransacaoController`) — risco de payload/consulta crescer sem limite conforme o usuário
  acumula histórico de movimentações.
- **Causa raiz:** endpoints implementados antes de a paginação virar convenção do sistema, nunca
  revisitados.
- **Correção aplicada:** os dois endpoints passam a devolver `Page` com `@PageableDefault(size =
  20)` e `PaginationUtils.enforceMaxSize(pageable, 100)`, como `TransacaoController`.
  Repositórios ganharam variantes paginadas. Os dois clientes consomem `.content ?? []` com
  `size=100` (padrão já usado em `categoriaService`/`contaFinanceiraService`) — **muda o contrato
  de API (breaking change)** para qualquer cliente externo não atualizado nesta rodada.
- **Arquivos alterados:** controller/repositório de investimentos em
  `backend/src/main/java/com/gestor/financeiro/{controller,repository}/`,
  `mobile/src/services/investimentoService.ts`, `mobile/app/(app)/more/investimentos.tsx`,
  `frontend/src/services/investimentoService.ts`, `frontend/src/pages/Investimentos.tsx`
- **Testes/validacoes executadas:** mesma execução de `./mvnw test` (292/0 falhas):
  `InvestimentoIdempotenciaPaginacaoTest` cobre paginação; `InvestimentoCustodiaCotacaoTest`
  ajustado ao retorno paginado. Contrato verificado contra instância efêmera:
  `GET /v1/investimentos/{id}/movimentacoes` devolve envelope `Page` (`totalElements`, `size=20`);
  `GET /v1/investimentos?size=500` é capado em `size=100`.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** breaking change de contrato — clientes externos fora de mobile/web não foram
  (nem poderiam ser) atualizados por esta rodada. `backend/API.md` não documenta os endpoints de
  investimentos (não documentava antes e continua sem documentar) — ver BACKLOG-0097.
- **Commit:** pendente

---

## BUG-0078 — `RefreshToken.toString()` vazava hash de token e e-mail (PII) em logs

- **Problema relacionado:** BACKLOG-0083
- **Data:** 2026-08-21
- **Area:** backend, seguranca, LGPD
- **Sintoma:** a descrição original de BACKLOG-0083 (auditoria de 2026-07-14) dizia que a entidade
  não tinha `toString()` customizado — isso estava errado. A entidade **tem** `toString()`
  customizado (`model/RefreshToken.java`), e era exatamente ele quem vazava: imprimia 20
  caracteres do hash SHA-256 do token e `usuario.getEmail()` (PII), além de disparar lazy-load e
  poder estourar `NullPointerException` no `substring` do token.
- **Causa raiz:** implementação anterior do `toString()` incluía diretamente o hash do token e o
  e-mail do usuário associado, sem exclusão de campos sensíveis.
- **Correção aplicada:** `toString()` agora expõe só `id`, `usuarioId` (via `usuario.getId()`, que
  não força o lazy load) e `dataExpiracao`/`revogado`.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/model/RefreshToken.java`
- **Testes/validacoes executadas:** `RefreshTokenToStringTest` (2, novo) dentro de `./mvnw test`
  292 testes, 0 falhas.
- **Resultado:** PASS
- **Ressalvas:** nenhuma identificada.
- **Commit:** pendente

---

## BUG-0079 — Entidades bidirecionais sem proteção contra recursão em toString/equals/hashCode

- **Problema relacionado:** BACKLOG-0084
- **Data:** 2026-08-21
- **Area:** backend
- **Sintoma:** entidades JPA com relacionamento bidirecional usando Lombok `@Data` (que gera
  `equals`/`hashCode`/`toString` incluindo os relacionamentos) sem proteção contra recursão
  infinita quando ambos os lados se referenciam.
- **Causa raiz:** `grep mappedBy backend/src/main/java/com/gestor/financeiro/model/*.java`
  confirma exatamente 2 ciclos bidirecionais no modelo: `Ativo` ↔ `MovimentacaoAtivo`
  (`model/Ativo.java:66`) e `Transacao` ↔ `Parcela` (`model/Transacao.java:80`). O par
  `Transacao`↔`Parcela` já tinha `@ToString.Exclude`/`@EqualsAndHashCode.Exclude`; o par
  `Ativo`↔`MovimentacaoAtivo` não tinha nenhuma proteção, nem no JSON.
- **Correção aplicada:** ambos os pares ganharam `@ToString.Exclude` +
  `@EqualsAndHashCode.Exclude` nos dois lados; o par de investimentos ganhou também
  `@JsonIgnoreProperties`.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/model/Ativo.java`,
  `backend/src/main/java/com/gestor/financeiro/model/MovimentacaoAtivo.java`,
  `backend/src/main/java/com/gestor/financeiro/model/Transacao.java`,
  `backend/src/main/java/com/gestor/financeiro/model/Parcela.java`
- **Testes/validacoes executadas:** `EntidadesBidirecionaisSemRecursaoTest` (2, novo) dentro de
  `./mvnw test` 292 testes, 0 falhas.
- **Resultado:** PASS
- **Ressalvas:** nenhuma identificada.
- **Commit:** pendente

---

## BUG-0080 — Defaults inseguros remanescentes em `application.properties` base

- **Problema relacionado:** BACKLOG-0085
- **Data:** 2026-08-21
- **Area:** backend, seguranca
- **Sintoma:** perfil base (`application.properties`, herdado por qualquer perfil que não
  sobrescreva explicitamente) tinha vários defaults voltados para conveniência de desenvolvimento
  em vez de segurança: `spring.jpa.show-sql=true`, `app.docs.public=true`,
  `management.endpoint.health.show-details=always`, log em `DEBUG` para o pacote da aplicação e
  para Spring Security, `cookie.secure=${COOKIE_SECURE:false}` e
  `cors.allowed.origins=${CORS_ALLOWED_ORIGINS:http://localhost:5173}` como fallback.
- **Causa raiz:** BACKLOG-0011 (fechado em 2026-07-13) tratou apenas senha de DB e JWT secret
  default; o restante do arquivo nunca recebeu revisão linha a linha classificando cada default
  como seguro/exige override/deve ser removido.
- **Correção aplicada:** defaults do perfil base invertidos para o lado seguro:
  `spring.jpa.show-sql=false`, `app.docs.public=false`,
  `management.endpoint.health.show-details=never`, `logging.level.com.gestor.financeiro=INFO`,
  `logging.level.org.springframework.security=WARN`, `cookie.secure=${COOKIE_SECURE:true}`,
  `cors.allowed.origins=${CORS_ALLOWED_ORIGINS:}` (sem default de dev).
  `application-dev.properties` já declarava todos esses valores explicitamente, então o
  desenvolvimento não muda. `src/test/resources/application-test.properties` passou a declarar
  `app.docs.public=true` e `show-details=always`, que os testes de infraestrutura exercitam e que
  antes vinham herdados do base.
- **Arquivos alterados:** `backend/src/main/resources/application.properties`,
  `backend/src/test/resources/application-test.properties`
- **Testes/validacoes executadas:** `./mvnw test` — 292 testes, 0 falhas.
- **Resultado:** PASS
- **Ressalvas:** nenhuma identificada nesta rodada; validação de comportamento em produção real
  depende do gate de deploy já registrado em BACKLOG-0080 (nginx/redes/smoke), item distinto.
- **Commit:** pendente

---

## BUG-0081 — Textos visíveis escondidos da árvore de acessibilidade por `accessibilityLabel` do container

- **Problema relacionado:** BACKLOG-0096
- **Data:** 2026-08-21
- **Area:** mobile, acessibilidade
- **Sintoma:** vários controles com texto visível também tinham `accessibilityLabel` próprio no
  `Touchable` pai. Como um `Touchable` `accessible` (padrão) colapsa os filhos num nó único, o
  `accessibilityLabel` substituía o texto na árvore de acessibilidade — leitor de tela anunciava só
  o rótulo, e buscas por texto (Maestro, testes) deixavam de encontrar a palavra visível na tela.
- **Causa raiz:** ausência de convenção documentada sobre quando usar `accessibilityLabel` no
  container versus deixar o texto dos filhos ser o rótulo.
- **Correção aplicada:** convenção nova registrada em `DESIGN.md` (seção "Acessibilidade"):
  controle com texto visível não leva `accessibilityLabel`; `accessibilityLabel` só para
  controles icon-only; contexto extra vai em `accessibilityHint`; `accessibilityRole`/
  `accessibilityState` continuam sempre. Aplicada em: `mobile/src/components/home/SaldoCard.tsx`
  (botões "Nova Transação" e "Carteira", era "Abrir carteira"); `mobile/app/(app)/more/faturas.tsx`
  (ação "+ Cartão", era "Cadastrar novo cartão", e "Salvar" do formulário de cartão, era "Salvar
  cartão"); `mobile/src/components/metas/CardMeta.tsx` (card da meta, era "Abrir detalhes da meta
  X", e botão "Depositar", era "Depositar na meta X"; `AnelProgresso` deixou de repetir o nome da
  meta no label); `mobile/src/components/NovaTransacaoModal.tsx` (4 botões de setup rápido);
  `mobile/src/components/home/ParcelasCarrossel.tsx` (2); `mobile/src/components/carteira/LinhaFatura.tsx`
  (1); `mobile/app/(app)/more/fatura.tsx` ("Pagar Fatura"). Mantidos os labels legítimos de
  controles icon-only (`ui/Fab`, FAB da tab bar, `ui/BackButton`, badge "OK/Divergente" da tela
  Contas, ícones de editar/excluir). Flows Maestro voltaram a buscar por texto:
  `mobile/.maestro/financial-critical.yaml` usa "Carteira", "Cartão", "Salvar", "Meta Smoke",
  "Depositar" no lugar dos labels de workaround; o comentário de workaround foi removido.
- **Arquivos alterados:** `mobile/src/components/home/SaldoCard.tsx`,
  `mobile/app/(app)/more/faturas.tsx`, `mobile/src/components/metas/CardMeta.tsx`,
  `mobile/src/components/NovaTransacaoModal.tsx`, `mobile/src/components/home/ParcelasCarrossel.tsx`,
  `mobile/src/components/carteira/LinhaFatura.tsx`, `mobile/app/(app)/more/fatura.tsx`,
  `mobile/.maestro/financial-critical.yaml`, `DESIGN.md`
- **Testes/validacoes executadas:** `npx tsc --noEmit` limpo e Jest 200/200 no mobile.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** não houve verificação manual com TalkBack/VoiceOver nesta rodada.
- **Commit:** pendente

---

## BUG-0082 — Home não atualizava saldo/lista após salvar transação por caminhos que não a Home direta

- **Problema relacionado:** BACKLOG-0095
- **Data:** 2026-08-21
- **Area:** mobile
- **Sintoma:** `financial-critical` falhava ao asseverar a compra no cartão depois de salvá-la — a
  transação não aparecia na Home. A hipótese original (registrada em BACKLOG-0095) era timing de
  refetch ou validação silenciosa de cartão.
- **Causa raiz:** diferente da hipótese original. A validação de cartão existe e é visível
  (`mobile/src/components/NovaTransacaoModal.tsx:273` seta `pagamentoError`) e `:151`
  auto-seleciona o primeiro cartão. O problema real: o `invalidateQueries` do modal não incluía
  `['home']` nem `['operacoes']` — as duas únicas chaves que alimentam a Home — e nenhum outro
  ponto do app invalidava essas chaves. A Home só atualizava porque o call site dela
  (`app/(app)/(inicio)/index.tsx`) passava `onSaved` com `refetch()` manual; salvar pelo FAB da tab
  bar (`app/(app)/_layout.tsx`, `onSaved` navega para `/transacoes`) ou pela tela de faturas
  deixava a Home com dados velhos. O flow Maestro rola a Home antes de tocar "Nova transação", o
  `SaldoCard` sai do viewport e o toque acerta o FAB da tab bar — o caminho que não atualizava.
- **Correção aplicada:** fonte única de invalidação em
  `mobile/src/hooks/useInvalidarAposTransacao.ts` (`CHAVES_AFETADAS_POR_TRANSACAO` inclui `['home']`
  e `['operacoes']`), consumida por `NovaTransacaoModal.tsx` e `EditarTransacaoModal.tsx` (que
  antes duplicavam a lista de chaves cada um). O `onSaved` da Home deixou de fazer `refetch()`
  manual (pull-to-refresh continua usando `atualizar()`). No flow
  `mobile/.maestro/financial-critical.yaml`, o `extendedWaitUntil` do bloco "Compra cartão smoke"
  virou `scrollUntilVisible` (a seção "Parcelas Agendadas" aparece e empurra a lista) e foi
  acrescentado o guard rail `assertNotVisible: "Selecione um cartão."`.
- **Arquivos alterados:** `mobile/src/hooks/useInvalidarAposTransacao.ts` (novo),
  `mobile/src/components/NovaTransacaoModal.tsx`, `mobile/src/components/EditarTransacaoModal.tsx`,
  `mobile/app/(app)/(inicio)/index.tsx`, `mobile/.maestro/financial-critical.yaml`
- **Testes/validacoes executadas:** `mobile/src/__tests__/invalidarAposTransacao.test.ts` (3 casos
  novos — invalida as duas queries da Home; invalida a lista compartilhada mais as chaves extras da
  edição; sem chave duplicada na lista compartilhada) dentro de Jest 200/200 no mobile;
  `npx tsc --noEmit` limpo. O flow Maestro `financial-critical` **não foi executado nesta rodada**
  (sem simulador disponível) — a correção foi validada por leitura de código e pelos testes
  unitários da invalidação, não pela execução ponta a ponta do flow.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** critério de aceite completo de BACKLOG-0095 (`financial-critical` verde ponta a
  ponta) depende da execução real do flow em simulador, que fica pendente.
- **Commit:** pendente

---

## BUG-0070 — Erros do backend chegavam genéricos ao app (login, cadastro e bloqueio de conta)

- **Problemas relacionados:** PROB-0083, BACKLOG-0094, BUG-0069
- **Data:** 2026-08-21
- **Area:** mobile, auth
- **Sintoma:** Três mensagens diferentes chegavam ao usuário como texto genérico:
  senha errada no login (`422 BUSINESS_ERROR "Email ou senha incorretos"`) e e-mail já cadastrado
  (`422 BUSINESS_ERROR "Email já cadastrado!"`) viravam "Dados inválidos. Verifique os campos.";
  rate limit e bloqueio de conta por tentativas (`429 RATE_LIMIT` / `429 ACCOUNT_LOCKED`, com
  `Retry-After`) viravam "Erro inesperado. Tente novamente." — o usuário era bloqueado por 60s ou
  15min sem nenhuma explicação.
- **Causa raiz:** `mobile/src/services/api.ts` colapsava o envelope `ApiError`
  (`{code, message, details}`) numa única `userMessage`: em 400/422 lia só `details` (ignorando
  `message` quando `details` era nulo) e não tinha ramo para 429. O mapa `details` (campo→mensagem)
  também nunca chegava às telas, o que impedia erro por campo em qualquer formulário do app.
- **Correcao aplicada:** o interceptor passou a preservar o envelope inteiro no erro enriquecido
  (`codigo`, `campos`, `status`, `retryAfterSegundos`), com precedência explícita em 400/422
  (detalhe de campo → mensagem de negócio → fallback) e ramo dedicado para 429 (mensagem do
  backend ou tempo do `Retry-After`). Novo `mobile/src/utils/erros.ts` centraliza a leitura
  (`mensagemDeErro`, `camposDeErro`, `chavesDeErro`, `ehSessaoExpirada`, `segundosParaTentarDeNovo`).
  O contorno local do BUG-0069 em `ajustes.tsx` foi removido, já que a origem está corrigida.
- **Arquivos alterados:** `mobile/src/services/api.ts`, `mobile/src/types/index.ts`,
  `mobile/src/utils/erros.ts` (novo), `mobile/app/(app)/ajustes.tsx`,
  `mobile/src/__tests__/AjustesScreen.test.tsx`
- **Testes/validacoes executadas:** `mobile/src/__tests__/apiErros.test.ts` (7 casos: 422 com e sem
  `details`, preservação de todos os campos, 429 `ACCOUNT_LOCKED`, 429 só com `Retry-After`, 401 em
  rota de auth, falha de rede). Verificação em runtime contra backend local (porta 8094, banco
  descartável `gf_auth_v2`): `422 {"code":"BUSINESS_ERROR","message":"Email ou senha incorretos"}`,
  `422 "Email já cadastrado!"` e `429 {"code":"RATE_LIMIT","message":"Muitas tentativas. Aguarde 60
  segundos e tente novamente."}` com `Retry-After: 50`.
- **Resultado:** PASS

---

## BUG-0071 — Cadastro criava contas duplicadas por diferença de maiúsculas no e-mail

- **Data:** 2026-08-21
- **Area:** backend, mobile, auth
- **Sintoma:** `Alice@x.com` e `alice@x.com` criavam duas contas distintas; quem cadastrava com
  maiúscula e depois digitava minúsculo (ou o contrário) recebia "Email ou senha incorretos" e não
  conseguia mais entrar na própria conta.
- **Causa raiz:** nenhuma ponta normalizava o e-mail. `AuthController.register` usava
  `findByEmail` exato e gravava o valor cru; o índice `UNIQUE` do Postgres é sensível a caixa; o
  app só fazia `trim()`.
- **Correcao aplicada:** backend grava `email.trim().toLowerCase()` e checa duplicidade com
  `existsByEmailIgnoreCase`; login e recuperação de senha passaram por `buscarPorEmail`, que tenta
  o casamento exato primeiro (preserva contas legadas com maiúsculas) e só cai no
  `findAllByEmailIgnoreCase` quando existe exatamente uma conta equivalente — nunca escolhe entre
  duas contas legadas ambíguas. No app, `normalizarEmail` é aplicado no cadastro.
- **Arquivos alterados:** `backend/.../controller/AuthController.java`,
  `backend/.../repository/UsuarioRepository.java`, `mobile/src/utils/validate.ts`,
  `mobile/src/services/authService.ts`, `mobile/app/(auth)/register.tsx`
- **Testes/validacoes executadas:** `AuthControllerTest` 28/28 PASS, com três casos novos
  (normalização para minúsculo, duplicidade recusada com caixa diferente, login aceitando caixa
  diferente da cadastrada). Runtime contra backend local: cadastro de `Ana.Souza@Teste.com` gravou
  `ana.souza@teste.com`, segundo cadastro em minúsculo devolveu 422 e login com `ANA.souza@teste.com`
  autenticou.
- **Resultado:** PASS

---

## BUG-0072 — Checklist "Complete seu setup" sumiu da home no redesign de navegação

- **Data:** 2026-08-21
- **Area:** mobile
- **Sintoma:** Usuário novo terminava o onboarding e chegava na home com 1 conta, 0 categorias e 0
  cartões, sem nenhum convite a continuar o setup. `src/store/homeChecklist.ts` e seu teste
  continuavam no repositório, mas nenhuma tela importava o store.
- **Causa raiz:** regressão silenciosa — o checklist entrou em `f0b27de` (PR-F3-10) em
  `mobile/app/(app)/index.tsx` e foi perdido quando a home mudou de arquivo no redesign de
  navegação (`9a3b205`), confirmado por `git log -S homeChecklist -- mobile/app`. O checklist era
  justamente a contrapartida que justificou reduzir o onboarding a uma etapa única (PR-F3-09).
- **Correcao aplicada:** checklist restaurado em `mobile/app/(app)/(inicio)/index.tsx`, agora no
  padrão visual novo (`Card` + `ListRow` + `Entrance`) e derivado apenas das queries que a home já
  faz (nenhum request extra): primeira movimentação, categorias, primeira meta e cartão. Continua
  dispensável de vez pelo `dismissHomeChecklist` já existente.
- **Arquivos alterados:** `mobile/app/(app)/(inicio)/index.tsx`
- **Testes/validacoes executadas:** `npx tsc --noEmit` e `npm run lint` limpos; suíte mobile
  194/194 PASS. Validação visual em simulador **não executada** (ver ressalva do relatório).
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** derivação de "cartão cadastrado" usa `saldoEmCartoes`/`totalFaturas` do agregado
  `/v1/home` — um cartão sem fatura e sem saldo mantém o item visível até o primeiro uso.

---

## BUG-0058 — Hardening de release, LGPD e acessibilidade mobile

- **Problemas relacionados:** BACKLOG-0073, BACKLOG-0075, BACKLOG-0076, BACKLOG-0077, BACKLOG-0078, BACKLOG-0079
- **Data:** 2026-07-13
- **Area:** mobile, release mobile, acessibilidade, LGPD, observabilidade
- **Baseline:** `807e777`; implementação em `2db9b58`.
- **Escopo:** exclusivamente `mobile/` e passos/jobs mobile em `.github/workflows`; nenhuma alteração de frontend web ou backend.
- **Correcao aplicada:** política de privacidade nativa versão `2026-07` antes do consentimento; alvos de toque >=44pt, labels e alertas acessíveis; dashboard sem hero/efeitos promocionais; ESLint a11y bloqueante e TypeScript independente; Sentry sem PII com release SHA/ambiente e source maps condicionais; workflows de Android/iOS Release e Maestro Android/iOS; smokes de login, recuperação e privacidade.
- **Falha encontrada durante validação:** plugin Sentry inicialmente forçava upload sem organização/projeto e quebrava Android Release. Causa removida: plugin de build só entra com token+org+project; runtime continua disponível e CI de release exige todos os secrets.
- **Arquivos principais:** `mobile/app.config.js`, `mobile/metro.config.js`, `mobile/src/observability/sentry.ts`, `mobile/app/(auth)/privacidade.tsx`, componentes/telas mobile, `mobile/.eslintrc.cjs`, `mobile/.maestro/*`, `.github/workflows/mobile-release.yml`, `.github/workflows/mobile-maestro.yml`, job mobile de `.github/workflows/ci.yml`.
- **Validacoes:** Expo Doctor 18/18; TypeScript PASS; ESLint a11y zero warnings; Jest 11/11; Android `assembleRelease` PASS (472 tasks); iOS Simulator Release `BUILD SUCCEEDED`; npm runtime zero high/critical (14 moderate + 1 low do toolchain já registrados); YAML e `git diff --check` PASS.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** CI remoto, secrets/projeto Sentry, Maestro contra staging, VoiceOver/TalkBack, hardware físico, assinatura e publicação em stores pendentes. Não fecha integralmente BACKLOG-0073/75/76/77/78/79.
- **Relatorio:** `REVIEW_REPORTS/2026-07-13_mobile-release-hardening-implementation.md`
- **Commit:** `2db9b58`

---

## BUG-0057 — Dependencias runtime e gates SCA corrigidos

- **Problema relacionado:** BACKLOG-0072
- **Data:** 2026-07-13
- **Area:** backend, frontend, mobile, seguranca, CI
- **Sintoma:** auditorias de runtime reportavam vulnerabilidades high/critical no mobile e o backend nao possuia SCA Maven bloqueante. Primeira execucao do OWASP Dependency-Check confirmou CVEs em Spring Boot/Framework/Security, Jackson, PostgreSQL JDBC, Tomcat e Log4j2.
- **Causa raiz:** lock mobile mantinha `axios` e transitivas vulneraveis; backend permanecia na matriz Spring Boot 3.5.7 de 2025; CI executava testes/builds sem bloquear dependencias vulneraveis.
- **Correcao aplicada:** `axios` e transitivas mobile atualizados sem upgrade major do Expo; Spring Boot atualizado para 3.5.16 com patches PostgreSQL JDBC 42.7.13, Tomcat 10.1.57 e Log4j2 2.25.5; OWASP Dependency-Check 12.2.2 adicionado em profile Maven com limite CVSS 7 e `failOnError=true`; CI ganhou cache NVD semanal, relatorio como artifact e `npm audit --omit=dev --audit-level=high` web/mobile.
- **Risco residual:** 14 moderate e 1 low no grafo npm mobile pertencem ao toolchain Expo/RN e exigem upgrade major para Expo 57. Excecao temporaria, mitigacao, responsavel e prazo registrados em `docs/SECURITY_DEPENDENCY_RISK_REGISTER.md`. Zero critical/high permanece aceito.
- **Testes/validacoes executadas:** npm audit web/mobile PASS no limite high; web lint/build/test PASS; mobile Expo Doctor 18/18, TypeScript e Jest 11/11 PASS; backend `clean verify` PASS; OWASP Dependency-Check/NVD PASS com zero dependencias CVSS >= 7.
- **Resultado:** PASS

---

## BUG-0056 — Build nativo Expo 54 desbloqueado

- **Problema relacionado:** BACKLOG-0071
- **Data:** 2026-07-13
- **Area:** mobile, release, CI
- **Sintoma:** `expo-doctor` falhava 2/18 grupos de checks e Android não compilava: Reanimated `4.5.1` exigia RN `0.83–0.86`, mas Expo SDK 54 usa RN `0.81.5`; Worklets `0.10.2` acompanhava a versão incompatível. App config também declarava `android.usesCleartextTraffic` fora do schema Expo.
- **Causa raiz:** `nativewind` estava declarado sem Babel/Metro/Tailwind config e sem qualquer `className`; seu peer `react-native-reanimated >=3.6.2` ficou indireto e npm escolheu latest incompatível. Expo Router também resolveu `react-dom@19.2.7` contra React `19.1.0` por falta do peer web direto.
- **Correcao aplicada:** stack NativeWind/Tailwind dormente e import CSS removidos; Expo `54.0.35`, Linking `8.0.12`, Router `6.0.24`, React DOM `19.1.0`, React Native Web `0.21.x`, Reanimated `4.1.x` e Worklets `0.5.1` alinhados à matriz do SDK 54; `expo-system-ui` instalado; `usesCleartextTraffic` removido do app config; `expo-doctor` adicionado ao job mobile do CI. Produção Android não permite cleartext; permissão permanece somente nos manifests debug gerados pelo Expo.
- **Arquivos alterados:** `mobile/package.json`, `mobile/package-lock.json`, `mobile/app.json`, `mobile/app/_layout.tsx`, remoção de `mobile/src/global.css`, `.github/workflows/ci.yml`, `docs/BACKLOG.md`, `docs/BUGFIX_LOG.md`.
- **Testes/validacoes executadas:** `npm ci` PASS; `expo-doctor` 18/18; TypeScript PASS; Jest 11/11; export web PASS; prebuild limpo PASS; Android debug/release PASS; iOS Release arm64 para destino genérico + Release para Simulator PASS; smoke iPhone 17 Simulator abriu login sem crash; Android release sem `usesCleartextTraffic`; iOS `NSAllowsArbitraryLoads=false`.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** BACKLOG-0071 não foi fechado: nenhum hardware físico Android/iOS estava conectado para o smoke obrigatório. `npm audit --omit=dev` ainda reporta 1 critical e 4 high; tratamento pertence ao BACKLOG-0072 e não foi iniciado.
- **Commits:** `af6f2be` (stack Expo/Jest), `17b6246` (gates CI)

---

## BUG-0055 — Cronograma canonico, seguranca e gates finais

- **Data:** 2026-07-13
- **Area:** backend, web, mobile, banco, seguranca
- **Correcao aplicada:** Cartao usa somente `FaturaLancamento`; projecao calcula saldo restante sem duplicidade/rollover; novo `/api/v1/transacoes/{id}/cronograma`; mobile migrado; JJWT 0.13.0; secrets sem defaults; auth/dashboard tipados; manutencao removida da API; SMTP, ArchUnit, JaCoCo e Jest adicionados.
- **Compatibilidade:** `/api/v1/parcelas/**` continua para nao-cartao; leitura legada anuncia depreciacao e mutacao de cartao retorna 410. V27 permanece staged ate Release B.
- **Validacoes:** backend `mvn verify` PASS (151 testes; 74% linhas elegiveis; criticos >=85%); Flyway PostgreSQL 16 PASS; frontend lint (0 erros), build + 8 testes PASS; mobile TypeScript + 11 testes PASS.
- **Ressalvas:** Frontend ainda reporta 98 warnings ESLint historicos. Operacao VPS e V27 nao executadas.
- **Resultado:** PASS_COM_RESSALVA
- **Commits:** `74b643a` (cronograma), `311f293` (JWT/secrets), `7e4c34a` (DTOs), `279a92f` (maintenance), `db463dd` (cobertura)

---

## BUG-0054 — Backfill de resíduo de arredondamento em parcelas/faturas antigas

- **Problema relacionado:** BACKLOG-0051
- **Data:** 2026-07-11
- **Area:** backend, banco, integridade financeira
- **Sintoma:** Compras parceladas criadas antes da correção de arredondamento (BUG-0017/PROB-0038) podiam ter `SUM(parcelas.valor)` ou `SUM(fatura_lancamentos.valor)` diferente de `transacao.valor_total`, deixando centavos residuais em históricos de parcelas/faturas.
- **Causa raiz:** Antes da correção, o valor arredondado da parcela era replicado em todas as parcelas/lançamentos; a última parcela não absorvia o resto para fechar exatamente o total.
- **Correcao aplicada:** Novo diagnóstico SQL read-only `scripts/diagnose-rounding-residue-backfill.sql`; novo `ParcelamentoRoundingBackfillService` com dry-run/correção idempotente por usuário, exposto somente pelo runner offline `maintenance`. A correção ajusta somente casos seguros: última `Parcela` e último `FaturaLancamento` `COMPRA`, recalculando `FaturaCartao.valorTotal` pela diferença e ajustando `Conta.valorGasto` apenas quando a fatura ainda não está `PAGA`. Transações com `AJUSTE`/`ESTORNO`/rollover ficam fora da correção automática.
- **Arquivos alterados:** `ParcelaRepository.java`, `FaturaLancamentoRepository.java`, `ParcelamentoRoundingBackfillService.java`, `ParcelamentoRoundingBackfillResult.java`, `TransacaoController.java`, `scripts/diagnose-rounding-residue-backfill.sql`, `ParcelamentoRoundingBackfillServiceTest.java`, `docs/BACKLOG.md`, `docs/BUGFIX_LOG.md`.
- **Testes/validacoes executadas:** `./mvnw -q -Dtest=ParcelamentoRoundingBackfillServiceTest test` PASS (fora do sandbox por exigência do Mockito/ByteBuddy). Diagnóstico read-only no Postgres local (`docker compose exec -T postgres psql -U postgres -d gestor_financeiro < scripts/diagnose-rounding-residue-backfill.sql`) retornou 0 resíduos em `parcelas`, 0 em faturas seguras e 0 casos manuais.
- **Resultado:** PASS
- **Ressalvas:** Diagnóstico local não representa produção/staging. Rodar job offline `rounding-residue` antes de `--apply`.
- **Commit:** `61e18f5`

---

## BUG-0052 — Fechamento de P3 baixo em fatura, mobile UX e Docker

- **Problema relacionado:** BACKLOG-0034, BACKLOG-0035, BACKLOG-0046, BACKLOG-0049, BACKLOG-0053, BACKLOG-0055, BACKLOG-0057, BACKLOG-0069, PROB-0060
- **Data:** 2026-07-11
- **Area:** backend, frontend, mobile, infra, documentacao
- **Sintoma:** Itens P3 baixos ainda abertos: logout mobile sem confirmacao, Dashboard mobile sem pull-to-refresh, swap Vim sem ignore, pagamento parcial de fatura sem suporte, credito de cartao exibido como gasto negativo, edicao de compra parcelada redistribuindo restante por parcelas abertas, badge de ajuste/estorno sem paridade web e Dockerfile backend pulando testes.
- **Correcao aplicada:**
  1. Mobile perfil passou a confirmar logout com `Alert.alert`.
  2. Dashboard mobile recebeu `RefreshControl` refazendo resumo, transacoes recentes, projecao e insights.
  3. `.gitignore` passou a bloquear `*.swp`, `*.swo` e `*.swpx`.
  4. `pagarFatura` passou a aceitar pagamento parcial com lock pessimista na fatura, acumulando `valorPago`, liberando limite pelo valor pago e marcando `PAGA` apenas ao quitar o saldo restante; web/mobile enviam `Idempotency-Key`.
  5. Web/mobile exibem `Conta.valorGasto < 0` como credito disponivel, nao gasto negativo.
  6. Edicao de compra parcelada recalcula o cronograma cheio; parcelas pagas ficam imutaveis e a diferenca vira `AJUSTE` na proxima fatura aberta.
  7. Web ganhou badge de tipo `AJUSTE`/`ESTORNO` com remocao do prefixo textual, em paridade com mobile.
  8. `backend/Dockerfile` removeu `-DskipTests`; build de imagem agora roda testes.
- **Arquivos alterados:** `backend/Dockerfile`, `.gitignore`, `FaturaController.java`, `FaturaService.java`, `FaturaCartaoRepository.java`, `FaturaCartaoWorkflowTest.java`, `mobile/app/(app)/perfil.tsx`, `mobile/app/(app)/index.tsx`, `mobile/app/(app)/more/faturas.tsx`, `mobile/src/services/faturaService.ts`, `frontend/src/pages/Faturas.tsx`, `frontend/src/pages/contas.tsx`, `frontend/src/services/faturaService.ts`, `docs/BACKLOG.md`, `docs/DEPLOY.md`, `docs/PROBLEM_LEDGER.md`, `docs/SYSTEM_OVERVIEW.md`
- **Testes/validacoes executadas:** `./mvnw -q -Dtest=FaturaCartaoWorkflowTest test` PASS; `./mvnw -q test` PASS; `frontend npm run build -- --mode production` PASS; `mobile npm run lint` (`tsc --noEmit`) PASS.
- **Resultado:** PASS
- **Ressalvas:** Fatura com total zero/negativo por estorno puro continua sem rollover explicito (BACKLOG-0054).
- **Commit:** `a62f594`, `70f24e5`, `85277b7`, `2448089`, `9e4711e`

---

## BUG-0051 — PROB MEDIUM: rate limit distribuído, sessão mobile, duplo clique financeiro, PostgreSQL real e backup seguro

- **Problema relacionado:** PROB-0031, PROB-0048, PROB-0055, PROB-0056, PROB-0057, PROB-0058, PROB-0059
- **Data:** 2026-07-11
- **Area:** backend, frontend, mobile, infra, segurança, operação
- **Sintoma:** PROBs MEDIUM abertos cobriam duplo clique financeiro nos clientes, rate limit local em memória, contrato CSRF mobile ambíguo, validação PostgreSQL real dependente de Testcontainers quebrado no host, backup sem criptografia/restore drill e field injection em módulos centrais.
- **Correcao aplicada:**
  1. `LoginRateLimitFilter` passou a usar `RateLimitService` com tabela `rate_limit_buckets` e lock pessimista (`V24__rate_limit_buckets.sql`), eliminando `ConcurrentHashMap` local.
  2. Contrato de sessão separado: web usa cookie HttpOnly + CSRF; mobile usa refresh token no body/SecureStore, sem `Set-Cookie` e com bloqueio de cookie em request mobile.
  3. Web/mobile receberam locks/disabled para ações financeiras críticas: pagar fatura, movimentar carteira, pagar/pular conta fixa e reservar meta.
  4. `PostgresMigrationIT` aceita PostgreSQL externo; `scripts/verify-postgres-migrations.sh` sobe PostgreSQL via Docker CLI e virou gate no CI.
  5. Backup passou a exigir criptografia (`BACKUP_GPG_RECIPIENT` ou `BACKUP_ENCRYPTION_PASSPHRASE`), restore aceita `.gpg`, e `restore-drill-db.sh` automatiza drill em banco descartável; compose VPS gera `.sql.gz.gpg`.
  6. Sweep completo de `@Autowired` em `backend/src/main/java`: controllers, services, config e security passaram para constructor injection com dependencias `final` e `@RequiredArgsConstructor`.
- **Arquivos alterados:** `LoginRateLimitFilter.java`, `RateLimitService.java`, `RateLimitBucket.java`, `RateLimitBucketRepository.java`, `V24__rate_limit_buckets.sql`, `RefreshTokenCsrfFilter.java`, `AuthController.java`, `mobile/src/services/api.ts`, `mobile/src/services/authService.ts`, `frontend/src/pages/Faturas.tsx`, `frontend/src/pages/Carteira.tsx`, `frontend/src/pages/ContasFixas.tsx`, `frontend/src/pages/Metas.tsx`, `mobile/app/(app)/more/faturas.tsx`, `mobile/app/(app)/more/contas-fixas.tsx`, `PostgresMigrationIT.java`, `scripts/verify-postgres-migrations.sh`, `scripts/backup-db.sh`, `scripts/restore-db.sh`, `scripts/restore-drill-db.sh`, `docker-compose.vps.yml`, `deploy/vps/Dockerfile.postgres-backup`, `.github/workflows/ci.yml`
- **Testes/validacoes executadas:** `AuthControllerTest,SecurityTest` PASS; backend `./mvnw -q test` PASS; frontend `npm run build -- --mode production` PASS; mobile `npm run lint` (`tsc --noEmit`) PASS; `scripts/verify-postgres-migrations.sh` PASS; `bash -n scripts/backup-db.sh scripts/restore-db.sh scripts/restore-drill-db.sh` PASS; `rg "@Autowired" backend/src/main/java` sem ocorrencias; `nc -vz 127.0.0.1 8081` confirmou porta 8081 sem backend local.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** `mvn verify -Pintegration-test` ainda falha neste host porque Testcontainers recebe resposta inválida do socket Docker Desktop, apesar do Docker CLI rodar containers. O gate canônico agora é `scripts/verify-postgres-migrations.sh`. Testes Spring ainda usam `@Autowired`, aceitavel para testes de integracao/contexto Spring.
- **Commit:** pendente

---

## BUG-0047 — logout-all quebrado (NPE/500) por skip do filtro JWT em /api/auth/**

- **Problema relacionado:** Auditoria de segurança 2026-07-10
- **Data:** 2026-07-10
- **Area:** backend, segurança
- **Sintoma:** `POST /api/auth/logout-all` sempre retornava 500. Nenhum dispositivo conseguia fazer logout global.
- **Causa raiz:** `JwtAuthenticationFilter` fazia early-return para todo path iniciado por `/api/auth/`, nunca populando o `SecurityContext`. O endpoint `AuthController.logoutAll(Authentication authentication)` recebia `authentication == null` e estourava `NullPointerException` em `authentication.getName()`. Além disso, `/api/auth/**` estava como `permitAll`, então mesmo com token o Spring não exigia autenticação nessa rota.
- **Correcao aplicada:**
  1. `JwtAuthenticationFilter`: removido o early-return por prefixo `/api/auth/`. O filtro agora sempre popula o `SecurityContext` quando há Bearer token válido; ausência de token é inofensiva (rotas públicas continuam liberadas no `SecurityConfig`).
  2. `SecurityConfig`: adicionado matcher específico `/api/auth/logout-all` → `authenticated()` **antes** do `permitAll` de `/api/auth/**` (ordem importa; match mais específico primeiro). Garante `Authentication` não-nulo no controller.
- **Arquivos alterados:** `config/JwtAuthenticationFilter.java`, `config/SecurityConfig.java`
- **Testes/validacoes executadas:** `mvn -o compile` — BUILD SUCCESS.
- **Resultado:** PASS
- **Ressalvas:** Sem teste de integração automatizado do fluxo logout-all ainda. Recomendado adicionar MockMvc cobrindo 200 com token válido e 401 sem token.
- **Commit:** pendente

---

## BUG-0048 — Actuator health expunha detalhes de infra a anônimos (perfil vps)

- **Problema relacionado:** Auditoria de segurança 2026-07-10
- **Data:** 2026-07-10
- **Area:** backend, infra, segurança
- **Sintoma:** No perfil `vps`, `GET /actuator/health` (público via `permitAll`) retornava detalhes de componentes (status de banco, disco, etc.) para requisições anônimas.
- **Causa raiz:** `management.endpoint.health.show-details=always` combinado com `/actuator/health` em `permitAll`.
- **Correcao aplicada:** `application-vps.properties`: `show-details=when-authorized`. Anônimos recebem apenas `UP`/`DOWN`; detalhes só para requisições autenticadas. Perfil `prod` já usava `never` (inalterado); `dev` mantido `always` por ser ambiente local.
- **Arquivos alterados:** `application-vps.properties`
- **Testes/validacoes executadas:** `mvn -o compile` — BUILD SUCCESS.
- **Resultado:** PASS
- **Ressalvas:** Nenhuma.
- **Commit:** pendente

---

## BUG-0001 — ddl-auto=update em produção substituído por Flyway

- **Problema relacionado:** PROB-0006
- **Data:** 2026-07-07
- **Area:** backend, banco, infra
- **Sintoma:** Hibernate `ddl-auto=update` em produção — risco de alteração destrutiva de schema, schema drift entre ambientes e perda de previsibilidade.
- **Causa raiz:** Configuração `spring.jpa.hibernate.ddl-auto=update` em `application-prod.properties` e `application.properties`. Ausência de migrations versionadas.
- **Correcao aplicada:**
  1. Adicionado `flyway-database-postgresql` ao `pom.xml`.
  2. Criada migration baseline `V1__baseline_schema.sql` com DDL das 10 tabelas existentes.
  3. `application.properties`: `ddl-auto=validate`, `flyway.enabled=true`, `baseline-on-migrate=true`.
  4. `application-prod.properties`: `ddl-auto=validate`, `flyway.enabled=true`.
  5. `application-test.properties`: `flyway.enabled=false` (H2 não suporta migrations PostgreSQL).
  6. `DEPLOY.md` atualizado para documentar migrations Flyway.
- **Arquivos alterados:** `pom.xml`, `application.properties`, `application-prod.properties`, `application-test.properties`, `V1__baseline_schema.sql`, `DEPLOY.md`
- **Testes/validacoes executadas:** `mvn test` — 13/13 passaram.
- **Resultado:** PASS
- **Ressalvas:** Na execução original, validação PostgreSQL real não rodou localmente. Fechada posteriormente em 2026-07-08 com smoke VPS: Flyway 14 migrations + schema JPA OK.
- **Commit:** pendente

---

## BUG-0003 — Optimistic locking e @Transactional adicionados

- **Problema relacionado:** PROB-0002, PROB-0012
- **Data:** 2026-07-07
- **Area:** backend
- **Sintoma:** Race conditions em Carteira, Meta, Conta e Categoria sem @Version. Operacoes de escrita sem @Transactional — risco de gravacao parcial e inconsistencia.
- **Causa raiz:** Ausencia de optimistic locking nas entidades com valores acumulados. Ausencia de @Transactional na maioria dos metodos write.
- **Correcao aplicada:**
  1. @Version adicionado em Carteira, Conta, Meta, Categoria.
  2. Migration V2 para colunas version.
  3. OptimisticLockingFailureException tratado no GlobalExceptionHandler → 409 Conflict.
  4. @Transactional adicionado em todos os metodos write de 6 services.
- **Arquivos alterados:** `Carteira.java`, `Conta.java`, `Meta.java`, `Categoria.java`, `GlobalExceptionHandler.java`, 6 services, `V2__optimistic_locking_columns.sql`, `FinancialIntegrityTest.java`
- **Testes/validacoes executadas:** `mvn test` — 29/29 passaram incluindo FinancialIntegrityTest.
- **Resultado:** PASS
- **Ressalvas:** Concorrencia real testada apenas com H2. Validacao com PostgreSQL pendente.
- **Commit:** pendente

---

## BUG-0002 — IDOR corrigido em TransacaoService, ContaService e ContaFixaService

- **Problema relacionado:** PROB-0001, PROB-0021
- **Data:** 2026-07-07
- **Area:** backend, seguranca
- **Sintoma:** Criacao de transacao aceitava categoriaId/contaId de outro usuario. Atualizacao de gasto em conta e categoria de outro usuario. ContaFixa aceitava categoriaId de outro usuario. CarteiraService possuia overload deletar(Long) sem ownership.
- **Causa raiz:** Uso de `findById()` sem filtro de usuarioId em TransacaoService.criar(), TransacaoService.deletar(), ContaService.adicionarGasto(), ContaService.removerGasto(), ContaFixaService.criar(), ContaFixaService.atualizar(). CarteiraService.deletar(Long) overload sem ownership.
- **Correcao aplicada:**
  1. TransacaoService.criar(): `findById` → `findByIdAndUsuarioId` para categoria e conta.
  2. TransacaoService.deletar(): `findById` → `findByIdAndUsuarioId` para categoria.
  3. ContaService.adicionarGasto/removerGasto: adicionado parametro usuarioId e uso de `findByIdAndUsuarioId`.
  4. CarteiraService.deletar(Long) sem ownership removido.
  5. ContaFixaService.criar/atualizar: validacao de categoriaId via `findByIdAndUsuarioId`.
- **Arquivos alterados:** `TransacaoService.java`, `ContaService.java`, `CarteiraService.java`, `ContaFixaService.java`, `TransacaoControllerTest.java`, `TestDataFactory.java`
- **Testes/validacoes executadas:** `mvn test` — 25/25 passaram (incluindo 2 novos testes IDOR de cross-user categoriaId e contaId).
- **Resultado:** PASS
- **Ressalvas:** Nenhuma.
- **Commit:** pendente

---

## BUG-0004 — Performance de consultas críticas corrigida

- **Problema relacionado:** PROB-0003, PROB-0004, PROB-0020
- **Data:** 2026-07-07
- **Area:** backend, banco
- **Sintoma:** findAll() massivo em ParcelaService e ContaFixaService. Dashboard agregacoes em memoria. CarteiraService scan de categorias. Contagens via size() em lista carregada.
- **Correcao aplicada:**
  1. ParcelaRepository: atualizarStatusParcelasAtrasadas via JPQL UPDATE.
  2. ContaFixaRepository: resetarContasPagasVencidas + atualizarStatusContasAtrasadas.
  3. TransacaoRepository: 3 queries SUM para dashboard.
  4. Repositories: countBy queries para contagens.
  5. CarteiraRepository.sumSaldoByUsuarioId. CategoriaRepository.findByNomeIgnoreCase.
  6. Migration V3: 11 indices de performance.
- **Arquivos alterados:** 7 repositories, 3 services, DashboardService, V3__performance_indexes.sql
- **Testes/validacoes executadas:** mvn test → 29/29 PASS, BUILD SUCCESS
- **Resultado:** PASS
- **Ressalvas:** Performance real validada apenas com H2.
- **Commit:** pendente

---

## BUG-0005 — Segurança de sessão, CORS, rate limit e logs

- **Problema relacionado:** PROB-0005, PROB-0008, PROB-0009, PROB-0010, PROB-0011
- **Data:** 2026-07-07
- **Area:** backend, seguranca
- **Sintoma:** Cookie sem Secure em prod. CORS fallback localhost em prod. Rate limit apenas login/forgot-password. Email e token em logs.
- **Correcao aplicada:** cookie.secure=true prod. CORS fallback removido. Rate limit register/reset/validate-token. EmailService maskEmail, token nunca logado.
- **Arquivos alterados:** application-prod.properties, LoginRateLimitFilter.java, EmailService.java
- **Testes/validacoes executadas:** mvn test → 29/29 PASS, BUILD SUCCESS
- **Resultado:** PASS
- **Ressalvas:** CSRF dispensado (JWT stateless + SameSite=Lax). PROB-0009 parcial (dev mantem defaults).
- **Commit:** pendente

---

## BUG-0006 — Contrato de erro padronizado com requestId

- **Problema relacionado:** N/A (melhoria estrutural Fase 0)
- **Data:** 2026-07-07
- **Area:** backend, observabilidade
- **Sintoma:** Erros sem requestId — difícil rastrear falhas entre frontend, mobile e logs.
- **Correcao aplicada:** ApiError +requestId. RequestIdFilter UUID/MDC/X-Request-Id. GlobalExceptionHandler inclui requestId em todos erros. Log 500 com requestId.
- **Arquivos alterados:** ApiError.java, RequestIdFilter.java (novo), GlobalExceptionHandler.java
- **Testes/validacoes executadas:** mvn test → 29/29 PASS
- **Resultado:** PASS
- **Ressalvas:** Health check de banco ja incluso via DataSourceHealthIndicator do Actuator.
- **Commit:** pendente

---

## BUG-0007 — Política de senha, account lockout e memory leak do rate limit

- **Problema relacionado:** PROB-0007, PROB-0023, PROB-0024
- **Data:** 2026-07-07
- **Area:** backend, seguranca
- **Sintoma:** Senhas de 6 caracteres sem complexidade aceitas. Sem lockout de conta apos falhas consecutivas. Rate limit ConcurrentHashMap crescia indefinidamente sem limpeza proativa.
- **Correcao aplicada:**
  1. @ValidPassword: min 8 caracteres, ao menos 1 letra e 1 numero.
  2. Validacao aplicada em RegisterRequest e ResetPasswordRequest.
  3. Campos failedAttempts e lockedUntil na entidade Usuario.
  4. Migration V4: colunas failed_attempts e locked_until.
  5. Account lockout no AuthController com configs max-failed-attempts e lockout-minutes.
  6. AccountLockedException + handler 429 ACCOUNT_LOCKED.
  7. @Scheduled cleanup a cada 60s no LoginRateLimitFilter.
  8. @EnableScheduling na FinanceiroApplication.
  9. docker-compose.yml com PostgreSQL 17-alpine.
  10. application-dev.properties para validacao local com PostgreSQL.
  11. LOCAL_POSTGRES_VALIDATION.md com instrucoes.
- **Arquivos alterados:** 18 arquivos (ver relatorio PR-FOUNDATION-07).
- **Testes/validacoes executadas:** mvn test → 34/34 PASS, BUILD SUCCESS
- **Resultado:** PASS
- **Ressalvas:** Na execução original, PostgreSQL validation com Docker nao executou neste ambiente. Fechada posteriormente em 2026-07-08 com smoke VPS.
- **Commit:** pendente

---

## BUG-0008 — Correções pós-auditoria de CORS, CSRF, rate limit e VPS

- **Problema relacionado:** PROB-0008, PROB-0010, PROB-0019, PEND-001, PEND-002
- **Data:** 2026-07-07
- **Area:** backend, frontend, seguranca, banco
- **Sintoma:** CORS de produção ainda podia herdar fallback localhost pelo `@Value` do código. `validate-token` era `GET`, mas o rate limit só processava `POST`. Refresh/logout usavam cookie HttpOnly sem defesa CSRF ponta a ponta. Validação PostgreSQL real não tinha profile para VPS informada.
- **Correcao aplicada:**
  1. `SecurityConfig` passou a ler `cors.allowed.origins`, respeitando profile prod com default vazio.
  2. `LoginRateLimitFilter` passou a limitar `GET /api/auth/validate-token`.
  3. Criado `RefreshTokenCsrfFilter` para exigir `X-CSRF-Token` em `refresh-token` e `logout` quando `refreshToken` cookie existe.
  4. `AuthController` passou a emitir/rotacionar cookie `csrfToken` e limpar refresh + CSRF no logout.
  5. Frontend envia `X-CSRF-Token` automaticamente em refresh/logout, inclusive no refresh do interceptor.
  6. Logs de payload de cadastro removidos do frontend.
  7. Profile padrão do backend passou a ser `vps`; `application-dev.properties` aceita override por env; `application-vps.properties` e `application-prod.properties` apontam para `187.77.61.191:5433/dbnexos-gestor-financeiro`.
  8. `LOCAL_POSTGRES_VALIDATION.md` documenta execução com profile `vps`.
  9. Conectividade TCP com `187.77.61.191:5433` validada.
  10. Smoke Spring Boot contra profile `vps` tentou conectar no PostgreSQL remoto; servidor respondeu, mas rejeitou senha para `admin_nexos`.
- **Arquivos alterados:** `SecurityConfig.java`, `LoginRateLimitFilter.java`, `RefreshTokenCsrfFilter.java`, `AuthController.java`, `AuthControllerTest.java`, `api.ts`, `authService.ts`, `application.properties`, `application-dev.properties`, `application-prod.properties`, `application-vps.properties`, `logback-spring.xml`, `.env.example`, `README-backend.md`, `LOCAL_POSTGRES_VALIDATION.md`, `CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md`
- **Testes/validacoes executadas:** `./mvnw -q -Dtest=AuthControllerTest test` -> 17/17 PASS; `./mvnw -q test` -> 36/36 PASS; `npm run build` no frontend -> PASS; `nc -vz -w 5 187.77.61.191 5433` -> PASS.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Smoke Flyway/schema no PostgreSQL VPS nao executou porque a credencial de `admin_nexos` foi rejeitada.
- **Commit:** pendente

---

## BUG-0009 — Validação PostgreSQL real automatizada para Ledger

- **Problema relacionado:** PEND-001, PR-LEDGER-01
- **Data:** 2026-07-08
- **Area:** backend, banco, testes, CI
- **Sintoma:** Evolução Ledger ainda dependia de validação H2/local manual. Não havia suíte automatizada que subisse PostgreSQL real, aplicasse Flyway em banco limpo e validasse schema com Hibernate `ddl-auto=validate`.
- **Correcao aplicada:**
  1. Adicionadas dependências Testcontainers (`junit-jupiter` e `postgresql`).
  2. Criado profile Maven `integration-test` com Failsafe para testes `*IT.java`.
  3. Criado `PostgresMigrationIT` usando `postgres:16-alpine`.
  4. Criado `application-postgres-it.properties` com Flyway ativo e `baseline-on-migrate=false`.
  5. Configurado Mockito como `javaagent` no Surefire/Failsafe para evitar falha de self-attach no JDK 21.
  6. CI passou a executar `mvn verify -Pintegration-test --batch-mode`.
- **Arquivos alterados:** `.github/workflows/ci.yml`, `backend/pom.xml`, `backend/src/test/java/com/gestor/financeiro/PostgresMigrationIT.java`, `backend/src/test/resources/application-postgres-it.properties`, `LOCAL_POSTGRES_VALIDATION.md`, `LEDGER_ROADMAP_GESTOR_FINANCEIRO.md`, `CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md`
- **Testes/validacoes executadas:** `cd backend && ./mvnw -q test` -> 36/36 PASS; `docker info --format '{{.ServerVersion}}'` -> FAIL_AMBIENTE; smoke VPS em 2026-07-08 com `dbnexos_gestor` -> PostgreSQL 17.10, Flyway validou 14 migrations e schema JPA inicializou.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Testcontainers não executou localmente porque Docker daemon estava desligado (`Cannot connect to the Docker daemon`). Validação equivalente em PostgreSQL VPS real passou com usuario `dbnexos_gestor`.
- **Commit:** pendente

---

## BUG-0010 — Mapeamento `moeda` do Ledger incompatível com PostgreSQL real

- **Data:** 2026-07-08
- **Problema relacionado:** PR-LEDGER-02, PEND-001, PEND-004
- **Severidade:** MEDIA
- **Sintoma:** Smoke VPS autenticado conectou no PostgreSQL e validou Flyway, mas Hibernate `ddl-auto=validate` falhou em `movimentos_carteira.moeda`: banco tinha `CHAR(3)`/`bpchar`, enquanto o mapeamento JPA era tratado como `VARCHAR(3)`.
- **Causa raiz:** `columnDefinition = "char(3)"` documentava o DDL, mas Hibernate 6 ainda validava o atributo Java como `VARCHAR` sem tipo JDBC explícito.
- **Correção aplicada:** Adicionado `@JdbcTypeCode(SqlTypes.CHAR)` no campo `MovimentoCarteira.moeda`, alinhando JPA com a migration `V11__movimento_carteira.sql`.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/model/MovimentoCarteira.java`, `docs/BUGFIX_LOG.md`, `docs/CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md`, `docs/LOCAL_POSTGRES_VALIDATION.md`, `docs/GESTOR_FINANCEIRO_ALTO_NIVEL_PROXIMOS_PASSOS.md`, `docs/SYSTEM_OVERVIEW.md`, `docs/LEDGER_ROADMAP_GESTOR_FINANCEIRO.md`
- **Testes/validacoes executadas:** `./mvnw -q test` -> PASS; smoke VPS com `dbnexos_gestor` -> PASS; Flyway validou 14 migrations; schema JPA inicializou com PostgreSQL 17.10.
- **Resultado:** PASS
- **Ressalvas:** Nenhuma para validação VPS. Testcontainers local continua dependente de Docker ativo.
- **Commit:** pendente

---

## BUG-0011 — 500 ao criar transação com carteiraId (detached entity)

- **Problema relacionado:** PROB-0032
- **Data:** 2026-07-09
- **Area:** backend
- **Sintoma:** `POST /api/v1/transacoes` com `carteiraId` no payload retornava 500 `INTERNAL_ERROR` — "Detached entity with generated id ... Carteira.version null".
- **Causa raiz:** `TransacaoController.toEntity()` cria um stub `new Carteira()` só com o `id` recebido. `TransacaoService.criar()` não resolvia essa carteira via repository antes do `save()`, causando cascade em entidade detached sem `version` (viola `@Version`).
- **Correcao aplicada:** `TransacaoService.criar()` passou a resolver a carteira via `carteiraRepository.findByIdAndUsuarioId(id, usuarioId)` (valida ownership) e substituir o stub detached antes de persistir a transação.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`
- **Testes/validacoes executadas:** `mvn test` -> 69/69 PASS. Replicação manual do payload exato do app mobile contra API local (porta 8081) confirmando reprodução do erro antes e ausência do erro depois. Fluxo E2E: carteira inicial 1000 + entrada 3000 − saída 200 = saldo 3800.
- **Resultado:** PASS
- **Ressalvas:** Transações antigas criadas antes da correção, sem carteira resolvida, não têm movimento retroativo no Ledger (ver BACKLOG-0045).
- **Commit:** pendente

---

## BUG-0012 — Saldo total congelado (mobile não enviava carteiraId)

- **Problema relacionado:** PROB-0033
- **Data:** 2026-07-09
- **Area:** mobile
- **Sintoma:** Saldo total de carteiras/dashboard nunca mudava após o usuário criar transações pelo app.
- **Causa raiz:** `NovaTransacaoModal.tsx` não incluía `carteiraId` no payload de `POST /api/v1/transacoes`. Sem carteira associada, `TransacaoService.criar()` não registra movimento no Ledger (por design), então a transação não movimenta saldo.
- **Correcao aplicada:** Adicionado seletor de carteira (chips) no `NovaTransacaoModal`, pré-selecionando a primeira carteira do usuário. `carteiraId?: number` adicionado a `TransacaoRequest`. Invalidação de queries ampliada para incluir `carteiras` e `dashboard-projecao` após criar transação.
- **Arquivos alterados:** `mobile/src/components/NovaTransacaoModal.tsx`, `mobile/src/types/index.ts`
- **Testes/validacoes executadas:** Fluxo E2E via API com payload do mobile incluindo `carteiraId`: carteira 1000 + entrada 3000 − saída 200 = saldo 3800; delete com estorno → 4000.
- **Resultado:** PASS
- **Ressalvas:** Depende de BUG-0011 estar corrigido no backend. Transações antigas sem carteira continuam sem movimento retroativo (BACKLOG-0045).
- **Commit:** pendente

---

## BUG-0013 — Sessão mobile expira sem refresh automático (após ~15 min)

- **Problema relacionado:** PROB-0034
- **Data:** 2026-07-09
- **Area:** mobile, backend, seguranca
- **Sintoma:** Após ~15 minutos de uso, todas as chamadas autenticadas do app mobile passavam a falhar com 401, exigindo novo login manual.
- **Causa raiz:** Access token JWT expira em `900000ms` (15 min). O interceptor Axios do mobile (`api.ts`) apenas traduzia o 401 em mensagem amigável, sem tentar renovar a sessão via `refresh-token`, ao contrário do interceptor web.
- **Correcao aplicada:**
  1. Interceptor de resposta em `mobile/src/services/api.ts` detecta 401 fora de rotas `/auth/`, chama `refreshAccessToken()` (promise compartilhada/deduplicada entre requests concorrentes) e repete a request original com o novo Bearer token.
  2. `refreshAccessToken()` chama `POST /api/auth/refresh-token` enviando cookie HttpOnly (`withCredentials: true`) + header `X-CSRF-Token` lido do `SecureStore`.
  3. `AuthController` (backend) passou a devolver `csrfToken` também no corpo de login/refresh, além do cookie — clientes nativos não leem cookies para o double-submit; o double-submit segue seguro pois o corpo cross-origin não é legível pelo browser.
  4. `csrfToken` persistido em `SecureStore` via `store/auth.ts`.
- **Arquivos alterados:** `mobile/src/services/api.ts`, `mobile/src/store/auth.ts`, `mobile/src/types/index.ts`, `backend/src/main/java/com/gestor/financeiro/controller/AuthController.java`
- **Testes/validacoes executadas:** `mvn test` -> 69/69 PASS. Validação manual: refresh-token rotaciona corretamente e o novo access token funciona na chamada seguinte.
- **Resultado:** PASS
- **Ressalvas:** Nenhuma identificada para o fluxo mobile.
- **Commit:** pendente

---

## BUG-0014 — Transações soft-deletadas continuavam somando (ativa=true ausente nas queries)

- **Problema relacionado:** PROB-0035
- **Data:** 2026-07-09
- **Area:** backend, banco
- **Sintoma:** Transações deletadas (soft-delete `ativa=false`) continuavam sendo somadas em dashboard, relatórios, insights, orçamento e apareciam em listagens paginadas e na fatura de cartão.
- **Causa raiz:** Queries de `TransacaoRepository` (SUM agregados, agrupamento por categoria, listagens, consulta de fatura) não filtravam `ativa = true`.
- **Correcao aplicada:** Adicionado `AND t.ativa = true` em `sumValorTotalByUsuarioIdAndTipoAndDataBetween`, `sumValorEfetivoByUsuarioIdAndTipoAndDataBetween`, `sumValorEfetivoAgrupadoPorCategoria`, `sumSaidasByUsuarioIdAndPeriodo`, `sumSaidasByCategoria` e `findByUsuarioIdAndDataBetweenWithCategoria`. Criadas as variantes derivadas `findByUsuarioIdAndAtivaTrue` e `findByUsuarioIdAndDataBetweenAndAtivaTrue` usadas por `TransacaoService.listarPorUsuario`/`listarPorPeriodo`, e `findByUsuarioIdAndContaIdAndDataBetweenAndAtivaTrue` usada por `FaturaService.gerarOuBuscarFatura` (substituindo `findByUsuarioIdAndContaIdAndDataBetween`).
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/repository/TransacaoRepository.java`, `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`, `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`
- **Testes/validacoes executadas:** `mvn test` -> 69/69 PASS. Fluxo E2E: delete de transação com estorno → saldo retorna corretamente (4000) e valor some das agregações.
- **Resultado:** PASS
- **Ressalvas:** Método derivado antigo `findByUsuarioIdAndDataBetween` (sem `AndAtivaTrue`) permanece no repository; confirmar que nenhum outro caller (ex: exportação CSV, insights) ainda o utiliza sem filtro de `ativa`.
- **Commit:** pendente

---

## BUG-0015 — categoria.valorGasto somava também transações de ENTRADA

- **Problema relacionado:** PROB-0036
- **Data:** 2026-07-09
- **Area:** backend
- **Sintoma:** `Categoria.valorGasto` era incrementado mesmo quando a transação era do tipo ENTRADA, inflando o indicador de orçamento/gasto por categoria.
- **Causa raiz:** `TransacaoService.criar()` e `TransacaoService.deletar()` ajustavam `categoria.valorGasto` incondicionalmente, sem checar `transacao.getTipo()`.
- **Correcao aplicada:** Ajuste de `valorGasto` restrito a `transacao.getTipo() == TipoTransacao.SAIDA` tanto na criação quanto na deleção (estorno).
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`
- **Testes/validacoes executadas:** `mvn test` -> 69/69 PASS. Fluxo E2E: orçamento jul/2026 com gasto 150/500 correto após misturar entrada e saída na mesma categoria/mês.
- **Resultado:** PASS
- **Ressalvas:** Nenhuma.
- **Commit:** pendente

---

## BUG-0016 — Vazamento de hash de senha no response de registro

- **Problema relacionado:** PROB-0037
- **Data:** 2026-07-09
- **Area:** backend, seguranca
- **Sintoma:** `POST /api/auth/register` retornava a entidade `Usuario` completa no corpo da resposta, incluindo hash bcrypt da senha, `failedAttempts` e `lockedUntil`.
- **Causa raiz:** `AuthController.register()` fazia `ResponseEntity.ok(usuarioSalvo)` com a entidade JPA diretamente.
- **Correcao aplicada:** Resposta trocada para `Map.of("id", ..., "nome", ..., "email", ...)`, sem nenhum campo sensível.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/controller/AuthController.java`
- **Testes/validacoes executadas:** `mvn test` -> 69/69 PASS. Inspeção manual do payload de resposta de `POST /api/auth/register` contra API local antes/depois da correção.
- **Resultado:** PASS
- **Ressalvas:** Não foi verificado se outros endpoints (ex: `GET /usuarios/me`) também retornam a entidade completa em vez de DTO — ver BACKLOG a ser aberto se confirmado.
- **Commit:** pendente

---

## BUG-0017 — Última parcela absorve arredondamento; limite do cartão zera corretamente

- **Problema relacionado:** PROB-0038
- **Data:** 2026-07-09
- **Area:** backend
- **Sintoma:** Compra parcelada (ex.: R$100,00 em 3x) gerava parcelas de valor arredondado (33,33 x 3 = 99,99), deixando R$0,01 residual permanente em `Conta.valorGasto` mesmo após quitar todas as faturas.
- **Causa raiz:** `valorParcela = valorTotal.divide(n, 2, HALF_UP)` aplicado identicamente em todas as N parcelas, sem reconciliar o resto da divisão inteira.
- **Correcao aplicada:** Última parcela/lançamento passa a usar `valorTotal - valorParcela*(n-1)` em vez do valor arredondado fixo. Helper `valorParcelaOuResto` criado em `TransacaoService` (usado em `criarParcelas` e `atualizarValorParcelas`); lógica equivalente aplicada inline em `FaturaService.registrarCompraCartao`.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`, `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`
- **Testes/validacoes executadas:** Novo teste `FaturaCartaoWorkflowTest.ultimaParcelaAbsorveArredondamentoELimiteZeraAposPagarTodasAsFaturas`. `./mvnw -o test` → `Tests run: 76, Failures: 0, Errors: 0` (executado nesta sessão).
- **Resultado:** PASS
- **Ressalvas:** Compras/parcelas já persistidas antes da correção (se houver em ambiente real) mantêm o resíduo antigo — sem backfill.
- **Commit:** pendente

---

## BUG-0018 — Edição de valor/data de compra no cartão ressincroniza fatura e limite

- **Problema relacionado:** PROB-0039
- **Data:** 2026-07-09
- **Area:** backend
- **Sintoma:** Editar valor ou data de uma compra já lançada no cartão não atualizava os lançamentos de fatura (`FaturaLancamento`) nem o `valorGasto` da conta/categoria — fatura e limite ficavam dessincronizados da transação real.
- **Causa raiz:** `TransacaoService.atualizar()` alterava apenas `valorTotal`/`data` da transação e o Ledger de carteira (`registrarMovimentoDiferenca`), sem tocar em fatura de cartão ou em `valorGasto`.
- **Correcao aplicada:** Para compras de cartão (`isCompraCartao`) com valor ou data alterados: cancela os lançamentos antigos (`faturaService.cancelarCompraCartao`) antes de salvar e recria (`faturaService.registrarCompraCartao`) depois; `cancelarCompraCartao` falha com `BusinessException` se alguma fatura envolvida já estiver `PAGA`. `Conta.valorGasto` e `Categoria.valorGasto` ajustados pela diferença de valor (apenas transações `SAIDA`). Parcelas legadas (`Parcela`) recalculadas via novo método `atualizarValorParcelas`.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`
- **Testes/validacoes executadas:** Novo teste `FaturaCartaoWorkflowTest.editarValorDeCompraNoCartaoRessincronizaFaturaELimite`. `./mvnw -o test` → 76/76 PASS.
- **Resultado:** PASS
- **Ressalvas:** Mensagem de erro exibida no mobile/frontend quando a edição é bloqueada por fatura já paga não foi validada nesta sessão (sem teste de UI/mensagem amigável).
- **Commit:** pendente

---

## BUG-0019 — Compra retroativa não entra mais em fatura já paga

- **Problema relacionado:** PROB-0040
- **Data:** 2026-07-09
- **Area:** backend
- **Sintoma:** Compra registrada com data retroativa cuja competência correspondia a uma fatura já `PAGA` era lançada normalmente naquela fatura, gerando inconsistência entre valor pago e valor total da fatura.
- **Causa raiz:** `registrarCompraCartao` buscava/criava a fatura pela competência calculada sem checar `fatura.getStatus()`.
- **Correcao aplicada:** Novo helper `faturaDisponivelParaLancamento(usuarioId, conta, competencia)` rola a competência mês a mês (limite de 24 iterações) até encontrar uma fatura com status diferente de `PAGA`; lança `BusinessException` se nenhuma for encontrada no período.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`
- **Testes/validacoes executadas:** Novo teste `FaturaCartaoWorkflowTest.compraRetroativaNaoEntraEmFaturaPagaVaiParaProximaAberta`. `./mvnw -o test` → 76/76 PASS.
- **Resultado:** PASS
- **Ressalvas:** Limite de 24 meses é arbitrário; risco residual aceito como extremamente improvável no fluxo real.
- **Commit:** pendente

---

## BUG-0020 — Status FECHADA da fatura agora é derivado e exibido

- **Problema relacionado:** PROB-0041
- **Data:** 2026-07-09
- **Area:** backend, frontend, mobile
- **Sintoma:** Fatura com `dataFechamento` já passada (fechada para novos lançamentos, mas ainda não vencida/paga) continuava aparecendo como "Aberta" no mobile e no frontend web.
- **Causa raiz:** Lógica de derivação de status em `FaturaService` cobria apenas `PAGA` e `VENCIDA` (via `dataVencimento`), sem checar `dataFechamento`.
- **Correcao aplicada:** Branch adicional: se `dataFechamento` já passou e a fatura não está `PAGA` nem `VENCIDA`, retorna `FaturaStatus.FECHADA`. Labels de badge adicionados: `"FECHADA"` em `mobile/app/(app)/more/faturas.tsx`, `"Fechada"` em `frontend/src/pages/Faturas.tsx`.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`, `mobile/app/(app)/more/faturas.tsx`, `frontend/src/pages/Faturas.tsx`
- **Testes/validacoes executadas:** Revisão manual de código e diff. Nenhum teste automatizado dedicado a este branch de status foi adicionado nesta sessão.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Sem cobertura de teste automatizado para a transição a `FECHADA` nem para a precedência `VENCIDA > FECHADA` quando ambas as datas já passaram.
- **Commit:** pendente

---

## BUG-0021 — Falso erro "pagamento parcial não suportado" eliminado (soma de lançamentos como fonte da verdade)

- **Problema relacionado:** PROB-0042
- **Data:** 2026-07-09
- **Area:** backend
- **Sintoma:** `pagarFatura` podia rejeitar o pagamento do valor total exibido na tela com erro "Pagamento parcial de fatura ainda não é suportado", quando `fatura.getValorTotal()` persistido divergia da soma real dos `FaturaLancamento` (causado por PROB-0038/PROB-0039 antes da correção, ou por qualquer outra dessincronia futura).
- **Causa raiz:** `pagarFatura` e `toResponse` priorizavam `fatura.getValorTotal()` persistido em vez da soma calculada dos lançamentos ao validar/exibir o valor da fatura.
- **Correcao aplicada:** `pagarFatura` e `toResponse` agora tratam a soma dos `FaturaLancamento` (`calcularTotalLancamentos`) como fonte da verdade; usam `fatura.getValorTotal()` persistido apenas como fallback quando não há lançamentos (faturas antigas pré-migration V17).
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`
- **Testes/validacoes executadas:** Coberto indiretamente pelos 3 novos testes de `FaturaCartaoWorkflowTest` (todos fazem `pagarFatura` com o valor exato da soma de lançamentos). `./mvnw -o test` → 76/76 PASS.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Sem teste dedicado ao caso específico de fatura pré-V17 (sem `FaturaLancamento`, apenas `valorTotal` persistido) exercitando o fallback.
- **Commit:** pendente

---

## BUG-0022 — ENTRADA com conta associada não incrementa mais valorGasto (limite) da conta

- **Problema relacionado:** PROB-0043
- **Data:** 2026-07-09
- **Area:** backend
- **Sintoma:** Transação do tipo `ENTRADA` vinculada a uma `Conta` incrementava `Conta.valorGasto` (limite consumido do cartão) da mesma forma que uma `SAIDA`, inflando indevidamente o limite exibido.
- **Causa raiz:** `TransacaoService.criar()`, `atualizar()` e `deletar()` chamavam `contaService.adicionarGasto`/`removerGasto` sempre que havia `conta` associada, sem checar `transacao.getTipo()`.
- **Correcao aplicada:** Guarda `transacao.getTipo() == TipoTransacao.SAIDA` adicionada antes de toda chamada a `adicionarGasto`/`removerGasto` em `criar()`, `atualizar()` e `deletar()` — mesmo padrão já aplicado a `Categoria.valorGasto` em BUG-0015.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`
- **Testes/validacoes executadas:** `./mvnw -o test` → 76/76 PASS. Nenhum teste dedicado especificamente a "ENTRADA + conta + valorGasto" foi adicionado nesta sessão.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Cobertura apenas por revisão manual do código; recomenda-se teste de unidade dedicado.
- **Commit:** pendente

---

## BUG-0023 — Edição de compra com fatura paga desbloqueada (gera lançamento AJUSTE compensatório)

- **Problema relacionado:** PROB-0044 (substitui o comportamento de bloqueio registrado em PROB-0039)
- **Data:** 2026-07-09
- **Area:** backend
- **Sintoma:** Editar valor/data de uma compra de cartão com pelo menos uma fatura envolvida já paga era bloqueado com `BusinessException`, impedindo o usuário de corrigir o valor de uma compra parcelada após a primeira fatura ser quitada.
- **Causa raiz:** `TransacaoService.atualizar()` chamava `faturaService.cancelarCompraCartao(transacao)` (versão antiga, sem `usuarioId`), que lançava `BusinessException` para qualquer lançamento em fatura `PAGA`, tratando fatura paga como imutável sem mecanismo de compensação.
- **Correcao aplicada:** Novo método `FaturaService.ressincronizarCompraCartao(transacao, usuarioId)` chamado por `TransacaoService.atualizar()` no lugar do par cancelar+recriar. Lançamentos em faturas abertas são removidos e recriados com o valor restante redistribuído pelas parcelas ainda não pagas (última parcela em aberto absorve o arredondamento). A diferença sobre a parte já paga (fatura imutável) é lançada como `TipoFaturaLancamento.AJUSTE` (podendo ser negativo) na próxima fatura em aberto — a edição nunca mais é bloqueada.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`, `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`
- **Testes/validacoes executadas:** Novo teste `FaturaCartaoWorkflowTest.editarCompraJaPagaGeraLancamentoDeAjusteNaProximaFatura` — edita compra parcialmente paga de R$100 para R$150; fatura paga permanece em 100.00 (imutável), fatura seguinte recebe lançamento `AJUSTE` de R$50.00, e `Conta.valorGasto` reflete 50.00. `cd backend && ./mvnw -o test` → `Tests run: 78, Failures: 0, Errors: 0` (executado pelo `docs-reporter` nesta sessão).
- **Resultado:** PASS
- **Ressalvas:** Redistribuição das parcelas não pagas usa "restante ÷ parcelas não pagas" (não recalcula parcela cheia) — ver BACKLOG-0055.
- **Commit:** pendente

---

## BUG-0024 — Cancelamento de compra com fatura paga desbloqueado (gera lançamento ESTORNO compensatório)

- **Problema relacionado:** PROB-0044
- **Data:** 2026-07-09
- **Area:** backend
- **Sintoma:** Cancelar/deletar uma compra de cartão parcelada com pelo menos uma fatura já paga era bloqueado com `BusinessException` ("Não é possível cancelar compra de fatura paga").
- **Causa raiz:** `FaturaService.cancelarCompraCartao(Transacao transacao)` (assinatura antiga) percorria os lançamentos da transação e lançava `BusinessException` assim que encontrava um em fatura `PAGA`, sem mecanismo de compensação.
- **Correcao aplicada:** Assinatura alterada para `cancelarCompraCartao(Transacao transacao, Long usuarioId)`. Lançamentos em faturas abertas são removidos normalmente; a soma dos lançamentos em faturas já pagas é calculada e lançada como `TipoFaturaLancamento.ESTORNO` negativo na próxima fatura em aberto (crédito de limite) — o cancelamento nunca mais é bloqueado.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`, `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java` (chamada em `deletar()` atualizada para a nova assinatura)
- **Testes/validacoes executadas:** Novo teste `FaturaCartaoWorkflowTest.cancelarCompraParceladaComFaturaPagaGeraEstornoNaProximaFatura` — compra de R$300 em 3x com uma parcela paga (R$100), cancelada; fatura seguinte recebe lançamento `ESTORNO` de -R$100.00, `Conta.valorGasto` fica em -100.00 (crédito). `./mvnw -o test` → 78/78 PASS.
- **Resultado:** PASS
- **Ressalvas:** `Conta.valorGasto` negativo é intencional (autocorrige em compras/pagamentos futuros), mas UI pode exibir de forma pouco intuitiva — ver BACKLOG-0053. Fatura contendo só estorno (total ≤ 0) não é "pagável" pelo fluxo atual — ver BACKLOG-0054.
- **Commit:** pendente

---

## BUG-0025 — Invariante de limite de cartão centralizado no FaturaService

- **Problema relacionado:** PROB-0044
- **Data:** 2026-07-09
- **Area:** backend
- **Sintoma:** Antes desta correção, `Conta.valorGasto` era ajustado em dois lugares diferentes para compras de cartão: em `TransacaoService` (via `contaService.adicionarGasto`/`removerGasto`) e potencialmente de forma inconsistente com os lançamentos de fatura, aumentando o risco de dessincronia entre o limite exibido e a soma real de lançamentos em faturas não pagas.
- **Causa raiz:** Ausência de um ponto único de verdade para o ajuste de `valorGasto` relacionado a compras de cartão.
- **Correcao aplicada:** Estabelecido o invariante `Conta.valorGasto == soma dos lançamentos em faturas não pagas`. Novos helpers privados em `FaturaService`: `criarLancamento(...)` e `removerLancamentoDeFaturaAberta(...)` chamam `ajustarLimiteUtilizado(conta, delta)` a cada mutação de lançamento (criação, remoção, ajuste, estorno). `TransacaoService` deixou de chamar `contaService.adicionarGasto`/`removerGasto` para transações que são compra de cartão (`isCompraCartao`) — mantido apenas para contas que não são cartão de crédito. `pagarFatura` continua liberando limite pelo total da fatura (sem alteração de contrato).
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`, `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`
- **Testes/validacoes executadas:** Coberto indiretamente por todos os testes de `FaturaCartaoWorkflowTest` (7 testes na classe), que verificam `Conta.valorGasto` após criar, editar, pagar e cancelar compras de cartão. `./mvnw -o test` → 78/78 PASS.
- **Resultado:** PASS
- **Ressalvas:** Nenhum teste isola exclusivamente o helper `ajustarLimiteUtilizado` fora do fluxo de compra/edição/cancelamento — cobertura é indireta via testes de fluxo completo.
- **Commit:** pendente

---

## BUG-0026 — UI exibe lançamentos de crédito (ajuste/estorno) em verde com prefixo descritivo

- **Problema relacionado:** PROB-0044
- **Data:** 2026-07-09
- **Area:** frontend, mobile
- **Sintoma:** Antes desta correção, todo lançamento de fatura era exibido em vermelho (cor de débito), inclusive lançamentos de crédito (valor negativo) recém-introduzidos por `AJUSTE`/`ESTORNO` — visualmente indistinguível de uma compra normal.
- **Causa raiz:** Renderização de `l.valor` em `mobile/app/(app)/more/faturas.tsx` e `frontend/src/pages/Faturas.tsx` usava cor fixa (`colors.danger`/`text-red-400`) sem checar o sinal do valor.
- **Correcao aplicada:** Cor do valor do lançamento passa a depender do sinal: `l.valor < 0` renderiza em verde (`colors.success`/`text-green-400`), mantendo vermelho para valores positivos. Descrições de lançamentos de ajuste/estorno vêm prefixadas com `"Ajuste: "`/`"Estorno: "` desde o backend (`FaturaService.ressincronizarCompraCartao`/`cancelarCompraCartao`). Campo `tipo: 'COMPRA' | 'AJUSTE' | 'ESTORNO'` adicionado a `FaturaLancamentoDto` (backend), `mobile/src/types/index.ts` e `frontend/src/services/faturaService.ts`.
- **Arquivos alterados:** `mobile/app/(app)/more/faturas.tsx`, `frontend/src/pages/Faturas.tsx`, `mobile/src/types/index.ts`, `frontend/src/services/faturaService.ts`, `backend/src/main/java/com/gestor/financeiro/dto/FaturaLancamentoDto.java`
- **Testes/validacoes executadas:** Nenhum teste automatizado de UI (mobile e frontend não têm suíte e2e/component configurada para esta tela). Typecheck mobile limpo (relatado pelo agente de implementação). Erros de TypeScript pré-existentes no frontend, fora dos arquivos de fatura, não foram investigados por estarem fora do escopo desta correção.
- **Resultado:** NAO_EXECUTADO (sem teste automatizado; validação visual não confirmada por este agente)
- **Ressalvas:** Verificação de que o typecheck do frontend realmente não introduziu novos erros nos arquivos de fatura não foi reexecutada pelo `docs-reporter` (relato do agente de implementação, não verificado independentemente nesta sessão).
- **Commit:** pendente

---

## BUG-0027 — Mobile ganhou edição/exclusão de transação (EditarTransacaoModal)

- **Problema relacionado:** PROB-0045
- **Data:** 2026-07-09
- **Area:** mobile
- **Sintoma:** Não havia forma de editar ou excluir uma transação a partir do app mobile — a lista de transações não respondia a toque e não existia modal de edição.
- **Causa raiz:** Funcionalidade nunca implementada no mobile; apenas criação (`NovaTransacaoModal`) existia.
- **Correcao aplicada:** Novo componente `mobile/src/components/EditarTransacaoModal.tsx`, aberto ao tocar em uma linha de `mobile/app/(app)/transacoes.tsx`. Edita apenas `valor`, `descricao`, `data`, `observacoes` (únicos campos aplicados pelo backend em `TransacaoService.atualizar`); tipo/categoria/forma de pagamento exibidos como bloco fixo não editável. Para compra de cartão, exibe aviso de que a edição ressincroniza faturas (parte já paga vira ajuste na próxima fatura aberta, conforme `FaturaService.ressincronizarCompraCartao`). Exclusão via `Alert.alert` de confirmação, com texto específico avisando sobre estorno quando é compra de cartão. Após salvar/excluir, invalida as query keys `transacoes`, `transacoes-recentes`, `dashboard-resumo`, `dashboard-projecao`, `carteiras`, `contas`, `contas-fatura`, `fatura`, `categorias`. Subtítulo da lista passa a mostrar `· Nx` quando a transação é parcelada.
- **Arquivos alterados:** `mobile/src/components/EditarTransacaoModal.tsx` (novo), `mobile/app/(app)/transacoes.tsx`
- **Testes/validacoes executadas:** `tsc --noEmit` limpo no mobile (relatado pelo agente de implementação, não reexecutado de forma independente pelo `docs-reporter`). Validação manual de contrato contra backend local (porta 8081) com payloads exatos do app: `POST` compra 3x → `201`; `PUT` com corpo exato do modal → `200`; `DELETE` → `204`. Usuário de teste descartável usado e dados de transação removidos após o teste (restou apenas o usuário `teste-fatura-ui@teste.com` no banco local). `FaturaCartaoWorkflowTest`: 7/7 PASS (suite backend não alterada por este item).
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Sem teste automatizado mobile (projeto não tem suíte configurada — ver limitações conhecidas em `SYSTEM_OVERVIEW.md`). Validação de contrato foi manual e única, contra ambiente local, com um único usuário de teste — não cobre concorrência. Ver PROB-0048: a validação manual de contrato rodou contra um processo backend com build defasado (o processo não foi reiniciado na sessão — reinício ficou a cargo do usuário); ela comprova apenas o contrato HTTP (status codes/shape do payload). A validação do comportamento do código atual veio de `FaturaCartaoWorkflowTest` (7/7).
- **Commit:** pendente

---

## BUG-0028 — Badge de status da fatura no mobile passa a diferenciar ABERTA/FECHADA/VENCIDA/PAGA por cor

- **Problema relacionado:** PROB-0046
- **Data:** 2026-07-09
- **Area:** mobile
- **Sintoma:** Badge de status da fatura em `mobile/app/(app)/more/faturas.tsx` era binário: verde só para `PAGA`, vermelho para qualquer outro status — inclusive `ABERTA`, que é o estado normal de uma fatura dentro do período de compras.
- **Causa raiz:** Lógica de cor (`fatura.status === 'PAGA' ? verde : vermelho`) nunca foi atualizada quando os status `FECHADA`/`VENCIDA` passaram a ser exibidos como valores distintos (PROB-0041).
- **Correcao aplicada:** Nova constante `statusBadge` mapeando cada status a uma cor semântica: `PAGA` → `colors.success`; `VENCIDA` → `colors.danger`; `FECHADA` → `colors.warning`; `ABERTA` (padrão) → `colors.brandFg`/`colors.brandBg`, tratando o estado aberto como normal, não como alerta.
- **Arquivos alterados:** `mobile/app/(app)/more/faturas.tsx`
- **Testes/validacoes executadas:** Nenhum teste automatizado (mobile sem suíte de UI configurada). Verificação por leitura do diff.
- **Resultado:** NAO_EXECUTADO (sem teste automatizado; validação visual não confirmada por este agente)
- **Ressalvas:** Correção de baixo risco (mudança puramente visual), mas sem cobertura de teste dedicada.
- **Commit:** pendente

---

## BUG-0029 — Lançamentos de ajuste/estorno na fatura (mobile) ganham badge de tipo e descrição sem prefixo redundante

- **Problema relacionado:** PROB-0047
- **Data:** 2026-07-09
- **Area:** mobile
- **Sintoma:** Lançamentos `AJUSTE`/`ESTORNO` na tela de fatura mobile só eram distinguíveis de uma compra normal pelo prefixo textual `"Ajuste: "`/`"Estorno: "` na descrição e pela cor do valor (BUG-0026) — sem nenhum indicador visual dedicado de tipo.
- **Causa raiz:** BUG-0026 tratou apenas a cor condicional do valor; o campo `tipo` (já disponível no DTO/tipos desde BUG-0026) não tinha uso visual além disso.
- **Correcao aplicada:** Cada lançamento agora calcula um `tipoBadge`: `ESTORNO` → chip verde (`colors.success`); `AJUSTE` → chip âmbar (`colors.warning`); `COMPRA` → sem badge. O prefixo `"Estorno: "`/`"Ajuste: "` é removido da descrição exibida via regex (`/^(Estorno|Ajuste):\s*/`) já que o badge assume esse papel — a descrição original retornada pela API permanece intacta, apenas a exibição é ajustada.
- **Arquivos alterados:** `mobile/app/(app)/more/faturas.tsx`
- **Testes/validacoes executadas:** Nenhum teste automatizado (mobile sem suíte de UI configurada). Verificação por leitura do diff.
- **Resultado:** NAO_EXECUTADO (sem teste automatizado; validação visual não confirmada por este agente)
- **Ressalvas:** Regex de remoção do prefixo depende do texto exato gerado pelo backend (`FaturaService.ressincronizarCompraCartao`/`cancelarCompraCartao`); mudança futura nesse texto sem atualizar a regex causaria prefixo duplicado. Não foi verificado nesta rodada se `frontend/src/pages/Faturas.tsx` (web) recebeu o mesmo tratamento (a leitura do diff atual do web mostra apenas a cor condicional de BUG-0026, sem badge/prefixo removido) — ver PROB-0047, próximo passo, e BACKLOG a criar se aplicável.
- **Commit:** pendente

---

## BUG-0030 — Login não retornava onboardingCompleto e mandava todo usuário pro onboarding

- **Problema relacionado:** BUG-M01 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** backend (auth), mobile
- **Sintoma:** todo login no mobile redirecionava para `/onboarding`, mesmo usuário com onboarding completo.
- **Causa raiz:** map `usuario` da resposta de `POST /api/auth/login` só tinha `id/nome/email`; `login.tsx:23` checava `user.onboardingCompleto` → `undefined` → falsy.
- **Correcao aplicada:** `AuthController` inclui `onboardingCompleto` (via `usuario.isOnboardingCompleto()`) no map `usuario` do login — mesma projeção do `UsuarioResponseDto`.
- **Arquivos alterados:** `AuthController.java`
- **Testes/validacoes executadas:** `mvn test -Dtest=AuthControllerTest` — 17/17 PASS.
- **Resultado:** PASS

---

## BUG-0031 — "Exportar Dados" do mobile sempre dava 401 (URL sem autenticação)

- **Problema relacionado:** BUG-M02 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** mobile
- **Sintoma:** tile "Exportar Dados" abria/compartilhava a URL crua de `/v1/exportar/completo`; endpoint exige Bearer → 401 sempre; URL da API vazava pelo Share.
- **Causa raiz:** download via browser/`Share.share` sem header `Authorization`.
- **Correcao aplicada:** CSV baixado pelo axios autenticado (`responseType: 'text'`); nativo grava com `expo-file-system` (`File`/`Paths.cache`) e compartilha o arquivo com `expo-sharing`; web baixa via Blob + anchor. Deps novas: `expo-file-system ~19.0.23`, `expo-sharing ~14.0.8` (instaladas com `--legacy-peer-deps` por conflito pré-existente react 19.1.0 × react-dom 19.2.7).
- **Arquivos alterados:** `mobile/app/(app)/more/index.tsx`, `mobile/package.json`, `mobile/package-lock.json`
- **Testes/validacoes executadas:** `npx tsc --noEmit` — limpo.
- **Resultado:** PASS
- **Ressalvas:** validação em device real pendente (fluxo Share nativo).

---

## BUG-0032 — Logout não revogava refresh token no servidor

- **Problema relacionado:** BUG-M03 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** mobile (segurança)
- **Sintoma:** logout só limpava storage local; refresh token do cookie HttpOnly continuava válido no servidor. Em `perfil.tsx` a chamada nem tinha `await` e o logout era disparado em duplicidade (service + contexto).
- **Causa raiz:** `authService.logout()` nunca chamava `POST /api/auth/logout`.
- **Correcao aplicada:** `authService.logout()` chama `POST /auth/logout` com header `X-CSRF-Token` (best-effort: storage local sempre limpo mesmo se a rede falhar); `perfil.tsx` usa apenas o `logout()` do contexto com `await`; tipo do contexto ajustado para `() => Promise<void>`.
- **Arquivos alterados:** `mobile/src/services/authService.ts`, `mobile/app/(app)/perfil.tsx`, `mobile/src/context/AuthContext.tsx`
- **Testes/validacoes executadas:** `npx tsc --noEmit` — limpo. Backend já tinha teste de logout+CSRF em `AuthControllerTest` (17/17 PASS).
- **Resultado:** PASS

---

## BUG-0033 — carteiraService usava endpoints deprecated de adicionar/remover dinheiro

- **Problema relacionado:** BUG-M04 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** mobile
- **Sintoma:** `carteiraService` apontava para `POST /{id}/adicionar` e `/{id}/remover`, marcados `@Deprecated(since = "PR-LEDGER-06")` — movimentos fora do ledger.
- **Causa raiz:** service não migrou quando o backend ganhou `POST /{id}/ajustes`.
- **Correcao aplicada:** `adicionarValor`/`removerValor` substituídos por `ajustarSaldo(id, tipo ENTRADA|SAIDA, valor, descricao?)` chamando `/v1/carteiras/{id}/ajustes`. Nenhuma tela usava os métodos antigos (código morto) — sem mudança de UI.
- **Arquivos alterados:** `mobile/src/services/carteiraService.ts`
- **Testes/validacoes executadas:** `npx tsc --noEmit` — limpo; grep confirmou zero chamadores dos métodos removidos.
- **Resultado:** PASS
- **Ressalvas:** endpoints deprecated ainda existem no backend; remover quando o frontend web também migrar.

---

## BUG-0034 — Pagar conta fixa não debitava nenhuma carteira (dinheiro sumia)

- **Problema relacionado:** PROD-M05 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** backend + mobile
- **Sintoma:** `PUT /v1/contas-fixas/{id}/pagar` criava transação SAIDA sem carteira; `saldoCarteiras` não caía ao pagar aluguel/energia.
- **Causa raiz:** `ContaFixaController` ignorava `ValorRequest.carteiraId`; `ContaFixaService.marcarComoPaga` nunca vinculava carteira à transação.
- **Correcao aplicada:** controller repassa `carteiraId`; service exige carteira (`BusinessException` 422 se ausente) e seta na transação — `TransacaoService.criar` valida ownership e registra o débito no ledger (mesma mecânica do pagamento de fatura). Mobile: modal Pagar em `more/contas-fixas.tsx` ganhou seletor de carteira (chips, padrão de `more/faturas.tsx`), pré-seleciona quando há uma só, invalida `carteiras` e `transacoes-recentes`.
- **Arquivos alterados:** `ContaFixaController.java`, `ContaFixaService.java`, `mobile/src/services/contaFixaService.ts`, `mobile/app/(app)/more/contas-fixas.tsx`
- **Testes/validacoes executadas:** `FinancialIntegrityTest`, `LedgerServiceTest`, `TransacaoServiceLedgerTest` 16/16 PASS; E2E na stack local (payloads do mobile): pagar sem carteira → 422 "Informe a carteira de pagamento"; com carteira → saldo 4000→3850 e movimento SAIDA no extrato.
- **Resultado:** PASS
- **Ressalvas:** frontend web (`ContasFixas.tsx`) ainda chama sem `carteiraId` e agora recebe 422 — alinhar quando o web for retomado (antes vazava dinheiro em silêncio; erro explícito é o comportamento correto até lá).

---

## BUG-0035 — Reservar valor em meta não saía de carteira nenhuma (dupla contagem)

- **Problema relacionado:** PROD-M06 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** backend + mobile
- **Sintoma:** `PUT /v1/metas/{id}/adicionar` só incrementava `valorReservado`; dinheiro "guardado" seguia disponível na carteira.
- **Causa raiz:** `MetaService.adicionarValor`/`removerValor` não tocavam o ledger de carteiras.
- **Correcao aplicada:** ambos exigem `carteiraId` (422 se ausente). Reserva debita a carteira (`RESERVA_META`, origem `META`, saldo insuficiente → 422); resgate credita de volta (`RESGATE_META`) e é limitado ao reservado ("Valor maior que o reservado na meta"). Enums novos em `TipoMovimentoCarteira` (coluna é VARCHAR, sem migration). Mobile: modal Adicionar em `metas.tsx` ganhou seletor "Sai de" com chips de carteira.
- **Arquivos alterados:** `TipoMovimentoCarteira.java`, `MetaService.java`, `MetaController.java`, `mobile/src/services/metaService.ts`, `mobile/app/(app)/metas.tsx`
- **Testes/validacoes executadas:** mesmos testes de ledger 16/16 PASS; E2E: reservar 200 → saldo 3850→3650 com movimento `RESERVA_META`; resgate acima do reservado → 422; resgatar 80 → saldo 3730 com `RESGATE_META`.
- **Resultado:** PASS
- **Ressalvas:** (1) web `Metas.tsx` chama sem `carteiraId` → 422 até alinhar; (2) metas antigas com `valorReservado` acumulado antes da correção nunca debitaram carteira — resgatá-las agora credita dinheiro que não saiu; avaliar backfill/zerar em dados reais; (3) `removerValor` sem UI no mobile (UX-M11) — assinatura do service já aceita `carteiraId`.

---

## BUG-0036 — Home: rótulos de saldo enganavam (patrimônio rotulado como saldo do mês)

- **Problema relacionado:** PROD-M07 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** mobile
- **Sintoma:** hero mostrava `saldoCarteiras` (patrimônio) com label "Saldo total · {mês}"; KPI "Disponível" repetia o mesmo `saldoCarteiras`; `resumo.saldo` (saldo do mês) não era usado.
- **Causa raiz:** rótulos herdados do protótipo sem distinguir patrimônio × movimento do mês.
- **Correcao aplicada:** hero = "Saldo total" (sem mês); chips ↑↓ ganharam sufixo "em {mês}"; KPI "Disponível" virou "Saldo do mês" usando `resumo.saldo`; glifos de Receitas/Despesas alinhados à semântica do hero (↑ entrada, ↓ saída).
- **Arquivos alterados:** `mobile/app/(app)/index.tsx`
- **Testes/validacoes executadas:** `npx tsc --noEmit` limpo; E2E confirmou contrato: `resumo.saldo` (2700) ≠ `saldoCarteiras` (3730) no dashboard.
- **Resultado:** PASS
- **Ressalvas:** após PROD-M06 em produção, "Disponível" pode voltar como patrimônio − reservas ativas (hoje o débito da reserva já remove da carteira, então `saldoCarteiras` já reflete o disponível real).

---

## BUG-0037 — Transações: paginação falsa e somatório mentiroso

- **Problema relacionado:** UX-M08 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** mobile + backend
- **Sintoma:** tela carregava só `page=0&size=20` sem infinite scroll; cards Entradas/Saídas somavam apenas os 20 itens carregados; filtro ENTRADA/SAIDA era client-side sobre a página; sem período nem busca.
- **Causa raiz:** listagem usava `useQuery` fixo em `/minhas` página 0; somatório calculado no client sobre a página; backend não expunha filtro de tipo/busca em `/periodo`.
- **Correcao aplicada:** backend: `/v1/transacoes/periodo` ganhou parâmetros opcionais `tipo` e `q` (busca case-insensitive por descrição; queries dedicadas no repositório, sempre `ativa = true`). Mobile: `useInfiniteQuery` com paginação real, seletor de mês (‹ mês ›, default atual, avanço bloqueado além do mês corrente), somatório do cabeçalho vindo de `/v1/relatorios` (totais do período), campo de busca com debounce 350ms e chips de tipo virando parâmetros de query. Modais de transação passaram a invalidar `['relatorio']` e `['dashboard-evolucao']`.
- **Arquivos alterados:** `backend/.../TransacaoController.java`, `TransacaoService.java`, `TransacaoRepository.java`, `mobile/app/(app)/transacoes.tsx`, `mobile/src/services/transacaoService.ts`, `mobile/src/components/NovaTransacaoModal.tsx`, `mobile/src/components/EditarTransacaoModal.tsx`
- **Testes/validacoes executadas:** `TransacaoControllerTest` com novo teste `listarPorPeriodo_deveFiltrarPorTipoEBusca` (tipo, q, combinado, ENTRADA) PASS; `npx tsc --noEmit` limpo.
- **Resultado:** PASS
- **Ressalvas:** backend local (porta 8081) precisa ser reiniciado para expor os novos parâmetros.

---

## BUG-0038 — Sem tela de cadastro no app

- **Problema relacionado:** UX-M09 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** mobile
- **Sintoma:** backend tinha `POST /auth/register`, mas usuário novo não conseguia criar conta pelo app (só login + forgot-password).
- **Causa raiz:** tela nunca foi construída.
- **Correcao aplicada:** `mobile/app/(auth)/register.tsx` consumindo `/auth/register` (`RegisterRequest`: nome, email, password, confirmPassword), validação client espelhando o backend (`@ValidPassword`: mínimo 8, 1 letra, 1 número; nome ≥2; e-mail; confirmação), login automático após sucesso com redirect para onboarding. Link "Criar conta" no login.
- **Arquivos alterados:** `mobile/app/(auth)/register.tsx` (novo), `mobile/app/(auth)/login.tsx`
- **Testes/validacoes executadas:** `npx tsc --noEmit` limpo; `AuthControllerTest` PASS (contrato de register inalterado).
- **Resultado:** PASS
- **Ressalvas:** validar fluxo completo em device (teclado/scroll) na próxima sessão de testes manuais.

---

## BUG-0039 — Reset de senha terminava no vácuo

- **Problema relacionado:** UX-M10 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** mobile + backend
- **Sintoma:** forgot-password enviava o e-mail, mas não existia tela para `POST /auth/reset-password` nem deep link.
- **Causa raiz:** segunda metade do fluxo nunca foi construída.
- **Correcao aplicada:** `mobile/app/(auth)/reset-password.tsx` (token + nova senha + confirmação, mesma regra `@ValidPassword`); aceita deep link `gestorfinanceiro://reset-password?token=...` via `useLocalSearchParams` (scheme já existia no `app.json`) e colagem manual do token; entrada pela tela de sucesso do forgot-password ("Já recebi o código"). `EmailService` monta o link com a property `app.reset-password-link-base` (default: scheme do app).
- **Arquivos alterados:** `mobile/app/(auth)/reset-password.tsx` (novo), `mobile/app/(auth)/forgot-password.tsx`, `backend/.../EmailService.java`
- **Testes/validacoes executadas:** `npx tsc --noEmit` limpo; backend compila; contrato `/auth/reset-password` e `ResetPasswordRequest` inalterados.
- **Resultado:** PASS
- **Ressalvas:** envio real de e-mail continua TODO (stub loga sem token); testar deep link em device real.

---

## BUG-0040 — Backend rico, mobile cego: extrato de carteira e evolução mensal (parcial da seção 4)

- **Problema relacionado:** Seção 4 (docs/AUDITORIA_MOBILE_2026-07-10.md) — prioridades "extrato de carteira e gráficos primeiro"
- **Data:** 2026-07-10
- **Area:** mobile
- **Sintoma:** `/v1/carteiras/{id}/movimentos` e `/v1/dashboard/evolucao-mensal` prontos no backend, sem UI.
- **Causa raiz:** backlog de evolução.
- **Correcao aplicada:** Carteiras: tocar no card abre extrato do ledger (paginação infinita, valor assinado verde/vermelho, saldo resultante por movimento, labels pt-BR por tipo em `TIPO_MOVIMENTO_LABEL`). Relatórios: card "Evolução mensal" com barras entradas × saídas dos últimos 6 meses (Views puras, sem lib de gráfico; legenda; acessibilidade por mês).
- **Arquivos alterados:** `mobile/app/(app)/more/carteiras.tsx`, `mobile/app/(app)/more/relatorios.tsx`, `mobile/src/services/carteiraService.ts`, `mobile/src/services/relatorioService.ts`, `mobile/src/types/index.ts`, `mobile/src/utils/format.ts`
- **Testes/validacoes executadas:** `npx tsc --noEmit` limpo.
- **Resultado:** PASS
- **Ressalvas:** restante da seção 4 segue pendente (comparação mensal, insights, parcelas, anexos, importação CSV, investimentos, reconciliação com UI).

---

## BUG-0041 — UX-M11 a M14: metas, categoria, nomenclatura e perfil

- **Problema relacionado:** UX-M11 a UX-M14 (docs/AUDITORIA_MOBILE_2026-07-10.md)
- **Data:** 2026-07-10
- **Area:** mobile + backend
- **Sintoma:** metas sem editar/excluir/retirar valor; edição de transação sem troca de categoria; "Contas" e "Carteiras" confundiam; perfil escondido e sem ações.
- **Causa raiz:** UI mobile incompleta para contratos já existentes e falta de endpoints de perfil.
- **Correcao aplicada:** Metas: sheet de detalhe com editar, excluir, adicionar e retirar valor; form expõe `valorMensal`. Transações: edição permite trocar categoria; backend valida ownership e ajusta gasto por categoria. Nomenclatura: UI usa "Contas" para saldos/dinheiro (`carteiras`) e "Cartões" para `contas`. Perfil: entrada no hub "Mais"; formulário de nome e senha; backend `PUT /v1/usuarios/me` e `PUT /v1/usuarios/me/senha`.
- **Arquivos alterados:** `mobile/app/(app)/metas.tsx`, `mobile/src/components/EditarTransacaoModal.tsx`, `backend/.../TransacaoService.java`, `backend/.../UsuarioController.java`, `mobile/app/(app)/perfil.tsx`, `mobile/app/(app)/more/index.tsx`, telas de contas/cartões/onboarding.
- **Testes/validacoes executadas:** `npm --prefix mobile run lint -- --pretty false` PASS; `backend/./mvnw -q -Dtest=TransacaoServiceLedgerTest test` PASS; `backend/./mvnw -q -Dtest=TransacaoControllerTest test` PASS com permissão elevada por Mockito/ByteBuddy.
- **Resultado:** PASS
- **Ressalvas:** nomes internos, rotas e serviços permanecem `carteiras`/`contas` para evitar migração desnecessária.

## BUG-0042 — Seção 4: comparação mensal no mobile

- **Problema relacionado:** Seção 4 (docs/AUDITORIA_MOBILE_2026-07-10.md) — endpoint `/v1/dashboard/comparacao-mensal` pronto no backend, sem UI.
- **Data:** 2026-07-10
- **Area:** mobile
- **Sintoma:** Relatórios mostrava evolução de 6 meses, mas não respondia rapidamente se o mês atual melhorou ou piorou contra o mês anterior.
- **Causa raiz:** backlog de evolução; service mobile ainda não consumia o endpoint de comparação mensal.
- **Correcao aplicada:** `relatorioService.comparacaoMensal()` consome `/v1/dashboard/comparacao-mensal`; Relatórios ganhou card "Comparação mensal" com saldo, entradas, saídas e variação absoluta/percentual entre mês atual e anterior; criação/edição de transações invalidam o novo cache.
- **Arquivos alterados:** `mobile/app/(app)/more/relatorios.tsx`, `mobile/src/services/relatorioService.ts`, `mobile/src/types/index.ts`, `mobile/src/components/NovaTransacaoModal.tsx`, `mobile/src/components/EditarTransacaoModal.tsx`
- **Testes/validacoes executadas:** `npm --prefix mobile run lint -- --pretty false` PASS; detector Impeccable local retornou `[]`.
- **Resultado:** PASS
- **Ressalvas:** demais itens da seção 4 seguem pendentes (insights, parcelas, anexos, importação CSV, investimentos, reconciliação com UI).

---

## BUG-0043 — Seção 4: reconciliação, insights e parcelas no mobile

- **Problema relacionado:** Seção 4 (docs/AUDITORIA_MOBILE_2026-07-10.md) — endpoints de reconciliação, insights e parcelas prontos no backend, sem UI mobile.
- **Data:** 2026-07-10
- **Area:** mobile
- **Sintoma:** Usuário não via conferência ledger × saldo da conta, não recebia alertas/recomendações na home e não conseguia pagar/despagar parcelas individualmente pelo app.
- **Causa raiz:** backlog de evolução mobile sobre contratos já existentes.
- **Correcao aplicada:** Carteiras: extrato mostra status de reconciliação, saldos comparados e diferença. Home: novo card de insights consome `/v1/insights`, exibe resumo, gasto do mês, média, alertas de categoria e recomendação principal. Transações: edição de transação parcelada lista parcelas via `/v1/parcelas/transacao/{id}` e permite pagar/despagar com invalidação de dashboards, relatórios, faturas e parcelas.
- **Arquivos alterados:** `mobile/app/(app)/index.tsx`, `mobile/app/(app)/more/carteiras.tsx`, `mobile/src/components/EditarTransacaoModal.tsx`, `mobile/src/services/carteiraService.ts`, `mobile/src/services/insightsService.ts`, `mobile/src/services/parcelaService.ts`, `mobile/src/types/index.ts`, `docs/AUDITORIA_MOBILE_2026-07-10.md`.
- **Testes/validacoes executadas:** `npm --prefix mobile run lint -- --pretty false` PASS.
- **Resultado:** PASS
- **Ressalvas:** anexos, importação CSV e investimentos foram fechados depois em BUG-0044.

---

## BUG-0044 — Seção 4: anexos, importação CSV e investimentos no mobile

- **Problema relacionado:** Seção 4 (docs/AUDITORIA_MOBILE_2026-07-10.md) — últimos endpoints prontos no backend sem UI mobile.
- **Data:** 2026-07-10
- **Area:** mobile
- **Sintoma:** Usuário não conseguia anexar comprovantes por transação, importar extrato CSV pelo app nem gerenciar investimentos.
- **Causa raiz:** mobile ainda não tinha fluxo de seleção/câmera/arquivo nem tela dedicada de investimentos.
- **Correcao aplicada:** Instalação das dependências Expo compatíveis `expo-document-picker` e `expo-image-picker`. Edição de transação ganhou seção "Comprovantes" com upload por câmera/arquivo, listagem e exclusão via `/v1/anexos`. Hub "Mais" ganhou importação CSV autenticada via `/v1/importar/csv`. Novo módulo `Investimentos` consome `/v1/investimentos`, permitindo listar, criar, editar, excluir ativos e registrar/listar movimentações.
- **Arquivos alterados:** `mobile/package.json`, `mobile/package-lock.json`, `mobile/app/(app)/more/index.tsx`, `mobile/app/(app)/more/investimentos.tsx`, `mobile/src/components/EditarTransacaoModal.tsx`, `mobile/src/services/anexoService.ts`, `mobile/src/services/importService.ts`, `mobile/src/services/investimentoService.ts`, `mobile/src/types/index.ts`, `docs/AUDITORIA_MOBILE_2026-07-10.md`.
- **Testes/validacoes executadas:** `npm --prefix mobile run lint -- --pretty false` PASS.
- **Resultado:** PASS
- **Ressalvas:** validar câmera, seletor de documentos e upload multipart em device real/simulador com API rodando.

---

## BUG-0045 — /v1/insights retornava 500 com dados reais (visto no simulador iOS)

- **Problema relacionado:** Seção 4 (docs/AUDITORIA_MOBILE_2026-07-10.md) — home logava `[API Error] {"status": 500, "url": "/v1/insights"}` em loop.
- **Data:** 2026-07-10
- **Area:** backend
- **Sintoma:** `GET /v1/insights` 500 para qualquer usuário com gasto no mês anterior; usuário sem histórico recebia 200.
- **Causa raiz:** (1) `InsightsService.gerarAlertasCategoria` lia `row[1]` (nome da categoria, String) como `BigDecimal` — `sumSaidasByCategoria` retorna `[id, nome, soma]` → `ClassCastException`. (2) Variação mensal dividia por `gastoMesAtual` — denominador errado (correto: média) e `ArithmeticException` com mês atual zerado.
- **Correcao aplicada:** `mapAnterior` passa a usar `row[2]` (com conversores defensivos `asLong`/`asBigDecimal`); variação divide por `gastoMedioMensal`.
- **Arquivos alterados:** `backend/.../InsightsService.java`, `backend/src/test/java/com/gestor/financeiro/InsightsServiceTest.java` (novo)
- **Testes/validacoes executadas:** `InsightsServiceTest` PASS (regressão dos dois cenários); E2E no backend local: usuário com gasto de R$600 no mês anterior e R$900 no atual → 200 com variação 350% (sobre média de 3 meses) e alerta de categoria +50%.
- **Resultado:** PASS
- **Ressalvas:** dependências `expo-image-picker`/`expo-document-picker` estavam em versões do SDK 57 com Expo 54 (`createPermissionHook is not a function` no simulador) — realinhadas para 17.0.10/14.0.8 via `npm install --legacy-peer-deps` (conflito de peer `react-dom@19.2.7` × `react@19.1.0` pré-existente na árvore). Recarregar o app no simulador para pegar os módulos corretos.

---

## BUG-0046 — Web: pagar conta fixa e reservar meta sem carteiraId (422)

- **Problema relacionado:** Ressalva da seção 6, item 2 (docs/AUDITORIA_MOBILE_2026-07-10.md) — após PROD-M05/M06 o backend passou a exigir `carteiraId`, mas o frontend web continuava enviando só `{ valor }`.
- **Data:** 2026-07-10
- **Area:** frontend (web)
- **Sintoma:** No web, "Marcar como Paga" (Contas Fixas) e "Adicionar Dinheiro" (Metas) retornavam 422 sempre.
- **Causa raiz:** `contaFixaService.marcarComoPaga` e `metaService.adicionarValor`/`removerValor` não enviavam `carteiraId`, agora obrigatório no contrato.
- **Correcao aplicada:** services passam a enviar `carteiraId` no body; `ContasFixas.tsx` ganhou select "Pagar com qual conta?" no form inline de pagamento e `Metas.tsx` ganhou select "Sai de qual conta?" no form de adicionar valor (ambos listam carteiras com saldo via `/carteiras/minhas`, validação obrigatória, toast com mensagem do backend em erro).
- **Arquivos alterados:** `frontend/src/services/contaFixaService.ts`, `frontend/src/services/metaService.ts`, `frontend/src/pages/ContasFixas.tsx`, `frontend/src/pages/Metas.tsx`.
- **Testes/validacoes executadas:** `npx tsc --noEmit` PASS no frontend.
- **Resultado:** PASS
- **Ressalvas:** `metaService.removerValor` foi atualizado no contrato, mas a UI web ainda não expõe "retirar valor" (paridade com mobile fica para depois). Validar fluxo E2E no browser com API rodando.

---

## BUG-0047 — Auditoria segurança/LGPD: itens #2, #4, #4b, #6, #8, #10, #11 implementados

- **Problema relacionado:** docs/REVIEW_REPORTS/2026-07-10_full-system_security-lgpd-audit.md
- **Data:** 2026-07-10
- **Area:** backend
- **Sintoma/risco por item:**
  - **#2** Host/usuário do banco de produção commitados como default em `application-prod.properties`.
  - **#8** `DB_PASSWORD:1234` e `jwt.secret` com default fraco no `application.properties` base.
  - **#6** Upload de anexo sem validação de tipo (stored XSS via HTML com MIME arbitrário; filename do cliente no path).
  - **#4** Refresh token em texto puro no banco (vazamento do DB = roubo de sessão).
  - **#4b** Token de reset de senha idem (achado colateral do #4).
  - **#10** LGPD art. 18: sem endpoint de eliminação; exportação incompleta (faltavam cadastro, carteiras, metas, contas fixas).
  - **#11** LGPD: cadastro sem registro de consentimento.
- **Correcao aplicada:**
  - **#2/#8** Removidos todos os defaults sensíveis; env obrigatório (falha no boot se ausente). Perfil dev mantém defaults locais próprios.
  - **#6** `AnexoService`: whitelist pdf/jpg/jpeg/png/webp + verificação de magic bytes; nome em disco = `UUID.ext`; MIME canônico da whitelist (nunca o do cliente); download com `contentTypeSeguro()` (neutraliza MIME legado) e `Content-Disposition` via builder (sem injeção de header).
  - **#4/#4b** `TokenHasher` novo (`security/`): valor cru de 256 bits entregue uma única vez; banco guarda só SHA-256 hex. Aplicado a refresh token e reset de senha. Sem DDL; tokens antigos deixam de validar (re-login único).
  - **#10** `DELETE /api/v1/usuarios/me` (confirmação por senha) → `UsuarioExclusaoService` apaga todos os dados do titular em transação única, em ordem de FK; arquivos de upload removidos após commit. Exportação `/api/v1/exportar/completo` ganhou dados cadastrais, carteiras, metas e contas fixas (incluindo inativas).
  - **#11** `V19__consentimento_usuario.sql` (`politica_versao`, `consentimento_em`); register exige `aceitaTermos=true` e grava versão (`app.politica.versao`) + timestamp.
- **Arquivos alterados:** `application.properties`, `application-prod.properties`, `AnexoService.java`, `AnexoController.java`, `TokenHasher.java` (novo), `RefreshTokenService.java`, `AuthController.java`, `UsuarioExclusaoService.java` (novo), `UsuarioController.java`, `ExcluirContaRequest.java` (novo), `ExportService.java`, `MetaRepository.java`, `ContaFixaRepository.java`, `Usuario.java`, `RegisterRequest.java`, `V19__consentimento_usuario.sql` (novo), testes: `AnexoServiceTest.java` (novo), `UsuarioExclusaoTest.java` (novo), `AuthControllerTest.java`.
- **Testes/validacoes executadas:** suíte completa do backend PASS (95 testes, 0 falhas).
- **Resultado:** PASS
- **Ressalvas:**
  - **BREAKING para clientes:** register agora exige `aceitaTermos: true` — web e mobile precisam de checkbox de consentimento antes do deploy conjunto. Upload de anexo fora da whitelist retorna 422 (HEIC do iOS não incluído; converter no app ou ampliar whitelist).
  - **#2 parte infra pendente (só o operador pode fazer):** firewall no Postgres da VPS (porta 5433 restrita ao IP da app), trocar usuário/senha do banco. Host/user antigos permanecem no histórico Git — mitigação é rotacionar, não reescrever.
  - Pendentes do backlog: #5 (rate limit atrás de proxy confiável), #9 (SMTP real no EmailService).

---

## BUG-0048 — Auditoria de UI mobile: tokens, design system e acessibilidade (PROB-0061 a PROB-0064)

- **Problema relacionado:** PROB-0061, PROB-0062, PROB-0063, PROB-0064
- **Data:** 2026-07-10
- **Area:** mobile
- **Sintoma/risco por item:**
  - **PROB-0061** Onboarding com paleta Tailwind fora da canônica (categoria criada ficava com cor não re-selecionável no editor), CTA final verde `#22C55E` (viola "verde é dinheiro, violeta é marca"), inputs/chips manuais e zero a11y.
  - **PROB-0062** `#ffffff`/`#fff` fixos em perfil e splash (contraste quebrado no dark mode) e tiles arco-íris no hub "Mais" (anti-referência do PRODUCT.md).
  - **PROB-0063** Telas de auth com inputs manuais sem `accessibilityLabel`, links em `brand` (~3.5:1, falha AA) e alvos de toque < 44pt.
  - **PROB-0064** Categorias com FAB caseiro sem label, swatches de cor sem role/estado e espaçamento duplicado.
- **Correcao aplicada:**
  - Onboarding: `CATEGORIAS_SUGERIDAS` → `CATEGORY_COLORS` (novo cinza neutro na paleta para "Outros"); CTA em `brand`/`brandText`; inputs → `Field`, chips → `Chip`; roles `checkbox`/`button` + estados + alvos ≥44pt; barra de progresso simplificada.
  - Tema: `brandText` no botão brand do perfil; "Sair" em `dangerBg`+`danger`; splash em `colors.bg`; tiles do hub todos em `brandBg`; badge "Em breve" 8→10pt.
  - Auth (login/register/forgot/reset): inputs → `Field` (com `autoComplete`/`textContentType`); links → `brandFg` (AA); minHeight 44 e `accessibilityRole` nos toques; radius 12 unificado.
  - Categorias: componente `Fab` ("Nova categoria"); swatches com role `radio` + `selected` + hitSlop; Nome via `Field`; Cancelar/Salvar com role, alvo 44pt e `brandFg`.
- **Arquivos alterados:** `mobile/app/onboarding.tsx`, `mobile/app/index.tsx`, `mobile/app/(app)/perfil.tsx`, `mobile/app/(app)/more/index.tsx`, `mobile/app/(app)/more/categorias.tsx`, `mobile/app/(auth)/login.tsx`, `mobile/app/(auth)/register.tsx`, `mobile/app/(auth)/forgot-password.tsx`, `mobile/app/(auth)/reset-password.tsx`, `mobile/src/utils/format.ts`.
- **Testes/validacoes executadas:** `npx tsc --noEmit` PASS no mobile (script `lint`).
- **Resultado:** PASS
- **Ressalvas:** Mudanças visuais/a11y — validar no Expo nos dois temas (onboarding e auth). Demais telas `more/` (faturas, contas-fixas, investimentos, ...) ainda têm inputs manuais fora do `Field` — mesmo padrão, pendente de replicação. Verificações Entrance/ScreenTransition/FloatEmoji: já respeitavam Reduce Motion (nenhuma ação).

---

## BUG-0049 — Investimentos: venda acima da posição, divisão por zero e 500 em tipo inválido

- **Problema relacionado:** PROB-0054
- **Data:** 2026-07-11
- **Area:** backend, investimentos, integridade financeira
- **Sintoma:** `InvestimentoService.adicionarMovimentacao` permitia VENDA com quantidade maior que a posição atual (quantidade final negativa), dividia por zero ao calcular preço médio em VENDA com posição zero, lançava `RuntimeException` genérica (500) para tipo de movimentação inválido, e BONIFICACAO somava `valorTotal` ao custo indevidamente (deveria ser custo zero, reduzindo o preço médio).
- **Causa raiz:** `adicionarMovimentacao`/`updateAtivoPosicao` implementados como atualização aritmética direta sobre `Ativo.quantidade`/`custoTotal` sem validação de domínio nem tratamento por tipo de movimentação (COMPRA/VENDA/DIVIDENDO/BONIFICACAO).
- **Correcao aplicada:** Reescrita de `adicionarMovimentacao` e `updateAtivoPosicao`: VENDA acima da posição rejeitada com `BusinessException` ("Quantidade insuficiente para venda..."); quantidade sempre > 0 e preço >= 0 (> 0 exceto BONIFICACAO); tipo inválido vira `BusinessException` em vez de exceção não tratada; DIVIDENDO não altera quantidade/custo; BONIFICACAO usa custo ZERO. Lookups de ativo migrados de `RuntimeException` para `ResourceNotFoundException`. Adicionalmente, integração opcional de caixa: novo campo `MovimentacaoRequest.carteiraId` — se informado, COMPRA debita e VENDA/DIVIDENDO creditam a carteira via `LedgerService.registrarMovimento` (nova origem `INVESTIMENTO`).
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/InvestimentoService.java`, `backend/src/main/java/com/gestor/financeiro/dto/MovimentacaoRequest.java`, `backend/src/main/java/com/gestor/financeiro/model/enums/OrigemMovimentoCarteira.java`, `backend/src/main/resources/db/migration/V22__movimentos_carteira_origem_investimento.sql`, `backend/src/test/java/com/gestor/financeiro/InvestimentoServiceTest.java` (novo).
- **Testes/validacoes executadas:** 14 novos testes em `InvestimentoServiceTest` (venda acima da posição, venda sem posição não divide por zero, quantidade/preço não positivos, tipo inválido, bonificação sem custo, dividendo sem alterar posição, compra/venda/dividendo movimentando caixa, saldo insuficiente na compra, origem `INVESTIMENTO`, sem carteira não gera movimento). Suite completa: 116 testes, 0 falha. Migration V22 (chain V1..V22) aplicada em PostgreSQL 16 real via Docker CLI; CHECK confirmado aceitando `INVESTIMENTO` e rejeitando valor fora do domínio.
- **Resultado:** PASS
- **Ressalvas:** Integração de caixa é opt-in — enquanto o mobile não enviar `carteiraId`, patrimônio de investimentos e caixa seguem desacoplados (decisão de produto, não regressão). Migrations V20/V21/V22 e as mudanças de código deste fix ainda não commitadas/deployadas. `PostgresMigrationIT` segue dependente de Docker (PROB-0058).
- **Commit:** pendente

---

## BUG-0050 — Relatório somava transações canceladas em maiores despesas, gasto por conta e contagem

- **Problema relacionado:** PROB-0053 (também PROB-0035)
- **Data:** 2026-07-11
- **Area:** backend, relatórios, performance
- **Sintoma:** No relatório, `totalEntradas`/`totalSaidas` já excluíam transações canceladas (`ativa = false`), mas "maiores despesas", "gasto por conta" e a contagem de transações vinham de `findByUsuarioIdAndDataBetween` — que **não filtrava `ativa`** — então uma SAIDA cancelada aparecia entre as maiores despesas, somava no gasto por conta e inflava a contagem, divergindo dos totais. Em paralelo, relatórios e projeções carregavam listas completas em memória (risco de OOM com histórico grande).
- **Causa raiz:** `RelatorioService` e `ProjecaoService` mantiveram o padrão antigo de carregar entidades e filtrar/somar em Java (o dashboard já havia migrado para SQL). O load em memória do relatório usava uma query sem o predicado `ativa = true`.
- **Correcao aplicada:** `RelatorioService` migrado para 3 queries agregadas em `TransacaoRepository` (`findMaioresDespesas` com LEFT JOIN categoria + `ORDER BY valorTotal DESC` + `Pageable(0,10)`; `sumSaidasAgrupadoPorConta` com `GROUP BY` conta + `ORDER BY SUM DESC` + `Pageable(0,8)`; `countSaidasByUsuarioIdAndPeriodo`) — todas filtrando `ativa = true`, alinhando os três blocos aos totais. `ProjecaoService` trocou os helpers por `SUM(COALESCE(...))` no banco (`ContaFixaRepository.somarPlanejadoNoPeriodo`, `ParcelaRepository.somarValorNoPeriodo`, `FaturaCartaoRepository.somarValorTotalPorStatusNoPeriodo`). Contrato dos endpoints mantido.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/RelatorioService.java`, `.../service/ProjecaoService.java`, `.../repository/TransacaoRepository.java`, `.../repository/ContaFixaRepository.java`, `.../repository/ParcelaRepository.java`, `.../repository/FaturaCartaoRepository.java`, `backend/src/main/resources/db/migration/V23__relatorio_projecao_support_indexes.sql` (novo), `backend/src/test/java/com/gestor/financeiro/RelatorioServiceTest.java` (novo), `.../ProjecaoServiceTest.java` (novo).
- **Testes/validacoes executadas:** `RelatorioServiceTest` (3: totais ignorando ENTRADA e cancelada, maiores despesas ordenadas/limitadas/cor padrão sem categoria, gasto por conta agrupado/ordenado/tipo resolvido) e `ProjecaoServiceTest` (2: soma conta fixa pendente do mês e ignora paga, sem lançamentos mantém saldo). SQL logado confirma `group by`/`order by`/`fetch first N rows only`. Suíte completa: 121 testes, 0 falha. Migrations V1..V23 aplicadas em PostgreSQL 16 real (psql em container descartável); 3 índices de suporte criados (2 parciais `WHERE ativa/ativo = true` + 1 composto).
- **Resultado:** PASS
- **Ressalvas:** Índices de suporte não validados via `PostgresMigrationIT` (segue dependente de Docker/Testcontainers — PROB-0058); validação feita por psql direto. Projeção ainda emite ~3 queries por mês projetado (N pequeno). Mudanças ainda não commitadas.
- **Commit:** pendente

---

---

## BUG-0053 — Implementado rollover de credito/saldo devedor de fatura de cartao (R1/R2)

- **Problema relacionado:** PROB-0050 (fecha o restante do escopo, ja que pagamento parcial foi resolvido por BUG-0052)
- **Data:** 2026-07-11
- **Area:** backend, frontend, mobile, produto financeiro
- **Sintoma:** A regra de produto para credito de fatura (total `<= 0`) e saldo devedor rolado (pagamento parcial no fechamento) estava **especificada** em `SYSTEM_OVERVIEW.md` (decisao travada em 2026-07-11) mas **nao implementada** em codigo: `pagarFatura` nao tratava o caso de fatura com total zero/negativo e nao havia rollover explicito de credito ou divida entre faturas.
- **Causa raiz:** Modelo de fatura tratava credito/estorno como quitacao simples dentro da mesma fatura, sem mecanismo de "carregar" saldo (credor ou devedor) para a proxima competencia.
- **Correcao aplicada:** Arquitetura escolhida pelo dono do produto — **rollover lazy na leitura + servico idempotente + trava de banco**, sem endpoint de fechar fatura nem scheduler (status `FECHADA` continua derivado na leitura, como desde BUG-0020). Novo metodo `FaturaService.liquidarFaturaAnterior(...)`, chamado por `buscarAtual`, `buscarPorMes` e `criarOuBuscarFatura`: ao materializar a fatura de competencia M, liquida recursivamente para tras (M-1, M-2, ...) as faturas existentes ja fechadas. Recursao termina por competencia decrescendo estritamente, fatura anterior inexistente (nao materializa fatura retroativa vazia) ou teto de 24 meses.
  - **R1** (total da origem `<= 0`): gera lancamento `CREDITO_ANTERIOR` (valor negativo) na proxima fatura em aberto; marca a fatura de origem `PAGA` com `dataPagamento = dataFechamento`. Nunca cria `MovimentoCarteira`.
  - **R2** (total `> 0` e `valorPago < total`): gera `SALDO_DEVEDOR_ANTERIOR` (valor positivo = total - valorPago) na proxima fatura; nao altera status da origem alem do derivado padrao. Sem juros (fora de escopo do MVP).
  - **Idempotencia/trava:** guard em codigo `FaturaLancamentoRepository.existsByFaturaOrigemId` + lock pessimista na fatura de origem (`findWithLockByIdAndUsuarioId`) + unique index parcial `ux_fatura_rollover_origem_tipo (fatura_origem_id, tipo) WHERE fatura_origem_id IS NOT NULL` (backstop de banco, migration `V25__fatura_rollover.sql`); `DataIntegrityViolationException` tratada como no-op.
  - **Modelo:** enum `TipoFaturaLancamento` ganhou `CREDITO_ANTERIOR` e `SALDO_DEVEDOR_ANTERIOR`. `FaturaLancamento.transacao` passou a ser nullable; novo campo `faturaOrigem` para rastreabilidade. `toResponse` corrigido para nao dar NPE em lancamento sem transacao.
  - **UI web+mobile:** lancamentos `CREDITO_ANTERIOR` exibidos em verde ("Credito anterior"); `SALDO_DEVEDOR_ANTERIOR` em ambar/alerta ("Saldo devedor anterior", nunca vermelho). Tipos TS estendidos.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`, `backend/src/main/java/com/gestor/financeiro/model/FaturaLancamento.java`, `backend/src/main/java/com/gestor/financeiro/model/enums/TipoFaturaLancamento.java`, `backend/src/main/java/com/gestor/financeiro/repository/FaturaLancamentoRepository.java`, `backend/src/main/resources/db/migration/V25__fatura_rollover.sql` (novo), `frontend/src/pages/Faturas.tsx`, `frontend/src/services/faturaService.ts`, `mobile/app/(app)/more/faturas.tsx`, `mobile/src/services/faturaService.ts`, `mobile/src/types/index.ts`.
- **Testes/validacoes executadas:** `FaturaRolloverTest.java` (novo) — 7 casos: R1 basico, credito abate a proxima fatura, credito rola de novo (fatura seguinte tambem `<= 0`), R2 saldo devedor rolado, pagamento total sem gerar rollover, idempotencia em dupla leitura (buscar 2x nao duplica lancamento), cadeia de rollover com mes pulado (fatura intermediaria inexistente). Invariante `Conta.valorGasto` assertado dentro dos casos 1, 4 e 6. Execucao real desta rodada: `./mvnw -q test` → **Tests run: 142, Failures: 0, Errors: 0**; `scripts/verify-postgres-migrations.sh` → PASS (`PostgresMigrationIT` 5/0); frontend `npm run build --silent` → PASS; mobile `npm run lint --silent` → PASS. Nao-regressao: `FaturaCartaoWorkflowTest` 9/9 continua verde.
- **Resultado:** PASS
- **Ressalvas:**
  - Unique index `ux_fatura_rollover_origem_tipo` da migration V25 **nao existe no schema de teste** (H2 create-drop, Flyway desligado em teste) — idempotencia testada apenas pelo guard de codigo `existsByFaturaOrigemId`; o backstop de banco nao e exercitado por teste automatizado. Concorrencia real de 2 threads simultaneas materializando a mesma fatura futura **nao tem teste dedicado** (design coberto por lock pessimista + unique index, a validar em producao/PostgreSQL real — mesma limitacao estrutural de `PostgresMigrationIT` dependente de Docker, PROB-0058).
  - Unique index de rollover foi validado via `scripts/verify-postgres-migrations.sh` em PostgreSQL real de teste (`PostgresMigrationIT`), mas concorrencia real de 2 threads simultaneas materializando a mesma fatura futura ainda nao tem teste dedicado.
- **Commit:** `a62f594`, `70f24e5`

---

## BUG-0059 — Rate limit de auth deixa de ser contornavel via X-Forwarded-For forjado

- **Problema relacionado:** PROB-0066
- **Data:** 2026-07-14
- **Area:** backend, seguranca, infra
- **Sintoma:** `LoginRateLimitFilter`/`AuthController` resolviam o IP do cliente via `getRemoteAddr()` com `forward-headers-strategy=framework`; combinado com nginx em modo *append-only* no `X-Forwarded-For`, o primeiro IP da lista (controlado pelo cliente) era usado como chave do rate limit de login/forgot-password/register, permitindo contornar os limites trocando o header a cada tentativa.
- **Causa raiz:** Confianca implicita em todo o `X-Forwarded-For` recebido, sem que Tomcat/nginx normalizassem o header a partir de uma lista fechada de proxies confiaveis.
- **Correcao aplicada:** `forward-headers-strategy` trocado de `framework` para `native` em `application-vps.properties`, com `RemoteIpValve` (`remote-ip-header=X-Forwarded-For`, `protocol-header=X-Forwarded-Proto`, `internal-proxies` cobrindo loopback e faixas privadas Docker). Env var `SERVER_FORWARD_HEADERS_STRATEGY=native` adicionada em `docker-compose.production.yml` e `docker-compose.vps.yml` (a env var sobrepoe o profile — os dois arquivos precisavam ser atualizados). `nginx.conf.template` (topologia standalone, 1 hop) passou a sobrescrever `X-Forwarded-For` com `$remote_addr`. `nginx.npm.conf` (atras do Nginx Proxy Manager, 2 hops) mantem append-only, com a premissa de que o NPM anexa seu proprio `$remote_addr` documentada em `deploy/vps/README.md`. Rede Docker interna dedicada `web<->API` criada em `docker-compose.production.yml`, com a API removida da rede `proxy` (NPM so alcanca o container `web`).
- **Arquivos alterados:** `backend/src/main/resources/application-vps.properties`, `docker-compose.production.yml`, `docker-compose.vps.yml`, `deploy/vps/nginx.conf.template`, `deploy/vps/nginx.npm.conf`, `deploy/vps/README.md`
- **Testes/validacoes executadas:** `./mvnw -q test` → 155/155 PASS; `./mvnw -q verify` → BUILD SUCCESS. `nginx -t` e smoke de rate limit contra `X-Forwarded-For` forjado em staging **nao executados nesta rodada** (gate de deploy pendente).
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Validacao automatizada cobre apenas o build/testes de unidade do backend; a cadeia real de proxies (nginx standalone e/ou Nginx Proxy Manager) nao foi exercitada em staging/producao. `nginx -t` nos dois configs, recriacao das redes do compose e smoke validando que XFF forjado nao muda o bucket ficam pendentes (ver BACKLOG-0080).
- **Commit:** `c959dfc`

---

## BUG-0060 — Pagamento de parcela deixa de duplicar debito na carteira

- **Problema relacionado:** PROB-0067
- **Data:** 2026-07-14
- **Area:** backend, banco
- **Sintoma:** `PUT /api/v1/parcelas/{id}/pagar` chamado duas vezes para a mesma parcela ja paga criava um segundo `MovimentoCarteira` de saida, debitando a carteira duas vezes pelo mesmo pagamento.
- **Causa raiz:** `ParcelaService.marcarComoPaga` sem guard de estado (`PAGO`) e sem `@Version` na entidade `Parcela` para serializar escrita concorrente.
- **Correcao aplicada:** Guard adicionado — `marcarComoPaga` retorna no-op (sem gerar movimento) se a parcela ja estiver `PAGO`. Campo `@Version private Long version` adicionado a `Parcela`, com coluna `version BIGINT NOT NULL DEFAULT 0` criada via migration `V28__pre_production_hardening.sql`, mesmo padrao ja usado em Carteira/Conta/Meta/Categoria (PROB-0002). Deliberadamente sem `idempotencyKey` estatica, para preservar o fluxo de produto pagar → despagar → pagar.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/ParcelaService.java`, `backend/src/main/java/com/gestor/financeiro/model/Parcela.java`, `backend/src/main/resources/db/migration/V28__pre_production_hardening.sql`, `backend/src/test/java/com/gestor/financeiro/ParcelaServiceTest.java`
- **Testes/validacoes executadas:** Novo teste `ParcelaServiceTest.pagarParcelaJaPagaEhIdempotente`; `./mvnw -q test` → 155/155 PASS; `./mvnw -q verify` → BUILD SUCCESS.
- **Resultado:** PASS
- **Ressalvas:** Concorrencia real (duas threads simultaneas pagando a mesma parcela ainda nao paga) depende do `@Version` gerar 409 via `OptimisticLockingFailureException`, mas nao tem teste automatizado dedicado de concorrencia com threads simultaneas.
- **Commit:** `0d1e0c0`

---

## BUG-0061 — Exclusao de carteira em uso normal deixa de retornar HTTP 500

- **Problema relacionado:** PROB-0068
- **Data:** 2026-07-14
- **Area:** backend, banco
- **Sintoma:** `DELETE /api/v1/carteiras/{id}` numa carteira com movimentos de origem `TRANSACAO`/`PARCELA` (uso normal, o caminho mais comum) retornava HTTP 500 por violacao de FK `RESTRICT`, em vez de um erro de negocio tratado.
- **Causa raiz:** `CarteiraService.deletar` so verificava movimentos de origem `CARTEIRA_AJUSTE`, deixando o superset de origens reais (transacoes, parcelas) sem checagem previa.
- **Correcao aplicada:** Novo metodo `existsByCarteiraId(Long)` em `MovimentoCarteiraRepository`; `CarteiraService.deletar` bloqueia a exclusao com a `BusinessException` de negocio ja existente sempre que houver qualquer movimento associado a carteira, independentemente da origem.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/CarteiraService.java`, `backend/src/main/java/com/gestor/financeiro/repository/MovimentoCarteiraRepository.java`, `backend/src/test/java/com/gestor/financeiro/CarteiraControllerTest.java`
- **Testes/validacoes executadas:** Novos testes `CarteiraControllerTest.deletarCarteiraComMovimentoDeTransacaoRetornaErroDeNegocio` e `CarteiraControllerTest.deletarCarteiraSemMovimentoRemove`; `./mvnw -q test` → 155/155 PASS.
- **Resultado:** PASS
- **Ressalvas:** Nenhuma identificada.
- **Commit:** `0d1e0c0`

---

## BUG-0062 — Indices adicionados para `movimentos_carteira.carteira_id` e `refresh_tokens.usuario_id`

- **Problema relacionado:** PROB-0069
- **Data:** 2026-07-14
- **Area:** banco
- **Sintoma:** Consulta isolada por `carteira_id` (usada por `existsByCarteiraId`, novo do BUG-0061) e consultas de auth por `usuario_id` em `refresh_tokens` (login/refresh/logout-all) sem indice dedicado, resultando em full scan.
- **Causa raiz:** Indices nao acompanharam o crescimento de queries por esses campos; o unico indice existente em `movimentos_carteira` (V11) e composto e liderado por `usuario_id`.
- **Correcao aplicada:** `CREATE INDEX IF NOT EXISTS idx_movimentos_carteira_carteira ON movimentos_carteira(carteira_id)` e `CREATE INDEX IF NOT EXISTS idx_refresh_tokens_usuario ON refresh_tokens(usuario_id)`, na mesma migration que adiciona a coluna `version` de `Parcela` (BUG-0060).
- **Arquivos alterados:** `backend/src/main/resources/db/migration/V28__pre_production_hardening.sql`
- **Testes/validacoes executadas:** `scripts/verify-postgres-migrations.sh` PASS contra PostgreSQL real via Docker, incluindo a migration V28.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Validacao de integracao (`PostgresMigrationIT`/`scripts/verify-postgres-migrations.sh`) contra PostgreSQL real nao foi reexecutada por este agente nesta rodada; recomenda-se confirmar antes de promover para producao.
- **Commit:** `0d1e0c0`

---

## BUG-0063 — SPA passa a servir headers de seguranca (HSTS, X-Frame-Options, CSP, etc.)

- **Problema relacionado:** PROB-0070
- **Data:** 2026-07-14
- **Area:** frontend, seguranca, infra
- **Sintoma:** Rotas fora de `/api/**` (SPA servido pelo nginx) nao recebiam nenhum header de seguranca aplicado pelo Spring Security no backend.
- **Causa raiz:** `SecurityConfig` (Spring) so intercepta `/api/**`; o SPA e servido por um nginx separado sem os headers configurados.
- **Correcao aplicada:** Adicionados em `nginx.conf.template` e `nginx.npm.conf`: HSTS, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin` e CSP (`default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'`), repetidos no bloco `/assets/` (add_header nao herda de blocos pai quando o filho declara o proprio).
- **Arquivos alterados:** `deploy/vps/nginx.conf.template`, `deploy/vps/nginx.npm.conf`
- **Testes/validacoes executadas:** `frontend npm run build` → PASS (build nao afetado pela mudanca, que e so de configuracao de proxy). `nginx -t` e carregamento manual do SPA validando ausencia de violacao de CSP no console **nao executados nesta rodada**.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** CSP restritiva (sem `unsafe-inline`) nao foi validada contra o SPA carregado de fato em staging; risco de quebra silenciosa se algum ponto do app depender de inline script/style nao coberto pelo build. Ver BACKLOG-0080.
- **Commit:** `c959dfc`

---

## BUG-0064 — Token de reset de senha deixa de trafegar na query string

- **Problema relacionado:** PROB-0071
- **Data:** 2026-07-14
- **Area:** backend, frontend, seguranca, LGPD
- **Sintoma:** `GET /api/auth/validate-token?token=...` expunha o token de reset de senha na query string, sujeito a access logs de proxies/CDN e historico do navegador.
- **Causa raiz:** Endpoint de validacao implementado como `GET` com parametro de query em vez de `POST` com corpo.
- **Correcao aplicada:** Novo endpoint `POST /api/auth/validate-token` com `ValidateTokenRequest { token }` (`@NotBlank`, `@Size(max=255)`); `GET` removido do controller (agora retorna 405 via novo handler de `HttpRequestMethodNotSupportedException` no `GlobalExceptionHandler`, que antes caia no catch-all e respondia 500). `frontend/src/pages/ResetPassword.tsx` atualizado para o novo contrato POST. Email de recuperacao (deep link mobile) nao foi alterado.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/controller/AuthController.java`, `backend/src/main/java/com/gestor/financeiro/dto/ValidateTokenRequest.java` (novo), `backend/src/main/java/com/gestor/financeiro/exception/GlobalExceptionHandler.java`, `frontend/src/pages/ResetPassword.tsx`, `backend/API.md`, `deploy/vps/README.md`
- **Testes/validacoes executadas:** `AuthControllerTest` atualizado, incluindo `validateToken_getRemovidoRetorna405` e conversao dos testes existentes de GET para POST; `./mvnw -q test` → 155/155 PASS; `frontend npm run build` → PASS; `frontend npm run test` → 15/15 PASS.
- **Resultado:** PASS
- **Ressalvas:** Mudanca de contrato de API e breaking para qualquer integrador externo que ainda dependa do `GET` antigo — sem versionamento formal de API no projeto, mitigado apenas por atualizacao de documentacao.
- **Commit:** `5c08ce0`

---

## BUG-0065 — Teto de validacao adicionado a `TransacaoRequest.totalParcelas`

- **Problema relacionado:** PROB-0072
- **Data:** 2026-07-14
- **Area:** backend
- **Sintoma:** `totalParcelas` aceitava qualquer valor positivo sem limite superior (ex.: 999999 parcelas seria aceito pela validacao de DTO).
- **Causa raiz:** Validacao original cobria apenas o piso, sem teto.
- **Correcao aplicada:** `@Max(120)` adicionado ao campo `totalParcelas` em `TransacaoRequest`.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/dto/TransacaoRequest.java`
- **Testes/validacoes executadas:** `./mvnw -q test` → 155/155 PASS.
- **Resultado:** PASS
- **Ressalvas:** Nenhuma identificada.
- **Commit:** `0d1e0c0`

---

## BUG-0066 — Recorrências invertiam salário e não protegiam execução automática

- **Data:** 2026-07-14
- **Area:** backend, banco, mobile, segurança, projeção e release Android
- **Sintoma:** toda conta fixa era tratada como saída, fazendo salário reduzir a projeção; não existia execução automática segura, histórico por ocorrência ou bloqueio local dos dados financeiros.
- **Causa raiz:** `ContaFixa` não possuía tipo, carteira ou modo de execução; a projeção subtraía todas as recorrências; a navegação duplicava animação e a sessão persistida não tinha uma camada de desbloqueio local.
- **Correcao aplicada:** tipo `ENTRADA/SAIDA`, execução manual/automática, carteira obrigatória na automação, scheduler com timezone, recuperação após reinício, ocorrência única por recorrência/vencimento, chave `RECORRENCIA:{id}:{data}`, lock pessimista, falha por saldo sem débito, endpoint `/realizar`, aviso no Dashboard, projeção com entradas e bloqueio mobile por biometria/credencial/senha.
- **Migration:** `V29__recorrencias_automaticas.sql`; registros existentes permanecem `SAIDA` e manuais.
- **Compatibilidade:** `/pagar` foi preservado; campos anteriores do DTO de projeção permanecem e `totalEntradas` foi adicionado.
- **Testes/validacoes executadas:** migrations PostgreSQL PASS; Maven 164/164 PASS; testes focados 6/6 PASS; Jest 11/11; ESLint e TypeScript PASS; Expo export PASS; Android Release PASS; iOS Simulator Release PASS; `git diff --check` PASS.
- **Release:** mobile `1.1.0` (`versionCode 4`), APK interno com SHA-256 `931f6754c9056239f3db9508dc2c47731317ac3eef29abf78d26ba2c65e47fc9`.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** APK local usa a chave debug do template Expo; assinatura definitiva, CI remoto, smoke em hardware físico e publicação na store não foram executados.
- **Commit:** este commit

---

## BUG-0067 — `formatDate` do mobile exibia dia anterior para datas `LocalDate` (fuso UTC-3)

- **Problema relacionado:** N/A
- **Data:** 2026-08-19
- **Area:** mobile
- **Sintoma:** `formatDate` em `mobile/src/utils/format.ts` fazia `new Intl.DateTimeFormat('pt-BR').format(new Date(date))`. Para strings date-only `YYYY-MM-DD` (todo campo `LocalDate` vindo da API), `new Date('2026-08-19')` e interpretada como meia-noite UTC, que em `America/Sao_Paulo` (UTC-3) cai no dia anterior. Descoberto no simulador iOS (iPhone 17 Pro, iOS 26.5) com stack local (backend `dev` na porta 8090, banco descartavel `gf_sim` no container `gf-postgres`, Metro com `EXPO_PUBLIC_API_BASE_URL=http://localhost:8090/api`): transacao salva com `data=2026-08-19` (confirmado via API) aparecia como 18/08/2026 na lista de Transacoes.
- **Causa raiz:** Confirmada — `formatDate` delegava toda entrada, inclusive strings date-only sem componente de hora, para `Intl.DateTimeFormat` via `new Date(string)`, que o motor JS interpreta em UTC quando a string nao tem hora/timezone explicito.
- **Correcao aplicada:** `formatDateOnlyBR` movido para antes de `formatDate` em `mobile/src/utils/format.ts`; `formatDate` agora detecta string date-only via regex `ISO_DATE_ONLY` e delega para `formatDateOnlyBR` (que monta a data por componentes, sem passar por `Date`/UTC), mantendo o caminho `Intl` para objetos `Date` e strings ISO com hora. `formatDateTime` nao foi alterado (so recebe `LocalDateTime`). Removidos 3 workarounds ad-hoc `'T00:00:00'` que contornavam o mesmo bug localmente em `mobile/app/(app)/more/investimentos.tsx:292`, `mobile/app/(app)/more/relatorios.tsx:79` e `:281`. Novo teste `mobile/src/__tests__/format.test.ts` (nao existia teste de `format.ts` antes). `mobile/package.json`: script de teste passou a ser `TZ=America/Sao_Paulo jest --runInBand`, porque a CI (`.github/workflows/ci.yml:59,79` → `npm run test`) roda em UTC, onde o bug nao reproduz — sem TZ fixo o teste de regressao passaria mesmo com o codigo quebrado.
- **Call sites afetados, todos corrigidos pela mudanca na origem (nao precisaram de edicao individual):** `mobile/app/(app)/transacoes.tsx:229` (`t.data`, sintoma originalmente observado); `mobile/app/(app)/metas.tsx:111` — gravidade alta, nao era so visual: a data deslocada virava default do input de edicao e voltava ao backend via `parseDateBR`, persistindo a data errada; `mobile/app/(app)/metas.tsx:294` e `:408` (`meta.dataPrevista`); `mobile/app/(app)/more/faturas.tsx:216,218,320` (`dataFechamento`, `dataVencimento`, `l.data`).
- **Arquivos alterados:** `mobile/src/utils/format.ts`, `mobile/app/(app)/more/investimentos.tsx`, `mobile/app/(app)/more/relatorios.tsx`, `mobile/package.json`, `mobile/src/__tests__/format.test.ts` (novo)
- **Testes/validacoes executadas:** Jest → 13 suites / 41 testes PASS. Regressao comprovada manualmente: com `git stash` do `format.ts` corrigido, o novo teste falha com `Expected "19/08/2026" / Received "18/08/2026"`, `"01/01/2026"/"31/12/2025"` e `"31/12/2026"/"30/12/2026"`. `npm run typecheck` → PASS. `npm run lint` → **2 erros pre-existentes** em `mobile/src/components/NovaTransacaoModal.tsx:108` e `:164` ("Definition for rule 'react-hooks/exhaustive-deps' was not found"); confirmado pre-existente reproduzindo o mesmo erro com a arvore limpa (`git stash -u`), em arquivo nao tocado por esta correcao. Evidencia visual no simulador iOS: lista de Transacoes passou a mostrar 19/08/2026; meta "Viagem" com data limite 31/12/2026 exibe "ate 31/12/2026", a tela de edicao reabre com 31/12/2026 e, apos salvar, a API mantem `dataPrevista: 2026-12-31` (round-trip correto).
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** (1) Sem commit ainda — mudancas apenas no working tree sobre a baseline `175d8ea` (branch `main`). (2) `npm run lint` do mobile falha por 2 erros pre-existentes e nao relacionados (`react-hooks/exhaustive-deps` nao encontrada) — como o lint e `--max-warnings=0` e bloqueante na CI, isso bloqueia merge ate ser corrigido; ver BACKLOG-0093. (3) Mesmo bug de fuso confirmado no frontend web, sem correcao aplicada nesta rodada (mobile e web nao compartilham helper de formatacao); ver BACKLOG-0092. (4) Armadilha de verificacao registrada: o dev-client do simulador serviu bundle de cache do Metro por 2 tentativas, mostrando o valor antigo mesmo com o codigo corrigido; so apos reiniciar o Metro com `expo start -c` a tela refletiu a correcao — relevante para quem for reproduzir a validacao.
- **Commit:** pendente

---

---

## BUG-0068 — Maestro `smoke-auth.yaml` asserta rótulos de tab bar que não existem mais

- **Problema relacionado:** N/A
- **Data:** 2026-08-21
- **Area:** mobile, documentacao/teste
- **Sintoma:** `mobile/.maestro/smoke-auth.yaml` fazia `assertVisible` para "Início", "Transações",
  "Planejamento" e "Mais" logo após o login. Nenhuma dessas quatro strings corresponde à tab bar
  atual do app (`Início · Análises · + · Metas · Ajustes`, `mobile/app/(app)/_layout.tsx`) — os
  rótulos "Transações", "Planejamento" e "Mais" não existem em nenhuma tela do app no estado atual
  do repositório. Descoberto ao revisar o flow como parte do redesign de `ajustes.tsx` na mesma
  sessão (a antiga tela "Mais" deixou de existir como tal).
- **Causa raiz:** O flow não foi atualizado nas rodadas anteriores de redesign de navegação/tema
  (commits `9a3b205`/`63df4b1`/`73caf8b` e seguintes, que trocaram a tab bar) nem na reversão do
  protótipo "Fase 4" (PROB-0082, 2026-08-19, que restaurou uma tab bar diferente desta). Além
  disso, a tab bar nativa (`react-native-screens`) não expõe os rótulos das abas na árvore de
  acessibilidade que o Maestro lê — mesmo corrigindo os textos, `assertVisible` por rótulo de aba
  não é uma asserção confiável para essa tab bar.
- **Correcao aplicada:** As quatro asserções por rótulo de aba foram substituídas por
  `assertVisible: "Saldo Disponível"`, conteúdo real da Home que confirma que o login levou à tela
  certa, com um comentário no próprio YAML explicando por que a asserção deixou de ser por aba.
- **Arquivos alterados:** `mobile/.maestro/smoke-auth.yaml`
- **Testes/validacoes executadas:** Correção lida e conferida por inspeção do arquivo; a execução
  real do flow em simulador/dispositivo via Maestro **não foi executada nesta rodada** (mesma
  pendência de rodada Maestro/visual já registrada para o Bloco B da Fase 3 e para o redesign de
  metas/tema).
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Não executado em Maestro real nesta rodada — só a leitura/edição do YAML foi
  validada. Outros flows Maestro do projeto (`financial-critical.yaml`, `fase4-visual.yaml` já
  removido) não foram auditados em busca do mesmo problema nesta sessão.
- **Commit:** pendente (working tree não commitado sobre a baseline `12cc447` na `main`)

---

## BUG-0069 — Mensagem de erro genérica na exclusão de conta com senha incorreta

- **Problema relacionado:** PROB-0083
- **Data:** 2026-08-21
- **Area:** mobile, LGPD
- **Sintoma:** No fluxo novo de exclusão de conta (`mobile/app/(app)/ajustes.tsx`), enviar a senha
  errada para `DELETE /v1/usuarios/me` fazia a tela exibir "Dados inválidos. Verifique os campos."
  em vez de "Senha incorreta" — a mensagem de negócio devolvida pelo backend (HTTP 422,
  `{"code":"BUSINESS_ERROR","message":"Senha incorreta"}`) era descartada pelo interceptor
  genérico do Axios (ver PROB-0083, causa raiz completa e não corrigida na origem).
  Reproduzido contra backend local (porta 8093, banco `gf_ajustes`) via `curl` e via app no
  simulador iOS.
- **Causa raiz:** `mobile/src/services/api.ts` só usa `error.response.data.details` para montar a
  mensagem amigável de 400/422; `BusinessException` sem `details` (`details: null`) nunca chega à
  UI com o texto real.
- **Correcao aplicada:** Contorno local, não a correção do interceptor (que fica pendente,
  BACKLOG-0094): em `mobile/app/(app)/ajustes.tsx`, o handler do modal de exclusão lê
  `err.response.data.message` diretamente quando `err.response.data.code === 'BUSINESS_ERROR'`,
  em vez de depender de `err.userMessage` (que o interceptor já teria mascarado).
- **Arquivos alterados:** `mobile/app/(app)/ajustes.tsx`
- **Testes/validacoes executadas:** Manual via `curl -X DELETE
  http://localhost:8093/api/v1/usuarios/me` com senha errada (422, mensagem "Senha incorreta"
  confirmada no corpo da resposta) e com senha certa (204, seguido de tentativa de login com a
  mesma conta falhando — confirma exclusão real). Teste automatizado
  `mobile/src/__tests__/AjustesScreen.test.tsx` (`'mostra o erro do backend e mantém a sessão
  quando a senha está errada'`) cobre o caminho de erro com mock do Axios. Suite completa do
  mobile: 172 testes PASS (`npm test`); `npm run lint` e `npm run typecheck` limpos.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Correção é local a esta tela — qualquer outra tela que dependa de mensagem de
  `BusinessException` em 400/422 continua recebendo o texto genérico até o interceptor ser
  corrigido na origem (PROB-0083/BACKLOG-0094). Sem commit ainda — mudanças no working tree sobre
  a baseline `12cc447` (branch `main`).
- **Commit:** pendente

---

## BUG-0083 — `more/orcamentos.tsx`: falha de rede exibida como "orçamento inexistente", risco de duplicação

- **Problema relacionado:** N/A
- **Data:** 2026-08-22
- **Area:** mobile
- **Sintoma:** O `useQuery` de orçamento não expunha `isError` e o `queryFn` engolia qualquer erro
  de `GET /v1/orcamentos` (mês passado) com `.catch(() => null)`. Uma falha de rede caía no mesmo
  ramo visual de "Nenhum orçamento para {mês}", com o botão "Criar Orçamento" desenhado por cima de
  um orçamento que existia mas não tinha carregado — caminho direto para o usuário criar um
  orçamento duplicado para o mesmo mês.
- **Causa raiz:** Confirmada — o contrato real do backend distingue os dois endpoints: `GET
  /v1/orcamentos` (mês arbitrário) faz `orElseThrow(ResourceNotFoundException)` e retorna 404
  quando o mês não tem orçamento, enquanto `GET /v1/orcamentos/atual` busca-ou-cria e **nunca**
  devolve 404 (por isso o mês corrente nunca precisou de tratamento de ausência). A tela tratava
  qualquer erro, inclusive falha de rede/5xx, como sinônimo de "orçamento ausente" (404).
- **Correcao aplicada:** `useQuery` passa a expor `isError`; só HTTP 404 é interpretado como
  ausência de orçamento (habilita "Criar Orçamento"). Qualquer outro erro sobe para um ramo próprio
  que mostra `EstadoVazio` com `refetch()`, sem oferecer criação. Regressão travada em
  `mobile/src/__tests__/OrcamentoScreen.test.tsx` (três caminhos: ausência real por 404, falha de
  rede, sucesso).
- **Arquivos alterados:** `mobile/app/(app)/more/orcamentos.tsx`,
  `mobile/src/__tests__/OrcamentoScreen.test.tsx` (novo)
- **Testes/validacoes executadas:** `mobile/src/__tests__/OrcamentoScreen.test.tsx` cobrindo os três
  caminhos; conferido que o teste de rede falha sem o ramo de erro (regressão comprovada antes da
  correção). `npm run typecheck`, `npm run lint` e `npm test` completos do mobile limpos ao final da
  série de 13 PRs (244 testes em 29 suítes) — ver BUG-0091 para o resumo de execução consolidado.
- **Resultado:** PASS
- **Ressalvas:** Nenhuma validação em simulador/Maestro para este fluxo específico nesta rodada
  (ver BACKLOG-0098).
- **Commit:** `707df848bcd71e939e5fc33e1e2f6cfd8898196d`

---

## BUG-0084 — `accessibilityLabel` curado apagando conteúdo real da árvore de acessibilidade (reincidência da classe BACKLOG-0096)

- **Problema relacionado:** N/A (mesma classe de defeito de BACKLOG-0096, já fechado; esta é
  reincidência encontrada durante a migração visual, não o mesmo item reaberto)
- **Data:** 2026-08-21 a 2026-08-22 (série de 13 PRs de padronização visual)
- **Area:** mobile, acessibilidade
- **Sintoma:** Vários pontos do app fabricavam `accessibilityLabel` manualmente para um `Touchable`
  cujos filhos já tinham texto visível, e esse label sintético **substituía** (não complementava) o
  texto real na árvore de acessibilidade — leitor de tela lia menos informação do que a pessoa
  vidente via na tela. Instâncias confirmadas por commit:
  - `mobile/src/components/ui/ListRow.tsx` (kit, `9c1335be`): fabricava
    `accessibilityLabel` a partir do título, apagando subtítulo e valor de toda linha de lista do
    app que usa `ListRow` (afeta todo consumidor do componente, incluindo
    `(inicio)/transacoes.tsx` a partir de sua própria migração em `a2b0249f`, que herdou a correção
    já aplicada no kit).
  - `mobile/app/(app)/metas.tsx` (`e0f2ccf4`): cartões de modalidade de meta com label curado
    apagando o texto visível da opção.
  - `mobile/app/(app)/more/investimentos.tsx` (`11a9195b`): card do ativo com
    `accessibilityLabel="Abrir investimento {ticker}"`, apagando nome, tipo, valor de mercado e
    rentabilidade.
  - `mobile/app/(app)/more/carteiras.tsx` (`e04846e5`): card da conta com
    `accessibilityLabel="Ver extrato da conta {nome}"`, apagando subtipo, saldo e banco.
  - `mobile/app/(app)/more/contas-fixas.tsx` (`0e65a78c`): os três botões de ação do card
    (`Editar`/`Pular`/`Pagar`-`Receber`) carregavam `accessibilityLabel` com o nome da conta
    injetado (`"Editar Aluguel"`, `"Pular Aluguel este mês"`), divergindo do texto visível
    (`"Editar"`) — o caso que `DESIGN.md:166` nomeia explicitamente como proibido, porque o leitor
    de tela anuncia uma coisa e a busca por texto (inclusive em teste/Maestro) procura outra.
  - `mobile/app/(app)/more/visao-financeira.tsx` (`e7714f43`): as nove métricas carregavam
    `accessibilityLabel="{label}: {valor}. Ver composição"`, apagando `DESCRICOES[id]` (a
    descrição do glossário ADR-0013) da árvore — justamente o texto que explica o que a métrica
    significa.
- **Causa raiz:** Confirmada — padrão repetido de compor `accessibilityLabel` manualmente em vez de
  deixar o React Native derivar o rótulo dos filhos com texto visível, mesma causa raiz já corrigida
  uma vez em BACKLOG-0096 (2026-08-21) em outro conjunto de telas, mas não generalizada ao kit nem
  varrida nas telas que ainda não tinham migrado para o padrão visual novo.
- **Correcao aplicada:** Em todos os pontos listados, o `accessibilityLabel` curado foi removido
  (deixando o RN compor o rótulo a partir do conteúdo visível); onde havia contexto extra legítimo
  a preservar (ex.: nome da conta na ação "Editar"), o contexto foi movido para
  `accessibilityHint`, seguindo a convenção registrada em `DESIGN.md` (seção "Acessibilidade").
  `mobile/.maestro/financial-critical.yaml` foi ajustado em paralelo (ver Ressalvas) para parar de
  depender dos labels curados que deixaram de existir.
- **Arquivos alterados:** `mobile/src/components/ui/ListRow.tsx`, `mobile/app/(app)/metas.tsx`,
  `mobile/app/(app)/more/investimentos.tsx`, `mobile/app/(app)/more/carteiras.tsx`,
  `mobile/app/(app)/more/contas-fixas.tsx`, `mobile/app/(app)/more/visao-financeira.tsx`
- **Testes/validacoes executadas:** `mobile/src/__tests__/padraoVisual.test.ts` (trinco novo, ver
  BUG-0091) não varre `accessibilityLabel` diretamente, mas cada tela foi validada por
  `npm run typecheck` e `npm test` a cada PR; suite final 244 testes / 29 suítes PASS. Sem
  verificação manual com VoiceOver/TalkBack.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** (1) Não houve verificação manual com leitor de tela real (VoiceOver/TalkBack) para
  nenhuma das telas listadas. (2) Verificação de que nenhuma outra tela do app ainda tem essa
  classe de bug não foi feita por varredura automatizada — depende de revisão manual futura, já
  que o trinco `padraoVisual.test.ts` não cobre `accessibilityLabel`.
- **Commit:** `9c1335be7ebec9d5bf6ca60e39471fbfd93bda38`, `e0f2ccf42d857c17f1d08207dde9f657e3b8fbad`,
  `11a9195bbcff8ff63b3fca464927187be6733152`, `e04846e50f48f6ad37381113875a147e493d337b`,
  `0e65a78c074ab3f04dd1b6fa741bb9910b73a62d`, `e7714f43d3c82e39543f18cb8fa8baafe4859335`

---

## BUG-0085 — `more/investimentos.tsx`: FAB próprio escondido atrás do painel flutuante da tab bar

- **Problema relacionado:** N/A
- **Data:** 2026-08-22
- **Area:** mobile
- **Sintoma:** O botão `+` de cadastrar ativo era um círculo desenhado à mão com `bottom: 24`,
  posição que fica atrás do painel flutuante da tab bar (que ocupa a faixa inferior da tela) —
  exatamente o caso que `ui/Fab` já existe para resolver, com `bottom: useTabBarSpace()`.
- **Causa raiz:** Confirmada — a tela de investimentos não usava o componente `ui/Fab` do kit,
  reimplementando o botão flutuante com posicionamento fixo que não considera a altura real da tab
  bar.
- **Correcao aplicada:** Substituição do círculo próprio por `ui/Fab`.
- **Arquivos alterados:** `mobile/app/(app)/more/investimentos.tsx`
- **Testes/validacoes executadas:** `npm run typecheck` e `npm test` do mobile limpos (ver resumo
  consolidado em BUG-0091). Sem verificação visual em simulador nesta rodada.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Sem confirmação visual em simulador/dispositivo de que o FAB agora fica acima do
  painel da tab bar em todos os tamanhos de tela.
- **Commit:** `11a9195bbcff8ff63b3fca464927187be6733152`

---

## BUG-0086 — `perfil.tsx`: troca de senha com campos crus e erro de negócio no campo errado

- **Problema relacionado:** N/A
- **Data:** 2026-08-22
- **Area:** mobile, seguranca
- **Sintoma:** A troca de senha no perfil era o único formulário de senha do app que não usava
  `ui/CampoSenha`: os dois campos usavam `Field secureTextEntry` cru, sem olho de mostrar/ocultar e
  sem medidor de força — a senha se digitava inteiramente às cegas. Além disso, só existia um
  estado `senhaError`, preso ao campo "Nova senha": quando o backend recusava por
  `BusinessException` ("Senha atual incorreta", erro sem campo associado no contrato de erro), a
  mensagem aparecia embaixo do campo "Nova senha", levando o usuário a acreditar que a senha nova
  estava errada quando o problema era a senha atual.
- **Causa raiz:** Confirmada — (1) o formulário de troca de senha da tela de perfil nunca tinha sido
  migrado para `ui/CampoSenha` (usado em login, cadastro, recuperação e exclusão de conta desde
  antes desta série); (2) o roteamento de erro não distinguia erro de campo (`AlterarSenhaRequest`
  tem os campos `senhaAtual`/`novaSenha`) de erro de regra de negócio sem campo — todo erro caía no
  único estado `senhaError` do campo errado.
- **Correcao aplicada:** Os dois campos passam a `ui/CampoSenha`. O tratamento de erro passa a usar
  `camposDeErro` (mapa por nome de campo, `senhaAtual`/`novaSenha`) para erros de validação e
  `mensagemDeErro` (faixa geral, sem campo) para regra de negócio sem campo associado — como
  `DESIGN.md:127-129` já especificava para o resto do app.
- **Arquivos alterados:** `mobile/app/(app)/perfil.tsx`
- **Testes/validacoes executadas:** `npm run typecheck`, `npm run lint` e `npm test` do mobile
  limpos ao final da série (244 testes / 29 suítes — ver BUG-0091). Sem teste automatizado dedicado
  ao roteamento de erro deste formulário especificamente.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Sem verificação manual do fluxo de erro (senha atual incorreta) contra backend real
  nesta rodada, e sem teste automatizado dedicado ao cenário.
- **Commit:** `ad5fc0224b1b68742e55b693be2339fd876113e1`

---

## BUG-0087 — `metas.tsx`: encadeamento de folhas sem cleanup e leitura direta de erro Axios

- **Problema relacionado:** N/A
- **Data:** 2026-08-22
- **Area:** mobile
- **Sintoma:** Duas falhas distintas no mesmo arquivo: (1) o encadeamento de folhas modais (fechar
  uma, abrir a próxima) usava três `setTimeout(…, 350)` soltos, sem cleanup nem cancelamento — um
  `setState` podia disparar depois da tela já ter saído da árvore, e alternar de folha rapidamente
  deixava dois agendamentos correndo ao mesmo tempo; (2) `deletarMutation.onError` lia
  `error.response.data.message` diretamente na mão, contra a regra centralizada em
  `src/utils/erros.ts`.
- **Causa raiz:** Confirmada — (1) ausência de um timer único cancelável para a transição entre
  folhas `pageSheet` (necessária porque o iOS não apresenta uma nova `pageSheet` enquanto a anterior
  ainda está fechando); (2) acesso direto à forma do erro Axios em vez do helper padrão do projeto.
- **Correcao aplicada:** (1) Um timer só, guardado em ref, cancelado no desmonte do componente e a
  cada nova troca de folha antes de agendar a próxima — a espera de 350ms continua existindo (ainda
  é necessária pelo motivo do iOS), só deixou de vazar; (2) `deletarMutation.onError` passa a usar
  `mensagemDeErro`, o helper padrão de `src/utils/erros.ts`.
- **Arquivos alterados:** `mobile/app/(app)/metas.tsx`
- **Testes/validacoes executadas:** `npm run typecheck` e `npm test` do mobile limpos (resumo
  consolidado em BUG-0091). Sem teste automatizado dedicado ao cleanup do timer (cenário de
  race condition não é trivial de reproduzir em teste de unidade/Jest sem fake timers dedicados).
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Sem teste automatizado específico para a condição de corrida do timer antigo; a
  correção foi validada por leitura de código e pela suíte geral, não por um teste que reproduza o
  bug original e falhe sem a correção.
- **Commit:** `e0f2ccf42d857c17f1d08207dde9f657e3b8fbad`

---

## BUG-0088 — `carteiras.tsx` e `categorias.tsx`: erro genérico da API jogado sempre no campo Nome

- **Problema relacionado:** N/A
- **Data:** 2026-08-22
- **Area:** mobile
- **Sintoma:** Em ambas as telas, o `onError` da mutação de criação jogava qualquer erro vindo da
  API no estado de erro do campo "Nome" (`nomeError`) — um erro de outro campo (ex.: "saldo
  inválido" em `carteiras.tsx`) aparecia embaixo de "Nome", sem relação com o campo que de fato
  causou o problema.
- **Causa raiz:** Confirmada — ausência de separação entre erro de campo específico e erro de faixa
  geral; o handler assumia que todo erro de criação era sobre o nome. Em `carteiras.tsx` havia ainda
  uma duplicidade: `handleSalvar` tinha `mutateAsync` dentro de um `try/catch` **e** um `onError` da
  mutação, os dois escrevendo no mesmo estado.
- **Correcao aplicada:** Separação de campo e faixa geral via `camposDeErro`/`mensagemDeErro` (mesmo
  padrão já usado em `register`/`onboarding`), removendo em `carteiras.tsx` a duplicidade
  `try/catch` + `onError`.
- **Arquivos alterados:** `mobile/app/(app)/more/carteiras.tsx`, `mobile/app/(app)/more/categorias.tsx`
- **Testes/validacoes executadas:** `npm run typecheck` e `npm test` do mobile limpos (resumo
  consolidado em BUG-0091).
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Sem teste automatizado dedicado a este roteamento de erro especificamente em
  nenhuma das duas telas.
- **Commit:** `e04846e50f48f6ad37381113875a147e493d337b`, `5bf99a8a66a775f73274e6a963f5772a2923cd74`

---

## BUG-0089 — `more/visao-financeira.tsx`: seção de projeção sumia sem aviso quando a query falhava

- **Problema relacionado:** N/A
- **Data:** 2026-08-22
- **Area:** mobile
- **Sintoma:** `projecaoQuery` não tratava `isLoading` nem `isError`. Quando a projeção falhava, a
  seção inteira desaparecia da tela sem qualquer aviso, e o usuário não conseguia distinguir "não há
  projeção para o período" de "a chamada falhou".
- **Causa raiz:** Confirmada — a seção só renderizava com base em `data`, sem ramos para os estados
  de carregamento e erro do React Query.
- **Correcao aplicada:** A seção passa a ter skeleton durante o carregamento, `EstadoVazio` com
  `refetch()` em caso de erro, e estado vazio explícito quando a API retorna sem dados.
- **Arquivos alterados:** `mobile/app/(app)/more/visao-financeira.tsx`
- **Testes/validacoes executadas:** `npm run typecheck` e `npm test` do mobile limpos (resumo
  consolidado em BUG-0091).
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Sem teste automatizado dedicado aos três ramos (loading/erro/vazio) desta seção
  específica.
- **Commit:** `e7714f43d3c82e39543f18cb8fa8baafe4859335`

---

## BUG-0090 — `more/orcamentos.tsx`: salvamento mudo sem limites preenchidos e setas de mês inacessíveis

- **Problema relacionado:** N/A
- **Data:** 2026-08-22
- **Area:** mobile, acessibilidade
- **Sintoma:** Duas falhas distintas no mesmo arquivo, independentes do BUG-0083: (1) `salvar()`
  retornava silenciosamente, sem nenhum aviso, quando nenhum limite de categoria estava preenchido;
  (2) as setas de navegação de mês não tinham `accessibilityRole`, não tinham rótulo acessível e o
  alvo de toque era menor que 44 (só `padding: 8`) — invisíveis para leitor de tela e fora do
  mínimo de área de toque do app.
- **Causa raiz:** Confirmada — (1) ausência de validação com feedback antes de `salvar()`; (2) as
  setas eram um `TouchableOpacity` cru sem os atributos de acessibilidade e sem `hitSlop`/padding
  suficiente para o alvo mínimo.
- **Correcao aplicada:** (1) `salvar()` passa a informar o que falta preencher antes de retornar;
  (2) novo componente `ui/NavegadorDeMes` (alvo de 44, `accessibilityRole`, rótulo e
  `accessibilityState`) substitui as setas cruas — o mesmo controle já existia correto em
  `(inicio)/transacoes.tsx`; as duas telas passam a compartilhá-lo.
- **Arquivos alterados:** `mobile/app/(app)/more/orcamentos.tsx`,
  `mobile/app/(app)/(inicio)/transacoes.tsx`, `mobile/src/components/ui/NavegadorDeMes.tsx` (novo)
- **Testes/validacoes executadas:** `npm run typecheck` e `npm test` do mobile limpos (resumo
  consolidado em BUG-0091).
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Sem verificação manual com leitor de tela para confirmar o comportamento do novo
  `ui/NavegadorDeMes`.
- **Commit:** `707df848bcd71e939e5fc33e1e2f6cfd8898196d`

---

## BUG-0091 — `analises.tsx`: barra de categoria assumia ordenação do backend; `categorias.tsx`: seletor de cor rotulado por posição

- **Problema relacionado:** N/A
- **Data:** 2026-08-21 (`analises.tsx`) e 2026-08-22 (`categorias.tsx`)
- **Area:** mobile
- **Sintoma:** Três defeitos de baixo impacto, agrupados por severidade: (1) em `analises.tsx`, a
  régua de 100% das barras de categoria lia `gastosPorCategoria[0]`, assumindo implicitamente que o
  backend sempre mandava a lista ordenada por valor — se a ordem mudasse, a barra ficaria incorreta
  sem erro visível; (2) na mesma tela, uma lista vinda da API usava `key={i}` (índice) em vez de uma
  chave estável; (3) em `categorias.tsx`, o seletor de cor da categoria rotulava as opções por
  posição ("Cor 1", "Cor 2", ...) para leitor de tela, então quem usa VoiceOver/TalkBack não sabia
  qual cor estava escolhendo, apesar de os nomes reais das cores já existirem havia tempo como
  comentário ao lado do hex em `CATEGORY_COLORS`.
- **Causa raiz:** Confirmada nos três casos — (1)/(2) suposições implícitas sobre ordenação/chave
  estável no consumo da API; (3) o rótulo de acessibilidade nunca tinha sido extraído dos
  comentários já existentes no código para um dado estruturado.
- **Correcao aplicada:** (1) a régua de 100% passa a ser `Math.max` sobre a lista recebida, honesta
  independente da ordem; (2) `key={i}` substituído por `mes.periodo`; (3) novo mapa `NOME_DA_COR`
  em `mobile/src/utils/format.ts`, extraído dos comentários existentes em `CATEGORY_COLORS` — a
  ordem do array de cores não muda, pois `categoriasIniciais` e `NovaTransacaoModal` continuam
  escolhendo por índice.
- **Arquivos alterados:** `mobile/app/(app)/analises.tsx`, `mobile/app/(app)/more/categorias.tsx`,
  `mobile/src/utils/format.ts`
- **Testes/validacoes executadas:** `npm run typecheck`, `npm run lint` e `npm test` completos do
  mobile ao final da série de 13 PRs: **244 testes em 29 suítes PASS** (eram 200 em 26 suítes antes
  da série). Este é o resumo de verificação local consolidado referenciado pelas demais entradas
  BUG-0083 a BUG-0090 desta mesma rodada.
- **Resultado:** PASS_COM_RESSALVA
- **Ressalvas:** Nenhum dos quatro flows Maestro (`financial-critical.yaml`, `smoke-auth.yaml`,
  `privacy-consent.yaml`, `recovery-navigation.yaml`) foi executado nesta máquina para validar os
  ajustes de rótulo feitos em `financial-critical.yaml` ao longo da série (5 passos ajustados: 3 em
  `e04846e5` para acompanhar o rótulo curado removido de `carteiras.tsx`, 1 em `d44fc43` para o
  guard-rail de erro reconhecer "Não deu para...", e o restante na unificação de telas de
  referência em `49e14a0d`). Ver BACKLOG-0098.
- **Commit:** `d44fc43844a259d90ce5c4b7afbcde30d6887faa`, `5bf99a8a66a775f73274e6a963f5772a2923cd74`

---

## BUG-0092 — `more/fatura.tsx`: primeiro toque em "Pagar Fatura" engolido pelo teclado

- **Problema relacionado:** N/A
- **Data:** 2026-08-22
- **Area:** mobile
- **Sintoma:** Com o campo de valor focado (teclado aberto), o primeiro toque em "Pagar Fatura"
  não pagava nada; só o segundo toque, com o teclado já fechado, gravava o pagamento. Reproduzido
  em runtime pelo flow `mobile/.maestro/financial-critical.yaml`: o passo do Maestro reportava o
  toque como `COMPLETED`, mas `faturas_cartao.valor_pago` continuava `0.00` no banco logo depois;
  um segundo toque manual na mesma tela já assentada gravava `25.00`.
- **Causa raiz:** Confirmada — o `ScrollView` da tela não definia `keyboardShouldPersistTaps`. O
  padrão do React Native é `"never"`: com o teclado aberto, o primeiro toque fora do campo é
  consumido para fechar o teclado e não chega ao filho. Como "Pagar Fatura" fica logo abaixo do
  campo de valor, digitar um pagamento parcial e tocar no botão em seguida via apenas o teclado
  sumir. Varredura em `mobile/app/**` confirmou que esta era a **única** tela com `TextInput`
  dentro de `ScrollView` sem esse prop — as outras 9 telas equivalentes já usavam `"handled"`.
- **Correcao aplicada:** `keyboardShouldPersistTaps="handled"` no `ScrollView` de
  `mobile/app/(app)/more/fatura.tsx`.
- **Arquivos alterados:** `mobile/app/(app)/more/fatura.tsx`
- **Testes/validacoes executadas:** Reproduzido e confirmado corrigido em runtime (simulador iPhone
  17 Pro, iOS 26.5, stack local porta 8081, banco `gf_verify`) via
  `mobile/.maestro/financial-critical.yaml` — o passo de pagamento parcial passou a gravar
  `valor_pago = 25.00` no primeiro toque. `npx tsc --noEmit`, `npm run lint` e `npm test` (244
  testes / 29 suítes) limpos ao final da rodada.
- **Resultado:** PASS
- **Ressalvas:** Bug pré-existente à série de padronização visual (a raiz do `ScrollView` é
  idêntica antes e depois do commit `9c1335be`) — não foi introduzido por ela, apenas encontrado
  durante a verificação em runtime desta rodada (2026-08-22).
- **Commit:** pendente

---

## BUG-0093 — `more/fatura.tsx`: teclado permanecia aberto após pagamento concluído

- **Problema relacionado:** BUG-0092 (consequência direta da correção)
- **Data:** 2026-08-22
- **Area:** mobile
- **Sintoma:** Com o toque de "Pagar Fatura" deixando de ser engolido (BUG-0092), o teclado
  passou a ficar aberto depois de um pagamento concluído com sucesso, cobrindo a lista de
  "Lançamentos" e a barra de navegação inferior.
- **Causa raiz:** Confirmada — nada fechava o teclado explicitamente após `pagarFatura` resolver
  com sucesso; sem o toque engolido, o foco no campo de valor permanecia.
- **Correcao aplicada:** `Keyboard.dismiss()` chamado imediatamente após o sucesso de
  `pagarFatura`, antes das invalidações de query. O campo continua sendo recarregado com o saldo
  restante pelo efeito já existente, para quem quiser pagar o resto em seguida.
- **Arquivos alterados:** `mobile/app/(app)/more/fatura.tsx`
- **Testes/validacoes executadas:** Confirmado em runtime no mesmo simulador/stack de BUG-0092 —
  teclado fecha e a lista de lançamentos fica visível logo após o pagamento.
- **Resultado:** PASS
- **Ressalvas:** Nenhuma.
- **Commit:** pendente

---

## BUG-0094 — `CardMeta.tsx`: ações do card de meta inacessíveis ao leitor de tela (touchable aninhado)

- **Problema relacionado:** N/A (mesma classe de defeito de BUG-0084/BACKLOG-0096, mas caso
  estruturalmente diferente: aqui é aninhamento de `TouchableOpacity`, não `accessibilityLabel`
  curado)
- **Data:** 2026-08-22
- **Area:** mobile, acessibilidade
- **Sintoma:** A raiz do card era um `TouchableOpacity` com `accessibilityRole="button"`, e o iOS
  funde todos os descendentes num único nó de acessibilidade. Dump real da árvore (Maestro
  `hierarchy`) ANTES da correção:
  `Meta Smoke, 0%, R$ 0,00, de R$ 1.000,00, Excluir a meta Meta Smoke, Editar a meta Meta Smoke, Depositar`.
  "Depositar", "Editar" e "Excluir" eram anunciados como texto, não como elementos próprios — o
  VoiceOver não tinha como acioná-los, e o Maestro também não achava o botão (`Element not found:
  Text matching regex: Depositar`).
- **Causa raiz:** Confirmada — `TouchableOpacity` na raiz do card envolvia tanto o bloco de
  informação (que deveria abrir os detalhes) quanto a linha de ações (`Depositar`/editar/excluir,
  que já eram `TouchableOpacity`/`Botao` próprios), criando touchable dentro de touchable. Uma
  varredura em `mobile/app/**` e `mobile/src/components/**` confirmou que `CardMeta` era o único
  caso desse padrão no app.
- **Correcao aplicada:** A raiz do card virou `View`; apenas o bloco de informação (ícone, nome,
  progresso, valores) passou a ser o `TouchableOpacity` que abre os detalhes; a linha de ações
  ficou irmã dele, fora do touchable. Dump da árvore DEPOIS da correção, agora com quatro nós
  próprios: `🏷️, Meta Folha, 0%, R$ 0,00, de R$ 1.000,00` / `Depositar` / `Editar a meta Meta
  Folha` / `Excluir a meta Meta Folha`. Sem mudança visual (screenshot comparado antes/depois).
- **Arquivos alterados:** `mobile/src/components/metas/CardMeta.tsx`
- **Testes/validacoes executadas:** Dumps de árvore de acessibilidade via `maestro hierarchy`
  antes e depois da correção (mesma fonte que VoiceOver consome); screenshot visual comparado sem
  diferença; `npm test` (244/29) e `npx tsc --noEmit` limpos.
- **Resultado:** PASS
- **Ressalvas:** Verificação feita pela árvore de acessibilidade do Maestro, não com VoiceOver
  realmente ligado no dispositivo — ver `docs/BACKLOG.md` BACKLOG-0078 (pendência de validação
  assistiva em device físico, já existente, atualizada nesta rodada).
- **Commit:** pendente

---

## BUG-0095 — `backend/RelatorioService.java`: relatório de categorias mostrava o texto "null" no lugar do ícone

- **Problema relacionado:** N/A
- **Data:** 2026-08-22
- **Area:** backend
- **Sintoma:** Na tela "Gastos por categoria" (`analises.tsx`), categorias sem ícone cadastrado
  mostravam literalmente o texto `null` dentro do tile do ícone, em vez do emoji de fallback
  (capturado em screenshot).
- **Causa raiz:** Confirmada — em `RelatorioService.gastosPorCategoria`, o ícone era construído
  com `String.valueOf(row[4])`; quando a coluna SQL é `NULL`, `String.valueOf(null)` devolve a
  string `"null"` (não um `null` de verdade), que viajava intacta no JSON até o app. O fallback do
  app (`{c.icone || '🏷️'}` em `analises.tsx`) só cobre `null`/string vazia, não a string não vazia
  `"null"`. As linhas vizinhas do mesmo método (`Maiores despesas`) já faziam a guarda correta —
  foi um caso esquecido nesta função.
- **Correcao aplicada:** Novo helper privado `asTexto(Object)` que devolve `null` de verdade
  quando o valor é `null` (em vez de `String.valueOf`); aplicado ao campo de ícone. O campo `cor`
  ganhou fallback explícito `"#6B7280"` quando `null`, mesma convenção já usada duas linhas abaixo
  no mesmo arquivo.
- **Arquivos alterados:**
  `backend/src/main/java/com/gestor/financeiro/service/RelatorioService.java`
- **Testes/validacoes executadas:** Confirmado pela API (`"icone": null` no JSON, em vez de
  `"icone": "null"`) e visualmente no app (tile agora mostra 🏷️). Suite backend completa: 292
  testes PASS, `mvn` `BUILD SUCCESS`.
- **Resultado:** PASS
- **Ressalvas:** Nenhuma.
- **Commit:** pendente

---

## BUG-0096 — Backend: falhas de refresh token (expirado/revogado/não encontrado) respondiam 422/404 em vez de 401, deixando o cliente sem sinal de sessão morta

- **Problema relacionado:** PROB-0085
- **Data:** 2026-08-22
- **Area:** backend, seguranca
- **Sintoma:** `RefreshTokenService` respondia à falha de renovação de token com três status HTTP
  diferentes conforme a causa: 422 (`BusinessException` "Refresh token expirado"), 404
  (`ResourceNotFoundException` "não encontrado") e 401 (`TokenReuseDetectedException`, reuso
  detectado). Só o 401 tinha tratamento especial nos clientes — 422/404 pareciam erros de negócio
  comuns, não fim de sessão, e o cliente mobile não os interpretava como motivo para deslogar (ver
  BUG-0097). Além disso a janela do refresh token (7 dias) era curta e fixa no código, e
  `RefreshTokenService.limparTokensExpirados()` existia sem nenhum caller — a tabela
  `refresh_tokens` crescia sem limite, agravado pela rotação (cada renovação grava uma linha nova).
- **Causa raiz:** Confirmada — as três saídas de falha de `RefreshTokenService` usavam exceções
  mapeadas para 422/404 no `GlobalExceptionHandler`, em vez de uma exceção dedicada de sessão
  mapeada para 401; `AuthController` tinha o mesmo problema para "Refresh token não fornecido"; e
  `RefreshTokenScheduler` simplesmente não existia.
- **Correcao aplicada:** Nova `exception/SessaoExpiradaException.java`, mapeada em
  `GlobalExceptionHandler` para HTTP 401 com `code: SESSION_EXPIRED` e mensagem "Sessão expirada.
  Faça login novamente.". As três saídas de falha do `RefreshTokenService` (expirado, revogado, não
  encontrado) e o caso "Refresh token não fornecido" do `AuthController` passaram a lançar
  `SessaoExpiradaException`; `TokenReuseDetectedException` continua 401 com code próprio
  (`TOKEN_REUSE_DETECTED`), sem alteração. Janela do refresh token elevada de 7 para 30 dias
  (decisão do dono do produto), agora configurável via `jwt.refresh-expiration-days=30` em
  `application.properties` e nos profiles `dev`/`prod`/`vps`; `RefreshTokenService` passou a ler o
  valor via `@Value` em vez de constante fixa. A rotação já era deslizante (cada renovação regrava a
  expiração a partir de agora) — o que faltava era o refresh de fato acontecer no cliente (ver
  BUG-0097). O Max-Age do cookie web do refresh token em `AuthController` deixou de ser 7 dias fixos
  e passou a acompanhar a mesma property (antes o cookie expirava antes do token que ele carrega).
  Novo `service/RefreshTokenScheduler.java`: cron diário `0 15 3 * * *` (03:15
  `America/Sao_Paulo`, configurável via `app.refresh-token.cleanup.cron`), habilitado por padrão via
  `@ConditionalOnProperty(app.refresh-token.cleanup.enabled, matchIfMissing=true)`, com guarda de
  sobreposição via `AtomicBoolean` (mesmo padrão de `ReconciliacaoScheduler`) chamando o método que
  já existia sem caller.
- **Arquivos alterados:**
  `backend/src/main/java/com/gestor/financeiro/exception/SessaoExpiradaException.java` (novo),
  `backend/src/main/java/com/gestor/financeiro/exception/GlobalExceptionHandler.java`,
  `backend/src/main/java/com/gestor/financeiro/service/RefreshTokenService.java`,
  `backend/src/main/java/com/gestor/financeiro/controller/AuthController.java`,
  `backend/src/main/java/com/gestor/financeiro/service/RefreshTokenScheduler.java` (novo),
  `backend/src/main/resources/application.properties`,
  `backend/src/main/resources/application-dev.properties`,
  `backend/src/main/resources/application-prod.properties`,
  `backend/src/main/resources/application-vps.properties`,
  `backend/src/test/java/com/gestor/financeiro/AuthControllerTest.java`.
- **Testes/validacoes executadas:** Suíte backend completa: 296 testes, 0 falhas (`BUILD SUCCESS`).
  4 testes novos em `AuthControllerTest`: refresh expirado → 401 `SESSION_EXPIRED`; refresh
  desconhecido → 401 `SESSION_EXPIRED`; refresh ausente → 401 `SESSION_EXPIRED`; rotação desliza a
  expiração para ~30 dias e revoga o token anterior. Verificação em runtime na stack local (backend
  porta 8081, banco descartável `gf_sessao`): login emite token com 30 dias de janela; rotação
  revoga o anterior e emite novo; token envelhecido para 2 dias volta a ~30 após renovar
  (deslizamento comprovado); refresh expirado → 401 `SESSION_EXPIRED`; refresh desconhecido → 401
  `SESSION_EXPIRED`; refresh ausente → 401 `SESSION_EXPIRED`; reuso → 401
  `TOKEN_REUSE_DETECTED`; rota protegida com token podre → 401. Scheduler exercitado com cron
  acelerado: log `refresh_token_cleanup_concluido removidos=2`, batendo exatamente com os 2 tokens
  expirados existentes na base de verificação.
- **Resultado:** PASS
- **Ressalvas:** Nenhuma.
- **Commit:** pendente

---

## BUG-0097 — Mobile: desbloqueio por biometria/senha não renovava a sessão, e falha de refresh não-401 não desconectava o usuário

- **Problema relacionado:** PROB-0085
- **Data:** 2026-08-22
- **Area:** mobile, seguranca
- **Sintoma:** Após desbloquear o app com digital, a UI podia ficar presa em erro/loading com log
  acusando token expirado, sem redirecionamento automático ao login — só se recuperava deslogando e
  logando manualmente. Reportado pelo dono do produto em uso real (2026-08-22).
- **Causa raiz:** Confirmada — dois defeitos combinados. (1)
  `mobile/src/services/api.ts` só descartava as credenciais salvas quando a falha de refresh vinha
  com status 401/403; nos casos 422/404 (ver BUG-0096) os tokens mortos continuavam no
  `SecureStore`, `refreshAccessToken()` devolvia `null`, a request original falhava com 401 e nada
  avisava o `AuthContext` — `isAuthenticated` é só `usuario !== null` e `restoreSession` já tinha
  rodado no boot, então a UI seguia montada como autenticada. O guarda de boot tinha o mesmo furo: a
  condição `(401||403) && !refreshToken` nunca fechava, porque o refresh token continuava gravado.
  (2) `AppLockGate.unlockWithDevice` validava a biometria localmente e só fazia
  `setLocked(false)` — nenhuma chamada ao servidor acontecia no desbloqueio, então o cadeado visual
  podia estar sobre uma sessão morta havia dias sem que o app percebesse.
- **Correcao aplicada:** `api.ts`: qualquer **resposta** do servidor na falha de refresh agora
  encerra a sessão (não só 401/403); a ausência de `response` (falha de rede/timeout) preserva os
  tokens — tolerância offline mantida. Novo canal `setOnSessionExpired(fn)` + `encerrarSessao()`
  (limpa `SecureStore`); `refreshAccessToken` passou a ser exportada para ser chamada fora do
  interceptor. `context/AuthContext.tsx` registra o handler de sessão encerrada no mount (limpa
  cache de queries e `setUsuario(null)`, derrubando `isAuthenticated` e levando ao login);
  `restoreSession` passou a encerrar a sessão sempre que o servidor negou a autenticação (401/403),
  sem depender da ausência local do refresh token. `components/AppLockGate.tsx`: tanto
  `unlockWithDevice` (biometria) quanto `unlockWithPassword` agora chamam `refreshAccessToken()`
  antes de liberar a UI — falha de rede libera a UI assim mesmo (tolerância offline: o usuário não
  fica preso fora do app por falta de conexão); sessão recusada pelo servidor cai no canal de sessão
  encerrada acima. A dedup por `refreshPromise` compartilhado (já existente em `api.ts`) evita
  corrida com um 401 simultâneo de tela — importante porque, com rotação, duas chamadas de refresh
  em paralelo fariam a segunda parecer reuso de token e revogar todas as sessões do usuário.
- **Arquivos alterados:** `mobile/src/services/api.ts`, `mobile/src/context/AuthContext.tsx`,
  `mobile/src/components/AppLockGate.tsx`, `mobile/src/__tests__/sessaoExpirada.test.ts` (novo),
  `mobile/src/__tests__/AppLockGate.test.tsx`.
- **Testes/validacoes executadas:** Suíte mobile completa: 30 suítes / 254 testes, 0 falhas. Novo
  `src/__tests__/sessaoExpirada.test.ts` com 7 casos (401/403/404/422 encerram a sessão e avisam o
  handler; falha de rede preserva os tokens; par de tokens rotacionado é gravado corretamente;
  chamada de refresh única compartilhada entre requests concorrentes). `AppLockGate.test.tsx` ganhou
  3 casos novos: desbloqueio por digital chama `refreshAccessToken`; cancelamento da biometria não
  chama refresh; refresh falhando por erro de rede ainda libera a UI (tolerância offline). `npx tsc
  --noEmit` e `npm run lint` limpos.
- **Resultado:** PASS
- **Ressalvas:** Verificação de runtime feita só em iOS/simulador nesta rodada; não há confirmação
  em Android. Biometria continua sem proteger o token em repouso no `SecureStore` (risco residual
  registrado em BACKLOG-0104, fora do escopo desta correção).
- **Commit:** pendente

---

---

## BUG-0098 — Chave de idempotência (`Idempotency-Key`) era descartada silenciosamente em qualquer compra de cartão

- **Problema relacionado:** PROB-0098
- **Data:** 2026-08-29
- **Area:** backend
- **Sintoma:** pré-existente ao trabalho desta sessão — qualquer chamador que enviasse
  `ledgerIdempotencyKey` para uma compra de cartão tinha a chave ignorada; o efeito ficava mascarado
  porque `FaturaService.registrarCompraCartao` já criava a operação de ledger com `requestPayload =
  "compra-cartao|transacao={id}"`, único por transação nova. Encontrado durante a implementação da
  recorrência de cartão (PROB-0098/migration `V67`), quando a execução automática
  (`ContaFixaService`/`RecorrenciaScheduler`) passou a depender de idempotência real para não
  duplicar cobrança em reexecução.
- **Causa raiz:** confirmada — `TransacaoService.registrarMovimentoCriacao` retorna cedo quando
  `carteira == null` (compra de cartão nunca tem `carteira`), então o `ledgerIdempotencyKey` de
  qualquer chamador nunca chegava a ser usado nesse caminho.
- **Correcao aplicada:** nova sobrecarga genérica `FaturaService.registrarCompraCartao(Transacao,
  Long, String idempotencyKey)` — a versão de 2 argumentos passou a delegar para ela com `null`, sem
  alterar nenhum chamador existente. Quando a chave é não-nula, ela é propagada para
  `CriarOperacaoCommand.idempotencyKey` com `requestPayload` estável por ocorrência;
  `TransacaoService.criar` propaga a chave recebida. Muda a semântica de idempotência da compra de
  cartão (registrado em `SYSTEM_OVERVIEW.md`).
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/FaturaService.java`,
  `backend/src/main/java/com/gestor/financeiro/service/TransacaoService.java`.
- **Testes/validacoes executadas:** suíte backend completa (501 testes, 0 falhas) e o novo
  `ContaFixaCartaoTest` (10 casos) exercitam o caminho de execução automática de cartão.
- **Resultado:** PASS
- **Ressalvas:** a cobertura de teste direta é pela via da recorrência de cartão; uma compra de
  cartão avulsa com `Idempotency-Key` enviada explicitamente pelo cliente não tem teste automatizado
  dedicado a este achado especificamente, nem evidência de runtime isolada fora do fluxo de
  recorrência.
- **Commit:** pendente

---

## BUG-0099 — Execução automática de recorrência não revalidava o vencimento sob lock; duas instâncias podiam executar a mesma ocorrência duas vezes

- **Problema relacionado:** PROB-0098
- **Data:** 2026-08-29
- **Area:** backend
- **Sintoma:** pré-existente, encontrado durante a implementação da recorrência de cartão.
  `realizar(..., automatico=true)` pulava a guarda de vencimento que o caminho manual tinha; o
  `RecorrenciaScheduler.recuperarAoIniciar` lê os ids das ocorrências pendentes antes de tomar o lock
  e dispara a cada boot da aplicação. Com duas instâncias do backend, a segunda podia executar uma
  ocorrência que a primeira já havia avançado.
- **Causa raiz:** confirmada — ausência de revalidação de `vencimento <= hoje` depois do lock
  pessimista no caminho automático. No caixa o sintoma era freado por outros guards indiretos (saldo
  insuficiente); no cartão, nada frearia (nenhuma validação de limite existe hoje), então uma segunda
  execução criaria uma segunda `Transacao`/lançamento de fatura para a mesma cobrança.
- **Impacto tecnico:** risco de cobrança duplicada em ambiente com múltiplas instâncias do backend,
  tanto para recorrência de caixa quanto de cartão.
- **Correcao aplicada:** revalidação de `vencimento <= hoje` depois do lock, retornando sem efeito
  (no-op) se a ocorrência já foi avançada por outra execução concorrente. A correção vale para os
  dois destinos (caixa e cartão), fechando o furo também no caminho de caixa que já existia antes
  desta entrega.
- **Arquivos alterados:**
  `backend/src/main/java/com/gestor/financeiro/service/ContaFixaService.java`.
- **Testes/validacoes executadas:** `ContaFixaCartaoTest` (10 casos novos) e suíte backend completa
  (501 testes, 0 falhas). Verificação em runtime: restart do backend local (porta 8081, dispara
  `recuperarAoIniciar`) manteve exatamente 3 lançamentos/3 transações/3 execuções — sem duplicação —
  confirmando que a revalidação sob lock segura a segunda tentativa.
- **Resultado:** PASS
- **Ressalvas:** verificado com restart único de uma instância local (não há ambiente com duas
  instâncias reais rodando em paralelo nesta sessão); a prova de concorrência genuína com dois
  processos simultâneos não foi executada, apenas a reexecução após restart do mesmo processo.
- **Commit:** pendente

---

## BUG-0100 — `carteiraId` do corpo da requisição de `realizar` desviava a cobrança de uma recorrência de cartão para o caixa

- **Problema relacionado:** PROB-0098
- **Data:** 2026-08-29
- **Area:** backend
- **Sintoma:** encontrado durante a implementação da recorrência de cartão. `realizar` montava
  `carteiraEfetiva = carteiraId != null ? carteiraId : ...` usando o valor vindo do corpo da
  requisição (`ValorRequest`); numa recorrência configurada para cartão, um `carteiraId` presente no
  corpo faria a assinatura debitar o caixa em vez do cartão.
- **Causa raiz:** confirmada — `realizar` não considerava que a `ContaFixa` já tinha um destino fixo
  (cartão); o valor do corpo da requisição sempre tinha prioridade sobre o destino cadastrado.
- **Impacto tecnico:** uma assinatura de cartão poderia ser cobrada indevidamente do caixa se o
  cliente (mobile/web/qualquer chamador da API) enviasse `carteiraId` no corpo do `realizar`.
- **Correcao aplicada:** quando a `ContaFixa` tem cartão associado, o `carteiraId` do corpo passa a
  ser ignorado — o destino é sempre o definido no cadastro da recorrência.
- **Arquivos alterados:**
  `backend/src/main/java/com/gestor/financeiro/service/ContaFixaService.java`.
- **Testes/validacoes executadas:** `ContaFixaCartaoTest` (10 casos) e suíte backend completa (501
  testes, 0 falhas). Runtime: `realizar` com `carteiraId` no corpo numa assinatura de cartão não
  tocou o caixa (conta corrente permaneceu 2500,00).
- **Resultado:** PASS
- **Ressalvas:** nenhuma.
- **Commit:** pendente

---

## BUG-0101 — Corrida no unique `(conta_fixa_id, data_vencimento)` de `execucoes_recorrencia` devolvia HTTP 500

- **Problema relacionado:** PROB-0098
- **Data:** 2026-08-29
- **Area:** backend
- **Sintoma:** pré-existente, encontrado durante a implementação da recorrência de cartão. Quando
  duas requisições concorrentes tentavam realizar a mesma ocorrência, a segunda estourava o
  `UNIQUE(conta_fixa_id, data_vencimento)` como `DataIntegrityViolationException`, propagada como
  HTTP 500 em vez de um erro de negócio tratável pelo cliente.
- **Causa raiz:** confirmada — ausência de tradução da exceção de integridade do banco para uma
  exceção de negócio mapeada no `GlobalExceptionHandler`.
- **Impacto tecnico:** cliente (mobile/web) recebia 500 genérico em vez de um 422 com mensagem
  acionável, numa corrida que se tornou mais provável com a recorrência automática de cartão.
- **Correcao aplicada:** `saveAndFlush` encapsulado, traduzindo `DataIntegrityViolationException`
  para `BusinessException` — o mesmo HTTP 422 "Esta recorrência já foi realizada ou pulada" do
  caminho sequencial.
- **Arquivos alterados:**
  `backend/src/main/java/com/gestor/financeiro/service/ContaFixaService.java`.
- **Testes/validacoes executadas:** `ContaFixaCartaoTest` (10 casos) e suíte backend completa (501
  testes, 0 falhas). Runtime: reexecução da mesma ocorrência devolveu 422 "Esta recorrência já foi
  realizada ou pulada" sem duplicar (fatura seguiu com 1 lançamento) — confirmado em vez do 500
  anterior.
- **Resultado:** PASS
- **Ressalvas:** o cenário de corrida real (duas threads/requisições simultâneas de fato) não foi
  reproduzido com concorrência genuína nesta sessão — a validação em runtime foi por reexecução
  sequencial da mesma ocorrência já realizada, não por dois requests paralelos de verdade.
- **Commit:** pendente

---

## BUG-0102 — Exclusão de cartão (soft delete) deixava assinaturas vinculadas cobrando invisivelmente

- **Problema relacionado:** PROB-0098
- **Data:** 2026-08-29
- **Area:** backend
- **Sintoma:** encontrado durante a implementação da recorrência de cartão. `CartaoService.deletarCartao`
  é soft delete (`ativo=false`) e não tinha nenhum conhecimento de `contas_fixas` — excluir um cartão
  que tinha uma assinatura vinculada não desativava a recorrência, que continuaria sendo executada
  pelo scheduler contra um cartão inativo.
- **Causa raiz:** confirmada — ausência de qualquer verificação/ação sobre `contas_fixas` no fluxo de
  exclusão de cartão.
- **Impacto tecnico:** assinatura continuaria sendo cobrada (ou falharia contra a validação de cartão
  inativo, sem aviso) depois de o usuário achar que tinha excluído o cartão e, com ele, a cobrança.
- **Correcao aplicada:** `CartaoService.deletarCartao` passou a desativar, na mesma transação, todas
  as recorrências vinculadas ao cartão excluído, e a retornar quantas foram desativadas — novo
  `ContaFixaRepository.desativarPorConta` (`@Modifying(clearAutomatically = true, flushAutomatically
  = true)`). O endpoint `DELETE` continua devolvendo 204 (contrato inalterado); o app mobile não
  expõe exclusão de cartão hoje, então não houve necessidade de UI nova.
- **Arquivos alterados:** `backend/src/main/java/com/gestor/financeiro/service/CartaoService.java`,
  `backend/src/main/java/com/gestor/financeiro/repository/ContaFixaRepository.java`.
- **Testes/validacoes executadas:** `ContaFixaCartaoTest` (10 casos) e suíte backend completa (501
  testes, 0 falhas). Runtime: `DELETE` do cartão (204) desativou as duas assinaturas vinculadas.
- **Resultado:** PASS
- **Ressalvas:** nenhuma.
- **Commit:** pendente

---

> Este arquivo e mantido pelo `docs-reporter`. Bugs corrigidos devem ser registrados com o proximo ID
> sequencial (BUG-0002, BUG-0003, ...). Para historico de versoes, consulte `docs/CHANGELOG.md`.
