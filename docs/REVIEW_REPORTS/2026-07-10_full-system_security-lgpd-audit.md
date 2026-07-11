# Relatorio de Revisao

**Arquivo:** 2026-07-10_full-system_security-lgpd-audit.md

---

## Objetivo

Auditoria ampla do sistema a pedido do usuário: rotas abertas, segurança, conformidade LGPD, bugs explícitos e qualquer problema aparente no app. Correção imediata dos itens #1, #3 e #7.

## Escopo verificado

- Configuração de segurança do Spring Security (filtros, CORS, headers, CSRF, autorização de rotas).
- Autenticação/sessão: JWT, refresh token, rate limit, lockout, recuperação de senha.
- Autorização por objeto (IDOR / ownership) nos services e controllers.
- Upload/download de anexos.
- Exposição de segredos e configuração por perfil (dev/prod/vps).
- Conformidade LGPD: minimização, exposição de PII, direitos do titular.
- Armazenamento de token no cliente (web/mobile).

## Arquivos lidos

- `config/SecurityConfig.java`, `config/JwtUtil.java`, `config/JwtAuthenticationFilter.java`, `config/LoginRateLimitFilter.java`, `config/RefreshTokenCsrfFilter.java`
- `controller/AuthController.java`, `controller/UsuarioController.java`, `controller/AnexoController.java`
- `security/AuthenticatedUserService.java`
- `service/AnexoService.java`, `service/RefreshTokenService.java`, `service/EmailService.java`, e ownership em Conta/Categoria/Transacao/Carteira/Meta/Parcela.
- `exception/GlobalExceptionHandler.java`
- `dto/RegisterRequest.java`, `dto/ResetPasswordRequest.java`, `validation/ValidPassword.java`
- `application.properties`, `application-dev/prod/vps.properties`
- `mobile/src/store/auth.ts`, `mobile/app.json` (diff staged)

## Comandos executados

| Comando | Resultado |
|---|---|
| grep ownership/IDOR, PII em logs, PreAuthorize, secrets | ver Achados |
| `mvn -o compile` | BUILD SUCCESS |

## Achados

| # | Severidade | Descricao | Evidencia |
|---|---|---|---|
| 1 | ALTO | `logout-all` sempre 500 (NPE). Filtro JWT pulava `/api/auth/**` e não populava `Authentication`. | `JwtAuthenticationFilter.java:40`, `AuthController.java:257` |
| 2 | ALTO | Credenciais de produção commitadas: host `187.77.61.191:5433`, user `admin_nexos`, nome do DB. | `application-prod.properties` |
| 3 | (FALSO POSITIVO) | reset-password "sem política de senha". Na verdade já aplica `@ValidPassword`. | `dto/ResetPasswordRequest.java:12` |
| 4 | MEDIO | Refresh token armazenado em texto puro (UUID) no DB; vazamento = roubo de sessão. | `RefreshTokenService.java:48,66` |
| 5 | MEDIO | Rate limit por `X-Forwarded-For` não validado (spoofável) e in-memory (não distribuído). | `LoginRateLimitFilter.java:155` |
| 6 | MEDIO | Upload sem whitelist de MIME/extensão; `originalFilename` sem sanitização em path e `Content-Disposition`. | `AnexoService.java:42,50`, `AnexoController.java:56` |
| 7 | MEDIO | Actuator health expunha detalhes a anônimos (perfil vps). | `application-vps.properties` |
| 8 | BAIXO | JWT secret default fraco no `application.properties` base; DB password default `1234`. | `application.properties` |
| 9 | BAIXO | `EmailService` é stub: recuperação de senha não envia email real em produção. | `EmailService.java` |
| 10 | LGPD | Sem endpoint de exclusão de conta nem exportação dos dados do titular (art. 18 — eliminação/portabilidade). | `UsuarioController.java` (0 `DeleteMapping`) |
| 11 | LGPD | Sem registro de consentimento na coleta de nome/email. | `AuthController.register` |

### Pontos positivos (sem ação)

- Ownership/IDOR coberto: controllers usam `buscarPorIdDoUsuario(id, usuarioId)`; os `buscarPorId(id)` sem checagem são código morto (nenhum controller os chama).
- CSRF double-submit implementado (`RefreshTokenCsrfFilter`). Cookies `HttpOnly` + `Secure` (prod) + `SameSite=Lax`.
- Lockout por brute force (5 tentativas / 15 min) + rate limit por rota sensível.
- CORS via env, sem wildcard com `allowCredentials`.
- Entidade `Usuario` nunca exposta em respostas (hash/lockout protegidos).
- Email mascarado em logs (`EmailService.maskEmail`); token de reset não é logado.
- Mobile guarda tokens em `expo-secure-store` (Keychain/Keystore), não em storage inseguro.

## O que foi corrigido

- **#1 (ALTO) — logout-all / BUG-0047:**
  - `JwtAuthenticationFilter`: removido o early-return por prefixo `/api/auth/`. O filtro agora popula o `SecurityContext` sempre que houver Bearer token válido, independente do path. Rotas públicas seguem liberadas via `SecurityConfig` (permitAll), então a mudança é segura e sem efeito colateral em login/register/refresh.
  - `SecurityConfig`: adicionado `requestMatchers("/api/auth/logout-all").authenticated()` **antes** do `permitAll("/api/auth/**")` (ordem = specificity-first). Agora o endpoint exige token e o controller recebe `Authentication` não-nulo.
- **#3 — nada a fazer:** já implementado corretamente (`@ValidPassword` + `@NotBlank` em `novaSenha`). Finding original foi falso positivo (grep de verificação não cobriu a anotação). Documentado para rastreabilidade.
- **#7 (MEDIO) — actuator / BUG-0048:** `application-vps.properties` → `management.endpoint.health.show-details=when-authorized`. Anônimos veem só status; detalhes só autenticados. `prod` já era `never`; `dev` mantido `always` (local).

Validação: `mvn -o compile` → BUILD SUCCESS.

## O que ficou pendente

- **#2 (ALTO):** remover credenciais de prod do `application-prod.properties` e mover host/user para env; rotacionar credencial exposta e limpar histórico Git. Exige decisão de infra.
- **#4 (MEDIO):** hashear refresh token no DB (SHA-256) — requer migration + ajuste de lookup.
- **#5 (MEDIO):** confiar apenas no IP do proxy real (configurar `trusted proxies` / `ForwardedHeaderFilter`) e/ou mover rate limit para store distribuído.
- **#6 (MEDIO):** validar MIME/extensão do upload e sanitizar `originalFilename`.
- **#8/#9 (BAIXO):** endurecer defaults do `application.properties` base; implementar `JavaMailSender` real.
- **#10/#11 (LGPD):** endpoints de exclusão de conta e exportação de dados; registro de consentimento.

## Recomendacao final

Corrigidos os três itens solicitados sem introduzir gambiarra (fixes na causa raiz, compilando). Priorizar em seguida #2 (segredos) e #4–#6. Tratar #10/#11 antes de expor o app a titulares reais.

## Status final

PASS_COM_RESSALVA — #1, #3 (já ok) e #7 resolvidos e documentados; demais achados registrados como backlog.

---

> Relatorio gerado na auditoria de 2026-07-10. Correções: BUG-0047, BUG-0048.
