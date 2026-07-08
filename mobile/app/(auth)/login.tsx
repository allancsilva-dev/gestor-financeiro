import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, ActivityIndicator, KeyboardAvoidingView, Platform, StyleSheet } from 'react-native';
import { useAuth } from '../../src/context/AuthContext';
import { useTheme } from '../../src/theme';
import { ApiErrorWithMessage } from '../../src/types';
import { useRouter } from 'expo-router';

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

        <Text style={[styles.label, { color: colors.textSecondary }]}>E-MAIL</Text>
        <TextInput value={email} onChangeText={setEmail} placeholder="seu@email.com" placeholderTextColor={colors.textMuted} autoCapitalize="none" keyboardType="email-address" style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary }]} />

        <Text style={[styles.label, { color: colors.textSecondary, marginTop: 14 }]}>SENHA</Text>
        <TextInput value={password} onChangeText={setPassword} placeholder="••••••••" placeholderTextColor={colors.textMuted} secureTextEntry style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary }]} />

        <TouchableOpacity onPress={() => router.push('/(auth)/forgot-password')} style={{ alignSelf: 'flex-end', marginTop: 8 }}>
          <Text style={{ color: colors.brand, fontSize: 12 }}>Esqueceu a senha?</Text>
        </TouchableOpacity>

        {error ? <Text style={{ color: colors.danger, marginTop: 8 }}>{error}</Text> : null}

        <TouchableOpacity onPress={onSubmit} disabled={loading} style={[styles.button, { backgroundColor: colors.brand, opacity: loading ? 0.8 : 1 }] }>
          {loading ? <ActivityIndicator color={colors.brandText} /> : <Text style={{ color: colors.brandText, fontWeight: '700', letterSpacing: 1 }}>ENTRAR</Text>}
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
  label: { fontSize: 9, letterSpacing: 0.8, marginBottom: 6 },
  input: { borderWidth: 1, borderRadius: 8, padding: 12 },
  button: { marginTop: 24, borderRadius: 8, height: 48, alignItems: 'center', justifyContent: 'center' },
});
