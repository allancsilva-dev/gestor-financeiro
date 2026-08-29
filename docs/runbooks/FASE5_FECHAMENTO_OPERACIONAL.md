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
`GF_E2E_SIMULATOR_UDID=<udid>`. Evidências ficam em `artifacts/fase5/<run-id>/`. Qualquer flow,
reconciliação, prova de unicidade ou varredura sensível com falha encerra o processo com código não
zero. Profile `local-e2e` nunca deve ser usado em deploy.

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
