# Protocolo de Execução — Fundação Técnica do Gestor Financeiro

**Projeto:** Gestor Financeiro
**Data:** 2026-07-07
**Documento complementar de:** `GESTOR_FINANCEIRO_ALTO_NIVEL_PROXIMOS_PASSOS.md`
**Finalidade:** transformar a direção estratégica do projeto em um protocolo operacional para execução segura, incremental e rastreável por IA ou agente executor.
**Status atual:** protocolo histórico. A Fase 0 foi executada em 2026-07-07 e o estado vigente está em `CHECKLIST_EXECUCAO_PRS_GESTOR_FINANCEIRO.md`, `PROBLEM_LEDGER.md` e `BACKLOG.md`.

---

## 1. Papel deste documento

Este arquivo não substitui o documento `GESTOR_FINANCEIRO_ALTO_NIVEL_PROXIMOS_PASSOS.md`. Ele complementa aquele documento.

O documento principal define **o que é um gestor financeiro pessoal de alto nível**, quais riscos existem no estado atual e qual direção técnica o projeto deve seguir.

Este documento define **como a IA deve executar as mudanças no sistema**, um PR por vez, sem misturar escopos, sem antecipar features e sem marcar como concluído algo que não tenha evidência objetiva.

A execução técnica da Fase 0 seguiu este protocolo. Para novos trabalhos, usar este arquivo como referência histórica e seguir o estado atual registrado no checklist/backlog.

---

## 2. Regra central de execução

A IA deve executar **um único PR por vez**.

É proibido misturar correções de fases diferentes no mesmo PR. Cada PR deve ter objetivo claro, escopo limitado, arquivos esperados, validações obrigatórias e critério de aceite.

A ordem correta da Fase 0 é:

1. `PR-FOUNDATION-01` — Banco versionado com Flyway;
2. `PR-FOUNDATION-02` — Ownership e IDOR;
3. `PR-FOUNDATION-03` — Integridade financeira, `@Transactional` e locking;
4. `PR-FOUNDATION-04` — Performance de consultas críticas;
5. `PR-FOUNDATION-05` — Segurança de sessão, cookies, CORS, CSRF, secrets e logs;
6. `PR-FOUNDATION-06` — Contrato de erro e observabilidade mínima.

Nenhum PR posterior deve ser iniciado antes do PR anterior estar implementado, testado e documentado.

---

## 3. Ordem de autoridade documental

Antes de alterar código, a IA deve ler os documentos do projeto e respeitar esta ordem de autoridade:

1. Código real do repositório;
2. `PROBLEM_LEDGER.md`;
3. `BUGFIX_LOG.md`;
4. `SYSTEM_OVERVIEW.md`;
5. `BACKLOG.md`;
6. `DEPLOY.md`;
7. `DIAGRAMS.md`;
8. `GESTOR_FINANCEIRO_ALTO_NIVEL_PROXIMOS_PASSOS.md`;
9. Este arquivo, `PROTOCOLO_EXECUCAO_FOUNDATION_GESTOR_FINANCEIRO.md`.

Se houver conflito entre documentação e código real, a IA deve registrar a divergência e não presumir que a documentação está correta.

Se houver conflito entre o documento estratégico e este protocolo, prevalece o documento estratégico para direção de produto e este protocolo para forma de execução.

---

## 4. O que a IA deve fazer antes de qualquer alteração

Todo PR deve começar em modo de auditoria.

Antes de editar arquivos, a IA deve:

1. identificar o PR atual;
2. declarar objetivo do PR;
3. declarar escopo permitido;
4. declarar escopo proibido;
5. listar documentos lidos;
6. mapear arquivos que provavelmente serão alterados;
7. confirmar o estado atual do código;
8. identificar riscos de regressão;
9. propor plano de execução;
10. só então iniciar alteração de código.

A IA não deve assumir que um problema existe apenas porque está documentado. Ela deve confirmar no código.

A IA também não deve assumir que um problema foi resolvido apenas porque algum arquivo mudou. Ela deve buscar evidência objetiva.

---

## 5. Formato obrigatório de cada PR

Cada PR deve seguir este formato mínimo:

