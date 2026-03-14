import { useState, useEffect } from 'react';
import Layout from '../components/Layout';
import carteiraService, { Carteira } from '../services/carteiraService.ts';
import { useAuth } from '../context/AuthContext';
import { toast } from 'react-hot-toast';
import { Wallet, Banknote, PiggyBank, Plus, Trash2, Edit2, ArrowDownCircle, ArrowUpCircle, Building2 } from 'lucide-react';
import CurrencyInput from '../components/CurrencyInput';
import { formatCurrency } from '../utils/currency';

const LISTA_BANCOS = [
  'Agibank',
  'Banco do Brasil',
  'Banco Original',
  'Banco Pan',
  'Banestes',
  'Banrisul',
  'BMG',
  'Bradesco',
  'BRB',
  'BTG Pactual',
  'C6 Bank',
  'Caixa Econômica Federal',
  'Inter',
  'Itaú',
  'Mercado Pago',
  'Neon',
  'Next',
  'Nubank',
  'Outro',
  'PagBank',
  'Picpay',
  'Safra',
  'Santander',
  'Sicoob',
  'Sicredi'
];

const CarteiraPage = () => {
  const { usuario } = useAuth();
  const [carteiras, setCarteiras] = useState<Carteira[]>([]);
  const [loading, setLoading] = useState(true);
  const [mostrarFormulario, setMostrarFormulario] = useState(false);
  const [editando, setEditando] = useState<Carteira | null>(null);
  const [saldoTotal, setSaldoTotal] = useState(0);
  
  const [nome, setNome] = useState('');
  const [tipo, setTipo] = useState<'DINHEIRO' | 'CONTA_BANCARIA' | 'POUPANCA'>('DINHEIRO');
  const [saldo, setSaldo] = useState('');
  const [banco, setBanco] = useState('');

  const [carteiraSelecionada, setCarteiraSelecionada] = useState<number | null>(null);
  const [valorMovimentacao, setValorMovimentacao] = useState('');
  const [tipoMovimentacao, setTipoMovimentacao] = useState<'adicionar' | 'remover'>('adicionar');
  const saldoNumerico = saldo ? parseFloat(saldo) : null;
  const valorMovimentacaoNumerico = valorMovimentacao ? parseFloat(valorMovimentacao) : null;

  useEffect(() => {
    if (usuario?.id) {
      carregarCarteiras();
    }
  }, [usuario]);

  const carregarCarteiras = async () => {
    if (!usuario?.id) return;

    try {
      setLoading(true);
      const [carteirasData, saldoData] = await Promise.all([
        carteiraService.listarCarteiras(usuario.id),
        carteiraService.calcularSaldoTotal(usuario.id)
      ]);
      setCarteiras(carteirasData);
      setSaldoTotal(saldoData);
    } catch (error) {
      console.error('Erro ao carregar carteiras:', error);
      toast.error('Erro ao carregar carteiras');
    } finally {
      setLoading(false);
    }
  };

  const abrirFormulario = (carteira?: Carteira) => {
    if (carteira) {
      setEditando(carteira);
      setNome(carteira.nome);
      setTipo(carteira.tipo);
      setSaldo(carteira.saldo.toString());
      setBanco(carteira.banco || '');
    } else {
      resetarFormulario();
    }
    setMostrarFormulario(true);
  };

  const resetarFormulario = () => {
    setEditando(null);
    setNome('');
    setTipo('DINHEIRO');
    setSaldo('');
    setBanco('');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!usuario?.id) {
      toast.error('Usuário não autenticado');
      return;
    }

    try {
      const carteiraData = {
        nome,
        tipo,
        saldo: parseFloat(saldo) || 0,
        banco: tipo === 'CONTA_BANCARIA' ? banco : undefined,
        usuario: { id: usuario.id }
      };

      if (editando) {
        await carteiraService.atualizar(editando.id, carteiraData);
        toast.success('Carteira atualizada com sucesso!');
      } else {
        await carteiraService.criar(carteiraData);
        toast.success('Carteira criada com sucesso!');
      }

      setMostrarFormulario(false);
      resetarFormulario();
      carregarCarteiras();
    } catch (error) {
      console.error('Erro ao salvar carteira:', error);
      toast.error('Erro ao salvar carteira');
    }
  };

  const handleDeletar = async (id: number) => {
    if (window.confirm('Deseja realmente deletar esta carteira?')) {
      try {
        await carteiraService.deletar(id);
        toast.success('Carteira deletada com sucesso!');
        carregarCarteiras();
      } catch (error) {
        console.error('Erro ao deletar carteira:', error);
        toast.error('Erro ao deletar carteira');
      }
    }
  };

  const handleMovimentacao = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!carteiraSelecionada || !valorMovimentacao) {
      toast.error('Preencha todos os campos');
      return;
    }

    try {
      const valor = parseFloat(valorMovimentacao);
      
      if (tipoMovimentacao === 'adicionar') {
        await carteiraService.adicionarDinheiro(carteiraSelecionada, valor);
        toast.success('Dinheiro adicionado com sucesso!');
      } else {
        await carteiraService.removerDinheiro(carteiraSelecionada, valor);
        toast.success('Dinheiro removido com sucesso!');
      }

      setCarteiraSelecionada(null);
      setValorMovimentacao('');
      carregarCarteiras();
    } catch (error) {
      console.error('Erro ao movimentar dinheiro:', error);
      toast.error('Erro ao movimentar dinheiro');
    }
  };

  const getIconeTipo = (tipo: string) => {
    switch (tipo) {
      case 'DINHEIRO':
        return <Banknote className="w-6 h-6" />;
      case 'CONTA_BANCARIA':
        return <Wallet className="w-6 h-6" />;
      case 'POUPANCA':
        return <PiggyBank className="w-6 h-6" />;
      default:
        return <Wallet className="w-6 h-6" />;
    }
  };

  const getCorTipo = (tipo: string) => {
    switch (tipo) {
      case 'DINHEIRO':
        return 'bg-green-500';
      case 'CONTA_BANCARIA':
        return 'bg-orange-500';
      case 'POUPANCA':
        return 'bg-purple-500';
      default:
        return 'bg-gray-500';
    }
  };

  const formatarTipo = (tipo: string) => {
    return tipo.replace('_', ' ').toLowerCase()
      .split(' ')
      .map(palavra => palavra.charAt(0).toUpperCase() + palavra.slice(1))
      .join(' ');
  };

  if (loading) {
    return (
      <Layout>
        <div className="flex items-center justify-center h-64">
          <div className="text-lg text-gray-400">Carregando...</div>
        </div>
      </Layout>
    );
  }

  return (
    <Layout>
      <div className="space-y-6">
        <div className="flex justify-between items-center">
          <div>
            <h1 className="text-3xl font-bold text-white">Carteira</h1>
            <p className="text-gray-400 mt-1">Gerencie seu dinheiro, contas bancárias e poupança</p>
          </div>
          <button
            onClick={() => abrirFormulario()}
            className="bg-gradient-to-r from-orange-500 to-orange-600 text-white px-4 py-2 rounded-lg hover:shadow-lg hover:shadow-orange-500/30 flex items-center gap-2 transition-all"
          >
            <Plus className="w-5 h-5" />
            Nova Carteira
          </button>
        </div>

        <div className="bg-gradient-to-r from-orange-500 to-orange-600 rounded-lg p-6 text-white shadow-lg">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-orange-100 text-sm font-medium">Saldo Total Disponível</p>
              <p className="text-4xl font-bold mt-2">{formatCurrency(saldoTotal)}</p>
              <p className="text-orange-100 text-sm mt-2">{carteiras.length} carteira(s)</p>
            </div>
            <div className="bg-white/20 p-4 rounded-full">
              <Wallet className="w-12 h-12" />
            </div>
          </div>
        </div>

        {mostrarFormulario && (
          <div className="bg-slate-800 border border-slate-700 rounded-lg shadow-xl p-6">
            <h2 className="text-xl font-bold text-white mb-4">
              {editando ? 'Editar Carteira' : 'Nova Carteira'}
            </h2>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-400 mb-2">
                    Nome *
                  </label>
                  <input
                    type="text"
                    value={nome}
                    onChange={(e) => setNome(e.target.value)}
                    className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-gray-500 focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
                    placeholder="Salário"
                    required
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-400 mb-2">
                    Tipo *
                  </label>
                  <select
                    value={tipo}
                    onChange={(e) => setTipo(e.target.value as any)}
                    className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
                    required
                  >
                    <option value="DINHEIRO">Dinheiro</option>
                    <option value="CONTA_BANCARIA">Conta Bancária</option>
                    <option value="POUPANCA">Poupança</option>
                  </select>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-400 mb-2">
                    Saldo Inicial *
                  </label>
                  <CurrencyInput
                    value={saldoNumerico}
                    onValueChange={(value) => setSaldo(value === null ? '' : value.toString())}
                    className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-gray-500 focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
                    placeholder="R$ 0,00"
                    required
                  />
                </div>

                {tipo === 'CONTA_BANCARIA' && (
                  <div>
                    <label className="block text-sm font-medium text-gray-400 mb-2">
                      Banco *
                    </label>
                    <div className="relative">
                      <Building2 className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-500 w-5 h-5" />
                      <select
                        value={banco}
                        onChange={(e) => setBanco(e.target.value)}
                        className="w-full bg-slate-900 border border-slate-700 rounded-lg pl-12 pr-4 py-3 text-white focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
                        required
                      >
                        <option value="">Selecione o banco</option>
                        {LISTA_BANCOS.map((nomeBanco) => (
                          <option key={nomeBanco} value={nomeBanco}>
                            {nomeBanco}
                          </option>
                        ))}
                      </select>
                    </div>
                  </div>
                )}
              </div>

              <div className="flex gap-3 justify-end pt-4">
                <button
                  type="button"
                  onClick={() => {
                    setMostrarFormulario(false);
                    resetarFormulario();
                  }}
                  className="px-4 py-2 bg-slate-700 border border-slate-600 rounded-lg text-white hover:bg-slate-600 transition"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-gradient-to-r from-orange-500 to-orange-600 text-white rounded-lg hover:shadow-lg hover:shadow-orange-500/30 transition"
                >
                  {editando ? 'Atualizar' : 'Criar'}
                </button>
              </div>
            </form>
          </div>
        )}

        {carteiras.length === 0 ? (
          <div className="text-center py-12 bg-slate-800 border border-slate-700 rounded-lg shadow">
            <Wallet className="w-16 h-16 text-gray-500 mx-auto mb-4" />
            <h3 className="text-lg font-medium text-white mb-2">
              Nenhuma carteira cadastrada
            </h3>
            <p className="text-gray-400 mb-4">
              Comece criando sua primeira carteira para gerenciar seu dinheiro
            </p>
            <button
              onClick={() => abrirFormulario()}
              className="bg-gradient-to-r from-orange-500 to-orange-600 text-white px-6 py-2 rounded-lg hover:shadow-lg hover:shadow-orange-500/30 inline-flex items-center gap-2 transition-all"
            >
              <Plus className="w-5 h-5" />
              Criar Primeira Carteira
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {carteiras.map((carteira) => (
              <div
                key={carteira.id}
                className="bg-slate-800 border border-slate-700 rounded-lg shadow-lg hover:shadow-xl transition-shadow p-6"
              >
                <div className="flex items-start justify-between mb-4">
                  <div className="flex items-center gap-3">
                    <div className={`${getCorTipo(carteira.tipo)} p-3 rounded-full text-white`}>
                      {getIconeTipo(carteira.tipo)}
                    </div>
                    <div>
                      <h3 className="font-bold text-white">{carteira.nome}</h3>
                      <p className="text-sm text-gray-400">{formatarTipo(carteira.tipo)}</p>
                      {carteira.banco && (
                        <p className="text-xs text-gray-500 mt-1">{carteira.banco}</p>
                      )}
                    </div>
                  </div>
                  <div className="flex gap-2">
                    <button
                      onClick={() => abrirFormulario(carteira)}
                      className="text-orange-400 hover:text-orange-300 p-1 transition"
                      title="Editar"
                    >
                      <Edit2 className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => handleDeletar(carteira.id)}
                      className="text-red-400 hover:text-red-300 p-1 transition"
                      title="Deletar"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>

                <div className="mb-4">
                  <p className="text-sm text-gray-400 mb-1">Saldo Atual</p>
                  <p className={`text-2xl font-bold ${carteira.saldo >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                    {formatCurrency(carteira.saldo)}
                  </p>
                </div>

                <div className="flex gap-2 mt-4 pt-4 border-t border-slate-700">
                  <button
                    onClick={() => {
                      setCarteiraSelecionada(carteira.id);
                      setTipoMovimentacao('adicionar');
                      setValorMovimentacao('');
                    }}
                    className="flex-1 bg-green-500/10 text-green-400 px-3 py-2 rounded-lg hover:bg-green-500/20 flex items-center justify-center gap-2 text-sm font-medium transition"
                  >
                    <ArrowUpCircle className="w-4 h-4" />
                    Adicionar
                  </button>
                  <button
                    onClick={() => {
                      setCarteiraSelecionada(carteira.id);
                      setTipoMovimentacao('remover');
                      setValorMovimentacao('');
                    }}
                    className="flex-1 bg-red-500/10 text-red-400 px-3 py-2 rounded-lg hover:bg-red-500/20 flex items-center justify-center gap-2 text-sm font-medium transition"
                  >
                    <ArrowDownCircle className="w-4 h-4" />
                    Remover
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {carteiraSelecionada && (
          <div className="fixed inset-0 bg-black bg-opacity-70 flex items-center justify-center p-4 z-50">
            <div className="bg-slate-800 border border-slate-700 rounded-lg shadow-2xl max-w-md w-full p-6">
              <h2 className="text-xl font-bold text-white mb-4">
                {tipoMovimentacao === 'adicionar' ? 'Adicionar Dinheiro' : 'Remover Dinheiro'}
              </h2>
              <form onSubmit={handleMovimentacao} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-400 mb-2">
                    Valor (R$)
                  </label>
                  <CurrencyInput
                    value={valorMovimentacaoNumerico}
                    onValueChange={(value) => setValorMovimentacao(value === null ? '' : value.toString())}
                    className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-gray-500 focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
                    placeholder="R$ 0,00"
                    required
                    autoFocus
                  />
                </div>
                <div className="flex gap-3 justify-end">
                  <button
                    type="button"
                    onClick={() => {
                      setCarteiraSelecionada(null);
                      setValorMovimentacao('');
                    }}
                    className="px-4 py-2 bg-slate-700 border border-slate-600 rounded-lg text-white hover:bg-slate-600 transition"
                  >
                    Cancelar
                  </button>
                  <button
                    type="submit"
                    className={`px-4 py-2 rounded-lg text-white transition ${
                      tipoMovimentacao === 'adicionar'
                        ? 'bg-green-600 hover:bg-green-700'
                        : 'bg-red-600 hover:bg-red-700'
                    }`}
                  >
                    Confirmar
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
    </Layout>
  );
};

export default CarteiraPage;