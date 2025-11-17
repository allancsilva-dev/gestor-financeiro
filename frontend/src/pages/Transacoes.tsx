import { useState, useEffect } from 'react';
import { transacaoService, Transacao } from '../services/transacaoService';
import { categoriaService } from '../services/categoriaService';
import { contaService, Conta } from '../services/contaService';
import { useAuth } from '../context/AuthContext'; // ← ADICIONADO
import toast from 'react-hot-toast';
import Layout from '../components/Layout';
import CategoriaDropdown from '../components/CategoriaDropdown';

export default function Transacoes() {
  const { usuario } = useAuth(); // ← ADICIONADO
  const [transacoes, setTransacoes] = useState<any[]>([]);
  const [contas, setContas] = useState<Conta[]>([]);
  const [mostrarForm, setMostrarForm] = useState(false);
  const [loading, setLoading] = useState(false);
  const [editando, setEditando] = useState<any | null>(null);
  
  const [formData, setFormData] = useState({
    descricao: '',
    valorTotal: '',
    tipo: 'SAIDA' as 'ENTRADA' | 'SAIDA',
    data: new Date().toISOString().split('T')[0],
    categoriaNome: '',
    categoriaCor: '',
    categoriaIcone: '',
    contaId: '',
    parcelado: false,
    totalParcelas: ''
  });

  useEffect(() => {
    if (usuario?.id) { // ← ADICIONADO: só carrega se tiver usuário
      carregarDados();
    }
  }, [usuario]);

  const carregarDados = async () => {
    if (!usuario?.id) return; // ← ADICIONADO: proteção

    try {
      setLoading(true);
      const [transacoesData, contasData] = await Promise.all([
        transacaoService.listarPorUsuario(usuario.id), // ← CORRIGIDO!
        contaService.listarPorUsuario(usuario.id)      // ← CORRIGIDO!
      ]);
      setTransacoes(transacoesData);
      // Filtrar apenas cartões de crédito
      const cartoes = contasData.filter((c: Conta) => c.tipo === 'CREDITO');
      setContas(cartoes);
    } catch (error: any) {
      toast.error('Erro ao carregar dados');
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const abrirFormulario = (transacao?: any) => {
    if (transacao) {
      // Modo edição
      setEditando(transacao);
      setFormData({
        descricao: transacao.descricao,
        valorTotal: transacao.valorTotal?.toString() || '',
        tipo: transacao.tipo,
        data: transacao.data,
        categoriaNome: transacao.categoria?.nome || '',
        categoriaCor: transacao.categoria?.cor || '',
        categoriaIcone: transacao.categoria?.icone || '',
        contaId: transacao.conta?.id?.toString() || '',
        parcelado: transacao.parcelado || false,
        totalParcelas: transacao.totalParcelas?.toString() || ''
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
      descricao: '',
      valorTotal: '',
      tipo: 'SAIDA',
      data: new Date().toISOString().split('T')[0],
      categoriaNome: '',
      categoriaCor: '',
      categoriaIcone: '',
      contaId: '',
      parcelado: false,
      totalParcelas: ''
    });
  };

  const handleCategoriaChange = (categoria: { nome: string; cor: string; icone: string }) => {
    setFormData({
      ...formData,
      categoriaNome: categoria.nome,
      categoriaCor: categoria.cor,
      categoriaIcone: categoria.icone
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!usuario?.id) { // ← ADICIONADO: proteção
      toast.error('Usuário não autenticado');
      return;
    }

    if (!formData.categoriaNome) {
      toast.error('Selecione uma categoria');
      return;
    }

    try {
      setLoading(true);
      
      // Primeiro, buscar ou criar a categoria
      let categoriaId;
      try {
        const categoriasExistentes = await categoriaService.listarMinhas();
        const categoriaExistente = categoriasExistentes.find(
          (c: any) => c.nome.toLowerCase() === formData.categoriaNome.toLowerCase()
        );
        
        if (categoriaExistente) {
          categoriaId = categoriaExistente.id;
        } else {
          // Criar nova categoria
          const novaCategoria = await categoriaService.criar({
            nome: formData.categoriaNome,
            cor: formData.categoriaCor,
            icone: formData.categoriaIcone,
            valorEsperado: 0
          });
          categoriaId = novaCategoria.id;
          toast.success('Nova categoria criada!');
        }
      } catch (error) {
        console.error('Erro ao buscar/criar categoria:', error);
        toast.error('Erro ao processar categoria');
        return;
      }
      
      const transacaoParaEnviar: any = {
        usuario: { id: usuario.id }, // ← CORRIGIDO!
        conta: { id: parseInt(formData.contaId) },
        categoria: { id: categoriaId },
        descricao: formData.descricao,
        valorTotal: parseFloat(formData.valorTotal),
        tipo: formData.tipo,
        data: formData.data,
        parcelado: formData.parcelado,
        totalParcelas: formData.parcelado ? parseInt(formData.totalParcelas) : null
      };
      
      if (editando) {
        // Atualizar
        await transacaoService.atualizar(editando.id, transacaoParaEnviar);
        toast.success('Transação atualizada com sucesso!');
      } else {
        // Criar
        await transacaoService.criar(transacaoParaEnviar);
        toast.success('Transação criada com sucesso!');
      }
      
      resetarFormulario();
      setMostrarForm(false);
      carregarDados();
    } catch (error: any) {
      toast.error(editando ? 'Erro ao atualizar transação' : 'Erro ao criar transação');
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
            <h1 className="text-3xl font-bold text-gray-800">Transações</h1>
            <button
              onClick={() => {
                resetarFormulario();
                setMostrarForm(!mostrarForm);
              }}
              className="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition"
            >
              {mostrarForm ? 'Cancelar' : '+ Nova Transação'}
            </button>
          </div>

          {mostrarForm && (
            <div className="bg-white p-6 rounded-lg shadow-md mb-8">
              <h2 className="text-xl font-bold mb-4">
                {editando ? 'Editar Transação' : 'Nova Transação'}
              </h2>
              
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
                      min="0"
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
                    <CategoriaDropdown
                      value={formData.categoriaNome}
                      onChange={handleCategoriaChange}
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Cartão</label>
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

                {!editando && (
                  <>
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
                            {formData.totalParcelas}x de R$ {formatarMoeda(parseFloat(formData.valorTotal) / parseInt(formData.totalParcelas))}
                          </p>
                        )}
                      </div>
                    )}
                  </>
                )}

                {editando && editando.parcelado && (
                  <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-3">
                    <p className="text-sm text-yellow-800">
                      ⚠️ Esta é uma transação parcelada ({editando.totalParcelas}x). Alterar o valor afetará apenas esta transação, não as parcelas.
                    </p>
                  </div>
                )}

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
                    <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Cartão</th>
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
                              {t.totalParcelas}x de R$ {formatarMoeda(t.valorParcela || 0)}
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
                          {t.tipo === 'ENTRADA' ? '+' : '-'} R$ {formatarMoeda(t.valorTotal || 0)}
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex gap-3">
                          <button
                            onClick={() => abrirFormulario(t)}
                            className="text-blue-600 hover:text-blue-800 font-medium transition"
                          >
                            Editar
                          </button>
                          <button
                            onClick={() => handleDeletar(t.id!)}
                            className="text-red-600 hover:text-red-800 font-medium transition"
                          >
                            Deletar
                          </button>
                        </div>
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