```text
[1] Identificação
Nome do PR:
Objetivo:
Motivo técnico:
Status inicial:

[2] Fontes lidas
Documentos:
Arquivos de código:

[3] Escopo permitido
O que pode ser alterado:

[4] Escopo proibido
O que não pode ser alterado:

[5] Auditoria inicial
Estado encontrado:
Problemas confirmados:
Problemas não confirmados:
Riscos:

[6] Plano de alteração
Arquivos previstos:
Mudanças previstas:
Testes previstos:

[7] Implementação
Alterações realizadas:
Motivo de cada alteração:

[8] Validação
Comandos executados:
Resultados:
Testes que passaram:
Testes não executados:

[9] Documentação
Arquivos atualizados:
Registro no BUGFIX_LOG:

[10] Veredito
PASS:
PASS_COM_RESSALVA:
BLOCKED:
FAIL:
```

Nenhum PR deve terminar apenas com frase genérica como “implementado com sucesso”. A conclusão precisa ter evidência.

---

## 6. Regras gerais de segurança e qualidade

Durante qualquer PR da Fase 0, a IA deve respeitar estas regras:

- não criar feature nova de produto;
- não alterar visual sem necessidade técnica;
- não criar rotas novas sem relação direta com o PR;
- não remover validações existentes sem justificar;
- não reduzir segurança para fazer teste passar;
- não mascarar erro real com `try/catch` genérico;
- não retornar dados de outro usuário;
- não acessar entidade financeira por `id` isolado em fluxo autenticado;
- não usar `findAll()` em rotina crítica;
- não calcular relatório grande em memória;
- não logar senha, token, e-mail sensível, payload financeiro completo ou cookie;
- não deixar secret default em ambiente produtivo;
- não marcar teste como PASS se ele não foi executado.

---

## 7. Critério global de aceite da Fase 0

A Fase 0 só pode ser considerada concluída quando todos os pontos abaixo forem verdadeiros:

- banco versionado por migrations;
- produção sem `ddl-auto=update`;
- todo recurso financeiro protegido por ownership;
- operações financeiras críticas transacionais;
- entidades com saldo ou valor acumulado protegidas contra concorrência;
- dashboard sem agregação pesada em memória;
- rotinas agendadas sem `findAll()` massivo;
- endpoints sensíveis com rate limit adequado;
- CORS restrito por ambiente;
- cookie seguro em produção;
- CSRF resolvido quando houver cookie em mutação;
- secrets obrigatórios sem fallback inseguro;
- logs sem PII e sem tokens;
- contrato de erro padronizado;
- `requestId` rastreável;
- health check real de banco;
- documentação atualizada;
- `BUGFIX_LOG.md` atualizado para cada correção relevante.

---

## 8. PR-FOUNDATION-01 — Banco versionado com Flyway

### Objetivo

Remover a dependência de `ddl-auto=update` e estabelecer governança real de schema.

### Motivo técnico

Sistema financeiro não pode depender de alteração automática de schema em produção. O banco precisa ser previsível, versionado, auditável e reprodutível em ambiente limpo.

### Escopo permitido

- adicionar dependência do Flyway;
- criar baseline do schema atual;
- criar pasta de migrations;
- configurar perfis de ambiente;
- mudar produção para `ddl-auto=validate` ou `none`;
- ajustar documentação de deploy;
- criar ou ajustar smoke de startup com PostgreSQL;
- documentar reset local de banco.

### Escopo proibido

- alterar regra de negócio financeira;
- corrigir IDOR;
- implementar locking;
- refatorar dashboard;
- alterar frontend;
- alterar mobile;
- criar novas entidades de produto sem necessidade direta para migration.

### Evidência obrigatória

- aplicação sobe com banco limpo usando migrations;
- aplicação falha se schema divergir em modo validate;
- documentação explica como rodar migrations;
- `ddl-auto=update` removido de produção.

---

## 9. PR-FOUNDATION-02 — Ownership e IDOR

### Objetivo

Garantir que um usuário nunca consiga acessar, associar, alterar ou excluir recurso financeiro pertencente a outro usuário.

### Motivo técnico

Em sistema financeiro, IDOR é falha crítica. Todo ID enviado pelo frontend ou mobile deve ser tratado como manipulável.

### Escopo permitido

- revisar services autenticados;
- substituir `findById(id)` inseguro por busca com `usuarioId` ou raiz de posse;
- corrigir associação de `categoriaId`, `contaId`, `carteiraId`, `metaId` e outros recursos relacionados;
- corrigir delete sem ownership;
- adicionar repositories específicos por usuário;
- adicionar testes negativos de acesso cruzado.

### Escopo proibido

- mudar modelo de autenticação;
- implementar workspace/família;
- alterar layout;
- refatorar dashboard;
- implementar locking financeiro, exceto se uma pequena anotação for inevitável e justificada.

