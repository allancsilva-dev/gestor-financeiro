import { useEffect, useMemo, useState } from 'react';
import { ChevronDown, ChevronUp, Plus, Trash2, TrendingUp } from 'lucide-react';
import toast from 'react-hot-toast';
import Layout from '../components/Layout';
import FieldError from '../components/FieldError';
import { investimentoService, Ativo, Movimentacao } from '../services/investimentoService';
import contaFinanceiraService, { contaPodeMovimentarCaixa, ContaFinanceira } from '../services/contaFinanceiraService';
import { formatCurrency } from '../utils/currency';
import { fieldA11y } from '../validation/fieldA11y';
import { useZodForm } from '../hooks/useZodForm';
import { ativoSchema, movimentacaoAtivoSchema } from '../validation/schemas';

const TIPOS_ATIVO = ['ACAO', 'FII', 'ETF', 'RENDA_FIXA', 'CRIPTO', 'OUTRO'];
const TIPOS_MOV = ['COMPRA', 'VENDA', 'DIVIDENDO', 'BONIFICACAO'];
const LIQUIDEZ = ['IMEDIATA', 'D1', 'D2', 'CARENCIA', 'BLOQUEADA'];
const hoje = () => new Date().toISOString().split('T')[0];
const label = (value: string) => value.replaceAll('_', ' ').toLocaleLowerCase('pt-BR').replace(/^./, c => c.toUpperCase());

