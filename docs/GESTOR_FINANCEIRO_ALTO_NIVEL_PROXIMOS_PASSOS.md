# Gestor Financeiro Pessoal de Alto Nível — Direção Técnica e Próximos Passos

**Projeto:** Gestor Financeiro
**Data:** 2026-07-07
**Objetivo:** definir o que caracteriza um gestor financeiro pessoal de alto nível e qual deve ser a sequência correta de evolução do projeto, considerando robustez, segurança, integridade financeira, performance, UX e capacidade de crescimento.

---

## 1. Veredito executivo

O projeto já possui uma base funcional de MVP: autenticação, dashboard, categorias, contas, carteiras, transações, parcelas, contas fixas, metas, frontend web, mobile inicial e documentação técnica. Porém, ainda não deve ser tratado como “pronto para produção” nem como tecnicamente aprovado para crescimento.

O próximo passo correto não é adicionar novas funcionalidades visuais nem fazer deploy público. O próximo passo correto é consolidar a fundação técnica do sistema financeiro: integridade de dados, isolamento de usuário, controle transacional, versionamento de banco, segurança de sessão, performance das consultas e previsibilidade operacional.

Um gestor financeiro pessoal lida com dados sensíveis e decisões financeiras do usuário. Por isso, a regra central deve ser: **nenhuma feature nova pode avançar enquanto houver risco conhecido de corrupção de saldo, vazamento entre usuários, schema drift, sessão insegura ou operação financeira parcialmente gravada.**

---

## 2. O que é um gestor financeiro pessoal de alto nível

Um gestor financeiro pessoal de alto nível não é apenas uma tela para cadastrar entradas e saídas. Ele deve ser um sistema confiável para registrar, explicar, prever e proteger a vida financeira do usuário.

Ele deve responder quatro perguntas fundamentais com precisão:

1. **Quanto eu tenho agora?**
   Saldo consolidado em carteiras, contas, cartões, metas e compromissos pendentes.

2. **Para onde meu dinheiro está indo?**
   Gastos por categoria, recorrências, padrões mensais, variações e alertas.

3. **O que vai acontecer nos próximos dias ou meses?**
   Contas futuras, parcelas, faturas, metas, orçamento projetado e risco de saldo negativo.

4. **O que eu deveria fazer?**
   Recomendações, limites, alertas, planejamento, economia possível e priorização de compromissos.

A diferença entre um sistema simples e um sistema de alto nível está na confiabilidade. Se o usuário paga uma parcela, move saldo, edita uma transação, exclui uma conta ou altera uma meta, o sistema precisa manter consistência absoluta. Em finanças pessoais, uma diferença pequena de saldo já destrói a confiança do produto.

---

## 3. Princípios obrigatórios do produto

### 3.1 Integridade financeira acima de velocidade de entrega

Toda operação que altera dinheiro deve ser atômica, auditável e reversível quando fizer sentido. Não pode existir operação que salva metade dos dados e falha no restante. Também não pode existir atualização de saldo sem proteção contra concorrência.

Exemplos de operações críticas:

- criar transação de saída e atualizar saldo da carteira;
- pagar parcela e refletir no cartão, conta ou carteira;
- reservar valor em meta financeira;
- cancelar transação parcelada;
- editar transação já refletida no saldo;
- remover conta, carteira ou categoria vinculada a registros históricos.

Essas operações devem usar `@Transactional`, validação de ownership, locking otimista e regras claras de compensação.

### 3.2 Segurança por padrão

O sistema deve assumir que todo ID recebido pelo frontend ou mobile pode ser manipulado. Portanto, nenhum endpoint pode buscar recurso apenas por `id`. O padrão obrigatório deve ser sempre buscar por `id + usuarioId`, ou por uma raiz de posse equivalente.

No estado atual, o sistema é single-user por `usuario_id`. Isso funciona para MVP, mas precisa ser aplicado de forma rígida. Qualquer método como `findById(id)`, `deleteById(id)` ou `buscarCategoria(id)` dentro de fluxo autenticado é potencialmente inseguro se não validar o dono do recurso.

### 3.3 Banco versionado e previsível

`ddl-auto=update` não é aceitável para evolução séria. O banco precisa ser governado por migrations versionadas com Flyway ou Liquibase. O schema financeiro deve ter histórico, revisão, rollback planejado e consistência entre ambientes.

O padrão recomendado é:

