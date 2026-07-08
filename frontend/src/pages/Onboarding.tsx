import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { onboardingService } from '../services/onboardingService';
import carteiraService from '../services/carteiraService';
import { contaService } from '../services/contaService';
import { categoriaService } from '../services/categoriaService';
import contaFixaService from '../services/contaFixaService';
import { metaService } from '../services/metaService';
import toast from 'react-hot-toast';
import { Check, ChevronLeft, ChevronRight, Wallet, CreditCard, Tag, TrendingUp, Target, List } from 'lucide-react';

const PASSOS = [
  { titulo: 'Carteira', icone: Wallet, descricao: 'Onde seu dinheiro está' },
  { titulo: 'Conta/Cartão', icone: CreditCard, descricao: 'Meio de pagamento' },
  { titulo: 'Categorias', icone: Tag, descricao: 'Organize seus gastos' },
  { titulo: 'Renda', icone: TrendingUp, descricao: 'Sua renda mensal' },
  { titulo: 'Meta', icone: Target, descricao: 'Seu objetivo financeiro' },
  { titulo: 'Confirmar', icone: List, descricao: 'Revisar e finalizar' },
];

const CATEGORIAS_SUGERIDAS = [
  { nome: 'Alimentação', cor: '#EF4444', icone: '🍔' },
  { nome: 'Transporte', cor: '#F59E0B', icone: '🚗' },
  { nome: 'Moradia', cor: '#8B5CF6', icone: '🏠' },
  { nome: 'Saúde', cor: '#EC4899', icone: '🏥' },
  { nome: 'Educação', cor: '#3B82F6', icone: '📚' },
  { nome: 'Lazer', cor: '#10B981', icone: '🎮' },
  { nome: 'Vestuário', cor: '#6366F1', icone: '👕' },
  { nome: 'Investimentos', cor: '#14B8A6', icone: '📈' },
  { nome: 'Assinaturas', cor: '#F97316', icone: '📱' },
  { nome: 'Outros', cor: '#6B7280', icone: '📦' },
];

