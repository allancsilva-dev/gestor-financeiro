import { useState, useEffect } from 'react';
import { contaService, Conta } from '../services/contaService';
import toast from 'react-hot-toast';
import Layout from '../components/Layout';

export default function Contas() {
  const [contas, setContas] = useState<Conta[]>([]);
  const [mostrarForm, setMostrarForm] = useState(false);
  const [loading, setLoading] = useState(false);
  
  const [formData, setFormData] = useState({
    nome: '',
    tipo: 'CREDITO' as 'CREDITO',
    limiteTotal: '',
    diaFechamento: '',
    diaVencimento: '',
    cor: '#8B10AE'
  });

  useEffect(() => {
    carregarContas();
  }, []);

  const carregarContas = async () => {
    try {
      setLoading(true);
      const data = await contaService.listarPorUsuario(1);
      // Filtrar apenas cartões de crédito
      const cartoes = data.filter((c: Conta) => c.tipo === 'CREDITO');
      setContas(cartoes);
    } catch (error: any) {
      toast.error('Erro ao carregar cartões');
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    try {
      setLoading(true);
      
      const contaParaEnviar: any = {
        usuario: { id: 1 },
        nome: formData.nome,
        tipo: 'CREDITO',
        cor: formData.cor,
        limiteTotal: parseFloat(formData.limiteTotal) || 0,
        diaFechamento: parseInt(formData.diaFechamento) || 1,
        diaVencimento: parseInt(formData.diaVencimento) || 1
      };
      
      await contaService.criar(contaParaEnviar);
      toast.success('Cartão criado com sucesso!');
      
      setFormData({ 
        nome: '', 
        tipo: 'CREDITO', 
        limiteTotal: '',
        diaFechamento: '', 
        diaVencimento: '', 
        cor: '#8B10AE' 
      });
      setMostrarForm(false);
      carregarContas();
    } catch (error: any) {
      toast.error('Erro ao criar cartão');
      console.error(error);
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
      toast.error('Erro ao deletar cartão');
      console.error(error);
    }
  };

  const formatarMoeda = (valor: number) => {
    return valor.toLocaleString('pt-BR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
  };

  return (
    <Layout>
      <div className="p-8">
        <div className="max-w-6xl mx-auto">
          
          <div className="flex justify-between items-center mb-8">
            <h1 className="text-3xl font-bold text-gray-800">💳 Cartões de Crédito</h1>
            <button
              onClick={() => setMostrarForm(!mostrarForm)}
              className="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition"
            >
              {mostrarForm ? 'Cancelar' : '+ Novo Cartão'}
            </button>
          </div>

          {mostrarForm && (
            <div className="bg-white p-6 rounded-lg shadow-md mb-8">
              <h2 className="text-xl font-bold mb-4">Novo Cartão de Crédito</h2>
              
              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium mb-1 text-gray-700">Nome do Cartão</label>
                  <input
                    type="text"
                    value={formData.nome}
                    onChange={(e) => setFormData({ ...formData, nome: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                    placeholder="Ex: Nubank, Inter, C6 Bank"
                    required
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium mb-1 text-gray-700">Cor</label>
                  <input
                    type="color"
                    value={formData.cor}
                    onChange={(e) => setFormData({ ...formData, cor: e.target.value })}
                    className="w-full h-10 border border-gray-300 rounded-lg cursor-pointer"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium mb-1 text-gray-700">Limite Total (R$)</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    value={formData.limiteTotal}
                    onChange={(e) => setFormData({ ...formData, limiteTotal: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                    placeholder="3000.00"
                    required
                  />
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Dia de Fechamento</label>
                    <input
                      type="number"
                      min="1"
                      max="31"
                      value={formData.diaFechamento}
                      onChange={(e) => setFormData({ ...formData, diaFechamento: e.target.value })}
                      className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                      placeholder="10"
                      required
                    />
                    <p className="text-xs text-gray-500 mt-1">Dia que a fatura fecha</p>
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Dia de Vencimento</label>
                    <input
                      type="number"
                      min="1"
                      max="31"
                      value={formData.diaVencimento}
                      onChange={(e) => setFormData({ ...formData, diaVencimento: e.target.value })}
                      className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                      placeholder="17"
                      required
                    />
                    <p className="text-xs text-gray-500 mt-1">Dia do vencimento da fatura</p>
                  </div>
                </div>

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full bg-green-600 text-white py-2 rounded-lg hover:bg-green-700 transition disabled:opacity-50"
                >
                  {loading ? 'Salvando...' : 'Salvar Cartão'}
                </button>
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
              contas.map((conta) => (
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
                    <button
                      onClick={() => handleDeletar(conta.id!)}
                      className="text-red-600 hover:text-red-800 text-sm font-medium"
                    >
                      Deletar
                    </button>
                  </div>

                  <div className="space-y-3">
                    <div>
                      <p className="text-sm text-gray-600">Limite Total</p>
                      <p className="text-lg font-bold text-gray-800">
                        R$ {formatarMoeda(conta.limiteTotal || 0)}
                      </p>
                    </div>

                    <div>
                      <p className="text-sm text-gray-600">Valor Gasto</p>
                      <p className={`text-lg font-bold ${(conta.valorGasto || 0) > (conta.limiteTotal || 0) ? 'text-red-600' : 'text-green-600'}`}>
                        R$ {formatarMoeda(conta.valorGasto || 0)}
                      </p>
                    </div>

                    <div>
                      <p className="text-sm text-gray-600 mb-1">Disponível</p>
                      <p className="text-sm font-semibold text-blue-600 mb-2">
                        R$ {formatarMoeda((conta.limiteTotal || 0) - (conta.valorGasto || 0))}
                      </p>
                      <div className="w-full bg-gray-200 rounded-full h-2">
                        <div
                          className={`h-2 rounded-full transition-all ${
                            (conta.valorGasto || 0) > (conta.limiteTotal || 0) 
                              ? 'bg-red-600' 
                              : 'bg-blue-600'
                          }`}
                          style={{ width: `${Math.min(((conta.valorGasto || 0) / (conta.limiteTotal || 1) * 100), 100)}%` }}
                        ></div>
                      </div>
                      <p className="text-xs text-gray-500 mt-1 text-right">
                        {(((conta.valorGasto || 0) / (conta.limiteTotal || 1)) * 100).toFixed(1)}% usado
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
              ))
            )}
          </div>
        </div>
      </div>
    </Layout>
  );
}