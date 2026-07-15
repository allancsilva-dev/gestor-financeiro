# Meta do Nexos Finanças — 15/07/2026

## Objetivo

Transformar o Gestor Financeiro em um produto de finanças pessoais simples, confiável e progressivamente automatizado, usando Oinc, Piere e Pró Assessor como referências de experiência e capacidade.

O produto deve permitir que uma pessoa sem conhecimento financeiro:

- entenda saldo, compromissos e situação do mês em poucos segundos;
- registre ou importe movimentações com mínimo esforço;
- confie que saldo, fatura, orçamento, metas e relatórios contam a mesma história;
- receba orientação útil baseada nos próprios dados;
- automatize tarefas repetitivas sem perder controle.

## Veredito da auditoria

O sistema atual é um gestor financeiro manual avançado. Possui boa base técnica, muitos módulos e regras relevantes, mas ainda não entrega experiência comparável às referências.

Problema principal não é falta de telas. É falta de uma verdade financeira única e de um modelo de produto simples.

### Avaliação aproximada

- Base técnica: 7/10
- Coerência financeira: 5/10
- UX mobile: 11/20
- Paridade com referências: 3/10
- Prontidão para lançamento público: não aprovado

## Referências de produto

### Oinc

Referência para:

- agregação de contas e cartões via Open Finance;
- categorização automática;
- orçamento mensal;
- identificação de assinaturas;
- boletos;
- poupança automática e transferências inteligentes.

### Piere

Referência para:

- visão de patrimônio líquido;
- contas conectadas;
- orçamento com rollover;
- regras de transação;
- planejamento de dívidas;
- orientação e automação por IA;
- relatórios e tendências acionáveis.

### Pró Assessor

Referência para:

- lançamento por texto ou áudio no WhatsApp;
- categorização por IA;
- importação e revisão de fatura;
- perguntas em linguagem natural;
- alertas e resumos;
- gestão pessoal e familiar.

## Diferença entre referências e sistema atual

| Capacidade | Referências | Sistema atual |
|---|---|---|
| Captura de dados | Open Finance, banco, WhatsApp, áudio | Digitação manual e CSV |
| Categorização | Automática, IA e regras | Escolha manual por lançamento |
| Contas | Modelo conectado e compreensível | `Conta` e `Carteira` sobrepostas |
| Orientação | IA e ações personalizadas | Regras fixas apresentadas como insights |
| Planejamento | Orçamento, patrimônio, dívida e metas | Módulos separados com divergências |
| Automação | Pix, transferências, poupança e alertas | Recorrências internas |
| Família | Compartilhamento e visão conjunta | Usuário individual |

## Princípio central

O sistema precisa ter uma única verdade financeira.

Toda movimentação deve explicar claramente:

1. onde o dinheiro está;
2. de onde veio ou para onde foi;
3. quando afeta caixa, fatura, orçamento e relatório;
4. se representa gasto, transferência, reserva, investimento ou ajuste;
5. qual saldo resultou da operação.

Dashboard, extrato, fatura, orçamento, meta, projeção e relatório devem derivar dessa mesma verdade.

## Achados bloqueantes

### P0-1 — Onboarding cadastra renda como saída

`ContaFixa` assume `SAIDA`. Onboarding cria a renda sem definir `ENTRADA`.

Impactos:

- salário pode aparecer como despesa recorrente;
- projeção recebe sinal financeiro errado;
- experiência inicial ensina modelo incorreto;
- problema existe nos fluxos web e mobile.

Além disso, onboarding associa renda à primeira categoria sugerida, atualmente `Alimentação`.

Critério de aceite futuro:

- renda criada como `ENTRADA`;
- categoria de renda coerente, própria ou opcional;
- testes de onboarding mobile, web e backend;
- projeção comprova crédito mensal correto.

### P0-2 — Exclusão LGPD não contempla execuções de recorrência

`UsuarioExclusaoService` não remove `ExecucaoRecorrencia`. A tabela possui FKs restritivas para usuário, conta fixa e transação.

Impactos:

- usuário que utilizou recorrências pode não conseguir excluir conta;
- direito de eliminação fica incompleto;
- operação pode terminar em conflito de integridade.

Critério de aceite futuro:

