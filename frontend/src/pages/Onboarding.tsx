import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { onboardingService } from '../services/onboardingService';
import toast from 'react-hot-toast';
import { Check, ChevronLeft, ChevronRight, Wallet, CreditCard, Tag, TrendingUp, Target, List } from 'lucide-react';
import FieldError from '../components/FieldError';
import { fieldA11y } from '../validation/fieldA11y';
import { useZodForm } from '../hooks/useZodForm';
import { carteiraSchema, onboardingCategoriasSchema, onboardingContaSchema, onboardingMetaSchema, onboardingRendaSchema } from '../validation/schemas';

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

  const [carteira, setCarteira] = useState({ nome: 'Carteira Principal', tipo: 'CONTA_BANCARIA', saldo: '', banco: '' });
  const [conta, setConta] = useState({ nome: 'Cartão Principal', tipo: 'CREDITO', limiteTotal: '' });
  const [categoriasSelecionadas, setCategoriasSelecionadas] = useState<string[]>(
    CATEGORIAS_SUGERIDAS.map((c) => c.nome)
  );
  const [renda, setRenda] = useState({ nome: 'Salário', valor: '', diaVencimento: '1' });
  const [pularRenda, setPularRenda] = useState(false);
  const [meta, setMeta] = useState({ nome: '', valorTotal: '', valorMensal: '', dataPrevista: '' });
  const [pularMeta, setPularMeta] = useState(false);

  const [resumo, setResumo] = useState<{
    carteiraCriada?: string;
    contaCriada?: string;
    categoriasCriadas?: string[];
    rendaCriada?: string;
    metaCriada?: string;
  }>({});
  const carteiraValidation = useZodForm(carteiraSchema);
  const contaValidation = useZodForm(onboardingContaSchema);
  const categoriasValidation = useZodForm(onboardingCategoriasSchema);
  const rendaValidation = useZodForm(onboardingRendaSchema);
  const metaValidation = useZodForm(onboardingMetaSchema);

  const TOTAL_PASSOS = PASSOS.length;

  // Passos só validam e guardam estado local; a persistência acontece de uma vez
  // no /finalizar transacional do backend (ADR-0002) — falha no meio não deixa dados parciais.
  const handleValidarCarteira = () => {
    const parsed = carteiraValidation.validate(carteira);
    if (!parsed) return;
    setResumo((r) => ({ ...r, carteiraCriada: `${carteira.nome} (${carteira.tipo})` }));
    setPasso(1);
  };

  const handleValidarConta = () => {
    const parsed = contaValidation.validate(conta);
    if (!parsed) return;
    setResumo((r) => ({ ...r, contaCriada: `${conta.nome} (${conta.tipo})` }));
    setPasso(2);
  };

  const handleValidarCategorias = () => {
    if (!categoriasValidation.validate(categoriasSelecionadas)) return;
    setResumo((r) => ({ ...r, categoriasCriadas: [...categoriasSelecionadas] }));
    setPasso(3);
  };

  const handleValidarRenda = () => {
    if (pularRenda) {
      setResumo((r) => ({ ...r, rendaCriada: 'Pulado' }));
      setPasso(4);
      return;
    }
    const parsed = rendaValidation.validate(renda);
    if (!parsed) return;
    setResumo((r) => ({ ...r, rendaCriada: `${parsed.nome} R$ ${parsed.valor.toFixed(2)}` }));
    setPasso(4);
  };

  const handleValidarMeta = () => {
    if (pularMeta) {
      setResumo((r) => ({ ...r, metaCriada: 'Pulado' }));
      setPasso(5);
      return;
    }
    const parsed = metaValidation.validate(meta);
    if (!parsed) return;
    setResumo((r) => ({ ...r, metaCriada: `${meta.nome}` }));
    setPasso(5);
  };

  const handleFinalizar = async () => {
    const carteiraParsed = carteiraValidation.validate(carteira);
    const contaParsed = contaValidation.validate(conta);
    if (!carteiraParsed || !contaParsed || categoriasSelecionadas.length === 0) {
      toast.error('Revise os passos anteriores antes de finalizar');
      return;
    }
    const rendaParsed = pularRenda ? null : rendaValidation.validate(renda);
    if (!pularRenda && !rendaParsed) {
      toast.error('Revise o passo de renda antes de finalizar');
      return;
    }
    const metaParsed = pularMeta ? null : metaValidation.validate(meta);
    if (!pularMeta && !metaParsed) {
      toast.error('Revise o passo de meta antes de finalizar');
      return;
    }

    setLoading(true);
    try {
      await onboardingService.finalizar({
        carteira: {
          nome: carteiraParsed.nome,
          tipo: carteiraParsed.tipo,
          saldo: carteiraParsed.saldo ?? 0,
          banco: carteiraParsed.banco || undefined,
        },
        conta: {
          nome: contaParsed.nome,
          tipo: contaParsed.tipo,
          limiteTotal: contaParsed.tipo === 'CREDITO' ? contaParsed.limiteTotal : undefined,
        },
        categorias: categoriasSelecionadas.map((nome) => {
          const sugerida = CATEGORIAS_SUGERIDAS.find((c) => c.nome === nome);
          return {
            nome,
            cor: sugerida?.cor || '#6B7280',
            icone: sugerida?.icone || '📌',
            valorEsperado: 0,
          };
        }),
        renda: rendaParsed
          ? { nome: rendaParsed.nome, valor: rendaParsed.valor, diaVencimento: rendaParsed.diaVencimento }
          : undefined,
        meta: metaParsed
          ? {
              nome: metaParsed.nome,
              valorTotal: metaParsed.valorTotal,
              valorMensal: metaParsed.valorMensal,
              dataLimite: metaParsed.dataPrevista || undefined,
            }
          : undefined,
      });
      await refreshUser();
      toast.success('Configuração concluída! Bem-vindo(a) ao Gestor Financeiro!');
      navigate('/dashboard');
    } catch (err: any) {
      // transação única no backend: nada foi persistido, o reenvio é seguro
      toast.error(err.response?.data?.message || 'Erro ao finalizar. Nada foi salvo — tente novamente.');
    } finally {
      setLoading(false);
    }
  };

  const handleAvancar = () => {
    if (passo === 0) handleValidarCarteira();
    else if (passo === 1) handleValidarConta();
    else if (passo === 2) handleValidarCategorias();
    else if (passo === 3) handleValidarRenda();
    else if (passo === 4) handleValidarMeta();
  };

  const toggleCategoria = (nome: string) => {
    setCategoriasSelecionadas((prev) => {
      const next = prev.includes(nome) ? prev.filter((c) => c !== nome) : [...prev, nome];
      categoriasValidation.revalidateField('form', next);
      return next;
    });
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
                  {...fieldA11y('nome', carteiraValidation.errors.nome)}
                  type="text"
                  value={carteira.nome}
                  onChange={(e) => { const next = { ...carteira, nome: e.target.value }; setCarteira(next); carteiraValidation.revalidateField('nome', next); }}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-orange-500 transition-colors aria-invalid:border-red-500"
                  placeholder="Ex: Conta Principal"
                />
                <FieldError name="nome" error={carteiraValidation.errors.nome} />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Tipo</label>
                <select
                  {...fieldA11y('tipo', carteiraValidation.errors.tipo)}
                  value={carteira.tipo}
                  onChange={(e) => { const next = { ...carteira, tipo: e.target.value }; setCarteira(next); carteiraValidation.revalidateField('tipo', next); }}
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
                  {...fieldA11y('saldo', carteiraValidation.errors.saldo)}
                  type="number"
                  step="0.01"
                  min="0"
                  value={carteira.saldo}
                  onChange={(e) => { const next = { ...carteira, saldo: e.target.value }; setCarteira(next); carteiraValidation.revalidateField('saldo', next); }}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-orange-500 transition-colors aria-invalid:border-red-500"
                  placeholder="0,00"
                />
                <FieldError name="saldo" error={carteiraValidation.errors.saldo} />
              </div>
              {carteira.tipo === 'CONTA_BANCARIA' && (
                <div>
                  <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Banco</label>
                  <input
                    {...fieldA11y('banco', carteiraValidation.errors.banco)}
                    type="text"
                    value={carteira.banco}
                    onChange={(e) => { const next = { ...carteira, banco: e.target.value }; setCarteira(next); carteiraValidation.revalidateField('banco', next); }}
                    className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-orange-500 transition-colors aria-invalid:border-red-500"
                    placeholder="Ex: Nubank, Itaú"
                  />
                  <FieldError name="banco" error={carteiraValidation.errors.banco} />
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
                  {...fieldA11y('nome', contaValidation.errors.nome)}
                  type="text"
                  value={conta.nome}
                  onChange={(e) => { const next = { ...conta, nome: e.target.value }; setConta(next); contaValidation.revalidateField('nome', next); }}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-orange-500 transition-colors"
                  placeholder="Ex: Cartão Nubank"
                />
                <FieldError name="nome" error={contaValidation.errors.nome} />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Tipo</label>
                <select
                  {...fieldA11y('tipo', contaValidation.errors.tipo)}
                  value={conta.tipo}
                  onChange={(e) => { const next = { ...conta, tipo: e.target.value }; setConta(next); contaValidation.revalidateField('tipo', next); }}
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
                    {...fieldA11y('limiteTotal', contaValidation.errors.limiteTotal)}
                    type="number"
                    step="0.01"
                    min="0"
                    value={conta.limiteTotal}
                    onChange={(e) => { const next = { ...conta, limiteTotal: e.target.value }; setConta(next); contaValidation.revalidateField('limiteTotal', next); }}
                    className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-orange-500 transition-colors aria-invalid:border-red-500"
                    placeholder="0,00"
                  />
                  <FieldError name="limiteTotal" error={contaValidation.errors.limiteTotal} />
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
              <FieldError name="form" error={categoriasValidation.errors.form} />
            </div>
          )}

          {/* Step 3: Renda Mensal */}
          {passo === 3 && (
            <div className="space-y-4">
              <p className="text-sm text-slate-400">Configure sua renda principal para projeções financeiras:</p>
              <div>
                <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Nome</label>
                <input
                  {...fieldA11y('nome', rendaValidation.errors.nome)}
                  type="text"
                  value={renda.nome}
                  onChange={(e) => { const next = { ...renda, nome: e.target.value }; setRenda(next); rendaValidation.revalidateField('nome', next); }}
                  disabled={pularRenda}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-orange-500 transition-colors disabled:opacity-40"
                  placeholder="Ex: Salário"
                />
                <FieldError name="nome" error={rendaValidation.errors.nome} />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Valor Mensal (R$)</label>
                <input
                  {...fieldA11y('valor', rendaValidation.errors.valor)}
                  type="number"
                  step="0.01"
                  min="0"
                  value={renda.valor}
                  onChange={(e) => { const next = { ...renda, valor: e.target.value }; setRenda(next); rendaValidation.revalidateField('valor', next); }}
                  disabled={pularRenda}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-orange-500 transition-colors disabled:opacity-40 aria-invalid:border-red-500"
                  placeholder="0,00"
                />
                <FieldError name="valor" error={rendaValidation.errors.valor} />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Dia do Recebimento</label>
                <select
                  {...fieldA11y('diaVencimento', rendaValidation.errors.diaVencimento)}
                  value={renda.diaVencimento}
                  onChange={(e) => { const next = { ...renda, diaVencimento: e.target.value }; setRenda(next); rendaValidation.revalidateField('diaVencimento', next); }}
                  disabled={pularRenda}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-orange-500 transition-colors disabled:opacity-40"
                >
                  {Array.from({ length: 31 }, (_, i) => i + 1).map((dia) => (
                    <option key={dia} value={dia}>Dia {dia}</option>
                  ))}
                </select>
                <FieldError name="diaVencimento" error={rendaValidation.errors.diaVencimento} />
              </div>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  {...fieldA11y('nome', metaValidation.errors.nome)}
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
                  onChange={(e) => { const next = { ...meta, nome: e.target.value }; setMeta(next); metaValidation.revalidateField('nome', next); }}
                  disabled={pularMeta}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-orange-500 transition-colors disabled:opacity-40"
                  placeholder="Ex: Reserva de emergência"
                />
                <FieldError name="nome" error={metaValidation.errors.nome} />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Valor Total (R$)</label>
                <input
                  {...fieldA11y('valorTotal', metaValidation.errors.valorTotal)}
                  type="number"
                  step="0.01"
                  min="0"
                  value={meta.valorTotal}
                  onChange={(e) => { const next = { ...meta, valorTotal: e.target.value }; setMeta(next); metaValidation.revalidateField('valorTotal', next); }}
                  disabled={pularMeta}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-orange-500 transition-colors disabled:opacity-40"
                  placeholder="0,00"
                />
                <FieldError name="valorTotal" error={metaValidation.errors.valorTotal} />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Valor Mensal (R$)</label>
                <input
                  {...fieldA11y('valorMensal', metaValidation.errors.valorMensal)}
                  type="number"
                  step="0.01"
                  min="0"
                  value={meta.valorMensal}
                  onChange={(e) => { const next = { ...meta, valorMensal: e.target.value }; setMeta(next); metaValidation.revalidateField('valorMensal', next); }}
                  disabled={pularMeta}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-orange-500 transition-colors disabled:opacity-40"
                  placeholder="0,00"
                />
                <FieldError name="valorMensal" error={metaValidation.errors.valorMensal} />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1 uppercase tracking-wider">Data Prevista</label>
                <input
                  {...fieldA11y('dataPrevista', metaValidation.errors.dataPrevista)}
                  type="date"
                  value={meta.dataPrevista}
                  onChange={(e) => { const next = { ...meta, dataPrevista: e.target.value }; setMeta(next); metaValidation.revalidateField('dataPrevista', next); }}
                  disabled={pularMeta}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-orange-500 transition-colors disabled:opacity-40"
                />
                <FieldError name="dataPrevista" error={metaValidation.errors.dataPrevista} />
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
                disabled={loading}
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
