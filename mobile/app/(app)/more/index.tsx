import React from 'react';
import { View, Text, TouchableOpacity, Alert, Platform, ScrollView } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import * as Sharing from 'expo-sharing';
import { File, Paths } from 'expo-file-system';
import { useTheme } from '../../../src/theme';
import api from '../../../src/services/api';
import Card from '../../../src/components/ui/Card';

// Grid 2 colunas (DESIGN.md) — tile 44 com tint da cor do item, label + subtítulo
const itens = [
  { label: 'Contas',       sub: 'Saldos e dinheiro', rota: '/more/carteiras',    icone: '₿',  cor: '#7c5cfc' },
  { label: 'Contas Fixas', sub: 'Mensais',          rota: '/more/contas-fixas', icone: '📅', cor: '#8b2fff' },
  { label: 'Orçamentos',   sub: 'Por categoria',    rota: '/more/orcamentos',   icone: '📊', cor: '#ffa502' },
  { label: 'Cartão',       sub: 'Faturas',          rota: '/more/faturas',      icone: '💳', cor: '#ff4757' },
  { label: 'Relatórios',   sub: 'Gráficos',         rota: '/more/relatorios',   icone: '📋', cor: '#2ed573' },
  { label: 'Categorias',   sub: 'Organizar',        rota: '/more/categorias',   icone: '🏷', cor: '#ff6b81' },
  { label: 'Cartões',      sub: 'Crédito e débito', rota: '/more/contas',       icone: '💳', cor: '#1e90ff' },
  { label: 'Perfil',       sub: 'Nome e segurança', rota: '/(app)/perfil',      icone: '👤', cor: '#7c5cfc' },
  { label: 'Entrada por IA', sub: 'Em breve',       rota: null,                 icone: '🤖', cor: '#8b2fff', desabilitado: true },
  { label: 'Exportar Dados', sub: 'CSV',            rota: null,                 icone: '📥', cor: '#7c5cfc', acao: 'exportar' },
];

// Baixa o CSV pela API autenticada e compartilha o arquivo — nunca expor URL da API
const handleExport = async () => {
  try {
    const { data: csv } = await api.get<string>('/v1/exportar/completo', { responseType: 'text' });

    if (Platform.OS === 'web') {
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'dados-completos.csv';
      a.click();
      URL.revokeObjectURL(url);
      return;
    }

    const file = new File(Paths.cache, 'dados-completos.csv');
    file.create({ overwrite: true });
    file.write(csv);

    if (await Sharing.isAvailableAsync()) {
      await Sharing.shareAsync(file.uri, { mimeType: 'text/csv', dialogTitle: 'Exportar dados' });
    } else {
      Alert.alert('Exportar Dados', `Arquivo salvo em:\n${file.uri}`);
    }
  } catch (err: any) {
    Alert.alert('Exportar Dados', err?.userMessage ?? 'Não foi possível exportar. Tente novamente.');
  }
};

export default function MoreScreen() {
  const colors = useTheme();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const navegar = (rota: string | null, acao?: string) => {
    if (acao === 'exportar') { handleExport(); return; }
    if (!rota) return;
    router.push(rota as any);
  };

  return (
    <ScrollView style={{ flex: 1, backgroundColor: colors.bg }} contentContainerStyle={{ paddingTop: insets.top + 16, paddingHorizontal: 16, paddingBottom: 32 }}>
      <Text style={{ color: colors.textPrimary, fontSize: 23, fontWeight: '800', letterSpacing: -0.4 }}>Mais</Text>
      <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4, marginBottom: 16 }}>Ferramentas e configurações</Text>

      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 12 }}>
        {itens.map((it, idx) => (
          <TouchableOpacity
            key={idx}
            onPress={() => navegar(it.rota, (it as any).acao)}
            activeOpacity={it.desabilitado ? 1 : 0.7}
            accessibilityRole="button"
            accessibilityLabel={it.desabilitado ? `${it.label} (em breve)` : it.label}
            disabled={it.desabilitado}
            style={{ flexBasis: '47%', flexGrow: 0 }}
          >
            <Card radius={16} style={{ opacity: it.desabilitado ? 0.55 : 1 }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <View style={{
                  width: 44, height: 44, borderRadius: 12, marginBottom: 12,
                  backgroundColor: it.cor + '24', alignItems: 'center', justifyContent: 'center',
                }}>
                  <Text style={{ fontSize: 22 }}>{it.icone}</Text>
                </View>
                {it.desabilitado && (
                  <View style={{ backgroundColor: colors.warningBg, paddingHorizontal: 6, paddingVertical: 3, borderRadius: 999 }}>
                    <Text style={{ color: colors.warning, fontSize: 8, fontWeight: '700', textTransform: 'uppercase', letterSpacing: 0.4 }}>Em breve</Text>
                  </View>
                )}
              </View>
              <Text style={{ color: colors.textPrimary, fontSize: 14, fontWeight: '700' }}>{it.label}</Text>
              <Text style={{ color: colors.textSecondary, fontSize: 11, marginTop: 2 }}>{it.sub}</Text>
            </Card>
          </TouchableOpacity>
        ))}
      </View>
    </ScrollView>
  );
}
