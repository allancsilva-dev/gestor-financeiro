import { useState, useEffect } from 'react';
import { transacaoService, Transacao } from '../services/transacaoService';
import { categoriaService, Categoria } from '../services/categoriaService';
import { contaService, Conta } from '../services/contaService';
import toast from 'react-hot-toast';
import Layout from '../components/Layout';

export default function Transacoes() {
  const [transacoes, setTransacoes] = useState<any[]>([]);
  const [categorias, setCategorias] = useState<Categoria[]>([]);
  const [contas, setContas] = useState<Conta[]>([]);
  const [mostrarForm, setMostrarForm] = useState(false);
  const [loading, setLoading] = useState(false);
  
  const [formData, setFormData] = useState({
    descricao: '',
    valorTotal: '',
    tipo: 'SAIDA' as 'ENTRADA' | 'SAIDA',
    data: new Date().toISOString().split('T')[0],
    categoriaId: '',
    contaId: '',
    parcelado: false,
    totalParcelas: ''
  });

  useEffect(() => {
    carregarDados();
  }, []);

  const carregarDados = async () => {
    try {
      setLoading(true);
      const [transacoesData, categoriasData, contasData] = await Promise.all([
        transacaoService.listarPorUsuario(1),
        categoriaService.listarMinhas(),
        contaService.listarPorUsuario(1)
      ]);
      setTransacoes(transacoesData);
      setCategorias(categoriasData);
      setContas(contasData);
    } catch (error: any) {
      toast.error('Erro ao carregar dados');
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    try {
      setLoading(true);
      
      const transacaoParaEnviar: any = {
        usuario: { id: 1 },
        conta: { id: parseInt(formData.contaId) },
        categoria: { id: parseInt(formData.categoriaId) },
        descricao: formData.descricao,
        valorTotal: parseFloat(formData.valorTotal),
        tipo: formData.tipo,
        data: formData.data,
        parcelado: formData.parcelado,
        totalParcelas: formData.parcelado ? parseInt(formData.totalParcelas) : null
      };
      
      await transacaoService.criar(transacaoParaEnviar);
      toast.success('Transação criada com sucesso!');
      
      setFormData({ 
        descricao: '', 
        valorTotal: '', 
        tipo: 'SAIDA',
        data: new Date().toISOString().split('T')[0],
        categoriaId: '',
        contaId: '',
        parcelado: false,
        totalParcelas: ''
      });
      setMostrarForm(false);
      carregarDados();
    } catch (error: any) {
      toast.error('Erro ao criar transação');
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const handleDeletar = async (id: number) => {
    if (!window.confirm('Tem certeza que deseja deletar?')) return;
    
    try {
      await transacaoService.deletar(id);
      toast.success('Transação deletada!');
      carregarDados();
    } catch (error: any) {
      toast.error('Erro ao deletar transação');
      console.error(error);
    }
  };

  return (
    <Layout>
      <div className="p-8">
        <div className="max-w-6xl mx-auto">
          
          <div className="flex justify-between items-center mb-8">
            <h1 className="text-3xl font-bold text-gray-800">Transações</h1>
            <button
              onClick={() => setMostrarForm(!mostrarForm)}
              className="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition"
            >
              {mostrarForm ? 'Cancelar' : '+ Nova Transação'}
            </button>
          </div>

          {mostrarForm && (
            <div className="bg-white p-6 rounded-lg shadow-md mb-8">
              <h2 className="text-xl font-bold mb-4">Nova Transação</h2>
              
              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium mb-1 text-gray-700">Descrição</label>
                  <input
                    type="text"
                    value={formData.descricao}
                    onChange={(e) => setFormData({ ...formData, descricao: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                    placeholder="Ex: Compra no mercado"
                    required
                  />
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Valor Total (R$)</label>
                    <input
                      type="number"
                      step="0.01"
                      value={formData.valorTotal}
                      onChange={(e) => setFormData({ ...formData, valorTotal: e.target.value })}
                      className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                      placeholder="100.00"
                      required
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Tipo</label>
                    <select
                      value={formData.tipo}
                      onChange={(e) => setFormData({ ...formData, tipo: e.target.value as any })}
                      className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                    >
                      <option value="SAIDA">💸 Saída</option>
                      <option value="ENTRADA">💰 Entrada</option>
                    </select>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Categoria</label>
                    <select
                      value={formData.categoriaId}
                      onChange={(e) => setFormData({ ...formData, categoriaId: e.target.value })}
                      className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                      required
                    >
                      <option value="">Selecione...</option>
                      {categorias.map((cat) => (
                        <option key={cat.id} value={cat.id}>{cat.nome}</option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Conta</label>
                    <select
                      value={formData.contaId}
                      onChange={(e) => setFormData({ ...formData, contaId: e.target.value })}
                      className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                      required
                    >
                      <option value="">Selecione...</option>
                      {contas.map((conta) => (
                        <option key={conta.id} value={conta.id}>{conta.nome}</option>
                      ))}
                    </select>
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium mb-1 text-gray-700">Data</label>
                  <input
                    type="date"
                    value={formData.data}
                    onChange={(e) => setFormData({ ...formData, data: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                    required
                  />
                </div>

                <div className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    id="parcelado"
                    checked={formData.parcelado}
                    onChange={(e) => setFormData({ ...formData, parcelado: e.target.checked })}
                    className="w-4 h-4"
                  />
                  <label htmlFor="parcelado" className="text-sm font-medium text-gray-700">
                    Parcelar compra?
                  </label>
                </div>

                {formData.parcelado && (
                  <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Número de Parcelas</label>
                    <input
                      type="number"
                      min="2"
                      max="48"
                      value={formData.totalParcelas}
                      onChange={(e) => setFormData({ ...formData, totalParcelas: e.target.value })}
                      className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                      placeholder="12"
                      required={formData.parcelado}
                    />
                    {formData.totalParcelas && formData.valorTotal && (
                      <p className="text-sm text-gray-600 mt-1">
                        {formData.totalParcelas}x de R$ {(parseFloat(formData.valorTotal) / parseInt(formData.totalParcelas)).toFixed(2)}
                      </p>
                    )}
                  </div>
                )}

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full bg-green-600 text-white py-2 rounded-lg hover:bg-green-700 transition disabled:opacity-50"
                >
                  {loading ? 'Salvando...' : 'Salvar Transação'}
                </button>
              </form>
            </div>
          )}

          <div className="bg-white rounded-lg shadow-md overflow-hidden">
            {loading ? (
              <div className="text-center py-12">
                <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
                <p className="mt-2 text-gray-600">Carregando...</p>
              </div>
            ) : transacoes.length === 0 ? (
              <div className="text-center py-12">
                <p className="text-gray-500 text-lg">Nenhuma transação cadastrada</p>
                <p className="text-gray-400 text-sm mt-2">Clique em "+ Nova Transação" para começar</p>
              </div>
            ) : (
              <table className="w-full">
                <thead className="bg-gray-100">
                  <tr>
                    <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Data</th>
                    <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Descrição</th>
                    <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Categoria</th>
                    <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Conta</th>
                    <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Valor</th>
                    <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {transacoes.map((t) => (
                    <tr key={t.id} className="border-t border-gray-200 hover:bg-gray-50 transition">
                      <td className="px-6 py-4 text-gray-700">
                        {new Date(t.data).toLocaleDateString('pt-BR')}
                      </td>
                      <td className="px-6 py-4">
                        <div>
                          <p className="font-medium text-gray-800">{t.descricao}</p>
                          {t.parcelado && (
                            <p className="text-xs text-gray-500">
                              {t.totalParcelas}x de R$ {(t.valorParcela || 0).toFixed(2)}
                            </p>
                          )}
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <span className="inline-flex items-center gap-2">
                          <span
                            className="w-3 h-3 rounded-full"
                            style={{ backgroundColor: t.categoria?.cor || '#666' }}
                          ></span>
                          {t.categoria?.nome || 'N/A'}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-gray-700">
                        {t.conta?.nome || 'N/A'}
                      </td>
                      <td className="px-6 py-4">
                        <span className={`font-semibold ${t.tipo === 'ENTRADA' ? 'text-green-600' : 'text-red-600'}`}>
                          {t.tipo === 'ENTRADA' ? '+' : '-'} R$ {(t.valorTotal || 0).toFixed(2)}
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        <button
                          onClick={() => handleDeletar(t.id!)}
                          className="text-red-600 hover:text-red-800 font-medium transition"
                        >
                          Deletar
                        </button>
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