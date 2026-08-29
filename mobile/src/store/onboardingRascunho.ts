import * as SecureStore from 'expo-secure-store';
import Constants from 'expo-constants';

/**
 * Rascunho do onboarding. O fluxo agora tem vários passos e um único envio no
 * fim — se o app for fechado no meio (ou o token expirar), o que já foi
 * digitado precisa continuar lá quando o usuário voltar. Vale por dispositivo e
 * é apagado assim que o onboarding é concluído.
 */
export interface OnboardingRascunho {
  passo?: string;
  conta?: { nome: string; tipo: string; saldo: string; banco?: string };
  renda?: { nome: string; valor: string; dia: string } | null;
  categorias?: string[];
  cartao?: { nome: string; limite: string; fechamento: string; vencimento: string } | null;
  meta?: { nome: string; valor: string; data: string } | null;
}

const RASCUNHO_KEY = 'onboardingRascunho';

const isLocalE2E = Constants.expoConfig?.extra?.appEnv === 'local-e2e';
const volatileStore: Record<string, string | undefined> = {};

export const salvarRascunho = async (rascunho: OnboardingRascunho) => {
  const valor = JSON.stringify(rascunho);
  if (isLocalE2E) { volatileStore[RASCUNHO_KEY] = valor; return; }
  await SecureStore.setItemAsync(RASCUNHO_KEY, valor);
};

export const lerRascunho = async (): Promise<OnboardingRascunho | null> => {
  const valor = isLocalE2E
    ? volatileStore[RASCUNHO_KEY] ?? null
    : await SecureStore.getItemAsync(RASCUNHO_KEY);
  if (!valor) return null;
  try {
    const dados = JSON.parse(valor);
    return dados && typeof dados === 'object' ? (dados as OnboardingRascunho) : null;
  } catch {
    // Rascunho corrompido não pode travar a entrada do usuário no app
    return null;
  }
};

export const limparRascunho = async () => {
  if (isLocalE2E) { delete volatileStore[RASCUNHO_KEY]; return; }
  await SecureStore.deleteItemAsync(RASCUNHO_KEY);
};