- excluir execuções antes das entidades referenciadas;
- teste PostgreSQL com recorrência realizada, pulada e falha de saldo;
- confirmar remoção dos dados e arquivos do titular;
- preservar dados de outros usuários.

### P0-3 — Meta concluída ou excluída pode ocultar dinheiro reservado

Ao atingir 100%, meta recebe `ativa=false`. Listagem retorna somente metas ativas. Exclusão também apenas desativa meta, mesmo com valor reservado.

Impactos:

- meta concluída desaparece;
- valor já debitado da carteira deixa de ser facilmente visível;
- usuário perde caminho normal para resgatar ou destinar dinheiro;
- saldo líquido e patrimônio ficam incompletos.

Critério de aceite futuro:

- metas concluídas continuam acessíveis em histórico;
- conclusão não equivale a exclusão;
- exclusão com valor reservado exige destinação ou resgate;
- patrimônio mostra valores reservados;
- testes cobrem conclusão, resgate, arquivamento e exclusão.

### P0-4 — Onboarding web não é atômico e diverge do mobile

O mobile envia toda a configuração ao endpoint transacional de finalização. O web cria carteira, conta,
categorias, renda e meta em chamadas independentes e apenas depois marca o onboarding como concluído.

Impactos:

- falha intermediária deixa cadastro financeiro parcial;
- nova tentativa pode encontrar entidades já criadas ou duplicar etapas;
- web e mobile aplicam contratos e regras diferentes para a mesma jornada;
- correções no endpoint canônico podem não corrigir o fluxo web;
- renda criada diretamente pelo web também herda `SAIDA` por omissão.

Critério de aceite futuro:

- web e mobile usam um único caso de uso transacional no backend;
- operação inteira confirma ou reverte em conjunto;
- reenvio possui comportamento idempotente definido;
- teste cobre falha em cada etapa sem deixar dados parciais;
- contrato de onboarding não é reproduzido nos clientes.

## Problemas estruturais

### P1-1 — `Conta` e `Carteira` representam conceitos sobrepostos

Ambas possuem banco, tipo e campos de saldo. `Conta.saldoAtual` praticamente não participa das regras. No mobile, `Carteira` é exibida ao usuário como “Conta”.

Consequências:

- modelo difícil para usuário e desenvolvedor;
- transação pode atualizar relatório sem atualizar saldo;
- contas de débito, dinheiro e poupança ficam parcialmente mortas;
- regras precisam tratar várias combinações opcionais.

Direção recomendada:

- uma entidade principal de conta financeira;
- subtipos de caixa e dívida: dinheiro, conta corrente, poupança, conta de pagamento, caixa de corretora,
  cartão de crédito e empréstimo;
- todas as contas com ledger;
- cartão com fatura e limite como comportamento específico;
- investimentos representados por conta de custódia e posições, sem compor saldo disponível;
- eliminar ou migrar campos sem fonte de verdade.

### P1-2 — Transação sem carteira não movimenta saldo

Transação ativa pode aparecer em categorias e relatórios sem gerar movimento financeiro.

Consequência: números podem estar individualmente corretos segundo regras diferentes, mas discordar na tela.

Direção recomendada:

- exigir origem/destino financeiro para operações que afetam caixa;
- separar explicitamente lançamento informativo de movimento real;
- indicar claramente dados incompletos/importados pendentes de conciliação.

### P1-3 — Parcelamento possui múltiplas fontes de verdade

Existem caminhos diferentes para:

- compra de cartão;
- parcela fora do cartão;
- dashboard mensal;
- relatório por período;
- cronograma;
- projeção;
- fatura.

Consultas de dashboard e relatório usam data da transação e, dependendo do caso, valor total ou uma parcela. Fatura usa `FaturaLancamento`.

Direção recomendada:

- definir política contábil explícita de caixa e competência;
- usar cronograma financeiro canônico;
- relatórios permitirem visão por compra, competência e pagamento;
- nunca inferir parcelamento por campos opcionais divergentes.

### P1-4 — Conceitos de saldo não são suficientemente explicados

Home apresenta:

- saldo total das carteiras;
- receitas do mês;
- despesas do mês;
- saldo do mês;
- projeção de caixa.

Sem hierarquia conceitual forte, usuário pode confundir dinheiro disponível com resultado mensal.

