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

## 6. Validar com PostgreSQL da VPS

Profile `vps` aponta para o banco PostgreSQL principal na VPS Hostinger.

Dados conhecidos:

| Campo | Valor |
|---|---|
| Host | `187.77.61.191` |
| Porta | `5433` |
| Banco | `dbnexos-gestor-financeiro` |
| Usuario padrao | `admin_nexos` |

Senha nao deve ser registrada neste repositorio. Informe por variavel de ambiente:

```bash
cd backend
DATABASE_URL=jdbc:postgresql://187.77.61.191:5433/dbnexos-gestor-financeiro \
DB_USERNAME=admin_nexos \
DB_PASSWORD=SUA_SENHA \
JWT_SECRET=SUA_CHAVE_JWT_COM_32_BYTES_OU_MAIS \
./mvnw spring-boot:run -Dspring-boot.run.profiles=vps
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

Resultado: porta TCP acessivel. Smoke Flyway/schema ainda depende de usuario e senha.

Validacao executada em 2026-07-07 com `DB_USERNAME=admin_nexos`:

```text
FATAL: password authentication failed for user "admin_nexos"
```

Resultado: servidor PostgreSQL acessivel, mas credencial rejeitada. Smoke Flyway/schema nao executou porque a conexao autenticada falhou.

---

## 7. Credenciais de acesso local

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

## 8. Observacoes

- Profile `dev` usa `spring.flyway.baseline-on-migrate=true` para suportar banco existente com tabelas.
- Profile `vps` usa `jdbc:postgresql://187.77.61.191:5433/dbnexos-gestor-financeiro` e `admin_nexos` por padrao. Exige `DB_PASSWORD` e `JWT_SECRET`.
- Para banco limpo, o Flyway cria tudo; para banco ja populado, aplica apenas migrations pendentes.
- O volume `pgdata` mantem dados entre restarts. Use `down -v` para limpeza completa.
- Testes automatizados (`mvn test`) continuam usando H2 in-memory com profile `test`.

---

**Ultima atualizacao:** 2026-07-07 (PR-FOUNDATION-07)
