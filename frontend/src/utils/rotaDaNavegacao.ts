import type { NavegacaoOrigem } from '../services/metricasService';

// Drill-down web (PR-F3-12): rota da aplicação para cada destino fornecido
// pelo backend (PR-F3-04). Destino desconhecido ou sem ID obrigatório → null;
// o cliente nunca inventa link aproximado.
export function rotaDaNavegacao(nav: NavegacaoOrigem): string | null {
  switch (nav.destino) {
    case 'EXTRATO_CONTA':
      return nav.id != null ? `/contas-financeiras?contaId=${nav.id}` : null;
    case 'TRANSACAO':
      return nav.id != null ? `/transacoes?transacaoId=${nav.id}` : null;
    case 'FATURA':
      return '/faturas';
    case 'META':
      return '/metas';
    case 'INVESTIMENTO':
      return '/investimentos';
    case 'TRANSACOES': {
      const params = new URLSearchParams(nav.filtros ?? {}).toString();
      return params ? `/transacoes?${params}` : '/transacoes';
    }
    default:
      return null;
  }
}
