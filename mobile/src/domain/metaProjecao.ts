import { Meta } from '../types';
import { formatCurrency } from '../utils/format';

/**
 * Projeções de ritmo da meta.
 *
 * O backend devolve só os fatos (valor total, reservado, aporte mensal declarado e
 * data prevista). O quanto falta por mês e quando a meta cai são derivações — ficam
 * aqui, num módulo puro e testável, e não espalhadas pela tela.
 */

export type TomDoRitmo = 'atencao' | 'noTrilho';

export interface RotuloDeRitmo {
  texto: string;
  tom: TomDoRitmo;
}

const aoMeioDia = (iso: string): Date => new Date(`${iso.slice(0, 10)}T12:00:00`);

const faltaPara = (meta: Meta): number =>
  Math.max(0, Number(meta.valorTotal ?? 0) - Number(meta.valorReservado ?? 0));

/** Meses cheios entre duas datas, arredondando para cima e nunca abaixo de 1. */
const mesesEntre = (de: Date, ate: Date): number => {
  const meses =
    (ate.getFullYear() - de.getFullYear()) * 12 +
    (ate.getMonth() - de.getMonth()) +
    (ate.getDate() >= de.getDate() ? 0 : -1);
  return Math.max(1, meses);
};

const mesAnoBR = (data: Date): string =>
  data.toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });

/**
 * Quanto a meta exige por mês para fechar no prazo previsto.
 * `null` quando não há prazo ou quando já não falta nada.
 */
export function aporteMensalNecessario(meta: Meta, hoje: Date = new Date()): number | null {
  const falta = faltaPara(meta);
  if (falta <= 0 || !meta.dataPrevista) return null;
  return falta / mesesEntre(hoje, aoMeioDia(meta.dataPrevista));
}

/**
 * Em quantos meses a meta fecha mantendo o aporte declarado, e em que mês isso cai.
 * `null` sem aporte declarado ou quando já não falta nada.
 */
export function previsaoDeConclusao(
  meta: Meta,
  hoje: Date = new Date(),
): { meses: number; dataAlvo: Date } | null {
  const falta = faltaPara(meta);
  const mensal = Number(meta.valorMensal ?? 0);
  if (falta <= 0 || mensal <= 0) return null;
  const meses = Math.max(1, Math.ceil(falta / mensal));
  const dataAlvo = new Date(hoje.getFullYear(), hoje.getMonth() + meses, 1, 12);
  return { meses, dataAlvo };
}

/**
 * A frase do rodapé do card.
 *
 * Se o aporte declarado dá conta do prazo, a meta está no trilho e o card mostra
 * quando ela cai. Se não dá — ou se não há aporte declarado —, mostra o esforço que
 * o prazo exige, em tom de atenção. Sem prazo e sem aporte não há o que projetar.
 */
export function rotuloDeRitmo(meta: Meta, hoje: Date = new Date()): RotuloDeRitmo | null {
  if (meta.status !== 'ATIVA' || faltaPara(meta) <= 0) return null;

  const necessario = aporteMensalNecessario(meta, hoje);
  const previsao = previsaoDeConclusao(meta, hoje);
  const mensal = Number(meta.valorMensal ?? 0);

  if (previsao && (necessario == null || mensal >= necessario)) {
    return {
      texto: `Atingindo em ${previsao.meses}m · ${mesAnoBR(previsao.dataAlvo)}`,
      tom: 'noTrilho',
    };
  }
  if (necessario != null) {
    return { texto: `Precisa de ${formatCurrency(necessario)}/mês`, tom: 'atencao' };
  }
  return null;
}
