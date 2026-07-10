# Auditoria Mobile × Backend — 2026-07-10

Escopo: app Expo (`mobile/`) + contratos com backend Spring Boot. Frontend web fora do escopo.
Objetivo: rastrear cada problema, a solução correta (sem gambiarra) e o status.

Legenda status: ✅ CORRIGIDO · ⬜ PENDENTE · 🔶 EM ANDAMENTO

---

## 1. Bugs de contrato (quebrados hoje)

### BUG-M01 — Login manda todo usuário pro onboarding
**Status:** ✅ CORRIGIDO (2026-07-10, BUG-0030) — `AuthController` agora inclui `onboardingCompleto` no map `usuario` do login. `AuthControllerTest` 17/17 PASS.
**Onde:** `mobile/app/(auth)/login.tsx:23` + `backend/.../AuthController.java:146`
**Problema:** login checa `user.onboardingCompleto`, mas o map `usuario` retornado por `/auth/login` só tem `id/nome/email`. Campo vem `undefined` → falsy → todo login redireciona para `/onboarding`, mesmo usuário antigo. `GET /v1/usuarios/me` (`UsuarioResponseDto`) já tem o campo; o login não.
**Solução correta:** incluir `onboardingCompleto` no map `usuario` da resposta de login no `AuthController` (fonte única: mesma projeção do `UsuarioResponseDto`). Não resolver no mobile com chamada extra a `/me` — o contrato de login deve ser completo, o tipo `Usuario` do mobile já espera o campo.

### BUG-M02 — "Exportar Dados" nunca funcionou (401)
**Status:** ✅ CORRIGIDO (2026-07-10, BUG-0031) — CSV baixado via axios autenticado; nativo salva com `expo-file-system` e compartilha o arquivo com `expo-sharing`; web baixa via Blob. Falta validar o Share em device real.
**Onde:** `mobile/app/(app)/more/index.tsx:22-36`
**Problema:** abre `${API_BASE_URL}/v1/exportar/completo` via browser/`Share`. Endpoint exige `Authorization: Bearer` → 401 sempre. Ainda compartilha URL da API para fora.
**Solução correta:** baixar via `api` (axios autenticado) com `responseType: 'blob'`/arraybuffer, salvar com `expo-file-system` e compartilhar o **arquivo** com `expo-sharing`. Nunca compartilhar URL da API.

### BUG-M03 — Logout não revoga refresh token no servidor
**Status:** ✅ CORRIGIDO (2026-07-10, BUG-0032) — `authService.logout()` chama `POST /auth/logout` com `X-CSRF-Token` (best-effort, storage sempre limpo); `perfil.tsx` usa só o `logout()` do contexto, com `await`.
**Onde:** `mobile/src/services/authService.ts:17` + `mobile/app/(app)/perfil.tsx:25`
**Problema:** logout só limpa storage local. Backend tem `POST /auth/logout` (revoga refresh token do cookie HttpOnly) — nunca é chamado. Sessão continua válida no servidor. Em `perfil.tsx` a chamada nem tem `await`.
**Solução correta:** `authService.logout()` chama `POST /auth/logout` (com header `X-CSRF-Token`, `withCredentials`) antes de limpar storage; limpar storage mesmo se a chamada falhar (best-effort, mas sempre tentar). `perfil.tsx` aguarda com `await` antes de navegar.

### BUG-M04 — Carteiras usam endpoints deprecated
**Status:** ✅ CORRIGIDO (2026-07-10, BUG-0033) — `carteiraService` migrado para `POST /{id}/ajustes` (`ajustarSaldo`). Métodos antigos eram código morto (zero chamadores). Endpoints deprecated seguem no backend até o web migrar.
**Onde:** `mobile/src/services/carteiraService.ts:20-23`
**Problema:** usa `POST /v1/carteiras/{id}/adicionar` e `/remover`, marcados `@Deprecated(since = "PR-LEDGER-06")`. O substituto é `POST /{id}/ajustes` (`AjusteCarteiraRequest`: tipo + valor + descrição), que registra movimento no ledger.
**Solução correta:** migrar `carteiraService` para `/ajustes`; UI pede descrição do ajuste (ex.: "acerto de saldo"). Depois de migrado, remover os endpoints deprecated do backend.

---

## 2. Buracos de produto — o dinheiro não fecha

