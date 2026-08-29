# Fase 5 — fechamento operacional

## Execução local

Pré-requisitos: Docker Desktop ativo, um iPhone Simulator bootado, Xcode, CocoaPods, Maestro,
Node 20 e Java 21. Segredos nunca entram em argumentos persistidos ou evidências.

```bash
cd backend
./mvnw -q test
./mvnw -q verify -Pintegration-test

cd ..
./scripts/e2e-assistant-ios.sh
```

O runner escolhe o primeiro iPhone bootado. Para seleção explícita, use
`GF_E2E_SIMULATOR_UDID=<udid>`. Evidências ficam em `artifacts/fase5/<run-id>/`; para apontar outro
diretório, use `GF_E2E_ARTIFACT_DIR`. Qualquer flow, reconciliação, prova de unicidade ou varredura
sensível com falha encerra o processo com código não zero. Profile `local-e2e` nunca deve ser usado
em deploy.

Antes de rodar: Docker Desktop precisa estar de pé (`docker info`) e um iPhone precisa estar bootado
(`xcrun simctl boot <udid>`). O runner não sobe nenhum dos dois.

### O que o runner faz de propósito

- **Build Release, assinado pelo Xcode.** `Debug` força `DEV=true` em `react-native-xcode.sh` e o
  bundle sai com LogBox, que cobre a tela ao primeiro `console.error`. Build sem assinatura fica sem
  entitlements, e sem Keychain o app não guarda sessão (`expo-secure-store`). Ver PROB-0088.
- **`xcrun simctl keychain reset` antes de cada flow.** `clearState` do Maestro não limpa Keychain,
  então a sessão do flow anterior sobreviveria e a tela de login nunca apareceria (PROB-0089).
- **Fixture com cartão e três categorias.** Sem cartão não há como provar parcelamento; com uma
  categoria só, a extração por palavra não prova nada.
- **`EXPO_PUBLIC_ASSISTANT_TEXT_ENABLED=true` no build.** Sem essa flag o item "Assistente" nasce
  desabilitado em Ajustes e nenhum flow entra na tela.

### Flows e prova financeira

Seis flows do assistente, cada um seguido de `prove_financial_state`, que compara contra o banco:
transações confirmadas, operações de origem `ASSISTENTE`, confirmações, **movimentos de carteira** e
**lançamentos de fatura**, além de exigir reconciliação global sem divergência.

| flow | transações | movimentos | lançamentos de fatura |
|---|---|---|---|
| `assistant-text` | 1 | 1 | 0 |
| `assistant-ambiguity` | 1 | 1 | 0 |
| `assistant-retry` | 2 | 2 | 0 |
| `assistant-confirm-retry` | 3 | 3 | 0 |
| `assistant-parcelado` | 4 | 3 | 3 |
| `assistant-audio` | 5 | 3 | 6 |

Movimentos param em 3 porque compra no cartão não toca a carteira: o cronograma é da fatura. Depois
dos flows o runner ainda prova que a mesma `Idempotency-Key` com payload diferente devolve `409` e
varre as evidências atrás de segredo.

### Ressalva conhecida: `importacao-mobile`

O flow de importação grava o CSV em `<device>/data/Media/Downloads`, que o seletor de arquivos do
iOS não lista — ele abre em "Recentes", vazio. Nesse sintoma específico o runner registra
`importacao-mobile-skipped.txt` e segue; qualquer outra falha do flow derruba o gate. Rastreado em
BACKLOG-0111.

## Checklist externo obrigatório

### Gemini/OpenAI: billing, política e smoke pago

- **Responsável:** administrador das contas AI e responsável de segurança/produto.
- **Credencial/aprovação:** billing ativo, política de dados aceita, API keys restritas e teto aprovado.
- **Procedimento:** revisar retenção/uso de dados; executar um smoke mínimo em ambiente interno.
- **Evidência:** aprovação datada, configuração de teto e request ID sanitizado do smoke.
- **Rollback:** revogar keys e manter `ASSISTANT_EXTERNAL_ENABLED=false`.

### Meta Business e WhatsApp

- **Responsável:** administrador Meta Business.
- **Credencial/aprovação:** Business, display name, número, webhook, termos e tarifas aprovados.
- **Procedimento:** seguir `WHATSAPP_ASSISTANT_SANDBOX.md`, sem habilitar produção.
- **Evidência:** IDs de aprovação e relatório sanitizado do smoke sandbox.
- **Rollback:** flags `false`, token revogado e webhook removido.

### Backup off-host e restore drill (`PROB-0081`)

- **Responsável:** infraestrutura/SRE.
- **Credencial/aprovação:** remote off-host, chave pública GPG e janela de manutenção.
- **Procedimento:** executar backup canônico, validar checksum remoto, restaurar DB e uploads em host limpo.
- **Evidência:** manifesto/checksums, logs sanitizados, contagens e download íntegro de anexo.
- **Rollback:** abortar promoção; manter serviço anterior e preservar último backup validado.

### Deploy e smoke de produção

- **Responsável:** responsável de release, com aprovação produto/segurança.
- **Credencial/aprovação:** acesso ao ambiente, gates locais verdes e três checklists anteriores aprovados.
- **Procedimento:** deploy controlado, migrations, health, reconciliação e smoke sem operação paga indevida.
- **Evidência:** release SHA, migrations, health, reconciliação zero e relatório de smoke.
- **Rollback:** desabilitar flags, reverter release compatível e executar pós-rollback/reconciliação.
