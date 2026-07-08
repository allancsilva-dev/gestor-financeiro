# Validacao Local com PostgreSQL

**Proposito:** instrucoes para validar a aplicacao contra PostgreSQL real usando Flyway em ambiente local de desenvolvimento.

**Aviso:** este documento cobre apenas validacao local de desenvolvimento. Nao contem instrucoes de deploy, CI/CD ou producao.

---

## 1. Pre-requisitos

- Docker e Docker Compose instalados (Docker Desktop no macOS/Windows, ou Docker Engine no Linux).
- JDK 17+
- Maven Wrapper (`./mvnw`)

---

## 2. Subir PostgreSQL local

```bash
# Na raiz do projeto:
docker compose up -d postgres
```

O container:
- Nome: `gf-postgres`
- Banco: `gestor_financeiro`
- Usuario: `postgres`
- Senha: `1234`
- Porta: `5432`
- Volume persistente: `pgdata` (dados sobrevivem a restarts do container)
- Healthcheck: `pg_isready` (5s intervalo, 5 tentativas)

Verificar status:
```bash
docker compose ps
```

Parar:
```bash
docker compose stop postgres
```

Remover tudo (inclui volume de dados):
```bash
docker compose down -v
```

---

## 3. Rodar aplicacao contra PostgreSQL local

O profile `dev` esta configurado em `application-dev.properties` com as mesmas credenciais do Docker Compose por padrao, mas aceita override por `DATABASE_URL`, `DB_USERNAME` e `DB_PASSWORD`.

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

No startup, o Flyway executa as migrations automaticamente em ordem:
1. `V1__baseline_schema.sql` — 10 tabelas do schema
2. `V2__optimistic_locking_columns.sql` — colunas version
3. `V3__performance_indexes.sql` — indices de performance
4. `V4__account_lockout.sql` — colunas failed_attempts e locked_until
5. `V5__onboarding_usuario.sql` — flag de onboarding
6. `V6__orcamento_mensal.sql` — orçamento mensal
7. `V7__transacao_conta_fixa.sql` — vínculo de transação com conta fixa
8. `V8__fatura_cartao.sql` — faturas de cartão
9. `V9__anexos.sql` — anexos
10. `V10__investimentos.sql` — investimentos
11. `V11__movimento_carteira.sql` — schema inicial do Ledger

O Hibernate valida (`ddl-auto=validate`) que as entidades JPA batem com o schema do banco.

---

## 4. Validar Flyway em banco limpo

Para testar migrations em banco vazio:

```bash
# Parar e remover container + volume
docker compose down -v

# Subir novamente (banco limpo)
docker compose up -d postgres

# Rodar aplicacao — Flyway cria schema do zero
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Verificar status do Flyway:

```bash
# Via logs da aplicacao (informacoes de migration aparecem no startup)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 | grep -i flyway
```

---

## 5. Resetar banco local

```bash
docker compose down -v   # Remove container e volume (banco zerado)
docker compose up -d      # Recria container com banco vazio
```

---

## 6. Rodar teste automatizado com PostgreSQL real

O PR-LEDGER-01 adicionou uma suíte de integração separada dos testes unitários. O PR-LEDGER-02 ampliou `PostgresMigrationIT` para validar a migration `V11__movimento_carteira.sql`, a tabela `movimentos_carteira`, constraint de valor e FK de carteira:

```bash
cd backend
./mvnw -q verify -Pintegration-test
```

Esse comando:

1. mantém os testes unitários no profile `test` com H2;
2. roda testes `*IT.java` pelo Maven Failsafe;
3. sobe PostgreSQL real via Testcontainers (`postgres:16-alpine`);
4. aplica Flyway em banco limpo;
5. valida o schema com Hibernate `ddl-auto=validate`;
6. consulta `flyway_schema_history` para confirmar migrations aplicadas;
7. valida constraints principais do schema Ledger.

Arquivos envolvidos:

| Arquivo | Função |
|---|---|
| `backend/pom.xml` | Dependências Testcontainers + profile Maven `integration-test` |
| `backend/src/test/java/com/gestor/financeiro/PostgresMigrationIT.java` | Teste de integração PostgreSQL real |
| `backend/src/test/resources/application-postgres-it.properties` | Profile com Flyway ativo e `ddl-auto=validate` |
| `.github/workflows/ci.yml` | Executa `mvn verify -Pintegration-test --batch-mode` |

Validação local em 2026-07-08:

```text
Cannot connect to the Docker daemon at unix:///Users/Zero/.docker/run/docker.sock. Is the docker daemon running?
```

Resultado: infraestrutura implementada e atualizada até `V11`, mas execução local bloqueada porque Docker daemon estava desligado. Para fechar a ressalva, iniciar Docker e rerodar `./mvnw -q verify -Pintegration-test`.

---

## 7. Validar com PostgreSQL da VPS

Profile `vps` aponta para o banco PostgreSQL principal na VPS Hostinger.

Dados conhecidos:

| Campo | Valor |
|---|---|
| Host | `187.77.61.191` |
| Porta | `5433` |
| Banco | `dbnexos-gestor-financeiro` |
| Usuario validado | `dbnexos_gestor` |
| Usuario antigo | `admin_nexos` — rejeitado em 2026-07-08 |

Senha nao deve ser registrada neste repositorio. Informe por variavel de ambiente:

```bash
cd backend
DATABASE_URL=jdbc:postgresql://187.77.61.191:5433/dbnexos-gestor-financeiro \
DB_USERNAME=admin_nexos \
DB_PASSWORD=SUA_SENHA \
JWT_SECRET=SUA_CHAVE_JWT_COM_32_BYTES_OU_MAIS \
./mvnw spring-boot:run -Dspring-boot.run.profiles=vps
```

Para o usuario validado em 2026-07-08:

```bash
cd backend
DATABASE_URL=jdbc:postgresql://187.77.61.191:5433/dbnexos-gestor-financeiro \
DB_USERNAME=dbnexos_gestor \
DB_PASSWORD=SUA_SENHA \
JWT_SECRET=SUA_CHAVE_JWT_COM_32_BYTES_OU_MAIS \
./mvnw spring-boot:run -Dspring-boot.run.profiles=vps \
  -Dspring-boot.run.arguments="--spring.main.web-application-type=none"