### Evidência obrigatória

- usuário A não usa categoria, conta, carteira, meta ou transação do usuário B;
- delete não remove recurso de outro usuário;
- associação cruzada retorna erro controlado;
- testes de IDOR existem e passam.

---

## 10. PR-FOUNDATION-03 — Integridade financeira, transação e locking

### Objetivo

Impedir corrupção de saldo, inconsistência parcial e gravação incompleta em operações financeiras.

### Motivo técnico

Criar, editar, pagar, cancelar ou excluir registros financeiros pode afetar saldos e estados derivados. Essas operações precisam ser atômicas e protegidas contra concorrência.

### Escopo permitido

- adicionar `@Transactional` em métodos de escrita;
- adicionar `@Version` em entidades com saldo, valor acumulado ou estado financeiro sensível;
- tratar exceção de locking otimista;
- revisar criar, editar, excluir, pagar, despagar e cancelar;
- adicionar testes de rollback;
- adicionar teste mínimo de concorrência.

### Escopo proibido

- reescrever todo o domínio financeiro;
- criar ledger completo neste PR;
- implementar orçamento;
- implementar fatura de cartão;
- alterar contratos sem necessidade.

### Evidência obrigatória

- falha no meio da operação gera rollback;
- concorrência não corrompe saldo;
- conflito retorna 409 ou erro equivalente padronizado;
- testes cobrem pelo menos um fluxo crítico de saldo.

---

## 11. PR-FOUNDATION-04 — Performance de consultas críticas

### Objetivo

Remover padrões que não escalam com histórico financeiro real.

### Motivo técnico

Um usuário pode acumular milhares de transações, parcelas, contas fixas e eventos. Dashboard e rotinas não podem carregar tudo em memória.

### Escopo permitido

- substituir `findAll()` em rotinas por queries filtradas;
- mover agregações para SQL/JPQL;
- adicionar paginação em listagens volumosas;
- criar índices por migration;
- revisar filtros por período, status, usuário, categoria, conta e carteira;
- medir ou justificar queries principais.

### Escopo proibido

- redesenhar visual do dashboard;
- adicionar relatório novo;
- implementar IA financeira;
- criar exportação;
- alterar mobile.

### Evidência obrigatória

- nenhuma rotina crítica busca todos os registros do banco;
- dashboard usa agregação no banco;
- listagens volumosas têm paginação ou limite;
- índices necessários estão em migration.

---

## 12. PR-FOUNDATION-05 — Segurança de sessão, cookies, CORS, CSRF, secrets e logs

### Objetivo

Fechar riscos básicos de autenticação, sessão, configuração e exposição de dados sensíveis.

### Motivo técnico

Dados financeiros exigem proteção forte de sessão, configuração previsível e ausência de vazamento em log.

### Escopo permitido

- configurar `cookie.secure=true` em produção;
- restringir CORS por ambiente;
- remover fallback inseguro para localhost em produção;
- falhar startup sem secrets obrigatórios;
- implementar ou ajustar CSRF quando cookies forem usados em mutações;
- ampliar rate limit em endpoints sensíveis;
- remover logs de token, senha, e-mail sensível, cookie e payload financeiro completo.

### Escopo proibido

- trocar stack de autenticação inteira;
- implementar MFA;
- alterar domínio financeiro;
- alterar telas sem necessidade;
- mover token para outro modelo sem decisão explícita.

### Evidência obrigatória

- produção não sobe com secret default;
- CORS aceita apenas origem configurada;
- cookie de refresh é seguro em produção;
- endpoint sensível tem rate limit;
- logs não exibem token ou PII sensível;
- CSRF está documentado como implementado ou conscientemente dispensado com justificativa técnica válida.

---

## 13. PR-FOUNDATION-06 — Contrato de erro e observabilidade mínima

### Objetivo

Padronizar falhas para que backend, frontend e mobile tratem erros de forma previsível e rastreável.

### Motivo técnico

Sem envelope de erro estável, a UX fica inconsistente e a análise de falhas em produção se torna difícil.

### Escopo permitido

- padronizar envelope de erro;
- adicionar código de erro estável;
- adicionar `requestId`;
- mapear validação para 400/422;
- mapear conflito financeiro para 409;
- mapear autenticação para 401;
- mapear acesso negado ou recurso inexistente sem vazar existência indevida;
- adicionar health check real de banco;
- estruturar logs sem PII.

### Escopo proibido