Definições necessárias:

- `Saldo disponível`: dinheiro líquido e imediatamente utilizável nas contas de caixa;
- `Reservado`: dinheiro existente, mas alocado a metas ou envelopes;
- `Resultado do mês`: receitas menos despesas por política definida;
- `Comprometido`: contas e faturas futuras dentro de horizonte definido;
- `Disponível para gastar`: saldo disponível menos reservado e comprometido;
- `Investido`: valor de mercado das posições, com data da cotação e liquidez explícitas;
- `Dívidas`: cartões, empréstimos e financiamentos;
- `Patrimônio líquido`: caixa, investimentos e outros ativos menos dívidas.

### P1-5 — Timezone inconsistente

Recorrências usam `America/Sao_Paulo`. Dashboard, faturas, orçamento, relatórios e insights usam timezone padrão do servidor em vários pontos.

Impactos:

- mudança antecipada de dia ou mês em servidor UTC;
- status e totais diferentes durante virada do período;
- inconsistência entre scheduler e APIs.

Direção recomendada:

- `Clock` injetável;
- timezone de negócio explícito;
- timestamps persistidos em UTC;
- datas financeiras calculadas no timezone do usuário ou do produto;
- testes de virada de dia, mês e ano.

### P1-6 — Anexos não possuem persistência operacional segura

Anexos são armazenados no filesystem local da API. Os arquivos de compose não montam volume de uploads para o serviço.

Impactos:

- comprovantes podem sumir após recriação do container;
- backup PostgreSQL não inclui anexos;
- exclusão e recuperação dependem do mesmo filesystem efêmero.

Direção recomendada:

- object storage privado;
- criptografia em repouso;
- URLs assinadas e curtas;
- antivírus/validação adicional quando necessário;
- política de retenção, backup e exclusão testada.

### P1-7 — Estratégias de backup divergem

Um compose exige backup criptografado. Outro grava `pg_dump` diretamente no volume.

Direção recomendada:

- uma política canônica de backup;
- criptografia obrigatória;
- retenção documentada;
- restore drill automatizado;
- evidência vinculada ao release.

## UX mobile

### Lançamento rápido não cumpre meta declarada

Produto promete lançamento em menos de 10 segundos. Formulário atual exige:

- valor;
- descrição;
- data completa digitada;
- categoria manual;
- origem ou cartão;
- parcelamento quando aplicável.

Data começa vazia. Categoria não é sugerida por descrição ou histórico.

Direção recomendada:

- hoje como data padrão;
- valor como primeiro foco;
- lembrar última conta;
- sugerir categoria pelo histórico;
- ações rápidas para gasto recorrente;
- revisão posterior para dados secundários;
- captura por texto, áudio ou importação como evolução.

### Home possui carga cognitiva alta

Home repete receitas, despesas e saldo em hero, KPIs, insights e projeção. Usa muitos cards e animações de entrada, contrariando anti-referência do próprio `PRODUCT.md`.

Direção recomendada:

1. saldo disponível;
2. compromissos próximos;
3. ação principal;
4. últimas movimentações;
5. uma recomendação acionável;
6. detalhes sob demanda.

### Acessibilidade

Pontos positivos:

- tema claro/escuro;
- Reduce Motion;
- skeletons;
- estados vazios e retry;
- semântica verde/vermelho;
- componentes reutilizáveis;
- alvos principais de 44pt.

Pendências:

- placeholder claro: contraste aproximado 3,16:1;
- placeholder escuro: contraste aproximado 3,26:1;
- WCAG AA exige 4,5:1 para texto normal e placeholder;
- controles locais ainda não usam sempre componentes canônicos;
- validação real com VoiceOver, TalkBack e fonte ampliada pendente.

### Nota técnica da auditoria UI

- Acessibilidade: 2/4
- Performance: 3/4
- Responsividade: 2/4
- Theming: 3/4
- Anti-padrões: 1/4
- Total: 11/20 — aceitável, com trabalho significativo pendente

## Insights e orientação

`InsightsService` não utiliza IA. Usa médias, percentuais e limites fixos.

Isso não é errado, mas nome e apresentação criam expectativa maior que capacidade.

Direção recomendada em fases:

