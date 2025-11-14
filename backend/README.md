# Gestor Financeiro - Backend

Sistema de gestão financeira pessoal com Spring Boot e PostgreSQL.

## ✅ Funcionalidades Implementadas

- [x] Cadastro de usuário (POST /api/auth/register)
- [ ] Login com JWT
- [ ] Criptografia de senha (BCrypt)
- [ ] CRUD de transações

## 🛠️ Tecnologias

- Java 25
- Spring Boot 3.2
- PostgreSQL 17
- Spring Data JPA
- Spring Security

## 🚀 Como rodar

1. Clonar o repositório
2. Configurar PostgreSQL (banco: gestor_financeiro)
3. Ajustar application.properties
4. Executar: `.\mvnw.cmd spring-boot:run`

## 📡 Endpoints

### POST /api/auth/register
Cadastra novo usuário.

**Body:**
```json
{
  "nome": "Seu Nome",
  "email": "seu@email.com",
  "senha": "suasenha"
}
```

**Response:**
```json
{
  "id": 1,
  "nome": "Seu Nome",
  "email": "seu@email.com",
  "senha": "suasenha"
}
```

✅ Setup inicial e Git                    ← CONCLUÍDO!
✅ Criar backend Spring Boot              ← CONCLUÍDO!
✅ Configurar PostgreSQL                  ← CONCLUÍDO!
✅ Criar entidade Usuario                 ← CONCLUÍDO!
✅ Criar endpoint /register               ← CONCLUÍDO!
✅ Criptografar senha (BCrypt)            ← CONCLUÍDO!
✅ Criar endpoint /login                  ← CONCLUÍDO!
✅ Tratamento de erros HTTP               ← CONCLUÍDO!
⬜ Implementar JWT (token)                ← PRÓXIMO!
⬜ Proteger rotas privadas
⬜ Criar outras entidades (Transação, Categoria, Conta)
⬜ Frontend React