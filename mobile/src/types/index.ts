// ── Enums e tipos básicos ─────────────────────────────────────────────
export type TipoTransacao = 'ENTRADA' | 'SAIDA';
export type TipoCarteira = 'DINHEIRO' | 'CONTA_BANCARIA' | 'POUPANCA';
export type TipoConta = 'CREDITO' | 'DEBITO' | 'DINHEIRO' | 'POUPANCA';
export type StatusPagamento = 'PAGO' | 'PENDENTE' | 'ATRASADO' | 'CANCELADO';
export type BadgeStatus = 'ativo' | 'pendente' | 'inativo' | 'cancelado';
export type AsyncStatus = 'idle' | 'loading' | 'success' | 'error';

// ── Genéricos ───────────────────────────────────────────────────────
export interface PagedResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}

export interface AsyncState<T> {
  status: AsyncStatus;
  data: T | null;
  error: string | null; // sempre mensagem amigável em pt-BR
}

// Erro enriquecido pelo interceptor — componentes usam userMessage
export interface ApiErrorWithMessage extends Error {
  userMessage: string;
}

// ── Auth ────────────────────────────────────────────────────────────
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

// ── Dashboard ───────────────────────────────────────────────────────
export interface DashboardResumo {
  totalEntradas: number;
  totalSaidas: number;
  saldo: number; // saldo do mês
  saldoCarteiras: number; // patrimônio total das carteiras
  totalCategorias: number;
  totalContas: number;
  totalMetas: number;
  totalContasFixas: number;
}

export interface GastoPorCategoria {
  categoria: string;
  valor: number;
  cor: string;
  percentual: number;
}

export interface EvolucaoMensal {
  mes: string;
  entradas: number;
  saidas: number;
  saldo: number;
}

// ── Transações ──────────────────────────────────────────────────────
export interface CategoriaResumo {
  id: number;
  nome: string;
  cor: string;
  icone?: string;
}

export interface ContaResumo {
  id: number;
  nome: string;
  tipo: TipoConta;
}

export interface Transacao {
  id: number;
  descricao: string;
  valorTotal: number;
  tipo: TipoTransacao;
  data: string;
  status: StatusPagamento;
  parcelado: boolean;
  totalParcelas?: number;
  valorParcela?: number;
  observacoes?: string;
  recorrente: boolean;
  categoria?: CategoriaResumo;
  conta?: ContaResumo;
}

export interface TransacaoRequest {
  descricao: string;
  valor: number; // campo aceito pelo backend (também aceita valorTotal)
  data: string; // formato YYYY-MM-DD obrigatório
  tipo: TipoTransacao;
  categoriaId: number; // obrigatório pelo backend
  contaId?: number;
  parcelado?: boolean;
  totalParcelas?: number;
  observacoes?: string;
  recorrente?: boolean;
}

// ── Categorias ──────────────────────────────────────────────────────
export interface Categoria {
  id: number;
  nome: string;
  cor: string;
  icone?: string;
  valorEsperado?: number;
  valorGasto?: number;
  ativo: boolean;
}

export interface CategoriaRequest {
  nome: string;
  cor: string;
  icone?: string;
  valorEsperado?: number;
}

// ── Carteiras ───────────────────────────────────────────────────────
export interface Carteira {
  id: number;
  nome: string;
  tipo: TipoCarteira;
  saldo: number;
  banco?: string;
}

export interface CarteiraRequest {
  nome: string;
  tipo: TipoCarteira;
  saldo: number;
  banco?: string;
}

// ── Contas ─────────────────────────────────────────────────────────
export interface Conta {
  id: number;
  nome: string;
  tipo: TipoConta;
  limiteTotal?: number;
  diaFechamento?: number;
  diaVencimento?: number;
  cor?: string;
}

export interface ContaRequest {
  nome: string;
  tipo: TipoConta;
  limiteTotal?: number;
  diaFechamento?: number;
  diaVencimento?: number;
  cor?: string;
}

// ── Contas Fixas ───────────────────────────────────────────────────
export interface ContaFixa {
  id: number;
  nome: string;
  valorPlanejado: number;
  valorReal?: number;
  diaVencimento: number;
  dataProximoVencimento?: string;
  status: StatusPagamento;
  recorrente: boolean;
  ativo: boolean;
  observacoes?: string;
  categoria?: CategoriaResumo;
}

export interface ContaFixaRequest {
  descricao: string; // campo nome do backend aceita "descricao" via @JsonAlias
  valor: number; // campo valorPlanejado aceita "valor" via @JsonAlias
  diaVencimento: number;
  categoriaId: number; // obrigatório pelo backend
  recorrente?: boolean;
  observacoes?: string;
}

// ── Metas ──────────────────────────────────────────────────────────
export interface Meta {
  id: number;
  nome: string;
  valorTotal: number;
  valorReservado: number;
  valorMensal?: number;
  dataInicio?: string;
  dataPrevista?: string;
  dataConclusao?: string;
  ativa: boolean;
  cor?: string;
  icone?: string;
  descricao?: string;
}

export interface MetaRequest {
  nome: string;
  valorTotal: number;
  valorMensal?: number;
  dataLimite?: string; // formato YYYY-MM-DD — mapeado para dataPrevista no backend
  cor?: string;
  icone?: string;
  descricao?: string;
}

export interface MetaProgresso {
  metaId: number;
  valorTotal: number;
  valorReservado: number;
  valorRestante: number;
  progresso: number; // porcentagem 0-100
}

// ── Parcelas ───────────────────────────────────────────────────────
export interface Parcela {
  id: number;
  numeroParcela: number;
  totalParcelas: number;
  valor: number;
  dataVencimento: string;
  status: StatusPagamento;
}
