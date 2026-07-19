import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Dashboard from './Dashboard';
import metricasService from '../services/metricasService';
import dashboardService from '../services/dashboardService';

const navigateMock = vi.fn();
vi.mock('react-router-dom', async importOriginal => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => navigateMock,
}));
vi.mock('../components/Layout', () => ({ default: ({ children }: { children: React.ReactNode }) => <>{children}</> }));
vi.mock('../components/GraficoGastosPorCategoria', () => ({ default: () => <div>gráfico categorias</div> }));
vi.mock('../components/GraficoEvolucaoMensal', () => ({ default: () => <div>gráfico evolução</div> }));
vi.mock('../services/metricasService', () => ({ default: { obter: vi.fn(), listarOrigens: vi.fn() } }));
vi.mock('../services/dashboardService', () => ({ default: { gastosPorCategoria: vi.fn(), evolucaoMensal: vi.fn(), projecao: vi.fn() } }));

const metricas = {
  dataReferencia: '2026-07-16', horizonteComprometido: '2026-07-31',
  disponivelAgora: 1000, reservado: 100, comprometido: 200, disponivelParaGastar: 700,
  investido: 500, dividas: 300, resultadoMensal: 80, patrimonioLiquido: 1200,
  variacaoPatrimonial: { total: 50, caixa: 40, passivo: 10, aportesInvestimento: 0, rendimentosInvestimento: 0, precoMercado: null },
};

describe('Dashboard canônico', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(metricasService.obter).mockResolvedValue(metricas);
    vi.mocked(metricasService.listarOrigens).mockResolvedValue([{ tipo: 'CONTA_FINANCEIRA', id: 1, descricao: 'Conta principal', valor: 1000 }]);
    vi.mocked(dashboardService.gastosPorCategoria).mockResolvedValue([]);
    vi.mocked(dashboardService.evolucaoMensal).mockResolvedValue([]);
    vi.mocked(dashboardService.projecao).mockResolvedValue({ saldoAtual: 0, meses: [] });
  });

  it('exibe as nove métricas e abre as origens inline', async () => {
    render(<Dashboard />);
    for (const name of ['Disponível agora', 'Reservado', 'Comprometido', 'Disponível para gastar', 'Investido', 'Dívidas', 'Resultado mensal', 'Patrimônio líquido', 'Variação patrimonial']) {
      expect(await screen.findByRole('button', { name: new RegExp(name, 'i') })).toBeInTheDocument();
    }
    fireEvent.click(screen.getByRole('button', { name: /Disponível agora/i }));
    expect(await screen.findByText('Conta principal')).toBeInTheDocument();
    expect(metricasService.listarOrigens).toHaveBeenCalledWith('DISPONIVEL_AGORA');
  });

  it('origem com destino válido navega; origem sem destino não é clicável (PR-F3-12)', async () => {
    vi.mocked(metricasService.listarOrigens).mockResolvedValue([
      { tipo: 'CONTA_FINANCEIRA', id: 1, descricao: 'Conta principal', valor: 1000, navegacao: { destino: 'EXTRATO_CONTA', id: 1, filtros: null } },
      { tipo: 'SAIDAS_NAO_CARTAO_COMPETENCIA', id: null, descricao: 'Saídas não cartão', valor: -50, navegacao: null },
    ]);
    render(<Dashboard />);
    fireEvent.click(await screen.findByRole('button', { name: /Disponível agora/i }));
    fireEvent.click(await screen.findByRole('button', { name: /Conta principal.*Abrir detalhe/i }));
    expect(navigateMock).toHaveBeenCalledWith('/contas-financeiras?contaId=1');
    expect(screen.getByText('Saídas não cartão')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Saídas não cartão/i })).not.toBeInTheDocument();
  });

  it('oferece retry no estado de erro', async () => {
    vi.mocked(metricasService.obter).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(metricas);
    render(<Dashboard />);
    fireEvent.click(await screen.findByRole('button', { name: 'Tentar novamente' }));
    await waitFor(() => expect(screen.getByRole('button', { name: /Disponível para gastar/i })).toBeInTheDocument());
  });
});
