import { useState, useEffect } from 'react';
import Layout from '../components/Layout';
import contaFixaService, { ContaFixa } from '../services/contaFixaService';
import { categoriaService } from '../services/categoriaService';
import { useAuth } from '../context/AuthContext';
import toast from 'react-hot-toast';
import { Plus, Edit2, Trash2, Check, X, Calendar, DollarSign, Tag } from 'lucide-react';
import CurrencyInput from '../components/CurrencyInput';
import { formatCurrency } from '../utils/currency';

export default function ContasFixas() {
  const { usuario } = useAuth();
  const [contasFixas, setContasFixas] = useState<ContaFixa[]>([]);
  const [categorias, setCategorias] = useState<any[]>([]);
  const [mostrarForm, setMostrarForm] = useState(false);
  const [editando, setEditando] = useState<ContaFixa | null>(null);
  const [loading, setLoading] = useState(false);
  const [pagandoConta, setPagandoConta] = useState<number | null>(null);
  const [valorPagamento, setValorPagamento] = useState('');

  const [formData, setFormData] = useState({
    nome: '',
    valorPlanejado: '',
    diaVencimento: '',
    categoriaId: '',
    observacoes: ''
  });

  useEffect(() => {
    if (usuario?.id) {
      carregarDados();
    }
  }, [usuario]);

  const carregarDados = async () => {
    if (!usuario?.id) return;

    try {
      setLoading(true);
      const [contasData, categoriasData] = await Promise.all([
        contaFixaService.listarAtivas(usuario.id),
        categoriaService.listarMinhas(0, 100)
      ]);
      setContasFixas(contasData);
      setCategorias(categoriasData);
    } catch (error: any) {
      toast.error('Erro ao carregar dados');
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const abrirFormulario = (conta?: ContaFixa) => {
    if (conta) {
      setEditando(conta);
      setFormData({
        nome: conta.nome,
        valorPlanejado: conta.valorPlanejado.toString(),
        diaVencimento: conta.diaVencimento.toString(),
        categoriaId: conta.categoria.id.toString(),
        observacoes: conta.observacoes || ''
      });
    } else {
      resetarFormulario();
    }
    setMostrarForm(true);
  };

  const resetarFormulario = () => {
    setEditando(null);
    setFormData({
      nome: '',
      valorPlanejado: '',
      diaVencimento: '',
      categoriaId: '',
      observacoes: ''
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!usuario?.id) {
      toast.error('Usuário não autenticado');
      return;
    }

    try {
      setLoading(true);

      const contaData = {
        nome: formData.nome,
        valorPlanejado: parseFloat(formData.valorPlanejado),
        diaVencimento: parseInt(formData.diaVencimento),
        observacoes: formData.observacoes,
        categoria: { id: parseInt(formData.categoriaId) },
        usuario: { id: usuario.id }
      };

      if (editando) {
        await contaFixaService.atualizar(editando.id, contaData);
        toast.success('Conta fixa atualizada!');
      } else {
        await contaFixaService.criar(contaData);
        toast.success('Conta fixa criada!');
      }

      setMostrarForm(false);
      resetarFormulario();
      carregarDados();
    } catch (error: any) {
      toast.error('Erro ao salvar conta fixa');
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const handleDeletar = async (id: number) => {
    if (!window.confirm('Deseja realmente deletar esta conta fixa?')) return;

    try {
      await contaFixaService.deletar(id);
      toast.success('Conta fixa deletada!');
      carregarDados();
    } catch (error: any) {
      toast.error('Erro ao deletar conta fixa');
      console.error(error);
    }
  };

  const handleMarcarComoPaga = async (id: number) => {
    if (!valorPagamento) {
      toast.error('Informe o valor pago');
      return;
    }

    try {
      await contaFixaService.marcarComoPaga(id, parseFloat(valorPagamento));
      toast.success('Conta marcada como paga!');
      setPagandoConta(null);
      setValorPagamento('');
      carregarDados();
    } catch (error: any) {
      toast.error('Erro ao marcar como paga');
      console.error(error);
    }
  };

  const calcularTotalPendente = () => {
    return contasFixas
      .filter(c => c.status === 'PENDENTE' || c.status === 'ATRASADO')
      .reduce((total, c) => total + c.valorPlanejado, 0);
  };

  const calcularTotalPago = () => {
    return contasFixas
      .filter(c => c.status === 'PAGO')
      .reduce((total, c) => total + (c.valorReal || c.valorPlanejado), 0);
  };

  const valorPlanejadoNumerico = formData.valorPlanejado ? parseFloat(formData.valorPlanejado) : null;
  const valorPagamentoNumerico = valorPagamento ? parseFloat(valorPagamento) : null;

  if (loading && contasFixas.length === 0) {
    return (
      <Layout>
        <div className="flex items-center justify-center min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900">
          <div className="text-center">
            <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-orange-500"></div>
            <p className="mt-4 text-gray-400">Carregando...</p>
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
          <div className="flex justify-between items-center">
            <div>
              <h1 className="text-4xl font-bold text-white mb-2">📋 Contas Fixas</h1>
              <p className="text-gray-400">Gerencie suas despesas recorrentes mensais</p>
            </div>
            <button
              onClick={() => abrirFormulario()}
              className="bg-gradient-to-r from-orange-500 to-orange-600 text-white px-6 py-3 rounded-xl hover:shadow-lg hover:shadow-orange-500/20 transition-all flex items-center gap-2 font-medium"
            >
              <Plus className="w-5 h-5" />
              Nova Conta Fixa
            </button>
          </div>

          {/* Cards de Resumo */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700">
              <div className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-xl bg-blue-500/20 flex items-center justify-center">
                  <DollarSign className="w-6 h-6 text-blue-400" />
                </div>
                <div>
                  <p className="text-gray-400 text-sm">Total de Contas</p>
                  <p className="text-2xl font-bold text-white">{contasFixas.length}</p>
                </div>
              </div>
            </div>

            <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700">
              <div className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-xl bg-red-500/20 flex items-center justify-center">
                  <X className="w-6 h-6 text-red-400" />
                </div>
                <div>
                  <p className="text-gray-400 text-sm">Pendentes</p>
                  <p className="text-2xl font-bold text-red-400">
                    {formatCurrency(calcularTotalPendente())}
                  </p>
                </div>
              </div>
            </div>

            <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700">
              <div className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-xl bg-green-500/20 flex items-center justify-center">
                  <Check className="w-6 h-6 text-green-400" />
                </div>
                <div>
                  <p className="text-gray-400 text-sm">Pagas</p>
                  <p className="text-2xl font-bold text-green-400">
                    {formatCurrency(calcularTotalPago())}
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* Formulário */}
          {mostrarForm && (
            <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700">
              <h2 className="text-2xl font-bold text-white mb-6">
                {editando ? 'Editar Conta Fixa' : 'Nova Conta Fixa'}
              </h2>

              <form onSubmit={handleSubmit} className="space-y-4">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-400 mb-2">
                      Nome da Conta *
                    </label>
                    <input
                      type="text"
                      value={formData.nome}
                      onChange={(e) => setFormData({ ...formData, nome: e.target.value })}
                      className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white focus:ring-2 focus:ring-orange-500 focus:border-transparent"
                      placeholder="Ex: Aluguel"
                      required
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-400 mb-2">
                      Valor Planejado (R$) *
                    </label>
                    <CurrencyInput
                      value={valorPlanejadoNumerico}
                      onValueChange={(value) => setFormData({ ...formData, valorPlanejado: value === null ? '' : value.toString() })}
                      className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white focus:ring-2 focus:ring-orange-500 focus:border-transparent"
                      placeholder="R$ 0,00"
                      required
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-400 mb-2">
                      Dia do Vencimento *
                    </label>
                    <select
                      value={formData.diaVencimento}
                      onChange={(e) => setFormData({ ...formData, diaVencimento: e.target.value })}
                      className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white focus:ring-2 focus:ring-orange-500 focus:border-transparent"
                      required
                    >
                      <option value="">Selecione...</option>
                      {Array.from({ length: 31 }, (_, i) => i + 1).map(dia => (
                        <option key={dia} value={dia}>Todo dia {dia}</option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-400 mb-2">
                      Categoria *
                    </label>
                    <select
                      value={formData.categoriaId}
                      onChange={(e) => setFormData({ ...formData, categoriaId: e.target.value })}
                      className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white focus:ring-2 focus:ring-orange-500 focus:border-transparent"
                      required
                    >
                      <option value="">Selecione...</option>
                      {categorias.map(cat => (
                        <option key={cat.id} value={cat.id}>{cat.nome}</option>
                      ))}
                    </select>
                  </div>
                </div>

                <div className="flex gap-3 justify-end">
                  <button
                    type="button"
                    onClick={() => {
                      setMostrarForm(false);
                      resetarFormulario();
                    }}
                    className="px-6 py-3 bg-slate-700 text-white rounded-xl hover:bg-slate-600 transition"
                  >
                    Cancelar
                  </button>
                  <button
                    type="submit"
                    disabled={loading}
                    className="px-6 py-3 bg-gradient-to-r from-orange-500 to-orange-600 text-white rounded-xl hover:shadow-lg hover:shadow-orange-500/20 transition disabled:opacity-50"
                  >
                    {loading ? 'Salvando...' : (editando ? 'Atualizar' : 'Criar')}
                  </button>
                </div>
              </form>
            </div>
          )}

          {/* Lista de Contas Fixas */}
          {contasFixas.length === 0 ? (
            <div className="bg-slate-800 rounded-2xl p-12 border border-slate-700 text-center">
              <div className="text-6xl mb-4">📋</div>
              <h3 className="text-xl font-bold text-white mb-2">
                Nenhuma conta fixa cadastrada
              </h3>
              <p className="text-gray-400 mb-6">
                Comece cadastrando suas despesas recorrentes mensais
              </p>
              <button
                onClick={() => abrirFormulario()}
                className="bg-gradient-to-r from-orange-500 to-orange-600 text-white px-6 py-3 rounded-xl hover:shadow-lg inline-flex items-center gap-2"
              >
                <Plus className="w-5 h-5" />
                Criar Primeira Conta Fixa
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {contasFixas.map((conta) => (
                <div
                  key={conta.id}
                  className={`bg-slate-800 rounded-2xl p-6 border ${
                    conta.status === 'PAGO' 
                      ? 'border-green-500/30' 
                      : conta.status === 'ATRASADO'
                      ? 'border-red-500/30'
                      : 'border-slate-700'
                  } hover:shadow-xl transition-all`}
                >
                  {/* Header */}
                  <div className="flex justify-between items-start mb-4">
                    <div className="flex items-center gap-3">
                      <div
                        className="w-3 h-3 rounded-full"
                        style={{ backgroundColor: conta.categoria.cor }}
                      ></div>
                      <div>
                        <h3 className="font-bold text-white text-lg">{conta.nome}</h3>
                        <p className="text-sm text-gray-400">{conta.categoria.nome}</p>
                      </div>
                    </div>
                    <div className="flex gap-2">
                      <button
                        onClick={() => abrirFormulario(conta)}
                        className="text-blue-400 hover:text-blue-300 p-1"
                      >
                        <Edit2 className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleDeletar(conta.id)}
                        className="text-red-400 hover:text-red-300 p-1"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>

                  {/* Valor */}
                  <div className="mb-4">
                    <p className="text-3xl font-bold text-white">
                      {formatCurrency(conta.valorPlanejado)}
                    </p>
                    <p className="text-sm text-gray-400 mt-1">
                      <Calendar className="w-4 h-4 inline mr-1" />
                      Vence todo dia {conta.diaVencimento}
                    </p>
                    <p className="text-xs text-gray-500 mt-1">
                      Próximo: {new Date(conta.dataProximoVencimento).toLocaleDateString('pt-BR')}
                    </p>
                  </div>

                  {/* Status/Ação */}
                  {conta.status === 'PAGO' ? (
                    <div className="bg-green-500/10 border border-green-500/30 rounded-xl p-3">
                      <div className="flex items-center gap-2 text-green-400">
                        <Check className="w-5 h-5" />
                        <span className="font-medium">Paga</span>
                      </div>
                      {conta.valorReal && (
                        <p className="text-sm text-gray-400 mt-1">
                          Valor pago: {formatCurrency(conta.valorReal)}
                        </p>
                      )}
                    </div>
                  ) : conta.status === 'ATRASADO' ? (
                    <div className="bg-red-500/10 border border-red-500/30 rounded-xl p-3 mb-3">
                      <div className="flex items-center gap-2 text-red-400">
                        <X className="w-5 h-5" />
                        <span className="font-medium">Atrasada</span>
                      </div>
                    </div>
                  ) : null}
                  
                  {conta.status !== 'PAGO' && (
                    pagandoConta === conta.id ? (
                      <div className="space-y-2">
                        <CurrencyInput
                          value={valorPagamentoNumerico}
                          onValueChange={(value) => setValorPagamento(value === null ? '' : value.toString())}
                          className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-2 text-white"
                          placeholder="R$ 0,00"
                          autoFocus
                        />
                        <div className="flex gap-2">
                          <button
                            onClick={() => handleMarcarComoPaga(conta.id)}
                            className="flex-1 bg-green-500 text-white py-2 rounded-xl hover:bg-green-600 transition"
                          >
                            Confirmar
                          </button>
                          <button
                            onClick={() => {
                              setPagandoConta(null);
                              setValorPagamento('');
                            }}
                            className="flex-1 bg-slate-700 text-white py-2 rounded-xl hover:bg-slate-600 transition"
                          >
                            Cancelar
                          </button>
                        </div>
                      </div>
                    ) : (
                      <button
                        onClick={() => {
                          setPagandoConta(conta.id);
                          setValorPagamento(conta.valorPlanejado.toString());
                        }}
                        className="w-full bg-orange-500/10 hover:bg-orange-500/20 text-orange-400 py-3 rounded-xl transition border border-orange-500/30 font-medium"
                      >
                        Marcar como Paga
                      </button>
                    )
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </Layout>
  );
}