- desenvolvimento local pode usar migrations;
- produção deve usar `ddl-auto=validate` ou `none`;
- nenhuma entidade JPA deve alterar o banco automaticamente em produção;
- cada mudança de entidade deve vir acompanhada de migration;
- CI deve validar que a aplicação sobe contra um banco limpo usando as migrations.

### 3.4 Performance desde o início

Um gestor financeiro cresce por volume histórico. Mesmo um usuário individual pode gerar milhares de transações, parcelas, recorrências e eventos. Portanto, dashboard, vencimentos e relatórios não podem carregar tudo em memória.

O banco deve fazer agregações, filtros e paginação. A aplicação deve receber apenas o necessário.

### 3.5 UX orientada a confiança

A experiência de usuário deve ser clara, previsível e segura. O usuário precisa entender o que aconteceu com o dinheiro dele.

Toda tela crítica deve ter:

- estado de carregamento;
- estado vazio;
- estado de erro com mensagem compreensível;
- confirmação para ações destrutivas;
- feedback após salvar, editar, excluir, pagar ou cancelar;
- prevenção de duplo clique em operações financeiras;
- recuperação amigável de conflito ou sessão expirada.

### 3.6 Expansão sem refatoração profunda

Mesmo sendo um MVP, a arquitetura não deve fechar portas. O sistema pode começar single-user, mas deve permitir no futuro:

- conta familiar ou casal;
- múltiplas carteiras e bancos;
- múltiplas moedas;
- anexos e comprovantes;
- importação OFX/CSV;
- integração bancária via Open Finance;
- investimentos;
- orçamento mensal;
- relatórios avançados;
- alertas inteligentes;
- permissões e compartilhamento.

Para isso, as decisões atuais devem evitar acoplamentos frágeis, regras duplicadas e ownership manual espalhado.

---

## 4. Estado atual do projeto segundo os arquivos analisados

### 4.1 Base implementada

O projeto possui backend Java 17 com Spring Boot 3.5.7, PostgreSQL, Spring Data JPA, Spring Security, JWT, refresh token, frontend React/Vite/TypeScript, mobile Expo/React Native e documentação técnica. O domínio atual cobre usuário, categoria, carteira, conta, conta fixa, meta, transação, parcela, refresh token e password reset.

O fluxo principal já existe: cadastro, login, dashboard, categorias, contas, carteiras, transações, parcelamento, contas fixas e metas.

### 4.2 Problemas críticos registrados

O `PROBLEM_LEDGER.md` registra 30 problemas auditados. A Fase 0 fechou os principais riscos backend: IDOR, race condition com `@Version`, `findAll()` massivo, agregação em memória no dashboard, `cookie.secure`, `ddl-auto=update`, política de senha, rate limit, CORS de produção, logs de reset, `@Transactional`, delete sem ownership, account lockout e limpeza do mapa de rate limit.

Os riscos relevantes ainda abertos ou parciais estão concentrados em mobile, frontend e validação operacional:

- `PROB-0009`: defaults fracos permanecem em ambiente dev por decisão local;
- `PROB-0013` a `PROB-0018`: problemas estruturais no mobile;
- `PROB-0019`: CSRF no frontend web ainda aberto;
- `PROB-0022`: API deprecated do JJWT;
- `PROB-0025` a `PROB-0030`: limpeza mobile/frontend, tipagem, rota 404 e logs.

A base backend está mais estável, mas o sistema ainda não deve ir para produção pública sem fechar P0/P1 de mobile/frontend e validar PostgreSQL real.

### 4.3 Contradição documental resolvida

O arquivo `PROXIMOS_PASSOS.md` antigo coloca deploy em produção como prioridade alta. O estado atual pós-Fase 0 mostra que deploy ainda depende de validação PostgreSQL real, CI/CD, configuração operacional e fechamento das pendências abertas em mobile/frontend.

Portanto, a prioridade antiga de deploy deve ser lida como obsoleta. A prioridade atual é estabilizar clientes, validar banco real e preparar operação.

---

## 5. Decisões técnicas e estado atual

### 5.1 `ddl-auto=update`

**Estado atual:** corrigido na Fase 0. O backend usa Flyway e `ddl-auto=validate`.
**Risco residual:** validação de startup com PostgreSQL real ainda pendente.
**Próximo passo:** executar validação com Docker/PostgreSQL ou ambiente equivalente.

### 5.2 Ownership manual espalhado