```

O startup deve:

1. conectar no PostgreSQL remoto;
2. executar Flyway;
3. validar schema com `ddl-auto=validate`;
4. expor `/actuator/health`.

Se o banco remoto ja tiver tabelas existentes, `spring.flyway.baseline-on-migrate=true` no profile `vps` permite baseline controlado.

Validacao executada em 2026-07-07:

```bash
nc -vz -w 5 187.77.61.191 5433
# Connection to 187.77.61.191 port 5433 [tcp/pyrrho] succeeded!
```

Resultado: porta TCP acessivel.

Validacao executada em 2026-07-07 com `DB_USERNAME=admin_nexos`:

```text
FATAL: password authentication failed for user "admin_nexos"
```

Resultado: servidor PostgreSQL acessivel, mas credencial rejeitada. Smoke Flyway/schema nao executou porque a conexao autenticada falhou.

Validacao executada em 2026-07-08 com `DB_USERNAME=dbnexos_gestor`:

```text
PostgreSQL 17.10
Successfully validated 14 migrations
Current version of schema "public": 14
Schema "public" is up to date. No migration necessary.
Started FinanceiroApplication
```

Resultado: smoke Flyway/schema concluido com sucesso. Durante a primeira tentativa autenticada foi encontrado BUG-0010 (`movimentos_carteira.moeda` como `CHAR(3)` no PostgreSQL e `VARCHAR(3)` no mapeamento JPA). A entidade `MovimentoCarteira` foi ajustada com `@JdbcTypeCode(SqlTypes.CHAR)` e a validacao VPS passou.

---

## 8. Credenciais de acesso local

| Campo | Valor |
|---|---|
| Host | `localhost` |
| Porta | `5432` |
| Banco | `gestor_financeiro` |
| Usuario | `postgres` |
| Senha | `1234` |

Conexao via psql:
```bash
psql -h localhost -p 5432 -U postgres -d gestor_financeiro
# senha: 1234
```

---

## 9. Observacoes

- Profile `dev` usa `spring.flyway.baseline-on-migrate=true` para suportar banco existente com tabelas.
- Profile `vps` usa `jdbc:postgresql://187.77.61.191:5433/dbnexos-gestor-financeiro` e `admin_nexos` por padrao. Exige `DB_PASSWORD` e `JWT_SECRET`.
- Para banco limpo, o Flyway cria tudo; para banco ja populado, aplica apenas migrations pendentes.
- Testes unitários (`mvn test`) continuam usando H2 in-memory com profile `test`.
- Testes de integração PostgreSQL real rodam com `mvn verify -Pintegration-test` e requerem Docker ativo.
- O volume `pgdata` mantem dados entre restarts. Use `down -v` para limpeza completa.

---

**Ultima atualizacao:** 2026-07-08 (BUG-0010, validacao VPS concluida)
