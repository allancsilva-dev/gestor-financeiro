import { useState, useEffect } from 'react';
import Layout from '../components/Layout';
import toast from 'react-hot-toast';
import orcamentoService, { OrcamentoResponse, OrcamentoCategoriaItem } from '../services/orcamentoService';
import { categoriaService, Categoria } from '../services/categoriaService';
import { useAuth } from '../context/AuthContext';
import { formatCurrency } from '../utils/currency';
import { TrendingDown, TrendingUp, Plus, Trash2, Save, ChevronLeft, ChevronRight } from 'lucide-react';
import FieldError from '../components/FieldError';
import { fieldA11y } from '../validation/fieldA11y';
import { useZodForm } from '../hooks/useZodForm';
import { orcamentoSchema } from '../validation/schemas';

const MESES = [
  'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
  'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro',
];

function getProgressColor(percentual: number): string {
  if (percentual >= 100) return 'bg-red-500';
  if (percentual >= 75) return 'bg-yellow-500';
  return 'bg-green-500';
}

function getStatusColor(percentual: number): string {
  if (percentual >= 100) return 'text-red-400';
  if (percentual >= 75) return 'text-yellow-400';
  return 'text-green-400';
}

export default function Orcamentos() {
  const { usuario } = useAuth();
  const [orcamento, setOrcamento] = useState<OrcamentoResponse | null>(null);
  const [categorias, setCategorias] = useState<Categoria[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [editando, setEditando] = useState(false);

  const now = new Date();
  const [mes, setMes] = useState(now.getMonth() + 1);
  const [ano, setAno] = useState(now.getFullYear());

  const [limites, setLimites] = useState<Map<number, string>>(new Map());
  const validation = useZodForm(orcamentoSchema);

  const carregar = async (m: number, a: number) => {
    setLoading(true);
    try {
      if (m === now.getMonth() + 1 && a === now.getFullYear()) {
        const data = await orcamentoService.buscarAtual();
        setOrcamento(data);
      } else {
        try {
          const data = await orcamentoService.buscarPorMes(m, a);
          setOrcamento(data);
        } catch {
          setOrcamento(null);
        }
      }
      const cats = await categoriaService.listarMinhas(0, 100);
      setCategorias(cats);
    } catch (err: any) {
      toast.error('Erro ao carregar orçamento');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (usuario?.id) carregar(mes, ano);
  }, [usuario?.id, mes, ano]);

  const iniciarEdicao = () => {
    const map = new Map<number, string>();
    if (orcamento) {
      orcamento.categorias.forEach((c) => map.set(c.categoriaId, c.valorLimite.toString()));
    }
    // Add all categories that don't have limits yet
    categorias.forEach((c) => {
      if (c.id && !map.has(c.id)) map.set(c.id, '');
    });
    setLimites(map);
    setEditando(true);
  };

  const handleSalvar = async () => {
    const catsInput = Array.from(limites.entries())
      .filter(([, valor]) => valor.trim() !== '')
      .map(([categoriaId, valorLimite]) => ({ categoriaId, valorLimite }));
    const payload = validation.validate({ mes, ano, categorias: catsInput });
    if (!payload) return;

    setSaving(true);
    try {
      const data = await orcamentoService.criarOuAtualizar(payload);
      setOrcamento(data);
      setEditando(false);
      validation.resetValidation();
      toast.success('Orçamento salvo!');
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Erro ao salvar');
    } finally {
      setSaving(false);
    }
  };

  const mesAnterior = () => {
    if (mes === 1) { setMes(12); setAno(ano - 1); }
    else setMes(mes - 1);
  };

  const mesProximo = () => {
    if (mes === 12) { setMes(1); setAno(ano + 1); }
    else setMes(mes + 1);
  };

  return (
    <Layout>
      <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 p-8">
        <div className="max-w-4xl mx-auto">
          {/* Header */}
          <div className="flex items-center justify-between mb-6">
            <div>
              <h1 className="text-2xl font-bold text-white">Orçamento Mensal</h1>
              <p className="text-sm text-slate-400 mt-1">Planeje e acompanhe seus gastos por categoria</p>
            </div>
          </div>

          {/* Navegação de mês */}
          <div className="flex items-center justify-center gap-4 mb-8">
            <button onClick={mesAnterior} className="p-2 hover:bg-slate-700/50 rounded-lg transition-colors">
              <ChevronLeft className="w-5 h-5 text-slate-400" />
            </button>
            <h2 className="text-lg font-semibold text-white min-w-40 text-center">
              {MESES[mes - 1]} {ano}
            </h2>
            <button onClick={mesProximo} className="p-2 hover:bg-slate-700/50 rounded-lg transition-colors">
              <ChevronRight className="w-5 h-5 text-slate-400" />
            </button>
          </div>

          {loading ? (
            <div className="text-center py-12">
              <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-orange-500" />
            </div>
          ) : !editando && !orcamento?.categorias?.length ? (
            /* Empty state */
            <div className="text-center py-16 bg-slate-800/50 border border-slate-700 rounded-2xl">
              <div className="text-5xl mb-4">📊</div>
              <h3 className="text-lg font-semibold text-white mb-2">Nenhum orçamento para {MESES[mes - 1]}</h3>
              <p className="text-sm text-slate-400 mb-6">Defina limites de gasto por categoria para este mês</p>
              <button
                onClick={iniciarEdicao}
                className="px-6 py-3 bg-gradient-to-r from-orange-500 to-orange-600 text-white rounded-lg font-medium hover:from-orange-600 hover:to-orange-700 transition-all shadow-lg shadow-orange-500/20"
              >
                Criar Orçamento
              </button>
            </div>
          ) : editando ? (
            /* Modo edição */
            <div className="bg-slate-800 border border-slate-700 rounded-2xl p-6">
              <h3 className="text-lg font-semibold text-white mb-4">Definir limites por categoria</h3>
              <p className="text-sm text-slate-400 mb-6">Informe o valor máximo que deseja gastar em cada categoria</p>
              <div className="space-y-3">
                {categorias.map((cat) => {
                  const valor = limites.get(cat.id!) || '';
                  const entryIndex = Array.from(limites.entries()).filter(([, item]) => item.trim() !== '').findIndex(([id]) => id === cat.id);
                  const fieldName = entryIndex >= 0 ? `categorias.${entryIndex}.valorLimite` : `limite-${cat.id}`;
                  return (
                    <div key={cat.id} className="flex items-center gap-3 p-3 bg-slate-900/50 rounded-lg border border-slate-700">
                      <span className="text-lg w-8">{cat.icone || '📌'}</span>
                      <span className="flex-1 text-sm text-white font-medium">{cat.nome}</span>
                      <div className="relative">
                        <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500 text-sm">R$</span>
                        <input
                          {...fieldA11y(fieldName, validation.errors[fieldName])}
                          type="number"
                          step="0.01"
                          min="0"
                          value={valor}
                          onChange={(e) => {
                            const novo = new Map(limites);
                            novo.set(cat.id!, e.target.value);
                            setLimites(novo);
                            const nextEntries = Array.from(novo.entries()).filter(([, item]) => item.trim() !== '').map(([categoriaId, valorLimite]) => ({ categoriaId, valorLimite }));
                            const nextIndex = nextEntries.findIndex(item => item.categoriaId === cat.id);
                            if (nextIndex >= 0) validation.revalidateField(`categorias.${nextIndex}.valorLimite`, { mes, ano, categorias: nextEntries });
                          }}
                          className="w-32 bg-slate-800 border border-slate-600 rounded-lg pl-10 pr-3 py-2 text-white text-right focus:outline-none focus:border-orange-500 aria-invalid:border-red-500"
                          placeholder="0,00"
                        />
                        <FieldError name={fieldName} error={validation.errors[fieldName]} />
                      </div>
                    </div>
                  );
                })}
              </div>
              <FieldError name="categorias" error={validation.errors.categorias} />
              <div className="flex gap-3 mt-6">
                <button
                  onClick={() => { setEditando(false); validation.resetValidation(); }}
                  className="px-4 py-2.5 border border-slate-600 text-slate-300 rounded-lg hover:bg-slate-700/50 transition-colors"
                >
                  Cancelar
                </button>
                <button
                  onClick={handleSalvar}
                  disabled={saving}
                  className="flex items-center gap-2 px-6 py-2.5 bg-gradient-to-r from-orange-500 to-orange-600 text-white rounded-lg font-medium hover:from-orange-600 hover:to-orange-700 transition-all shadow-lg shadow-orange-500/20 disabled:opacity-50"
                >
                  {saving ? (
                    <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  ) : (
                    <Save className="w-4 h-4" />
                  )}
                  Salvar
                </button>
              </div>
            </div>
          ) : (
            /* Modo visualização */
            <>
              {/* Resumo */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
                <div className="bg-slate-800 border border-slate-700 rounded-xl p-5">
                  <p className="text-xs text-slate-400 uppercase tracking-wider mb-1">Planejado</p>
                  <p className="text-2xl font-bold text-white">{formatCurrency(orcamento?.valorTotalPlanejado || 0)}</p>
                </div>
                <div className="bg-slate-800 border border-slate-700 rounded-xl p-5">
                  <p className="text-xs text-slate-400 uppercase tracking-wider mb-1">Gasto</p>
                  <p className="text-2xl font-bold text-red-400">{formatCurrency(orcamento?.valorTotalGasto || 0)}</p>
                </div>
                <div className="bg-slate-800 border border-slate-700 rounded-xl p-5">
                  <p className="text-xs text-slate-400 uppercase tracking-wider mb-1">Disponível</p>
                  <p className={`text-2xl font-bold ${(orcamento?.valorTotalPlanejado || 0) - (orcamento?.valorTotalGasto || 0) >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                    {formatCurrency((orcamento?.valorTotalPlanejado || 0) - (orcamento?.valorTotalGasto || 0))}
                  </p>
                </div>
              </div>

              {/* Categorias */}
              <div className="bg-slate-800 border border-slate-700 rounded-2xl p-6">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="text-lg font-semibold text-white">Categorias</h3>
                  <button
                    onClick={iniciarEdicao}
                    className="flex items-center gap-1.5 text-sm text-orange-400 hover:text-orange-300 transition-colors"
                  >
                    <Plus className="w-4 h-4" />
                    Editar
                  </button>
                </div>
                <div className="space-y-4">
                  {orcamento?.categorias.map((cat: OrcamentoCategoriaItem) => (
                    <div key={cat.id} className="space-y-2">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className="text-lg">{cat.categoriaIcone || '📌'}</span>
                          <span className="text-sm text-white font-medium">{cat.categoriaNome}</span>
                        </div>
                        <div className="text-right">
                          <p className={`text-sm font-semibold ${getStatusColor(cat.percentualGasto)}`}>
                            {formatCurrency(cat.valorGasto)} / {formatCurrency(cat.valorLimite)}
                          </p>
                          <p className={`text-xs ${getStatusColor(cat.percentualGasto)}`}>
                            {cat.percentualGasto}%
                          </p>
                        </div>
                      </div>
                      <div className="w-full h-2.5 bg-slate-700 rounded-full overflow-hidden">
                        <div
                          className={`h-full rounded-full transition-all duration-500 ${getProgressColor(cat.percentualGasto)}`}
                          style={{ width: `${Math.min(cat.percentualGasto, 100)}%` }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </Layout>
  );
}
