# 💰 Gestor Financeiro Pessoal

Sistema web completo para controle financeiro pessoal com backend Spring Boot e frontend React.

## 🎯 Status do Projeto

### ✅ Backend (Concluído)
- [x] API REST com Spring Boot
- [x] Autenticação JWT completa
- [x] Criptografia de senha (BCrypt)
- [x] Rotas protegidas
- [x] Banco de dados PostgreSQL
- [x] Validação de dados
- [x] Tratamento de erros HTTP

### 🚧 Frontend (Em desenvolvimento)
- [x] Projeto React + TypeScript + Vite
- [x] Tailwind CSS configurado
- [ ] Página de Login
- [ ] Página de Dashboard
- [ ] Integração com backend

## 🛠️ Tecnologias

### Backend
- **Java 25**
- **Spring Boot 3.5.7**
- **Spring Security + JWT**
- **PostgreSQL 17**
- **Maven**

### Frontend
- **React 18 + TypeScript**
- **Vite 5**
- **Tailwind CSS 3**
- **Axios** (requisições HTTP)
- **React Router** (navegação)

## 📋 Pré-requisitos

- Java 17+
- Node.js 20+ LTS
- PostgreSQL 15+
- Maven 3.9+

## 🚀 Como Rodar

### Backend

1. **Configurar banco de dados:**
```bash
psql -U postgres
CREATE DATABASE gestor_financeiro;
\q
```

2. **Configurar credenciais:**

Edite `backend/src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/gestor_financeiro
spring.datasource.username=postgres
spring.datasource.password=SUA_SENHA_AQUI
```

3. **Rodar:**
```bash
cd backend
.\mvnw.cmd spring-boot:run
```

API rodando em: `http://localhost:8080`

### Frontend
```bash
cd frontend
npm install
npm run dev
```

App rodando em: `http://localhost:5173`

## 📡 Endpoints da API

### Autenticação (Públicos)

#### POST `/api/auth/register`
Cadastra novo usuário.

**Body:**
```json
{
  "nome": "Seu Nome",
  "email": "seu@email.com",
  "senha": "suasenha"
}
```

**Response (200):**
```json
{
  "id": 1,
  "nome": "Seu Nome",
  "email": "seu@email.com",
  "senha": "$2a$10$..."
}
```

#### POST `/api/auth/login`
Faz login e retorna token JWT.

**Body:**
```json
{
  "email": "seu@email.com",
  "senha": "suasenha"
}
```

**Response (200):**
```json
{
  "message": "Login realizado com sucesso!",
  "success": true,
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### Usuários (Protegidos - requer token)

#### GET `/api/usuarios/me`
Retorna dados do usuário autenticado.

**Headers:**
```
Authorization: Bearer SEU_TOKEN_AQUI
```

**Response (200):**
```json
{
  "id": 1,
  "nome": "Seu Nome",
  "email": "seu@email.com",
  "senha": null
}
```

## 🧪 Testando com Thunder Client / Postman

1. **Cadastrar usuário:** POST `/api/auth/register`
2. **Fazer login:** POST `/api/auth/login` (copie o token)
3. **Acessar rota protegida:** GET `/api/usuarios/me`
   - Header: `Authorization: Bearer SEU_TOKEN`

## 🔐 Segurança

- ✅ Senhas criptografadas com BCrypt (10 rounds)
- ✅ Tokens JWT com expiração de 24h
- ✅ Rotas protegidas por Spring Security
- ✅ Validação de dados no backend
- ✅ CORS configurado
- ✅ Stateless (sem sessão)

## 📂 Estrutura do Projeto
```
gestor-financeiro/
├── backend/
│   ├── src/main/java/com/gestor/financeiro/
│   │   ├── config/          # JWT, Security
│   │   ├── controller/      # Endpoints
│   │   ├── dto/            # Request/Response
│   │   ├── model/          # Entidades
│   │   └── repository/     # JPA
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── components/     # Componentes reutilizáveis
│   │   ├── pages/         # Páginas
│   │   ├── services/      # API calls
│   │   ├── types/         # TypeScript interfaces
│   │   └── context/       # Context API
│   └── package.json
└── README.md
```

## 🎯 Próximos Passos

- [ ] Criar página de Login no frontend
- [ ] Criar página de Dashboard
- [ ] Implementar CRUD de transações
- [ ] Criar gráficos de gastos
- [ ] Adicionar categorias e contas bancárias

## 👨‍💻 Autor

Projeto desenvolvido como estudo de Spring Boot, React e autenticação JWT.

## 📝 Licença

Projeto pessoal para fins educacionais.