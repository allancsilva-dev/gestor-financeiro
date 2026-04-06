# Prompt 01 — Base do App Mobile (Gestor Financeiro)
> Baseado no contrato de desenvolvimento Nexos Tech — Mobile Edition.
> Todo código, visual e arquitetura seguem estas regras. Não invente padrões novos.
> Não adicione bibliotecas sem aprovação explícita.

Cole este prompt inteiro no Claude Code dentro da pasta `mobile`.

---

## Contexto do projeto

Você está trabalhando no projeto `gestor-financeiro`, um monorepo com três pastas:

- `backend/` — Spring Boot (já pronto e funcional)
- `frontend/` — React + TypeScript (já pronto)
- `mobile/` — React Native + Expo (em construção)

A pasta `mobile` já possui instalado:
- Expo SDK 54, Expo Router
- @tanstack/react-query, axios
- nativewind, tailwindcss@3.4.17
- expo-secure-store
- react-native-safe-area-context, react-native-screens

Os arquivos abaixo já existem — não modificar:
- `tailwind.config.js`
- `babel.config.js`
- `metro.config.js`
- `src/global.css`

---

## Objetivo desta fase

Construir a base funcional do app com:
- Login real conectado ao backend
- Token de acesso em memória
- Navegação funcionando (tab bar + auth flow)
- Dashboard consumindo API real
- UI completa com suporte a dark/light mode automático

**Não implementar nesta fase:**
- Refresh token automático
- Retry/backoff
- Persistência de sessão entre aberturas do app
- Verificação de sessão com `/me` na inicialização

---

## REGRA ABSOLUTA — Idioma e Localização

Todo o sistema segue **Português Brasil (pt-BR)**. Esta regra não tem exceção.

**Interface (obrigatório pt-BR):**
- Labels, títulos, botões, placeholders, mensagens de erro, empty states → todos em pt-BR
- Datas: `DD/MM/AAAA` — nunca MM/DD/AAAA
- Horas: `HH:MM` formato 24h
- Valores monetários: `R$ 1.250,00` — ponto como milhar, vírgula como decimal
- Números decimais: `1.234,56` — nunca `1,234.56`

**Código:**
- Variáveis, funções, tipos, arquivos → inglês (convenção universal)
- Comentários no código → português Brasil
- Logs de erro voltados ao dev → português Brasil

**Exemplos obrigatórios:**
```
✅ "Nenhum registro encontrado"     ❌ "No records found"
✅ "Erro ao carregar dados"         ❌ "Error loading data"
✅ "Salvar" / "Cancelar"            ❌ "Save" / "Cancel"
✅ R$ 1.250,00                      ❌ R$ 1,250.00
✅ 06/04/2026                       ❌ 04/06/2026
```

---

## REGRA ABSOLUTA — Qualidade de Código

```
Não criar padrões visuais novos.
Sempre reutilizar componentes de /components/ui antes de criar novos.
Código legível, previsível e consistente.
Evitar soluções complexas desnecessárias.
Toda tela entregue deve ter os 3 estados: loading, vazio e erro.
UI nunca recebe erro bruto — sempre mensagem amigável mapeada no service.
Toda a interface em Português Brasil — sem exceção.
Nenhuma cor hardcoded em componentes — sempre via useTheme().
Nunca usar Intl diretamente em componentes — sempre via utilitários de /utils/format.ts.
```

---

## Estrutura de pastas a criar

```
mobile/
  app/
    _layout.tsx
    index.tsx
    (auth)/
      _layout.tsx
      login.tsx
    (app)/
      _layout.tsx
      index.tsx
      transacoes.tsx
      metas.tsx
      perfil.tsx
  src/
    config/
      api.config.ts
    theme/
      colors.ts
      index.ts
    types/
      index.ts
    services/
      api.ts
      authService.ts
    store/
      auth.ts
    context/
      AuthContext.tsx
    components/
      ui/
        SkeletonBox.tsx
        Badge.tsx
    utils/
      format.ts
      validate.ts
```

---

## 1. Configuração centralizada (src/config/api.config.ts)