- alterar todos os fluxos de frontend de uma vez;
- redesenhar mensagens visuais;
- criar observabilidade avançada complexa;
- instalar stack pesada de monitoramento sem necessidade.

### Evidência obrigatória

- erro possui formato estável;
- resposta contém `requestId`;
- frontend/mobile conseguem exibir mensagem consistente;
- health check falha quando banco está indisponível;
- logs permitem rastrear erro por `requestId` sem expor dados sensíveis.

---

## 14. O que não fazer antes da Fase 0 terminar

Antes da Fase 0 estar concluída, é proibido usar a IA para implementar:

- orçamento mensal;
- fatura de cartão de crédito;
- projeção de caixa avançada;
- relatórios avançados;
- exportação CSV/PDF;
- anexos e comprovantes;
- importação OFX/CSV;
- Open Finance;
- investimentos;
- inteligência artificial financeira;
- conta familiar ou casal;
- permissões avançadas;
- deploy público definitivo;
- redesign visual amplo;
- mobile completo como prioridade principal.

Esses itens são importantes, mas dependem de uma base confiável. Implementá-los antes da Fase 0 aumenta retrabalho e risco de dados incorretos.

---

## 15. Padrão de prompt para executar cada PR

Use o modelo abaixo para abrir qualquer PR da Fase 0 com a IA executora.

```text
Você é o executor técnico do projeto Gestor Financeiro.

PR atual: <NOME_DO_PR>

Regra central:
Execute somente este PR. Não antecipe fases. Não implemente features fora do escopo. Não faça commit automático.

Fontes obrigatórias de leitura antes de alterar código:
- SYSTEM_OVERVIEW.md
- PROBLEM_LEDGER.md
- BACKLOG.md
- BUGFIX_LOG.md
- DEPLOY.md
- GESTOR_FINANCEIRO_ALTO_NIVEL_PROXIMOS_PASSOS.md
- PROTOCOLO_EXECUCAO_FOUNDATION_GESTOR_FINANCEIRO.md
- arquivos de código diretamente relacionados ao PR

Ordem de trabalho:
1. Identifique o estado atual do código.
2. Confirme se o problema documentado existe.
3. Liste escopo permitido e proibido.
4. Mapeie arquivos que serão alterados.
5. Pare e apresente o plano antes de alterar, se estiver em modo auditoria.
6. Implemente apenas o necessário para este PR.
7. Adicione ou ajuste testes.
8. Atualize documentação.
9. Atualize BUGFIX_LOG.md quando corrigir problema registrado.
10. Informe comandos executados e resultados.

Critério de conclusão:
Não marque como PASS sem evidência. Se teste não foi executado, declare como NÃO EXECUTADO. Se houver bloqueio, marque como BLOCKED e explique a causa.
```

---

## 16. Definition of Done por PR

Um PR só pode ser aceito quando cumprir todos os itens aplicáveis:

- problema confirmado antes da correção;
- escopo respeitado;
- código alterado apenas onde necessário;
- testes adicionados ou justificativa objetiva quando não aplicável;
- documentação atualizada;
- `BUGFIX_LOG.md` atualizado quando houver bug corrigido;
- comandos de validação executados;
- resultado de cada comando informado;
- nenhuma feature fora do PR foi implementada;
- nenhuma segurança foi reduzida;
- nenhuma dívida crítica nova foi introduzida.

---

## 17. Status recomendado após criação deste protocolo

Com este arquivo complementar, o projeto passa a ter dois documentos centrais para a próxima etapa:

1. `GESTOR_FINANCEIRO_ALTO_NIVEL_PROXIMOS_PASSOS.md` — direção técnica e visão de produto de alto nível;
2. `PROTOCOLO_EXECUCAO_FOUNDATION_GESTOR_FINANCEIRO.md` — protocolo operacional para a IA executar a Fase 0 com segurança.

A ação recomendada originalmente era iniciar o primeiro PR da Fase 0:

```text
PR-FOUNDATION-01 — Banco versionado com Flyway
```

Status recomendado original:

```text
NAO_APTO_PARA_DEPLOY
OBSOLETO_APTO_PARA_FASE_0_FOUNDATION
APTO_PARA_EXECUCAO_CONTROLADA_POR_PR
```

Status vigente após execução da Fase 0:

```text
NAO_APTO_PARA_DEPLOY
APTO_PARA_POS_FASE_0_ESTABILIZACAO
PENDENTE_VALIDACAO_POSTGRESQL_REAL
PENDENTE_MOBILE_FRONTEND_P0_P1
```
