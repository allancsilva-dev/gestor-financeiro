# Gestor Financeiro — Backend

API REST do sistema de gestão financeira pessoal, construída com Spring Boot e PostgreSQL.

![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-green.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-blue.svg)

---

## ✅ Funcionalidades Implementadas

### Autenticação e Segurança
- [x] Registro de usuário com senha criptografada (BCrypt)
- [x] Login com geração de access token (JWT) + refresh token
- [x] Renovação automática de access token via refresh token
- [x] Logout com revogação de refresh token
- [x] Logout de todos os dispositivos
- [x] Recuperação de senha por email
- [x] Redefinição de senha com token

### Gestão Financeira
- [x] CRUD de transações (com suporte a parcelamento)
- [x] CRUD de categorias (com soft delete)
- [x] CRUD de carteiras
- [x] CRUD de contas
- [x] CRUD de contas fixas (com marcação de pagamento)
- [x] CRUD de metas financeiras (com controle de progresso)
- [x] Gerenciamento de parcelas

### Dashboard e Análises
- [x] Resumo financeiro (saldo, entradas, saídas do mês)
- [x] Gastos por categoria (dados para gráfico de pizza)
- [x] Evolução mensal — últimos 6 meses (dados para gráfico de linhas)
- [x] Comparação mensal (dados para gráfico de barras)

---

## 🛠️ Tecnologias

- Java 17
- Spring Boot 3.4.1
- Spring Security + JWT
- Spring Data JPA / Hibernate
- PostgreSQL 17
- BCrypt
- JavaMailSender (recuperação de senha)

---

## 📁 Estrutura do Projeto

```
src/main/java/com/gestor/financeiro/
├── config/
│   ├── CustomUserDetailsService.java
│   ├── JwtAuthenticationFilter.java
│   ├── JwtUtil.java
│   └── SecurityConfig.java
├── controller/
│   ├── AuthController.java
│   ├── CarteiraController.java
│   ├── CategoriaController.java
│   ├── ContaController.java
│   ├── ContaFixaController.java
│   ├── DashboardController.java
│   ├── MetaController.java
│   ├── ParcelaController.java
│   ├── TransacaoController.java
│   └── UsuarioController.java
├── dto/
├── model/
│   ├── enums/
│   └── (entidades JPA)
├── repository/
└── service/
```

---

## 🚀 Como rodar

### Pré-requisitos
- Java 17+
- Maven
- PostgreSQL 17

### Configuração

1. Clone o repositório e acesse a pasta do backend:
```bash
cd backend
```

2. Copie o arquivo de variáveis de ambiente:
```bash
cp .env.example .env
```

3. Edite o `.env` com suas configurações:
```env
DATABASE_URL=jdbc:postgresql://localhost:5432/gestor_financeiro
DB_USERNAME=seu_usuario
DB_PASSWORD=sua_senha
JWT_SECRET=seu_jwt_secret_aqui
```

Para usar o banco principal na VPS Hostinger, use o profile `vps`:

```env
SPRING_PROFILES_ACTIVE=vps
DATABASE_URL=jdbc:postgresql://187.77.61.191:5433/dbnexos-gestor-financeiro
DB_USERNAME=admin_nexos
DB_PASSWORD=sua_senha
JWT_SECRET=seu_jwt_secret_aqui
```

O profile padrão do backend é `vps`. Para desenvolvimento local com PostgreSQL local/Docker, defina:

```env
SPRING_PROFILES_ACTIVE=dev
```

> Para gerar um JWT Secret seguro: https://randomkeygen.com/

4. Crie o banco de dados no PostgreSQL:
```sql
CREATE DATABASE gestor_financeiro;
```

5. Execute a aplicação:
```bash
./mvnw.cmd spring-boot:run
```

A API estará disponível em: **http://localhost:8081**

---

## 📡 Endpoints

Documentação completa em [API.md](../docs/API.md)

### Resumo
| Grupo | Base Path |
|-------|-----------|
| Autenticação | `/api/auth` |
| Usuário | `/api/usuarios` |
| Dashboard | `/api/dashboard` |
| Transações | `/api/transacoes` |
| Categorias | `/api/categorias` |
| Carteiras | `/api/carteiras` |
| Contas | `/api/contas` |
| Contas Fixas | `/api/contas-fixas` |
| Metas | `/api/metas` |
| Parcelas | `/api/parcelas` |

---

## 🔐 Segurança

- Access token JWT expira em **15 minutos**
- Refresh token expira em **7 dias**
- Senhas criptografadas com **BCrypt**
- Secrets protegidos via **variáveis de ambiente**
- CORS configurado
- Soft delete nas categorias (dados preservados)

---

## 📊 Versão

**1.4.0** — Ver [CHANGELOG.md](../docs/CHANGELOG.md)

---

**Mantido por:** Zero (Allan Carvalho)  
**GitHub:** [@ZeroHardCore](https://github.com/ZeroHardCore)
