# 📝 Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/),
e este projeto adere ao [Versionamento Semântico](https://semver.org/lang/pt-BR/).

---

## [1.4.0] - 2025-11-30

### 🔐 Segurança
- **[CRÍTICO]** Movido JWT secret para variável de ambiente
- **[CRÍTICO]** Reduzido tempo de expiração do access token de 24h para 15min
- **[CRÍTICO]** Protegidas credenciais do banco com variáveis de ambiente
- Adicionado `.env` no `.gitignore`
- Criado `application-prod.properties` para produção
- Criado `.env.example` como template
- Removidos logs com informações sensíveis

### ✅ Validações
- Sistema validado e pronto para deploy em produção
- Todos os secrets protegidos
- CORS configurado para produção

---

## [1.3.0] - 2025-11-30

### ⭐ Features
- **Refresh Token implementado** (auto-renovação de sessão)
- Access token expira em 15 minutos
- Refresh token expira em 7 dias
- Renovação automática transparente para o usuário
- Logout revoga tokens no backend

### 🗄️ Banco de Dados
- Criada tabela `refresh_tokens`
- Índices para otimização de queries
- Foreign key com cascade delete

### 🔧 Backend
- Criado `RefreshToken` entity
- Criado `RefreshTokenRepository`
- Criado `RefreshTokenService`
- Atualizado `AuthController` com novos endpoints:
  - `POST /api/auth/refresh-token` - Renovar access token
  - `POST /api/auth/logout` - Revogar refresh token
  - `POST /api/auth/logout-all` - Revogar todos os tokens

### 💻 Frontend
- Atualizado `authService` para salvar refresh token
- Implementado interceptor Axios para renovação automática
- Atualizado `AuthContext` para renovar token ao inicializar
- Adicionado `refreshToken` em `LoginResponse` type

### 📚 Documentação
- Criado `LICOES_APRENDIDAS.md` com debugging experiences
- Atualizado README com refresh token

---

## [1.2.0] - 2025-11-29

### 📊 Dashboard
- Implementados gráficos com Recharts
- Gráfico de pizza (Gastos por Categoria)
- Gráfico de linhas (Evolução Mensal)
- Cards de resumo financeiro
- Cards secundários (Cartões, Metas, Contas Fixas)

### 🐛 Correções
- Corrigido Lazy Loading do JPA/Hibernate
  - Adicionado `JOIN FETCH` em queries customizadas
  - Categorias agora carregam corretamente nas transações
- Corrigido cache do Vite com prop `chartData`
- Corrigido layout dos gráficos (grid responsivo)
- Corrigido usuário hardcoded em todas as telas

### 🔧 Backend
- Criado `DashboardController`
- Criado `DashboardService` com cálculos de:
  - Saldo total de carteiras
  - Total de entradas/saídas do mês
  - Gastos por categoria
  - Evolução mensal (6 meses)
  - Comparação mensal
- Query customizada no `TransacaoRepository`

### 💻 Frontend
- Criado componente `Dashboard`
- Criado `GraficoGastosPorCategoria`
- Criado `GraficoEvolucaoMensal`
- Criado `dashboardService`
- Implementado `useAuth()` em TODAS as páginas

---

## [1.1.0] - 2025-11-28

### ⭐ Features
- Sistema de Autenticação JWT completo
- Recuperação de senha por email
- Gestão de transações (criar, editar, deletar)
- Parcelamento de compras
- Categorias personalizadas (cores e ícones)
- Controle de cartões de crédito
- Gestão de metas financeiras
- Contas fixas mensais

### 🔧 Backend
- Spring Security configurado
- JWT authentication filter
- BCrypt para senhas
- Soft delete para categorias
- Validação de proprietário (usuário só vê seus dados)

### 💻 Frontend
- AuthContext com Context API
- Rotas protegidas
- Interceptor Axios para token
- Notificações toast
- UI responsiva com Tailwind

### 🗄️ Banco de Dados
- Tabelas: usuarios, categorias, transacoes, contas, metas, contas_fixas
- Relacionamentos JPA configurados
- Índices para performance

---

## [1.0.0] - 2025-11-25

### 🎉 Lançamento Inicial
- Estrutura básica do projeto
- Configuração Spring Boot
- Configuração React + Vite
- PostgreSQL configurado
- Primeiras telas de Login e Registro

---

## 📊 Estatísticas de Desenvolvimento

### Versão 1.4.0 (Atual)
- **Tempo de desenvolvimento:** ~15 horas
- **Commits:** 50+
- **Arquivos modificados:** 30+
- **Linhas de código:** ~8.000+
- **Problemas resolvidos:** 10+

### Principais Desafios
1. **Lazy Loading JPA** (~2h de debugging)
2. **Cache do Vite** (~1.5h)
3. **Usuário hardcoded** (~1h)
4. **Layout dos gráficos** (~30min)
5. **Refresh token implementation** (~3h)

---

## 🎯 Próximas Versões

Ver [PROXIMOS_PASSOS.md](./PROXIMOS_PASSOS.md)

### v1.5.0 (Planejado)
- Skeleton Loaders
- Filtros no Dashboard
- Rate Limiting
- Validações de entrada

### v2.0.0 (Futuro)
- Dark/Light mode
- Exportação CSV/PDF
- Notificações push
- App mobile (React Native)

---

## 📝 Notas de Versão

### [1.4.0] - Segurança
Esta versão foca em **segurança e preparação para produção**. Todas as vulnerabilidades críticas foram corrigidas e o sistema está pronto para deploy.

### [1.3.0] - Refresh Token
Implementação do sistema de **refresh token** para melhorar a experiência do usuário, permitindo sessões de até 7 dias sem necessidade de novo login.

### [1.2.0] - Dashboard
Implementação completa do **dashboard com gráficos** e correção de bugs críticos de Lazy Loading e cache.

### [1.1.0] - MVP
Primeira versão funcional completa com todas as funcionalidades principais implementadas.

### [1.0.0] - Fundação
Estrutura básica do projeto e configurações iniciais.

---

**Última atualização:** 30/11/2025  
**Mantido por:** Zero (Allan Carvalho)
