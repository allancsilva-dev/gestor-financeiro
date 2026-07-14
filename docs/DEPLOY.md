# 🚀 Guia de Deploy - Gestor Financeiro

Guia completo para deploy em produção do sistema Gestor Financeiro.

**Versão:** 1.5.0
**Última atualização:** 2026-07-08

---

## 📋 Índice

- [Visão Geral](#visão-geral)
- [Pré-requisitos](#pré-requisitos)
- [Opção 1: Stack Gratuita (Recomendado)](#opção-1-stack-gratuita-recomendado)
- [Opção 2: Stack Paga](#opção-2-stack-paga)
- [Preparação do Código](#preparação-do-código)
- [Deploy do Banco de Dados](#deploy-do-banco-de-dados)
- [Deploy do Backend](#deploy-do-backend)
- [Deploy do Frontend](#deploy-do-frontend)
- [Configurações Pós-Deploy](#configurações-pós-deploy)
- [Monitoramento](#monitoramento)
- [Troubleshooting](#troubleshooting)

---

## 🎯 Visão Geral

### **Stack Recomendada (100% Gratuita)**

| Componente | Serviço | Custo | Limite Gratuito |
|------------|---------|-------|----------------|
| **Backend** | Railway | R$ 0 | $5/mês crédito |
| **Frontend** | Vercel | R$ 0 | 100 GB bandwidth |
| **Banco** | Neon | R$ 0 | 10 GB storage |
| **TOTAL** | - | **R$ 0/mês** | Suficiente para testes |

### **Tempo Estimado**
- Preparação: 30 minutos
- Deploy: 20 minutos
- Testes: 10 minutos
- **Total: ~1 hora**

---

## 📦 Pré-requisitos

- [ ] Conta no GitHub
- [ ] Conta no Railway (criar em railway.app)
- [ ] Conta no Vercel (criar em vercel.com)
- [ ] Conta no Neon (criar em neon.tech)
- [ ] Código commitado no GitHub
- [ ] CI/CD pipeline configurado (`.github/workflows/ci.yml`)
- [ ] Variáveis de ambiente configuradas (usar `.env.example` como template)

---

## 🆓 Opção 1: Stack Gratuita (Recomendado)

### **1. Neon PostgreSQL (Banco de Dados)**

#### **Criar banco:**
1. Acesse: https://neon.tech
2. **Sign up** com GitHub
3. **Create a project** → `gestor-financeiro`
4. **Region:** US East (Ohio) - menor latência
5. Copie a **Connection String:**
   ```
   postgresql://user:password@ep-xxx.us-east-2.aws.neon.tech/neondb?sslmode=require
   ```

#### **Migrar dados (opcional):**
```bash
# Exportar banco local
pg_dump -U postgres gestor_financeiro > backup.sql

# Importar para Neon
psql "postgresql://user:password@host.neon.tech/neondb?sslmode=require" < backup.sql
```

---

### **2. Railway (Backend)**

#### **Deploy:**
1. Acesse: https://railway.app
2. **Login** com GitHub
3. **New Project** → **Deploy from GitHub repo**
4. Selecione: `gestor-financeiro`
5. **Root Directory:** `/backend`
6. **Build Command:** (deixe vazio, Railway detecta Maven)
7. **Start Command:** (deixe vazio)

#### **Configurar variáveis:**
1. Clique em **Variables**
2. Adicione:
   ```
   DATABASE_URL=postgresql://user:password@host.neon.tech/neondb?sslmode=require
   DB_USERNAME=seu_usuario
   DB_PASSWORD=sua_senha
   JWT_SECRET=gere-uma-chave-forte-aqui-minimo-32-caracteres
   SPRING_PROFILES_ACTIVE=prod
   PORT=8080
   FRONTEND_URL=https://seu-app.vercel.app
   ```

3. **Generate Domain** → Copie a URL gerada
   - Exemplo: `https://gestor-financeiro-production.up.railway.app`

#### **Deploy automático:**
- Railway faz deploy automaticamente a cada push no GitHub! ✅

---

### **3. Vercel (Frontend)**

#### **Deploy:**
1. Acesse: https://vercel.com
2. **Add New** → **Project**
3. **Import Git Repository** → Selecione `gestor-financeiro`
4. **Root Directory:** `frontend`
5. **Framework Preset:** Vite
6. **Build Command:** `npm run build`
7. **Output Directory:** `dist`

#### **Configurar variáveis:**
1. Clique em **Environment Variables**
2. Adicione:
   ```
   VITE_API_URL=https://seu-backend.railway.app/api
   ```

3. **Deploy**

#### **URL gerada:**
- `https://gestor-financeiro.vercel.app`

---

## 💰 Opção 2: Stack Paga

### **AWS (Produção escalável)**

| Componente | Serviço AWS | Custo Estimado |
|------------|-------------|----------------|
| Backend | Elastic Beanstalk | ~$15/mês |
| Frontend | S3 + CloudFront | ~$5/mês |
| Banco | RDS PostgreSQL | ~$20/mês |
| **TOTAL** | - | **~$40/mês** |

---

## 🔧 Preparação do Código

### **1. Backend - Criar Dockerfile**

**Localização:** `backend/Dockerfile`

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### **2. Frontend - Verificar build**

```bash
cd frontend
npm ci
npm run build

# Testar build localmente
npm run preview
```

O frontend inclui `vercel.json` com rewrites SPA configurados — todas as rotas redirecionam para `index.html`.

### **3. Atualizar CORS no Backend**

**SecurityConfig.java:**

```java
@Value("${cors.allowed.origins:http://localhost:5173}")
private String allowedOrigins;

@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
    // ...
}
```

### **4. Commitar mudanças**

```bash
git add .
git commit -m "chore: preparar para deploy em produção"
git push origin main
```

---

## 🗄️ Deploy do Banco de Dados

### **Neon PostgreSQL (Recomendado)**

#### **Passos:**
1. ✅ Criar projeto no Neon
2. ✅ Copiar connection string
3. ✅ Migrar dados (se necessário)
4. ✅ Testar conexão:

```bash
psql "postgresql://user:password@host.neon.tech/neondb?sslmode=require"
```

#### **Criar tabelas (primeira vez):**

As tabelas são criadas automaticamente pelo Flyway via migrations versionadas.
A aplicação executa as migrations em `db/migration/` no startup.

Para banco novo: o Flyway aplica `V1__baseline_schema.sql` e cria todas as tabelas.
Para banco existente com tabelas: use `spring.flyway.baseline-on-migrate=true` (padrão em dev).

#### **Migrations com Flyway**

O projeto usa Flyway para versionamento de schema. Migrations ficam em `backend/src/main/resources/db/migration/`.

**Criar nova migration:**
```sql
-- V2__descricao_da_mudanca.sql
ALTER TABLE transacoes ADD COLUMN nova_coluna VARCHAR(255);
```

**Rodar migrations manualmente:**
```bash
cd backend
./mvnw flyway:migrate
```

**Validar schema contra entidades JPA:**
```bash
./mvnw spring-boot:run  # Flyway roda migrations no startup
```
O Hibernate valida (`ddl-auto=validate`) se as entidades batem com o schema do banco.

**Resetar banco local:**
```bash
# Apagar e recriar
psql -U postgres -c "DROP DATABASE gestor_financeiro"
psql -U postgres -c "CREATE DATABASE gestor_financeiro"
# Rodar aplicação — Flyway cria tudo
```

---

## ☁️ Deploy do Backend

### **Railway (Passo a passo completo)**

#### **1. Conectar repositório:**
- New Project → Deploy from GitHub
- Selecione `gestor-financeiro`

#### **2. Configurar build:**
- Root Directory: `/backend`
- Build: Automático (Maven) com `Procfile` incluso no repositório

#### **3. Variáveis de ambiente:**
```env
DATABASE_URL=postgresql://...
DB_USERNAME=...
DB_PASSWORD=...
JWT_SECRET=... (gere em https://randomkeygen.com/)
SPRING_PROFILES_ACTIVE=prod
PORT=8080
FRONTEND_URL=https://seu-frontend.vercel.app
```

#### **4. Deploy:**
- Clique em **Deploy**
- Aguarde build (3-5 minutos)
- Copie a URL gerada

#### **5. Testar:**
```bash
curl https://seu-backend.railway.app/api/auth/login
```

---

## 🌐 Deploy do Frontend

### **Vercel (Passo a passo completo)**

#### **1. Importar projeto:**
- Add New → Project
- Import from GitHub: `gestor-financeiro`

#### **2. Configurar:**
- Root Directory: `frontend`
- Framework: Vite
- Build Command: `npm run build`
- Output Directory: `dist`

#### **3. Variável de ambiente:**
```env
VITE_API_URL=https://seu-backend.railway.app/api
```

#### **4. Deploy:**
- Deploy
- Aguarde (1-2 minutos)
- Acesse a URL gerada

#### **5. Domínio customizado (opcional):**
- Settings → Domains
- Add Domain: `meuapp.com`
- Configurar DNS

---

## ⚙️ Configurações Pós-Deploy

### **1. Atualizar CORS no backend:**

Adicione a URL do frontend nas variáveis:
```env
FRONTEND_URL=https://gestor-financeiro.vercel.app
```

### **2. Testar fluxo completo:**

- [ ] Login funciona
- [ ] Health check: `GET /actuator/health` retorna `{"status":"UP"}`
- [ ] Criar transação
- [ ] Dashboard carrega
- [ ] Gráficos aparecem
- [ ] Refresh token funciona
- [ ] Logout funciona
- [ ] CSRF: refresh/logout incluem header `X-CSRF-Token`

### **3. Criar usuário de teste:**

Use o próprio frontend em produção!

### **4. Monitorar logs:**

**Railway:**
- Clique no serviço → Deployments → View Logs

**Vercel:**
- Deployments → Ver detalhes → Functions

---

## 📊 Monitoramento

### Health Check

Backend expoe endpoint de health check via Spring Boot Actuator:

```bash
# Verificar status
curl https://seu-backend.railway.app/actuator/health

# Resposta (200 OK):
# {"status":"UP"}

# Script incluso no projeto:
./scripts/health-check.sh https://seu-backend.railway.app

# Se retornar DOWN, verificar banco de dados e logs
```

**Endpoint publico em producao:** `GET /actuator/health` (sem detalhes — `show-details=never`).
**Em dev:** `GET /actuator/health` + `GET /actuator/info` com detalhes completos.

O health check verifica:
- Conectividade com banco PostgreSQL (DataSourceHealthIndicator)
- Status da aplicacao (disk space, etc.)

### Metricas importantes

#### **Railway (Backend):**
- CPU Usage
- Memory Usage
- Request Count
- Response Time

#### **Vercel (Frontend):**
- Bandwidth Usage
- Build Time
- Function Invocations

#### **Neon (Banco):**
- Storage Used
- Active Connections
- Query Performance

### **Alertas:**

Configure alertas para:
- ❌ Aplicação offline
- ⚠️ CPU > 80%
- ⚠️ Memória > 80%
- ⚠️ Erros 500

---

## 💾 Backup e Restore

### Backup do banco Neon

Neon oferece **Point-in-Time Recovery (PITR)** gratuito no plano Free — restore automatizado para qualquer ponto nas ultimas 24h.

### Backup manual criptografado (qualquer PostgreSQL)

```bash
# Usando script do projeto
BACKUP_ENCRYPTION_PASSPHRASE='senha-forte-fora-do-repo' \
  ./scripts/backup-db.sh postgresql://user:pass@host:5432/db

# Ou via DATABASE_URL no ambiente
export DATABASE_URL="postgresql://..."
BACKUP_ENCRYPTION_PASSPHRASE='senha-forte-fora-do-repo' ./scripts/backup-db.sh
```

Backups salvos em `backups/` como `.sql.gz.gpg`. Mantidos ultimos 7. Backup sem criptografia é bloqueado por padrão; para banco local descartável, use `ALLOW_UNENCRYPTED_BACKUP=true`.

### Restore manual

```bash
BACKUP_ENCRYPTION_PASSPHRASE='senha-forte-fora-do-repo' \
  ./scripts/restore-db.sh backups/gestor_financeiro_20260101.sql.gz.gpg postgresql://user:pass@host:5432/db
```

Restore drill em banco descartável:

```bash
BACKUP_ENCRYPTION_PASSPHRASE='senha-forte-fora-do-repo' \
  ./scripts/restore-drill-db.sh backups/gestor_financeiro_20260101.sql.gz.gpg postgresql://user:pass@host:5432/db_descartavel
```

Apos restore, validar schema com Flyway:
```bash
cd backend && ./mvnw flyway:validate
```

### Backup via GitHub Actions

Workflow de CI pode ser estendido com job agendado (`schedule`). Configurar secret `DATABASE_URL` no GitHub para habilitar.

### Comandos de manutenção offline

Backfills não possuem endpoint HTTP. Perfil `maintenance`; dry-run padrão; relatório JSON obrigatório:

```bash
cd backend
SPRING_PROFILES_ACTIVE=maintenance DATABASE_URL=... DB_USERNAME=... DB_PASSWORD=... JWT_SECRET=... \
  ./mvnw spring-boot:run -Dspring-boot.run.arguments="--job=ledger-orphans --report=../artifacts/ledger-dry-run.json"

# somente após backup criptografado, restore drill e revisão do dry-run
SPRING_PROFILES_ACTIVE=maintenance DATABASE_URL=... DB_USERNAME=... DB_PASSWORD=... JWT_SECRET=... \
  ./mvnw spring-boot:run -Dspring-boot.run.arguments="--job=ledger-orphans --report=../artifacts/ledger-apply.json --apply"
```

Jobs: `ledger-orphans`, `rounding-residue`, `card-schedule`. Cada usuário roda em transação separada; relatórios usam somente IDs técnicos. Reexecução após `--apply` deve gerar zero mudanças. `REVISAO_MANUAL` permanece intacto.

Antes da Release B, execute `card-schedule` e confirme zero `sem_lancamento_canonico`. Só então promova `db/contract/V27__remove_redundant_card_parcels.sql` para `db/migration`.

SMTP opcional: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM`. Sem host, recuperação mantém resposta neutra e não envia mensagem.

---

## 🐛 Troubleshooting

### **Problema: Backend não inicia**

**Possíveis causas:**
1. Variável de ambiente faltando
2. Connection string do banco incorreta
3. JWT_SECRET inválido

**Solução:**
```bash
# Ver logs no Railway
railway logs

# Verificar variáveis
railway variables
```

### **Problema: CORS error no frontend**

**Causa:** URL do frontend não está no CORS

**Solução:**
```env
# No Railway, adicione:
FRONTEND_URL=https://seu-frontend.vercel.app
```

### **Problema: Banco de dados não conecta**

**Verificar:**
```bash
# Testar conexão
psql "sua-connection-string"

# Verificar se DATABASE_URL está correta
echo $DATABASE_URL
```

### **Problema: Frontend com 404**

**Causa:** Rotas do React não configuradas

**Solução (Vercel):**

Criar `vercel.json`:
```json
{
  "rewrites": [
    { "source": "/(.*)", "destination": "/index.html" }
  ]
}
```

### **Problema: Refresh token não funciona**

**Verificar:**
1. JWT_SECRET é o mesmo em dev e prod?
2. Cookie/localStorage está sendo salvo?
3. CORS permite credentials?

---

## 📱 Testes em Produção

### Release Android local

O release mobile é gerado a partir de `mobile/` após atualizar `expo.version` e
`android.versionCode`:

```bash
npx expo prebuild --no-install
cd android
ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew assembleRelease --no-daemon
```

Saída padrão: `mobile/android/app/build/outputs/apk/release/app-release.apk`.
Para o release 1.1.0, a cópia identificada é
`nexos-financas-1.1.0.apk`, SHA-256
`931f6754c9056239f3db9508dc2c47731317ac3eef29abf78d26ba2c65e47fc9`.

O `build.gradle` local ainda assina `release` com `signingConfigs.debug`, útil
somente para distribuição interna. Publicação externa deve usar o artifact do
workflow `.github/workflows/mobile-release.yml` e uma chave de distribuição
protegida; nunca versionar keystore ou senha no repositório.

### **Checklist:**

- [ ] Acessar URL do frontend
- [ ] Fazer cadastro
- [ ] Fazer login
- [ ] Criar transação
- [ ] Criar categoria
- [ ] Criar cartão
- [ ] Visualizar dashboard
- [ ] Aguardar 16 minutos (token expirar)
- [ ] Navegar (deve renovar automaticamente)
- [ ] Fazer logout
- [ ] Tentar acessar sem login (deve redirecionar)

---

## 🎯 Próximos Passos Após Deploy

1. ✅ Coletar feedback de usuários
2. ✅ Monitorar erros via logs do Railway/Vercel
3. ✅ Otimizar performance
4. ✅ Configurar backup automático do banco Neon
5. ✅ CI/CD ativo via GitHub Actions (`.github/workflows/ci.yml`)

---

## 📞 Suporte

**Problemas com deploy?**

1. Verifique os logs
2. Consulte a documentação oficial:
   - [Railway Docs](https://docs.railway.app)
   - [Vercel Docs](https://vercel.com/docs)
   - [Neon Docs](https://neon.tech/docs)
3. Abra uma [issue](https://github.com/seu-usuario/gestor-financeiro/issues)

---

**Última atualização:** 30/11/2025  
**Mantido por:** Zero (Allan Carvalho)
