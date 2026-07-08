import { useState } from 'react';
import Layout from '../components/Layout';
import toast from 'react-hot-toast';
import relatorioService, { RelatorioResponse } from '../services/relatorioService';
import { formatCurrency } from '../utils/currency';
import { Search, TrendingUp, TrendingDown, DollarSign, Receipt, Download } from 'lucide-react';
import api from '../services/api';

export default function Relatorios() {
  const now = new Date();
  const [inicio, setInicio] = useState(() => {
    const d = new Date(now.getFullYear(), now.getMonth(), 1);
    return d.toISOString().split('T')[0];
  });
  const [fim, setFim] = useState(() => now.toISOString().split('T')[0]);
  const [data, setData] = useState<RelatorioResponse | null>(null);
  const [loading, setLoading] = useState(false);

  const carregar = async () => {
    setLoading(true);
    try {
      const r = await relatorioService.gerar(inicio, fim);
      setData(r);
    } catch {
      toast.error('Erro ao carregar relatório');
    } finally {
      setLoading(false);
    }
  };

  const downloadCsv = async (endpoint: string, filename: string) => {
    try {
      const BASE = import.meta.env.VITE_API_URL || 'http://localhost:8081/api';
      const url = `${BASE}/v1/exportar/${endpoint}`;
      if (endpoint === 'transacoes') {
        const response = await api.get(`/exportar/${endpoint}?inicio=${inicio}&fim=${fim}`, { responseType: 'blob' });
        triggerDownload(response.data, filename);
      } else {
        const response = await api.get(`/exportar/${endpoint}`, { responseType: 'blob' });
        triggerDownload(response.data, filename);
      }
    } catch {
      toast.error('Erro ao exportar');
    }
  };

  const triggerDownload = (data: any, filename: string) => {
    const blob = data instanceof Blob ? data : new Blob([data], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
    toast.success('Download iniciado!');
  };

  return (
    <Layout>
      <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 p-8">
        <div className="max-w-5xl mx-auto">
          <div className="mb-6">
            <h1 className="text-2xl font-bold text-white">Relatórios</h1>
            <p className="text-sm text-slate-400 mt-1">Analise suas finanças por período</p>
          </div>

          <div className="bg-slate-800 border border-slate-700 rounded-2xl p-6 mb-6">
            <div className="flex items-end gap-4">
              <div>
                <label className="block text-xs text-slate-400 mb-1">Início</label>
                <input type="date" value={inicio} onChange={(e) => setInicio(e.target.value)}
                  className="bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white text-sm" />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1">Fim</label>
                <input type="date" value={fim} onChange={(e) => setFim(e.target.value)}
                  className="bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white text-sm" />
              </div>
              <button onClick={carregar} disabled={loading}
                className="flex items-center gap-2 px-5 py-2 bg-orange-500 hover:bg-orange-600 text-white rounded-lg font-medium transition-colors disabled:opacity-50">
                {loading ? <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" /> : <Search className="w-4 h-4" />}
                Gerar Relatório
              </button>
            </div>
          </div>

          <div className="flex flex-wrap gap-2 mb-6">
            <button onClick={() => downloadCsv('transacoes', `transacoes-${inicio}-${fim}.csv`)} className="flex items-center gap-1.5 px-3 py-2 bg-slate-800 border border-slate-600 text-slate-300 rounded-lg text-sm hover:bg-slate-700 transition-colors">
              <Download className="w-3.5 h-3.5" /> Transações CSV
            </button>
            <button onClick={() => downloadCsv('categorias', 'categorias.csv')} className="flex items-center gap-1.5 px-3 py-2 bg-slate-800 border border-slate-600 text-slate-300 rounded-lg text-sm hover:bg-slate-700 transition-colors">
              <Download className="w-3.5 h-3.5" /> Categorias CSV
            </button>
            <button onClick={() => downloadCsv('contas', 'contas.csv')} className="flex items-center gap-1.5 px-3 py-2 bg-slate-800 border border-slate-600 text-slate-300 rounded-lg text-sm hover:bg-slate-700 transition-colors">
              <Download className="w-3.5 h-3.5" /> Contas CSV
            </button>
            <button onClick={() => downloadCsv('completo', 'dados-completos.csv')} className="flex items-center gap-1.5 px-3 py-2 bg-orange-500/20 border border-orange-500/30 text-orange-400 rounded-lg text-sm font-medium hover:bg-orange-500/30 transition-colors">
              <Download className="w-3.5 h-3.5" /> Exportar Tudo
            </button>
          </div>

          {data && (
            <>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
                {[
                  { label: 'Entradas', value: data.totalEntradas, icon: TrendingUp, color: 'text-green-400', bg: 'bg-green-500/10' },
                  { label: 'Saídas', value: data.totalSaidas, icon: TrendingDown, color: 'text-red-400', bg: 'bg-red-500/10' },
                  { label: 'Saldo', value: data.saldo, icon: DollarSign, color: data.saldo >= 0 ? 'text-green-400' : 'text-red-400', bg: data.saldo >= 0 ? 'bg-green-500/10' : 'bg-red-500/10' },
                  { label: 'Transações', value: data.totalTransacoes, icon: Receipt, color: 'text-blue-400', bg: 'bg-blue-500/10', isNum: true },
                ].map((kpi, i) => (
                  <div key={i} className={`${kpi.bg} border border-slate-700 rounded-xl p-4`}>
                    <div className="flex items-center gap-2 mb-2">
                      <kpi.icon className={`w-4 h-4 ${kpi.color}`} />
                      <span className="text-xs text-slate-400">{kpi.label}</span>
                    </div>
                    <p className={`text-xl font-bold ${kpi.isNum ? 'text-white' : kpi.color}`}>{kpi.isNum ? (kpi.value as number) : formatCurrency(kpi.value as number)}</p>
                  </div>
                ))}
              </div>

              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                <div className="bg-slate-800 border border-slate-700 rounded-2xl p-6">
                  <h3 className="text-lg font-semibold text-white mb-4">Gastos por Categoria</h3>
                  {data.gastosPorCategoria.length === 0 ? (
                    <p className="text-sm text-slate-400">Sem dados no período</p>
                  ) : (
                    <div className="space-y-3">
                      {data.gastosPorCategoria.map((c, i) => (
                        <div key={i} className="flex items-center gap-3">
                          <div className="w-3 h-3 rounded-full" style={{ backgroundColor: c.cor }} />
                          <span className="flex-1 text-sm text-white">{c.nome}</span>
                          <span className="text-sm text-red-400">{formatCurrency(c.valorTotal)}</span>
                          <span className="text-xs text-slate-500 w-10 text-right">{c.porcentagem}%</span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                <div className="bg-slate-800 border border-slate-700 rounded-2xl p-6">
                  <h3 className="text-lg font-semibold text-white mb-4">Gastos por Forma de Pagamento</h3>
                  {data.gastosPorConta.length === 0 ? (
                    <p className="text-sm text-slate-400">Sem dados no período</p>
                  ) : (
                    <div className="space-y-3">
                      {data.gastosPorConta.map((c, i) => (
                        <div key={i} className="space-y-1">
                          <div className="flex items-center justify-between">
                            <span className="text-sm text-white">{c.nome}</span>
                            <span className="text-sm text-slate-400">{c.tipo}</span>
                          </div>
                          <div className="flex items-center justify-between">
                            <div className="flex-1 h-2 bg-slate-700 rounded-full overflow-hidden mr-3">
                              <div className="h-full bg-blue-500 rounded-full" style={{ width: `${Math.min(c.porcentagem, 100)}%` }} />
                            </div>
                            <span className="text-sm text-red-400 w-20 text-right">{formatCurrency(c.valorTotal)}</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                <div className="bg-slate-800 border border-slate-700 rounded-2xl p-6 lg:col-span-2">
                  <h3 className="text-lg font-semibold text-white mb-4">Maiores Despesas</h3>
                  {data.maioresDespesas.length === 0 ? (
                    <p className="text-sm text-slate-400">Sem dados no período</p>
                  ) : (
                    <div className="space-y-2">
                      {data.maioresDespesas.map((d, i) => (
                        <div key={i} className="flex items-center justify-between p-3 bg-slate-900/50 rounded-lg border border-slate-700">
                          <div className="flex items-center gap-3">
                            <div className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ backgroundColor: d.categoriaCor + '20' }}>
                              <span className="text-xs" style={{ color: d.categoriaCor }}>{d.categoriaNome?.charAt(0) || '?'}</span>
                            </div>
                            <div>
                              <p className="text-sm text-white">{d.descricao}</p>
                              <p className="text-xs text-slate-400">{d.categoriaNome || 'Sem categoria'} • {new Date(d.data).toLocaleDateString('pt-BR')}</p>
                            </div>
                          </div>
                          <p className="text-sm font-semibold text-red-400">{formatCurrency(d.valor)}</p>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </>
          )}

          {!data && !loading && (
            <div className="text-center py-16 bg-slate-800/50 border border-slate-700 rounded-2xl">
              <Search className="w-12 h-12 text-slate-500 mx-auto mb-4" />
              <p className="text-slate-400">Selecione um período e gere o relatório</p>
            </div>
          )}
        </div>
      </div>
    </Layout>
  );
}
