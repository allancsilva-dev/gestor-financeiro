import React, { useEffect } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { Stack } from 'expo-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../src/context/AuthContext';
import { Sentry } from '../src/observability/sentry';
import { useTheme } from '../src/theme';
import AppLockGate from '../src/components/AppLockGate';
import { useReducedMotion } from 'react-native-reanimated';
import * as SplashScreen from 'expo-splash-screen';

SplashScreen.preventAutoHideAsync().catch(() => undefined);

const queryClient = new QueryClient();

function RootLayout() {
  const colors = useTheme();
  const reduceMotion = useReducedMotion();
  useEffect(() => { SplashScreen.hideAsync().catch(() => undefined); }, []);
  return (
    <QueryClientProvider client={queryClient}>
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

export default Sentry.wrap(RootLayout);
