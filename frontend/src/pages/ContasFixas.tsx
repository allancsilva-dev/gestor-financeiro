import { useState, useEffect } from 'react';
import Layout from '../components/Layout';
import contaFixaService, { ContaFixa } from '../services/contaFixaService';
import { categoriaService, Categoria } from '../services/categoriaService';
import carteiraService, { Carteira } from '../services/carteiraService';
import { useAuth } from '../context/AuthContext';
import toast from 'react-hot-toast';
import { Plus, Edit2, Trash2, Check, X, Calendar, DollarSign, Tag, SkipForward } from 'lucide-react';
import CurrencyInput from '../components/CurrencyInput';
import { formatCurrency } from '../utils/currency';
import FieldError from '../components/FieldError';
import { fieldA11y } from '../validation/fieldA11y';
import { useZodForm } from '../hooks/useZodForm';
import { contaFixaSchema, pagamentoSchema } from '../validation/schemas';
import { toNullableNumber } from '../validation/numbers';

export default function ContasFixas() {
  const { usuario } = useAuth();
  const [contasFixas, setContasFixas] = useState<ContaFixa[]>([]);
  const [categorias, setCategorias] = useState<Categoria[]>([]);
  const [mostrarForm, setMostrarForm] = useState(false);
  const [editando, setEditando] = useState<ContaFixa | null>(null);
  const [loading, setLoading] = useState(false);
  const [acaoFinanceiraId, setAcaoFinanceiraId] = useState<string | null>(null);
  const [pagandoConta, setPagandoConta] = useState<number | null>(null);
  const [valorPagamento, setValorPagamento] = useState('');
  const [carteiras, setCarteiras] = useState<Carteira[]>([]);
  const [carteiraPagamento, setCarteiraPagamento] = useState('');

  const [formData, setFormData] = useState({
    nome: '',
    valorPlanejado: '',
    diaVencimento: '',
    categoriaId: '',
    observacoes: ''
  });
  const formValidation = useZodForm(contaFixaSchema);
  const paymentValidation = useZodForm(pagamentoSchema);

  useEffect(() => {
    if (usuario?.id) {
      carregarDados();
    }
  }, [usuario]);

  const carregarDados = async () => {
    if (!usuario?.id) return;

    try {
      setLoading(true);
      const [contasData, categoriasData, carteirasData] = await Promise.all([
        contaFixaService.listarAtivas(usuario.id),
        categoriaService.listarMinhas(0, 100),
        carteiraService.listarCarteiras(usuario.id, 0, 100)
      ]);
      setContasFixas(contasData);
      setCategorias(categoriasData);
      setCarteiras(carteirasData);
    } catch (error: any) {
      toast.error('Erro ao carregar dados');
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
    formValidation.resetValidation();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!usuario?.id) {
      toast.error('Usuário não autenticado');
      return;
    }
    const parsed = formValidation.validate(formData);
    if (!parsed) return;

    try {
      setLoading(true);

      const contaData = {
        nome: parsed.nome,
        valorPlanejado: parsed.valorPlanejado,
        diaVencimento: parsed.diaVencimento,
        observacoes: parsed.observacoes,
        categoria: { id: parsed.categoriaId },
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
    }
  };

  const handleMarcarComoPaga = async (id: number) => {
    const actionKey = `pagar:${id}`;
    if (acaoFinanceiraId) return;
    const payment = paymentValidation.validate({ valor: valorPagamento, carteiraId: carteiraPagamento });
    if (!payment) return;

    try {
      setAcaoFinanceiraId(actionKey);
      await contaFixaService.marcarComoPaga(id, payment.valor, payment.carteiraId);
      toast.success('Conta marcada como paga!');
      setPagandoConta(null);
      setValorPagamento('');
      setCarteiraPagamento('');
      paymentValidation.resetValidation();
      carregarDados();
    } catch (error: any) {
      toast.error(error?.response?.data?.message ?? 'Erro ao marcar como paga');
    } finally {
      setAcaoFinanceiraId(null);
    }
  };

  const handlePularMes = async (id: number) => {
    const actionKey = `pular:${id}`;
    if (acaoFinanceiraId) return;
    try {
      setAcaoFinanceiraId(actionKey);
      await contaFixaService.pularMes(id);
      toast.success('Mês pulado!');
      carregarDados();
    } catch (error: any) {
      toast.error('Erro ao pular mês');
    } finally {
      setAcaoFinanceiraId(null);
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

  const valorPlanejadoNumerico = toNullableNumber(formData.valorPlanejado);
  const valorPagamentoNumerico = toNullableNumber(valorPagamento);

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
                      {...fieldA11y('nome', formValidation.errors.nome)}
                      type="text"
                      value={formData.nome}
                      onChange={(e) => { const next = { ...formData, nome: e.target.value }; setFormData(next); formValidation.revalidateField('nome', next); }}
                      className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white focus:ring-2 focus:ring-orange-500 focus:border-transparent aria-invalid:border-red-500"
                      placeholder="Ex: Aluguel"
                      required
                    />
                    <FieldError name="nome" error={formValidation.errors.nome} />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-400 mb-2">
                      Valor Planejado (R$) *
                    </label>
                    <CurrencyInput
                      {...fieldA11y('valorPlanejado', formValidation.errors.valorPlanejado)}
                      value={valorPlanejadoNumerico}
                      onValueChange={(value) => { const next = { ...formData, valorPlanejado: value === null ? '' : value.toString() }; setFormData(next); formValidation.revalidateField('valorPlanejado', next); }}
                      className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white focus:ring-2 focus:ring-orange-500 focus:border-transparent aria-invalid:border-red-500"
                      placeholder="R$ 0,00"
                      required
                    />
                    <FieldError name="valorPlanejado" error={formValidation.errors.valorPlanejado} />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-400 mb-2">
                      Dia do Vencimento *
                    </label>
                    <select
                      {...fieldA11y('diaVencimento', formValidation.errors.diaVencimento)}
                      value={formData.diaVencimento}
                      onChange={(e) => { const next = { ...formData, diaVencimento: e.target.value }; setFormData(next); formValidation.revalidateField('diaVencimento', next); }}
                      className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white focus:ring-2 focus:ring-orange-500 focus:border-transparent aria-invalid:border-red-500"
                      required
                    >
                      <option value="">Selecione...</option>
                      {Array.from({ length: 31 }, (_, i) => i + 1).map(dia => (
                        <option key={dia} value={dia}>Todo dia {dia}</option>
                      ))}
                    </select>
                    <FieldError name="diaVencimento" error={formValidation.errors.diaVencimento} />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-400 mb-2">
                      Categoria *
                    </label>
                    <select
                      {...fieldA11y('categoriaId', formValidation.errors.categoriaId)}
                      value={formData.categoriaId}
                      onChange={(e) => { const next = { ...formData, categoriaId: e.target.value }; setFormData(next); formValidation.revalidateField('categoriaId', next); }}
                      className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white focus:ring-2 focus:ring-orange-500 focus:border-transparent aria-invalid:border-red-500"
                      required
                    >
                      <option value="">Selecione...</option>
                      {categorias.map(cat => (
                        <option key={cat.id} value={cat.id}>{cat.nome}</option>
                      ))}
                    </select>
                    <FieldError name="categoriaId" error={formValidation.errors.categoriaId} />
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
                          {...fieldA11y('valor', paymentValidation.errors.valor)}
                          value={valorPagamentoNumerico}
                          onValueChange={(value) => { const next = value === null ? '' : value.toString(); setValorPagamento(next); paymentValidation.revalidateField('valor', { valor: next, carteiraId: carteiraPagamento }); }}
                          className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-2 text-white aria-invalid:border-red-500"
                          placeholder="R$ 0,00"
                          autoFocus
                        />
                        <FieldError name="valor" error={paymentValidation.errors.valor} />
                        <select
                          {...fieldA11y('carteiraId', paymentValidation.errors.carteiraId)}
                          value={carteiraPagamento}
                          onChange={(e) => { setCarteiraPagamento(e.target.value); paymentValidation.revalidateField('carteiraId', { valor: valorPagamento, carteiraId: e.target.value }); }}
                          className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-2 text-white aria-invalid:border-red-500"
                        >
                          <option value="">Pagar com qual conta?</option>
                          {carteiras.map((c) => (
                            <option key={c.id} value={c.id}>
                              {c.nome} · {formatCurrency(c.saldo)}
                            </option>
                          ))}
                        </select>
                        <FieldError name="carteiraId" error={paymentValidation.errors.carteiraId} />
                        <div className="flex gap-2">
                        <button
                            disabled={acaoFinanceiraId !== null}
                            onClick={() => handleMarcarComoPaga(conta.id)}
                            className="flex-1 bg-green-500 text-white py-2 rounded-xl hover:bg-green-600 transition disabled:opacity-50"
                          >
                            {acaoFinanceiraId === `pagar:${conta.id}` ? 'Processando...' : 'Confirmar'}
                          </button>
                          <button
                            disabled={acaoFinanceiraId !== null}
                            onClick={() => {
                              setPagandoConta(null);
                              setValorPagamento('');
                              setCarteiraPagamento('');
                              paymentValidation.resetValidation();
                            }}
                            className="flex-1 bg-slate-700 text-white py-2 rounded-xl hover:bg-slate-600 transition disabled:opacity-50"
                          >
                            Cancelar
                          </button>
                        </div>
                      </div>
                    ) : (
                      <div className="flex gap-2">
                        <button
                          disabled={acaoFinanceiraId !== null}
                          onClick={() => {
                            setPagandoConta(conta.id);
                            setValorPagamento(conta.valorPlanejado.toString());
                          }}
                          className="flex-1 bg-orange-500/10 hover:bg-orange-500/20 text-orange-400 py-3 rounded-xl transition border border-orange-500/30 font-medium disabled:opacity-50"
                        >
                          Marcar como Paga
                        </button>
                        {conta.recorrente !== false && (
                          <button
                            disabled={acaoFinanceiraId !== null}
                            onClick={() => handlePularMes(conta.id)}
                            className="px-4 py-3 bg-slate-700/50 hover:bg-slate-600/50 text-slate-400 hover:text-slate-300 rounded-xl transition border border-slate-600/30 flex items-center gap-1 disabled:opacity-50"
                            title="Pular este mês"
                          >
                            <SkipForward className="w-4 h-4" />
                          </button>
                        )}
                      </div>
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