```typescript
// ⚠️  DESENVOLVIMENTO: troque pelo IP da sua máquina na rede Wi-Fi local.
// Descubra com `ipconfig` (Windows) ou `ifconfig` (Mac/Linux).
// Exemplo: http://192.168.1.10:8081
// ⚠️  PRODUÇÃO: substituir por variável de ambiente via expo-constants.
export const API_BASE_URL = 'http://SEU_IP_LOCAL:8081/api';
```

Todos os outros arquivos importam a URL apenas daqui. Nunca repetir a URL em outro lugar.

---

## 2. Tema (src/theme/colors.ts)

```typescript
export const DARK_COLORS = {
  bg: '#080b12',
  card: '#13182a',
  top: '#0f1117',
  border: 'rgba(255,255,255,0.07)',
  textPrimary: '#e8edf5',
  textSecondary: '#6b7d99',
  textMuted: '#3d4f68',
  brand: '#00c8ff',
  brandText: '#0a1628',
  brand2: '#8b2fff',
  success: '#2ed573',
  warning: '#ffa502',
  danger: '#ff4757',
  info: '#00c8ff',
  successBg: 'rgba(46,213,115,0.12)',
  warningBg: 'rgba(255,165,2,0.12)',
  dangerBg: 'rgba(255,71,87,0.12)',
  infoBg: 'rgba(0,200,255,0.12)',
  navBg: '#0f1117',
  navBorder: 'rgba(255,255,255,0.06)',
  skeletonBase: '#13182a',
  skeletonHighlight: '#1e2d45',
};

export const LIGHT_COLORS = {
  bg: '#f0f4fa',
  card: '#ffffff',
  top: '#ffffff',
  border: 'rgba(0,0,0,0.07)',
  textPrimary: '#1a1d23',
  textSecondary: '#6b7280',
  textMuted: '#aab0bd',
  brand: '#0090c0',
  brandText: '#ffffff',
  brand2: '#8b2fff',
  success: '#1a9e4a',
  warning: '#b36000',
  danger: '#cc2233',
  info: '#0090c0',
  successBg: 'rgba(26,158,74,0.12)',
  warningBg: 'rgba(179,96,0,0.12)',
  dangerBg: 'rgba(204,34,51,0.12)',
  infoBg: 'rgba(0,144,192,0.12)',
  navBg: '#ffffff',
  navBorder: 'rgba(0,0,0,0.07)',
  skeletonBase: '#e8edf5',
  skeletonHighlight: '#f5f7fa',
};

export type AppColors = typeof DARK_COLORS;
```

---

## 3. Hook useTheme (src/theme/index.ts)

```typescript
import { useColorScheme } from 'react-native';
import { DARK_COLORS, LIGHT_COLORS, AppColors } from './colors';

// Segue o tema do sistema automaticamente — sem toggle manual
export const useTheme = (): AppColors => {
  const scheme = useColorScheme();
  return scheme === 'dark' ? DARK_COLORS : LIGHT_COLORS;
};
```

---

## 4. Tipos globais (src/types/index.ts)

```typescript
export interface Usuario {
  id: number;
  nome: string;
  email: string;
}

export interface LoginResponse {
  message: string;
  success: boolean;
  accessToken?: string;
  token?: string;
  usuario?: Usuario;
}

export interface PagedResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}

export type AsyncStatus = 'idle' | 'loading' | 'success' | 'error';

export interface AsyncState<T> {
  status: AsyncStatus;
  data: T | null;
  error: string | null; // sempre mensagem amigável em pt-BR — nunca erro técnico
}

// Erro enriquecido pelo interceptor — componentes usam userMessage, nunca o erro bruto
export interface ApiErrorWithMessage extends Error {
  userMessage: string;
}

// Tipos de domínio usados no dashboard
export interface DashboardResumo {
  saldoTotal: number;
  totalEntradas: number;
  totalSaidas: number;
}

export interface Transacao {
  id: number;
  descricao: string;
  valor: number;
  tipo: 'ENTRADA' | 'SAIDA';
  categoria?: { nome: string };
  data: string;
}

// Status de badges — padrão Nexos
export type BadgeStatus = 'ativo' | 'pendente' | 'inativo' | 'cancelado';
```

---

