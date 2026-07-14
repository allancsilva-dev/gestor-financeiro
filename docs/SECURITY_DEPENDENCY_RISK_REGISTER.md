# Registro de Risco de Dependencias

## RISK-DEP-0001 — Toolchain Expo SDK 54

- **Data:** 2026-07-13
- **Responsavel:** mantenedor tecnico do Gestor Financeiro
- **Prazo:** 2026-09-30 ou antes do proximo release publico, o que ocorrer primeiro
- **Status:** ACEITO_TEMPORARIAMENTE
- **Escopo:** mobile, somente toolchain de build/desenvolvimento
- **Evidencia:** `npm audit --omit=dev` apos atualizacao reporta `0 critical`, `0 high`, `14 moderate` e `1 low`.
- **Analise:** os alertas restantes chegam pelas cadeias Node de Expo CLI/config, Metro, React Native dev middleware e geracao Xcode. Nao representam bibliotecas executadas pela API nem codigo carregado no bundle nativo de producao. Pacotes diretos `expo`, `expo-constants`, `expo-linking` e `expo-router` aparecem no relatorio porque agregam essas dependencias de toolchain.
- **Causa da excecao:** o reparo automatico exige Expo 57, upgrade major fora da matriz validada do SDK 54/RN 0.81.5. Aplicar esse salto dentro do reparo de seguranca reabriria os builds nativos estabilizados no `BACKLOG-0071`.
- **Mitigacoes:** CI bloqueia qualquer nova vulnerabilidade runtime `high`/`critical`; entradas de app config, projeto Xcode e assets de build sao controladas no repositorio; builds de release nao processam entrada arbitraria de usuario; lockfile permanece versionado e instalado com `npm ci`.
- **Remocao:** planejar upgrade Expo SDK 54 para versao suportada mais recente, executar `expo-doctor`, TypeScript, Jest, prebuild limpo, Android debug/release, iOS release e smokes fisicos; remover esta excecao somente quando `npm audit --omit=dev` confirmar eliminacao ou quando cada alerta residual tiver nova analise.
- **Regra anti-expansao:** `npm audit --omit=dev --audit-level=high` e bloqueante no CI. Nenhuma excecao futura critical/high pode ser adicionada a este registro sem CVE, caminho, impacto, mitigacao, responsavel e prazo proprios.
