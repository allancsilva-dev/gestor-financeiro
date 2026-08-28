# ADR-0017 — Assistente financeiro mobile-first

- **Status:** ACEITO
- **Data:** 2026-08-27
- **Decisão de produto:** mobile primeiro; web fora da Fase 5

## Contexto

O ledger e as métricas oficiais são determinísticos. Um modelo generativo não pode se tornar
uma segunda fonte de verdade financeira nem receber autoridade implícita para escolher o titular.
O assistente precisa reduzir a fricção de entrada e explicar dados existentes sem enfraquecer
ownership, idempotência, retenção, orçamento ou auditabilidade.

## Decisão

O fluxo será **texto → perguntas → recomendações → áudio pré-gravado → WhatsApp**. Gemini é o
fornecedor primário e OpenAI o secundário real. O parser determinístico roda sempre antes de um
fornecedor e continua sendo o fallback final. App texto e áudio são síncronos e limitados; WhatsApp
é assíncrono na lane `ASSISTANT`, isolada do worker financeiro (concorrência 2). Jobs transportam
somente IDs, nunca mensagem, telefone, transcript ou contexto.

Nenhuma IA escreve no ledger. Ela só propõe um DTO fechado, que o backend valida e resolve por
ownership. O usuário revisa o formulário financeiro existente e uma transação única confirma o
rascunho exatamente uma vez. Há no máximo uma pergunta de esclarecimento; dúvida persistente abre
o formulário. `usuarioId` vem exclusivamente da conversa/vínculo persistido e autenticado, nunca
do modelo, texto, webhook ou `SecurityContext` de worker.

O schema versionado de extração rejeita campos adicionais, inclusive `confidence`, IDs, SQL,
comandos e ferramentas. Prompt e dados financeiros são domínios separados; descrições são
marcadas como dados não confiáveis. Busca web, grounding e ferramentas externas ficam desligados.
Recusas de segurança e erros de autenticação/configuração não causam failover.

Áudio é arquivo pré-gravado: Files + Interactions API no primário, transcrição OpenAI no fallback.
Live API, streaming, object storage novo e fila durável para áudio ficam fora do escopo.

## Limites e fail-closed

- texto: 2.000 caracteres; contexto externo: 8.000 caracteres;
- áudio: 8 MiB e 60 segundos; uma transcrição global por vez;
- duas chamadas textuais globais; 20 chamadas externas por titular/dia; US$ 5/dia global;
- saturação responde `429` e `Retry-After`, sem fila ilimitada;
- uma chamada e no máximo um retry para `429/5xx`, limitado pelo timeout total;
- feature externa desligada por padrão; produção exige chave, billing confirmado, aceite da
  política de dados e teto de custo. Free tier só pode usar dataset sintético isolado;
- OpenAI usa Responses API com `store=false` e ferramentas nativas desligadas.

## Retenção, LGPD e auditoria

Mensagens/transcripts expiram em 30 dias e rascunhos não confirmados em 24 horas. Arquivos de
áudio locais/remotos e buffers são apagados em `finally`; falha de limpeza gera métrica/alerta.
Prompt/resposta brutos ficam desligados em produção e, em ambiente sintético opt-in, expiram em
até 72 horas. Telefone/`wa_id` é ciphertext versionado mais HMAC de busca; o hash continua dado
pseudonimizado sujeito a exportação e exclusão.

A confirmação mantém snapshot imutável, sem depender da conversa: operação/transação, campos
normalizados, hash do input, provider/model, versões de prompt/schema, correções e timestamp.
Depois de 30 dias o contexto linguístico original deixa de ser reproduzível; o ganho de privacidade
é aceito. Toda migration `assistant_*` entra, no mesmo PR, nos manifestos simétricos de exclusão e
exportação, protegidos por teste de catálogo PostgreSQL.

## Consequências

Respostas podem degradar para parser/formulário quando fornecedores ou orçamento falharem. Ações
de recomendação apenas abrem tela ou rascunho. WhatsApp e texto ficam desligados em produção até
gates explícitos; ativação do WhatsApp exige nova consulta de tarifa/termos Meta e homologação.
O web não recebe implementação nesta fase.
