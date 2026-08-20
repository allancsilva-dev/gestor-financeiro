import { Stack } from 'expo-router';
import { useTheme } from '../../../src/theme';

// Transações vive dentro da pilha do Início: a referência tirou a aba própria,
// mas a aba precisa continuar marcada quando a lista está aberta.
export default function InicioLayout() {
  const colors = useTheme();
  return (
    <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.bg } }}>
      <Stack.Screen name="index" />
      <Stack.Screen name="transacoes" />
    </Stack>
  );
}
