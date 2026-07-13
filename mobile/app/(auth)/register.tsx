import React, { useState } from 'react';
import { View, Text, TouchableOpacity, ActivityIndicator, KeyboardAvoidingView, Platform, StyleSheet, ScrollView } from 'react-native';
import { useAuth } from '../../src/context/AuthContext';
import { useTheme } from '../../src/theme';
import api from '../../src/services/api';
import { ApiErrorWithMessage } from '../../src/types';
import { useRouter } from 'expo-router';
import Field from '../../src/components/ui/Field';
import { isValidEmail, isValidPassword } from '../../src/utils/validate';

export default function Register() {
  const colors = useTheme();
  const { login } = useAuth();
  const router = useRouter();
  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [aceitaTermos, setAceitaTermos] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onSubmit = async () => {
    setError(null);
    const nomeTrim = nome.trim();
    const emailTrim = email.trim();
    if (nomeTrim.length < 2) return setError('Informe seu nome (mínimo 2 caracteres).');
    if (!isValidEmail(emailTrim)) return setError('Informe um e-mail válido.');
    if (!isValidPassword(password)) return setError('Senha deve ter no mínimo 8 caracteres, com ao menos 1 letra e 1 número.');
    if (password !== confirmPassword) return setError('As senhas não coincidem.');
    if (!aceitaTermos) return setError('É preciso aceitar a política de privacidade para criar a conta.');
    try {
      setLoading(true);
      await api.post('/auth/register', { nome: nomeTrim, email: emailTrim, password, confirmPassword, aceitaTermos });
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

        <Field label="Nome" value={nome} onChangeText={setNome} placeholder="Seu nome" autoCapitalize="words" textContentType="name" />

        <Field label="E-mail" value={email} onChangeText={setEmail} placeholder="seu@email.com" autoCapitalize="none" keyboardType="email-address" textContentType="emailAddress" />

        <Field label="Senha" value={password} onChangeText={setPassword} placeholder="Mínimo 8 caracteres, 1 letra e 1 número" secureTextEntry textContentType="newPassword" />

        <Field label="Confirmar senha" value={confirmPassword} onChangeText={setConfirmPassword} placeholder="Repita a senha" secureTextEntry textContentType="newPassword" />

        <TouchableOpacity
          onPress={() => setAceitaTermos((v) => !v)}
          accessibilityRole="checkbox"
          accessibilityState={{ checked: aceitaTermos }}
          style={styles.termosRow}
        >
          <View style={[styles.checkbox, { borderColor: aceitaTermos ? colors.brand : colors.border, backgroundColor: aceitaTermos ? colors.brand : 'transparent' }]}>
            {aceitaTermos ? <Text style={{ color: colors.brandText, fontSize: 12, fontWeight: '700' }}>✓</Text> : null}
          </View>
          <Text style={{ color: colors.textSecondary, fontSize: 13, flex: 1 }}>
            Li e aceito a <Text style={{ color: colors.brandFg, fontWeight: '600' }}>política de privacidade</Text> e o tratamento dos meus dados conforme a LGPD.
          </Text>
        </TouchableOpacity>

        {error ? <Text style={{ color: colors.danger, marginTop: 8 }}>{error}</Text> : null}

        <TouchableOpacity onPress={onSubmit} disabled={loading} accessibilityRole="button" style={[styles.button, { backgroundColor: colors.brand, opacity: loading ? 0.8 : 1 }]}>
          {loading ? <ActivityIndicator color={colors.brandText} /> : <Text style={{ color: colors.brandText, fontWeight: '700', letterSpacing: 1 }}>CRIAR CONTA</Text>}
        </TouchableOpacity>

        <TouchableOpacity onPress={() => router.back()} accessibilityRole="button" style={{ alignSelf: 'center', marginTop: 16, minHeight: 44, justifyContent: 'center' }}>
          <Text style={{ color: colors.textSecondary, fontSize: 13 }}>Já tenho conta · <Text style={{ color: colors.brandFg, fontWeight: '600' }}>Entrar</Text></Text>
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
  termosRow: { flexDirection: 'row', alignItems: 'center', gap: 10, marginTop: 2, minHeight: 44 },
  checkbox: { width: 22, height: 22, borderRadius: 6, borderWidth: 1.5, alignItems: 'center', justifyContent: 'center' },
  button: { marginTop: 24, borderRadius: 12, height: 48, alignItems: 'center', justifyContent: 'center' },
});
