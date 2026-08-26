#  Gestor Financeiro

Sistema fullstack de gestão financeira pessoal com foco em segurança, API versionada e suporte consistente para web e mobile.

![Version](https://img.shields.io/badge/version-2.0.0-blue.svg)
![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-green.svg)
![React](https://img.shields.io/badge/React-19-blue.svg)

---

##  Sobre o Projeto

Aplicação fullstack para controle financeiro com:
- autenticação JWT com rotação de refresh token
- contratos de sessão separados para web e mobile
- API versionada em `/api/v1`
- conta financeira, ledger e reconciliação
- cartões/faturas, metas, recorrências, orçamento e investimentos
- nove métricas oficiais com composição e drill-down
- clientes web e mobile

---

##  Estado atual

As Fases 0–3 da evolução de alto nível foram implementadas. O sistema está apto para
desenvolvimento, mas o deploy público permanece bloqueado pelos gates operacionais de backup
off-host, restore e reconciliação descritos em
[`docs/15 07 2026 - MetaDoNexosFinancas.md`](docs/15%2007%202026%20-%20MetaDoNexosFinancas.md).

### Segurança e integridade

- Refresh token em cookie HttpOnly (`/api/auth`)
- tokens mobile no Expo Secure Store, com recuperação de sessão expirada
- Detecção de reuso de refresh token com invalidação de sessão
- Rate limiting persistente em rotas de autenticação
- CORS centralizado + headers de segurança
- Validação de ownership para evitar IDOR
- operações financeiras transacionais, idempotentes e reconciliáveis

### Qualidade de API
- Responses de erro padronizadas com `ApiError`
- Validação de entrada com `@Valid` e DTOs de request
- OpenAPI/Swagger em `/swagger-ui.html`
- Actuator health em `/actuator/health`
- Endpoints versionados (`/api/v1/**`, exceto auth)

### Produto e UX
- Paginação em endpoints de listagem (`page`, `size`, `sort`)
- Hardening N+1 com `FetchType.LAZY` e `@EntityGraph`
- Frontend com `AbortController`, retry/backoff e ErrorBoundary
- Mobile Expo com tema claro/escuro/sistema, lançamento rápido e acessibilidade
- CSV, anexos, investimentos, recorrências e insights determinísticos

---

##  Tecnologias

**Backend:** Java 17, Spring Boot 3.5.16, Spring Security, Spring Data JPA, PostgreSQL, OpenAPI (springdoc), Actuator, Logback

**Frontend:** React 19, TypeScript 5.9, Vite 7, Axios, Tailwind CSS, Recharts

**Mobile:** React 19, React Native 0.81, Expo SDK 54, TanStack Query

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
./mvnw spring-boot:run
```

### Frontend
```bash
cd frontend
npm install
npm run dev
```

### Mobile
```bash
cd mobile
npm install
npm start
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
- `GET /api/v1/metricas`
- `GET /api/v1/compromissos`
- `GET /api/v1/transacoes/periodo?page=0&size=20`

Exemplo rápido:

```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"dev@example.com","password":"senha"}'

curl -H "Authorization: Bearer <ACCESS_TOKEN>" \
  http://localhost:8081/api/v1/metricas
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

Referências atuais:
- [docs/CHANGELOG.md](docs/CHANGELOG.md)
- [docs/15 07 2026 - MetaDoNexosFinancas.md](docs/15%2007%202026%20-%20MetaDoNexosFinancas.md)
- [docs/BACKLOG.md](docs/BACKLOG.md)
- [docs/SYSTEM_OVERVIEW.md](docs/SYSTEM_OVERVIEW.md)
