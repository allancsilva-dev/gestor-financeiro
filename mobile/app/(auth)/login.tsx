import React, { useState } from 'react';
import { View, Text, TouchableOpacity, ActivityIndicator, KeyboardAvoidingView, Platform, StyleSheet } from 'react-native';
import { useAuth } from '../../src/context/AuthContext';
import { useTheme } from '../../src/theme';
import { ApiErrorWithMessage } from '../../src/types';
import { useRouter } from 'expo-router';
import Field from '../../src/components/ui/Field';

export default function Login() {
  const colors = useTheme();
  const { login } = useAuth();
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onSubmit = async () => {
    setError(null);
    if (!email || !password) return setError('Preencha e-mail e senha.');
    try {
      setLoading(true);
      const user = await login(email, password);
      if (!user.onboardingCompleto) {
        router.replace('/onboarding');
      } else {
        router.replace('/(app)/');
      }
    } catch (err) {
      const e = err as ApiErrorWithMessage;
      setError(e.userMessage ?? 'Erro inesperado. Tente novamente.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={[styles.container, { backgroundColor: colors.bg }]}>
      <View style={styles.inner}>
        <View style={[styles.logo, { backgroundColor: `${colors.brand}26` }]}>
          <View style={[styles.logoInner, { backgroundColor: colors.brand }]} />
        </View>
        <Text style={[styles.title, { color: colors.textPrimary }]}>Gestor Financeiro</Text>
        <Text style={[styles.subtitle, { color: colors.textSecondary }]}>Entre na sua conta para continuar</Text>

        <Field label="E-mail" value={email} onChangeText={setEmail} placeholder="seu@email.com" autoCapitalize="none" keyboardType="email-address" autoComplete="email" textContentType="emailAddress" />

        <Field label="Senha" value={password} onChangeText={setPassword} placeholder="••••••••" secureTextEntry autoComplete="password" textContentType="password" />

        <TouchableOpacity onPress={() => router.push('/(auth)/forgot-password')} accessibilityRole="button" style={{ alignSelf: 'flex-end', minHeight: 44, justifyContent: 'center' }}>
          <Text style={{ color: colors.brandFg, fontSize: 13 }}>Esqueceu a senha?</Text>
        </TouchableOpacity>

        {error ? <Text style={{ color: colors.danger, marginTop: 8 }}>{error}</Text> : null}

        <TouchableOpacity onPress={onSubmit} disabled={loading} accessibilityRole="button" style={[styles.button, { backgroundColor: colors.brand, opacity: loading ? 0.8 : 1 }] }>
          {loading ? <ActivityIndicator color={colors.brandText} /> : <Text style={{ color: colors.brandText, fontWeight: '700', letterSpacing: 1 }}>ENTRAR</Text>}
        </TouchableOpacity>

        <TouchableOpacity onPress={() => router.push('/(auth)/register')} accessibilityRole="button" style={{ alignSelf: 'center', marginTop: 16, minHeight: 44, justifyContent: 'center' }}>
          <Text style={{ color: colors.textSecondary, fontSize: 13 }}>Não tem conta? <Text style={{ color: colors.brandFg, fontWeight: '600' }}>Criar conta</Text></Text>
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  inner: { flex: 1, justifyContent: 'center', paddingHorizontal: 24 },
  logo: { width: 48, height: 48, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  logoInner: { width: 22, height: 22, borderRadius: 6 },
  title: { fontSize: 24, fontWeight: '700', marginTop: 16 },
  subtitle: { fontSize: 13, marginBottom: 32 },
  button: { marginTop: 24, borderRadius: 12, height: 48, alignItems: 'center', justifyContent: 'center' },
});
