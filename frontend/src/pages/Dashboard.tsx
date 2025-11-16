import { useState, useEffect } from 'react';
import { dashboardService } from '../services/dashboardService';
import { transacaoService } from '../services/transacaoService';
import { metaService } from '../services/metaService';
import Layout from '../components/Layout';
import { LineChart, Line, BarChart, Bar, PieChart, Pie, Cell, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import toast from 'react-hot-toast';

export default function Dashboard() {
  const [resumo, setResumo] = useState<any>(null);
  const [transacoes, setTransacoes] = useState<any[]>([]);
  const [metas, setMetas] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    carregarDados();
  }, []);

  const carregarDados = async () => {
    try {
      setLoading(true);
      const [resumoData, transacoesData, metasData] = await Promise.all([
        dashboardService.obterResumo(1),
        transacaoService.listarPorUsuario(1),
        metaService.listarPorUsuario(1)
      ]);
      setResumo(resumoData);
      setTransacoes(transacoesData);
      setMetas(metasData);
    } catch (error: any) {
      toast.error('Erro ao carregar dashboard');
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const prepararDadosEntradasPorCategoria = () => {
    const categorias: any = {};
    
    transacoes
      .filter(t => t.tipo === 'ENTRADA')
      .forEach(t => {
        const nome = t.categoria?.nome || 'Sem categoria';
        if (!categorias[nome]) {
          categorias[nome] = 0;
        }
        categorias[nome] += t.valorTotal || 0;
      });

    return Object.entries(categorias)
      .map(([nome, valor]) => ({ nome, valor }))
      .sort((a: any, b: any) => b.valor - a.valor)
      .slice(0, 5);
  };

  const prepararDadosDespesasPorCategoria = () => {
    const categorias: any = {};
    
    transacoes
      .filter(t => t.tipo === 'SAIDA')
      .forEach(t => {
        const nome = t.categoria?.nome || 'Sem categoria';
        const cor = t.categoria?.cor || '#666';
        if (!categorias[nome]) {
          categorias[nome] = { nome, valor: 0, cor };
        }
        categorias[nome].valor += t.valorTotal || 0;
      });

    return Object.values(categorias)
      .sort((a: any, b: any) => b.valor - a.valor)
      .slice(0, 5);
  };

  const prepararDadosEvolucaoMensal = () => {
    // Agrupa transações por mês
    const meses: any = {};
    
    transacoes.forEach(t => {
      const data = new Date(t.data);
      const mes = data.toLocaleString('pt-BR', { month: 'short' });
      
      if (!meses[mes]) {
        meses[mes] = { mes, entradas: 0, saidas: 0 };
      }
      
      if (t.tipo === 'ENTRADA') {
        meses[mes].entradas += t.valorTotal || 0;
      } else {
        meses[mes].saidas += t.valorTotal || 0;
      }
    });

    return Object.values(meses);
  };

  const calcularProgresso = (meta: any) => {
    return ((meta.valorReservado || 0) / meta.valorTotal) * 100;
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

  const dadosEntradas = prepararDadosEntradasPorCategoria();
  const dadosDespesas = prepararDadosDespesasPorCategoria();
  const dadosEvolucao = prepararDadosEvolucaoMensal();

  const COLORS = ['#ef4444', '#f97316', '#f59e0b', '#06b6d4', '#a855f7', '#ec4899', '#8b5cf6'];

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
              ((resumo?.saldoCarteiras || 0) + (resumo?.saldo || 0)) >= 0 
                ? 'bg-gradient-to-br from-orange-500 to-orange-600' 
                : 'bg-gradient-to-br from-red-500 to-red-600'
            }`}>
              <div className="flex justify-between items-start mb-4">
                <span className="text-white/80 text-sm font-medium">Saldo Total</span>
                <span className="text-2xl">💰</span>
              </div>
              <div className="text-4xl font-bold text-white mb-2">
                R$ {formatarMoeda((resumo?.saldoCarteiras || 0) + (resumo?.saldo || 0))}
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

          {/* Gráficos */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Gráfico de Entradas */}
            <div className="bg-slate-800 rounded-2xl p-6 shadow-xl border border-slate-700">
              <h3 className="text-xl font-bold text-white mb-6">Top 5 Entradas por Categoria</h3>
              {dadosEntradas.length === 0 ? (
                <div className="text-center py-12 text-gray-500">
                  Nenhuma entrada cadastrada
                </div>
              ) : (
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart data={dadosEntradas}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                    <XAxis dataKey="nome" stroke="#94a3b8" />
                    <YAxis stroke="#94a3b8" />
                    <Tooltip 
                      contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155', borderRadius: '8px' }}
                      labelStyle={{ color: '#e2e8f0' }}
                      formatter={(value: any) => `R$ ${formatarMoeda(value)}`}
                    />
                    <Bar dataKey="valor" fill="#3b82f6" radius={[8, 8, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </div>

            {/* Gráfico de Despesas */}
            <div className="bg-slate-800 rounded-2xl p-6 shadow-xl border border-slate-700">
              <h3 className="text-xl font-bold text-white mb-6">Top 5 Despesas por Categoria</h3>
              {dadosDespesas.length === 0 ? (
                <div className="text-center py-12 text-gray-500">
                  Nenhuma despesa cadastrada
                </div>
              ) : (
                <ResponsiveContainer width="100%" height={300}>
                  <PieChart>
                    <Pie
                      data={dadosDespesas}
                      cx="50%"
                      cy="50%"
                      labelLine={false}
                      label={({ nome, percent }: any) => `${nome}: ${(percent * 100).toFixed(0)}%`}
                      outerRadius={100}
                      fill="#8884d8"
                      dataKey="valor"
                    >
                      {dadosDespesas.map((entry: any, index: number) => (
                        <Cell key={`cell-${index}`} fill={entry.cor || COLORS[index % COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip 
                      contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155', borderRadius: '8px' }}
                      formatter={(value: any) => `R$ ${formatarMoeda(value)}`}
                    />
                  </PieChart>
                </ResponsiveContainer>
              )}
            </div>
          </div>

          {/* Gráfico de Evolução Mensal */}
          <div className="bg-slate-800 rounded-2xl p-6 shadow-xl border border-slate-700">
            <h3 className="text-xl font-bold text-white mb-6">Evolução Mensal - Despesas vs Entradas</h3>
            {dadosEvolucao.length === 0 ? (
              <div className="text-center py-12 text-gray-500">
                Nenhuma transação cadastrada
              </div>
            ) : (
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={dadosEvolucao}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                  <XAxis dataKey="mes" stroke="#94a3b8" />
                  <YAxis stroke="#94a3b8" />
                  <Tooltip 
                    contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155', borderRadius: '8px' }}
                    labelStyle={{ color: '#e2e8f0' }}
                    formatter={(value: any) => `R$ ${formatarMoeda(value)}`}
                  />
                  <Legend />
                  <Bar dataKey="entradas" name="Entradas" fill="#3b82f6" radius={[8, 8, 0, 0]} />
                  <Bar dataKey="saidas" name="Despesas" fill="#ef4444" radius={[8, 8, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>

          {/* Metas em Progresso */}
          {metas.length > 0 && (
            <div className="bg-slate-800 rounded-2xl p-6 shadow-xl border border-slate-700">
              <h3 className="text-xl font-bold text-white mb-6">🎯 Metas em Progresso</h3>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                {metas.slice(0, 3).map((meta) => (
                  <div key={meta.id} className="bg-slate-900/50 rounded-xl p-5 border border-slate-700">
                    <div className="flex items-center gap-3 mb-4">
                      <div 
                        className="w-12 h-12 rounded-full flex items-center justify-center text-white font-bold text-lg"
                        style={{ backgroundColor: meta.cor || '#3498DB' }}
                      >
                        {meta.icone?.charAt(0).toUpperCase() || '🎯'}
                      </div>
                      <div className="flex-1">
                        <h4 className="font-bold text-white">{meta.nome}</h4>
                        <p className="text-sm text-gray-400">{calcularProgresso(meta).toFixed(0)}%</p>
                      </div>
                    </div>
                    <div className="w-full bg-slate-700 rounded-full h-3 mb-2">
                      <div
                        className="h-3 rounded-full transition-all"
                        style={{ 
                          width: `${Math.min(calcularProgresso(meta), 100)}%`,
                          backgroundColor: meta.cor || '#3498DB'
                        }}
                      ></div>
                    </div>
                    <div className="flex justify-between text-sm text-gray-400">
                      <span>R$ {formatarMoeda(meta.valorReservado || 0)}</span>
                      <span>R$ {formatarMoeda(meta.valorTotal)}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </Layout>
  );
}