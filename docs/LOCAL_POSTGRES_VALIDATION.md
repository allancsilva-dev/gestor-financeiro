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

O profile `dev` esta configurado em `application-dev.properties` com as mesmas credenciais do Docker Compose.

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

No startup, o Flyway executa as migrations automaticamente em ordem:
1. `V1__baseline_schema.sql` — 10 tabelas do schema
2. `V2__optimistic_locking_columns.sql` — colunas version
3. `V3__performance_indexes.sql` — indices de performance
4. `V4__account_lockout.sql` — colunas failed_attempts e locked_until

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

## 6. Credenciais de acesso

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

## 7. Observacoes

- Profile `dev` usa `spring.flyway.baseline-on-migrate=true` para suportar banco existente com tabelas.
- Para banco limpo, o Flyway cria tudo; para banco ja populado, aplica apenas migrations pendentes.
- O volume `pgdata` mantem dados entre restarts. Use `down -v` para limpeza completa.
- Testes automatizados (`mvn test`) continuam usando H2 in-memory com profile `test`.

---

**Ultima atualizacao:** 2026-07-07 (PR-FOUNDATION-07)
