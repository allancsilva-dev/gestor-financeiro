# Frontend — Gestor Financeiro

Instruções específicas do frontend do projeto `gestor-financeiro` (React + TypeScript + Vite + Tailwind).

## Pré-requisitos
- Node.js 18+
- npm 9+ (ou pnpm/yarn)

## Instalação
```bash
cd frontend
npm install
```

## Variáveis de ambiente
Crie um arquivo `.env` na pasta `frontend` (ou use `.env.local`) com as variáveis abaixo:

```
# URL base da API (ex.: http://localhost:8081)
VITE_API_URL=http://localhost:8081

# Porta de desenvolvimento (opcional)
VITE_PORT=5173
```

## Desenvolvimento
```bash
npm run dev
```
O frontend ficará disponível em `http://localhost:5173` por padrão.

## Build para produção
```bash
npm run build
npm run preview
```

## Observações
- Certifique-se que o backend esteja rodando em `VITE_API_URL` e que a variável `FRONTEND_URL` esteja registrada no backend (CORS).
- O projeto usa refresh token em cookie HttpOnly e access token em memória no cliente.

## Scripts úteis
- `npm run dev` — iniciar ambiente de desenvolvimento
- `npm run build` — gerar build de produção
- `npm run preview` — servir build localmente

## Deploy
Use Vercel/Netlify/Cloudflare Pages para hospedar o frontend. Configure a variável `VITE_API_URL` nas configurações de ambiente da plataforma.
