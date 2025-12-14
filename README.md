# 💰 Gestor Financeiro

Sistema completo de gestão financeira pessoal com controle de transações, categorias, metas e análises gráficas.

![Version](https://img.shields.io/badge/version-1.4.0-blue.svg)
![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-green.svg)
![React](https://img.shields.io/badge/React-18-blue.svg)

---

## 🎯 Sobre o Projeto

Aplicação fullstack para controle financeiro pessoal com:
- ✅ Autenticação JWT com refresh token
- ✅ Dashboard com gráficos interativos
- ✅ Controle de transações e categorias
- ✅ Gestão de cartões e metas
- ✅ Análises financeiras em tempo real

---

## ⚡ Funcionalidades Principais

### **Segurança**
- 🔐 JWT + Refresh Token (renovação automática)
- 🔒 Senhas criptografadas (BCrypt)
- ⏱️ Access token: 15min | Refresh token: 7 dias

### **Gestão Financeira**
- 💸 Transações (entrada/saída)
- 🏷️ Categorias personalizadas
- 💳 Múltiplos cartões
- 🎯 Metas financeiras
- 🔢 Parcelamento de compras

### **Análises**
- 📊 Dashboard interativo
- 🍰 Gastos por categoria (pizza)
- 📈 Evolução mensal (linha)
- 💰 Resumo financeiro completo

---

## 🛠️ Tecnologias

**Backend:** Java 17 • Spring Boot 3.4.1 • PostgreSQL 17 • Spring Security • JWT

**Frontend:** React 18 • TypeScript • Vite • Tailwind CSS • Recharts

---

## 🚀 Começando

### Pré-requisitos
- Java 17+
- Node.js 18+
- PostgreSQL 17+

### Instalação

```bash
# Clone
git clone https://github.com/ZeroHardCore/gestor-financeiro.git
cd gestor-financeiro

# Backend
cd backend
cp .env.example .env
# Edite .env com suas configurações:
# DATABASE_URL, DB_USERNAME, DB_PASSWORD
# JWT_SECRET (gere em https://randomkeygen.com/)
./mvnw.cmd spring-boot:run

# Frontend
cd frontend
npm install
npm run dev
```

**Backend:** http://localhost:8081  
**Frontend:** http://localhost:5173

---

## 📁 Estrutura

```
gestor-financeiro/
├── backend/           # Spring Boot API
├── frontend/          # React App
└── docs/              # Documentação
```

---

## 🔐 Segurança

✅ JWT com renovação automática  
✅ Senhas BCrypt  
✅ Secrets em variáveis de ambiente  
✅ CORS configurado  
✅ Soft delete (dados preservados)

---

## 📡 API Endpoints

Ver documentação completa em [DOCS.md](./docs/API.md)

Principais endpoints:
- `POST /api/auth/login` - Login
- `POST /api/auth/refresh-token` - Renovar token
- `GET /api/dashboard/resumo` - Dashboard
- `GET /api/transacoes/usuario/{id}` - Transações

---

## 🌐 Deploy

Ver guia completo: [DEPLOY.md](./docs/DEPLOY.md)

**Stack recomendada (gratuita):**
- Backend: Railway ou Render
- Frontend: Vercel
- Banco: Neon PostgreSQL

---

## 👤 Autor

**Zero (Allan Carvalho)**
- GitHub: [@ZeroHardCore](https://github.com/ZeroHardCore)

---

## 📊 Versão

**1.4.0** - Pronto para deploy

Ver: [CHANGELOG.md](./docs/CHANGELOG.md) | [PROXIMOS_PASSOS.md](./docs/PROXIMOS_PASSOS.md)

---

⭐ Dê uma estrela se este projeto te ajudou!