**Estado atual:** principais fluxos corrigidos com métodos por `usuarioId`.
**Risco residual:** ownership continua sendo disciplina manual de service/repository.
**Próximo passo:** manter padrão obrigatório em novos PRs e ampliar testes negativos quando novos recursos surgirem.

### 5.3 Atualização direta de saldo

**Estado atual:** `@Version` adicionado nas entidades financeiras principais e escritas críticas usam `@Transactional`.
**Risco residual:** teste de concorrência real com PostgreSQL ainda pendente.
**Próximo passo:** validar conflito em banco real antes de deploy.

### 5.4 Dashboard calculado em memória

**Estado atual:** dashboard usa agregações no repositório para somatórios críticos.
**Risco residual:** performance real ainda validada só com H2/testes locais.
**Próximo passo:** executar carga mínima em PostgreSQL com volume representativo.

### 5.5 Mobile tratado como extensão simples

**Decisão atual:** mobile existe, mas com IP fixo, token volátil e erros silenciosos.
**Problema:** isso torna o app inviável fora do ambiente local.
**Decisão recomendada:** tratar mobile como cliente oficial da API, com sessão persistida, env por ambiente, paths corretos e feedback de erro.
**Critério de aceite:** app abre, restaura sessão, consome API correta e informa falhas.

---

## 6. Fase 0 — Fundação backend executada

Esta fase foi executada em 2026-07-07 com status geral `CONCLUIDA` e ressalvas. O objetivo foi transformar o MVP funcional em uma base backend mais confiável.

### PR-FOUNDATION-01 — Banco versionado com Flyway

**Status:** `PASS_COM_RESSALVA`
**Objetivo:** remover dependência de `ddl-auto=update` e criar governança real de schema.

**Escopo:**

- adicionar Flyway ao backend;
- criar migration baseline do schema atual;
- configurar `ddl-auto=validate` em produção;
- garantir que dev/test/prod sobem de forma previsível;
- documentar comando de reset e migração local;
- ajustar deploy para rodar migrations antes da aplicação.

**Critérios de aceite:**

- banco limpo sobe via migrations;
- aplicação não altera schema automaticamente em produção;
- CI ou smoke local valida startup contra PostgreSQL;
- documentação de deploy atualizada.

### PR-FOUNDATION-02 — Ownership e IDOR

**Status:** `PASS`
**Objetivo:** impedir acesso cruzado entre usuários em todos os fluxos financeiros.

**Escopo:**

- corrigir `TransacaoService` para validar ownership de `categoriaId`, `contaId`, `carteiraId` e qualquer recurso relacionado;
- corrigir `CarteiraService.deletar()` sem ownership;
- revisar todos os services para remover `findById(id)` inseguro em contexto autenticado;
- padronizar exceção para acesso negado ou recurso inexistente;
- adicionar testes negativos de acesso cruzado.

**Critérios de aceite:**

- usuário A não consegue usar recurso do usuário B em criação, edição, exclusão ou associação;
- todo repository crítico possui método por `usuarioId`;
- testes cobrem IDOR em transações, categorias, contas, carteiras, metas, parcelas e contas fixas.

### PR-FOUNDATION-03 — Integridade financeira e locking

**Status:** `PASS_COM_RESSALVA`
**Objetivo:** impedir corrupção de saldo e inconsistência em operações simultâneas.

**Escopo:**

- adicionar `@Version` em `Carteira`, `Conta`, `Meta` e outras entidades com valores acumulados;
- tratar `OptimisticLockException` com resposta clara;
- adicionar `@Transactional` em todos os métodos de escrita;
- revisar fluxos de criar, editar, cancelar, pagar e despagar;
- garantir que alteração financeira seja atômica.

**Critérios de aceite:**

- operações concorrentes não corrompem saldo;
- falha parcial faz rollback;
- response de conflito é controlado;
- testes cobrem concorrência mínima em carteira/meta/conta.

### PR-FOUNDATION-04 — Performance de consultas críticas

**Status:** `PASS_COM_RESSALVA`
**Objetivo:** remover padrões que quebram com volume real.

**Escopo:**

- substituir `findAll()` em tarefas de parcelas e contas fixas por queries filtradas;
- mover agregações do dashboard para SQL/JPQL;
- adicionar paginação obrigatória em listagens volumosas;
- revisar índices de `usuario_id`, `data`, `status`, `categoria_id`, `conta_id`, `carteira_id`;
- impedir retorno de listas sem limite.

