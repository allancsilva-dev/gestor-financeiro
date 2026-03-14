import { useState, useEffect } from 'react';
import { categoriaService, Categoria } from '../services/categoriaService';
import toast from 'react-hot-toast';
import Layout from '../components/Layout';

export default function Categorias() {
  const [categorias, setCategorias] = useState<Categoria[]>([]);
  const [mostrarForm, setMostrarForm] = useState(false);
  const [loading, setLoading] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [paginaAtual, setPaginaAtual] = useState(0);
  const [totalPaginas, setTotalPaginas] = useState(1);
  const tamanhoPagina = 20;
  
  const [formData, setFormData] = useState({
    nome: '',
    cor: '#FF5733',
    icone: 'shopping-cart',
    valorEsperado: ''
  });

  useEffect(() => {
    carregarCategorias();
  }, [paginaAtual]);

  const carregarCategorias = async () => {
    try {
      setLoading(true);
      setErro(null);
      const data = await categoriaService.listarMinhasPaginado(paginaAtual, tamanhoPagina);
      setCategorias(data.content || []);
      setTotalPaginas(Math.max(data.totalPages || 1, 1));
    } catch (error: any) {
      const mensagem = error.response?.data?.message || error.message || 'Erro ao carregar categorias';
      setErro(mensagem);
      toast.error(mensagem);
      console.error('Erro completo:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    try {
      setLoading(true);
      
      const categoriaParaEnviar: Categoria = {
        nome: formData.nome,
        cor: formData.cor,
        icone: formData.icone,
        valorEsperado: parseFloat(formData.valorEsperado) || 0
      };
      
      await categoriaService.criar(categoriaParaEnviar);
      toast.success('Categoria criada com sucesso!');
      
      setFormData({ nome: '', cor: '#FF5733', icone: 'shopping-cart', valorEsperado: '' });
      setMostrarForm(false);
      carregarCategorias();
    } catch (error: any) {
      const mensagem = error.response?.data?.message || error.message || 'Erro ao criar categoria';
      toast.error(mensagem);
      console.error('Erro completo:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleDeletar = async (id: number) => {
    if (!window.confirm('Tem certeza que deseja deletar?')) return;
    
    try {
      await categoriaService.deletar(id);
      toast.success('Categoria deletada!');
      carregarCategorias();
    } catch (error: any) {
      const mensagem = error.response?.data?.message || error.message || 'Erro ao deletar categoria';
      toast.error(mensagem);
      console.error('Erro completo:', error);
    }
  };

  return (
    <Layout>
      <div className="p-8">
        <div className="max-w-6xl mx-auto">
          
          {/* Header */}
          <div className="flex justify-between items-center mb-8">
            <h1 className="text-3xl font-bold text-gray-800">Categorias</h1>
            <button
              onClick={() => setMostrarForm(!mostrarForm)}
              className="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition"
            >
              {mostrarForm ? 'Cancelar' : '+ Nova Categoria'}
            </button>
          </div>

          {/* Mensagem de Erro */}
          {erro && (
            <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg mb-4">
              <p className="font-semibold">Erro ao carregar:</p>
              <p className="text-sm">{erro}</p>
              <button 
                onClick={carregarCategorias}
                className="mt-2 text-sm underline hover:no-underline"
              >
                Tentar novamente
              </button>
            </div>
          )}

          {/* Formulário */}
          {mostrarForm && (
            <div className="bg-white p-6 rounded-lg shadow-md mb-8">
              <h2 className="text-xl font-bold mb-4">Nova Categoria</h2>
              
              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium mb-1 text-gray-700">Nome</label>
                  <input
                    type="text"
                    value={formData.nome}
                    onChange={(e) => setFormData({ ...formData, nome: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    placeholder="Ex: Mercado"
                    required
                  />
                </div>

                <div className="grid grid-cols-2 gap-4">
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
                    <label className="block text-sm font-medium mb-1 text-gray-700">Ícone</label>
                    <input
                      type="text"
                      value={formData.icone}
                      onChange={(e) => setFormData({ ...formData, icone: e.target.value })}
                      className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      placeholder="shopping-cart"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium mb-1 text-gray-700">Valor Esperado (R$)</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    value={formData.valorEsperado}
                    onChange={(e) => setFormData({ ...formData, valorEsperado: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    placeholder="500.00"
                    required
                  />
                </div>

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full bg-green-600 text-white py-2 rounded-lg hover:bg-green-700 transition disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {loading ? 'Salvando...' : 'Salvar Categoria'}
                </button>
              </form>
            </div>
          )}

          {/* Lista de Categorias */}
          <div className="bg-white rounded-lg shadow-md overflow-hidden">
            {loading ? (
              <div className="text-center py-12">
                <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
                <p className="mt-2 text-gray-600">Carregando...</p>
              </div>
            ) : categorias.length === 0 ? (
              <div className="text-center py-12">
                <p className="text-gray-500 text-lg">Nenhuma categoria cadastrada</p>
                <p className="text-gray-400 text-sm mt-2">Clique em "+ Nova Categoria" para começar</p>
              </div>
            ) : (
              <>
                <table className="w-full">
                  <thead className="bg-gray-100">
                    <tr>
                      <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Categoria</th>
                      <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Valor Esperado</th>
                      <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Valor Gasto</th>
                      <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Ações</th>
                    </tr>
                  </thead>
                  <tbody>
                    {categorias.map((cat) => (
                      <tr key={cat.id} className="border-t border-gray-200 hover:bg-gray-50 transition">
                        <td className="px-6 py-4">
                          <div className="flex items-center gap-3">
                            <div
                              className="w-10 h-10 rounded-full flex items-center justify-center text-white font-bold shadow-sm"
                              style={{ backgroundColor: cat.cor || '#666' }}
                            >
                              {cat.icone?.charAt(0).toUpperCase() || '?'}
                            </div>
                            <span className="font-medium text-gray-800">{cat.nome}</span>
                          </div>
                        </td>
                        <td className="px-6 py-4 text-gray-700">
                          R$ {(cat.valorEsperado || 0).toFixed(2)}
                        </td>
                        <td className="px-6 py-4">
                          <span className={`font-semibold ${(cat.valorGasto || 0) > (cat.valorEsperado || 0) ? 'text-red-600' : 'text-green-600'}`}>
                            R$ {(cat.valorGasto || 0).toFixed(2)}
                          </span>
                        </td>
                        <td className="px-6 py-4">
                          <button
                            onClick={() => handleDeletar(cat.id!)}
                            className="text-red-600 hover:text-red-800 font-medium transition"
                          >
                            Deletar
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                <div className="flex items-center justify-between px-6 py-4 border-t border-gray-200">
                  <button
                    onClick={() => setPaginaAtual((prev) => Math.max(prev - 1, 0))}
                    disabled={paginaAtual === 0}
                    className="px-4 py-2 rounded-lg border border-gray-300 disabled:opacity-50"
                  >
                    Anterior
                  </button>
                  <span className="text-sm text-gray-600">
                    Página {paginaAtual + 1} de {totalPaginas}
                  </span>
                  <button
                    onClick={() => setPaginaAtual((prev) => Math.min(prev + 1, totalPaginas - 1))}
                    disabled={paginaAtual >= totalPaginas - 1}
                    className="px-4 py-2 rounded-lg border border-gray-300 disabled:opacity-50"
                  >
                    Próximo
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </Layout>
  );
}