import { useState, useEffect } from 'react';
import Layout from '../components/Layout';
import toast from 'react-hot-toast';
import { useAuth } from '../context/AuthContext';
// --- 1. IMPORTAMOS O TIPO DO SERVICE ---
import dashboardService, { EvolucaoMensal, ProjecaoMensal } from '../services/dashboardService';
import { useApi } from '../hooks/useApi';

import GraficoGastosPorCategoria from '../components/GraficoGastosPorCategoria';
import GraficoEvolucaoMensal from '../components/GraficoEvolucaoMensal';
import { formatCurrency } from '../utils/currency';
import { insightsService, InsightsResponse } from '../services/insightsService';
import { useState } from 'react';

export default function Dashboard() {
  const { usuario } = useAuth();
  const [insights, setInsights] = useState<InsightsResponse | null>(null);

  const { data, loading, error, refetch } = useApi(
    async (signal) => {
      if (!usuario?.id) {
        return null;
      }

      const [resumoData, gastosData, evolucaoData, projecaoData] = await Promise.all([
        dashboardService.resumo(signal),
        dashboardService.gastosPorCategoria(signal),
        dashboardService.evolucaoMensal(signal),
        dashboardService.projecao(signal),
      ]);

      return {
        resumo: resumoData,
        gastosPorCategoria: gastosData,
        evolucaoMensal: evolucaoData,
        projecao: projecaoData,
      };
    },
    {
      immediate: !!usuario?.id,
      deps: [usuario?.id],
    }
  );

  const resumo = data?.resumo;
  const gastosPorCategoria = (data?.gastosPorCategoria || []) as any[];
  const evolucaoMensal = (data?.evolucaoMensal || []) as EvolucaoMensal[];
  const projecao = data?.projecao;

  useEffect(() => {
    if (error) {
      toast.error('Erro ao carregar dashboard');
    }
  }, [error]);

  useEffect(() => {
    if (usuario?.id) {
      insightsService.gerar().then(setInsights).catch(() => {});
    }
  }, [usuario?.id]);

  const formatarMoedaComSinal = (valor: number) => {
    const valorNumerico = typeof valor === 'number' ? valor : 0;
    if (valorNumerico < 0) {
      return `-${formatCurrency(Math.abs(valorNumerico))}`;
    }
    return formatCurrency(valorNumerico);
  };

  if (loading) {
    return (
      <Layout>
        <div className="flex items-center justify-center min-h-screen">
          <div className="text-center">
            <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-orange-500"></div>
            <p className="mt-4 text-gray-400">Carregando dashboard...</p>
          </div>
        </div>
      </Layout>
    );
  }

  if (!resumo) {
    return (
      <Layout>
        <div className="flex items-center justify-center min-h-screen">
          <div className="text-center p-8 bg-slate-800 rounded-lg">
            <h3 className="text-xl text-white font-bold mb-2">Ops!</h3>
            <p className="text-red-400">Não foi possível carregar os dados do dashboard.</p>
            <button
              type="button"
              onClick={() => refetch()}
              className="mt-4 px-4 py-2 rounded-lg bg-orange-500 hover:bg-orange-600 text-white"
            >
              Tentar novamente
            </button>
          </div>
        </div>
      </Layout>
    );
  }

  return (
    <Layout>
      <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 p-8">
        <div className="max-w-7xl mx-auto space-y-6">

          {/* Header */}
          <div className="mb-8">
            <h1 className="text-4xl font-bold text-white mb-2">Dashboard Financeiro</h1>
            <p className="text-gray-400">Visão geral das suas finanças</p>
          </div>

          {/* KPI Cards (3 colunas em 'md') */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
             {/* Saldo Total */}
             <div className={`rounded-2xl p-6 shadow-2xl transform hover:scale-105 transition-transform ${
              (resumo.saldoCarteiras || 0) >= 0
                ? 'bg-gradient-to-br from-orange-500 to-orange-600'
                : 'bg-gradient-to-br from-red-500 to-red-600'
              }`}>
              <div className="flex justify-between items-start mb-4">
                <span className="text-white/80 text-sm font-medium">Saldo Total</span>
                <span className="text-2xl">💰</span>
              </div>
              <div className="text-4xl font-bold text-white mb-2">
                {formatCurrency(resumo.saldoCarteiras)}
              </div>
              <div className={`text-sm flex items-center gap-1 text-white/90`}>
                {(resumo.saldo || 0) >= 0 ? '↑' : '↓'}
                <span>Fluxo do mês: {formatarMoedaComSinal(resumo.saldo)}</span>
              </div>
            </div>

            {/* Total Entradas */}
            <div className="bg-slate-800 rounded-2xl p-6 shadow-xl hover:shadow-2xl transition-shadow border border-slate-700">
              <div className="flex justify-between items-start mb-4">
                <span className="text-gray-400 text-sm font-medium">Total Entradas</span>
                <span className="text-2xl">📈</span>
              </div>
              <div className="text-4xl font-bold text-blue-400 mb-2">
                {formatCurrency(resumo.totalEntradas)}
              </div>
              <div className="text-sm text-green-400 flex items-center gap-1">
                <span>Receitas do mês</span>
              </div>
            </div>

            {/* Total Despesas */}
            <div className="bg-slate-800 rounded-2xl p-6 shadow-xl hover:shadow-2xl transition-shadow border border-slate-700">
              <div className="flex justify-between items-start mb-4">
                <span className="text-gray-400 text-sm font-medium">Total Despesas</span>
                <span className="text-2xl">📉</span>
              </div>
              <div className="text-4xl font-bold text-red-400 mb-2">
                {formatCurrency(resumo.totalSaidas)}
              </div>
              <div className="text-sm text-red-400 flex items-center gap-1">
                <span>Gastos do mês</span>
              </div>
            </div>
          </div>

          {/* Cards Secundários (3 colunas em 'md') */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {/* Cartões */}
            <div className="bg-slate-800 rounded-2xl p-5 shadow-xl border border-slate-700">
              <div className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-xl bg-blue-500/20 flex items-center justify-center text-2xl">
                  💳
                </div>
                <div>
                  <div className="text-gray-400 text-sm">Cartões</div>
                  <div className="text-2xl font-bold text-white">{resumo.totalContas}</div>
                </div>
              </div>
            </div>
            {/* Metas */}
            <div className="bg-slate-800 rounded-2xl p-5 shadow-xl border border-slate-700">
              <div className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-xl bg-purple-500/20 flex items-center justify-center text-2xl">
                  🎯
                </div>
                <div>
                  <div className="text-gray-400 text-sm">Metas Ativas</div>
                  <div className="text-2xl font-bold text-white">{resumo.totalMetas}</div>
                </div>
              </div>
            </div>
            {/* Contas Fixas */}
            <div className="bg-slate-800 rounded-2xl p-5 shadow-xl border border-slate-700">
              <div className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-xl bg-orange-500/20 flex items-center justify-center text-2xl">
                  📋
                </div>
                <div>
                  <div className="text-gray-400 text-sm">Contas Fixas</div>
                  <div className="text-2xl font-bold text-white">{resumo.totalContasFixas}</div>
                </div>
              </div>
            </div>
          </div>

          {/* Seção de Gráficos (V8) */}
          <div className="flex flex-col md:flex-row gap-6 mt-6">
            
            <div className="md:w-1/3">
              <GraficoGastosPorCategoria chartData={gastosPorCategoria} />
            </div>

            <div className="md:w-2/3">
              <GraficoEvolucaoMensal chartData={evolucaoMensal} />
            </div>
          </div>

          {/* Seção de Projeção de Caixa */}
          {projecao && projecao.meses && projecao.meses.length > 0 && (
            <div className="mt-6">
              <h2 className="text-xl font-bold text-white mb-4">Projeção de Caixa</h2>
              <div className="overflow-x-auto">
                <table className="w-full bg-slate-800 border border-slate-700 rounded-2xl overflow-hidden">
                  <thead>
                    <tr className="border-b border-slate-700">
                      <th className="text-left p-3 text-xs text-slate-400 uppercase">Mês</th>
                      <th className="text-right p-3 text-xs text-slate-400 uppercase">Contas Fixas</th>
                      <th className="text-right p-3 text-xs text-slate-400 uppercase">Parcelas</th>
                      <th className="text-right p-3 text-xs text-slate-400 uppercase">Total Saídas</th>
                      <th className="text-right p-3 text-xs text-slate-400 uppercase">Saldo Final</th>
                    </tr>
                  </thead>
                  <tbody>
                    {projecao.meses.map((m: ProjecaoMensal, i: number) => (
                      <tr key={i} className={`border-b border-slate-700/50 ${i === 0 ? 'bg-slate-700/30' : ''}`}>
                        <td className="p-3 text-sm text-white font-medium">{m.periodo}</td>
                        <td className="p-3 text-sm text-right text-red-400">{formatCurrency(m.totalContasFixas)}</td>
                        <td className="p-3 text-sm text-right text-red-400">{formatCurrency(m.totalParcelas)}</td>
                        <td className="p-3 text-sm text-right text-red-400 font-semibold">{formatCurrency(m.totalSaidas)}</td>
                        <td className={`p-3 text-sm text-right font-bold ${m.saldoFinal >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                          {formatCurrency(m.saldoFinal)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {insights && (
            <div className="mt-6 bg-gradient-to-r from-slate-700 to-slate-800 border border-slate-600 rounded-2xl p-6">
              <h2 className="text-xl font-bold text-white mb-4">Insights Financeiros</h2>
              <p className="text-slate-300 mb-4">{insights.resumo}</p>
              {insights.categoriasAlerta.length > 0 && (
                <div className="mb-4">
                  <h3 className="text-sm font-semibold text-amber-400 mb-2">Categorias com gasto elevado</h3>
                  <div className="space-y-1">
                    {insights.categoriasAlerta.map((a, i) => (
                      <div key={i} className="flex justify-between text-sm">
                        <span className="text-slate-300">{a.categoriaNome}</span>
                        <span className={a.acimaMedia ? 'text-red-400' : 'text-yellow-400'}>
                          {formatCurrency(a.gastoAtual)} ({a.variacaoPercentual >= 0 ? '+' : ''}{a.variacaoPercentual?.toFixed(0)}%)
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
              <div>
                <h3 className="text-sm font-semibold text-green-400 mb-2">Recomendações</h3>
                <ul className="space-y-1">
                  {insights.recomendacoes.map((r, i) => (
                    <li key={i} className="text-sm text-slate-300 flex gap-2">
                      <span className="text-green-400">~</span> {r}
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          )}

        </div>
      </div>
    </Layout>
  );
}