**Critérios de aceite:**

- nenhuma rotina crítica carrega todos os registros do banco;
- dashboard não depende de stream em memória para somatórios grandes;
- endpoints de listagem possuem paginação, ordenação e filtros;
- queries principais têm índices compatíveis.

### PR-FOUNDATION-05 — Segurança de sessão, cookies, CORS e CSRF

**Status:** `PASS_COM_RESSALVA`
**Objetivo:** fechar riscos básicos de autenticação e sessão.

**Escopo:**

- `cookie.secure=true` em produção;
- remover fallback CORS para localhost em produção;
- falhar startup sem secrets obrigatórios;
- implementar CSRF para requests state-changing quando houver cookies;
- expandir rate limit em register, reset-password e validações sensíveis;
- implementar account lockout ou proteção progressiva contra brute force;
- remover logs de PII e tokens.

**Critérios de aceite:**

- produção não sobe com secret default;
- cookie de refresh é seguro;
- CORS só aceita origem configurada;
- endpoint sensível possui rate limit;
- token de reset nunca aparece em log;
- CSRF validado nos fluxos necessários.

### PR-FOUNDATION-06 — Contrato de erro e observabilidade mínima

**Status:** `PASS`
**Objetivo:** tornar falhas rastreáveis e compreensíveis.

**Escopo:**

- padronizar envelope de erro;
- incluir código de erro estável;
- incluir `requestId`;
- mapear validações para 400/422;
- mapear conflito financeiro para 409;
- mapear autenticação para 401;
- mapear acesso negado/recurso inexistente sem vazar existência indevida;
- adicionar health check real de banco no Actuator;
- preparar logs estruturados sem PII.

**Critérios de aceite:**

- frontend e mobile conseguem exibir mensagens consistentes;
- logs permitem rastrear erro por `requestId`;
- health indica indisponibilidade de banco;
- erros não vazam dados sensíveis.

---

## 7. Fase 1 — Produto financeiro essencial

Depois da fundação, o sistema deve evoluir para cobrir o fluxo financeiro completo. Esta fase transforma o MVP em produto útil no dia a dia.

### 7.1 Cadastro financeiro guiado

O primeiro acesso deve orientar o usuário a configurar o mínimo necessário:

- moeda principal;
- carteira inicial;
- contas/cartões;
- categorias padrão;
- salário/renda recorrente, se desejar;
- contas fixas principais;
- objetivo financeiro inicial.

Sem onboarding, o usuário cai em telas vazias e não sabe por onde começar.

### 7.2 Orçamento mensal

Um gestor financeiro de alto nível precisa de orçamento, não apenas registro histórico.

Funcionalidades esperadas:

- limite mensal por categoria;
- orçamento planejado versus realizado;
- alerta de categoria próxima do limite;
- comparação com meses anteriores;
- sugestão baseada no histórico.

### 7.3 Recorrência real

Contas fixas precisam funcionar como motor de recorrência.

O sistema deve diferenciar:

- lançamento recorrente previsto;
- lançamento confirmado;
- lançamento pago;
- lançamento atrasado;
- lançamento ignorado/cancelado para um mês específico.

Isso evita duplicidade e permite projeção financeira.

### 7.4 Cartão de crédito e fatura

Cartão de crédito não deve ser tratado apenas como `Conta`. Um produto de alto nível precisa modelar faturas.

Entidades futuras recomendadas:

- `CartaoCredito`;
- `FaturaCartao`;
- `LancamentoFatura`;
- fechamento;
- vencimento;
- pagamento de fatura;
- parcelamento vinculado à fatura.

Sem isso, o saldo projetado e o gasto mensal ficam imprecisos.

### 7.5 Projeção de caixa

O sistema deve mostrar o futuro financeiro:

- saldo atual;
- entradas previstas;
- saídas previstas;
- parcelas futuras;
- contas fixas futuras;
- metas programadas;
- risco de saldo negativo.

Essa é uma das principais diferenças entre um app simples e um gestor financeiro de verdade.

### 7.6 Relatórios e filtros

Relatórios devem responder perguntas reais:

- gasto por categoria;
- evolução mensal;
- maiores despesas;
- recorrências que mais pesam;
- gastos por forma de pagamento;
- comparação mês atual versus anterior;
- tendência de economia;
- metas em risco.

Todos devem ter filtro por período, categoria, conta, carteira, status e tipo.

