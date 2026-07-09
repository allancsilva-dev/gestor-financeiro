import React from 'react';
import { View, Text, TouchableOpacity, ScrollView } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { LinearGradient } from 'expo-linear-gradient';
import { useTheme } from '../../src/theme';
import { useAuth } from '../../src/context/AuthContext';
import { useQueryClient, useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { authService } from '../../src/services/authService';
import { getInitials } from '../../src/utils/format';
import api from '../../src/services/api';
import { DashboardResumo } from '../../src/types';
import Card from '../../src/components/ui/Card';

export default function Perfil() {
  const colors = useTheme();
  const { usuario, logout } = useAuth();
  const queryClient = useQueryClient();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const { data: resumo } = useQuery({ queryKey: ['dashboard-resumo'], queryFn: () => api.get<DashboardResumo>('/v1/dashboard/resumo').then(r => r.data) });

  const handleLogout = () => {
    authService.logout();
    try { queryClient.clear(); } catch {}
    if (logout) logout();
    router.replace('/(auth)/login');
  };

  const stats: Array<{ label: string; valor: number | undefined; cor: string }> = [
    { label: 'Metas', valor: resumo?.totalMetas, cor: colors.brandFg },
    { label: 'Categorias', valor: resumo?.totalCategorias, cor: colors.brandFg },
    { label: 'Contas', valor: resumo?.totalContas, cor: colors.success },
    { label: 'Contas Fixas', valor: resumo?.totalContasFixas, cor: colors.brandFg },
  ];

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

      <TouchableOpacity
        onPress={handleLogout}
        accessibilityRole="button"
        style={{ backgroundColor: colors.danger, borderRadius: 12, height: 48, alignItems: 'center', justifyContent: 'center' }}
      >
        <Text style={{ color: '#ffffff', fontSize: 15, fontWeight: '700' }}>Sair</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}
