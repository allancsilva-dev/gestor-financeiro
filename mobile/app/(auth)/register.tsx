import React, { useState } from 'react';
import { Text, TouchableOpacity, View } from 'react-native';
import { useRouter } from 'expo-router';
import Constants from 'expo-constants';
import Ionicons from '@expo/vector-icons/Ionicons';
import { useAuth } from '../../src/context/AuthContext';
import { useTheme, radius, spacing, typography } from '../../src/theme';
import { authService } from '../../src/services/authService';
import { camposDeErro, mensagemDeErro } from '../../src/utils/erros';
import { isValidEmail, isValidPassword, normalizarEmail } from '../../src/utils/validate';
import { salvarUltimoEmail } from '../../src/store/ultimoEmail';
import Botao from '../../src/components/ui/Botao';
import CampoSenha from '../../src/components/ui/CampoSenha';
import Field from '../../src/components/ui/Field';
import TelaFluxo from '../../src/components/ui/TelaFluxo';

type Passo = 1 | 2 | 3;
type Campo = 'nome' | 'email' | 'password' | 'confirmPassword' | 'aceitaTermos';
type Erros = Partial<Record<Campo, string>>;

// details do backend → campo da tela
const MAPA_DE_CAMPOS: Record<string, Campo> = {
  nome: 'nome',
  email: 'email',
  password: 'password',
  confirmPassword: 'confirmPassword',
  passwordMatch: 'confirmPassword',
  aceitaTermos: 'aceitaTermos',
};

const PASSO_DO_CAMPO: Record<Campo, Passo> = {
  nome: 1,
  email: 1,
  password: 2,
  confirmPassword: 2,
  aceitaTermos: 3,
};

/**
 * Criação de conta em três passos: identidade, senha e consentimento. Antes era
 * um formulário único com cinco campos e um erro solto acima do botão — o
 * usuário só descobria o que estava errado depois de tentar enviar, um problema
 * de cada vez.
 */