## 5. Store de autenticação (src/store/auth.ts)

```typescript
// Token apenas em memória — sem persistência nesta fase.
// TODO fase 2: persistir com expo-secure-store para manter sessão entre aberturas.
let _accessToken: string | null = null;

export const setAccessToken = (token: string) => { _accessToken = token; };
export const getAccessToken = () => _accessToken;
export const clearAccessToken = () => { _accessToken = null; };
```

---

## 6. Cliente HTTP (src/services/api.ts)

```typescript
import axios, { AxiosError } from 'axios';
import { API_BASE_URL } from '../config/api.config';
import { getAccessToken } from '../store/auth';
import { ApiErrorWithMessage } from '../types';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  withCredentials: false,
  headers: { 'Content-Type': 'application/json' },
});

// Adiciona token em toda request autenticada
api.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Log centralizado — sem dados sensíveis (nunca logar body, token ou dados pessoais)
api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    console.error('[API Error]', {
      url: error.config?.url,
      status: error.response?.status,
      // NUNCA adicionar: body, headers com token, dados do usuário
    });

    const status = error.response?.status;

    // Mapeia para mensagem amigável em pt-BR — UI nunca recebe erro técnico
    let userMessage = 'Erro inesperado. Tente novamente.';
    if (!error.response) {
      userMessage = 'Sem conexão. Verifique sua internet.';
    } else if (status === 401) {
      userMessage = 'Sessão inválida. Faça login novamente.';
    } else if (status === 403) {
      userMessage = 'Você não tem permissão para esta ação.';
    } else if (status === 404) {
      userMessage = 'Registro não encontrado.';
    } else if (status === 422) {
      userMessage = 'Dados inválidos. Verifique os campos.';
    } else if (status && status >= 500) {
      userMessage = 'Erro no servidor. Tente novamente em instantes.';
    }

    const enrichedError = error as ApiErrorWithMessage;
    enrichedError.userMessage = userMessage;
    return Promise.reject(enrichedError);
  }
);

export default api;
```

---

## 7. AuthService (src/services/authService.ts)

```typescript
import api from './api';
import { setAccessToken, clearAccessToken } from '../store/auth';
import { LoginResponse, Usuario } from '../types';

export const authService = {
  async login(email: string, password: string): Promise<Usuario> {
    const { data } = await api.post<LoginResponse>('/auth/login', { email, password });
    const token = data.accessToken ?? data.token;
    if (!token) throw new Error('Token não recebido do servidor.');
    setAccessToken(token);
    // Falha explicitamente se backend não retornar o usuário.
    // Não usar fallback — mascararia problema real e geraria estado inconsistente.
    if (!data.usuario) throw new Error('Dados do usuário não retornados pelo servidor.');
    return data.usuario;
  },

  logout(): void {
    clearAccessToken();
  },
};
```

---

## 8. AuthContext (src/context/AuthContext.tsx)

```typescript
// isLoading é sempre false nesta fase — decisão consciente:
// não há verificação de sessão na inicialização.
// TODO fase 2: adicionar bootstrap com /me + SecureStore.

interface AuthContextType {
  usuario: Usuario | null;
  isAuthenticated: boolean;
  isLoading: false;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}
```

- Estado inicial: `usuario: null`
- `isAuthenticated`: derivado de `usuario !== null`
- `login()`: chama `authService.login()`, salva usuário no estado
- `logout()`: chama `authService.logout()`, limpa usuário do estado

---

## 9. Layout raiz (app/_layout.tsx)

Providers na ordem:
1. `QueryClientProvider` com `new QueryClient()`
2. `SafeAreaProvider`
3. `AuthProvider`
4. `Stack` com `headerShown: false`

Importar `../src/global.css` no topo.

---

## 10. Índice raiz (app/index.tsx)

```typescript
// Redireciona baseado no estado de autenticação
// isLoading é sempre false nesta fase — sem splash de espera
if (isAuthenticated) return <Redirect href="/(app)/" />;
return <Redirect href="/(auth)/login" />;
```

---

## 11. Layout auth (app/(auth)/_layout.tsx)

`Stack` com `headerShown: false`.

---

## 12. Tela de Login (app/(auth)/login.tsx)

