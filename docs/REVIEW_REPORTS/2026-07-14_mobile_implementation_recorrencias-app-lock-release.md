# Implementação de Recorrências, App Lock e Release Mobile 1.1.0

**Data:** 2026-07-14
**Branch:** `main`
**Baseline:** `409d0c7`
**Escopo:** backend, PostgreSQL e app mobile; frontend web preservado por compatibilidade.

## Objetivo

Corrigir projeção de receitas recorrentes, automatizar ocorrências sem duplicidade
ou saldo negativo, proteger dados financeiros mantendo a sessão mobile e gerar um
APK Release rastreável para validação interna.

## Escopo verificado

- domínio, API, scheduler e migration de recorrências;
- ledger, carteira e idempotência sob repetição;
- projeção mensal de entradas e saídas;
- bloqueio mobile, navegação, splash, Dashboard e tela Mais;
- contratos mobile, build Android/iOS e artifact APK.

## Implementado

- `ContaFixa` ganhou `tipo`, `execucaoAutomatica` e carteira opcional/obrigatória na automação;
- migration V29 preserva registros existentes como saídas manuais e cria `execucoes_recorrencia`;
- scheduler executa às 00:05 em São Paulo e recupera atrasos ao iniciar o servidor;
- ocorrência e movimento usam unicidade, lock pessimista e chave idempotente;
- saldo insuficiente registra falha pendente sem transação ou saldo negativo;
- `/realizar` atende entrada/saída; `/pagar` permanece compatível;
- projeção soma entradas antes de subtrair recorrências, parcelas e faturas;
- mobile permite criar/editar entrada/saída manual/automática e mostra falhas no Dashboard;
- `AppLockGate` protege cold start, segundo plano e seletor de apps sem apagar tokens;
- crossfade nativo substitui `ScreenTransition`, respeitando Reduce Motion;
- splash, containers e atalhos seguem os tokens claro/escuro do produto.

## Contratos e compatibilidade

- Registros anteriores: `tipo=SAIDA`, `execucao_automatica=false`.
- Automação exige carteira explícita.
- `PUT /api/v1/contas-fixas/{id}/pagar` continua disponível.
- Novo `PUT /api/v1/contas-fixas/{id}/realizar`.
- Novo `GET /api/v1/contas-fixas/falhas-pendentes`.
- `ProjecaoMensalDto` preserva campos anteriores e adiciona `totalEntradas`.
- Senha da conta é validada por POST autenticado e nunca armazenada no aparelho.

## Evidências

| Gate | Resultado |
|---|---|
| Maven completo | PASS, 164/164 |
| Recorrência + projeção | PASS, 6/6 |
| Migration PostgreSQL real | PASS, incluindo V29 |
| Jest mobile | PASS, 11/11 |
| ESLint / TypeScript | PASS |
| Expo export all | PASS |
| Android `assembleRelease` | PASS |
| iOS Simulator Release | `BUILD SUCCEEDED` |
| `git diff --check` | PASS |

## Artifact Android

- Produto: Nexos Finanças
- Versão: `1.1.0`
- Version code: `4`
- Application ID: `com.gestorfinanceiro.mobile`
- Arquivo: `mobile/android/app/build/outputs/apk/release/nexos-financas-1.1.0.apk`
- Tamanho: `80.323.853` bytes
- SHA-256: `931f6754c9056239f3db9508dc2c47731317ac3eef29abf78d26ba2c65e47fc9`

## Ressalvas

- O diretório nativo/build e o APK são ignorados pelo Git; a proveniência está
  registrada por versão, baseline, checksum e commit desta entrega.
- O build local `release` usa a chave debug do template Expo. É instalável para
  QA interno, mas não substitui assinatura de distribuição ou publicação na store.
- CI remoto, Maestro em staging, hardware físico e push notification ficaram fora.

## Status final

`PASS_COM_RESSALVA` — implementação, migration e builds aprovados; artifact interno
gerado e identificado, com assinatura/publicação externa ainda pendentes.