export default function Register() {
  const colors = useTheme();
  const { login } = useAuth();
  const router = useRouter();

  const [passo, setPasso] = useState<Passo>(1);
  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [aceitaTermos, setAceitaTermos] = useState(false);
  const [loading, setLoading] = useState(false);
  const [erros, setErros] = useState<Erros>({});
  const [erroGeral, setErroGeral] = useState<string | null>(null);

  const isLocalE2E = Constants.expoConfig?.extra?.appEnv === 'local-e2e';
  // Em local-e2e o AutoFill do iOS sequestra o campo de senha e só o último
  // caractere digitado sobrevive no TextInput controlado — o registro do
  // financial-critical falhava sempre com a senha truncada. 'none' tira o
  // AutoFill do caminho; fora do e2e o comportamento normal é preservado.
  const passwordTextContentType = isLocalE2E ? 'none' : 'newPassword';

  const limpar = (campo: Campo) =>
    setErros((atual) => (atual[campo] ? { ...atual, [campo]: undefined } : atual));

  const validarIdentidade = (): boolean => {
    const novos: Erros = {};
    if (nome.trim().length < 2) novos.nome = 'Informe seu nome (mínimo 2 caracteres).';
    if (!isValidEmail(email)) novos.email = 'Informe um e-mail válido.';
    setErros(novos);
    return Object.keys(novos).length === 0;
  };

  const validarSenha = (): boolean => {
    const novos: Erros = {};
    if (!isValidPassword(password)) {
      novos.password = 'Senha deve ter no mínimo 8 caracteres, com ao menos 1 letra e 1 número.';
    }
    if (password !== confirmPassword) novos.confirmPassword = 'As senhas não coincidem.';
    setErros(novos);
    return Object.keys(novos).length === 0;
  };

  const continuar = () => {
    setErroGeral(null);
    if (passo === 1) {
      if (!validarIdentidade()) return;
      setPasso(2);
      return;
    }
    if (passo === 2) {
      if (!validarSenha()) return;
      setPasso(3);
    }
  };

  const voltar = () => {
    setErroGeral(null);
    if (passo === 1) { router.back(); return; }
    setPasso((atual) => (atual === 3 ? 2 : 1));
  };

  const criarConta = async () => {
    setErroGeral(null);
    if (!aceitaTermos) {
      setErros({ aceitaTermos: 'É preciso aceitar a política de privacidade para criar a conta.' });
      return;
    }

    const emailNormalizado = normalizarEmail(email);
    try {
      setLoading(true);
      await authService.registrar({
        nome: nome.trim(),
        email: emailNormalizado,
        password,
        confirmPassword,
        aceitaTermos,
      });
      // Conta criada — entra direto e segue para o onboarding
      const user = await login(emailNormalizado, password);
      await salvarUltimoEmail(emailNormalizado);
      router.replace(user.onboardingCompleto ? '/(app)/' : '/onboarding');
    } catch (err) {
      const doBackend = camposDeErro(err, MAPA_DE_CAMPOS);
      setErros(doBackend);
      setErroGeral(mensagemDeErro(err, 'Erro ao criar conta. Tente novamente.'));

      // Campo recusado num passo anterior: volta para onde dá para corrigir
      const primeiro = (Object.keys(doBackend) as Campo[])[0];
      if (primeiro) setPasso(PASSO_DO_CAMPO[primeiro]);
    } finally {
      // Sem `finally` o botão ficava travado quando o cadastro dava certo mas o
      // login seguinte falhava.
      setLoading(false);
    }
  };

  const cabecalho: Record<Passo, { titulo: string; subtitulo: string }> = {
    1: { titulo: 'Criar conta', subtitulo: 'Comece a organizar suas finanças em minutos.' },
    2: { titulo: 'Escolha sua senha', subtitulo: 'Mínimo de 8 caracteres, com ao menos 1 letra e 1 número.' },
    3: { titulo: 'Seus dados, suas regras', subtitulo: 'Antes de criar a conta, veja como tratamos suas informações.' },
  };

  return (
    <TelaFluxo
      titulo={cabecalho[passo].titulo}
      subtitulo={cabecalho[passo].subtitulo}
      passo={passo}
      totalDePassos={3}
      onVoltar={voltar}
      rodape={
        passo === 3 ? (
          <Botao testID="register-submit" titulo="Criar conta" onPress={criarConta} carregando={loading} />
        ) : (
          <>
            <Botao testID="register-continuar" titulo="Continuar" onPress={continuar} />
            <Botao
              titulo="Já tenho conta · Entrar"
              variante="texto"
              onPress={() => router.back()}
            />
          </>
        )
      }
    >
      {passo === 1 ? (
        <View>
          <Field
            testID="register-name"
            label="Nome"
            value={nome}
            onChangeText={(t) => { setNome(t); limpar('nome'); }}
            placeholder="Seu nome"
            autoCapitalize="words"
            textContentType="name"
            error={erros.nome}
          />
          <Field
            testID="register-email"
            label="E-mail"
            value={email}
            onChangeText={(t) => { setEmail(t); limpar('email'); }}
            placeholder="seu@email.com"
            autoCapitalize="none"
            keyboardType="email-address"
            textContentType="emailAddress"
            error={erros.email}
          />
        </View>
      ) : null}

      {passo === 2 ? (
        <View>
          <CampoSenha
            testID="register-password"
            label="Senha"
            value={password}
            onChangeText={(t) => { setPassword(t); limpar('password'); }}
            placeholder="Mínimo 8 caracteres, 1 letra e 1 número"
            textContentType={passwordTextContentType}
            desprotegido={isLocalE2E}
            medidor
            error={erros.password}
          />
          <CampoSenha
            testID="register-confirm-password"
            label="Confirmar senha"
            value={confirmPassword}
            onChangeText={(t) => { setConfirmPassword(t); limpar('confirmPassword'); }}
            placeholder="Repita a senha"
            textContentType={passwordTextContentType}
            desprotegido={isLocalE2E}
            returnKeyType="done"
            error={erros.confirmPassword}
          />
        </View>
      ) : null}

      {passo === 3 ? (
        <View>
          <View style={{ gap: spacing.md, marginBottom: spacing.xl }}>
            {[
              { icone: 'lock-closed-outline' as const, texto: 'Seus dados financeiros ficam na sua conta e não são vendidos.' },
              { icone: 'download-outline' as const, texto: 'Você exporta tudo em CSV quando quiser, em Ajustes.' },
              { icone: 'trash-outline' as const, texto: 'Você apaga a conta e todos os dados a qualquer momento.' },
            ].map((item) => (
              <View key={item.texto} style={{ flexDirection: 'row', alignItems: 'flex-start', gap: spacing.md }}>
                <Ionicons name={item.icone} size={18} color={colors.brandFg} style={{ marginTop: 2 }} />
                <Text style={{ ...typography.body, color: colors.textSecondary, flex: 1 }}>{item.texto}</Text>
              </View>
            ))}
          </View>

          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.xs, minHeight: 44 }}>
            <TouchableOpacity
              onPress={() => { setAceitaTermos((v) => !v); limpar('aceitaTermos'); }}
              accessibilityRole="checkbox"
              accessibilityLabel="Aceito a política de privacidade"
              accessibilityState={{ checked: aceitaTermos }}
              style={{ width: 44, height: 44, alignItems: 'center', justifyContent: 'center' }}
            >
              <View
                style={{
                  width: 22, height: 22, borderRadius: radius.sm - 2, borderWidth: 1.5,
                  alignItems: 'center', justifyContent: 'center',
                  borderColor: aceitaTermos ? colors.brand : colors.border,
                  backgroundColor: aceitaTermos ? colors.brand : 'transparent',
                }}
              >
                {aceitaTermos ? <Ionicons name="checkmark" size={14} color={colors.brandText} /> : null}
              </View>
            </TouchableOpacity>
            <Text style={{ ...typography.body, color: colors.textSecondary, flex: 1 }}>
              Li e aceito a{' '}
              <Text
                accessibilityRole="link"
                onPress={() => router.push('/(auth)/privacidade')}
                style={{ color: colors.brandFg, fontWeight: '600', textDecorationLine: 'underline' }}
              >
                política de privacidade
              </Text>{' '}
              e o tratamento dos meus dados conforme a LGPD.
            </Text>
          </View>

          {erros.aceitaTermos ? (
            <Text
              accessibilityRole="alert"
              accessibilityLiveRegion="assertive"
              style={{ ...typography.meta, color: colors.danger, marginTop: spacing.sm }}
            >
              {erros.aceitaTermos}
            </Text>
          ) : null}
        </View>
      ) : null}

      {erroGeral ? (
        <Text
          accessibilityRole="alert"
          accessibilityLiveRegion="assertive"
          style={{ ...typography.body, color: colors.danger, marginTop: spacing.lg }}
        >
          {erroGeral}
        </Text>
      ) : null}
    </TelaFluxo>
  );
}