1. regras determinísticas transparentes;
2. explicação de como cada insight foi calculado;
3. recomendações ligadas a ação real;
4. regras personalizáveis;
5. IA somente com dados consistentes, rastreabilidade e limites claros.

## Testes e release

### Cobertura encontrada

- backend: aproximadamente 164 métodos `@Test`;
- web: 15 casos de teste;
- mobile: 11 casos de teste em 4 suites;
- Maestro: login, política de privacidade e navegação de recuperação.

Validação executada durante esta auditoria:

- backend: 164 testes, 0 falhas;
- migrations: PostgreSQL real, `PASS`;
- web: 15 testes, lint e build, `PASS`;
- mobile: 11 testes, typecheck e lint, `PASS`.

Suite verde não invalida os P0: os cenários que demonstrariam esses erros não estão cobertos.

### Lacunas

- onboarding financeiro;
- transação e saldo;
- cartão e fatura;
- recorrências;
- metas;
- orçamento;
- importação e conciliação;
- exclusão LGPD com dados completos;
- timezone;
- anexos em ambiente implantado.

Testes de clientes estão concentrados em autenticação, validação e infraestrutura. Jornadas financeiras principais ainda não possuem prova E2E suficiente.

## Pontos fortes que devem ser preservados

- ledger atômico;
- saldo materializado com reconciliação;
- locks e idempotência em fluxos importantes;
- regras de cartão e fatura acima de MVP comum;
- Flyway e constraints PostgreSQL;
- ownership contra IDOR;
- refresh token rotacionado e armazenado com hash;
- contratos separados de sessão web e mobile;
- importação e exportação já iniciadas;
- exclusão do titular já possui base;
- design system mobile reutilizável;
- estados de loading, erro e vazio nos fluxos principais.

## Decisão de posicionamento necessária

Antes de adicionar features grandes, escolher uma direção primária.

### Opção A — Organizador conectado, inspirado em Oinc

Foco:

- Open Finance;
- contas e cartões conectados;
- categorização automática;
- orçamento;
- assinaturas;
- metas e poupança automática.

### Opção B — Planejador inteligente, inspirado em Piere

Foco:

- patrimônio;
- dívidas;
- orçamento com rollover;
- regras;
- planejamento;
- automações e orientação personalizada.

### Opção C — Assessor conversacional, inspirado em Pró Assessor

Foco:

- WhatsApp;
- texto e áudio;
- importação inteligente de fatura;
- perguntas em linguagem natural;
- alertas e resumos;
- uso pessoal e familiar.

### Opção D — Estratégia progressiva recomendada

1. consolidar núcleo manual confiável;
2. simplificar modelo de contas;
3. automatizar captura e categorização;
4. adicionar patrimônio e planejamento;
5. adicionar canal conversacional;
6. integrar Open Finance quando segurança, operação e modelo estiverem maduros.

## Posicionamento consolidado recomendado

O objetivo não é copiar Oinc, Piere ou Pró Assessor. As referências devem fornecer padrões úteis, não uma
lista cumulativa de funcionalidades.

Posicionamento recomendado para o Nexos Finanças:

> Assistente financeiro pessoal com verdade financeira auditável, visão de dinheiro disponível e patrimônio,
> captura inteligente por arquivo ou conversa e orientação explicável.

Combinação coerente:

- núcleo financeiro manual e importado confiável;
- CSV/OFX como integrações válidas, não como remendo temporário;
- captura conversacional como diferencial próximo;
- orçamento, patrimônio, dívidas e metas como capacidade de planejamento;
- Open Finance como conector futuro opcional, não como dependência para validar o produto;
- ledger e reconciliação como base de todas as superfícies.

## Modelo financeiro alvo

### Conta financeira não significa saldo único

Unificar `Conta` e `Carteira` significa remover duas representações sobrepostas do mesmo conceito. Não
significa somar dinheiro, limite, dívida e investimento em um número sem semântica.

Uma conta financeira deve declarar:

- natureza: ativo ou passivo;
- tipo: dinheiro, corrente, poupança, pagamento, caixa de corretora, cartão ou empréstimo;
- moeda;
- instituição e identificação opcional;
- liquidez: imediata, D+1, D+2, com carência ou bloqueada;
- origem dos dados: manual, CSV, OFX, integração ou ajuste;
- estado de conciliação;
- saldo derivado do ledger, nunca de regras paralelas.

