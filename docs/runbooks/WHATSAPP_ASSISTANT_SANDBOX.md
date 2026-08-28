# Assistente WhatsApp — sandbox e ativação

O canal usa diretamente a Meta Cloud API. Twilio, templates proativos e produção permanecem fora do rollout atual.

## Pré-requisitos externos

- Meta Business verificado, política de privacidade pública e display name aprovado.
- Número Cloud API e webhook `GET/POST /api/v1/webhooks/meta/whatsapp` homologados.
- Configurar verify token, app secret, access token, phone number ID e versão explícita da Graph API.
- Gerar chave AES-256 em Base64, segredo HMAC independente e versão da chave.
- Manter `ASSISTANT_WHATSAPP_ENABLED=false` e `ASSISTANT_WHATSAPP_WORKER_ENABLED=false` em produção.

## Smoke de sandbox

1. Ativar as duas flags somente no ambiente interno e confirmar que o backend inicia fail-closed.
2. Criar o vínculo no app autenticado; o código deve expirar em 10 minutos e funcionar uma vez.
3. Enviar o código pelo número de teste Meta. Não há resposta durante o vínculo.
4. Enviar um lançamento textual e confirmar que apenas `{eventId}` aparece no job da lane `ASSISTANT`.
5. Confirmar transcript/resposta no WhatsApp e revisar qualquer rascunho financeiro dentro do app.
6. Repetir o mesmo webhook: deve retornar 200 sem novo job. Número não vinculado também retorna 200 sem resposta.
7. Validar exclusão e exportação LGPD do titular.

## Gate futuro de produção

Reconsultar termos, tarifas, limites, versão suportada da Graph API e requisitos de consentimento diretamente na Meta. Registrar aprovação explícita do produto e segurança antes de alterar as flags. Nenhum valor ou condição comercial atual é premissa arquitetural.
