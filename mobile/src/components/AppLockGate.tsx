import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, AppState, AppStateStatus, KeyboardAvoidingView, Platform, Text, TextInput, TouchableOpacity, View } from 'react-native';
import * as LocalAuthentication from 'expo-local-authentication';
import * as ScreenCapture from 'expo-screen-capture';
import Constants from 'expo-constants';
import api, { refreshAccessToken } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../theme';

const LOCK_AFTER_MS = 60_000;

export default function AppLockGate({ children }: { children: React.ReactNode }) {
  const colors = useTheme();
  const { isAuthenticated, isLoading } = useAuth();
  const [locked, setLocked] = useState(false);
  const [privacy, setPrivacy] = useState(false);
  const [authenticating, setAuthenticating] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const backgroundAt = useRef<number | null>(null);
  const startupCheckPending = useRef(true);
  const autoAttempted = useRef(false);
  const visuallyLocked = locked || (isAuthenticated && !isLoading && startupCheckPending.current);

  // Desbloqueio é o momento por onde o usuário sempre passa, então é onde a
  // sessão é renovada — antes disso a digital só abria a UI sobre um token que
  // podia estar morto havia dias, e o erro aparecia tela a tela.
  // Falha de rede não prende ninguém do lado de fora: libera e o interceptor
  // tenta de novo na próxima request. Se o servidor recusou o refresh, api.ts
  // já encerrou a sessão e o portão troca a tela sozinho.
  const renovarSessao = useCallback(async () => {
    try {
      await refreshAccessToken();
    } catch {
      // refreshAccessToken já trata; aqui só garantimos que o gate abra.
    }
  }, []);

  const unlockWithDevice = useCallback(async () => {
    if (authenticating) return;
    setAuthenticating(true);
    setError(null);
    try {
      const available = await LocalAuthentication.hasHardwareAsync();
      const enrolled = available && await LocalAuthentication.isEnrolledAsync();
      if (!enrolled) {
        setShowPassword(true);
        setError('Use a senha da sua conta para desbloquear.');
        return;
      }
      const result = await LocalAuthentication.authenticateAsync({
        promptMessage: 'Desbloquear Nexos Finanças',
        cancelLabel: 'Cancelar',
        fallbackLabel: 'Usar código do aparelho',
        disableDeviceFallback: false,
      });
      if (result.success) {
        await renovarSessao();
        setLocked(false);
        setShowPassword(false);
        setPassword('');
      }
    } finally {
      setAuthenticating(false);
    }
  }, [authenticating, renovarSessao]);

  useEffect(() => {
    if (isLoading) return;

    if (startupCheckPending.current) {
      startupCheckPending.current = false;
      if (isAuthenticated) {
        autoAttempted.current = false;
        setLocked(true);
      }
      return;
    }

    if (!isAuthenticated) {
      setLocked(false);
    }
  }, [isAuthenticated, isLoading]);

  useEffect(() => {
    if (locked && isAuthenticated && !autoAttempted.current) {
      autoAttempted.current = true;
      unlockWithDevice();
    }
    if (!locked) autoAttempted.current = false;
  }, [locked, isAuthenticated, unlockWithDevice]);

  // O overlay de privacidade abaixo limpa o app switcher do iOS, mas o Android
  // tira o snapshot da Activity de qualquer jeito: só FLAG_SECURE tira os valores
  // da miniatura de recentes e do print. Vale enquanto existe sessão — depois do
  // logout não há o que proteger e o usuário volta a poder printar a própria tela.
  useEffect(() => {
    // XCTest/Maestro captura a tela continuamente para localizar elementos. No
    // ambiente descartavel de E2E isso ativaria a protecao e deixaria a arvore
    // inteira preta; producao e desenvolvimento continuam protegidos.
    if (Constants.expoConfig?.extra?.appEnv === 'local-e2e') {
      ScreenCapture.allowScreenCaptureAsync().catch(() => undefined);
      return;
    }
    if (!isAuthenticated) {
      ScreenCapture.allowScreenCaptureAsync().catch(() => undefined);
      return;
    }
    ScreenCapture.preventScreenCaptureAsync().catch(() => undefined);
    return () => {
      ScreenCapture.allowScreenCaptureAsync().catch(() => undefined);
    };
  }, [isAuthenticated]);

  useEffect(() => {
    const onState = (next: AppStateStatus) => {
      if (next === 'inactive' || next === 'background') {
        setPrivacy(true);
        if (backgroundAt.current == null) backgroundAt.current = Date.now();
        return;
      }
      if (next === 'active') {
        const elapsed = backgroundAt.current == null ? 0 : Date.now() - backgroundAt.current;
        backgroundAt.current = null;
        if (isAuthenticated && elapsed >= LOCK_AFTER_MS) {
          autoAttempted.current = false;
          setLocked(true);
        }
        setPrivacy(false);
      }
    };
    const subscription = AppState.addEventListener('change', onState);
    return () => subscription.remove();
  }, [isAuthenticated]);

  const unlockWithPassword = async () => {
    if (!password || authenticating) return;
    setAuthenticating(true);
    setError(null);
    try {
      await api.post('/v1/usuarios/me/validar-senha', { senha: password });
      await renovarSessao();
      setLocked(false);
      setPassword('');
      setShowPassword(false);
    } catch (err: any) {
      setError(err?.userMessage ?? 'Senha incorreta.');
    } finally {
      setAuthenticating(false);
    }
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      {children}
      {(privacy || (visuallyLocked && isAuthenticated)) && (
        <KeyboardAvoidingView
          behavior={Platform.select({ ios: 'padding', android: 'height' })}
          accessibilityViewIsModal
          style={{ position: 'absolute', inset: 0, zIndex: 100, backgroundColor: colors.bg, alignItems: 'center', justifyContent: 'center', padding: 28 }}
        >
          {!privacy && (
            <View style={{ width: '100%', maxWidth: 380, alignItems: 'center' }}>
              <View style={{ width: 64, height: 64, borderRadius: 16, backgroundColor: colors.brandBg, alignItems: 'center', justifyContent: 'center' }}>
                <Text style={{ fontSize: 30 }}>🔒</Text>
              </View>
              <Text style={{ color: colors.textPrimary, fontSize: 22, fontWeight: '800', marginTop: 20 }}>App bloqueado</Text>
              <Text style={{ color: colors.textSecondary, fontSize: 14, textAlign: 'center', marginTop: 6, marginBottom: 20 }}>
                Seus valores ficam ocultos até você confirmar sua identidade.
              </Text>

              {showPassword ? (
                <>
                  <TextInput
                    value={password}
                    onChangeText={setPassword}
                    secureTextEntry
                    autoFocus
                    placeholder="Senha da conta"
                    placeholderTextColor={colors.textMuted}
                    accessibilityLabel="Senha da conta"
                    onSubmitEditing={unlockWithPassword}
                    style={{ width: '100%', minHeight: 50, borderRadius: 12, backgroundColor: colors.card, color: colors.textPrimary, paddingHorizontal: 14, borderWidth: 1, borderColor: error ? colors.danger : colors.border }}
                  />
                  {error && <Text style={{ color: colors.danger, alignSelf: 'flex-start', fontSize: 12, marginTop: 6 }}>{error}</Text>}
                  <TouchableOpacity disabled={!password || authenticating} onPress={unlockWithPassword} style={{ width: '100%', minHeight: 50, borderRadius: 999, backgroundColor: colors.brand, alignItems: 'center', justifyContent: 'center', marginTop: 14, opacity: !password ? 0.5 : 1 }}>
                    {authenticating ? <ActivityIndicator color="#fff" /> : <Text style={{ color: '#fff', fontWeight: '700' }}>Desbloquear</Text>}
                  </TouchableOpacity>
                  <TouchableOpacity onPress={() => { setShowPassword(false); setError(null); unlockWithDevice(); }} style={{ minHeight: 44, justifyContent: 'center', marginTop: 8 }}>
                    <Text style={{ color: colors.brandFg, fontWeight: '600' }}>Usar biometria ou código do aparelho</Text>
                  </TouchableOpacity>
                </>
              ) : (
                <>
                  <TouchableOpacity disabled={authenticating} onPress={unlockWithDevice} style={{ width: '100%', minHeight: 50, borderRadius: 999, backgroundColor: colors.brand, alignItems: 'center', justifyContent: 'center' }}>
                    {authenticating ? <ActivityIndicator color="#fff" /> : <Text style={{ color: '#fff', fontWeight: '700' }}>Desbloquear</Text>}
                  </TouchableOpacity>
                  <TouchableOpacity onPress={() => { setShowPassword(true); setError(null); }} style={{ minHeight: 44, justifyContent: 'center', marginTop: 8 }}>
                    <Text style={{ color: colors.brandFg, fontWeight: '600' }}>Usar senha da conta</Text>
                  </TouchableOpacity>
                </>
              )}
            </View>
          )}
        </KeyboardAvoidingView>
      )}
    </View>
  );
}