export default function Onboarding() {
  const navigate = useNavigate();
  const { refreshUser } = useAuth();
  const [passo, setPasso] = useState(0);
  const [loading, setLoading] = useState(false);

  const [carteira, setCarteira] = useState({ nome: 'Carteira Principal', tipo: 'CONTA_BANCARIA', saldo: 0, banco: '' });
  const [conta, setConta] = useState({ nome: 'Cartão Principal', tipo: 'CREDITO', limiteTotal: 0 });
  const [categoriasSelecionadas, setCategoriasSelecionadas] = useState<string[]>(
    CATEGORIAS_SUGERIDAS.map((c) => c.nome)
  );
  const [renda, setRenda] = useState({ nome: 'Salário', valor: 0, diaVencimento: 1 });
  const [pularRenda, setPularRenda] = useState(false);
  const [meta, setMeta] = useState({ nome: '', valorTotal: 0, valorMensal: 0, dataPrevista: '' });
  const [pularMeta, setPularMeta] = useState(false);

  const [resumo, setResumo] = useState<{
    carteiraCriada?: string;
    contaCriada?: string;
    categoriasCriadas?: string[];
    rendaCriada?: string;
    metaCriada?: string;
  }>({});

  const TOTAL_PASSOS = PASSOS.length;
  const podeAvancar = () => {
    if (passo === 0) return carteira.nome.trim().length > 0;
    if (passo === 1) return conta.nome.trim().length > 0;
    if (passo === 2) return categoriasSelecionadas.length > 0;
    if (passo === 3) return pularRenda || (renda.nome.trim().length > 0 && renda.valor > 0);
    if (passo === 4) return pularMeta || (meta.nome.trim().length > 0 && meta.valorTotal > 0);
    return true;
  };

  const handleCriarCarteira = async () => {
    setLoading(true);
    try {
      await carteiraService.criar({
        nome: carteira.nome,
        tipo: carteira.tipo,
        saldo: carteira.saldo,
        banco: carteira.banco || undefined,
      });
      setResumo((r) => ({ ...r, carteiraCriada: `${carteira.nome} (${carteira.tipo})` }));
      setPasso(1);
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Erro ao criar carteira');
    } finally {
      setLoading(false);
    }
  };

  const handleCriarConta = async () => {
    setLoading(true);
    try {
      await contaService.criar({
        nome: conta.nome,
        tipo: conta.tipo,
        limiteTotal: conta.tipo === 'CREDITO' ? conta.limiteTotal : undefined,
      });
      setResumo((r) => ({ ...r, contaCriada: `${conta.nome} (${conta.tipo})` }));
      setPasso(2);
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Erro ao criar conta');
    } finally {
      setLoading(false);
    }
  };

  const handleCriarCategorias = async () => {
    setLoading(true);
    try {
      const nomes: string[] = [];
      for (const nome of categoriasSelecionadas) {
        const sugerida = CATEGORIAS_SUGERIDAS.find((c) => c.nome === nome);
        await categoriaService.criar({
          nome,
          cor: sugerida?.cor || '#6B7280',
          icone: sugerida?.icone || '📌',
          valorEsperado: 0,
        });
        nomes.push(nome);
      }
      setResumo((r) => ({ ...r, categoriasCriadas: nomes }));
      setPasso(3);
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Erro ao criar categorias');
    } finally {
      setLoading(false);
    }
  };

  const handleCriarRenda = async () => {
    if (pularRenda) {
      setResumo((r) => ({ ...r, rendaCriada: 'Pulado' }));
      setPasso(4);
      return;
    }
    setLoading(true);
    try {
      await contaFixaService.criar({
        nome: renda.nome,
        valorPlanejado: renda.valor,
        diaVencimento: renda.diaVencimento,
        recorrente: true,
      });
      setResumo((r) => ({ ...r, rendaCriada: `${renda.nome} R$ ${renda.valor.toFixed(2)}` }));
      setPasso(4);
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Erro ao criar renda');
    } finally {
      setLoading(false);
    }
  };

  const handleCriarMeta = async () => {
    if (pularMeta) {
      setResumo((r) => ({ ...r, metaCriada: 'Pulado' }));
      setPasso(5);
      return;
    }
    setLoading(true);
    try {
      await metaService.criar({
        nome: meta.nome,
        valorTotal: meta.valorTotal,
        valorMensal: meta.valorMensal || 0,
        dataPrevista: meta.dataPrevista || undefined,
      });
      setResumo((r) => ({ ...r, metaCriada: `${meta.nome}` }));
      setPasso(5);
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Erro ao criar meta');
    } finally {
      setLoading(false);
    }
  };

  const handleFinalizar = async () => {
    setLoading(true);
    try {
      await onboardingService.completar();
      await refreshUser();
      toast.success('Configuração concluída! Bem-vindo(a) ao Gestor Financeiro!');
      navigate('/dashboard');
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Erro ao finalizar');
    } finally {
      setLoading(false);
    }
  };

  const handleAvancar = () => {
    if (passo === 0) handleCriarCarteira();
    else if (passo === 1) handleCriarConta();
    else if (passo === 2) handleCriarCategorias();
    else if (passo === 3) handleCriarRenda();
    else if (passo === 4) handleCriarMeta();
  };

  const toggleCategoria = (nome: string) => {
    setCategoriasSelecionadas((prev) =>
      prev.includes(nome) ? prev.filter((c) => c !== nome) : [...prev, nome]
    );
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 flex items-center justify-center p-4">
      <div className="w-full max-w-lg">
        {/* Progresso */}
        <div className="mb-8">
          <div className="flex justify-between mb-3">
            {PASSOS.map((_, i) => (
              <div
                key={i}
                className={`flex-1 h-1.5 rounded-full mx-0.5 transition-all duration-300 ${
                  i < passo ? 'bg-orange-500' : i === passo ? 'bg-orange-500 animate-pulse' : 'bg-slate-700'
                }`}
              />
            ))}
          </div>
          <p className="text-center text-xs text-slate-500">
            Passo {passo + 1} de {TOTAL_PASSOS}
          </p>
        </div>

        {/* Card */}
        <div className="bg-slate-800/80 backdrop-blur-sm border border-slate-700 rounded-2xl p-8 shadow-2xl">
          {/* Header */}
          <div className="text-center mb-8">
            <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-gradient-to-br from-orange-500 to-orange-600 shadow-lg shadow-orange-500/20 mb-4">
              {(() => {
                const Icone = PASSOS[passo].icone;
                return <Icone className="w-7 h-7 text-white" />;
              })()}
            </div>
            <h2 className="text-xl font-bold text-white">{PASSOS[passo].titulo}</h2>
            <p className="text-sm text-slate-400 mt-1">{PASSOS[passo].descricao}</p>
          </div>

          {/* Step 0: Carteira */}
          {passo === 0 && (
            <div className="space-y-4">
              <div>
                <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Nome</label>
                <input
                  type="text"
                  value={carteira.nome}
                  onChange={(e) => setCarteira((c) => ({ ...c, nome: e.target.value }))}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-orange-500 transition-colors"
                  placeholder="Ex: Conta Principal"
                />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Tipo</label>
                <select
                  value={carteira.tipo}
                  onChange={(e) => setCarteira((c) => ({ ...c, tipo: e.target.value }))}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-orange-500 transition-colors"
                >
                  <option value="CONTA_BANCARIA">Conta Bancária</option>
                  <option value="DINHEIRO">Dinheiro</option>
                  <option value="POUPANCA">Poupança</option>
                </select>
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Saldo Inicial (R$)</label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  value={carteira.saldo || ''}
                  onChange={(e) => setCarteira((c) => ({ ...c, saldo: parseFloat(e.target.value) || 0 }))}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-orange-500 transition-colors"
                  placeholder="0,00"
                />
              </div>
              {carteira.tipo === 'CONTA_BANCARIA' && (
                <div>
                  <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Banco (opcional)</label>
                  <input
                    type="text"
                    value={carteira.banco}
                    onChange={(e) => setCarteira((c) => ({ ...c, banco: e.target.value }))}
                    className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-orange-500 transition-colors"
                    placeholder="Ex: Nubank, Itaú"
                  />
                </div>
              )}
            </div>
          )}

          {/* Step 1: Conta/Cartão */}
          {passo === 1 && (
            <div className="space-y-4">
              <div>
                <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Nome</label>
                <input
                  type="text"
                  value={conta.nome}
                  onChange={(e) => setConta((c) => ({ ...c, nome: e.target.value }))}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-orange-500 transition-colors"
                  placeholder="Ex: Cartão Nubank"
                />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Tipo</label>
                <select
                  value={conta.tipo}
                  onChange={(e) => setConta((c) => ({ ...c, tipo: e.target.value }))}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-orange-500 transition-colors"
                >
                  <option value="CREDITO">Cartão de Crédito</option>
                  <option value="DEBITO">Cartão de Débito</option>
                  <option value="DINHEIRO">Dinheiro</option>
                  <option value="POUPANCA">Poupança</option>
                </select>
              </div>
              {conta.tipo === 'CREDITO' && (
                <div>
                  <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Limite (R$)</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    value={conta.limiteTotal || ''}
                    onChange={(e) => setConta((c) => ({ ...c, limiteTotal: parseFloat(e.target.value) || 0 }))}
                    className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-orange-500 transition-colors"
                    placeholder="0,00"
                  />
                </div>
              )}
            </div>
          )}

          {/* Step 2: Categorias */}
          {passo === 2 && (
            <div>
              <p className="text-sm text-slate-400 mb-4">Selecione as categorias que deseja criar:</p>
              <div className="grid grid-cols-2 gap-2 max-h-80 overflow-y-auto pr-1">
                {CATEGORIAS_SUGERIDAS.map((cat) => {
                  const selecionada = categoriasSelecionadas.includes(cat.nome);
                  return (
                    <button
                      key={cat.nome}
                      type="button"
                      onClick={() => toggleCategoria(cat.nome)}
                      className={`flex items-center gap-3 p-3 rounded-lg border transition-all text-left ${
                        selecionada
                          ? 'border-orange-500 bg-orange-500/10 text-white'
                          : 'border-slate-700 bg-slate-900/50 text-slate-400 hover:border-slate-600'
                      }`}
                    >
                      <span className="text-lg">{cat.icone}</span>
                      <span className="text-sm font-medium flex-1">{cat.nome}</span>
                      {selecionada && <Check className="w-4 h-4 text-orange-500 flex-shrink-0" />}
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* Step 3: Renda Mensal */}
          {passo === 3 && (
            <div className="space-y-4">
              <p className="text-sm text-slate-400">Configure sua renda principal para projeções financeiras:</p>
              <div>
                <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Nome</label>
                <input
                  type="text"
                  value={renda.nome}
                  onChange={(e) => setRenda((r) => ({ ...r, nome: e.target.value }))}
                  disabled={pularRenda}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-orange-500 transition-colors disabled:opacity-40"
                  placeholder="Ex: Salário"
                />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Valor Mensal (R$)</label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  value={renda.valor || ''}
                  onChange={(e) => setRenda((r) => ({ ...r, valor: parseFloat(e.target.value) || 0 }))}
                  disabled={pularRenda}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-orange-500 transition-colors disabled:opacity-40"
                  placeholder="0,00"
                />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Dia do Recebimento</label>
                <select
                  value={renda.diaVencimento}
                  onChange={(e) => setRenda((r) => ({ ...r, diaVencimento: parseInt(e.target.value) }))}
                  disabled={pularRenda}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-orange-500 transition-colors disabled:opacity-40"
                >
                  {Array.from({ length: 31 }, (_, i) => i + 1).map((dia) => (
                    <option key={dia} value={dia}>Dia {dia}</option>
                  ))}
                </select>
              </div>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={pularRenda}
                  onChange={(e) => setPularRenda(e.target.checked)}
                  className="w-4 h-4 rounded border-slate-600 bg-slate-800 text-orange-500 focus:ring-orange-500"
                />
                <span className="text-sm text-slate-400">Pular — configuro depois</span>
              </label>
            </div>
          )}

          {/* Step 4: Meta Financeira */}
          {passo === 4 && (
            <div className="space-y-4">
              <p className="text-sm text-slate-400">Defina uma meta financeira para começar:</p>
              <div>
                <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Nome da Meta</label>
                <input
                  type="text"
                  value={meta.nome}
                  onChange={(e) => setMeta((m) => ({ ...m, nome: e.target.value }))}
                  disabled={pularMeta}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-orange-500 transition-colors disabled:opacity-40"
                  placeholder="Ex: Reserva de emergência"
                />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Valor Total (R$)</label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  value={meta.valorTotal || ''}
                  onChange={(e) => setMeta((m) => ({ ...m, valorTotal: parseFloat(e.target.value) || 0 }))}
                  disabled={pularMeta}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-orange-500 transition-colors disabled:opacity-40"
                  placeholder="0,00"
                />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Valor Mensal (R$)</label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  value={meta.valorMensal || ''}
                  onChange={(e) => setMeta((m) => ({ ...m, valorMensal: parseFloat(e.target.value) || 0 }))}
                  disabled={pularMeta}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-orange-500 transition-colors disabled:opacity-40"
                  placeholder="0,00"
                />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Data Prevista</label>
                <input
                  type="date"
                  value={meta.dataPrevista}
                  onChange={(e) => setMeta((m) => ({ ...m, dataPrevista: e.target.value }))}
                  disabled={pularMeta}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-orange-500 transition-colors disabled:opacity-40"
                />
              </div>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={pularMeta}
                  onChange={(e) => setPularMeta(e.target.checked)}
                  className="w-4 h-4 rounded border-slate-600 bg-slate-800 text-orange-500 focus:ring-orange-500"
                />
                <span className="text-sm text-slate-400">Pular — configuro depois</span>
              </label>
            </div>
          )}

          {/* Step 5: Resumo e Confirmar */}
          {passo === 5 && (
            <div className="space-y-4">
              <p className="text-sm text-slate-400 text-center mb-4">Revise suas configurações antes de finalizar:</p>
              <div className="space-y-3">
                {resumo.carteiraCriada && (
                  <div className="flex items-center gap-3 p-3 bg-slate-900/50 rounded-lg border border-slate-700">
                    <Wallet className="w-5 h-5 text-teal-500" />
                    <div>
                      <p className="text-xs text-slate-500 uppercase">Carteira</p>
                      <p className="text-sm text-white">{resumo.carteiraCriada}</p>
                    </div>
                  </div>
                )}
                {resumo.contaCriada && (
                  <div className="flex items-center gap-3 p-3 bg-slate-900/50 rounded-lg border border-slate-700">
                    <CreditCard className="w-5 h-5 text-blue-500" />
                    <div>
                      <p className="text-xs text-slate-500 uppercase">Conta/Cartão</p>
                      <p className="text-sm text-white">{resumo.contaCriada}</p>
                    </div>
                  </div>
                )}
                {resumo.categoriasCriadas && (
                  <div className="flex items-center gap-3 p-3 bg-slate-900/50 rounded-lg border border-slate-700">
                    <Tag className="w-5 h-5 text-orange-500" />
                    <div>
                      <p className="text-xs text-slate-500 uppercase">Categorias ({resumo.categoriasCriadas.length})</p>
                      <p className="text-sm text-white">{resumo.categoriasCriadas.join(', ')}</p>
                    </div>
                  </div>
                )}
                {resumo.rendaCriada && (
                  <div className="flex items-center gap-3 p-3 bg-slate-900/50 rounded-lg border border-slate-700">
                    <TrendingUp className="w-5 h-5 text-green-500" />
                    <div>
                      <p className="text-xs text-slate-500 uppercase">Renda</p>
                      <p className="text-sm text-white">{resumo.rendaCriada}</p>
                    </div>
                  </div>
                )}
                {resumo.metaCriada && (
                  <div className="flex items-center gap-3 p-3 bg-slate-900/50 rounded-lg border border-slate-700">
                    <Target className="w-5 h-5 text-pink-500" />
                    <div>
                      <p className="text-xs text-slate-500 uppercase">Meta</p>
                      <p className="text-sm text-white">{resumo.metaCriada}</p>
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Navigation Buttons */}
          <div className="flex justify-between mt-8">
            {passo > 0 ? (
              <button
                type="button"
                onClick={() => setPasso(passo - 1)}
                className="flex items-center gap-2 px-4 py-2.5 text-sm text-slate-400 hover:text-white transition-colors"
              >
                <ChevronLeft className="w-4 h-4" />
                Voltar
              </button>
            ) : (
              <div />
            )}

            {passo < TOTAL_PASSOS - 1 ? (
              <button
                type="button"
                onClick={handleAvancar}
                disabled={!podeAvancar() || loading}
                className="flex items-center gap-2 px-6 py-2.5 bg-gradient-to-r from-orange-500 to-orange-600 text-white rounded-lg font-medium hover:from-orange-600 hover:to-orange-700 transition-all shadow-lg shadow-orange-500/20 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {loading ? (
                  <>
                    <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    Salvando...
                  </>
                ) : (
                  <>
                    Continuar
                    <ChevronRight className="w-4 h-4" />
                  </>
                )}
              </button>
            ) : (
              <button
                type="button"
                onClick={handleFinalizar}
                disabled={loading}
                className="flex items-center gap-2 px-6 py-2.5 bg-gradient-to-r from-green-500 to-green-600 text-white rounded-lg font-medium hover:from-green-600 hover:to-green-700 transition-all shadow-lg shadow-green-500/20 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {loading ? (
                  <>
                    <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    Finalizando...
                  </>
                ) : (
                  <>
                    <Check className="w-4 h-4" />
                    Começar a Usar
                  </>
                )}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
