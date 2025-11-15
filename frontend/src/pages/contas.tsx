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
    tipo: 'CREDITO' as 'CREDITO' | 'DEBITO' | 'DINHEIRO' | 'POUPANCA',
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
      // TODO: pegar usuarioId do contexto
      const data = await contaService.listarPorUsuario(1);
      setContas(data);
    } catch (error: any) {
      toast.error('Erro ao carregar contas');
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
        usuario: { id: 1 }, // TODO: pegar do contexto
        nome: formData.nome,
        tipo: formData.tipo,
        cor: formData.cor
      };

      if (formData.tipo === 'CREDITO') {
        contaParaEnviar.limiteTotal = parseFloat(formData.limiteTotal) || 0;
        contaParaEnviar.diaFechamento = parseInt(formData.diaFechamento) || 1;
        contaParaEnviar.diaVencimento = parseInt(formData.diaVencimento) || 1;
      }
      
      await contaService.criar(contaParaEnviar);
      toast.success('Conta criada com sucesso!');
      
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
      toast.error('Erro ao criar conta');
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const handleDeletar = async (id: number) => {
    if (!window.confirm('Tem certeza que deseja deletar?')) return;
    
    try {
      await contaService.deletar(id);
      toast.success('Conta deletada!');
      carregarContas();
    } catch (error: any) {
      toast.error('Erro ao deletar conta');
      console.error(error);
    }
  };

  const getTipoLabel = (tipo: string) => {
    const tipos: any = {
      CREDITO: '💳 Crédito',
      DEBITO: '🏦 Débito',
      DINHEIRO: '💵 Dinheiro',
      POUPANCA: '🐖 Poupança'
    };
    return tipos[tipo] || tipo;
  };

  return (
    <Layout>
      <div className="p-8">
        <div className="max-w-6xl mx-auto">
          
          <div className="flex justify-between items-center mb-8">
            <h1 className="text-3xl font-bold text-gray-800">Contas e Cartões</h1>
            <button
              onClick={() => setMostrarForm(!mostrarForm)}
              className="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition"
            >
              {mostrarForm ? 'Cancelar' : '+ Nova Conta'}
            </button>
          </div>

          {mostrarForm && (
            <div className="bg-white p-6 rounded-lg shadow-md mb-8">
              <h2 className="text-xl font-bold mb-4">Nova Conta</h2>
              
              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium mb-1 text-gray-700">Nome</label>
                  <input
                    type="text"
                    value={formData.nome}
                    onChange={(e) => setFormData({ ...formData, nome: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                    placeholder="Ex: Nubank"
                    required
                  />
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Tipo</label>
                    <select
                      value={formData.tipo}
                      onChange={(e) => setFormData({ ...formData, tipo: e.target.value as any })}
                      className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                    >
                      <option value="CREDITO">💳 Crédito</option>
                      <option value="DEBITO">🏦 Débito</option>
                      <option value="DINHEIRO">💵 Dinheiro</option>
                      <option value="POUPANCA">🐖 Poupança</option>
                    </select>
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
                </div>

                {formData.tipo === 'CREDITO' && (
                  <>
                    <div>
                      <label className="block text-sm font-medium mb-1 text-gray-700">Limite Total (R$)</label>
                      <input
                        type="number"
                        step="0.01"
                        value={formData.limiteTotal}
                        onChange={(e) => setFormData({ ...formData, limiteTotal: e.target.value })}
                        className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                        placeholder="3000.00"
                        required
                      />
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <label className="block text-sm font-medium mb-1 text-gray-700">Dia Fechamento</label>
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
                      </div>
                      <div>
                        <label className="block text-sm font-medium mb-1 text-gray-700">Dia Vencimento</label>
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
                      </div>
                    </div>
                  </>
                )}

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full bg-green-600 text-white py-2 rounded-lg hover:bg-green-700 transition disabled:opacity-50"
                >
                  {loading ? 'Salvando...' : 'Salvar Conta'}
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
              <div className="col-span-3 text-center py-12">
                <p className="text-gray-500 text-lg">Nenhuma conta cadastrada</p>
                <p className="text-gray-400 text-sm mt-2">Clique em "+ Nova Conta" para começar</p>
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
                      <p className="text-sm text-gray-500">{getTipoLabel(conta.tipo)}</p>
                    </div>
                    <button
                      onClick={() => handleDeletar(conta.id!)}
                      className="text-red-600 hover:text-red-800 text-sm"
                    >
                      Deletar
                    </button>
                  </div>

                  {conta.tipo === 'CREDITO' && (
                    <>
                      <div className="mb-2">
                        <p className="text-sm text-gray-600">Limite</p>
                        <p className="text-lg font-bold text-gray-800">
                          R$ {(conta.limiteTotal || 0).toFixed(2)}
                        </p>
                      </div>
                      <div className="mb-2">
                        <p className="text-sm text-gray-600">Gasto</p>
                        <p className={`text-lg font-bold ${(conta.valorGasto || 0) > (conta.limiteTotal || 0) ? 'text-red-600' : 'text-green-600'}`}>
                          R$ {(conta.valorGasto || 0).toFixed(2)}
                        </p>
                      </div>
                      <div className="mt-4">
                        <div className="flex justify-between text-xs text-gray-500 mb-1">
                          <span>Disponível</span>
                          <span>{(((conta.limiteTotal || 0) - (conta.valorGasto || 0)) / (conta.limiteTotal || 1) * 100).toFixed(0)}%</span>
                        </div>
                        <div className="w-full bg-gray-200 rounded-full h-2">
                          <div
                            className="bg-blue-600 h-2 rounded-full transition-all"
                            style={{ width: `${Math.min(((conta.valorGasto || 0) / (conta.limiteTotal || 1) * 100), 100)}%` }}
                          ></div>
                        </div>
                      </div>
                    </>
                  )}
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </Layout>
  );
}