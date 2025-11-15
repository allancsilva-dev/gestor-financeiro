# 🏦 GESTOR FINANCEIRO - DOCUMENTAÇÃO COMPLETA

## 📋 ÍNDICE
1. [Visão Geral](#visão-geral)
2. [Tecnologias Utilizadas](#tecnologias-utilizadas)
3. [Estrutura do Projeto](#estrutura-do-projeto)
4. [O que JÁ FOI IMPLEMENTADO](#o-que-já-foi-implementado)
5. [O que FALTA IMPLEMENTAR](#o-que-falta-implementar)
6. [Como Rodar o Projeto](#como-rodar-o-projeto)
7. [Endpoints da API](#endpoints-da-api)
8. [Problemas Resolvidos](#problemas-resolvidos)
9. [Próximos Passos](#próximos-passos)

---

## 🎯 VISÃO GERAL

Sistema fullstack de controle financeiro pessoal com:
- Backend em **Java 25 + Spring Boot**
- Frontend em **React + TypeScript + Vite + Tailwind CSS**
- Banco de dados **PostgreSQL**
- Autenticação **JWT**

**Status atual:** Backend 60% completo, Frontend 10% completo

---

## 🛠️ TECNOLOGIAS UTILIZADAS

### Backend
- Java 25
- Spring Boot 3.5.7
- Spring Security 6.5.6
- Spring Data JPA
- PostgreSQL 42.7.8
- JWT (io.jsonwebtoken)
- Lombok
- Maven

### Frontend
- React 18
- TypeScript
- Vite
- Tailwind CSS
- React Router
- Axios
- React Hot Toast

### Ferramentas
- VS Code
- Thunder Client (testes de API)
- Git/GitHub
- PostgreSQL (localhost)

---

## 📁 ESTRUTURA DO PROJETO

```
gestor-financeiro/
├── backend/
│   ├── src/main/java/com/gestor/financeiro/
│   │   ├── config/
│   │   │   ├── JwtUtil.java ✅
│   │   │   ├── JwtAuthenticationFilter.java ✅
│   │   │   ├── SecurityConfig.java ✅
│   │   │   └── CustomUserDetailsService.java ✅
│   │   ├── controller/
│   │   │   ├── AuthController.java ✅
│   │   │   ├── UsuarioController.java ✅
│   │   │   ├── CategoriaController.java ✅
│   │   │   ├── ContaController.java ✅
│   │   │   ├── TransacaoController.java ✅
│   │   │   ├── ParcelaController.java ✅
│   │   │   ├── MetaController.java ✅
│   │   │   ├── ContaFixaController.java ✅
│   │   │   └── DashboardController.java ✅
│   │   ├── dto/
│   │   │   ├── LoginRequest.java ✅
│   │   │   ├── LoginResponse.java ✅
│   │   │   ├── CategoriaCreateRequest.java ✅
│   │   │   └── CategoriaUpdateRequest.java ✅
│   │   ├── model/
│   │   │   ├── enums/
│   │   │   │   ├── TipoTransacao.java ✅
│   │   │   │   ├── TipoConta.java ✅
│   │   │   │   └── StatusPagamento.java ✅
│   │   │   ├── Usuario.java ✅
│   │   │   ├── Categoria.java ✅
│   │   │   ├── Conta.java ✅
│   │   │   ├── Transacao.java ✅
│   │   │   ├── Parcela.java ✅
│   │   │   ├── Meta.java ✅
│   │   │   └── ContaFixa.java ✅
│   │   ├── repository/
│   │   │   ├── UsuarioRepository.java ✅
│   │   │   ├── CategoriaRepository.java ✅
│   │   │   ├── ContaRepository.java ✅
│   │   │   ├── TransacaoRepository.java ✅
│   │   │   ├── ParcelaRepository.java ✅
│   │   │   ├── MetaRepository.java ✅
│   │   │   └── ContaFixaRepository.java ✅
│   │   ├── service/
│   │   │   ├── CategoriaService.java ✅
│   │   │   ├── ContaService.java ✅
│   │   │   ├── TransacaoService.java ✅
│   │   │   ├── ParcelaService.java ✅
│   │   │   ├── MetaService.java ✅
│   │   │   ├── ContaFixaService.java ✅
│   │   │   └── DashboardService.java ✅
│   │   └── FinanceiroApplication.java ✅
│   └── src/main/resources/
│       └── application.properties ✅
├── frontend/
│   ├── src/
│   │   ├── components/ ✅
│   │   ├── contexts/ ✅
│   │   ├── pages/
│   │   │   ├── Login.tsx ✅
│   │   │   ├── Cadastro.tsx ✅
│   │   │   └── Dashboard.tsx ✅ (básico)
│   │   ├── services/
│   │   │   └── api.ts ✅
│   │   ├── types/ ✅
│   │   ├── App.tsx ✅
│   │   └── main.tsx ✅
│   ├── index.html ✅
│   ├── package.json ✅
│   ├── tailwind.config.js ✅
│   └── vite.config.ts ✅
└── README.md ✅
```

---

## ✅ O QUE JÁ FOI IMPLEMENTADO

### 🔐 Autenticação e Segurança (100% COMPLETO)
- [x] Sistema de cadastro de usuário
- [x] Sistema de login com JWT
- [x] Geração e validação de tokens JWT
- [x] Spring Security configurado
- [x] Filtro de autenticação JWT
- [x] UserDetailsService customizado
- [x] Proteção de rotas
- [x] CORS configurado
- [x] BCrypt para senhas
- [x] Usuário extraído do token (não do JSON)

### 📊 Entidades do Banco de Dados (100% COMPLETO)
- [x] Usuario (id, nome, email, senha)
- [x] Categoria (id, nome, cor, icone, valorEsperado, valorGasto, ativo, usuario_id)
- [x] Conta (id, nome, tipo, limiteTotal, valorGasto, saldoAtual, diaFechamento, diaVencimento, cor, ativo, usuario_id)
- [x] Transacao (id, descricao, valorTotal, tipo, data, status, parcelado, totalParcelas, valorParcela, observacoes, recorrente, usuario_id, conta_id, categoria_id)
- [x] Parcela (id, numeroParcela, totalParcelas, valor, dataVencimento, dataPagamento, status, transacao_id)
- [x] Meta (id, nome, valorTotal, valorReservado, valorMensal, dataInicio, dataPrevista, dataConclusao, ativa, cor, icone, descricao, usuario_id)
- [x] ContaFixa (id, nome, valorPlanejado, valorReal, diaVencimento, dataProximoVencimento, status, recorrente, ativo, observacoes, usuario_id, categoria_id)

### 🎨 Enums (100% COMPLETO)
- [x] TipoTransacao (ENTRADA, SAIDA)
- [x] TipoConta (CREDITO, DEBITO, DINHEIRO, POUPANCA)
- [x] StatusPagamento (PAGO, PENDENTE, ATRASADO, CANCELADO)

### 🔌 Repositories (100% COMPLETO)
- [x] UsuarioRepository
- [x] CategoriaRepository
- [x] ContaRepository
- [x] TransacaoRepository
- [x] ParcelaRepository
- [x] MetaRepository
- [x] ContaFixaRepository

### 💼 Services (100% COMPLETO)
- [x] CategoriaService (com getUsuarioLogado)
- [x] ContaService (adicionar/remover gastos)
- [x] TransacaoService (criação automática de parcelas)
- [x] ParcelaService (marcar como paga)
- [x] MetaService (adicionar/remover valores, calcular progresso)
- [x] ContaFixaService (calcular próximo vencimento)
- [x] DashboardService (resumo geral)

### 🌐 Controllers (100% COMPLETO)
- [x] AuthController (login, register)
- [x] UsuarioController
- [x] CategoriaController (TESTADO E FUNCIONANDO ✅)
- [x] ContaController
- [x] TransacaoController
- [x] ParcelaController
- [x] MetaController
- [x] ContaFixaController
- [x] DashboardController

### 📱 Frontend (10% COMPLETO)
- [x] Estrutura básica do projeto
- [x] Tela de Login (funcionando)
- [x] Tela de Cadastro (funcionando)
- [x] Dashboard básico (apenas estrutura)
- [x] Context API para autenticação
- [x] Integração com backend (Axios)
- [x] Proteção de rotas
- [x] Persistência de token

---

## ❌ O QUE FALTA IMPLEMENTAR

### 🧪 Backend - Testes (0%)
- [ ] Testar endpoint de Conta
- [ ] Testar endpoint de Transação
- [ ] Testar endpoint de Parcela
- [ ] Testar endpoint de Meta
- [ ] Testar endpoint de Conta Fixa
- [ ] Testar endpoint de Dashboard

### 📱 Frontend - Telas de Inserção (0%)
- [ ] Formulário de Entradas/Receitas
- [ ] Formulário de Categorias
- [ ] Formulário de Contas/Cartões
- [ ] Formulário de Gastos por Cartão
- [ ] Formulário de Compras Parceladas
- [ ] Formulário de Metas
- [ ] Formulário de Contas Fixas

### 📊 Frontend - Dashboard (0%)
- [ ] Cards de resumo (entradas, saídas, saldo)
- [ ] Gráfico de pizza (gastos por categoria)
- [ ] Gráfico de barras (comparativo mensal)
- [ ] Gráfico de linha (evolução)
- [ ] Lista de transações recentes
- [ ] Lista de contas a vencer
- [ ] Barras de progresso de metas
- [ ] Alertas de contas atrasadas

### 🎨 Frontend - Melhorias (0%)
- [ ] Responsividade mobile
- [ ] Modo escuro
- [ ] Validação de formulários
- [ ] Loading states
- [ ] Error handling
- [ ] Confirmações de exclusão
- [ ] Filtros (por data, categoria, etc)
- [ ] Paginação

### 🚀 Deploy (0%)
- [ ] Deploy do backend (Railway, Heroku, AWS)
- [ ] Deploy do frontend (Vercel, Netlify)
- [ ] Configurar variáveis de ambiente
- [ ] SSL/HTTPS

---

## 🚀 COMO RODAR O PROJETO

### Pré-requisitos
```bash
- Java 25 (ou superior)
- PostgreSQL instalado e rodando
- Node.js 18+ e npm
- Git
```

### 1. Configurar Banco de Dados

```sql
-- Abrir PostgreSQL
psql -U postgres

-- Criar banco de dados
CREATE DATABASE gestor_financeiro;
```

### 2. Rodar Backend

```bash
# Navegar para a pasta backend
cd D:\Projetos\gestor-financeiro\backend

# Rodar o projeto
.\mvnw.cmd spring-boot:run
```

**Backend rodando em:** `http://localhost:8081`

### 3. Rodar Frontend

```bash
# Navegar para a pasta frontend
cd D:\Projetos\gestor-financeiro\frontend

# Instalar dependências (só na primeira vez)
npm install

# Rodar o projeto
npm run dev
```

**Frontend rodando em:** `http://localhost:5173`

---

## 📡 ENDPOINTS DA API

### Base URL
```
http://localhost:8081/api
```

### 🔐 Autenticação (SEM TOKEN)

#### Cadastrar Usuário
```http
POST /api/auth/register
Content-Type: application/json

{
  "nome": "João Silva",
  "email": "joao@email.com",
  "senha": "123456"
}
```

#### Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "joao@email.com",
  "senha": "123456"
}

# Resposta:
{
  "mensagem": "Login realizado com sucesso!",
  "sucesso": true,
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### 📊 Categorias (COM TOKEN)

#### Listar minhas categorias
```http
GET /api/categorias/minhas
Authorization: Bearer {TOKEN}
```

#### Criar categoria
```http
POST /api/categorias
Authorization: Bearer {TOKEN}
Content-Type: application/json

{
  "nome": "Mercado",
  "cor": "#FF5733",
  "icone": "shopping-cart",
  "valorEsperado": 500.00
}
```

#### Atualizar categoria
```http
PUT /api/categorias/{id}
Authorization: Bearer {TOKEN}
Content-Type: application/json

{
  "nome": "Supermercado",
  "cor": "#FF5733",
  "icone": "shopping-cart",
  "valorEsperado": 600.00
}
```

#### Deletar categoria (inativa)
```http
DELETE /api/categorias/{id}
Authorization: Bearer {TOKEN}
```

### 💳 Contas (COM TOKEN)

#### Listar minhas contas
```http
GET /api/contas/usuario/{usuarioId}
Authorization: Bearer {TOKEN}
```

#### Criar conta
```http
POST /api/contas
Authorization: Bearer {TOKEN}
Content-Type: application/json

{
  "usuario": { "id": 1 },
  "nome": "Nubank",
  "tipo": "CREDITO",
  "limiteTotal": 3000.00,
  "diaFechamento": 10,
  "diaVencimento": 17,
  "cor": "#8B10AE"
}
```

### 💰 Transações (COM TOKEN)

#### Listar transações
```http
GET /api/transacoes/usuario/{usuarioId}
Authorization: Bearer {TOKEN}
```

#### Criar transação (compra parcelada)
```http
POST /api/transacoes
Authorization: Bearer {TOKEN}
Content-Type: application/json

{
  "usuario": { "id": 1 },
  "conta": { "id": 1 },
  "categoria": { "id": 1 },
  "descricao": "Monitor LG 29 polegadas",
  "valorTotal": 1000.00,
  "tipo": "SAIDA",
  "data": "2025-11-15",
  "parcelado": true,
  "totalParcelas": 10
}
```

### 🎯 Metas (COM TOKEN)

#### Listar metas
```http
GET /api/metas/usuario/{usuarioId}
Authorization: Bearer {TOKEN}
```

#### Criar meta
```http
POST /api/metas
Authorization: Bearer {TOKEN}
Content-Type: application/json

{
  "usuario": { "id": 1 },
  "nome": "Viagem para Europa",
  "valorTotal": 15000.00,
  "valorMensal": 500.00,
  "cor": "#3498DB",
  "icone": "plane"
}
```

#### Adicionar valor à meta
```http
PUT /api/metas/{id}/adicionar
Authorization: Bearer {TOKEN}
Content-Type: application/json

{
  "valor": 500.00
}
```

### 📅 Contas Fixas (COM TOKEN)

#### Listar contas fixas
```http
GET /api/contas-fixas/usuario/{usuarioId}
Authorization: Bearer {TOKEN}
```

#### Criar conta fixa
```http
POST /api/contas-fixas
Authorization: Bearer {TOKEN}
Content-Type: application/json

{
  "usuario": { "id": 1 },
  "nome": "Netflix",
  "valorPlanejado": 45.90,
  "diaVencimento": 5,
  "recorrente": true
}
```

### 📈 Dashboard (COM TOKEN)

#### Ver resumo geral
```http
GET /api/dashboard/resumo/{usuarioId}
Authorization: Bearer {TOKEN}

# Resposta:
{
  "totalEntradas": 5000.00,
  "totalSaidas": 3500.00,
  "saldo": 1500.00,
  "totalCategorias": 8,
  "totalContas": 3,
  "totalMetas": 2,
  "totalContasFixas": 5
}
```

---

## 🐛 PROBLEMAS RESOLVIDOS

### 1. Erro 403 Forbidden
**Problema:** Rotas protegidas retornando 403  
**Causa:** Spring Security bloqueando sem validar JWT  
**Solução:** Criado `CustomUserDetailsService` e configurado `AuthenticationProvider`

### 2. Porta 8080 ocupada
**Problema:** Backend não iniciava (porta ocupada)  
**Solução:** Mudado para porta 8081 em `application.properties`

### 3. Transient Entity Exception
**Problema:** Erro ao salvar categoria sem usuário válido  
**Causa:** Criando `new Usuario()` vazio ao invés de buscar do banco  
**Solução:** Buscar usuário pelo ID antes de associar à categoria

### 4. Usuário null ao criar categoria
**Problema:** `usuarioId` sendo enviado no JSON  
**Causa:** Má prática de segurança (usuário deve vir do token)  
**Solução:** Extrair usuário do `SecurityContext` no service

### 5. Campos null ao criar categoria
**Problema:** Nome, cor, etc chegando null no banco  
**Causa:** DTO sem getters/setters ou sem Lombok  
**Solução:** Usar `record` do Java 14+ para DTOs

### 6. Processo Java não encerrado
**Problema:** Porta ainda ocupada após parar backend  
**Causa:** VS Code não matou o processo corretamente  
**Solução:** `taskkill /F /IM java.exe` ou reiniciar

---

## 🎯 PRÓXIMOS PASSOS

### Curto Prazo (1-2 dias)
1. ✅ Testar CRUD de Conta
2. ✅ Testar CRUD de Transação (com parcelas)
3. ✅ Testar CRUD de Meta
4. ✅ Testar CRUD de Conta Fixa
5. ✅ Testar Dashboard

### Médio Prazo (3-5 dias)
1. 🎨 Criar telas de inserção de dados no frontend
2. 📊 Implementar dashboard com gráficos (Chart.js)
3. 🔍 Implementar filtros e busca
4. 📱 Tornar responsivo

### Longo Prazo (1-2 semanas)
1. 🚀 Deploy do backend
2. 🚀 Deploy do frontend
3. 📄 Criar documentação completa
4. 🎥 Gravar vídeo demo
5. 💼 Adicionar ao portfólio

---

## 📝 CONFIGURAÇÕES IMPORTANTES

### Backend - application.properties
```properties
spring.application.name=financeiro

# Banco de Dados
spring.datasource.url=jdbc:postgresql://localhost:5432/gestor_financeiro
spring.datasource.username=postgres
spring.datasource.password=1234

# JPA/Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# Porta
server.port=8081
```

### Frontend - vite.config.ts
```typescript
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173
  }
})
```

---

## 🔑 CREDENCIAIS PADRÃO DE TESTE

### Banco de Dados
```
Host: localhost
Port: 5432
Database: gestor_financeiro
Username: postgres
Password: 1234
```

### Usuário de Teste
```
Email: joao@email.com
Senha: 123456
```

---

## 📚 RECURSOS ÚTEIS

### Documentação
- [Spring Boot](https://spring.io/projects/spring-boot)
- [Spring Security](https://spring.io/projects/spring-security)
- [JWT](https://jwt.io/)
- [React](https://react.dev/)
- [TypeScript](https://www.typescriptlang.org/)
- [Tailwind CSS](https://tailwindcss.com/)

### Ferramentas
- [Thunder Client](https://www.thunderclient.com/)
- [Postman](https://www.postman.com/)
- [DBeaver](https://dbeaver.io/) (GUI para PostgreSQL)

---

## 👨‍💻 DESENVOLVEDOR

**Primeiro projeto fullstack**  
Java 25 + Spring Boot + React + TypeScript  
Novembro 2025

---

## 📄 LICENÇA

Este projeto é de código aberto para fins educacionais.

---

## 🎉 CONQUISTAS

- ✅ Primeiro backend Java completo
- ✅ Sistema de autenticação JWT do zero
- ✅ 7 entidades com relacionamentos
- ✅ 7 controllers REST funcionando
- ✅ CRUD completo testado (Categoria)
- ✅ Frontend integrado com backend
- ✅ Sistema versionado no Git

---

**Data de Criação:** 12/11/2025  
**Última Atualização:** 15/11/2025  
**Status:** Em Desenvolvimento Ativo 🚀
**Criador: Allan Carvalho(Zero)**

