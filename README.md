#  Gestor Financeiro

Sistema fullstack de gestão financeira pessoal com foco em segurança, API versionada e suporte consistente para web e mobile.

![Version](https://img.shields.io/badge/version-2.0.0-blue.svg)
![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-green.svg)
![React](https://img.shields.io/badge/React-18-blue.svg)

---

##  Sobre o Projeto

Aplicação completa para controle financeiro com:
- autenticação JWT com rotação de refresh token
- refresh token em cookie HttpOnly
- API versionada em `/api/v1`
- paginação padrão em listagens
- dashboard com indicadores financeiros

---

##  Destaques da Versão 2.0.0

### Segurança
- Refresh token em cookie HttpOnly (`/api/auth`)
- Detecção de reuso de refresh token com invalidação de sessão
- Rate limiting em login e forgot-password
- CORS centralizado + headers de segurança
- Validação de ownership para evitar IDOR

### Qualidade de API
- Responses de erro padronizadas com `ApiError`
- Validação de entrada com `@Valid` e DTOs de request
- OpenAPI/Swagger em `/swagger-ui.html`
- Actuator health em `/actuator/health`
- Endpoints versionados (`/api/v1/**`, exceto auth)

### Performance e UX
- Paginação em endpoints de listagem (`page`, `size`, `sort`)
- Hardening N+1 com `FetchType.LAZY` e `@EntityGraph`
- Frontend com `AbortController`, retry/backoff e ErrorBoundary

---

##  Tecnologias

**Backend:** Java 17, Spring Boot 3.5.7, Spring Security, Spring Data JPA, PostgreSQL, OpenAPI (springdoc), Actuator, Logback

**Frontend:** React 18, TypeScript, Vite, Axios, Tailwind CSS, Recharts

---

##  Execução Local

### Pré-requisitos
- Java 17+
- Node.js 18+
- PostgreSQL 17+

### Backend
```bash
cd backend
cp .env.example .env
# configure DATABASE_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET
./mvnw.cmd spring-boot:run
```

### Frontend
```bash
cd frontend
npm install
npm run dev
```

URLs locais:
- Backend: `http://localhost:8081`
- Frontend: `http://localhost:5173`

---

##  API

Documentação completa: [backend/API.md](backend/API.md)

Endpoints principais:
- `POST /api/auth/login`
- `POST /api/auth/refresh-token`
- `GET /api/v1/dashboard/resumo`
- `GET /api/v1/transacoes/minhas?page=0&size=20`

Exemplo rápido:

```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"dev@example.com","password":"senha"}'

curl -H "Authorization: Bearer <ACCESS_TOKEN>" \
  http://localhost:8081/api/v1/dashboard/resumo
```

---

##  Deploy

Guia: [docs/DEPLOY.md](docs/DEPLOY.md)

Stack recomendada:
- Backend: Railway/Render
- Frontend: Vercel
- Banco: Neon PostgreSQL

---

##  Versão

**2.0.0**  API preparada para web + mobile com foco em segurança e estabilidade.

Referências:
- [docs/CHANGELOG.md](docs/CHANGELOG.md)
- [docs/PROXIMOS_PASSOS.md](docs/PROXIMOS_PASSOS.md)
