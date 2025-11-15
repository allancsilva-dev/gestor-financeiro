import { useState, useEffect } from 'react';
import { dashboardService } from '../services/dashboardService';
import { transacaoService } from '../services/transacaoService';
import { metaService } from '../services/metaService';
import Layout from '../components/Layout';
import { PieChart, Pie, Cell, ResponsiveContainer, Legend, Tooltip } from 'recharts';
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
      setTransacoes(transacoesData.slice(0, 5)); // Últimas 5
      setMetas(metasData.slice(0, 3)); // Top 3
    } catch (error: any) {
      toast.error('Erro ao carregar dashboard');
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const prepararDadosGrafico = () => {
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

    return Object.values(categorias);
  };

  const calcularProgresso = (meta: any) => {
    return ((meta.valorReservado || 0) / meta.valorTotal) * 100;
  };

  if (loading) {
    return (
      <Layout>
        <div className="flex items-center justify-center min-h-screen">
          <div className="text-center">
            <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
            <p className="mt-4 text-gray-600">Carregando dashboard...</p>
          </div>
        </div>
      </Layout>
    );
  }

  const dadosGrafico = prepararDadosGrafico();

  return (
    <Layout>
      <div className="p-8">
        <div className="max-w-7xl mx-auto">
          
          <h1 className="text-3xl font-bold text-gray-800 mb-8">Dashboard</h1>

          {/* Cards de Resumo */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
            <div className="bg-white rounded-lg shadow-md p-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm text-gray-600">Total Entradas</p>
                  <p className="text-2xl font-bold text-green-600">
                    R$ {(resumo?.totalEntradas || 0).toFixed(2)}
                  </p>
                </div>
                <div className="w-12 h-12 bg-green-100 rounded-full flex items-center justify-center">
                  <span className="text-2xl">💰</span>
                </div>
              </div>
            </div>

            <div className="bg-white rounded-lg shadow-md p-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm text-gray-600">Total Saídas</p>
                  <p className="text-2xl font-bold text-red-600">
                    R$ {(resumo?.totalSaidas || 0).toFixed(2)}
                  </p>
                </div>
                <div className="w-12 h-12 bg-red-100 rounded-full flex items-center justify-center">
                  <span className="text-2xl">💸</span>
                </div>
              </div>
            </div>

            <div className="bg-white rounded-lg shadow-md p-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm text-gray-600">Saldo</p>
                  <p className={`text-2xl font-bold ${(resumo?.saldo || 0) >= 0 ? 'text-blue-600' : 'text-red-600'}`}>
                    R$ {(resumo?.saldo || 0).toFixed(2)}
                  </p>
                </div>
                <div className="w-12 h-12 bg-blue-100 rounded-full flex items-center justify-center">
                  <span className="text-2xl">📊</span>
                </div>
              </div>
            </div>

            <div className="bg-white rounded-lg shadow-md p-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm text-gray-600">Metas Ativas</p>
                  <p className="text-2xl font-bold text-purple-600">
                    {resumo?.totalMetas || 0}
                  </p>
                </div>
                <div className="w-12 h-12 bg-purple-100 rounded-full flex items-center justify-center">
                  <span className="text-2xl">🎯</span>
                </div>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 mb-8">
            
            {/* Gráfico de Pizza */}
            <div className="bg-white rounded-lg shadow-md p-6">
              <h2 className="text-xl font-bold text-gray-800 mb-4">Gastos por Categoria</h2>
              
              {dadosGrafico.length === 0 ? (
                <div className="text-center py-12 text-gray-500">
                  Nenhuma transação de saída cadastrada
                </div>
              ) : (
                <ResponsiveContainer width="100%" height={300}>
                  <PieChart>
                    <Pie
                      data={dadosGrafico}
                      cx="50%"
                      cy="50%"
                      labelLine={false}
                      label={({ nome, percent }: any) => `${nome}: ${(percent * 100).toFixed(0)}%`}
                      outerRadius={80}
                      fill="#8884d8"
                      dataKey="valor"
                    >
                      {dadosGrafico.map((entry: any, index: number) => (
                        <Cell key={`cell-${index}`} fill={entry.cor} />
                      ))}
                    </Pie>
                    <Tooltip formatter={(value: any) => `R$ ${value.toFixed(2)}`} />
                    <Legend />
                  </PieChart>
                </ResponsiveContainer>
              )}
            </div>

            {/* Metas em Progresso */}
            <div className="bg-white rounded-lg shadow-md p-6">
              <h2 className="text-xl font-bold text-gray-800 mb-4">Metas em Progresso</h2>
              
              {metas.length === 0 ? (
                <div className="text-center py-12 text-gray-500">
                  Nenhuma meta cadastrada
                </div>
              ) : (
                <div className="space-y-4">
                  {metas.map((meta) => (
                    <div key={meta.id}>
                      <div className="flex justify-between text-sm mb-1">
                        <span className="font-medium text-gray-700">{meta.nome}</span>
                        <span className="text-gray-600">{calcularProgresso(meta).toFixed(0)}%</span>
                      </div>
                      <div className="w-full bg-gray-200 rounded-full h-2">
                        <div
                          className="h-2 rounded-full transition-all"
                          style={{ 
                            width: `${Math.min(calcularProgresso(meta), 100)}%`,
                            backgroundColor: meta.cor || '#3498DB'
                          }}
                        ></div>
                      </div>
                      <div className="flex justify-between text-xs text-gray-500 mt-1">
                        <span>R$ {(meta.valorReservado || 0).toFixed(2)}</span>
                        <span>R$ {meta.valorTotal.toFixed(2)}</span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Últimas Transações */}
          <div className="bg-white rounded-lg shadow-md overflow-hidden">
            <div className="px-6 py-4 border-b">
              <h2 className="text-xl font-bold text-gray-800">Últimas Transações</h2>
            </div>
            
            {transacoes.length === 0 ? (
              <div className="text-center py-12 text-gray-500">
                Nenhuma transação cadastrada
              </div>
            ) : (
              <table className="w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Data</th>
                    <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Descrição</th>
                    <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Categoria</th>
                    <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Valor</th>
                  </tr>
                </thead>
                <tbody>
                  {transacoes.map((t) => (
                    <tr key={t.id} className="border-t border-gray-200 hover:bg-gray-50">
                      <td className="px-6 py-4 text-sm text-gray-700">
                        {new Date(t.data).toLocaleDateString('pt-BR')}
                      </td>
                      <td className="px-6 py-4">
                        <p className="font-medium text-gray-800">{t.descricao}</p>
                        {t.parcelado && (
                          <p className="text-xs text-gray-500">
                            {t.totalParcelas}x de R$ {(t.valorParcela || 0).toFixed(2)}
                          </p>
                        )}
                      </td>
                      <td className="px-6 py-4">
                        <span className="inline-flex items-center gap-2">
                          <span
                            className="w-3 h-3 rounded-full"
                            style={{ backgroundColor: t.categoria?.cor || '#666' }}
                          ></span>
                          <span className="text-sm text-gray-700">{t.categoria?.nome || 'N/A'}</span>
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        <span className={`font-semibold ${t.tipo === 'ENTRADA' ? 'text-green-600' : 'text-red-600'}`}>
                          {t.tipo === 'ENTRADA' ? '+' : '-'} R$ {(t.valorTotal || 0).toFixed(2)}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      </div>
    </Layout>
  );
}