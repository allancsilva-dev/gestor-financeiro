import React, { useEffect, useState } from 'react';
import { View, Text, ScrollView, Alert } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import {
  useTheme, useTabBarSpace, radius, screenPadding, spacing, typography,
} from '../../src/theme';
import { useAuth } from '../../src/context/AuthContext';
import { getInitials } from '../../src/utils/format';
import { camposDeErro, mensagemDeErro } from '../../src/utils/erros';
import api from '../../src/services/api';
import { Usuario } from '../../src/types';
import Botao from '../../src/components/ui/Botao';
import CabecalhoSubTela from '../../src/components/ui/CabecalhoSubTela';
import CampoSenha from '../../src/components/ui/CampoSenha';
import Card from '../../src/components/ui/Card';
import Field from '../../src/components/ui/Field';
import { isValidPassword } from '../../src/utils/validate';

/**
 * Espessura do anel do avatar. O gradiente é a moldura e o círculo de dentro
 * cobre o miolo — a medida é traço, não espaçamento, então não sai da escala.
 */
const ANEL_DO_AVATAR = 3;

type CampoDeSenha = 'atual' | 'nova';

const MAPA_DE_SENHA: Record<string, CampoDeSenha> = {
  senhaAtual: 'atual',
  novaSenha: 'nova',
};

export default function Perfil() {
  const colors = useTheme();
  const tabBarSpace = useTabBarSpace();
  const { usuario, logout, updateUsuario } = useAuth();
  const queryClient = useQueryClient();
  const router = useRouter();
  const [nome, setNome] = useState(usuario?.nome ?? '');
  const [nomeError, setNomeError] = useState<string | null>(null);
  const [salvandoNome, setSalvandoNome] = useState(false);
  const [senhaAtual, setSenhaAtual] = useState('');
  const [novaSenha, setNovaSenha] = useState('');
  const [errosSenha, setErrosSenha] = useState<Partial<Record<CampoDeSenha, string>>>({});
  const [erroSenhaGeral, setErroSenhaGeral] = useState<string | null>(null);
  const [salvandoSenha, setSalvandoSenha] = useState(false);

  useEffect(() => {
    setNome(usuario?.nome ?? '');
  }, [usuario?.nome]);

  const handleLogout = async () => {
    Alert.alert('Sair da conta?', 'Você precisará entrar novamente para acessar seus dados.', [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Sair',
        style: 'destructive',
        onPress: async () => {
          // logout do contexto já revoga o refresh token no servidor e limpa o storage
          await logout();
          try { queryClient.clear(); } catch {}
          router.replace('/(auth)/login');
        },
      },
    ]);
  };

  const salvarNome = async () => {
    setNomeError(null);
    const trimmed = nome.trim();
    if (trimmed.length < 3) { setNomeError('Nome deve ter pelo menos 3 caracteres.'); return; }
    setSalvandoNome(true);
    try {
      const { data } = await api.put<Usuario>('/v1/usuarios/me', { nome: trimmed });
      await updateUsuario(data);
      Alert.alert('Perfil', 'Nome atualizado.');
    } catch (err) {
      setNomeError(mensagemDeErro(err, 'Erro ao salvar nome.'));
    } finally {
      setSalvandoNome(false);
    }
  };

  const salvarSenha = async () => {
    setErrosSenha({});
    setErroSenhaGeral(null);
    const local: Partial<Record<CampoDeSenha, string>> = {};
    if (!senhaAtual) local.atual = 'Informe a senha atual.';
    if (!isValidPassword(novaSenha)) local.nova = 'Nova senha precisa ter 8 caracteres, 1 letra e 1 número.';
    if (Object.keys(local).length > 0) { setErrosSenha(local); return; }

    setSalvandoSenha(true);
    try {
      await api.put('/v1/usuarios/me/senha', { senhaAtual, novaSenha });
      setSenhaAtual('');
      setNovaSenha('');
      Alert.alert('Perfil', 'Senha alterada.');
    } catch (err) {
      // "Senha atual incorreta" é BusinessException no backend: chega sem campo,
      // então mora na faixa geral. Antes caía no `error` do campo "Nova senha" —
      // o usuário lia que a senha nova estava errada quando o problema era a atual.
      setErrosSenha(camposDeErro(err, MAPA_DE_SENHA));
      setErroSenhaGeral(mensagemDeErro(err, 'Erro ao alterar senha.'));
    } finally {
      setSalvandoSenha(false);
    }
  };

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.bg }}
      contentContainerStyle={{ paddingBottom: tabBarSpace }}
      keyboardShouldPersistTaps="handled"
    >
      <CabecalhoSubTela titulo="Perfil" />

      <View style={{ paddingHorizontal: screenPadding }}>
        <View style={{ alignItems: 'center', marginBottom: spacing.xxl }}>
          <LinearGradient
            colors={[colors.brand, colors.brandDeep]}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 1 }}
            style={{ width: 90, height: 90, borderRadius: radius.pill, padding: ANEL_DO_AVATAR }}
          >
            <View style={{
              flex: 1, borderRadius: radius.pill, backgroundColor: colors.card,
              alignItems: 'center', justifyContent: 'center',
            }}>
              <Text style={{ ...typography.subDisplay, color: colors.brandFg }}>
                {usuario?.nome ? getInitials(usuario.nome) : ''}
              </Text>
            </View>
          </LinearGradient>
          <Text style={{ ...typography.section, color: colors.textPrimary, marginTop: spacing.md }}>{usuario?.nome}</Text>
          <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.xxs }}>{usuario?.email}</Text>
        </View>

        <Card radius={radius.lg} style={{ marginBottom: spacing.md }}>
          <Text style={{ ...typography.cardTitle, color: colors.textPrimary, marginBottom: spacing.md }}>Dados pessoais</Text>
          <Field label="Nome" value={nome} onChangeText={setNome} placeholder="Seu nome" error={nomeError} />
          <Botao titulo="Salvar nome" onPress={salvarNome} carregando={salvandoNome} />
        </Card>

        <Card radius={radius.lg} style={{ marginBottom: spacing.xxl }}>
          <Text style={{ ...typography.cardTitle, color: colors.textPrimary, marginBottom: spacing.md }}>Segurança</Text>
          {/* `ui/CampoSenha`, não `Field secureTextEntry`: esta era a única tela do
              app onde a senha se digitava às cegas, sem olho nem medidor. */}
          <CampoSenha
            label="Senha atual"
            value={senhaAtual}
            onChangeText={setSenhaAtual}
            autoCapitalize="none"
            error={errosSenha.atual}
          />
          <CampoSenha
            label="Nova senha"
            value={novaSenha}
            onChangeText={setNovaSenha}
            autoCapitalize="none"
            medidor
            error={errosSenha.nova}
          />
          {!!erroSenhaGeral && (
            <Text
              accessibilityRole="alert"
              accessibilityLiveRegion="polite"
              style={{ ...typography.meta, color: colors.danger, marginBottom: spacing.md }}
            >
              {erroSenhaGeral}
            </Text>
          )}
          <Botao titulo="Alterar senha" variante="secundario" onPress={salvarSenha} carregando={salvandoSenha} />
        </Card>

        <Botao titulo="Sair" variante="perigo" onPress={handleLogout} />
      </View>
    </ScrollView>
  );
}
