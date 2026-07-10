import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, ActivityIndicator, KeyboardAvoidingView, Platform, StyleSheet, ScrollView } from 'react-native';
import { useAuth } from '../../src/context/AuthContext';
import { useTheme } from '../../src/theme';
import api from '../../src/services/api';
import { ApiErrorWithMessage } from '../../src/types';
import { useRouter } from 'expo-router';

// Mesma regra do backend (@ValidPassword): mínimo 8, ao menos 1 letra e 1 número
const senhaValida = (s: string) => s.length >= 8 && /[A-Za-z]/.test(s) && /\d/.test(s);
const emailValido = (s: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s);

export default function Register() {
  const colors = useTheme();
  const { login } = useAuth();
  const router = useRouter();
  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onSubmit = async () => {
    setError(null);
    const nomeTrim = nome.trim();
    const emailTrim = email.trim();
    if (nomeTrim.length < 2) return setError('Informe seu nome (mínimo 2 caracteres).');
    if (!emailValido(emailTrim)) return setError('Informe um e-mail válido.');
    if (!senhaValida(password)) return setError('Senha deve ter no mínimo 8 caracteres, com ao menos 1 letra e 1 número.');
    if (password !== confirmPassword) return setError('As senhas não coincidem.');
    try {
      setLoading(true);
      await api.post('/auth/register', { nome: nomeTrim, email: emailTrim, password, confirmPassword });
      // Conta criada — entra direto e segue para o onboarding
      const user = await login(emailTrim, password);
      router.replace(user.onboardingCompleto ? '/(app)/' : '/onboarding');
    } catch (err) {
      const e = err as ApiErrorWithMessage;
      setError(e.userMessage ?? 'Erro ao criar conta. Tente novamente.');
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={[styles.container, { backgroundColor: colors.bg }]}>
      <ScrollView contentContainerStyle={styles.inner} keyboardShouldPersistTaps="handled">
        <View style={[styles.logo, { backgroundColor: `${colors.brand}26` }]}>
          <View style={[styles.logoInner, { backgroundColor: colors.brand }]} />
        </View>
        <Text style={[styles.title, { color: colors.textPrimary }]}>Criar conta</Text>
        <Text style={[styles.subtitle, { color: colors.textSecondary }]}>Comece a organizar suas finanças em minutos</Text>

        <Text style={[styles.label, { color: colors.textSecondary }]}>NOME</Text>
        <TextInput value={nome} onChangeText={setNome} placeholder="Seu nome" placeholderTextColor={colors.textMuted} autoCapitalize="words" textContentType="name" style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary }]} />

        <Text style={[styles.label, { color: colors.textSecondary, marginTop: 14 }]}>E-MAIL</Text>
        <TextInput value={email} onChangeText={setEmail} placeholder="seu@email.com" placeholderTextColor={colors.textMuted} autoCapitalize="none" keyboardType="email-address" textContentType="emailAddress" style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary }]} />

        <Text style={[styles.label, { color: colors.textSecondary, marginTop: 14 }]}>SENHA</Text>
        <TextInput value={password} onChangeText={setPassword} placeholder="Mínimo 8 caracteres, 1 letra e 1 número" placeholderTextColor={colors.textMuted} secureTextEntry textContentType="newPassword" style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary }]} />

        <Text style={[styles.label, { color: colors.textSecondary, marginTop: 14 }]}>CONFIRMAR SENHA</Text>
        <TextInput value={confirmPassword} onChangeText={setConfirmPassword} placeholder="Repita a senha" placeholderTextColor={colors.textMuted} secureTextEntry textContentType="newPassword" style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary }]} />

        {error ? <Text style={{ color: colors.danger, marginTop: 8 }}>{error}</Text> : null}

        <TouchableOpacity onPress={onSubmit} disabled={loading} accessibilityRole="button" style={[styles.button, { backgroundColor: colors.brand, opacity: loading ? 0.8 : 1 }]}>
          {loading ? <ActivityIndicator color={colors.brandText} /> : <Text style={{ color: colors.brandText, fontWeight: '700', letterSpacing: 1 }}>CRIAR CONTA</Text>}
        </TouchableOpacity>

        <TouchableOpacity onPress={() => router.back()} accessibilityRole="button" style={{ alignSelf: 'center', marginTop: 16, minHeight: 44, justifyContent: 'center' }}>
          <Text style={{ color: colors.textSecondary, fontSize: 13 }}>Já tenho conta · <Text style={{ color: colors.brand, fontWeight: '600' }}>Entrar</Text></Text>
        </TouchableOpacity>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  inner: { flexGrow: 1, justifyContent: 'center', paddingHorizontal: 24, paddingVertical: 32 },
  logo: { width: 48, height: 48, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  logoInner: { width: 22, height: 22, borderRadius: 6 },
  title: { fontSize: 24, fontWeight: '700', marginTop: 16 },
  subtitle: { fontSize: 13, marginBottom: 32 },
  label: { fontSize: 9, letterSpacing: 0.8, marginBottom: 6 },
  input: { borderWidth: 1, borderRadius: 8, padding: 12 },
  button: { marginTop: 24, borderRadius: 8, height: 48, alignItems: 'center', justifyContent: 'center' },
});
