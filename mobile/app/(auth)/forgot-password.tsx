import React, { useState } from 'react';
import { Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import api from '../../src/services/api';
import { useTheme, spacing, typography } from '../../src/theme';
import { mensagemDeErro } from '../../src/utils/erros';
import { isValidEmail, normalizarEmail } from '../../src/utils/validate';
import { lerUltimoEmail } from '../../src/store/ultimoEmail';
import Botao from '../../src/components/ui/Botao';
import Field from '../../src/components/ui/Field';
import TelaFluxo from '../../src/components/ui/TelaFluxo';

export default function ForgotPassword() {
  const colors = useTheme();
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState<string | null>(null);

  React.useEffect(() => {
    lerUltimoEmail().then((salvo) => { if (salvo) setEmail(salvo); });
  }, []);

  const onSubmit = async () => {
    setError(null);
    const emailInformado = normalizarEmail(email);
    if (!emailInformado) return setError('Informe seu e-mail.');
    if (!isValidEmail(emailInformado)) return setError('Informe um e-mail válido.');
    try {
      setLoading(true);
      await api.post('/auth/forgot-password', { email: emailInformado });
      setSuccess(true);
    } catch (err) {
      setError(mensagemDeErro(err, 'Erro ao enviar. Tente novamente.'));
    } finally {
      setLoading(false);
    }
  };

  if (success) {
    return (
      <TelaFluxo
        titulo="E-mail enviado"
        subtitulo="Se o e-mail estiver cadastrado, você receberá instruções para redefinir sua senha."
        onVoltar={() => router.back()}
        rodape={
          <>
            <Botao titulo="Já recebi o código" onPress={() => router.push('/(auth)/reset-password')} />
            <Botao titulo="Voltar para o login" variante="texto" onPress={() => router.back()} />
          </>
        }
      >
        <View />
      </TelaFluxo>
    );
  }

  return (
    <TelaFluxo
      titulo="Esqueceu a senha?"
      subtitulo="Informe seu e-mail para receber as instruções de recuperação."
      onVoltar={() => router.back()}
      rodape={
        <>
          <Botao testID="forgot-submit" titulo="Enviar instruções" onPress={onSubmit} carregando={loading} />
          <Botao titulo="Voltar para o login" variante="texto" onPress={() => router.back()} />
        </>
      }
    >
      <View>
        <Field
          testID="forgot-email"
          label="E-mail"
          value={email}
          onChangeText={setEmail}
          placeholder="seu@email.com"
          autoCapitalize="none"
          keyboardType="email-address"
          autoComplete="email"
          textContentType="emailAddress"
          error={error}
        />
        {error ? null : (
          <Text style={{ ...typography.meta, color: colors.textMuted }}>
            Por segurança, a resposta é a mesma exista ou não uma conta com esse e-mail.
          </Text>
        )}
      </View>
    </TelaFluxo>
  );
}
