import React, { useState } from 'react';
import { View, Text, TouchableOpacity, ActivityIndicator, KeyboardAvoidingView, Platform, StyleSheet, ScrollView } from 'react-native';
import { useTheme } from '../../src/theme';
import { useRouter, useLocalSearchParams } from 'expo-router';
import api from '../../src/services/api';
import { ApiErrorWithMessage } from '../../src/types';
import Field from '../../src/components/ui/Field';

// Mesma regra do backend (@ValidPassword): mínimo 8, ao menos 1 letra e 1 número
const senhaValida = (s: string) => s.length >= 8 && /[A-Za-z]/.test(s) && /\d/.test(s);

// Acessível por deep link (gestorfinanceiro://reset-password?token=...) ou
// pelo fluxo "Esqueceu a senha" com colagem manual do código do e-mail.
export default function ResetPassword() {
  const colors = useTheme();
  const router = useRouter();
  const params = useLocalSearchParams<{ token?: string }>();
  const [token, setToken] = useState(typeof params.token === 'string' ? params.token : '');
  const [novaSenha, setNovaSenha] = useState('');
  const [confirmar, setConfirmar] = useState('');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onSubmit = async () => {
    setError(null);
    if (!token.trim()) return setError('Cole o código recebido por e-mail.');
    if (!senhaValida(novaSenha)) return setError('Senha deve ter no mínimo 8 caracteres, com ao menos 1 letra e 1 número.');
    if (novaSenha !== confirmar) return setError('As senhas não coincidem.');
    try {
      setLoading(true);
      await api.post('/auth/reset-password', { token: token.trim(), novaSenha });
      setSuccess(true);
    } catch (err) {
      const e = err as ApiErrorWithMessage;
      setError(e.userMessage ?? 'Erro ao redefinir a senha. Tente novamente.');
    } finally {
      setLoading(false);
    }
  };

  if (success) {
    return (
      <View style={[styles.container, { backgroundColor: colors.bg }]}>
        <View style={styles.inner}>
          <Text style={[styles.title, { color: colors.textPrimary }]}>Senha redefinida</Text>
          <Text style={[styles.subtitle, { color: colors.textSecondary }]}>Sua nova senha já está valendo. Entre com ela para continuar.</Text>
          <TouchableOpacity onPress={() => router.replace('/(auth)/login')} accessibilityRole="button" style={[styles.button, { backgroundColor: colors.brand }]}>
            <Text style={{ color: colors.brandText, fontWeight: '700', letterSpacing: 1 }}>IR PARA O LOGIN</Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  }

  return (
    <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={[styles.container, { backgroundColor: colors.bg }]}>
      <ScrollView contentContainerStyle={styles.inner} keyboardShouldPersistTaps="handled">
        <Text style={[styles.title, { color: colors.textPrimary }]}>Nova senha</Text>
        <Text style={[styles.subtitle, { color: colors.textSecondary }]}>
          {params.token ? 'Escolha sua nova senha.' : 'Cole o código recebido por e-mail e escolha sua nova senha.'}
        </Text>

        {!params.token && (
          <Field label="Código de recuperação" value={token} onChangeText={setToken} placeholder="Código do e-mail" autoCapitalize="none" autoCorrect={false} />
        )}

        <Field label="Nova senha" value={novaSenha} onChangeText={setNovaSenha} placeholder="Mínimo 8 caracteres, 1 letra e 1 número" secureTextEntry textContentType="newPassword" />

        <Field label="Confirmar senha" value={confirmar} onChangeText={setConfirmar} placeholder="Repita a senha" secureTextEntry textContentType="newPassword" />

        {error ? <Text style={{ color: colors.danger, marginTop: 8 }}>{error}</Text> : null}

        <TouchableOpacity onPress={onSubmit} disabled={loading} accessibilityRole="button" style={[styles.button, { backgroundColor: colors.brand, opacity: loading ? 0.8 : 1 }]}>
          {loading ? <ActivityIndicator color={colors.brandText} /> : <Text style={{ color: colors.brandText, fontWeight: '700', letterSpacing: 1 }}>REDEFINIR SENHA</Text>}
        </TouchableOpacity>

        <TouchableOpacity onPress={() => router.replace('/(auth)/login')} accessibilityRole="button" style={{ alignSelf: 'center', marginTop: 16, minHeight: 44, justifyContent: 'center' }}>
          <Text style={{ color: colors.textSecondary, fontSize: 13 }}>Voltar para o login</Text>
        </TouchableOpacity>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  inner: { flexGrow: 1, justifyContent: 'center', paddingHorizontal: 24, paddingVertical: 32 },
  title: { fontSize: 24, fontWeight: '700', marginTop: 16 },
  subtitle: { fontSize: 13, marginBottom: 32 },
  button: { marginTop: 24, borderRadius: 12, height: 48, alignItems: 'center', justifyContent: 'center' },
});
