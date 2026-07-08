import { useState, useEffect } from 'react';
import { investimentoService, Ativo, Movimentacao } from '../services/investimentoService';
import Layout from '../components/Layout';
import toast from 'react-hot-toast';
import { formatCurrency } from '../utils/currency';

const TIPOS_ATIVO = ['ACAO', 'FII', 'RENDA_FIXA', 'CRIPTO', 'OUTRO'];
const TIPOS_MOV = ['COMPRA', 'VENDA', 'DIVIDENDO', 'BONIFICACAO'];

export default function Investimentos() {
  const [ativos, setAtivos] = useState<Ativo[]>([]);
  const [loading, setLoading] = useState(true);
  const [mostrarForm, setMostrarForm] = useState(false);
  const [ativoSelecionado, setAtivoSelecionado] = useState<Ativo | null>(null);
  const [movimentacoes, setMovimentacoes] = useState<Movimentacao[]>([]);
  const [form, setForm] = useState({ ticker: '', nome: '', tipo: 'ACAO', valorAtual: '' });
  const [movForm, setMovForm] = useState({ tipo: 'COMPRA', data: new Date().toISOString().split('T')[0], quantidade: '', precoUnitario: '' });

  useEffect(() => { carregar(); }, []);

  const carregar = async () => {
    setLoading(true);
    try {
      setAtivos(await investimentoService.listar());
    } catch { toast.error('Erro ao carregar'); }
    setLoading(false);
  };

  const carregarMovs = async (ativoId: number) => {
    try {
      setMovimentacoes(await investimentoService.listarMovimentacoes(ativoId));
    } catch { setMovimentacoes([]); }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await investimentoService.criar({ ...form, valorAtual: form.valorAtual ? parseFloat(form.valorAtual) : undefined });
      toast.success('Ativo criado');
      setMostrarForm(false);
      setForm({ ticker: '', nome: '', tipo: 'ACAO', valorAtual: '' });
      carregar();
    } catch { toast.error('Erro ao criar'); }
  };

  const handleMov = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!ativoSelecionado) return;
    try {
      await investimentoService.adicionarMovimentacao(ativoSelecionado.id!, {
        tipo: movForm.tipo,
        data: movForm.data,
        quantidade: parseFloat(movForm.quantidade),
        precoUnitario: parseFloat(movForm.precoUnitario),
      });
      toast.success('Movimentação registrada');
      carregarMovs(ativoSelecionado.id!);
      carregar();
    } catch { toast.error('Erro ao registrar'); }
  };

  const handleDeletar = async (id: number) => {
    if (!confirm('Excluir ativo?')) return;
    try {
      await investimentoService.deletar(id);
      carregar();
    } catch { toast.error('Erro ao excluir'); }
  };

  if (loading) return <Layout><div className="p-8 text-center"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600 mx-auto" /></div></Layout>;

  return (
    <Layout>
      <div className="p-8 max-w-6xl mx-auto">
        <div className="flex justify-between items-center mb-8">
          <h1 className="text-3xl font-bold text-gray-800">Investimentos</h1>
          <button onClick={() => setMostrarForm(!mostrarForm)}
            className="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700">
            {mostrarForm ? 'Cancelar' : '+ Novo Ativo'}
          </button>
        </div>

        {mostrarForm && (
          <form onSubmit={handleSubmit} className="bg-white p-6 rounded-lg shadow-md mb-8 space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1">Ticker</label>
                <input value={form.ticker} onChange={e => setForm({...form, ticker: e.target.value})}
                  className="w-full border rounded px-3 py-2" required placeholder="PETR4" />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Nome</label>
                <input value={form.nome} onChange={e => setForm({...form, nome: e.target.value})}
                  className="w-full border rounded px-3 py-2" required placeholder="Petrobras" />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Tipo</label>
                <select value={form.tipo} onChange={e => setForm({...form, tipo: e.target.value})}
                  className="w-full border rounded px-3 py-2">
                  {TIPOS_ATIVO.map(t => <option key={t} value={t}>{t}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Cotação atual (R$)</label>
                <input value={form.valorAtual} onChange={e => setForm({...form, valorAtual: e.target.value})}
                  className="w-full border rounded px-3 py-2" placeholder="32,50" />
              </div>
            </div>
            <button type="submit" className="bg-green-600 text-white px-6 py-2 rounded-lg hover:bg-green-700">Salvar</button>
          </form>
        )}

        {ativos.length === 0 ? (
          <p className="text-gray-500 text-center py-12">Nenhum ativo cadastrado</p>
        ) : (
          <div className="space-y-4">
            {ativos.map(a => (
              <div key={a.id} className="bg-white rounded-lg shadow-md p-6">
                <div className="flex justify-between items-start">
                  <div>
                    <h2 className="text-xl font-bold">{a.ticker} <span className="text-sm text-gray-500 font-normal">{a.nome}</span></h2>
                    <p className="text-sm text-gray-500 mt-1">
                      {a.quantidade} unidades | PM: {formatCurrency(a.precoMedio)} | Custo: {formatCurrency(a.custoTotal)}
                    </p>
                    {a.valorAtual && (
                      <p className="text-sm mt-1">
                        Cotação: {formatCurrency(a.valorAtual)} | {' '}
                        <span className={a.lucroPrejuizo >= 0 ? 'text-green-600' : 'text-red-600'}>
                          {a.rentabilidade >= 0 ? '+' : ''}{a.rentabilidade?.toFixed(2)}% ({formatCurrency(a.lucroPrejuizo || 0)})
                        </span>
                      </p>
                    )}
                  </div>
                  <div className="flex gap-2">
                    <button onClick={() => { setAtivoSelecionado(a); carregarMovs(a.id!); setMovForm({ tipo: 'COMPRA', data: new Date().toISOString().split('T')[0], quantidade: '', precoUnitario: '' }); }}
                      className="text-blue-600 hover:text-blue-800 text-sm">+ Mov</button>
                    <button onClick={() => handleDeletar(a.id!)}
                      className="text-red-600 hover:text-red-800 text-sm">Excluir</button>
                  </div>
                </div>

                {ativoSelecionado?.id === a.id && (
                  <div className="mt-4 border-t pt-4">
                    <form onSubmit={handleMov} className="flex gap-2 items-end flex-wrap mb-4">
                      <select value={movForm.tipo} onChange={e => setMovForm({...movForm, tipo: e.target.value})}
                        className="border rounded px-2 py-1 text-sm">
                        {TIPOS_MOV.map(t => <option key={t} value={t}>{t}</option>)}
                      </select>
                      <input type="date" value={movForm.data} onChange={e => setMovForm({...movForm, data: e.target.value})}
                        className="border rounded px-2 py-1 text-sm" />
                      <input value={movForm.quantidade} onChange={e => setMovForm({...movForm, quantidade: e.target.value})}
                        className="border rounded px-2 py-1 text-sm w-24" placeholder="Qtd" required />
                      <input value={movForm.precoUnitario} onChange={e => setMovForm({...movForm, precoUnitario: e.target.value})}
                        className="border rounded px-2 py-1 text-sm w-28" placeholder="Preço" required />
                      <button type="submit" className="bg-blue-600 text-white px-3 py-1 rounded text-sm">Registrar</button>
                    </form>

                    {movimentacoes.length > 0 ? (
                      <table className="w-full text-sm">
                        <thead><tr className="text-left text-gray-500"><th>Data</th><th>Tipo</th><th>Qtd</th><th>Preço</th><th>Total</th></tr></thead>
                        <tbody>
                          {movimentacoes.map(m => (
                            <tr key={m.id} className="border-t">
                              <td className="py-1">{new Date(m.data).toLocaleDateString('pt-BR')}</td>
                              <td className={m.tipo === 'COMPRA' ? 'text-green-600' : m.tipo === 'VENDA' ? 'text-red-600' : 'text-blue-600'}>{m.tipo}</td>
                              <td>{m.quantidade}</td>
                              <td>{formatCurrency(m.precoUnitario)}</td>
                              <td>{formatCurrency(m.valorTotal)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    ) : <p className="text-sm text-gray-400">Sem movimentações</p>}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </Layout>
  );
}