`KeyboardAvoidingView` cobrindo tela inteira, fundo `colors.bg`, conteúdo centralizado com `justifyContent: 'center'`, padding horizontal 24.

**Regras de formulário (padrão Nexos):**
- Erro exibido por campo, abaixo do input — nunca só no topo
- Botão desabilitado + loading durante submit
- Validação no client é convenção — validação no server é obrigatória (já feita no backend)

Elementos:
1. **Logo**: View 48×48 `borderRadius: 14` fundo `colors.brand + '26'`. Dentro: View 22×22 `borderRadius: 6` fundo `colors.brand`
2. **Título**: "Gestor Financeiro" — `textPrimary` 24px weight `'700'` marginTop 16
3. **Subtítulo**: "Entre na sua conta para continuar" — `textSecondary` 13px marginBottom 32
4. **Label E-mail**: "E-MAIL" — 9px `textSecondary` letterSpacing 0.8 marginBottom 6
5. **Input E-mail**: fundo `card`, borda 1px `border`, `borderRadius: 8`, padding 12, cor `textPrimary`, placeholder "seu@email.com" cor `textMuted`, `keyboardType: 'email-address'`, `autoCapitalize: 'none'`
6. **Label Senha**: igual, marginTop 14 marginBottom 6
7. **Input Senha**: igual com `secureTextEntry: true`, placeholder "••••••••"
8. **Link "Esqueceu a senha?"**: alinhado à direita, cor `colors.brand`, 12px, marginTop 8
9. **Mensagem de erro**: cor `colors.danger` 12px marginTop 8 — visível apenas quando `error !== null`
10. **Botão "ENTRAR"**: marginTop 24, fundo `colors.brand`, `borderRadius: 8`, altura 48, width `'100%'`. Texto cor `colors.brandText` weight `'700'` 11px letterSpacing 1. Durante loading: `<ActivityIndicator color={colors.brandText} />`. Desabilitado durante loading.

Validação:
- Campos vazios → `setError('Preencha e-mail e senha.')`
- Erro da API → `setError((error as ApiErrorWithMessage).userMessage)`

Após sucesso: `router.replace('/(app)/')`.

---

## 13. Layout autenticado (app/(app)/_layout.tsx)

Tab bar: **Transações · Metas · Início · Perfil · Mais**

```typescript
// IMPORTANTE: chamar useTheme() no corpo do componente
// Hooks não podem ser chamados dentro de objetos ou callbacks
const colors = useTheme();
```

Estilo da tab bar:
```typescript
tabBarStyle: {
  backgroundColor: colors.navBg,
  borderTopColor: colors.navBorder,
  borderTopWidth: 1,
  height: 64,
  paddingBottom: 8,
  paddingTop: 4,
}
tabBarActiveTintColor: colors.brand,
tabBarInactiveTintColor: colors.textSecondary,
tabBarLabelStyle: { fontSize: 9 }
```

**Botão Início (centro, index 2)** — `tabBarButton` customizado com `TouchableOpacity`:
- Circular 56×56, fundo `colors.brand`, `borderRadius: 28`, `marginTop: -20`
- Ícone: grade 2×2 de Views 7×7 `borderRadius: 2` cor `colors.brandText`
- Sem label

Ícones das outras abas: Views simples sem dependência de react-native-svg.

Espaçamento base: múltiplos de 4 — usar 4, 8, 12, 16, 24px. Nunca valores arbitrários.

---

## 14. Dashboard (app/(app)/index.tsx)

`ScrollView` fundo `colors.bg`, padding 16, paddingBottom 32.

**Queries (TanStack Query):**
```typescript
const resumoQuery = useQuery({
  queryKey: ['dashboard-resumo'],
  queryFn: () => api.get<DashboardResumo>('/v1/dashboard/resumo').then(r => r.data),
});

const transacoesQuery = useQuery({
  queryKey: ['transacoes-recentes'],
  queryFn: () =>
    api.get<PagedResponse<Transacao>>('/v1/transacoes/minhas?page=0&size=5&sort=data,desc')
       .then(r => r.data),
});
```

