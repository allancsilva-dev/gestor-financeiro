import { useEffect, useMemo, useState } from 'react';
import { toast } from 'react-hot-toast';
import { ChevronDown, ChevronUp, Edit2, Landmark, Plus, RefreshCw, Trash2 } from 'lucide-react';
import Layout from '../components/Layout';
import CurrencyInput from '../components/CurrencyInput';
import FieldError from '../components/FieldError';
import contaFinanceiraService, { contaGerenciada, ContaFinanceira, MovimentoContaFinanceira } from '../services/contaFinanceiraService';
import { formatCurrency } from '../utils/currency';
import { fieldA11y } from '../validation/fieldA11y';
import { useZodForm } from '../hooks/useZodForm';
import { carteiraSchema, movimentacaoCarteiraSchema } from '../validation/schemas';
import { toNullableNumber } from '../validation/numbers';

const tipos = [
  ['DINHEIRO', 'Dinheiro'], ['CONTA_BANCARIA', 'Conta bancária'], ['POUPANCA', 'Poupança'],
] as const;

const label = (value?: string) => value ? value.replaceAll('_', ' ').toLocaleLowerCase('pt-BR').replace(/^./, c => c.toUpperCase()) : '—';

export default function ContasFinanceiras() {
  const [contas, setContas] = useState<ContaFinanceira[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [formAberto, setFormAberto] = useState(false);
  const [editando, setEditando] = useState<ContaFinanceira | null>(null);
  const [form, setForm] = useState({ nome: '', tipo: 'DINHEIRO' as 'DINHEIRO' | 'CONTA_BANCARIA' | 'POUPANCA', saldo: '', banco: '' });
  const [detalheId, setDetalheId] = useState<number | null>(null);
  const [movimentos, setMovimentos] = useState<MovimentoContaFinanceira[]>([]);
  const [movimentosLoading, setMovimentosLoading] = useState(false);
  const [ajuste, setAjuste] = useState({ tipo: 'ENTRADA' as 'ENTRADA' | 'SAIDA', valor: '', descricao: '' });
  const contaValidation = useZodForm(carteiraSchema);
  const ajusteValidation = useZodForm(movimentacaoCarteiraSchema);

  const carregar = async () => {
    setLoading(true); setError(false);
    try { setContas(await contaFinanceiraService.listarTodas()); }
    catch { setError(true); }
    finally { setLoading(false); }
  };

  useEffect(() => { carregar(); }, []);

  const grupos = useMemo(() => ({
    ATIVO: contas.filter(c => c.natureza === 'ATIVO'),
    PASSIVO: contas.filter(c => c.natureza === 'PASSIVO'),
  }), [contas]);

  const abrirForm = (conta?: ContaFinanceira) => {
    if (conta) {
      setEditando(conta);
      setForm({ nome: conta.nome, tipo: conta.tipo === 'CARTAO' ? 'CONTA_BANCARIA' : conta.tipo, saldo: String(conta.saldo), banco: conta.banco ?? '' });
    } else {
      setEditando(null); setForm({ nome: '', tipo: 'DINHEIRO', saldo: '', banco: '' });
    }
    contaValidation.resetValidation(); setFormAberto(true);
  };

  const salvar = async (event: React.FormEvent) => {
    event.preventDefault();
    const parsed = contaValidation.validate(form);
    if (!parsed) return;
    const input = { nome: parsed.nome, tipo: parsed.tipo, saldo: parsed.saldo, banco: parsed.tipo === 'CONTA_BANCARIA' ? parsed.banco : undefined };
    try {
      if (editando) await contaFinanceiraService.atualizar(editando.id, input);
      else await contaFinanceiraService.criar(input);
      toast.success(editando ? 'Conta atualizada' : 'Conta criada');
      setFormAberto(false); await carregar();
    } catch { toast.error('Não foi possível salvar a conta'); }
  };

  const excluir = async (conta: ContaFinanceira) => {
    if (!confirm(`Excluir a conta “${conta.nome}”?`)) return;
    try { await contaFinanceiraService.deletar(conta.id); toast.success('Conta excluída'); await carregar(); }
    catch { toast.error('Esta conta não pode ser excluída'); }
  };

  const abrirDetalhe = async (conta: ContaFinanceira) => {
    if (detalheId === conta.id) { setDetalheId(null); return; }
    setDetalheId(conta.id); setMovimentosLoading(true); setMovimentos([]);
    try { setMovimentos((await contaFinanceiraService.listarMovimentos(conta.id)).content ?? []); }
    catch { toast.error('Não foi possível carregar o extrato'); }
    finally { setMovimentosLoading(false); }
  };

  const ajustar = async (event: React.FormEvent, conta: ContaFinanceira) => {
    event.preventDefault();
    const parsed = ajusteValidation.validate({ valor: ajuste.valor });
    if (!parsed) return;
    try {
      await contaFinanceiraService.ajustar(conta.id, { tipo: ajuste.tipo, valor: parsed.valor, descricao: ajuste.descricao || undefined });
      toast.success('Ajuste registrado'); setAjuste({ tipo: 'ENTRADA', valor: '', descricao: '' });
      const [, extrato] = await Promise.all([carregar(), contaFinanceiraService.listarMovimentos(conta.id)]);
      setMovimentos(extrato.content ?? []); setDetalheId(conta.id);
    } catch { toast.error('Não foi possível registrar o ajuste'); }
  };

  if (loading) return <Layout><div aria-label="Carregando contas" className="space-y-4"><div className="h-9 w-64 animate-pulse rounded bg-violet-200" /><div className="h-28 animate-pulse rounded-xl bg-white" /><div className="h-28 animate-pulse rounded-xl bg-white" /></div></Layout>;

  if (error) return <Layout><div className="mx-auto max-w-lg rounded-xl bg-white p-8 text-center"><h1 className="text-xl font-bold">Não foi possível carregar as contas</h1><p className="mt-2 text-slate-600">Confira sua conexão e tente novamente.</p><button onClick={carregar} className="mt-5 min-h-11 rounded-lg bg-violet-600 px-4 font-semibold text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet-700 focus-visible:ring-offset-2">Tentar novamente</button></div></Layout>;

  return <Layout><div className="mx-auto max-w-6xl space-y-6">
    <header className="flex flex-wrap items-start justify-between gap-4"><div><h1 className="text-3xl font-bold tracking-tight">Contas financeiras</h1><p className="mt-1 text-slate-600">Caixa, reservas, custódia e obrigações em uma visão única.</p></div><button onClick={() => abrirForm()} className="flex min-h-11 items-center gap-2 rounded-lg bg-violet-600 px-4 font-semibold text-white hover:bg-violet-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet-700 focus-visible:ring-offset-2"><Plus className="h-5 w-5" /> Nova conta</button></header>

    {formAberto && <section aria-labelledby="conta-form-title" className="rounded-xl bg-white p-5 shadow-sm"><h2 id="conta-form-title" className="text-lg font-bold">{editando ? 'Editar conta' : 'Nova conta'}</h2><form onSubmit={salvar} className="mt-4 grid gap-4 sm:grid-cols-2">
      <label className="text-sm font-medium">Nome<input {...fieldA11y('nome', contaValidation.errors.nome)} value={form.nome} onChange={e => setForm({...form, nome:e.target.value})} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 px-3 focus:border-violet-600 focus:outline-none focus:ring-2 focus:ring-violet-200" /><FieldError name="nome" error={contaValidation.errors.nome} /></label>
      <label className="text-sm font-medium">Tipo<select value={form.tipo} onChange={e => setForm({...form, tipo:e.target.value as typeof form.tipo})} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 px-3 focus:border-violet-600 focus:outline-none focus:ring-2 focus:ring-violet-200">{tipos.map(([v,l]) => <option key={v} value={v}>{l}</option>)}</select></label>
      <label className="text-sm font-medium">Saldo inicial<CurrencyInput {...fieldA11y('saldo', contaValidation.errors.saldo)} value={toNullableNumber(form.saldo)} onValueChange={v => setForm({...form, saldo:v == null ? '' : String(v)})} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 px-3 focus:border-violet-600 focus:outline-none focus:ring-2 focus:ring-violet-200" /><FieldError name="saldo" error={contaValidation.errors.saldo} /></label>
      {form.tipo === 'CONTA_BANCARIA' && <label className="text-sm font-medium">Banco<input value={form.banco} onChange={e => setForm({...form, banco:e.target.value})} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 px-3 focus:border-violet-600 focus:outline-none focus:ring-2 focus:ring-violet-200" /></label>}
      <div className="flex gap-2 sm:col-span-2 sm:justify-end"><button type="button" onClick={() => setFormAberto(false)} className="min-h-11 rounded-lg px-4 font-semibold text-slate-700 hover:bg-slate-100">Cancelar</button><button className="min-h-11 rounded-lg bg-violet-600 px-4 font-semibold text-white hover:bg-violet-700">Salvar</button></div>
    </form></section>}

    {contas.length === 0 ? <section className="rounded-xl bg-white px-5 py-12 text-center"><Landmark className="mx-auto h-10 w-10 text-violet-600" /><h2 className="mt-3 text-lg font-bold">Nenhuma conta financeira</h2><p className="mt-1 text-slate-600">Cadastre onde seu dinheiro fica para começar.</p><button onClick={() => abrirForm()} className="mt-5 min-h-11 rounded-lg bg-violet-600 px-4 font-semibold text-white">Criar primeira conta</button></section> : (['ATIVO','PASSIVO'] as const).map(natureza => grupos[natureza].length > 0 && <section key={natureza} aria-labelledby={`grupo-${natureza}`}><div className="mb-3 flex items-baseline justify-between"><h2 id={`grupo-${natureza}`} className="text-xl font-bold">{natureza === 'ATIVO' ? 'Ativos' : 'Passivos'}</h2><span className="text-sm text-slate-600">{grupos[natureza].length} conta(s)</span></div><div className="space-y-3">{grupos[natureza].map(conta => {
      const gerenciada = contaGerenciada(conta); const aberto = detalheId === conta.id;
      return <article key={conta.id} className="overflow-hidden rounded-xl bg-white shadow-sm"><div className="flex flex-wrap items-center gap-4 p-4 sm:p-5"><div className="flex h-11 w-11 items-center justify-center rounded-xl bg-violet-100 text-violet-700"><Landmark className="h-5 w-5" /></div><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><h3 className="font-bold">{conta.nome}</h3><span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${conta.estadoConciliacao === 'CONCILIADA' ? 'bg-green-100 text-green-800' : 'bg-amber-100 text-amber-800'}`}>{label(conta.estadoConciliacao)}</span>{gerenciada && <span className="rounded-full bg-violet-100 px-2 py-0.5 text-xs font-semibold text-violet-800">Somente leitura</span>}</div><p className="mt-1 text-sm text-slate-600">{label(conta.subtipo)} · Liquidez {label(conta.liquidez)} · {conta.moeda}</p></div><p className={`text-xl font-bold tabular-nums ${conta.natureza === 'PASSIVO' || conta.saldo < 0 ? 'text-red-700' : 'text-green-700'}`}>{formatCurrency(conta.saldo)}</p><div className="flex items-center gap-1">{!gerenciada && <><button onClick={() => abrirForm(conta)} aria-label={`Editar ${conta.nome}`} className="rounded-lg p-2 text-violet-700 hover:bg-violet-50 focus-visible:ring-2 focus-visible:ring-violet-600"><Edit2 className="h-4 w-4" /></button><button onClick={() => excluir(conta)} aria-label={`Excluir ${conta.nome}`} className="rounded-lg p-2 text-red-700 hover:bg-red-50 focus-visible:ring-2 focus-visible:ring-violet-600"><Trash2 className="h-4 w-4" /></button></>}<button onClick={() => abrirDetalhe(conta)} aria-expanded={aberto} aria-controls={`detalhe-${conta.id}`} className="flex min-h-11 items-center gap-1 rounded-lg px-3 text-sm font-semibold text-violet-700 hover:bg-violet-50 focus-visible:ring-2 focus-visible:ring-violet-600">Extrato {aberto ? <ChevronUp className="h-4 w-4"/> : <ChevronDown className="h-4 w-4"/>}</button></div></div>
      {aberto && <div id={`detalhe-${conta.id}`} className="border-t border-slate-100 bg-slate-50 p-4 sm:p-5">{!gerenciada && <form onSubmit={e => ajustar(e, conta)} className="mb-5 flex flex-wrap items-end gap-3"><label className="text-sm font-medium">Ajuste<select value={ajuste.tipo} onChange={e => setAjuste({...ajuste,tipo:e.target.value as typeof ajuste.tipo})} className="mt-1 block min-h-11 rounded-lg border border-slate-300 px-3"><option value="ENTRADA">Entrada</option><option value="SAIDA">Saída</option></select></label><label className="text-sm font-medium">Valor<CurrencyInput value={toNullableNumber(ajuste.valor)} onValueChange={v => setAjuste({...ajuste,valor:v == null ? '' : String(v)})} className="mt-1 block min-h-11 w-36 rounded-lg border border-slate-300 px-3" /></label><label className="min-w-52 flex-1 text-sm font-medium">Descrição<input value={ajuste.descricao} onChange={e => setAjuste({...ajuste,descricao:e.target.value})} className="mt-1 block min-h-11 w-full rounded-lg border border-slate-300 px-3" /></label><button className="min-h-11 rounded-lg bg-violet-600 px-4 font-semibold text-white">Registrar ajuste</button></form>}{movimentosLoading ? <p className="text-sm text-slate-600">Carregando extrato…</p> : movimentos.length === 0 ? <p className="text-sm text-slate-600">Ainda não há movimentos nesta conta.</p> : <div className="overflow-x-auto"><table className="w-full min-w-[620px] text-sm"><thead><tr className="text-left text-slate-600"><th className="pb-2">Data</th><th>Descrição</th><th>Origem</th><th className="text-right">Valor</th><th className="text-right">Saldo</th></tr></thead><tbody>{movimentos.map(m => <tr key={m.id} className="border-t border-slate-200"><td className="py-3">{new Date(m.dataMovimento).toLocaleDateString('pt-BR')}</td><td>{m.descricao}</td><td>{label(m.origem)}</td><td className={`text-right font-semibold ${m.valorAssinado >= 0 ? 'text-green-700' : 'text-red-700'}`}>{formatCurrency(m.valorAssinado)}</td><td className="text-right font-semibold">{formatCurrency(m.saldoResultante)}</td></tr>)}</tbody></table></div>}</div>}
      </article>;
    })}</div></section>)}
    <button onClick={carregar} className="flex min-h-11 items-center gap-2 rounded-lg px-3 text-sm font-semibold text-violet-700 hover:bg-violet-100 focus-visible:ring-2 focus-visible:ring-violet-600"><RefreshCw className="h-4 w-4" /> Atualizar contas</button>
  </div></Layout>;
}
