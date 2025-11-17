import { useState, useEffect } from 'react';
import Layout from '../components/Layout';
import carteiraService, { Carteira } from '../services/carteiraService.ts';
import { useAuth } from '../context/AuthContext';
import { toast } from 'react-hot-toast';
import { Wallet, Banknote, PiggyBank, Plus, Trash2, Edit2, ArrowDownCircle, ArrowUpCircle } from 'lucide-react';

const CarteiraPage = () => {
  const { usuario } = useAuth();
  const [carteiras, setCarteiras] = useState<Carteira[]>([]);
  const [loading, setLoading] = useState(true);
  const [mostrarFormulario, setMostrarFormulario] = useState(false);
  const [editando, setEditando] = useState<Carteira | null>(null);
  const [saldoTotal, setSaldoTotal] = useState(0);
  
  // Estados do formulário
  const [nome, setNome] = useState('');
  const [tipo, setTipo] = useState<'DINHEIRO' | 'CONTA_BANCARIA' | 'POUPANCA'>('DINHEIRO');
  const [saldo, setSaldo] = useState('');
  const [banco, setBanco] = useState('');

  // Estados de movimentação
  const [carteiraSelecionada, setCarteiraSelecionada] = useState<number | null>(null);
  const [valorMovimentacao, setValorMovimentacao] = useState('');
  const [tipoMovimentacao, setTipoMovimentacao] = useState<'adicionar' | 'remover'>('adicionar');

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
        return 'bg-blue-500';
      case 'POUPANCA':
        return 'bg-purple-500';
      default:
        return 'bg-gray-500';
    }
  };

  const formatarMoeda = (valor: number) => {
    return valor.toLocaleString('pt-BR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
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
          <div className="text-lg text-gray-600">Carregando...</div>
        </div>
      </Layout>
    );
  }

  return (
    <Layout>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex justify-between items-center">
          <div>
            <h1 className="text-3xl font-bold text-gray-900">Carteira</h1>
            <p className="text-gray-600 mt-1">Gerencie seu dinheiro, contas bancárias e poupança</p>
          </div>
          <button
            onClick={() => abrirFormulario()}
            className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 flex items-center gap-2"
          >
            <Plus className="w-5 h-5" />
            Nova Carteira
          </button>
        </div>

        {/* Card de Saldo Total */}
        <div className="bg-gradient-to-r from-blue-600 to-blue-700 rounded-lg p-6 text-white shadow-lg">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-blue-100 text-sm font-medium">Saldo Total Disponível</p>
              <p className="text-4xl font-bold mt-2">R$ {formatarMoeda(saldoTotal)}</p>
              <p className="text-blue-100 text-sm mt-2">{carteiras.length} carteira(s)</p>
            </div>
            <div className="bg-white/20 p-4 rounded-full">
              <Wallet className="w-12 h-12" />
            </div>
          </div>
        </div>

        {/* Formulário */}
        {mostrarFormulario && (
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-xl font-bold mb-4">
              {editando ? 'Editar Carteira' : 'Nova Carteira'}
            </h2>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Nome *
                  </label>
                  <input
                    type="text"
                    value={nome}
                    onChange={(e) => setNome(e.target.value)}
                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    placeholder="Ex: Carteira Principal"
                    required
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Tipo *
                  </label>
                  <select
                    value={tipo}
                    onChange={(e) => setTipo(e.target.value as any)}
                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    required
                  >
                    <option value="DINHEIRO">Dinheiro</option>
                    <option value="CONTA_BANCARIA">Conta Bancária</option>
                    <option value="POUPANCA">Poupança</option>
                  </select>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Saldo Inicial *
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    value={saldo}
                    onChange={(e) => setSaldo(e.target.value)}
                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    placeholder="0,00"
                    required
                  />
                </div>

                {tipo === 'CONTA_BANCARIA' && (
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                      Banco
                    </label>
                    <input
                      type="text"
                      value={banco}
                      onChange={(e) => setBanco(e.target.value)}
                      className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      placeholder="Ex: Banco do Brasil"
                    />
                  </div>
                )}
              </div>

              <div className="flex gap-3 justify-end">
                <button
                  type="button"
                  onClick={() => {
                    setMostrarFormulario(false);
                    resetarFormulario();
                  }}
                  className="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                >
                  {editando ? 'Atualizar' : 'Criar'}
                </button>
              </div>
            </form>
          </div>
        )}

        {/* Lista de Carteiras */}
        {carteiras.length === 0 ? (
          <div className="text-center py-12 bg-white rounded-lg shadow">
            <Wallet className="w-16 h-16 text-gray-400 mx-auto mb-4" />
            <h3 className="text-lg font-medium text-gray-900 mb-2">
              Nenhuma carteira cadastrada
            </h3>
            <p className="text-gray-600 mb-4">
              Comece criando sua primeira carteira para gerenciar seu dinheiro
            </p>
            <button
              onClick={() => abrirFormulario()}
              className="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 inline-flex items-center gap-2"
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
                className="bg-white rounded-lg shadow hover:shadow-lg transition-shadow p-6"
              >
                {/* Header do Card */}
                <div className="flex items-start justify-between mb-4">
                  <div className="flex items-center gap-3">
                    <div className={`${getCorTipo(carteira.tipo)} p-3 rounded-full text-white`}>
                      {getIconeTipo(carteira.tipo)}
                    </div>
                    <div>
                      <h3 className="font-bold text-gray-900">{carteira.nome}</h3>
                      <p className="text-sm text-gray-600">{formatarTipo(carteira.tipo)}</p>
                      {carteira.banco && (
                        <p className="text-xs text-gray-500 mt-1">{carteira.banco}</p>
                      )}
                    </div>
                  </div>
                  <div className="flex gap-2">
                    <button
                      onClick={() => abrirFormulario(carteira)}
                      className="text-blue-600 hover:text-blue-700 p-1"
                      title="Editar"
                    >
                      <Edit2 className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => handleDeletar(carteira.id)}
                      className="text-red-600 hover:text-red-700 p-1"
                      title="Deletar"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>

                {/* Saldo */}
                <div className="mb-4">
                  <p className="text-sm text-gray-600 mb-1">Saldo Atual</p>
                  <p className={`text-2xl font-bold ${carteira.saldo >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                    R$ {formatarMoeda(carteira.saldo)}
                  </p>
                </div>

                {/* Botões de Movimentação */}
                <div className="flex gap-2 mt-4 pt-4 border-t border-gray-200">
                  <button
                    onClick={() => {
                      setCarteiraSelecionada(carteira.id);
                      setTipoMovimentacao('adicionar');
                      setValorMovimentacao('');
                    }}
                    className="flex-1 bg-green-50 text-green-700 px-3 py-2 rounded-lg hover:bg-green-100 flex items-center justify-center gap-2 text-sm font-medium"
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
                    className="flex-1 bg-red-50 text-red-700 px-3 py-2 rounded-lg hover:bg-red-100 flex items-center justify-center gap-2 text-sm font-medium"
                  >
                    <ArrowDownCircle className="w-4 h-4" />
                    Remover
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Modal de Movimentação */}
        {carteiraSelecionada && (
          <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
            <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
              <h2 className="text-xl font-bold mb-4">
                {tipoMovimentacao === 'adicionar' ? 'Adicionar Dinheiro' : 'Remover Dinheiro'}
              </h2>
              <form onSubmit={handleMovimentacao} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    Valor (R$)
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    value={valorMovimentacao}
                    onChange={(e) => setValorMovimentacao(e.target.value)}
                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    placeholder="0,00"
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
                    className="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50"
                  >
                    Cancelar
                  </button>
                  <button
                    type="submit"
                    className={`px-4 py-2 rounded-lg text-white ${
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