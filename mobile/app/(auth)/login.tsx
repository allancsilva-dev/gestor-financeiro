import React, { useEffect, useRef, useState } from 'react';
import { Text, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';
import Constants from 'expo-constants';
import { useAuth } from '../../src/context/AuthContext';
import { useTheme, spacing, typography } from '../../src/theme';
import { camposDeErro, mensagemDeErro } from '../../src/utils/erros';
import { isValidEmail } from '../../src/utils/validate';
import { lerUltimoEmail, salvarUltimoEmail } from '../../src/store/ultimoEmail';
import Botao from '../../src/components/ui/Botao';
import CampoSenha from '../../src/components/ui/CampoSenha';
import Field from '../../src/components/ui/Field';
import TelaFluxo from '../../src/components/ui/TelaFluxo';

type Campo = 'email' | 'password';

export default function Login() {
  const colors = useTheme();
  const { login } = useAuth();
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [erros, setErros] = useState<Partial<Record<Campo, string>>>({});
  const [erroGeral, setErroGeral] = useState<string | null>(null);
  // Foco encadeado pelo teclado: com o teclado aberto o layout sobe e tocar no
  // próximo campo vira acerto de coordenada.
  const senhaRef = useRef<TextInput>(null);

  const isLocalE2E = Constants.expoConfig?.extra?.appEnv === 'local-e2e';

  // Quem já entrou neste aparelho não digita o e-mail de novo
  useEffect(() => {
    lerUltimoEmail().then((salvo) => { if (salvo) setEmail(salvo); });
  }, []);

  const onSubmit = async () => {
    setErroGeral(null);
    const emailInformado = email.trim();
    const novos: Partial<Record<Campo, string>> = {};
    if (!emailInformado) novos.email = 'Informe seu e-mail.';
    else if (!isValidEmail(emailInformado)) novos.email = 'Informe um e-mail válido.';
    if (!password) novos.password = 'Informe sua senha.';
    setErros(novos);
    if (Object.keys(novos).length > 0) return;

    try {
      setLoading(true);
      const user = await login(emailInformado, password);
      await salvarUltimoEmail(emailInformado);
      router.replace(user.onboardingCompleto ? '/(app)/' : '/onboarding');
    } catch (err) {
      setErros(camposDeErro(err, { email: 'email', password: 'password' }));
      setErroGeral(mensagemDeErro(err, 'Erro inesperado. Tente novamente.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <TelaFluxo
      titulo="Gestor Financeiro"
      subtitulo="Entre na sua conta para continuar."
      rodape={
        <>
          <Botao testID="login-submit" titulo="Entrar" onPress={onSubmit} carregando={loading} />
          <Botao
            titulo="Não tem conta? Criar conta"
            variante="texto"
            onPress={() => router.push('/(auth)/register')}
          />
        </>
      }
    >
      <View>
        <Field
          testID="login-email"
          label="E-mail"
          value={email}
          onChangeText={(t) => { setEmail(t); setErros((e) => ({ ...e, email: undefined })); }}
          placeholder="seu@email.com"
          autoCapitalize="none"
          keyboardType="email-address"
          autoComplete="email"
          textContentType="emailAddress"
          returnKeyType="next"
          onSubmitEditing={() => senhaRef.current?.focus()}
          error={erros.email}
        />
        <CampoSenha
          ref={senhaRef}
          testID="login-password"
          label="Senha"
          value={password}
          onChangeText={(t) => { setPassword(t); setErros((e) => ({ ...e, password: undefined })); }}
          placeholder="••••••••"
          autoComplete="password"
          textContentType={isLocalE2E ? 'none' : 'password'}
          desprotegido={isLocalE2E}
          returnKeyType="go"
          onSubmitEditing={onSubmit}
          error={erros.password}
        />

        <Botao
          titulo="Esqueceu a senha?"
          variante="texto"
          onPress={() => router.push('/(auth)/forgot-password')}
          style={{ alignSelf: 'flex-end', paddingHorizontal: 0 }}
        />

        {erroGeral ? (
          <Text
            accessibilityRole="alert"
            accessibilityLiveRegion="assertive"
            style={{ ...typography.body, color: colors.danger, marginTop: spacing.md }}
          >
            {erroGeral}
          </Text>
        ) : null}
      </View>
    </TelaFluxo>
  );
}
