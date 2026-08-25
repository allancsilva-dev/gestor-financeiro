import React, { useEffect } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { Stack } from 'expo-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../src/context/AuthContext';
import { TemaProvider } from '../src/context/TemaContext';
import { Sentry } from '../src/observability/sentry';
import { useEsquema, useTheme } from '../src/theme';
import AppLockGate from '../src/components/AppLockGate';
import { useReducedMotion } from 'react-native-reanimated';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';

SplashScreen.preventAutoHideAsync().catch(() => undefined);

const queryClient = new QueryClient();

function RootLayout() {
  const colors = useTheme();
  const esquema = useEsquema();
  const reduceMotion = useReducedMotion();
  useEffect(() => { SplashScreen.hideAsync().catch(() => undefined); }, []);
  return (
    <QueryClientProvider client={queryClient}>
      {/* Sem isto o Android fixa ícones claros: com edge-to-edge a barra é
          transparente e o relógio some sobre o fundo do tema claro. O iOS
          resolve sozinho pelo trait collection; o Android precisa do estilo. */}
      <StatusBar style={esquema === 'dark' ? 'light' : 'dark'} />
      <SafeAreaProvider>
        <AuthProvider>
          <AppLockGate>
            <Stack screenOptions={{
                headerShown: false,
                animation: reduceMotion ? 'none' : 'fade',
                animationDuration: reduceMotion ? 0 : 180,
                contentStyle: { backgroundColor: colors.bg },
              }}
            />
          </AppLockGate>
        </AuthProvider>
      </SafeAreaProvider>
    </QueryClientProvider>
  );
}

// O TemaProvider fica por fora: `RootLayout` chama `useTheme()` no próprio corpo
// e precisa do esquema já resolvido.
function Root() {
  return (
    <TemaProvider>
      <RootLayout />
    </TemaProvider>
  );
}

export default Sentry.wrap(Root);
