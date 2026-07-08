import { useState, useEffect } from 'react';
import Layout from '../components/Layout';
import toast from 'react-hot-toast';
import faturaService, { FaturaResponse, FaturaLancamento } from '../services/faturaService';
import { contaService, Conta } from '../services/contaService';
import { useAuth } from '../context/AuthContext';
import { formatCurrency } from '../utils/currency';
import { CreditCard, Calendar, DollarSign, CheckCircle, AlertCircle, ChevronLeft, ChevronRight } from 'lucide-react';

const MESES = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'];

export default function Faturas() {
  const { usuario } = useAuth();
  const [contasCredito, setContasCredito] = useState<Conta[]>([]);
  const [contaSelecionada, setContaSelecionada] = useState<Conta | null>(null);
  const [fatura, setFatura] = useState<FaturaResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [paying, setPaying] = useState(false);
  const now = new Date();
  const [mes, setMes] = useState(now.getMonth() + 1);
  const [ano, setAno] = useState(now.getFullYear());

  useEffect(() => {
    if (usuario?.id) {
      carregarContas();
    }
  }, [usuario?.id]);

  useEffect(() => {
    if (contaSelecionada?.id) {
      carregarFatura(contaSelecionada.id!, mes, ano);
    }
  }, [contaSelecionada, mes, ano]);

  const carregarContas = async () => {
    try {
      const contas = await contaService.listarPorUsuario(usuario!.id, 0, 50);
      const credito = contas.filter((c: Conta) => c.tipo === 'CREDITO');
      setContasCredito(credito);
      if (credito.length > 0 && !contaSelecionada) {
        setContaSelecionada(credito[0]);
      }
    } catch {
      toast.error('Erro ao carregar contas');
    }
  };

  const carregarFatura = async (contaId: number, m: number, a: number) => {
    setLoading(true);
    try {
      let data: FaturaResponse;
      if (m === now.getMonth() + 1 && a === now.getFullYear()) {
        data = await faturaService.buscarAtual(contaId);
      } else {
        data = await faturaService.buscarPorMes(contaId, m, a);
      }
      setFatura(data);
    } catch {
      setFatura(null);
    } finally {
      setLoading(false);
    }
  };

  const handlePagar = async () => {
    if (!fatura || fatura.valorTotal <= 0) return;
    setPaying(true);
    try {
      const result = await faturaService.pagarFatura(fatura.id, fatura.valorTotal);
      setFatura(result);
      toast.success('Fatura paga com sucesso!');
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Erro ao pagar fatura');
    } finally {
      setPaying(false);
    }
  };

  const mesAnterior = () => { if (mes === 1) { setMes(12); setAno(ano - 1); } else setMes(mes - 1); };
  const mesProximo = () => { if (mes === 12) { setMes(1); setAno(ano + 1); } else setMes(mes + 1); };

  return (
    <Layout>
      <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 p-8">
        <div className="max-w-4xl mx-auto">
          <div className="mb-6">
            <h1 className="text-2xl font-bold text-white">Faturas</h1>
            <p className="text-sm text-slate-400 mt-1">Acompanhe suas faturas de cartão de crédito</p>
          </div>

          {contasCredito.length === 0 ? (
            <div className="text-center py-16 bg-slate-800/50 border border-slate-700 rounded-2xl">
              <CreditCard className="w-12 h-12 text-slate-500 mx-auto mb-4" />
              <h3 className="text-lg font-semibold text-white mb-2">Nenhum cartão de crédito</h3>
              <p className="text-sm text-slate-400">Cadastre uma conta do tipo Crédito para ver faturas</p>
            </div>
          ) : (
            <>
              {/* Seleção de cartão */}
              {contasCredito.length > 1 && (
                <div className="flex gap-2 mb-6">
                  {contasCredito.map((c) => (
                    <button
                      key={c.id}
                      onClick={() => setContaSelecionada(c)}
                      className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${contaSelecionada?.id === c.id ? 'bg-orange-500 text-white' : 'bg-slate-800 text-slate-400 hover:bg-slate-700'}`}
                    >
                      {c.nome}
                    </button>
                  ))}
                </div>
              )}

              {/* Navegação de mês */}
              <div className="flex items-center justify-center gap-4 mb-6">
                <button onClick={mesAnterior} className="p-2 hover:bg-slate-700/50 rounded-lg transition-colors">
                  <ChevronLeft className="w-5 h-5 text-slate-400" />
                </button>
                <h2 className="text-lg font-semibold text-white min-w-40 text-center">{MESES[mes - 1]} {ano}</h2>
                <button onClick={mesProximo} className="p-2 hover:bg-slate-700/50 rounded-lg transition-colors">
                  <ChevronRight className="w-5 h-5 text-slate-400" />
                </button>
              </div>

              {loading ? (
                <div className="text-center py-12">
                  <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-orange-500" />
                </div>
              ) : fatura ? (
                <>
                  {/* Status Banner */}
                  <div className={`rounded-2xl p-6 mb-6 border ${fatura.status === 'PAGA' ? 'bg-green-500/10 border-green-500/30' : fatura.status === 'VENCIDA' ? 'bg-red-500/10 border-red-500/30' : 'bg-slate-800 border-slate-700'}`}>
                    <div className="flex items-center justify-between">
                      <div>
                        <div className="flex items-center gap-2 mb-1">
                          <CreditCard className={`w-5 h-5 ${fatura.status === 'PAGA' ? 'text-green-400' : 'text-orange-400'}`} />
                          <span className="text-white font-semibold">{contaSelecionada?.nome}</span>
                        </div>
                        <p className="text-sm text-slate-400">
                          Fechamento: {fatura.dataFechamento ? new Date(fatura.dataFechamento).toLocaleDateString('pt-BR') : '—'} | Vencimento: {fatura.dataVencimento ? new Date(fatura.dataVencimento).toLocaleDateString('pt-BR') : '—'}
                        </p>
                      </div>
                      <div className="text-right">
                        <p className="text-3xl font-bold text-white">{formatCurrency(fatura.valorTotal)}</p>
                        <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${fatura.status === 'PAGA' ? 'bg-green-500/20 text-green-400' : fatura.status === 'VENCIDA' ? 'bg-red-500/20 text-red-400' : 'bg-orange-500/20 text-orange-400'}`}>
                          {fatura.status === 'PAGA' ? 'Paga' : fatura.status === 'VENCIDA' ? 'Vencida' : 'Aberta'}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Botão Pagar */}
                  {fatura.status !== 'PAGA' && fatura.valorTotal > 0 && (
                    <div className="mb-6">
                      <button
                        onClick={handlePagar}
                        disabled={paying}
                        className="w-full bg-green-500 hover:bg-green-600 text-white py-3 rounded-xl font-semibold transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
                      >
                        {paying ? (
                          <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                        ) : (
                          <DollarSign className="w-5 h-5" />
                        )}
                        Pagar Fatura — {formatCurrency(fatura.valorTotal)}
                      </button>
                    </div>
                  )}

                  {/* Lançamentos */}
                  <div className="bg-slate-800 border border-slate-700 rounded-2xl p-6">
                    <h3 className="text-lg font-semibold text-white mb-4">Lançamentos</h3>
                    {fatura.lancamentos.length === 0 ? (
                      <p className="text-sm text-slate-400 text-center py-8">Nenhum lançamento nesta fatura</p>
                    ) : (
                      <div className="space-y-2">
                        {fatura.lancamentos.map((l: FaturaLancamento, i: number) => (
                          <div key={l.transacaoId || i} className="flex items-center justify-between p-3 bg-slate-900/50 rounded-lg border border-slate-700">
                            <div className="flex items-center gap-3">
                              <div className="w-8 h-8 rounded-lg flex items-center justify-center text-sm" style={{ backgroundColor: l.categoriaCor + '20' }}>
                                {l.categoriaIcone || '💳'}
                              </div>
                              <div>
                                <p className="text-sm text-white">{l.descricao}</p>
                                <p className="text-xs text-slate-400">
                                  {new Date(l.data).toLocaleDateString('pt-BR')}
                                  {l.totalParcelas && l.parcelaAtual ? ` • Parcela ${l.parcelaAtual}/${l.totalParcelas}` : ''}
                                </p>
                              </div>
                            </div>
                            <p className="text-sm font-semibold text-red-400">{formatCurrency(l.valor)}</p>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </>
              ) : (
                <div className="text-center py-16 bg-slate-800/50 border border-slate-700 rounded-2xl">
                  <p className="text-slate-400">Erro ao carregar fatura</p>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </Layout>
  );
}
