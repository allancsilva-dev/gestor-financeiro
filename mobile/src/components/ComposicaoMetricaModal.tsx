import React from 'react';
import { View, Text, Modal, TouchableOpacity, ActivityIndicator, ScrollView } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import metricasService from '../services/metricasService';
import { useTheme } from '../theme';
import { formatCurrency } from '../utils/format';
import { MetricaId, NavegacaoOrigem } from '../types';
import ListRow from './ui/ListRow';

interface ComposicaoMetricaModalProps {
  metrica: MetricaId | null;
  onClose: () => void;
}

// Drill-down (PR-F3-08): rota do app para cada destino fornecido pelo
// backend (PR-F3-04). Destino desconhecido → sem navegação.
export function rotaDaNavegacao(nav: NavegacaoOrigem): string | null {
  switch (nav.destino) {
    case 'EXTRATO_CONTA':
      return nav.id != null ? `/more/carteiras?contaId=${nav.id}` : null;
    case 'TRANSACAO':
      return nav.id != null ? `/transacoes?transacaoId=${nav.id}` : null;
    case 'FATURA':
      return '/more/faturas';
    case 'META':
      return '/metas';
    case 'INVESTIMENTO':
      return '/more/investimentos';
    case 'TRANSACOES': {
      const params = new URLSearchParams(nav.filtros ?? {}).toString();
      return params ? `/transacoes?${params}` : '/transacoes';
    }
    default:
      return null;
  }
}

// Composição (origens) de uma métrica oficial — extraído da home (PR-F3-06)
// para ser usado também na tela Visão financeira.
export default function ComposicaoMetricaModal({ metrica, onClose }: ComposicaoMetricaModalProps) {
  const colors = useTheme();
  const router = useRouter();

  const navegar = (nav: NavegacaoOrigem) => {
    const rota = rotaDaNavegacao(nav);
    if (!rota) return;
    onClose();
    router.push(rota as any);
  };

  const origensQuery = useQuery({
    queryKey: ['metricas-origens', metrica],
    queryFn: () => metricasService.listarOrigens(metrica!),
    enabled: metrica != null,
  });

  return (
    <Modal visible={metrica != null} animationType="slide" presentationStyle="pageSheet" onRequestClose={onClose}>
      <View style={{ flex: 1, backgroundColor: colors.bg, paddingTop: 18 }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
          <Text style={{ color: colors.textPrimary, fontSize: 17, fontWeight: '700' }}>Composição da métrica</Text>
          <TouchableOpacity onPress={onClose} accessibilityRole="button" style={{ minHeight: 44, justifyContent: 'center' }}>
            <Text style={{ color: colors.brandFg, fontWeight: '700' }}>Fechar</Text>
          </TouchableOpacity>
        </View>
        {origensQuery.isLoading ? (
          <ActivityIndicator color={colors.brand} style={{ marginTop: 48 }} />
        ) : origensQuery.isError ? (
          <TouchableOpacity onPress={() => origensQuery.refetch()} style={{ alignItems: 'center', padding: 48 }}>
            <Text style={{ color: colors.brandFg, fontWeight: '700' }}>Tentar novamente</Text>
          </TouchableOpacity>
        ) : (origensQuery.data?.length ?? 0) === 0 ? (
          <Text style={{ color: colors.textSecondary, textAlign: 'center', padding: 48 }}>Nenhuma origem compõe esta métrica.</Text>
        ) : (
          <ScrollView contentContainerStyle={{ padding: 16 }}>
            {origensQuery.data?.map(origem => {
              // Linha só aparenta ser clicável com destino válido (PR-F3-08)
              const rota = origem.navegacao ? rotaDaNavegacao(origem.navegacao) : null;
              return (
                <ListRow
                  key={`${origem.tipo}-${origem.id}`}
                  title={origem.descricao}
                  subtitle={origem.tipo}
                  onPress={rota ? () => navegar(origem.navegacao!) : undefined}
                  dica={rota ? 'Abre o detalhe da origem' : undefined}
                  trailing={
                    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                      <Text style={{ color: colors.textPrimary, fontSize: 14, fontWeight: '700', fontVariant: ['tabular-nums'] }}>
                        {formatCurrency(Number(origem.valor))}
                      </Text>
                      {rota != null && <Text style={{ color: colors.textSecondary, fontSize: 16 }}>›</Text>}
                    </View>
                  }
                />
              );
            })}
          </ScrollView>
        )}
      </View>
    </Modal>
  );
}
