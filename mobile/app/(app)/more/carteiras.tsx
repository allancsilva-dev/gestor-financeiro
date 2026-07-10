import React, { useMemo, useState } from 'react';
import { View, Text, TouchableOpacity, FlatList, Modal, ScrollView, TextInput, ActivityIndicator } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery, useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { carteiraService } from '../../../src/services/carteiraService';
import { TIPO_CARTEIRA_LABEL, TIPO_MOVIMENTO_LABEL, formatCurrency, formatDateTime, parseCurrencyBR, maskCurrencyInput } from '../../../src/utils/format';
import { Carteira, CarteiraRequest, TipoCarteira } from '../../../src/types';
import { useTheme } from '../../../src/theme';
import SkeletonBox from '../../../src/components/ui/SkeletonBox';

// Extrato do ledger — fonte de confiança do saldo da conta
function ExtratoModal({ carteira, onClose }: { carteira: Carteira | null; onClose: () => void }) {
  const colors = useTheme();
  const {
    data,
    isLoading,
    isError,
    refetch,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery({
    queryKey: ['carteira-movimentos', carteira?.id],
    queryFn: ({ pageParam }) => carteiraService.listarMovimentos(carteira!.id, pageParam),
    initialPageParam: 0,
    getNextPageParam: last => (last.number + 1 < last.totalPages ? last.number + 1 : undefined),
    enabled: carteira != null,
  });

  const movimentos = useMemo(() => data?.pages.flatMap(p => p.content) ?? [], [data]);

  return (
    <Modal visible={carteira != null} animationType="slide" presentationStyle="pageSheet" onRequestClose={onClose}>
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
          <View style={{ flex: 1 }}>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700' }} numberOfLines={1}>{carteira?.nome}</Text>
            <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 2 }}>
              Extrato · saldo {formatCurrency(Number(carteira?.saldo ?? 0))}
            </Text>
          </View>
          <TouchableOpacity onPress={onClose} accessibilityRole="button" style={{ minHeight: 44, justifyContent: 'center' }}>
            <Text style={{ color: colors.brand, fontSize: 15, fontWeight: '600' }}>Fechar</Text>
          </TouchableOpacity>
        </View>

        <FlatList
          data={movimentos}
          keyExtractor={m => m.id.toString()}
          contentContainerStyle={{ padding: 16, paddingBottom: 32 }}
          onEndReached={() => { if (hasNextPage && !isFetchingNextPage) fetchNextPage(); }}
          onEndReachedThreshold={0.4}
          ListFooterComponent={isFetchingNextPage ? (
            <View style={{ paddingVertical: 16 }}><ActivityIndicator color={colors.brand} /></View>
          ) : null}
          ListEmptyComponent={isLoading ? (
            <View style={{ gap: 8 }}>
              {[1, 2, 3, 4, 5].map(i => <SkeletonBox key={i} width="100%" height={64} />)}
            </View>
          ) : isError ? (
            <View style={{ alignItems: 'center', padding: 48 }}>
              <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>Erro ao carregar extrato</Text>
              <TouchableOpacity onPress={() => refetch()} accessibilityRole="button" style={{ marginTop: 8, minHeight: 44, justifyContent: 'center' }}>
                <Text style={{ color: colors.brand, fontWeight: '600' }}>Tentar novamente</Text>
              </TouchableOpacity>
            </View>
          ) : (
            <View style={{ alignItems: 'center', padding: 48 }}>
              <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>Sem movimentos ainda</Text>
              <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4, textAlign: 'center' }}>
                Transações e ajustes nesta conta aparecem aqui.
              </Text>
            </View>
          )}
          renderItem={({ item: m }) => {
            const credita = Number(m.valorAssinado) >= 0;
            return (
              <View style={{ backgroundColor: colors.card, borderRadius: 12, borderWidth: 1, borderColor: colors.border, padding: 12, marginBottom: 8 }}>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 8 }}>
                  <Text style={{ color: colors.textPrimary, fontSize: 14, fontWeight: '600', flex: 1 }} numberOfLines={1}>
                    {m.descricao || TIPO_MOVIMENTO_LABEL[m.tipo]}
                  </Text>
                  <Text style={{ color: credita ? colors.success : colors.danger, fontSize: 14, fontWeight: '700', fontVariant: ['tabular-nums'] }}>
                    {credita ? '+' : '−'} {formatCurrency(Math.abs(Number(m.valorAssinado)))}
                  </Text>
                </View>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 8, marginTop: 4 }}>
                  <Text style={{ color: colors.textSecondary, fontSize: 11 }}>
                    {TIPO_MOVIMENTO_LABEL[m.tipo]} · {formatDateTime(m.dataMovimento)}
                  </Text>
                  <Text style={{ color: colors.textSecondary, fontSize: 11, fontVariant: ['tabular-nums'] }}>
                    Saldo: {formatCurrency(Number(m.saldoResultante))}
                  </Text>
                </View>
              </View>
            );
          }}
        />
      </View>
    </Modal>
  );
}

