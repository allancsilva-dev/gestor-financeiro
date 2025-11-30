# 🎯 Próximos Passos

Roadmap e planejamento de funcionalidades futuras do Gestor Financeiro.

**Última atualização:** 30/11/2025  
**Versão atual:** 1.4.0

---

## 📋 Índice

- [Prioridade Alta](#prioridade-alta-v150)
- [Prioridade Média](#prioridade-média-v160)
- [Prioridade Baixa](#prioridade-baixa-v170)
- [Futuro Distante](#futuro-distante-v200)
- [Melhorias Técnicas](#melhorias-técnicas)
- [Backlog](#backlog)

---

## 🔴 Prioridade ALTA (v1.5.0)

### **1. Deploy em Produção** 🚀
**Status:** ⏳ Pronto para iniciar  
**Tempo estimado:** 1-2 horas  
**Prioridade:** CRÍTICA

**Tarefas:**
- [ ] Deploy do backend no Railway
- [ ] Deploy do frontend no Vercel
- [ ] Migrar banco para Neon PostgreSQL
- [ ] Configurar variáveis de ambiente
- [ ] Testar em produção
- [ ] Configurar domínio customizado (opcional)

**Benefício:** Permitir feedback de usuários reais

---

### **2. Remover Logs de Debug** 🔧
**Status:** ⏳ Pendente  
**Tempo estimado:** 15 minutos  
**Prioridade:** ALTA

**Arquivos:**
- `RefreshTokenService.java`
- `AuthController.java`
- `DashboardService.java`

**Ação:** Remover `System.out.println` com dados sensíveis

---

### **3. Rate Limiting** 🛡️
**Status:** ⏳ Pendente  
**Tempo estimado:** 1 hora  
**Prioridade:** ALTA (Segurança)

**Funcionalidade:**
- Limitar tentativas de login por IP
- Proteção contra brute force
- Configurar limite: 5 tentativas / 15 minutos

**Tecnologia:** Spring Boot + Bucket4j ou Resilience4j

---

### **4. Skeleton Loaders** ✨
**Status:** ⏳ Pendente  
**Tempo estimado:** 45 minutos  
**Prioridade:** ALTA (UX)

**Onde aplicar:**
- Dashboard (cards e gráficos)
- Lista de transações
- Lista de categorias
- Lista de metas

**Tecnologia:** Tailwind CSS + animações

---

## 🟡 Prioridade MÉDIA (v1.6.0)

### **5. Filtros no Dashboard** 🔍
**Status:** ⏳ Planejado  
**Tempo estimado:** 2-3 horas

**Funcionalidades:**
- Filtrar por mês/período
- Filtrar por categoria
- Filtrar por tipo (entrada/saída)
- Filtrar por cartão

---

### **6. Exportar Dados (CSV/PDF)** 📥
**Status:** ⏳ Planejado  
**Tempo estimado:** 2 horas

**Funcionalidades:**
- Exportar transações em CSV
- Exportar relatório mensal em PDF
- Exportar gráficos

**Tecnologia:** Apache POI (Java) ou jsPDF (React)

---

### **7. Edição de Transações** ✏️
**Status:** ⏳ Planejado  
**Tempo estimado:** 1-2 horas

**Funcionalidade:**
- Botão "Editar" já existe
- Implementar lógica de atualização
- Validações de edição

---

### **8. Terceiro Gráfico (Comparação Mensal)** 📊
**Status:** ✅ Backend pronto | ⏳ Frontend pendente  
**Tempo estimado:** 30 minutos

**Funcionalidade:**
- Endpoint já existe no backend
- Criar componente React
- Integrar no Dashboard

---

## 🟢 Prioridade BAIXA (v1.7.0)

### **9. Dark/Light Mode** 🌓
**Status:** ⏳ Planejado  
**Tempo estimado:** 1-2 horas

**Funcionalidade:**
- Toggle no header
- Salvar preferência no localStorage
- Animação suave de transição

---

### **10. Notificações Push** 🔔
**Status:** ⏳ Planejado  
**Tempo estimado:** 3-4 horas

**Funcionalidades:**
- Notificar vencimento de contas
- Notificar metas próximas de serem atingidas
- Notificar gastos acima do esperado

---

### **11. Categorias Pré-definidas** 🏷️
**Status:** ⏳ Planejado  
**Tempo estimado:** 1 hora

**Funcionalidade:**
- Sugerir categorias comuns no primeiro acesso
- Usuário pode aceitar ou criar as suas

**Categorias sugeridas:**
- Alimentação, Transporte, Lazer, Saúde, Educação, etc.

---

### **12. Busca de Transações** 🔎
**Status:** ⏳ Planejado  
**Tempo estimado:** 1 hora

**Funcionalidade:**
- Buscar por descrição
- Buscar por valor
- Buscar por data
- Buscar por categoria

---

## 🔮 Futuro Distante (v2.0.0)

### **13. App Mobile (React Native)** 📱
**Status:** 💡 Ideia  
**Tempo estimado:** 2-3 semanas

**Funcionalidades:**
- Mesmas do web
- Notificações push nativas
- Câmera para escanear notas fiscais (OCR)

---

### **14. Multi-moeda** 💱
**Status:** 💡 Ideia  
**Tempo estimado:** 1 semana

**Funcionalidade:**
- Suporte a múltiplas moedas
- Conversão automática
- Gráficos em moeda preferida

---

### **15. Compartilhamento de Despesas** 👥
**Status:** 💡 Ideia  
**Tempo estimado:** 2 semanas

**Funcionalidade:**
- Dividir despesas com outras pessoas
- Controle de "quem deve para quem"
- Integração com PIX

---

### **16. Investimentos** 📈
**Status:** 💡 Ideia  
**Tempo estimado:** 3 semanas

**Funcionalidades:**
- Controle de ações
- Controle de criptomoedas
- Rendimentos de renda fixa
- Gráficos de evolução patrimonial

---

### **17. Inteligência Artificial** 🤖
**Status:** 💡 Ideia  
**Tempo estimado:** 1 mês

**Funcionalidades:**
- Sugestões inteligentes de economia
- Previsão de gastos futuros
- Detecção de gastos anormais
- Recomendações personalizadas

---

## 🔧 Melhorias Técnicas

### **Backend**
- [ ] Adicionar validações com `@Valid`
- [ ] Implementar testes unitários (JUnit + Mockito)
- [ ] Implementar testes de integração
- [ ] Adicionar Swagger/OpenAPI
- [ ] Implementar cache (Redis)
- [ ] Logs estruturados (ELK Stack)
- [ ] Monitoramento (Prometheus + Grafana)

### **Frontend**
- [ ] Implementar testes (Jest + React Testing Library)
- [ ] Adicionar Storybook para componentes
- [ ] Implementar service workers (PWA)
- [ ] Otimizar bundle size
- [ ] Adicionar lazy loading de rotas
- [ ] Implementar error boundaries

### **DevOps**
- [ ] CI/CD com GitHub Actions
- [ ] Docker para desenvolvimento
- [ ] Docker Compose para stack completa
- [ ] Backup automático do banco
- [ ] Monitoramento de uptime
- [ ] Logs centralizados

---

## 📊 Backlog (Sem prioridade definida)

- [ ] Importar transações de arquivo CSV
- [ ] Integração com Open Banking
- [ ] Contas compartilhadas (família)
- [ ] Carteira digital
- [ ] Controle de assinaturas recorrentes
- [ ] Planejador de viagens
- [ ] Calculadora de aposentadoria
- [ ] Simulador de empréstimos
- [ ] Dashboard do contador/gestor
- [ ] Modo de visualização para impressão

---

## 🎯 Meta de Curto Prazo (Próximos 30 dias)

1. ✅ **Deploy em produção** - Permitir feedback de usuários
2. ✅ **Rate limiting** - Segurança contra ataques
3. ✅ **Skeleton loaders** - Melhorar UX
4. ✅ **Filtros no dashboard** - Análises mais detalhadas
5. ✅ **Exportar CSV** - Permitir análise externa

---

## 📈 Métricas de Sucesso

### Objetivos para v1.5.0:
- ✅ 10+ usuários testando
- ✅ Feedback coletado
- ✅ Bugs críticos corrigidos
- ✅ Performance otimizada

### Objetivos para v2.0.0:
- ✅ 100+ usuários ativos
- ✅ App mobile lançado
- ✅ Integrações com bancos
- ✅ Monetização definida

---

## 🤝 Como Contribuir

Se você quiser ajudar a implementar alguma dessas funcionalidades:

1. Escolha uma tarefa
2. Comente na issue correspondente
3. Fork o projeto
4. Implemente a feature
5. Abra um Pull Request

---

## 💬 Feedback

Tem alguma sugestão de funcionalidade?

- Abra uma [issue](https://github.com/seu-usuario/gestor-financeiro/issues)
- Entre em contato: allancarvalho01@gmail.com

---

**Última atualização:** 30/11/2025  
**Mantido por:** Zero (Allan Carvalho)