Princípio: todo real precisa ter origem e destino. Hoje vaza em dois fluxos; fatura de cartão já faz certo (`pagarFatura` recebe `carteiraId`) — usar como padrão.

### PROD-M05 — Pagar conta fixa não debita carteira
**Status:** ✅ CORRIGIDO (2026-07-10, BUG-0034) — controller repassa `carteiraId` (obrigatório, 422 sem ele); transação vinculada à carteira debita via ledger. Mobile pede carteira no modal Pagar. E2E: saldo 4000→3850 com movimento SAIDA.
**Onde:** `backend/.../ContaFixaService.marcarComoPaga` (linha ~96) + `ContaFixaController` `PUT /{id}/pagar` + `mobile/src/services/contaFixaService.ts:18`
**Problema:** pagamento cria transação SAIDA **sem carteira** → `saldoCarteiras` não cai. Usuário paga aluguel, patrimônio continua igual. `ValorRequest` já tem `carteiraId`, mas o controller ignora.
**Solução correta:** controller repassa `request.getCarteiraId()`; service vincula a carteira à transação criada (mesma mecânica do pagamento de fatura, movimenta o ledger). Mobile: tela de contas fixas pede carteira de pagamento (igual ao fluxo de pagar fatura em `more/faturas.tsx`) e envia `{ valor, carteiraId }`. `carteiraId` obrigatório no fluxo — sem carteira o saldo mente.

### PROD-M06 — Reservar valor em meta não sai de lugar nenhum
**Status:** ✅ CORRIGIDO (2026-07-10, BUG-0035) — reserva debita carteira (`RESERVA_META`), resgate credita (`RESGATE_META`, limitado ao reservado); `carteiraId` obrigatório. Mobile ganhou seletor "Sai de". E2E validado no ledger.
**Onde:** `backend/.../MetaService.adicionarValor` / `removerValor` + `mobile/app/(app)/metas.tsx`
**Problema:** só incrementa `valorReservado`. Dinheiro "guardado" continua contando como disponível nas carteiras — dupla contagem.
**Solução correta:** modelar reserva como movimento de carteira: `adicionarValor` recebe `carteiraId` e debita (movimento no ledger, tipo RESERVA_META); `removerValor` credita de volta. Dashboard: "Disponível" = saldo carteiras − reservas ativas (ou o débito já resolve se a reserva sai da carteira). Alternativa aceitável: carteira virtual "Reservas" por usuário. Não aceitar: subtrair só no front.

### PROD-M07 — Home: rótulos de saldo enganam
**Status:** ✅ CORRIGIDO (2026-07-10, BUG-0036) — hero "Saldo total" sem mês, chips ↑↓ com "em {mês}", KPI "Disponível" → "Saldo do mês" usando `resumo.saldo`.
**Onde:** `mobile/app/(app)/index.tsx:44,126,162`
**Problema:** hero mostra `saldoCarteiras` (patrimônio) com label "Saldo total · {mês}" — patrimônio não é do mês. KPI "Disponível" repete o mesmo `saldoCarteiras` do hero (redundante). O campo `resumo.saldo` (saldo do mês) vem do backend e ninguém usa.
**Solução correta:** hero = patrimônio, label "Saldo total" sem mês (chips ↑↓ continuam sendo do mês — deixar explícito "em {mês}"). KPI "Disponível" → "Saldo do mês" usando `resumo.saldo`. Após PROD-M06, "Disponível" pode voltar como patrimônio − reservas.

---

## 3. Telas — evolução funcional

### UX-M08 — Transações: paginação e somatório mentiroso
**Status:** ✅ CORRIGIDO (2026-07-10, BUG-0037) — `useInfiniteQuery` com paginação real sobre `/v1/transacoes/periodo`; seletor de mês (‹ mês ›, default atual); somatório do cabeçalho vem de `/v1/relatorios` (totais do período, não da página); filtro tipo e busca `q` viraram parâmetros do backend (novos filtros no endpoint `/periodo`). Teste de integração cobre tipo/q/combinado.
**Onde:** `mobile/app/(app)/transacoes.tsx`
**Problema:** carrega só `page=0&size=20`, sem infinite scroll. Cards "Entradas/Saídas" somam apenas os 20 itens carregados. Filtro ENTRADA/SAIDA é client-side sobre a página. Sem filtro por período, sem busca. Backend tem `GET /v1/transacoes/periodo` ocioso.
**Solução correta:** `useInfiniteQuery` com paginação real; seletor de mês (default mês atual) usando `/v1/transacoes/periodo`; somatório do cabeçalho vem do backend (`/v1/relatorios?inicio&fim` já retorna totalEntradas/totalSaidas do período — não somar página no client); filtro tipo/busca como parâmetros de query no backend (adicionar `tipo` e `q` ao endpoint se necessário).

