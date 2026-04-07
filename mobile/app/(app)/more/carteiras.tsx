import React, { useState } from 'react';
import { View, Text, TouchableOpacity, FlatList, Modal, ScrollView, TextInput, ActivityIndicator } from 'react-native';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { carteiraService } from '../../../src/services/carteiraService';
import { TIPO_CARTEIRA_LABEL, formatCurrency } from '../../../src/utils/format';
import { Carteira, CarteiraRequest, TipoCarteira } from '../../../src/types';
import { useTheme } from '../../../src/theme';
import SkeletonBox from '../../../src/components/ui/SkeletonBox';

export default function CarteirasScreen() {
  const colors = useTheme();
  const queryClient = useQueryClient();
  const [modalVisible, setModalVisible] = useState(false);
  const [nome, setNome] = useState('');
  const [tipo, setTipo] = useState<TipoCarteira | null>('DINHEIRO');
  const [saldo, setSaldo] = useState('0');
  const [nomeError, setNomeError] = useState<string | null>(null);
  const [tipoError, setTipoError] = useState<string | null>(null);
  const [saldoError, setSaldoError] = useState<string | null>(null);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['carteiras'],
    queryFn: () => carteiraService.listar(),
  });

  const criarMutation = useMutation({
    mutationFn: (req: CarteiraRequest) => carteiraService.criar(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['carteiras'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-resumo'] });
      setModalVisible(false);
      setNome(''); setSaldo('0'); setTipo('DINHEIRO');
    },
  });

  const handleSalvar = async () => {
    setNomeError(null); setTipoError(null); setSaldoError(null);
    let hasError = false;
    if (!nome.trim()) { setNomeError('Nome obrigatório.'); hasError = true; }
    if (!tipo) { setTipoError('Tipo obrigatório.'); hasError = true; }
    const v = parseFloat(saldo.replace(/\./g, '').replace(/,/g, '.'));
    if (isNaN(v) || v < 0) { setSaldoError('Saldo deve ser >= 0.'); hasError = true; }
    if (hasError) return;
    const req: CarteiraRequest = { nome: nome.trim(), tipo: tipo as TipoCarteira, saldo: Number(v) };
    try {
      await criarMutation.mutateAsync(req);
      // criarMutation.onSuccess already closes modal and clears fields
    } catch (err: any) {
      // display error via mutation.onError
    }
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={{ padding: 16 }}>
        <Text style={{ color: colors.textPrimary, fontSize: 18, fontWeight: '700' }}>Carteiras</Text>
      </View>

      {isLoading ? (
        <View style={{ padding: 16 }}>
          {[1,2,3].map(i => <SkeletonBox key={i} width="100%" height={64} />)}
        </View>
      ) : isError ? (
        <View style={{ alignItems: 'center', padding: 48 }}>
          <Text style={{ color: colors.textSecondary }}>Erro ao carregar carteiras</Text>
          <TouchableOpacity onPress={() => refetch()} style={{ marginTop: 8 }}>
            <Text style={{ color: colors.brand }}>Tentar novamente</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <FlatList
          data={data?.content ?? []}
          keyExtractor={item => item.id.toString()}
          renderItem={({ item: c }) => (
            <View style={{ backgroundColor: colors.card, borderRadius: 12, borderWidth: 1, borderColor: colors.border, padding: 14, marginBottom: 8, marginHorizontal: 16 }}>
              <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>{c.nome}</Text>
              <Text style={{ color: colors.textSecondary, fontSize: 12 }}>{TIPO_CARTEIRA_LABEL[c.tipo]}</Text>
              <Text style={{ color: colors.textPrimary, fontSize: 18, fontWeight: '700', marginTop: 8 }}>{formatCurrency(Number(c.saldo ?? 0))}</Text>
              {c.banco && <Text style={{ color: colors.textMuted, fontSize: 11, marginTop: 6 }}>{c.banco}</Text>}
            </View>
          )}
          ListEmptyComponent={() => (
            <View style={{ alignItems: 'center', padding: 48 }}>
              <Text style={{ color: colors.textSecondary }}>Nenhuma carteira encontrada</Text>
            </View>
          )}
        />
      )}

      <TouchableOpacity
        onPress={() => setModalVisible(true)}
        style={{ position: 'absolute', bottom: 24, right: 16, width: 56, height: 56, borderRadius: 28, backgroundColor: colors.brand, alignItems: 'center', justifyContent: 'center' }}
      >
        <Text style={{ color: colors.brandText, fontSize: 28, lineHeight: 30 }}>+</Text>
      </TouchableOpacity>

      <Modal visible={modalVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity onPress={() => { setModalVisible(false); setNome(''); setSaldo('0'); setTipo('DINHEIRO'); setNomeError(null); setTipoError(null); setSaldoError(null); }}>
              <Text style={{ color: colors.brand, fontSize: 15 }}>Cancelar</Text>
            </TouchableOpacity>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '600' }}>Nova Carteira</Text>
            <TouchableOpacity onPress={handleSalvar} disabled={criarMutation.status === 'pending'}>
              {criarMutation.status === 'pending' ? <ActivityIndicator color={colors.brand} size="small" /> : <Text style={{ color: colors.brand, fontSize: 15, fontWeight: '600' }}>Salvar</Text>}
            </TouchableOpacity>
          </View>
          <ScrollView contentContainerStyle={{ padding: 16 }}>
            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Nome</Text>
            <TextInput value={nome} onChangeText={setNome} placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />
            {nomeError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{nomeError}</Text>}

            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Tipo</Text>
            <View style={{ flexDirection: 'row', gap: 8, marginBottom: 8 }}>
              {(['DINHEIRO','CONTA_BANCARIA','POUPANCA'] as TipoCarteira[]).map(t => (
                <TouchableOpacity key={t} onPress={() => setTipo(t)} style={{ paddingHorizontal: 12, paddingVertical: 6, borderRadius: 20, backgroundColor: tipo === t ? colors.brand + '26' : colors.card, borderWidth: 1, borderColor: tipo === t ? colors.brand : colors.border }}>
                  <Text style={{ color: tipo === t ? colors.brand : colors.textSecondary }}>{TIPO_CARTEIRA_LABEL[t]}</Text>
                </TouchableOpacity>
              ))}
            </View>
            {tipoError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{tipoError}</Text>}

            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Saldo inicial</Text>
            <TextInput value={saldo} onChangeText={setSaldo} keyboardType="decimal-pad" placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />
            {saldoError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{saldoError}</Text>}
          </ScrollView>
        </View>
      </Modal>
    </View>
  );
}
