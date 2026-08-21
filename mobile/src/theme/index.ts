import { useColorScheme } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { spacing, tabBar } from './tokens';
import { DARK_COLORS, LIGHT_COLORS, AppColors } from './colors';
import { Esquema, useTemaOpcional } from '../context/TemaContext';

/**
 * Esquema efetivo: o escolhido em Ajustes quando há `TemaProvider` acima, senão
 * o do sistema — que era o único comportamento até a escolha de tema existir.
 */
export const useEsquema = (): Esquema => {
  const doSistema = useColorScheme();
  const tema = useTemaOpcional();
  return tema ? tema.esquema : doSistema === 'dark' ? 'dark' : 'light';
};

export const useTheme = (): AppColors => (useEsquema() === 'dark' ? DARK_COLORS : LIGHT_COLORS);

/**
 * Espaço a reservar no fim de toda tela rolável sob a tab bar.
 * A barra é um painel flutuante posicionado acima da safe area, então o
 * respiro só fecha somando `insets.bottom` — uma constante deixa a última
 * linha da lista embaixo da barra em aparelhos com home indicator.
 */
export const useTabBarSpace = (): number => {
  const insets = useSafeAreaInsets();
  return insets.bottom + tabBar.altura + spacing.lg;
};

export { DARK_COLORS, LIGHT_COLORS };
export type { AppColors };
export * from './tokens';
