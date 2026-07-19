import React from 'react';
import { View, Text, Modal, TouchableOpacity, ActivityIndicator, ScrollView } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import metricasService from '../services/metricasService';
import { useTheme } from '../theme';
import { formatCurrency } from '../utils/format';
import { MetricaId } from '../types';
import ListRow from './ui/ListRow';

interface ComposicaoMetricaModalProps {
  metrica: MetricaId | null;
  onClose: () => void;
}

// Composição (origens) de uma métrica oficial — extraído da home (PR-F3-06)
// para ser usado também na tela Visão financeira.
export default function ComposicaoMetricaModal({ metrica, onClose }: ComposicaoMetricaModalProps) {
  const colors = useTheme();

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
            {origensQuery.data?.map(origem => (
              <ListRow
                key={`${origem.tipo}-${origem.id}`}
                title={origem.descricao}
                subtitle={origem.tipo}
                value={formatCurrency(Number(origem.valor))}
              />
            ))}
          </ScrollView>
        )}
      </View>
    </Modal>
  );
}
