import React from 'react';
import { View, Text, TouchableOpacity, ScrollView } from 'react-native';
import { useTheme } from '../../src/theme';
import { useAuth } from '../../src/context/AuthContext';
import { useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { authService } from '../../src/services/authService';
import { formatCurrency, getInitials } from '../../src/utils/format';
import { useQuery } from '@tanstack/react-query';
import api from '../../src/services/api';
import { DashboardResumo } from '../../src/types';

export default function Perfil() {
  const colors = useTheme();
  const { usuario, logout } = useAuth();
  const queryClient = useQueryClient();
  const router = useRouter();

  const { data: resumo } = useQuery({ queryKey: ['dashboard-resumo'], queryFn: () => api.get<DashboardResumo>('/dashboard/resumo').then(r => r.data) });

  const handleLogout = () => {
    authService.logout();
    try { queryClient.clear(); } catch {}
    if (logout) logout();
    router.replace('/(auth)/login');
  };

  return (
    <ScrollView contentContainerStyle={{ padding: 16, backgroundColor: colors.bg }}>
      <View style={{ alignItems: 'center', marginBottom: 32 }}>
        <View style={{ width: 80, height: 80, borderRadius: 40, backgroundColor: colors.brand + '26', alignItems: 'center', justifyContent: 'center' }}>
          <Text style={{ color: colors.brand, fontSize: 28, fontWeight: '700' }}>{usuario?.nome ? getInitials(usuario.nome) : ''}</Text>
        </View>
        <Text style={{ color: colors.textPrimary, fontSize: 22, fontWeight: '700', marginTop: 12 }}>{usuario?.nome}</Text>
        <Text style={{ color: colors.textSecondary, fontSize: 14, marginTop: 4 }}>{usuario?.email}</Text>
      </View>

      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 32 }}>
        <View style={{ flex: 1, minWidth: '47%', backgroundColor: colors.card, borderRadius: 10, borderWidth: 1, borderColor: colors.border, padding: 14 }}>
          <Text style={{ color: colors.textSecondary }}>Categorias</Text>
          <Text style={{ color: colors.textPrimary, fontWeight: '700', marginTop: 8 }}>{resumo?.totalCategorias ?? '–'}</Text>
        </View>
        <View style={{ flex: 1, minWidth: '47%', backgroundColor: colors.card, borderRadius: 10, borderWidth: 1, borderColor: colors.border, padding: 14 }}>
          <Text style={{ color: colors.textSecondary }}>Contas</Text>
          <Text style={{ color: colors.textPrimary, fontWeight: '700', marginTop: 8 }}>{resumo?.totalContas ?? '–'}</Text>
        </View>
        <View style={{ flex: 1, minWidth: '47%', backgroundColor: colors.card, borderRadius: 10, borderWidth: 1, borderColor: colors.border, padding: 14 }}>
          <Text style={{ color: colors.textSecondary }}>Metas</Text>
          <Text style={{ color: colors.textPrimary, fontWeight: '700', marginTop: 8 }}>{resumo?.totalMetas ?? '–'}</Text>
        </View>
        <View style={{ flex: 1, minWidth: '47%', backgroundColor: colors.card, borderRadius: 10, borderWidth: 1, borderColor: colors.border, padding: 14 }}>
          <Text style={{ color: colors.textSecondary }}>Contas Fixas</Text>
          <Text style={{ color: colors.textPrimary, fontWeight: '700', marginTop: 8 }}>{resumo?.totalContasFixas ?? '–'}</Text>
        </View>
      </View>

      <TouchableOpacity onPress={handleLogout} style={{ backgroundColor: colors.dangerBg, borderWidth: 1, borderColor: colors.danger, borderRadius: 8, height: 48, alignItems: 'center', justifyContent: 'center' }}>
        <Text style={{ color: colors.danger, fontSize: 15, fontWeight: '600' }}>Sair</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}
