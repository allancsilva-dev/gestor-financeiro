import React, { useState } from 'react';
import { Text, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import Constants from 'expo-constants';
import api from '../../src/services/api';
import { useTheme, spacing, typography } from '../../src/theme';
import { mensagemDeErro } from '../../src/utils/erros';
import { isValidPassword } from '../../src/utils/validate';
import Botao from '../../src/components/ui/Botao';
import CampoSenha from '../../src/components/ui/CampoSenha';
import Field from '../../src/components/ui/Field';
import TelaFluxo from '../../src/components/ui/TelaFluxo';

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
  const [erroToken, setErroToken] = useState<string | null>(null);
  const [erroSenha, setErroSenha] = useState<string | null>(null);
  const [erroConfirmar, setErroConfirmar] = useState<string | null>(null);
  const [erroGeral, setErroGeral] = useState<string | null>(null);

  const isLocalE2E = Constants.expoConfig?.extra?.appEnv === 'local-e2e';

  const onSubmit = async () => {
    setErroGeral(null);
    const semToken = !token.trim();
    const senhaFraca = !isValidPassword(novaSenha);
    const naoConfere = novaSenha !== confirmar;
    setErroToken(semToken ? 'Cole o código recebido por e-mail.' : null);
    setErroSenha(senhaFraca ? 'Senha deve ter no mínimo 8 caracteres, com ao menos 1 letra e 1 número.' : null);
    setErroConfirmar(naoConfere ? 'As senhas não coincidem.' : null);
    if (semToken || senhaFraca || naoConfere) return;

    try {
      setLoading(true);
      await api.post('/auth/reset-password', { token: token.trim(), novaSenha });
      setSuccess(true);
    } catch (err) {
      setErroGeral(mensagemDeErro(err, 'Erro ao redefinir a senha. Tente novamente.'));
    } finally {
      setLoading(false);
    }
  };

  if (success) {
    return (
      <TelaFluxo
        titulo="Senha redefinida"
        subtitulo="Sua nova senha já está valendo. Entre com ela para continuar."
        rodape={<Botao titulo="Ir para o login" onPress={() => router.replace('/(auth)/login')} />}
      >
        <View />
      </TelaFluxo>
    );
  }

  return (
    <TelaFluxo
      titulo="Nova senha"
      subtitulo={params.token
        ? 'Escolha sua nova senha.'
        : 'Cole o código recebido por e-mail e escolha sua nova senha.'}
      onVoltar={() => router.replace('/(auth)/login')}
      rodape={
        <>
          <Botao testID="reset-submit" titulo="Redefinir senha" onPress={onSubmit} carregando={loading} />
          <Botao titulo="Voltar para o login" variante="texto" onPress={() => router.replace('/(auth)/login')} />
        </>
      }
    >
      <View>
        {!params.token && (
          <Field
            testID="reset-token"
            label="Código de recuperação"
            value={token}
            onChangeText={(t) => { setToken(t); setErroToken(null); }}
            placeholder="Código do e-mail"
            autoCapitalize="none"
            autoCorrect={false}
            error={erroToken}
          />
        )}

        <CampoSenha
          testID="reset-password"
          label="Nova senha"
          value={novaSenha}
          onChangeText={(t) => { setNovaSenha(t); setErroSenha(null); }}
          placeholder="Mínimo 8 caracteres, 1 letra e 1 número"
          textContentType={isLocalE2E ? 'none' : 'newPassword'}
          desprotegido={isLocalE2E}
          medidor
          error={erroSenha}
        />

        <CampoSenha
          testID="reset-confirm-password"
          label="Confirmar senha"
          value={confirmar}
          onChangeText={(t) => { setConfirmar(t); setErroConfirmar(null); }}
          placeholder="Repita a senha"
          textContentType={isLocalE2E ? 'none' : 'newPassword'}
          desprotegido={isLocalE2E}
          error={erroConfirmar}
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
