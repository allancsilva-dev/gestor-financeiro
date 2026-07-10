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
  onboardingCompleto: boolean;
}

export interface LoginResponse {
  message: string;
  success: boolean;
  accessToken?: string;
  token?: string;
  csrfToken?: string;
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
  carteiraId?: number; // sem carteira a transação não movimenta saldo
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

export type TipoMovimentoCarteira =
  | 'ENTRADA'
  | 'SAIDA'
  | 'AJUSTE_MANUAL'
  | 'TRANSFERENCIA_ENTRADA'
  | 'TRANSFERENCIA_SAIDA'
  | 'RESERVA_META'
  | 'RESGATE_META'
  | 'ESTORNO';

// Item do extrato (ledger) da carteira — GET /v1/carteiras/{id}/movimentos
export interface MovimentoCarteira {
  id: number;
  carteiraId: number;
  carteiraNome: string;
  tipo: TipoMovimentoCarteira;
  valor: number;
  valorAssinado: number; // positivo credita, negativo debita
  origem: string;
  referenciaTipo?: string;
  referenciaId?: number;
  descricao?: string;
  dataMovimento: string;
  saldoResultante: number;
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
  valorGasto?: number;
  diaFechamento?: number;
  diaVencimento?: number;
  cor?: string;
  banco?: string;
}

export interface ContaRequest {
  nome: string;
  tipo: TipoConta;
  limiteTotal?: number;
  diaFechamento?: number;
  diaVencimento?: number;
  cor?: string;
  banco?: string;
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

// ── Orçamento ─────────────────────────────────────────────────────
export interface OrcamentoCategoriaItem {
  id: number;
  categoriaId: number;
  categoriaNome: string;
  categoriaCor: string;
  categoriaIcone: string;
  valorLimite: number;
  valorGasto: number;
  percentualGasto: number;
}

export interface OrcamentoResponse {
  id: number;
  mes: number;
  ano: number;
  valorTotalPlanejado: number;
  valorTotalGasto: number;
  categorias: OrcamentoCategoriaItem[];
}

// ── Fatura ────────────────────────────────────────────────────────
export interface FaturaLancamento {
  transacaoId: number;
  descricao: string;
  valor: number;
  data: string;
  categoriaId: number | null;
  categoriaNome: string | null;
  categoriaCor: string;
  categoriaIcone: string;
  parcelaAtual: number | null;
  totalParcelas: number | null;
  tipo: 'COMPRA' | 'AJUSTE' | 'ESTORNO';
}

export interface FaturaResponse {
  id: number;
  contaId: number;
  contaNome: string;
  mes: number;
  ano: number;
  dataFechamento: string;
  dataVencimento: string;
  valorTotal: number;
  valorPago: number;
  status: string;
  dataPagamento: string | null;
  lancamentos: FaturaLancamento[];
}

// ── Projeção ──────────────────────────────────────────────────────
export interface ProjecaoMensal {
  periodo: string;
  mes: number;
  ano: number;
  saldoInicial: number;
  totalContasFixas: number;
  totalParcelas: number;
  totalSaidas: number;
  saldoFinal: number;
}

export interface ProjecaoResponse {
  saldoAtual: number;
  meses: ProjecaoMensal[];
}

// ── Relatórios ────────────────────────────────────────────────────
export interface RelatorioCategoriaItem {
  categoriaId: number;
  nome: string;
  cor: string;
  icone: string;
  valorTotal: number;
  porcentagem: number;
}

export interface RelatorioTransacaoItem {
  id: number;
  descricao: string;
  valor: number;
  data: string;
  categoriaNome: string | null;
  categoriaCor: string;
}

export interface RelatorioContaItem {
  contaId: number;
  nome: string;
  tipo: string;
  valorTotal: number;
  porcentagem: number;
}

// GET /v1/dashboard/evolucao-mensal — últimos 6 meses
export interface EvolucaoMensalItem {
  mes: string; // abreviado pt-BR (ex.: "jul.")
  entradas: number;
  saidas: number;
  saldo: number;
}

export interface RelatorioResponse {
  inicio: string;
  fim: string;
  totalEntradas: number;
  totalSaidas: number;
  saldo: number;
  totalTransacoes: number;
  gastosPorCategoria: RelatorioCategoriaItem[];
  maioresDespesas: RelatorioTransacaoItem[];
  gastosPorConta: RelatorioContaItem[];
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
