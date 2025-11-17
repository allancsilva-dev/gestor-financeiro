import { useState, useEffect } from 'react';
import Layout from '../components/Layout';
import toast from 'react-hot-toast';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';

export default function Dashboard() {
  const [resumo, setResumo] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const { usuario } = useAuth();

  useEffect(() => {
    carregarDados();
  }, []);

  const carregarDados = async () => {
    try {
      setLoading(true);
      const response = await api.get('/dashboard/resumo');
      setResumo(response.data);
    } catch (error: any) {
      toast.error('Erro ao carregar dashboard');
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const formatarMoeda = (valor: number) => {
    const valorAbsoluto = Math.abs(valor);
    const formatado = valorAbsoluto.toLocaleString('pt-BR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
    return valor < 0 ? `-${formatado}` : formatado;
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

  return (
    <Layout>
      <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 p-8">
        <div className="max-w-7xl mx-auto space-y-6">
          
          {/* Header */}
          <div className="mb-8">
            <h1 className="text-4xl font-bold text-white mb-2">Dashboard Financeiro</h1>
            <p className="text-gray-400">Visão geral das suas finanças</p>
          </div>

          {/* KPI Cards */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {/* Saldo Total - Card Destacado */}
            <div className={`rounded-2xl p-6 shadow-2xl transform hover:scale-105 transition-transform ${
              (resumo?.saldoCarteiras || 0) >= 0 
                ? 'bg-gradient-to-br from-orange-500 to-orange-600' 
                : 'bg-gradient-to-br from-red-500 to-red-600'
            }`}>
              <div className="flex justify-between items-start mb-4">
                <span className="text-white/80 text-sm font-medium">Saldo Total</span>
                <span className="text-2xl">💰</span>
              </div>
              <div className="text-4xl font-bold text-white mb-2">
                R$ {formatarMoeda(resumo?.saldoCarteiras || 0)}
              </div>
              <div className={`text-sm flex items-center gap-1 text-white/90`}>
                {(resumo?.saldo || 0) >= 0 ? '↑' : '↓'} 
                <span>Fluxo do mês: R$ {formatarMoeda(resumo?.saldo || 0)}</span>
              </div>
            </div>

            {/* Total Entradas */}
            <div className="bg-slate-800 rounded-2xl p-6 shadow-xl hover:shadow-2xl transition-shadow border border-slate-700">
              <div className="flex justify-between items-start mb-4">
                <span className="text-gray-400 text-sm font-medium">Total Entradas</span>
                <span className="text-2xl">📈</span>
              </div>
              <div className="text-4xl font-bold text-blue-400 mb-2">
                R$ {formatarMoeda(resumo?.totalEntradas || 0)}
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
                R$ {formatarMoeda(resumo?.totalSaidas || 0)}
              </div>
              <div className="text-sm text-red-400 flex items-center gap-1">
                <span>Gastos do mês</span>
              </div>
            </div>
          </div>

          {/* Cards Secundários */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="bg-slate-800 rounded-2xl p-5 shadow-xl border border-slate-700">
              <div className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-xl bg-blue-500/20 flex items-center justify-center text-2xl">
                  💳
                </div>
                <div>
                  <div className="text-gray-400 text-sm">Cartões</div>
                  <div className="text-2xl font-bold text-white">{resumo?.totalContas || 0}</div>
                </div>
              </div>
            </div>

            <div className="bg-slate-800 rounded-2xl p-5 shadow-xl border border-slate-700">
              <div className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-xl bg-purple-500/20 flex items-center justify-center text-2xl">
                  🎯
                </div>
                <div>
                  <div className="text-gray-400 text-sm">Metas Ativas</div>
                  <div className="text-2xl font-bold text-white">{resumo?.totalMetas || 0}</div>
                </div>
              </div>
            </div>

            <div className="bg-slate-800 rounded-2xl p-5 shadow-xl border border-slate-700">
              <div className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-xl bg-orange-500/20 flex items-center justify-center text-2xl">
                  📋
                </div>
                <div>
                  <div className="text-gray-400 text-sm">Contas Fixas</div>
                  <div className="text-2xl font-bold text-white">{resumo?.totalContasFixas || 0}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Layout>
  );
}