Cartão é conta de passivo com comportamento específico de limite, fechamento, vencimento, fatura e
pagamento. Limite disponível não é patrimônio nem dinheiro em caixa.

### Investimentos e liquidez

Dinheiro investido não deve compor carteira ou saldo disponível. Deve aparecer separadamente em patrimônio.

Estrutura recomendada:

- conta de custódia ou corretora;
- posições por ativo;
- quantidade, custo total e preço médio;
- cotação atual, fonte e instante da cotação;
- valor de mercado;
- liquidez e carência;
- movimentações de compra, venda, dividendo, rendimento, bonificação, taxa e imposto.

Compra de investimento representa conversão patrimonial:

```text
Conta corrente:            -R$ 1.000
Custo da posição:          +R$ 1.000
Dinheiro disponível:       -R$ 1.000
Investimentos:             +R$ 1.000
Patrimônio líquido inicial: sem mudança relevante
```

Compra de investimento não é despesa de consumo. Venda reduz posição e credita uma conta de caixa. Dividendo
credita caixa e é classificado como receita de investimento sem alterar quantidade da posição.

O vínculo opcional atual entre movimentação de investimento e `Carteira` permite alterar posição sem o caixa
correspondente. Direção futura:

- operação financeira real exige conta de origem ou destino;
- posição cadastrada sem histórico deve ser marcada como snapshot externo;
- dado incompleto permanece pendente de conciliação;
- importação nunca inventa movimento de caixa ausente;
- taxa, imposto, lucro e prejuízo possuem tratamento explícito.

### Métricas oficiais do produto

- `Disponível agora`: caixa líquido imediatamente utilizável;
- `Reservado`: parte do caixa alocada a metas ou envelopes;
- `Comprometido`: contas e faturas próximas dentro de horizonte informado;
- `Disponível para gastar`: disponível agora menos reservado e comprometido;
- `Investido`: valor de mercado das posições, separado por liquidez;
- `Dívidas`: obrigações de cartão, empréstimo e financiamento;
- `Resultado do mês`: receitas menos despesas, excluindo transferências internas e compra de investimento;
- `Patrimônio líquido`: ativos menos passivos;
- `Variação patrimonial`: mudança de patrimônio por aportes, retiradas, rendimentos e preço de mercado.

Cada número precisa informar data de referência, política de cálculo e caminho para os lançamentos de origem.

### Metas: reserva virtual e cofrinho real

Existem dois comportamentos válidos e eles não devem ser confundidos.

Reserva virtual:

```text
Saldo bancário:            R$ 5.000
Reservado para viagem:     R$ 1.500
Disponível para gastar:    R$ 3.500
```

O dinheiro continua na mesma conta. A meta cria uma alocação, não um saldo paralelo.

Cofrinho real:

```text
Conta corrente:           -R$ 1.500
Conta poupança/cofrinho:  +R$ 1.500
Patrimônio líquido:        sem mudança
```

Nesse caso existe transferência entre contas. Meta concluída continua acessível; arquivamento não movimenta
dinheiro; exclusão com saldo exige resgate, transferência ou destinação explícita.

### Transações e lançamentos balanceados

Evolução recomendada do ledger: uma transação financeira contém lançamentos que explicam origem e destino.

Exemplo de transferência:

```text
Conta corrente: -R$ 500
Poupança:       +R$ 500
Resultado mês:   R$ 0
Patrimônio:      R$ 0 de mudança
```

Invariantes:

- toda operação que afeta dinheiro possui origem ou destino;
- transferências internas não são receita nem despesa;
- compra de investimento não é despesa de consumo;
- pagamento de cartão reduz caixa e passivo sem contar a compra novamente;
- categorias são dimensão analítica, não fonte independente de saldo;
- operação importada incompleta não é contabilizada como conciliada;
- estorno referencia operação original;
- idempotência impede repetição por retry, webhook ou importação duplicada.

## Rollover: conceitos diferentes

### Rollover de orçamento — ainda não existe

Rollover de orçamento carrega sobra ou excesso de uma categoria para o mês seguinte.