**Header** (row, space-between, center, marginBottom 20):
- Esquerda: `getGreeting()` 11px `textSecondary` + nome 18px weight `'700'` `textPrimary`
- Direita: avatar 38×38 `borderRadius: 19` fundo `colors.brand + '26'`, iniciais `getInitials()` 14px weight `'700'` cor `colors.brand`

**Card de saldo** (fundo `card`, `borderRadius: 12`, borda 1px `border`, padding 16, marginBottom 24):
- Loading → `<SkeletonBox width="100%" height={110} />`
- Error → mensagem `colors.danger` 13px
- Sucesso:
  - "SALDO TOTAL" 9px `textSecondary` letterSpacing 0.8 marginBottom 4
  - Valor 28px weight `'800'` `textPrimary` via `formatCurrency()`
  - Row gap 12 marginTop 12:
    - Entradas: label "ENTRADAS" 8px `textSecondary` + `formatCurrency()` 13px weight `'600'` `colors.success`
    - Saídas: label "SAÍDAS" 8px `textSecondary` + `formatCurrency()` 13px weight `'600'` `colors.danger`

**Seção transações:**
- Header row space-between marginBottom 12: "ÚLTIMAS TRANSAÇÕES" 9px `textSecondary` letterSpacing 0.8 + "Ver todas" 11px `colors.brand`
- Loading → 4× `<SkeletonBox width="100%" height={52} borderRadius={8} />` gap 8
- Empty → "Nenhuma transação encontrada" centralizado `textSecondary` 14px + "Adicione sua primeira transação" `textMuted` 12px
- Error → mensagem centralizada `colors.danger` 13px
- Lista: cada item height 56, row, center, gap 12, borderBottom 1px `border`:
  - Ícone 36×36 `borderRadius: 8` fundo `successBg`/`dangerBg`, "↑"/"↓" 18px cor `success`/`danger`
  - Coluna flex 1: `descricao` 13px weight `'500'` `textPrimary` + categoria 11px `textSecondary`
  - `formatCurrency(valor)` 13px weight `'600'` `success`/`danger`

---

## 15. Badge (src/components/ui/Badge.tsx)

Componente reutilizável de status — padrão Nexos:

```typescript
// Props: status: BadgeStatus, label: string

// Cores por status:
// ativo/aprovado  → successBg / success
// pendente        → warningBg / warning
// inativo/cancelado → dangerBg / danger

// Estilo:
// fontSize: 10, fontWeight: '700'
// paddingHorizontal: 8, paddingVertical: 2
// borderRadius: 20
// Texto sempre em pt-BR — nunca inglês
```

---

## 16. Telas placeholder

`transacoes.tsx`, `metas.tsx`, `perfil.tsx`:
- flex 1, fundo `colors.bg`, center
- Nome da tela 18px weight `'600'` `textPrimary`
- "Em breve" 13px `textSecondary` marginTop 8

---

## 17. SkeletonBox (src/components/ui/SkeletonBox.tsx)

Props: `width: number | string`, `height: number`, `borderRadius?: number` (default 8)

```typescript
// OBRIGATÓRIO: useNativeDriver: false
// Motivo: animação de backgroundColor não é suportada pelo driver nativo do React Native.
// useNativeDriver: true só suporta transform e opacity.
// Usar false garante que a interpolação de cor funcione corretamente.

// Implementação:
// - Animated.Value(0)
// - Animated.loop + Animated.timing duration 900ms, useNativeDriver: false
// - interpolate: 0 → colors.skeletonBase, 1 → colors.skeletonHighlight
// - useTheme() para as cores
// - cleanup: animation.stop() no return do useEffect
```

---

## 18. Utilitários de formatação (src/utils/format.ts)

**Nunca usar Intl diretamente em componentes. Sempre importar deste arquivo.**