export default function Investimentos() {
  const [ativos, setAtivos] = useState<Ativo[]>([]);
  const [contas, setContas] = useState<ContaFinanceira[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [mostrarForm, setMostrarForm] = useState(false);
  const [ativoSelecionado, setAtivoSelecionado] = useState<Ativo | null>(null);
  const [movimentacoes, setMovimentacoes] = useState<Movimentacao[]>([]);
  const [movLoading, setMovLoading] = useState(false);
  const [form, setForm] = useState({ ticker: '', nome: '', tipo: 'ACAO', valorAtual: '', liquidez: 'IMEDIATA', custodiaId: '' });
  const [movForm, setMovForm] = useState({ tipo: 'COMPRA', data: hoje(), quantidade: '', precoUnitario: '', externa: false, carteiraId: '' });
  const ativoValidation = useZodForm(ativoSchema);
  const movValidation = useZodForm(movimentacaoAtivoSchema);
  const custodias = useMemo(() => contas.filter(c => c.subtipo === 'CUSTODIA'), [contas]);
  const contasCaixa = useMemo(() => contas.filter(contaPodeMovimentarCaixa), [contas]);

  async function carregar() {
    setLoading(true); setError(false);
    try {
      const [ativosData, contasData] = await Promise.all([investimentoService.listar(), contaFinanceiraService.listarTodas()]);
      setAtivos(ativosData); setContas(contasData);
    } catch { setError(true); }
    finally { setLoading(false); }
  }

  useEffect(() => { carregar(); }, []);

  const carregarMovs = async (ativo: Ativo) => {
    if (ativoSelecionado?.id === ativo.id) { setAtivoSelecionado(null); return; }
    setAtivoSelecionado(ativo); setMovLoading(true); setMovimentacoes([]);
    setMovForm({ tipo: 'COMPRA', data: hoje(), quantidade: '', precoUnitario: '', externa: false, carteiraId: contasCaixa[0] ? String(contasCaixa[0].id) : '' });
    try { setMovimentacoes(await investimentoService.listarMovimentacoes(ativo.id!)); }
    catch { toast.error('Não foi possível carregar as movimentações'); }
    finally { setMovLoading(false); }
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    const data = ativoValidation.validate(form);
    if (!data) return;
    try {
      await investimentoService.criar({ ...data, liquidez: form.liquidez as Ativo['liquidez'], custodiaId: form.custodiaId ? Number(form.custodiaId) : null });
      toast.success('Ativo criado'); setMostrarForm(false);
      setForm({ ticker: '', nome: '', tipo: 'ACAO', valorAtual: '', liquidez: 'IMEDIATA', custodiaId: '' });
      ativoValidation.resetValidation(); await carregar();
    } catch { toast.error('Não foi possível criar o ativo'); }
  };

  const handleMov = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!ativoSelecionado) return;
    const data = movValidation.validate(movForm);
    if (!data) return;
    if (!movForm.externa && !movForm.carteiraId) { toast.error('Escolha a conta de caixa ou marque como externa'); return; }
    try {
      const criada = await investimentoService.adicionarMovimentacao(ativoSelecionado.id!, {
        ...data, externa: movForm.externa, carteiraId: movForm.externa ? undefined : Number(movForm.carteiraId),
      });
      toast.success(criada.conciliacao === 'CONCILIADA' ? 'Operação conciliada com o caixa' : 'Snapshot externo registrado');
      setMovimentacoes(await investimentoService.listarMovimentacoes(ativoSelecionado.id!));
      setMovForm(current => ({ ...current, quantidade: '', precoUnitario: '' }));
      movValidation.resetValidation();
      const refreshed = await investimentoService.listar(); setAtivos(refreshed);
    } catch { toast.error('Não foi possível registrar a movimentação'); }
  };

  const excluir = async (id: number) => {
    if (!confirm('Excluir este ativo?')) return;
    try { await investimentoService.deletar(id); toast.success('Ativo excluído'); await carregar(); }
    catch { toast.error('Não foi possível excluir o ativo'); }
  };

  if (loading) return <Layout><div aria-label="Carregando investimentos" className="mx-auto max-w-6xl space-y-4"><div className="h-10 w-60 animate-pulse rounded bg-violet-200"/><div className="h-36 animate-pulse rounded-xl bg-white"/><div className="h-36 animate-pulse rounded-xl bg-white"/></div></Layout>;
  if (error) return <Layout><div className="mx-auto max-w-lg rounded-xl bg-white p-8 text-center"><h1 className="text-xl font-bold">Não foi possível carregar os investimentos</h1><p className="mt-2 text-slate-600">Tente novamente para consultar posições e movimentações.</p><button onClick={carregar} className="mt-5 min-h-11 rounded-lg bg-violet-600 px-4 font-semibold text-white">Tentar novamente</button></div></Layout>;

  return <Layout><div className="mx-auto max-w-6xl space-y-6">
    <header className="flex flex-wrap items-start justify-between gap-4"><div><h1 className="text-3xl font-bold tracking-tight">Investimentos</h1><p className="mt-1 text-slate-600">Posições, custódia e vínculo de cada operação com o caixa.</p></div><button onClick={() => setMostrarForm(v => !v)} className="flex min-h-11 items-center gap-2 rounded-lg bg-violet-600 px-4 font-semibold text-white hover:bg-violet-700 focus-visible:ring-2 focus-visible:ring-violet-700 focus-visible:ring-offset-2"><Plus className="h-5 w-5" /> Novo ativo</button></header>

    {mostrarForm && <form onSubmit={handleSubmit} className="rounded-xl bg-white p-5 shadow-sm"><h2 className="text-lg font-bold">Cadastrar ativo</h2><div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <label className="text-sm font-medium">Ticker<input {...fieldA11y('ticker', ativoValidation.errors.ticker)} value={form.ticker} onChange={e => setForm({...form,ticker:e.target.value})} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 px-3 focus:border-violet-600 focus:outline-none focus:ring-2 focus:ring-violet-200" placeholder="PETR4"/><FieldError name="ticker" error={ativoValidation.errors.ticker}/></label>
      <label className="text-sm font-medium">Nome<input {...fieldA11y('nome', ativoValidation.errors.nome)} value={form.nome} onChange={e => setForm({...form,nome:e.target.value})} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 px-3"/><FieldError name="nome" error={ativoValidation.errors.nome}/></label>
      <label className="text-sm font-medium">Tipo<select value={form.tipo} onChange={e => setForm({...form,tipo:e.target.value})} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 px-3">{TIPOS_ATIVO.map(t => <option key={t}>{t}</option>)}</select></label>
      <label className="text-sm font-medium">Cotação atual<input {...fieldA11y('valorAtual', ativoValidation.errors.valorAtual)} inputMode="decimal" value={form.valorAtual} onChange={e => setForm({...form,valorAtual:e.target.value})} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 px-3" placeholder="0,00"/><FieldError name="valorAtual" error={ativoValidation.errors.valorAtual}/></label>
      <label className="text-sm font-medium">Liquidez<select value={form.liquidez} onChange={e => setForm({...form,liquidez:e.target.value})} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 px-3">{LIQUIDEZ.map(l => <option key={l} value={l}>{label(l)}</option>)}</select></label>
      <label className="text-sm font-medium">Conta de custódia<select value={form.custodiaId} onChange={e => setForm({...form,custodiaId:e.target.value})} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 px-3"><option value="">Sem custódia vinculada</option>{custodias.map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}</select></label>
    </div><div className="mt-5 flex justify-end gap-2"><button type="button" onClick={() => setMostrarForm(false)} className="min-h-11 rounded-lg px-4 font-semibold hover:bg-slate-100">Cancelar</button><button className="min-h-11 rounded-lg bg-violet-600 px-4 font-semibold text-white">Salvar ativo</button></div></form>}

    {ativos.length === 0 ? <section className="rounded-xl bg-white py-12 text-center"><TrendingUp className="mx-auto h-10 w-10 text-violet-600"/><h2 className="mt-3 text-lg font-bold">Nenhum ativo cadastrado</h2><p className="mt-1 text-slate-600">Cadastre sua primeira posição para acompanhar a carteira.</p></section> : <div className="space-y-3">{ativos.map(a => { const open = ativoSelecionado?.id === a.id; const custody = contas.find(c => c.id === a.custodiaId); return <article key={a.id} className="overflow-hidden rounded-xl bg-white shadow-sm"><div className="flex flex-wrap items-center gap-4 p-5"><div className="min-w-0 flex-1"><div className="flex flex-wrap items-baseline gap-2"><h2 className="text-lg font-bold">{a.ticker}</h2><span className="text-sm text-slate-600">{a.nome}</span><span className="rounded-full bg-violet-100 px-2 py-0.5 text-xs font-semibold text-violet-800">{label(a.liquidez)}</span></div><p className="mt-1 text-sm text-slate-600">{a.quantidade} un. · PM {formatCurrency(a.precoMedio)} · {custody ? `Custódia: ${custody.nome}` : 'Sem custódia vinculada'}</p></div><div className="text-right"><p className="text-xl font-bold tabular-nums">{formatCurrency(a.valorMercado ?? a.custoTotal)}</p><p className={`text-sm font-semibold ${a.lucroPrejuizo >= 0 ? 'text-green-700' : 'text-red-700'}`}>{a.rentabilidade >= 0 ? '+' : ''}{a.rentabilidade?.toFixed(2)}% · {formatCurrency(a.lucroPrejuizo ?? 0)}</p></div><button onClick={() => carregarMovs(a)} aria-expanded={open} className="flex min-h-11 items-center gap-1 rounded-lg px-3 text-sm font-semibold text-violet-700 hover:bg-violet-50">Operações {open ? <ChevronUp className="h-4 w-4"/> : <ChevronDown className="h-4 w-4"/>}</button><button onClick={() => excluir(a.id!)} aria-label={`Excluir ${a.ticker}`} className="rounded-lg p-2 text-red-700 hover:bg-red-50"><Trash2 className="h-4 w-4"/></button></div>
      {open && <div className="border-t border-slate-100 bg-slate-50 p-5"><form onSubmit={handleMov} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4"><label className="text-sm font-medium">Tipo<select value={movForm.tipo} onChange={e => setMovForm({...movForm,tipo:e.target.value})} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 px-3">{TIPOS_MOV.map(t => <option key={t}>{t}</option>)}</select></label><label className="text-sm font-medium">Data<input type="date" value={movForm.data} onChange={e => setMovForm({...movForm,data:e.target.value})} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 px-3"/></label><label className="text-sm font-medium">Quantidade<input inputMode="decimal" value={movForm.quantidade} onChange={e => setMovForm({...movForm,quantidade:e.target.value})} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 px-3"/><FieldError name="quantidade" error={movValidation.errors.quantidade}/></label><label className="text-sm font-medium">Preço unitário<input inputMode="decimal" value={movForm.precoUnitario} onChange={e => setMovForm({...movForm,precoUnitario:e.target.value})} className="mt-1 min-h-11 w-full rounded-lg border border-slate-300 px-3"/><FieldError name="precoUnitario" error={movValidation.errors.precoUnitario}/></label><fieldset className="sm:col-span-2 lg:col-span-3"><legend className="text-sm font-medium">Origem da operação</legend><div className="mt-2 flex flex-wrap gap-4"><label className="flex min-h-11 items-center gap-2"><input type="radio" checked={!movForm.externa} onChange={() => setMovForm({...movForm,externa:false})}/> Operação real com caixa</label><label className="flex min-h-11 items-center gap-2"><input type="radio" checked={movForm.externa} onChange={() => setMovForm({...movForm,externa:true,carteiraId:''})}/> Snapshot externo</label></div>{!movForm.externa && <select aria-label="Conta de caixa" value={movForm.carteiraId} onChange={e => setMovForm({...movForm,carteiraId:e.target.value})} className="mt-2 min-h-11 w-full max-w-md rounded-lg border border-slate-300 px-3"><option value="">Selecione a conta de caixa</option>{contasCaixa.map(c => <option key={c.id} value={c.id}>{c.nome} · {formatCurrency(c.saldo)}</option>)}</select>}<p className="mt-1 text-xs text-slate-600">A operação real movimenta o ledger. O snapshot externo registra apenas a posição.</p></fieldset><button className="min-h-11 self-end rounded-lg bg-violet-600 px-4 font-semibold text-white">Registrar</button></form>
      <div className="mt-5 overflow-x-auto">{movLoading ? <p className="text-sm text-slate-600">Carregando operações…</p> : movimentacoes.length === 0 ? <p className="text-sm text-slate-600">Nenhuma operação registrada.</p> : <table className="w-full min-w-[700px] text-sm"><thead><tr className="text-left text-slate-600"><th className="pb-2">Data</th><th>Tipo</th><th>Quantidade</th><th>Valor</th><th>Conciliação</th><th>Vínculo</th></tr></thead><tbody>{movimentacoes.map(m => <tr key={m.id} className="border-t border-slate-200"><td className="py-3">{new Date(`${m.data}T12:00:00`).toLocaleDateString('pt-BR')}</td><td>{label(m.tipo)}</td><td>{m.quantidade}</td><td className={m.tipo === 'COMPRA' ? 'font-semibold text-red-700' : 'font-semibold text-green-700'}>{formatCurrency(m.valorTotal)}</td><td><span className={`rounded-full px-2 py-1 text-xs font-semibold ${m.conciliacao === 'CONCILIADA' ? 'bg-green-100 text-green-800' : 'bg-amber-100 text-amber-800'}`}>{m.conciliacao}</span></td><td>{m.operacaoId ? `Operação #${m.operacaoId}` : 'Sem movimento de caixa'}</td></tr>)}</tbody></table>}</div></div>}
    </article>; })}</div>}
  </div></Layout>;
}