### UX-M09 — Sem tela de cadastro
**Status:** ✅ CORRIGIDO (2026-07-10, BUG-0038) — `register.tsx` no grupo `(auth)` consumindo `/auth/register`, validação client igual à do backend (`@ValidPassword`: 8+ chars, 1 letra, 1 número; confirmação), login automático após sucesso e redirect para onboarding. Link "Criar conta" no login.
**Onde:** `mobile/app/(auth)/` (só login + forgot-password)
**Problema:** backend tem `POST /auth/register`; usuário novo não consegue criar conta pelo app.
**Solução correta:** tela `register.tsx` no grupo `(auth)` consumindo `/auth/register` (`RegisterRequest`), com validação alinhada às regras do backend e login automático após sucesso.

### UX-M10 — Reset de senha termina no vácuo
**Status:** ✅ CORRIGIDO (2026-07-10, BUG-0039) — `reset-password.tsx` aceita deep link `gestorfinanceiro://reset-password?token=...` (scheme já existia no `app.json`) e colagem manual do token (entrada pela tela de sucesso do forgot-password: "Já recebi o código"). `EmailService` monta o link com property `app.reset-password-link-base` (envio real de e-mail continua TODO).
**Onde:** `mobile/app/(auth)/forgot-password.tsx` (envio ok) — falta a segunda metade
**Problema:** e-mail é enviado, mas não existe tela para `POST /auth/reset-password` nem deep link do app.
**Solução correta:** configurar scheme/universal link no Expo (`app.json`) apontando para tela `reset-password.tsx` (token + nova senha); backend gera link com o scheme do app no e-mail. Enquanto não houver deep link, tela acessível pelo login com colagem manual do token.

### UX-M11 — Metas: sem editar, excluir ou retirar valor
**Status:** ✅ CORRIGIDO (2026-07-10) — cards abrem sheet de detalhe; UI permite editar, excluir e retirar valor com conta destino; criação/edição expõe `valorMensal`.
**Onde:** `mobile/app/(app)/metas.tsx`
**Problema:** UI só cria e adiciona valor. `metaService.removerValor`, `atualizar` e `deletar` já existem no mobile e no backend — sem UI. Form não expõe `valorMensal`.
**Solução correta:** tocar no card abre sheet de detalhe com editar/excluir/retirar valor; form de criação/edição ganha `valorMensal`. Excluir com confirmação destrutiva (e, após PROD-M06, devolve reserva à carteira).

### UX-M12 — Editar transação não permite trocar categoria
**Status:** ✅ CORRIGIDO (2026-07-10) — modal de edição expõe seletor de categoria; backend aplica `categoriaId` no update validando ownership e ajustando gasto por categoria.
**Onde:** `mobile/src/components/EditarTransacaoModal.tsx` + `backend/.../TransacaoService.atualizar`
**Problema:** backend só aplica descrição/valor/data/observações na atualização. Trocar categoria é a edição mais comum.
**Solução correta:** backend passa a aceitar `categoriaId` no update (validando ownership); mobile expõe seletor de categoria no modal. Tipo e forma de pagamento continuam imutáveis (correto — mudam a semântica contábil; o caminho é excluir e recriar).

### UX-M13 — Nomenclatura "Contas" × "Carteiras" confunde
**Status:** ✅ CORRIGIDO (2026-07-10) — UI mobile usa "Contas" para saldos/dinheiro (`carteiras`) e "Cartões" para `contas`; entidades/rotas internas preservadas.
**Onde:** `mobile/app/(app)/more/index.tsx` (tiles), telas `contas.tsx` / `carteiras.tsx`
**Problema:** "Contas · Bancos" (= cartões de crédito/débito) vs "Carteiras · Contas e saldos" (= onde o dinheiro fica). Usuário comum não distingue.
**Solução correta:** renomear na UI: "Contas" → **"Cartões"**; "Carteiras" → **"Contas"** (ou manter "Carteiras" e ajustar subtítulos). Só rótulo/cópia — não renomear entidades do backend. Decidir e aplicar consistente em toda a UI (home, modais, faturas).

