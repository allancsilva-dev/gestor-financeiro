import * as SecureStore from 'expo-secure-store';
import Constants from 'expo-constants';
import { competenciaAtual } from '../domain/periodo';

/**
 * Alertas que o usuário já leu e mandou embora.
 *
 * Fica **só no dispositivo** — dispensar um alerta é preferência de leitura, não fato financeiro,
 * e mandá-la ao servidor criaria estado de usuário para algo que o backend recalcula todo mês.
 *
 * A dispensa vale pela competência: no mês seguinte o alerta volta se o gasto continuar fora do
 * padrão. Sem isso, "dispensar" viraria "nunca mais me avise", que é o oposto do que o usuário quer
 * dizer quando toca em "Ok, entendi".
 */
const CHAVE = 'alertasDispensados';

const isLocalE2E = Constants.expoConfig?.extra?.appEnv === 'local-e2e';
const volatileStore: Record<string, string | undefined> = {};

interface Dispensados {
  competencia: string;
  chaves: string[];
}

const setItem = async (value: string) => {
  if (isLocalE2E) { volatileStore[CHAVE] = value; return; }
  await SecureStore.setItemAsync(CHAVE, value);
};

const getItem = async (): Promise<string | null> => {
  if (isLocalE2E) return volatileStore[CHAVE] ?? null;
  return SecureStore.getItemAsync(CHAVE);
};

/** `2026-08` — a competência em que a dispensa vale. */
export const competenciaDoMes = (hoje: Date = new Date()): string => {
  const { mes, ano } = competenciaAtual(hoje);
  return `${ano}-${String(mes).padStart(2, '0')}`;
};

export const chaveDoAlerta = (categoriaNome: string): string =>
  `categoria:${categoriaNome.trim().toLowerCase()}`;

export const listarDispensados = async (hoje: Date = new Date()): Promise<string[]> => {
  const raw = await getItem();
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw) as Dispensados;
    if (!parsed || typeof parsed.competencia !== 'string' || !Array.isArray(parsed.chaves)) return [];
    // Virou o mês: a dispensa do mês passado não silencia o alerta deste.
    if (parsed.competencia !== competenciaDoMes(hoje)) return [];
    return parsed.chaves.filter((chave) => typeof chave === 'string');
  } catch {
    return [];
  }
};

export const dispensarAlerta = async (chave: string, hoje: Date = new Date()): Promise<string[]> => {
  const atuais = await listarDispensados(hoje);
  if (atuais.includes(chave)) return atuais;
  const proximos = [...atuais, chave];
  await setItem(JSON.stringify({ competencia: competenciaDoMes(hoje), chaves: proximos }));
  return proximos;
};

export const limparDispensados = async (): Promise<void> => {
  await setItem(JSON.stringify({ competencia: competenciaDoMes(), chaves: [] }));
};