export default function CarteirasScreen() {
  const colors = useTheme();
  const insets = useSafeAreaInsets();
  const queryClient = useQueryClient();
  const [modalVisible, setModalVisible] = useState(false);
  const [extratoDe, setExtratoDe] = useState<Carteira | null>(null);
  const [nome, setNome] = useState('');
  const [tipo, setTipo] = useState<TipoCarteira | null>('DINHEIRO');
  const [saldo, setSaldo] = useState('');
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
      setNome(''); setSaldo(''); setTipo('DINHEIRO');
    },
    onError: (err: any) => {
      setNomeError(err?.userMessage ?? 'Erro ao criar conta.');
    },
  });

  const handleSalvar = async () => {
    setNomeError(null); setTipoError(null); setSaldoError(null);
    let hasError = false;
    if (!nome.trim()) { setNomeError('Nome obrigatório.'); hasError = true; }
    if (!tipo) { setTipoError('Tipo obrigatório.'); hasError = true; }
    const v = parseCurrencyBR(saldo || '0');
    if (isNaN(v) || v < 0) { setSaldoError('Saldo deve ser >= 0.'); hasError = true; }
    if (hasError) return;
    const req: CarteiraRequest = { nome: nome.trim(), tipo: tipo as TipoCarteira, saldo: Number(v) };
    try {
      await criarMutation.mutateAsync(req);
    } catch (err: any) {
      setNomeError(err?.userMessage ?? 'Erro ao criar conta.');
    }
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={{ paddingTop: insets.top + 16, paddingHorizontal: 16, paddingBottom: 12 }}>
        <Text style={{ color: colors.textPrimary, fontSize: 23, fontWeight: '800', letterSpacing: -0.4 }}>Contas</Text>
        <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4 }}>Onde seu dinheiro está guardado</Text>
      </View>

      {isLoading ? (
        <View style={{ padding: 16 }}>
          {[1,2,3].map(i => <SkeletonBox key={i} width="100%" height={64} />)}
        </View>
      ) : isError ? (
        <View style={{ alignItems: 'center', padding: 48 }}>
          <Text style={{ color: colors.textSecondary }}>Erro ao carregar contas</Text>
          <TouchableOpacity onPress={() => refetch()} style={{ marginTop: 8 }}>
            <Text style={{ color: colors.brand }}>Tentar novamente</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <FlatList
          data={data?.content ?? []}
          keyExtractor={item => item.id.toString()}
          renderItem={({ item: c }) => (
            <TouchableOpacity
              onPress={() => setExtratoDe(c)}
              activeOpacity={0.7}
              accessibilityRole="button"
              accessibilityLabel={`Ver extrato da conta ${c.nome}`}
              style={{ backgroundColor: colors.card, borderRadius: 12, borderWidth: 1, borderColor: colors.border, padding: 14, marginBottom: 8, marginHorizontal: 16 }}
            >
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                <View style={{ flex: 1 }}>
                  <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>{c.nome}</Text>
                  <Text style={{ color: colors.textSecondary, fontSize: 12 }}>{TIPO_CARTEIRA_LABEL[c.tipo]}</Text>
                </View>
                <Text style={{ color: colors.textSecondary, fontSize: 12 }}>Extrato ›</Text>
              </View>
              <Text style={{ color: colors.textPrimary, fontSize: 18, fontWeight: '700', marginTop: 8 }}>{formatCurrency(Number(c.saldo ?? 0))}</Text>
              {c.banco && <Text style={{ color: colors.textMuted, fontSize: 11, marginTop: 6 }}>{c.banco}</Text>}
            </TouchableOpacity>
          )}
          ListEmptyComponent={() => (
            <View style={{ alignItems: 'center', padding: 48 }}>
              <Text style={{ color: colors.textSecondary }}>Nenhuma conta encontrada</Text>
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

      <ExtratoModal carteira={extratoDe} onClose={() => setExtratoDe(null)} />

      <Modal visible={modalVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity onPress={() => { setModalVisible(false); setNome(''); setSaldo(''); setTipo('DINHEIRO'); setNomeError(null); setTipoError(null); setSaldoError(null); }}>
              <Text style={{ color: colors.brand, fontSize: 15 }}>Cancelar</Text>
            </TouchableOpacity>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '600' }}>Nova Conta</Text>
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
            <TextInput value={saldo} onChangeText={(t) => setSaldo(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />
            {saldoError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{saldoError}</Text>}
          </ScrollView>
        </View>
      </Modal>
    </View>
  );
}
