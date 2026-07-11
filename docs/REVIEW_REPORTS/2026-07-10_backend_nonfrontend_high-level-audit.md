# Relatorio de Revisao

**Arquivo:** 2026-07-10_backend_nonfrontend_high-level-audit.md

---

## Objetivo

Verificar o sistema completo excluindo frontend web, com foco em padrao de gestor financeiro pessoal de alto nivel: integridade financeira, seguranca, banco, backend, testes, importacao, investimentos, operacao e observabilidade.

## Escopo verificado

- Backend Spring Boot: controllers, services, repositories, DTOs, filtros de seguranca e handlers.
- Dominio financeiro: transacoes, carteiras, ledger, faturas/cartao, metas, orcamento, relatorios, projecoes, importacao CSV, investimentos e anexos.
- Banco: Flyway migrations, constraints, indices, schema financeiro.
- Infra/operacao: Docker, CI, scripts de backup/restore/health, profiles de configuracao.
- Testes backend: suite Maven unit/slice e tentativa de integration-test PostgreSQL via Testcontainers.

## Arquivos lidos

- `backend/pom.xml`
- `backend/src/main/java/com/gestor/financeiro/config/*`
- `backend/src/main/java/com/gestor/financeiro/controller/*`
- `backend/src/main/java/com/gestor/financeiro/service/*`
- `backend/src/main/java/com/gestor/financeiro/repository/*`
- `backend/src/main/java/com/gestor/financeiro/model/*`
- `backend/src/main/resources/application*.properties`
- `backend/src/main/resources/db/migration/*`
- `backend/Dockerfile`
- `.github/workflows/ci.yml`
- `docker-compose.yml`, `docker-compose.vps.yml`
- `scripts/backup-db.sh`, `scripts/restore-db.sh`, `scripts/health-check.sh`
- `docs/SYSTEM_OVERVIEW.md`, `docs/GESTOR_FINANCEIRO_ALTO_NIVEL_PROXIMOS_PASSOS.md`, `docs/BACKLOG.md`, `docs/PROBLEM_LEDGER.md`

## Comandos executados

| Comando | Resultado |
|---|---|
| `rg --files ...` | Mapa do backend/docs/infra coletado |
| `rg "findById|deleteById|findAll|@Autowired|ddl-auto|csrf|permitAll|@Transactional|@Version" ...` | Pontos de risco revisados |
| `cd backend && ./mvnw -q test` | PASS fora do sandbox: 95 testes, 0 falhas |
| `cd backend && ./mvnw -q verify -Pintegration-test` | FAIL por ambiente: Testcontainers nao encontrou Docker valido |
| `rg "PASSWORD|SECRET|TOKEN|DATABASE_URL|187\\.77|admin_nexos" ...` | Exposicao documental/configuracao revisada |

## Veredito

Base backend esta acima de MVP comum e ja tem pilares importantes: Flyway, ownership, JWT curto, refresh token rotativo, hash de refresh/reset token no banco, ledger com lock pessimista, reconciliação, anexos com validacao de assinatura, CI e testes relevantes.

Ainda nao esta no padrao de gestor financeiro de alto nivel. Nota tecnica aproximada:

- **Base backend:** 7/10
- **Confiabilidade financeira produto:** 5.5/10

Principal motivo: ainda existem caminhos que criam ou projetam dados financeiros fora do modelo contabil central, e alguns invariantes ainda dependem so do Java, nao do banco.

## Achados