```typescript
// Moeda: R$ 1.250,00
export const formatCurrency = (value: number): string =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);

// Data: 06/04/2026
export const formatDate = (date: Date | string): string =>
  new Intl.DateTimeFormat('pt-BR').format(new Date(date));

// Data e hora: 06/04/2026 14:30
export const formatDateTime = (date: Date | string): string =>
  new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(date));

// Número decimal: 1.250,00
export const formatNumber = (value: number, decimals = 2): string =>
  new Intl.NumberFormat('pt-BR', { minimumFractionDigits: decimals }).format(value);

// Porcentagem: 12,5%
export const formatPercent = (value: number, decimals = 1): string =>
  `${formatNumber(value, decimals)}%`;

// Telefone: (11) 99565-5921
export const formatPhone = (value: string): string => {
  const digits = value.replace(/\D/g, '');
  if (digits.length === 11) return digits.replace(/(\d{2})(\d{5})(\d{4})/, '($1) $2-$3');
  return digits.replace(/(\d{2})(\d{4})(\d{4})/, '($1) $2-$3');
};

// Saudação por horário
export const getGreeting = (): string => {
  const hour = new Date().getHours();
  if (hour < 12) return 'Bom dia,';
  if (hour < 18) return 'Boa tarde,';
  return 'Boa noite,';
};

// Iniciais: "Allan Carvalho" → "AC"
export const getInitials = (nome: string): string =>
  nome.trim().split(' ').slice(0, 2).map(n => n[0].toUpperCase()).join('');
```

---

## 19. Utilitários de validação (src/utils/validate.ts)

```typescript
// Validações centralizadas — usar em formulários futuros
// Retornam boolean — mensagens de erro ficam nos schemas Zod

export const isValidCPF = (cpf: string): boolean => {
  const digits = cpf.replace(/\D/g, '');
  if (digits.length !== 11 || /^(\d)\1+$/.test(digits)) return false;
  let sum = 0;
  for (let i = 0; i < 9; i++) sum += parseInt(digits[i]) * (10 - i);
  let remainder = (sum * 10) % 11;
  if (remainder === 10 || remainder === 11) remainder = 0;
  if (remainder !== parseInt(digits[9])) return false;
  sum = 0;
  for (let i = 0; i < 10; i++) sum += parseInt(digits[i]) * (11 - i);
  remainder = (sum * 10) % 11;
  if (remainder === 10 || remainder === 11) remainder = 0;
  return remainder === parseInt(digits[10]);
};

export const isValidPhone = (phone: string): boolean =>
  /^\(\d{2}\) \d{4,5}-\d{4}$/.test(phone);

export const isValidCEP = (cep: string): boolean =>
  /^\d{5}-\d{3}$/.test(cep);
```

---

## Checklist final (verificar antes de encerrar)

**Idioma e localização:**
- [ ] Toda a interface está em Português Brasil — sem uma palavra em inglês na UI
- [ ] Datas no formato DD/MM/AAAA
- [ ] Valores monetários no formato R$ 1.250,00
- [ ] Nenhum componente usa Intl diretamente — sempre via format.ts

**Arquitetura:**
- [ ] URL do backend está apenas em `src/config/api.config.ts`
- [ ] Nenhuma cor hardcoded — sempre via `useTheme()`
- [ ] `useTheme()` chamado no corpo do componente, nunca dentro de objetos
- [ ] Log do interceptor sem dados sensíveis (sem body, token, dados pessoais)

**Autenticação:**
- [ ] Login conecta no backend real e salva token em memória
- [ ] Token enviado no header Authorization em toda request autenticada
- [ ] `isLoading: false` com comentário explicando decisão
- [ ] `clearAccessToken()` com TODO fase 2

**UX — 3 estados obrigatórios:**
- [ ] Login: loading, erro, sucesso
- [ ] Dashboard card saldo: skeleton, erro, dados
- [ ] Dashboard transações: skeleton, empty state em pt-BR, erro, dados

**Componentes:**
- [ ] SkeletonBox com `useNativeDriver: false` e cleanup no unmount
- [ ] Badge criado e pronto para uso nas próximas telas
- [ ] Tab bar com botão Início elevado no centro

**Qualidade:**
- [ ] Espaçamentos são múltiplos de 4 (4, 8, 12, 16, 24px)
- [ ] Sem biblioteca nova adicionada além das já instaladas
- [ ] Sem padrão visual novo inventado

---

## Ao finalizar

Mostrar:
- Estrutura completa de arquivos criados
- Se alguma dependência extra foi necessária e por quê
- Qualquer decisão de implementação que divergiu do prompt