### 7.7 Exportação e portabilidade

Por LGPD e confiança, o usuário deve poder exportar seus dados.

Mínimo recomendado:

- CSV de transações;
- CSV de categorias;
- CSV de contas/carteiras;
- relatório mensal em PDF;
- endpoint de exportação completa dos dados.

---

## 8. Fase 2 — Web e mobile de qualidade

### 8.1 Frontend web

O web deve ser tratado como produto, não só como cliente da API.

Obrigatório:

- rota 404;
- error boundary global;
- skeleton loaders;
- estados vazios úteis;
- validação de formulário;
- tipos TypeScript sem `any` em services;
- confirmação em ações destrutivas;
- feedback de sucesso/erro;
- acessibilidade básica;
- responsividade real.

### 8.2 Mobile

O mobile deve sair do estado experimental.

Obrigatório:

- API URL por ambiente;
- token persistido com Secure Store;
- refresh/session restore no cold start;
- paths alinhados com backend;
- mutations com `onError`;
- botões com ação real;
- navegação de voltar nos submenus;
- pull-to-refresh em telas principais;
- remoção de código morto;
- logs de produção removidos.

### 8.3 Consistência entre web e mobile

Web e mobile devem consumir os mesmos contratos. Regras financeiras não podem ser duplicadas no frontend de forma divergente.

Recomendação futura: criar pacote compartilhado de tipos e regras simples, ou gerar clientes a partir do OpenAPI.

---

## 9. Fase 3 — Operação, deploy e confiabilidade

Só após a Fase 0 concluída o deploy deve voltar a ser prioridade.

### 9.1 Antes do deploy

Checklist obrigatório:

- migrations ativas;
- secrets obrigatórios;
- CORS restrito;
- cookie seguro;
- CSRF implementado quando aplicável;
- health check de banco;
- logs sem PII;
- IDOR corrigido;
- locking financeiro implementado;
- dashboard sem agregação em memória;
- tasks sem `findAll()` massivo;
- testes mínimos rodando.

### 9.2 Deploy recomendado

Para MVP controlado, Railway/Vercel/Neon pode ser aceitável. Para produção real com dados sensíveis, o foco não deve ser apenas custo zero, mas previsibilidade, backup, logs, restore e segurança.

Mínimo operacional:

- banco com backup automático;
- variáveis de ambiente protegidas;
- domínio HTTPS;
- logs centralizados;
- alertas de erro;
- monitoramento de health;
- política de restore;
- versionamento de migrations;
- CI/CD com testes antes do deploy.

---

## 10. Fase 4 — Recursos avançados de produto

Depois da base sólida, as features avançadas fazem sentido.

### 10.1 Importação de dados

- CSV de bancos;
- OFX;
- importação manual assistida;
- deduplicação de transações;
- mapeamento automático de categoria.

### 10.2 Anexos e comprovantes

- upload de comprovante;
- vínculo com transação;
- storage externo;
- limite de tamanho;
- antivírus ou validação de arquivo;
- política de retenção.

### 10.3 Open Finance

Futuro possível, mas não deve entrar cedo. Exige segurança, consentimento, integração regulada, tratamento de erro e modelo de reconciliação.

### 10.4 Investimentos

Deve ser modelado separado de carteira comum.

Possíveis entidades:

- ativo;
- posição;
- movimentação;
- rentabilidade;
- preço histórico;
- classe de ativo;
- corretora.

### 10.5 Inteligência financeira

A IA só deve entrar depois que os dados estiverem confiáveis. Caso contrário, ela apenas dará recomendações em cima de números errados.

Casos úteis:

- detectar gasto anormal;
- sugerir limite por categoria;
- prever saldo no fim do mês;
- identificar recorrências esquecidas;
- sugerir economia;
- explicar variações do mês.

---

## 11. Modelo de domínio recomendado para evolução

O modelo atual pode continuar no curto prazo, mas deve ser preparado para crescer.

### 11.1 Núcleo atual

- `Usuario`;
- `Categoria`;
- `Carteira`;
- `Conta`;
- `ContaFixa`;
- `Meta`;
- `Transacao`;
- `Parcela`;
- `RefreshToken`;
- `PasswordResetToken`.

### 11.2 Núcleo recomendado futuro

Para alto nível, considerar evolução para:

