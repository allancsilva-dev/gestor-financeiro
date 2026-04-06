import { useColorScheme } from 'react-native';
import { DARK_COLORS, LIGHT_COLORS, AppColors } from './colors';

// Segue o tema do sistema automaticamente — sem toggle manual
export const useTheme = (): AppColors => {
  const scheme = useColorScheme();
  return scheme === 'dark' ? DARK_COLORS : LIGHT_COLORS;
};
