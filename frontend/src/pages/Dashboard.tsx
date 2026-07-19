import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronDown, ChevronRight, ChevronUp, RefreshCw } from 'lucide-react';
import Layout from '../components/Layout';
import GraficoGastosPorCategoria from '../components/GraficoGastosPorCategoria';
import GraficoEvolucaoMensal from '../components/GraficoEvolucaoMensal';
import dashboardService, { EvolucaoMensal, GastosPorCategoria, ProjecaoMensal, ProjecaoResponse } from '../services/dashboardService';
import metricasService, { Metricas, MetricaId, OrigemMetrica } from '../services/metricasService';
import { rotaDaNavegacao } from '../utils/rotaDaNavegacao';
import { formatCurrency } from '../utils/currency';

type MetricDefinition = { id: MetricaId; label: string; value: (m: Metricas) => number; help: string };

const groups: { title: string; metrics: MetricDefinition[] }[] = [
  { title: 'Disponibilidade', metrics: [
    { id: 'DISPONIVEL_AGORA', label: 'Disponível agora', value: m => m.disponivelAgora, help: 'Caixa conciliado com liquidez imediata' },
    { id: 'RESERVADO', label: 'Reservado', value: m => m.reservado, help: 'Cofres e valores destinados a metas' },
    { id: 'DISPONIVEL_PARA_GASTAR', label: 'Disponível para gastar', value: m => m.disponivelParaGastar, help: 'O que resta após reservas e obrigações' },
  ]},
  { title: 'Obrigações', metrics: [
    { id: 'COMPROMETIDO', label: 'Comprometido', value: m => m.comprometido, help: 'Faturas e parcelas até o horizonte' },
    { id: 'DIVIDAS', label: 'Dívidas', value: m => m.dividas, help: 'Saldo total de contas passivas' },
  ]},
  { title: 'Patrimônio', metrics: [
    { id: 'INVESTIDO', label: 'Investido', value: m => m.investido, help: 'Valor de mercado das posições cotadas' },
    { id: 'PATRIMONIO_LIQUIDO', label: 'Patrimônio líquido', value: m => m.patrimonioLiquido, help: 'Ativos e investimentos menos passivos' },
    { id: 'VARIACAO_PATRIMONIAL', label: 'Variação patrimonial', value: m => m.variacaoPatrimonial.total, help: 'Mudança patrimonial no mês' },
  ]},
  { title: 'Resultado', metrics: [
    { id: 'RESULTADO_MENSAL', label: 'Resultado mensal', value: m => m.resultadoMensal, help: 'Receitas menos despesas por competência' },
  ]},
];

const originLabel = (value: string) => value.replaceAll('_', ' ').toLocaleLowerCase('pt-BR').replace(/^./, c => c.toUpperCase());

