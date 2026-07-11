import React, { useState } from 'react';
import { View, Text, TouchableOpacity, ActivityIndicator, KeyboardAvoidingView, Platform, StyleSheet } from 'react-native';
import { useTheme } from '../../src/theme';
import { useRouter } from 'expo-router';
import api from '../../src/services/api';
import { ApiErrorWithMessage } from '../../src/types';
import Field from '../../src/components/ui/Field';

export default function ForgotPassword() {
  const colors = useTheme();
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onSubmit = async () => {
    setError(null);
    if (!email.trim()) return setError('Informe seu e-mail.');
    try {
      setLoading(true);
      await api.post('/auth/forgot-password', { email: email.trim() });
      setSuccess(true);
    } catch (err) {
      const e = err as ApiErrorWithMessage;
      setError(e.userMessage ?? 'Erro ao enviar. Tente novamente.');
    } finally {
      setLoading(false);
    }
  };

  if (success) {
    return (
      <View style={[styles.container, { backgroundColor: colors.bg }]}>
        <View style={styles.inner}>
          <Text style={[styles.title, { color: colors.textPrimary }]}>E-mail enviado</Text>
          <Text style={[styles.subtitle, { color: colors.textSecondary }]}>Se o e-mail estiver cadastrado, você receberá instruções para redefinir sua senha.</Text>
          <TouchableOpacity onPress={() => router.push('/(auth)/reset-password')} accessibilityRole="button" style={[styles.button, { backgroundColor: colors.brand }]}>
            <Text style={{ color: colors.brandText, fontWeight: '700' }}>Já recebi o código</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={() => router.back()} accessibilityRole="button" style={{ alignSelf: 'center', marginTop: 16, minHeight: 44, justifyContent: 'center' }}>
            <Text style={{ color: colors.textSecondary, fontSize: 13 }}>Voltar para o login</Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  }

  return (
    <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={[styles.container, { backgroundColor: colors.bg }]}>
      <View style={styles.inner}>
        <Text style={[styles.title, { color: colors.textPrimary }]}>Esqueceu a senha?</Text>
        <Text style={[styles.subtitle, { color: colors.textSecondary }]}>Informe seu e-mail para receber as instruções de recuperação.</Text>

        <Field label="E-mail" value={email} onChangeText={setEmail} placeholder="seu@email.com" autoCapitalize="none" keyboardType="email-address" autoComplete="email" textContentType="emailAddress" />

        {error ? <Text style={{ color: colors.danger, marginTop: 8 }}>{error}</Text> : null}

        <TouchableOpacity onPress={onSubmit} disabled={loading} accessibilityRole="button" style={[styles.button, { backgroundColor: colors.brand, opacity: loading ? 0.8 : 1 }]}>
          {loading ? <ActivityIndicator color={colors.brandText} /> : <Text style={{ color: colors.brandText, fontWeight: '700', letterSpacing: 1 }}>ENVIAR</Text>}
        </TouchableOpacity>

        <TouchableOpacity onPress={() => router.back()} accessibilityRole="button" style={{ alignSelf: 'center', marginTop: 16, minHeight: 44, justifyContent: 'center' }}>
          <Text style={{ color: colors.textSecondary, fontSize: 13 }}>Voltar para o login</Text>
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  inner: { flex: 1, justifyContent: 'center', paddingHorizontal: 24 },
  title: { fontSize: 24, fontWeight: '700', marginTop: 16 },
  subtitle: { fontSize: 13, marginBottom: 32 },
  button: { marginTop: 24, borderRadius: 12, height: 48, alignItems: 'center', justifyContent: 'center' },
});