### UX-M14 — Perfil órfão e vazio
**Status:** ✅ CORRIGIDO (2026-07-10) — Perfil aparece no hub "Mais"; backend permite editar nome e trocar senha; mobile expõe formulário de dados pessoais e segurança.
**Onde:** `mobile/app/(app)/perfil.tsx` (rota com `href: null`, acesso só pelo avatar)
**Problema:** não edita nome nem senha; backend também só tem `GET /v1/usuarios/me`.
**Solução correta:** backend: `PUT /v1/usuarios/me` (nome) e endpoint de troca de senha (senha atual + nova). Mobile: formulário no perfil + entrada visível (ex.: no hub "Mais"). Baixa prioridade frente aos itens de dinheiro.

---

## 4. Backend rico, mobile cego (features prontas sem UI)

**Status:** ✅ CORRIGIDO (2026-07-10, BUG-0040/0042/0043/0044) — mobile cobre os endpoints prontos da seção: relatórios, extrato/reconciliação, insights, parcelas, anexos, importação CSV e investimentos.

| Endpoint pronto | Valor no app |
|---|---|
| ✅ `/v1/dashboard/gastos-por-categoria` | Coberto: Relatórios já lista gastos por categoria com barras e % (`/v1/relatorios`) |
| ✅ `/v1/dashboard/evolucao-mensal` | Gráfico de barras entradas × saídas (6 meses) em Relatórios |
| ✅ `/v1/dashboard/comparacao-mensal` | Card em Relatórios comparando mês atual × anterior, com saldo, entradas, saídas e variação |
| ✅ `/v1/carteiras/{id}/movimentos` | Extrato da carteira (tocar no card em Carteiras) |
| ✅ `/v1/carteiras/{id}/reconciliacao` | Badge e resumo de conferência no extrato da conta |
| ✅ `/v1/insights` | Card de insights na home com resumo, gasto do mês, média, alertas e recomendação |
| ✅ `/v1/parcelas` (pagar/despagar) | Gestão de parcela individual dentro da edição da transação |
| ✅ `/v1/anexos` | Comprovante por transação na edição: câmera, arquivo, listagem e exclusão |
| ✅ `/v1/importar/csv` | Importação de extrato no hub Mais via seletor de arquivo |
| ✅ `/v1/investimentos` | Módulo investimentos com ativos, edição, exclusão e movimentações |

Prioridade: extrato de carteira e gráficos primeiro (reforçam confiança e leitura); investimentos por último.

---

## 5. O que já está bom (não mexer sem motivo)

- `mobile/src/services/api.ts`: refresh token single-flight, retry de 401, mensagens de erro pt-BR, sem log de dado sensível.
- Contratos que **batem**: login (exceto BUG-M01), dashboard resumo, transações, metas, orçamentos, relatórios, contas fixas e fatura completa (incl. `tipo COMPRA/AJUSTE/ESTORNO`).
- Invalidação de queries consistente nos modais; acessibilidade (`accessibilityRole/Label`) presente; estados loading/erro/vazio em todas as telas.

---

## 6. Ordem de ataque recomendada

1. ~~**BUG-M01 a M04**~~ ✅ concluído em 2026-07-10 (BUG-0030 a BUG-0033 no BUGFIX_LOG).
2. ~~**PROD-M05 a M07**~~ ✅ concluído em 2026-07-10 (BUG-0034 a BUG-0036 no BUGFIX_LOG). Ressalva resolvida: web alinhado em 2026-07-10 (BUG-0046) — pagar/reservar enviam `carteiraId` com seletor de conta na UI.
3. ~~**UX-M08**~~ ✅ concluído em 2026-07-10 (BUG-0037).
4. ~~**UX-M09/M10**~~ ✅ concluído em 2026-07-10 (BUG-0038/0039).
5. ~~**UX-M11 a M14**~~ ✅ concluído em 2026-07-10.
6. ~~**Seção 4**~~ ✅ concluído em 2026-07-10 (BUG-0040/0042/0043/0044).

> Ao corrigir um item: atualizar o **Status** aqui e registrar em `docs/BUGFIX_LOG.md`.
