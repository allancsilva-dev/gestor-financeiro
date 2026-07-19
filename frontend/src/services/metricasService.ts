import api from './api';

export type MetricaId =
  | 'DISPONIVEL_AGORA' | 'RESERVADO' | 'COMPROMETIDO'
  | 'DISPONIVEL_PARA_GASTAR' | 'INVESTIDO' | 'DIVIDAS'
  | 'RESULTADO_MENSAL' | 'PATRIMONIO_LIQUIDO' | 'VARIACAO_PATRIMONIAL';

export interface VariacaoPatrimonial {
  total: number;
  caixa: number;
  passivo: number;
  aportesInvestimento: number;
  rendimentosInvestimento: number;
  precoMercado: number | null;
}

export interface Metricas {
  dataReferencia: string;
  horizonteComprometido: string;
  disponivelAgora: number;
  reservado: number;
  comprometido: number;
  disponivelParaGastar: number;
  investido: number;
  dividas: number;
  resultadoMensal: number;
  patrimonioLiquido: number;
  variacaoPatrimonial: VariacaoPatrimonial;
}

export type DestinoNavegacao =
  | 'EXTRATO_CONTA' | 'TRANSACAO' | 'FATURA' | 'META' | 'INVESTIMENTO' | 'TRANSACOES';

export interface NavegacaoOrigem {
  destino: DestinoNavegacao;
  id: number | null;
  filtros: Record<string, string> | null;
}

export interface OrigemMetrica {
  tipo: string;
  id: number | null;
  descricao: string;
  valor: number;
  navegacao?: NavegacaoOrigem | null;
}

const metricasService = {
  obter: async (signal?: AbortSignal): Promise<Metricas> =>
    (await api.get<Metricas>('/metricas', { signal })).data,

  listarOrigens: async (metrica: MetricaId, signal?: AbortSignal): Promise<OrigemMetrica[]> =>
    (await api.get<OrigemMetrica[]>(`/metricas/${metrica}/origens`, { signal })).data,
};

export default metricasService;
