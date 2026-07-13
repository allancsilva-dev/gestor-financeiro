import { useState, useEffect } from 'react';
import { contaService, Conta } from '../services/contaService';
import { useAuth } from '../context/AuthContext';
import toast from 'react-hot-toast';
import Layout from '../components/Layout';
import CurrencyInput from '../components/CurrencyInput';
import { formatCurrency } from '../utils/currency';
import FieldError from '../components/FieldError';
import { fieldA11y } from '../validation/fieldA11y';
import { useZodForm } from '../hooks/useZodForm';
import { contaSchema } from '../validation/schemas';
import { toNullableNumber } from '../validation/numbers';

export default function Contas() {
  const { usuario } = useAuth();
  const [contas, setContas] = useState<Conta[]>([]);
  const [mostrarForm, setMostrarForm] = useState(false);
  const [loading, setLoading] = useState(false);
  const [editando, setEditando] = useState<Conta | null>(null);
  
  const [formData, setFormData] = useState({
    nome: '',
    tipo: 'CREDITO' as 'CREDITO',
    limiteTotal: '',
    diaFechamento: '',
    diaVencimento: '',
    cor: '#8B10AE'
  });
  const validation = useZodForm(contaSchema);

  useEffect(() => {
    if (usuario?.id) {
      carregarContas();
    }
  }, [usuario]);

  const carregarContas = async () => {
    if (!usuario?.id) return;

    try {
      setLoading(true);
      const data = await contaService.listarPorUsuario(usuario.id);
      // Filtrar apenas cartões de crédito
      const cartoes = data.filter((c: Conta) => c.tipo === 'CREDITO');
      setContas(cartoes);
    } catch (error: any) {
      toast.error('Erro ao carregar cartoes');
    } finally {
      setLoading(false);
    }
  };

  const abrirFormulario = (conta?: Conta) => {
    if (conta) {
      // Modo edição
      setEditando(conta);
      setFormData({
        nome: conta.nome,
        tipo: 'CREDITO',
        limiteTotal: conta.limiteTotal?.toString() || '',
        diaFechamento: conta.diaFechamento?.toString() || '',
        diaVencimento: conta.diaVencimento?.toString() || '',
        cor: conta.cor || '#8B10AE'
      });
    } else {
      // Modo criação
      resetarFormulario();
    }
    setMostrarForm(true);
  };

  const resetarFormulario = () => {
    setEditando(null);
    setFormData({
      nome: '',
      tipo: 'CREDITO',
      limiteTotal: '',
      diaFechamento: '',
      diaVencimento: '',
      cor: '#8B10AE'
    });
    validation.resetValidation();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!usuario?.id) {
      toast.error('Usuário não autenticado');
      return;
    }
    const contaParaEnviar = validation.validate(formData);
    if (!contaParaEnviar) return;

    try {
      setLoading(true);
      
      if (editando) {
        // Atualizar
        await contaService.atualizar(editando.id!, contaParaEnviar);
        toast.success('Cartão atualizado com sucesso!');
      } else {
        // Criar
        await contaService.criar(contaParaEnviar);
        toast.success('Cartão criado com sucesso!');
      }
      
      resetarFormulario();
      setMostrarForm(false);
      carregarContas();
    } catch (error: any) {
      toast.error(editando ? 'Erro ao atualizar cartao' : 'Erro ao criar cartao');
    } finally {
      setLoading(false);
    }
  };

  const handleDeletar = async (id: number) => {
    if (!window.confirm('Tem certeza que deseja deletar este cartão?')) return;
    
    try {
      await contaService.deletar(id);
      toast.success('Cartão deletado!');
      carregarContas();
    } catch (error: any) {
      toast.error('Erro ao deletar cartao');
    }
  };

  const limiteTotalNumerico = toNullableNumber(formData.limiteTotal);

  return (
    <Layout>
      <div className="p-8">
        <div className="max-w-6xl mx-auto">
          
          <div className="flex justify-between items-center mb-8">
            <h1 className="text-3xl font-bold text-gray-800">💳 Cartões de Crédito</h1>
            <button
              onClick={() => {
                resetarFormulario();
                setMostrarForm(!mostrarForm);
              }}
              className="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition"
            >
              {mostrarForm ? 'Cancelar' : '+ Novo Cartão'}
            </button>
          </div>

          {mostrarForm && (
            <div className="bg-white p-6 rounded-lg shadow-md mb-8">
              <h2 className="text-xl font-bold mb-4">
                {editando ? 'Editar Cartão de Crédito' : 'Novo Cartão de Crédito'}
              </h2>
              
              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium mb-1 text-gray-700">Nome do Cartão</label>
                  <input
                    type="text"
                    {...fieldA11y('nome', validation.errors.nome)}
                    value={formData.nome}
                    onChange={(e) => { const next = { ...formData, nome: e.target.value }; setFormData(next); validation.revalidateField('nome', next); }}
                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 aria-invalid:border-red-500"
                    placeholder="Ex: Nubank, Inter, C6 Bank"
                    required
                  />
                  <FieldError name="nome" error={validation.errors.nome} />
                </div>

                <div>
                  <label className="block text-sm font-medium mb-1 text-gray-700">Cor</label>
                  <input
                    type="color"
                    {...fieldA11y('cor', validation.errors.cor)}
                    value={formData.cor}
                    onChange={(e) => { const next = { ...formData, cor: e.target.value }; setFormData(next); validation.revalidateField('cor', next); }}
                    className="w-full h-10 border border-gray-300 rounded-lg cursor-pointer"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium mb-1 text-gray-700">Limite Total (R$)</label>
                  <CurrencyInput
                    {...fieldA11y('limiteTotal', validation.errors.limiteTotal)}
                    value={limiteTotalNumerico}
                    onValueChange={(value) => { const next = { ...formData, limiteTotal: value === null ? '' : value.toString() }; setFormData(next); validation.revalidateField('limiteTotal', next); }}
                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 aria-invalid:border-red-500"
                    placeholder="R$ 0,00"
                    required
                  />
                  <FieldError name="limiteTotal" error={validation.errors.limiteTotal} />
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Dia de Fechamento</label>
                    <input
                      type="number"
                      min="1"
                      max="31"
                      {...fieldA11y('diaFechamento', validation.errors.diaFechamento)}
                      value={formData.diaFechamento}
                      onChange={(e) => { const next = { ...formData, diaFechamento: e.target.value }; setFormData(next); validation.revalidateField('diaFechamento', next); }}
                      className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 aria-invalid:border-red-500"
                      placeholder="10"
                      required
                    />
                    <FieldError name="diaFechamento" error={validation.errors.diaFechamento} />
                    <p className="text-xs text-gray-500 mt-1">Dia que a fatura fecha</p>
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Dia de Vencimento</label>
                    <input
                      type="number"
                      min="1"
                      max="31"
                      {...fieldA11y('diaVencimento', validation.errors.diaVencimento)}
                      value={formData.diaVencimento}
                      onChange={(e) => { const next = { ...formData, diaVencimento: e.target.value }; setFormData(next); validation.revalidateField('diaVencimento', next); }}
                      className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 aria-invalid:border-red-500"
                      placeholder="17"
                      required
                    />
                    <FieldError name="diaVencimento" error={validation.errors.diaVencimento} />
                    <p className="text-xs text-gray-500 mt-1">Dia do vencimento da fatura</p>
                  </div>
                </div>

                <div className="flex gap-3">
                  <button
                    type="button"
                    onClick={() => {
                      setMostrarForm(false);
                      resetarFormulario();
                    }}
                    className="flex-1 bg-gray-300 text-gray-700 py-2 rounded-lg hover:bg-gray-400 transition"
                  >
                    Cancelar
                  </button>
                  <button
                    type="submit"
                    disabled={loading}
                    className="flex-1 bg-green-600 text-white py-2 rounded-lg hover:bg-green-700 transition disabled:opacity-50"
                  >
                    {loading ? 'Salvando...' : (editando ? 'Atualizar' : 'Salvar')}
                  </button>
                </div>
              </form>
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {loading ? (
              <div className="col-span-3 text-center py-12">
                <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
                <p className="mt-2 text-gray-600">Carregando...</p>
              </div>
            ) : contas.length === 0 ? (
              <div className="col-span-3 text-center py-12 bg-white rounded-lg shadow-md p-8">
                <div className="text-6xl mb-4">💳</div>
                <p className="text-gray-500 text-lg font-semibold">Nenhum cartão cadastrado</p>
                <p className="text-gray-400 text-sm mt-2">Clique em "+ Novo Cartão" para adicionar seu primeiro cartão de crédito</p>
              </div>
            ) : (
              contas.map((conta) => {
                const valorGasto = conta.valorGasto || 0;
                const limiteTotal = conta.limiteTotal || 0;
                const creditoDisponivel = valorGasto < 0 ? Math.abs(valorGasto) : 0;
                const percentualUso = limiteTotal > 0 ? Math.min((Math.max(valorGasto, 0) / limiteTotal) * 100, 100) : 0;

                return (
                <div
                  key={conta.id}
                  className="bg-white rounded-lg shadow-md p-6 hover:shadow-lg transition"
                  style={{ borderTop: `4px solid ${conta.cor || '#666'}` }}
                >
                  <div className="flex justify-between items-start mb-4">
                    <div>
                      <h3 className="text-xl font-bold text-gray-800">{conta.nome}</h3>
                      <p className="text-sm text-gray-500">💳 Cartão de Crédito</p>
                    </div>
                    <div className="flex gap-2">
                      <button
                        onClick={() => abrirFormulario(conta)}
                        className="text-blue-600 hover:text-blue-800 text-sm font-medium"
                      >
                        Editar
                      </button>
                      <button
                        onClick={() => handleDeletar(conta.id!)}
                        className="text-red-600 hover:text-red-800 text-sm font-medium"
                      >
                        Deletar
                      </button>
                    </div>
                  </div>

                  <div className="space-y-3">
                    <div>
                      <p className="text-sm text-gray-600">Limite Total</p>
                      <p className="text-lg font-bold text-gray-800">
                        {formatCurrency(conta.limiteTotal || 0)}
                      </p>
                    </div>

                    <div>
                      <p className="text-sm text-gray-600">{creditoDisponivel > 0 ? 'Crédito disponível' : 'Valor Gasto'}</p>
                      <p className={`text-lg font-bold ${creditoDisponivel > 0 ? 'text-green-600' : valorGasto > limiteTotal ? 'text-red-600' : 'text-green-600'}`}>
                        {formatCurrency(creditoDisponivel > 0 ? creditoDisponivel : valorGasto)}
                      </p>
                    </div>

                    <div>
                      <p className="text-sm text-gray-600 mb-1">Disponível</p>
                      <p className="text-sm font-semibold text-blue-600 mb-2">
                        {formatCurrency(limiteTotal - valorGasto)}
                      </p>
                      <div className="w-full bg-gray-200 rounded-full h-2">
                        <div
                          className={`h-2 rounded-full transition-all ${
                            valorGasto > limiteTotal
                              ? 'bg-red-600' 
                              : 'bg-blue-600'
                          }`}
                          style={{ width: `${percentualUso}%` }}
                        ></div>
                      </div>
                      <p className="text-xs text-gray-500 mt-1 text-right">
                        {percentualUso.toFixed(1)}% usado
                      </p>
                    </div>

                    <div className="pt-3 border-t grid grid-cols-2 gap-2 text-sm">
                      <div>
                        <p className="text-gray-500">Fechamento</p>
                        <p className="font-semibold text-gray-700">Dia {conta.diaFechamento}</p>
                      </div>
                      <div>
                        <p className="text-gray-500">Vencimento</p>
                        <p className="font-semibold text-gray-700">Dia {conta.diaVencimento}</p>
                      </div>
                    </div>
                  </div>
                </div>
                );
              })
            )}
          </div>
        </div>
      </div>
    </Layout>
  );
}