export default function Dashboard() {
  const [metricas, setMetricas] = useState<Metricas | null>(null);
  const [gastos, setGastos] = useState<GastosPorCategoria[]>([]);
  const [evolucao, setEvolucao] = useState<EvolucaoMensal[]>([]);
  const [projecao, setProjecao] = useState<ProjecaoResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [expanded, setExpanded] = useState<MetricaId | null>(null);
  const [origens, setOrigens] = useState<Partial<Record<MetricaId, OrigemMetrica[]>>>({});
  const [origensLoading, setOrigensLoading] = useState(false);
  const [origensError, setOrigensError] = useState<MetricaId | null>(null);
  const navigate = useNavigate();

  const carregar = async () => {
    setLoading(true); setError(false);
    try {
      const [m, g, e, p] = await Promise.all([
        metricasService.obter(),
        dashboardService.gastosPorCategoria().catch(() => []),
        dashboardService.evolucaoMensal().catch(() => []),
        dashboardService.projecao().catch(() => null),
      ]);
      setMetricas(m); setGastos(g); setEvolucao(e); setProjecao(p);
    } catch { setError(true); }
    finally { setLoading(false); }
  };

  useEffect(() => { carregar(); }, []);

  const loadOrigins = async (id: MetricaId) => {
    setOrigensLoading(true); setOrigensError(null);
    try {
      const data = await metricasService.listarOrigens(id);
      setOrigens(current => ({ ...current, [id]: data }));
    } catch { setOrigensError(id); }
    finally { setOrigensLoading(false); }
  };

  const toggleOrigins = async (id: MetricaId) => {
    if (expanded === id) { setExpanded(null); return; }
    setExpanded(id); setOrigensError(null);
    if (origens[id]) return;
    await loadOrigins(id);
  };

  const metricCard = (definition: MetricDefinition, primary = false) => {
    if (!metricas) return null;
    const value = definition.value(metricas);
    const isNegative = value < 0 || ['COMPROMETIDO', 'DIVIDAS'].includes(definition.id);
    const open = expanded === definition.id;
    return <div key={definition.id} className={`${primary ? 'bg-violet-700 text-white' : 'bg-white text-slate-950'} overflow-hidden rounded-xl shadow-sm`}>
      <button onClick={() => toggleOrigins(definition.id)} aria-expanded={open} aria-controls={`origens-${definition.id}`} className={`min-h-32 w-full p-5 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset ${primary ? 'hover:bg-violet-800 focus-visible:ring-white' : 'hover:bg-violet-50 focus-visible:ring-violet-600'}`}>
        <span className={`text-sm font-semibold ${primary ? 'text-violet-100' : 'text-slate-600'}`}>{definition.label}</span>
        <span className={`mt-2 block text-2xl font-bold tabular-nums ${primary ? 'text-white' : isNegative ? 'text-red-700' : 'text-green-700'}`}>{formatCurrency(value)}</span>
        <span className={`mt-2 flex items-center justify-between gap-3 text-xs ${primary ? 'text-violet-100' : 'text-slate-600'}`}><span>{definition.help}</span>{open ? <ChevronUp className="h-4 w-4 shrink-0" /> : <ChevronDown className="h-4 w-4 shrink-0" />}</span>
      </button>
      {open && <div id={`origens-${definition.id}`} className={`${primary ? 'bg-violet-800' : 'bg-slate-50'} border-t border-black/5 px-5 py-4`}>
        {origensLoading && !origens[definition.id] ? <p className="text-sm">Carregando origens…</p> : origensError === definition.id ? <button onClick={() => loadOrigins(definition.id)} className="text-sm font-semibold underline">Falha ao carregar. Tentar novamente</button> : (origens[definition.id]?.length ?? 0) === 0 ? <p className="text-sm">Nenhuma origem compõe este valor.</p> : <ul className="space-y-2">{origens[definition.id]?.map((o, index) => {
          // Linha só aparenta ser clicável com destino válido (PR-F3-12)
          const rota = o.navegacao ? rotaDaNavegacao(o.navegacao) : null;
          const conteudo = <><span><span className="font-medium">{o.descricao}</span><span className={`ml-2 text-xs ${primary ? 'text-violet-200' : 'text-slate-500'}`}>{originLabel(o.tipo)}</span></span><span className="flex items-center gap-2"><strong className="tabular-nums">{formatCurrency(o.valor)}</strong>{rota && <ChevronRight className="h-4 w-4 shrink-0" aria-hidden="true" />}</span></>;
          return <li key={`${o.tipo}-${o.id}-${index}`} className="text-sm">
            {rota
              ? <button onClick={() => navigate(rota)} aria-label={`${o.descricao}, ${formatCurrency(o.valor)}. Abrir detalhe`} className={`flex min-h-11 w-full items-center justify-between gap-4 rounded-lg px-2 text-left focus-visible:outline-none focus-visible:ring-2 ${primary ? 'hover:bg-violet-700 focus-visible:ring-white' : 'hover:bg-violet-100 focus-visible:ring-violet-600'}`}>{conteudo}</button>
              : <div className="flex items-center justify-between gap-4 px-2">{conteudo}</div>}
          </li>;
        })}</ul>}
      </div>}
    </div>;
  };

  if (loading) return <Layout><div aria-label="Carregando dashboard" className="mx-auto max-w-7xl space-y-5"><div className="h-10 w-72 animate-pulse rounded bg-violet-200"/><div className="h-40 animate-pulse rounded-xl bg-violet-200"/><div className="grid gap-4 md:grid-cols-3">{[1,2,3].map(i => <div key={i} className="h-32 animate-pulse rounded-xl bg-white"/>)}</div></div></Layout>;
  if (error || !metricas) return <Layout><div className="mx-auto max-w-lg rounded-xl bg-white p-8 text-center"><h1 className="text-xl font-bold">Não foi possível montar sua visão financeira</h1><p className="mt-2 text-slate-600">As métricas oficiais não estão disponíveis agora.</p><button onClick={carregar} className="mt-5 min-h-11 rounded-lg bg-violet-600 px-4 font-semibold text-white">Tentar novamente</button></div></Layout>;

  const principal = groups[0].metrics[2];
  return <Layout><div className="mx-auto max-w-7xl space-y-8">
    <header className="flex flex-wrap items-end justify-between gap-3"><div><h1 className="text-3xl font-bold tracking-tight">Visão financeira</h1><p className="mt-1 text-slate-600">Posição em {new Date(`${metricas.dataReferencia}T12:00:00`).toLocaleDateString('pt-BR')} · compromissos até {new Date(`${metricas.horizonteComprometido}T12:00:00`).toLocaleDateString('pt-BR')}</p></div><button onClick={carregar} className="flex min-h-11 items-center gap-2 rounded-lg px-3 text-sm font-semibold text-violet-700 hover:bg-violet-100 focus-visible:ring-2 focus-visible:ring-violet-600"><RefreshCw className="h-4 w-4"/> Atualizar</button></header>
    <section aria-labelledby="principal-title"><h2 id="principal-title" className="sr-only">Leitura principal</h2>{metricCard(principal, true)}</section>
    {groups.map(group => { const items = group.metrics.filter(m => m.id !== principal.id); return items.length > 0 && <section key={group.title} aria-labelledby={`group-${group.title}`}><h2 id={`group-${group.title}`} className="mb-3 text-lg font-bold">{group.title}</h2><div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">{items.map(m => metricCard(m))}</div></section>; })}

    <section aria-labelledby="graficos-title"><h2 id="graficos-title" className="mb-3 text-xl font-bold">Tendências do mês</h2><div className="flex flex-col gap-5 xl:flex-row"><div className="xl:w-1/3"><GraficoGastosPorCategoria chartData={gastos} /></div><div className="xl:w-2/3"><GraficoEvolucaoMensal chartData={evolucao} /></div></div></section>
    {projecao?.meses?.length ? <section aria-labelledby="projecao-title"><h2 id="projecao-title" className="mb-3 text-xl font-bold">Projeção de caixa</h2><div className="overflow-x-auto rounded-xl bg-white shadow-sm"><table className="w-full min-w-[700px] text-sm"><thead><tr className="border-b border-slate-200 text-left text-slate-600"><th className="p-4">Mês</th><th className="p-4 text-right">Contas fixas</th><th className="p-4 text-right">Parcelas</th><th className="p-4 text-right">Total de saídas</th><th className="p-4 text-right">Saldo final</th></tr></thead><tbody>{projecao.meses.map((m: ProjecaoMensal) => <tr key={`${m.ano}-${m.mes}`} className="border-b border-slate-100 last:border-0"><td className="p-4 font-semibold">{m.periodo}</td><td className="p-4 text-right text-red-700">{formatCurrency(m.totalContasFixas)}</td><td className="p-4 text-right text-red-700">{formatCurrency(m.totalParcelas)}</td><td className="p-4 text-right font-semibold text-red-700">{formatCurrency(m.totalSaidas)}</td><td className={`p-4 text-right font-bold ${m.saldoFinal >= 0 ? 'text-green-700' : 'text-red-700'}`}>{formatCurrency(m.saldoFinal)}</td></tr>)}</tbody></table></div></section> : null}
  </div></Layout>;
}