| # | Severidade | Descricao | Evidencia |
|---|---|---|---|
| 1 | CRITICAL | Importacao CSV salva transacao direto no repository, pulando `TransacaoService`, ledger, fatura, `categoria.valorGasto` e `conta.valorGasto`. | `ImportService.java:75` |
| 2 | HIGH | Fatura/cartao ainda nao suporta pagamento parcial, fatura zero/negativa nem rollover explicito de credito. | `FaturaService.java:109-114`, BACKLOG-0049/0054 |
| 3 | HIGH | Banco nao protege invariantes financeiros basicos em tabelas centrais: valores positivos, parcelas validas, dias validos, enum/checks. | `V1__baseline_schema.sql:45-75` |
| 4 | HIGH | Unique de `fatura_lancamentos(fatura_id, transacao_id, parcela_numero)` nao garante unicidade para `parcela_numero NULL` no PostgreSQL. | `V17__fatura_lancamentos.sql:11` |
| 5 | HIGH | Relatorios e projecoes ainda carregam listas em memoria para top despesas, gastos por conta, contas fixas, parcelas e faturas. | `RelatorioService.java:52`, `ProjecaoService.java:84-112` |
| 6 | HIGH | Investimentos permite venda acima da posicao e pode gerar quantidade negativa/divisao indevida; modulo nao integra caixa/carteira. | `InvestimentoService.java:102-110` |
| 7 | MEDIUM | Rate limit e local/in-memory; perde efeito correto em multi-instancia e reinicio. | `LoginRateLimitFilter.java:50` |
| 8 | MEDIUM | `RefreshTokenCsrfFilter` pula CSRF para qualquer request com `X-Client-Type: mobile`; precisa threat model explicito e garantia de storage seguro no cliente. | `RefreshTokenCsrfFilter.java:45-47` |
| 9 | MEDIUM | Injeção por campo (`@Autowired`) aparece em grande parte do backend, reduzindo imutabilidade e testabilidade. | 135 usos em `backend/src/main/java` |
| 10 | MEDIUM | Teste PostgreSQL real via Testcontainers nao rodou localmente por Docker invalido. | `verify -Pintegration-test` -> `Could not find a valid Docker environment` |
| 11 | MEDIUM | Backup operacional existe, mas sem criptografia/restore drill automatizado documentado. | `scripts/backup-db.sh`, `docker-compose.vps.yml` |
| 12 | LOW | `application-dev.properties` usa SQL/debug verboso e defaults locais fracos por conveniencia; aceitavel em dev, perigoso se perfil errado subir. | `application-dev.properties:5-15` |
| 13 | LOW | Dockerfile faz `mvn clean package -DskipTests`; CI testa, mas build de imagem isolado nao barra regressao. | `backend/Dockerfile` |
| 14 | LOW | Documentos ainda citam host/user de VPS (`187.77.61.191`, `admin_nexos`); nao ha senha, mas aumenta superficie operacional. | `docs/LOCAL_POSTGRES_VALIDATION.md`, `backend/README-backend.md` |

## Pontos fortes

- Ledger com `PESSIMISTIC_WRITE`, `saldo_resultante`, `idempotency_key` opcional e reconciliacao.
- Ownership forte nos fluxos principais (`findByIdAndUsuarioId`, DTOs sem `usuario_id` vindo do client).
- Flyway com `ddl-auto=validate`.
- Auth acima de MVP: access token curto, refresh rotativo, reuse detection, tokens opacos com hash no banco.
- Upload de comprovantes com whitelist por extensao e magic bytes.
- Erros padronizados com `requestId`.
- Suite backend atual passou: 95 testes, 0 falhas.

## O que foi corrigido

Nada. Esta revisao apenas documenta problemas e proximos passos para posterior execucao dos fixes corretos.

## O que ficou pendente

- Corrigir todos os achados abertos registrados como `PROB-0049` a `PROB-0060`.
- Executar `verify -Pintegration-test` em ambiente com Docker valido ou CI, garantindo PostgreSQL real.
- Priorizar primeiro os problemas que podem corromper dinheiro: importacao, constraints DB, unique de fatura, investimentos e modelo de fatura/credito.

## Recomendacao final

Sequencia recomendada:

1. Fechar `PROB-0049`: importacao deve passar pelo mesmo command/service financeiro de uma transacao normal.
2. Fechar `PROB-0051` e `PROB-0052`: invariantes no banco e unique robusto em fatura.
3. Fechar `PROB-0050`: modelo formal de fatura parcial/credito/rollover.
4. Fechar `PROB-0053` e `PROB-0054`: performance e investimentos.
5. Depois endurecer operacao: rate limit distribuido, backups criptografados, Testcontainers rodando, Dockerfile sem skip-tests ou com gate claro.

## Status final

PASS_COM_RESSALVA — backend tem boa fundacao, mas ainda nao atinge padrao alto nivel por riscos de integridade financeira e operacao.

---

> Relatorio gerado na auditoria de 2026-07-10. Itens registrados em `PROBLEM_LEDGER.md` e `BACKLOG.md`.