```text
Julho: limite R$ 800, gasto R$ 650, sobra R$ 150
Agosto: base R$ 800 + rollover R$ 150 = R$ 950 disponível
```

Excesso também pode ser carregado:

```text
Julho: limite R$ 800, gasto R$ 900, excesso R$ 100
Agosto: base R$ 800 - rollover R$ 100 = R$ 700 disponível
```

Políticas por categoria:

- `NONE`: não carrega;
- `SURPLUS_ONLY`: carrega apenas sobra;
- `DEFICIT_ONLY`: carrega apenas excesso;
- `BOTH`: carrega sobra e excesso.

Implementação futura precisa armazenar base, `carryIn`, gasto, ajuste e resultado de fechamento. Fechamento deve
ser idempotente, auditável e versionar a regra aplicada. Alterar regra futura não pode reescrever silenciosamente
meses fechados.

### Rollover de fatura — já existe

O sistema possui rollover de cartão: crédito ou saldo devedor de uma fatura é levado à próxima fatura. Isso é
correto para dívida de cartão, mas não substitui rollover de orçamento.

## Estratégia de Open Finance

O compartilhamento é gratuito para o consumidor. Acesso produtivo direto por um aplicativo exige participação
regulada: somente instituições autorizadas e supervisionadas pelo Banco Central participam diretamente, com
requisitos de consentimento, segurança, diretório, certificação, operação e responsabilidade.

Caminhos reais:

1. tornar-se instituição autorizada — fora da realidade atual do produto;
2. contratar agregador ou parceiro autorizado — normalmente envolve custo comercial e operacional;
3. usar sandbox para desenvolvimento — sem dados bancários reais de clientes;
4. manter importação manual estruturada e preparar interface para conector futuro.

Não existe API pública gratuita legítima que forneça, em produção, dados reais de qualquer banco brasileiro a
um aplicativo não participante. Decisão recomendada: não bloquear o produto por Open Finance e não simular
integração insegura por captura de senha, scraping ou automação de internet banking.

Referências regulatórias:

