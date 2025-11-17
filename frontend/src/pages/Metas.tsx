import { useState, useEffect } from 'react';
import { metaService, Meta } from '../services/metaService';
import { useAuth } from '../context/AuthContext';
import toast from 'react-hot-toast';
import Layout from '../components/Layout';

export default function Metas() {
  const { usuario } = useAuth();
  const [metas, setMetas] = useState<Meta[]>([]);
  const [mostrarForm, setMostrarForm] = useState(false);
  const [mostrarAdicionar, setMostrarAdicionar] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [editando, setEditando] = useState<Meta | null>(null);
  
  const [formData, setFormData] = useState({
    nome: '',
    valorTotal: '',
    valorMensal: '',
    cor: '#3498DB',
    icone: 'target',
    descricao: ''
  });

  const [valorAdicionar, setValorAdicionar] = useState('');

  useEffect(() => {
    if (usuario?.id) {
      carregarMetas();
    }
  }, [usuario]);

  const carregarMetas = async () => {
    if (!usuario?.id) return;

    try {
      setLoading(true);
      const data = await metaService.listarPorUsuario(usuario.id);
      setMetas(data);
    } catch (error: any) {
      toast.error('Erro ao carregar metas');
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const abrirFormulario = (meta?: Meta) => {
    if (meta) {
      // Modo edição
      setEditando(meta);
      setFormData({
        nome: meta.nome,
        valorTotal: meta.valorTotal?.toString() || '',
        valorMensal: meta.valorMensal?.toString() || '',
        cor: meta.cor || '#3498DB',
        icone: meta.icone || 'target',
        descricao: meta.descricao || ''
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
      valorTotal: '',
      valorMensal: '',
      cor: '#3498DB',
      icone: 'target',
      descricao: ''
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
      
      const metaParaEnviar = {
        usuario: { id: usuario.id },
        nome: formData.nome,
        valorTotal: parseFloat(formData.valorTotal),
        valorMensal: parseFloat(formData.valorMensal),
        cor: formData.cor,
        icone: formData.icone,
        descricao: formData.descricao
      };
      
      if (editando) {
        // Atualizar
        await metaService.atualizar(editando.id!, metaParaEnviar);
        toast.success('Meta atualizada com sucesso!');
      } else {
        // Criar
        await metaService.criar(metaParaEnviar);
        toast.success('Meta criada com sucesso!');
      }
      
      resetarFormulario();
      setMostrarForm(false);
      carregarMetas();
    } catch (error: any) {
      toast.error(editando ? 'Erro ao atualizar meta' : 'Erro ao criar meta');
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const handleAdicionarValor = async (metaId: number) => {
    if (!valorAdicionar) return;
    
    try {
      await metaService.adicionarValor(metaId, parseFloat(valorAdicionar));
      toast.success('Valor adicionado!');
      setValorAdicionar('');
      setMostrarAdicionar(null);
      carregarMetas();
    } catch (error: any) {
      toast.error('Erro ao adicionar valor');
      console.error(error);
    }
  };

  const handleDeletar = async (id: number) => {
    if (!window.confirm('Tem certeza que deseja deletar?')) return;
    
    try {
      await metaService.deletar(id);
      toast.success('Meta deletada!');
      carregarMetas();
    } catch (error: any) {
      toast.error('Erro ao deletar meta');
      console.error(error);
    }
  };

  const calcularProgresso = (meta: Meta) => {
    return ((meta.valorReservado || 0) / meta.valorTotal) * 100;
  };

  const calcularMesesRestantes = (meta: Meta) => {
    const faltam = meta.valorTotal - (meta.valorReservado || 0);
    return Math.ceil(faltam / meta.valorMensal);
  };

  return (
    <Layout>
      <div className="p-8">
        <div className="max-w-6xl mx-auto">
          
          <div className="flex justify-between items-center mb-8">
            <h1 className="text-3xl font-bold text-gray-800">Metas</h1>
            <button
              onClick={() => {
                resetarFormulario();
                setMostrarForm(!mostrarForm);
              }}
              className="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition"
            >
              {mostrarForm ? 'Cancelar' : '+ Nova Meta'}
            </button>
          </div>

          {mostrarForm && (
            <div className="bg-white p-6 rounded-lg shadow-md mb-8">
              <h2 className="text-xl font-bold mb-4">
                {editando ? 'Editar Meta' : 'Nova Meta'}
              </h2>
              
              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium mb-1 text-gray-700">Nome da Meta</label>
                  <input
                    type="text"
                    value={formData.nome}
                    onChange={(e) => setFormData({ ...formData, nome: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                    placeholder="Ex: Viagem para Europa"
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
                      placeholder="15000.00"
                      required
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Valor Mensal (R$)</label>
                    <input
                      type="number"
                      step="0.01"
                      value={formData.valorMensal}
                      onChange={(e) => setFormData({ ...formData, valorMensal: e.target.value })}
                      className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                      placeholder="500.00"
                      required
                    />
                  </div>
                </div>

                {formData.valorTotal && formData.valorMensal && (
                  <p className="text-sm text-gray-600">
                    📅 Previsão: {Math.ceil(parseFloat(formData.valorTotal) / parseFloat(formData.valorMensal))} meses
                  </p>
                )}

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
                      className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                      placeholder="target"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium mb-1 text-gray-700">Descrição</label>
                  <textarea
                    value={formData.descricao}
                    onChange={(e) => setFormData({ ...formData, descricao: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                    placeholder="Ex: Viagem de 15 dias para Paris, Londres e Roma"
                    rows={3}
                  />
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

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {loading ? (
              <div className="col-span-2 text-center py-12">
                <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
                <p className="mt-2 text-gray-600">Carregando...</p>
              </div>
            ) : metas.length === 0 ? (
              <div className="col-span-2 text-center py-12">
                <p className="text-gray-500 text-lg">Nenhuma meta cadastrada</p>
                <p className="text-gray-400 text-sm mt-2">Clique em "+ Nova Meta" para começar</p>
              </div>
            ) : (
              metas.map((meta) => (
                <div
                  key={meta.id}
                  className="bg-white rounded-lg shadow-md p-6 hover:shadow-lg transition"
                  style={{ borderTop: `4px solid ${meta.cor || '#3498DB'}` }}
                >
                  <div className="flex justify-between items-start mb-4">
                    <div className="flex items-center gap-3">
                      <div
                        className="w-12 h-12 rounded-full flex items-center justify-center text-white text-xl font-bold"
                        style={{ backgroundColor: meta.cor || '#3498DB' }}
                      >
                        {meta.icone?.charAt(0).toUpperCase() || '🎯'}
                      </div>
                      <div>
                        <h3 className="text-xl font-bold text-gray-800">{meta.nome}</h3>
                        {meta.descricao && (
                          <p className="text-sm text-gray-500">{meta.descricao}</p>
                        )}
                      </div>
                    </div>
                    <div className="flex gap-2">
                      <button
                        onClick={() => abrirFormulario(meta)}
                        className="text-blue-600 hover:text-blue-800 text-sm"
                      >
                        Editar
                      </button>
                      <button
                        onClick={() => handleDeletar(meta.id!)}
                        className="text-red-600 hover:text-red-800 text-sm"
                      >
                        Deletar
                      </button>
                    </div>
                  </div>

                  <div className="mb-4">
                    <div className="flex justify-between text-sm text-gray-600 mb-1">
                      <span>R$ {(meta.valorReservado || 0).toFixed(2)}</span>
                      <span>R$ {meta.valorTotal.toFixed(2)}</span>
                    </div>
                    <div className="w-full bg-gray-200 rounded-full h-4">
                      <div
                        className="h-4 rounded-full transition-all"
                        style={{ 
                          width: `${Math.min(calcularProgresso(meta), 100)}%`,
                          backgroundColor: meta.cor || '#3498DB'
                        }}
                      ></div>
                    </div>
                    <p className="text-center text-lg font-bold text-gray-800 mt-2">
                      {calcularProgresso(meta).toFixed(1)}%
                    </p>
                  </div>

                  <div className="grid grid-cols-2 gap-4 mb-4 text-sm">
                    <div>
                      <p className="text-gray-600">Faltam</p>
                      <p className="font-bold text-gray-800">
                        R$ {(meta.valorTotal - (meta.valorReservado || 0)).toFixed(2)}
                      </p>
                    </div>
                    <div>
                      <p className="text-gray-600">Meses restantes</p>
                      <p className="font-bold text-gray-800">
                        {calcularMesesRestantes(meta)} meses
                      </p>
                    </div>
                  </div>

                  {mostrarAdicionar === meta.id ? (
                    <div className="space-y-2">
                      <input
                        type="number"
                        step="0.01"
                        value={valorAdicionar}
                        onChange={(e) => setValorAdicionar(e.target.value)}
                        className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                        placeholder="Valor a adicionar"
                      />
                      <div className="flex gap-2">
                        <button
                          onClick={() => handleAdicionarValor(meta.id!)}
                          className="flex-1 bg-green-600 text-white py-2 rounded-lg hover:bg-green-700 transition"
                        >
                          Confirmar
                        </button>
                        <button
                          onClick={() => {
                            setMostrarAdicionar(null);
                            setValorAdicionar('');
                          }}
                          className="flex-1 bg-gray-300 text-gray-700 py-2 rounded-lg hover:bg-gray-400 transition"
                        >
                          Cancelar
                        </button>
                      </div>
                    </div>
                  ) : (
                    <button
                      onClick={() => setMostrarAdicionar(meta.id!)}
                      className="w-full bg-blue-600 text-white py-2 rounded-lg hover:bg-blue-700 transition"
                    >
                      💰 Adicionar Dinheiro
                    </button>
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