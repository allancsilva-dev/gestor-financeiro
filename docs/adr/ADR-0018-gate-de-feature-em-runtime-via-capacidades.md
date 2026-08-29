# ADR-0018 — Gate de feature do app sai do build e vira runtime via endpoint de capacidades

- **Status:** ACEITO
- **Data:** 2026-08-29
- **Relacionado:** PROB-0092, ADR-0017 (assistente financeiro mobile-first)

## Contexto

Em 2026-08-29 o dono do produto subiu uma nova versão do backend e instalou um novo build do app, e
reportou que o Assistente estava inacessível. A investigação encontrou dois gates independentes, os
dois resolvendo a mesma pergunta ("o Assistente está ligado?") em momentos e lugares diferentes:

1. **Mobile:** `mobile/app/(app)/ajustes.tsx:49` desabilitava o item do Assistente quando
   `process.env.EXPO_PUBLIC_ASSISTANT_TEXT_ENABLED !== 'true'` (tile com opacity 0.55, badge "Em
   breve", `disabled`). Essa env var só era definida em `scripts/e2e-assistant-ios.sh`, para os
   flows Maestro locais — o workflow de release (`mobile-release.yml` / build via EAS) nunca a
   definia. Resultado: **todo build publicado saía com o Assistente travado**, porque a variável
   `EXPO_PUBLIC_*` é resolvida em tempo de build (`expo-constants`/Metro a embutem no bundle) e não
   pode ser alterada depois sem gerar um novo binário.
2. **Backend:** `AssistantController.java:21` tem `@ConditionalOnProperty("assistant.text.enabled")`
   (default `false` em `application.properties:133`), e nem `docker-compose.vps.yml` nem
   `docker-compose.production.yml` repassavam nenhuma variável `ASSISTANT_*` ao container `api`.
   Toda rota `/api/v1/assistant/**` respondia 404 — que o `@ConditionalOnProperty` remove do
   contexto Spring por completo, não é um "410 desligado" nem um corpo explicativo — e o app
   traduzia isso genericamente como "Não foi possível enviar".

Os dois gates são independentes: corrigir só um deixa o Assistente quebrado pelo outro. E os dois
compartilham o mesmo defeito estrutural: **a decisão "isso está ligado?" ficava fixada no momento
errado** — no build do app (variável de ambiente do Expo, imutável depois de publicado) e na
topologia HTTP do backend (rota que desaparece, sem contrato de resposta), em vez de ficar numa
única fonte consultável em runtime pelos dois lados.

## Decisão

O gate de feature deixa de existir no build do app e vira uma consulta de runtime contra o próprio
backend.

- Novo `GET /api/v1/capacidades` (autenticado, `CapacidadesController.java` +
  `CapacidadesResponse.java`), devolvendo `{assistenteTexto, assistenteAudio, assistenteWhatsapp}`.
  Lê exatamente as mesmas properties (`assistant.text/audio/whatsapp.enabled`) que já governam os
  `@ConditionalOnProperty` dos controllers reais — não é uma segunda fonte de verdade, é um espelho
  delas.
- **Deliberadamente sem `@ConditionalOnProperty`.** O endpoint existe precisamente para dizer que
  algo está desligado; se ele sumisse junto com a feature, o cliente voltaria a receber 404 sem
  contexto e o problema se repetiria.
- Mobile: `src/services/capacidadesService.ts` + `src/hooks/useCapacidades.ts` (React Query,
  `staleTime` de 5 minutos, **fail-closed**: erro ou loading tratam todas as capacidades como
  desligadas, nunca como ligadas por omissão). `ajustes.tsx` e `more/assistente.tsx` passaram a ler
  o hook em vez da env var; `EXPO_PUBLIC_ASSISTANT_TEXT_ENABLED` e
  `EXPO_PUBLIC_ASSISTANT_WHATSAPP_ENABLED` foram eliminadas do código do app.
  `more/assistente.tsx` ganhou guarda de deep link — quem chega direto na rota com a capacidade
  desligada vê `EstadoVazio` "Assistente indisponível", não um 404 travestido de erro genérico.
- `docker-compose.vps.yml` e `docker-compose.production.yml` passaram a repassar
  `ASSISTANT_TEXT_ENABLED`/`ASSISTANT_AUDIO_ENABLED` (default `false`) ao container `api`.
  `ASSISTANT_EXTERNAL_ENABLED` e as chaves de IA continuam de fora por decisão do dono do produto —
  o app fica limitado ao parser determinístico até uma decisão explícita de ligar fornecedor pago.
- Bônus de robustez na mesma correção: `GlobalExceptionHandler` passou a tratar `ProviderFailure`
  (que antes escapava do `AiExtractionPipeline` em CONFIGURATION/SAFETY_REFUSAL e virava 500
  genérico) como 503 `AI_UNAVAILABLE` ou 422 `AI_REFUSED` — um efeito colateral de mapear
  corretamente "feature indisponível" em vez de deixar a exceção subir crua.

## Consequências

- Ligar ou desligar uma capacidade do Assistente (texto, áudio, WhatsApp) passa a ser uma mudança de
  variável de ambiente do backend, sem exigir novo build/publicação do app — o app existente já
  reage à mudança na próxima consulta de `/api/v1/capacidades` (até 5 minutos de `staleTime`).
- O app nunca mais depende de saber, em tempo de build, o que está ligado em produção. Isso também
  cobre o caso de um mesmo binário apontar para ambientes diferentes (dev/staging/produção) com
  capacidades diferentes — antes isso era estruturalmente impossível com `EXPO_PUBLIC_*`.
- Introduz uma chamada de rede a mais no caminho de decisão de UI (mitigada por cache de 5 minutos
  do React Query). Fail-closed significa que uma falha de rede transitória esconde o Assistente em
  vez de arriscar mostrá-lo quebrado — troca aceita deliberadamente a favor de nunca exibir uma
  função que vai 404.
- Cria um contrato novo (`CapacidadesResponse`) que precisa evoluir junto de qualquer novo canal do
  Assistente (ou de outra feature que adote o mesmo padrão) — esquecer de adicionar o campo novo
  recria o mesmo tipo de gate quebrado que esta decisão eliminou.
- Não resolve, por si, o caso em que o **backend inteiro** está desatualizado ou fora do ar: se
  `/api/v1/capacidades` não responde, o fail-closed do hook já cobre esse caso (tudo desligado), mas
  não há um estado de UI específico para "não consegui nem perguntar se isso está ligado" versus
  "perguntei e está desligado" — ambos resultam na mesma tela. Aceito por ora; revisar se isso gerar
  confusão de suporte.

## Alternativa descartada

**Manter `EXPO_PUBLIC_ASSISTANT_TEXT_ENABLED`/`EXPO_PUBLIC_ASSISTANT_WHATSAPP_ENABLED`, mas
garantir que o workflow de release sempre as defina.** Corrigiria o sintoma imediato (build de
release saindo com o Assistente travado), mas manteria o defeito estrutural: qualquer mudança de
capacidade em produção continuaria exigindo novo build e nova publicação nas lojas, com o atraso e o
risco de divergência que isso implica (o app publicado nunca sabe, de verdade, o que o backend que
ele está consultando tem ligado agora). Descartada porque o objetivo não era só destravar o
Assistente uma vez, e sim parar de ter duas fontes de verdade — uma no bundle do app, outra no
backend — que podem divergir silenciosamente.