- [Banco Central — instituições participantes](https://www.bcb.gov.br/estabilidadefinanceira/openfinance_participantes)
- [Banco Central — perguntas frequentes sobre Open Finance](https://www.bcb.gov.br/meubc/faqs/s/open-finance)

Arquitetura deve prever `FinancialDataConnector` para que CSV, OFX, WhatsApp e futuro parceiro Open Finance
alimentem o mesmo pipeline canônico.

## Importação CSV/OFX como produto

CSV não deve ser tratado como solução inferior. Uma importação bem construída resolve captura com controle,
baixo custo e ampla compatibilidade.

Pipeline recomendado:

```text
Arquivo bruto
  -> detecção de formato/banco
  -> mapeamento de colunas
  -> normalização
  -> identificação de conta
  -> deduplicação
  -> sugestão de categoria
  -> prévia e correção
  -> confirmação
  -> conciliação
  -> ledger
```

Requisitos:

- CSV e OFX;
- templates versionados por instituição;
- assistente de mapeamento para formatos desconhecidos;
- hash do arquivo e chave externa por linha;
- deduplicação por identidade e por heurística explicável;
- prévia antes de gravar;
- importação parcial com relatório de erros;
- estado `PENDING_REVIEW` para ambiguidades;
- aprendizado de categoria baseado nas correções do usuário;
- histórico, reversão auditável e reprocessamento seguro;
- reconciliação entre saldo informado e movimentos importados.

Open Finance futuro troca a fonte, não o núcleo do processamento.

```text
CSV ------------------+
OFX ------------------+
WhatsApp -------------+-> normalização -> rascunho -> revisão -> ledger
Open Finance futuro --+
Entrada manual -------+
```

## WhatsApp, texto, áudio e IA

Captura conversacional é diferencial recomendado após a verdade financeira estar estabilizada.

Fluxo:

```text
Mensagem ou áudio
  -> webhook autenticado
  -> transcrição quando necessário
  -> extração estruturada
  -> regras e histórico do usuário
  -> rascunho de transação
  -> confirmação ou correção
  -> lançamento idempotente no ledger
```

Exemplo de entrada:

```text
Gastei 85 reais de gasolina hoje no Nubank.
```

Saída interna esperada:

```json
{
  "tipo": "EXPENSE",
  "valor": 85,
  "descricao": "Gasolina",
  "categoria": "Transporte",
  "data": "2026-07-15",
  "conta": "Nubank",
  "confidence": 0.94
}
```

Regras obrigatórias:

- IA nunca escreve diretamente no ledger sem validação do contrato e política de confirmação;
- alta confiança permite confirmação simples, não gravação silenciosa inicial;
- média confiança pergunta somente o campo ausente ou ambíguo;
- baixa confiança abre revisão;
- transferência, pagamento, exclusão ou automação exigem confirmação reforçada;
- resposta estruturada usa schema fechado e validação determinística;
- webhook, mensagem e lançamento possuem chaves idempotentes;
- prompt, modelo, resposta e correção possuem rastreabilidade compatível com privacidade;
- áudio bruto e conteúdo sensível possuem retenção mínima definida;
- consentimento, fornecedores, exclusão e exportação seguem LGPD;
- limites de custo, rate limit, fallback e indisponibilidade são parte do desenho.

Abstrações sugeridas:

```text
FinancialInputParser
|- RuleBasedParser
|- LlmTextParser
`- AudioTranscriptionParser

FinancialDataConnector
|- CsvConnector
|- OfxConnector
|- WhatsAppConnector
`- OpenFinanceConnector (futuro)
```

Ordem recomendada para inteligência:

1. regras determinísticas e categoria pelo histórico;
2. interpretação de texto;
3. importação inteligente de fatura;
4. áudio;
5. perguntas em linguagem natural sobre dados reconciliados;
6. recomendações personalizadas, explicáveis e acionáveis;
7. automações financeiras somente após controles adicionais.

Chat analítico não deve anteceder reconciliação. IA sobre números incoerentes produz resposta convincente e
incorreta.

## Arquitetura de domínio candidata

Conjunto conceitual para discussão, não autorização de implementação:

- `FinancialAccount`;
- `LedgerTransaction`;
- `LedgerEntry`;
- `Category` e `Merchant` como dimensões;
- `Budget`, `BudgetAllocation` e `BudgetRollover`;
- `Goal` e `GoalAllocation`;
- `InvestmentAccount`, `AssetPosition`, `InvestmentMovement` e `PriceQuote`;
- `CreditCardStatement` e seus lançamentos;
- `ImportedRecord`, `ImportBatch` e `Reconciliation`;
- `FinancialDataConnector` e `FinancialInputParser`.

Antes de migration ou código:

1. escrever glossário financeiro oficial;
2. registrar ADRs de contas, ledger, metas, investimentos, orçamento e competência;
3. definir invariantes executáveis;
4. mapear dados atuais para modelo futuro;
5. construir plano de migration reversível e reconciliável;
6. capturar saldos e totais antes/depois;
7. validar contra PostgreSQL real;
8. liberar por etapas e feature flags quando necessário.

## Roadmap recomendado

### Fase 0 — Congelamento e decisões

- não iniciar Open Finance, WhatsApp ou módulo novo enquanto P0 estiver aberto;
- consolidar glossário financeiro do produto;
- escrever ADRs para conta, ledger, metas, investimentos, orçamento, competência e liquidez;
- definir invariantes, métricas oficiais e política de reconciliação;
- aprovar modelo e plano de migration antes de alterar schema.

### Fase 1 — Integridade imediata

- corrigir renda do onboarding;
- tornar onboarding web/mobile um único caso de uso transacional e idempotente;
- corrigir ciclo de metas;
- corrigir exclusão LGPD com recorrências;
- unificar timezone;
- proteger anexos e backups;
- adicionar regressões automatizadas.

### Fase 2 — Verdade financeira

- definir política contábil;
- migrar `Conta` e `Carteira` para conta financeira coerente;
- evoluir transações para lançamentos que expliquem origem e destino;
- definir transferências internas;
- consolidar parcelamento;
- separar caixa, reservas, investimentos e dívidas;
- tornar operação real de investimento ligada ao caixa e snapshot externo explicitamente não conciliado;
- distinguir reserva virtual de cofrinho real;
- tornar saldo, orçamento, fatura e relatório reconciliáveis;
- criar patrimônio líquido, disponível para gastar e métricas oficiais;
- executar migration com reconciliação automatizada antes/depois.

### Fase 3 — Experiência simples

- reduzir home;
- lançamento realmente rápido;
- onboarding menor e progressivo;
- linguagem financeira consistente;
- histórico de metas concluídas;
- compromissos e disponível para gastar;
- visão separada de dinheiro disponível, reservado, investido e dívidas;
- drill-down de cada número até sua origem.

### Fase 4 — Importação e automação determinística

- regras de categorização;
- detecção de recorrências e assinaturas;
- pipeline canônico de importação;
- CSV e OFX com mapeamento, prévia, deduplicação e conciliação;
- importação e revisão de fatura;
- rollover de orçamento configurável e auditável;
- notificações;
- alertas úteis;
- automações de metas.

### Fase 5 — Assistente conversacional

- interpretação de texto com schema fechado;
- rascunho e confirmação antes do ledger;
- WhatsApp com webhook autenticado e idempotente;
- transcrição de áudio com retenção mínima;
- perguntas em linguagem natural sobre dados reconciliados;
- recomendações explicáveis ligadas a ações;
- observabilidade, privacidade, limites de custo e fallback de fornecedor.

### Fase 6 — Conectores regulados e expansão

- interface `FinancialDataConnector` estabilizada;
- avaliar parceiro Open Finance autorizado, cobertura, custo e SLA;
- consentimento, renovação, revogação e sincronização incremental;
- reconciliação entre dados bancários e ledger;
- planejamento patrimonial e dívidas mais avançado;
- uso familiar somente após modelo explícito de membros, permissões e propriedade dos dados.

## Regras para execução futura

- não criar módulo novo enquanto P0 estiver aberto;
- nenhuma regra financeira duplicada em cliente;
- cada feature precisa de fonte de verdade definida;
- toda alteração financeira exige teste de concorrência/idempotência proporcional;
- migrations precisam ser validadas em PostgreSQL real;
- jornada principal precisa de E2E;
- documentação não pode declarar correção sem evidência no código e ambiente;
- nenhum dado financeiro pode desaparecer por arquivamento, conclusão ou deploy;
- usuário precisa entender origem de cada número exibido;
- investimento nunca entra no saldo disponível sem venda/resgate e liquidação;
- transferência interna e compra de investimento não podem virar despesa de consumo;
- IA nunca grava operação definitiva sem contrato validado, política de confirmação e idempotência;
- fonte externa nova deve entrar pelo pipeline canônico de importação/conciliação;
- mês fechado ou regra histórica não pode ser reescrito silenciosamente;
- toda migration financeira precisa comprovar reconciliação antes/depois e caminho de recuperação.

## Critério de sucesso do produto

Produto estará alinhado à meta quando usuário conseguir:

1. abrir aplicativo e entender situação financeira em até 3 segundos;
2. registrar gasto comum em menos de 10 segundos;
3. explicar diferença entre saldo, resultado, comprometido e patrimônio;
4. reconciliar números de dashboard, extrato, fatura e relatório;
5. concluir uma meta sem perder acesso ao dinheiro;
6. importar ou capturar transações sem digitação repetitiva;
7. receber recomendação clara, explicável e acionável;
8. exportar ou excluir todos os seus dados com confiança;
9. recuperar serviço e anexos após falha ou deploy;
10. usar app sem conhecer termos técnicos internos;
11. distinguir dinheiro disponível, reservado, investido e devido;
12. comprar ou vender investimento sem distorcer despesa, caixa ou patrimônio;
13. transferir entre contas sem gerar receita ou despesa falsa;
14. importar o mesmo arquivo novamente sem duplicar lançamentos;
15. usar rollover de orçamento entendendo exatamente o valor carregado;
16. enviar texto ou áudio, revisar interpretação e confirmar lançamento com poucos passos;
17. rastrear recomendação ou resposta da IA até dados financeiros reconciliados;
18. adicionar futuro conector bancário sem criar uma segunda verdade financeira.

## Estado desta decisão

Este documento consolida diagnóstico, decisões candidatas e direção para discussão detalhada. Não autoriza
sozinho alterações de código, banco, infraestrutura ou produto.

Próximo trabalho deve começar somente após discussão, definição explícita de escopo, aprovação das decisões de
domínio e prioridade pelo responsável do produto.