- `Workspace` ou `FinanceProfile`, mesmo que inicialmente 1:1 com usuário;
- `Membership`, para futuro compartilhamento familiar;
- `Account`/`Carteira` com tipo bem definido;
- `CreditCard` separado de conta comum;
- `CreditCardInvoice`;
- `Budget`;
- `BudgetCategoryLimit`;
- `RecurringRule`;
- `FinancialEvent` ou `LedgerEntry`;
- `Attachment`;
- `AuditLog`;
- `NotificationPreference`;
- `DataExportJob`.

A decisão mais importante é evitar que tudo fique preso diretamente a `usuario_id` de forma irreversível. O sistema pode continuar single-user agora, mas preparar uma raiz `finance_profile_id` ou `workspace_id` reduz refatoração futura.

---

## 12. Padrão técnico obrigatório para novos PRs

Todo novo PR deve obedecer a este fluxo:

1. Ler documentação atual: `SYSTEM_OVERVIEW.md`, `PROBLEM_LEDGER.md`, `BACKLOG.md`, `DIAGRAMS.md` e arquivo específico da área.
2. Declarar objetivo do PR.
3. Declarar escopo permitido e escopo proibido.
4. Auditar o estado atual antes de alterar código.
5. Mapear arquivos afetados.
6. Implementar a menor unidade coesa de mudança.
7. Adicionar ou ajustar testes.
8. Atualizar documentação.
9. Registrar bug corrigido no `BUGFIX_LOG.md` quando aplicável.
10. Não marcar como PASS sem evidência de execução.

---

## 13. Definition of Done para sistema financeiro

Uma entrega só deve ser aceita quando cumprir todos os pontos aplicáveis:

- não cria risco de IDOR;
- valida ownership em todos os recursos relacionados;
- usa transação em escrita;
- não corrompe saldo sob concorrência;
- não usa `findAll()` em massa para rotina crítica;
- não calcula relatório grande em memória;
- possui validação de entrada;
- retorna erro padronizado;
- tem teste de sucesso e teste de falha;
- não loga PII, token ou senha;
- não depende de configuração local hardcoded;
- atualiza documentação;
- preserva compatibilidade web/mobile ou documenta quebra de contrato.

---

## 14. Ordem recomendada dos próximos passos

### Agora

Fechar pendências pós-Fase 0:

1. validar Flyway/startup com PostgreSQL real;
2. corrigir mobile P0: token persistente, URL por ambiente, path `/v1/dashboard/resumo`;
3. corrigir mobile P1: handlers mortos, erros silenciosos restantes, entry points zumbis;
4. decidir e implementar estratégia CSRF do frontend web ou documentar dispensa técnica completa;
5. limpar logs frontend/mobile e rota 404;
6. atualizar CI/build para backend, frontend e mobile.

### Depois

Executar Fase 1 depois que pendências P0/P1 estiverem fechadas:

1. onboarding financeiro;
2. orçamento mensal;
3. recorrência real;
4. fatura de cartão;
5. projeção de caixa;
6. relatórios e filtros;
7. exportação de dados.

### Depois disso

Executar Fase 2:

1. UX web completa;
2. mobile utilizável em ambiente real;
3. consistência de contrato entre clientes.

### Só então

Executar Fase 3:

1. deploy controlado;
2. CI/CD;
3. monitoramento;
4. backup;
5. operação assistida.

### Futuro

Executar Fase 4:

1. importação bancária;
2. anexos;
3. investimentos;
4. Open Finance;
5. inteligência financeira.

---

## 15. Recomendação final

O projeto não deve ser colocado em “OK” apenas porque o backend passa nos testes locais. O caminho correto é tratar o sistema como produto financeiro completo: backend, frontend, mobile, banco real e operação.

A fundação backend foi executada. A próxima entrega deve fechar as pendências de cliente e validação operacional. O marco de qualidade atual deve ser: **backend validado em PostgreSQL real, mobile utilizável fora da rede local, frontend sem lacunas básicas de segurança/UX e pipeline de build confiável.**

Depois que essa base estiver fechada, a análise deve ser refeita. Somente então vale decidir os próximos incrementos de produto, como orçamento, faturas, projeções, relatórios, exportação e deploy público.

**Status recomendado atual:** `NAO_APTO_PARA_DEPLOY`
**Status recomendado para desenvolvimento:** `APTO_PARA_POS_FASE_0_ESTABILIZACAO`
**Prioridade real:** fechar mobile/frontend e validação PostgreSQL antes de expandir produto.
