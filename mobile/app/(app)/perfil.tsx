import React, { useEffect, useState } from 'react';
import { View, Text, TouchableOpacity, ScrollView, ActivityIndicator, Alert } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { LinearGradient } from 'expo-linear-gradient';
import { useTheme } from '../../src/theme';
import { useAuth } from '../../src/context/AuthContext';
import { useQueryClient, useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { getInitials } from '../../src/utils/format';
import api from '../../src/services/api';
import { DashboardResumo, Usuario } from '../../src/types';
import Card from '../../src/components/ui/Card';
import Field from '../../src/components/ui/Field';

export default function Perfil() {
  const colors = useTheme();
  const { usuario, logout, updateUsuario } = useAuth();
  const queryClient = useQueryClient();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [nome, setNome] = useState(usuario?.nome ?? '');
  const [nomeError, setNomeError] = useState<string | null>(null);
  const [salvandoNome, setSalvandoNome] = useState(false);
  const [senhaAtual, setSenhaAtual] = useState('');
  const [novaSenha, setNovaSenha] = useState('');
  const [senhaError, setSenhaError] = useState<string | null>(null);
  const [salvandoSenha, setSalvandoSenha] = useState(false);

  const { data: resumo } = useQuery({ queryKey: ['dashboard-resumo'], queryFn: () => api.get<DashboardResumo>('/v1/dashboard/resumo').then(r => r.data) });

  useEffect(() => {
    setNome(usuario?.nome ?? '');
  }, [usuario?.nome]);

  const handleLogout = async () => {
    // logout do contexto já revoga o refresh token no servidor e limpa o storage
    await logout();
    try { queryClient.clear(); } catch {}
    router.replace('/(auth)/login');
  };

  const stats: Array<{ label: string; valor: number | undefined; cor: string }> = [
    { label: 'Metas', valor: resumo?.totalMetas, cor: colors.brandFg },
    { label: 'Categorias', valor: resumo?.totalCategorias, cor: colors.brandFg },
    { label: 'Cartões', valor: resumo?.totalContas, cor: colors.success },
    { label: 'Contas Fixas', valor: resumo?.totalContasFixas, cor: colors.brandFg },
  ];

  const salvarNome = async () => {
    setNomeError(null);
    const trimmed = nome.trim();
    if (trimmed.length < 3) { setNomeError('Nome deve ter pelo menos 3 caracteres.'); return; }
    setSalvandoNome(true);
    try {
      const { data } = await api.put<Usuario>('/v1/usuarios/me', { nome: trimmed });
      await updateUsuario(data);
      Alert.alert('Perfil', 'Nome atualizado.');
    } catch (err: any) {
      setNomeError(err?.userMessage ?? 'Erro ao salvar nome.');
    } finally {
      setSalvandoNome(false);
    }
  };

  const salvarSenha = async () => {
    setSenhaError(null);
    if (!senhaAtual) { setSenhaError('Informe a senha atual.'); return; }
    if (novaSenha.length < 8 || !/\d/.test(novaSenha) || !/[A-Za-zÀ-ÿ]/.test(novaSenha)) {
      setSenhaError('Nova senha precisa ter 8 caracteres, 1 letra e 1 número.');
      return;
    }
    setSalvandoSenha(true);
    try {
      await api.put('/v1/usuarios/me/senha', { senhaAtual, novaSenha });
      setSenhaAtual('');
      setNovaSenha('');
      Alert.alert('Perfil', 'Senha alterada.');
    } catch (err: any) {
      setSenhaError(err?.userMessage ?? 'Erro ao alterar senha.');
    } finally {
      setSalvandoSenha(false);
    }
  };

  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.bg }} contentContainerStyle={{ paddingTop: insets.top + 16, padding: 16, paddingBottom: 32 }}>
      <View style={{ alignItems: 'center', marginBottom: 24 }}>
        <LinearGradient
          colors={[colors.brand, colors.brandDeep]}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
          style={{ width: 90, height: 90, borderRadius: 45, padding: 3 }}
        >
          <View style={{ flex: 1, borderRadius: 42, backgroundColor: colors.card, alignItems: 'center', justifyContent: 'center' }}>
            <Text style={{ color: colors.brandFg, fontSize: 28, fontWeight: '700' }}>{usuario?.nome ? getInitials(usuario.nome) : ''}</Text>
          </View>
        </LinearGradient>
        <Text style={{ color: colors.textPrimary, fontSize: 19, fontWeight: '800', marginTop: 12 }}>{usuario?.nome}</Text>
        <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 2 }}>{usuario?.email}</Text>
      </View>

      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginBottom: 24 }}>
        {stats.map(({ label, valor, cor }) => (
          <Card key={label} radius={14} style={{ flexBasis: '46%', flexGrow: 1, alignItems: 'center', paddingVertical: 14, paddingHorizontal: 6 }}>
            <Text style={{ color: cor, fontSize: 18, fontWeight: '800', letterSpacing: -0.3, fontVariant: ['tabular-nums'] }}>{valor ?? '–'}</Text>
            <Text style={{ color: colors.textSecondary, fontSize: 9, textTransform: 'uppercase', letterSpacing: 0.5, marginTop: 3 }}>{label}</Text>
          </Card>
        ))}
      </View>

      <Card radius={16} style={{ marginBottom: 12 }}>
        <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '700', marginBottom: 12 }}>Dados pessoais</Text>
        <Field label="Nome" value={nome} onChangeText={setNome} placeholder="Seu nome" error={nomeError} />
        <TouchableOpacity
          onPress={salvarNome}
          disabled={salvandoNome}
          accessibilityRole="button"
          style={{ height: 48, borderRadius: 12, backgroundColor: colors.brand, alignItems: 'center', justifyContent: 'center' }}
        >
          {salvandoNome ? <ActivityIndicator color={colors.brandText} /> : <Text style={{ color: colors.brandText, fontSize: 15, fontWeight: '700' }}>Salvar nome</Text>}
        </TouchableOpacity>
      </Card>

      <Card radius={16} style={{ marginBottom: 24 }}>
        <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '700', marginBottom: 12 }}>Segurança</Text>
        <Field label="Senha atual" value={senhaAtual} onChangeText={setSenhaAtual} secureTextEntry />
        <Field label="Nova senha" value={novaSenha} onChangeText={setNovaSenha} secureTextEntry error={senhaError} />
        <TouchableOpacity
          onPress={salvarSenha}
          disabled={salvandoSenha}
          accessibilityRole="button"
          style={{ height: 48, borderRadius: 12, borderWidth: 1, borderColor: colors.brand, alignItems: 'center', justifyContent: 'center' }}
        >
          {salvandoSenha ? <ActivityIndicator color={colors.brand} /> : <Text style={{ color: colors.brandFg, fontSize: 15, fontWeight: '700' }}>Alterar senha</Text>}
        </TouchableOpacity>
      </Card>

      <TouchableOpacity
        onPress={handleLogout}
        accessibilityRole="button"
        style={{ backgroundColor: colors.dangerBg, borderRadius: 12, height: 48, alignItems: 'center', justifyContent: 'center' }}
      >
        <Text style={{ color: colors.danger, fontSize: 15, fontWeight: '700' }}>Sair</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}
