import React, { useEffect, useMemo, useRef, useState } from 'react';
import { View, Text, TouchableOpacity, FlatList, Modal, ScrollView, TextInput, ActivityIndicator } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useLocalSearchParams } from 'expo-router';
import { useQuery, useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import contaFinanceiraService, { contaGerenciada } from '../../../src/services/contaFinanceiraService';
import { TIPO_MOVIMENTO_LABEL, formatCurrency, formatDateTime, parseCurrencyBR, maskCurrencyInput } from '../../../src/utils/format';
import { ContaFinanceira, ContaFinanceiraRequest, SubtipoContaFinanceira } from '../../../src/types';
import { useTheme, useTabBarSpace } from '../../../src/theme';
import BackButton from '../../../src/components/ui/BackButton';
import SkeletonBox from '../../../src/components/ui/SkeletonBox';

// Extrato do ledger — fonte de confiança do saldo da conta
const SUBTIPO_LABEL: Record<SubtipoContaFinanceira, string> = {
  DINHEIRO: 'Dinheiro', CORRENTE: 'Conta corrente', POUPANCA: 'Poupança',
  PAGAMENTO: 'Conta de pagamento', COFRE: 'Cofre', CUSTODIA: 'Custódia', CARTAO: 'Cartão',
};

function ExtratoModal({ carteira, onClose }: { carteira: ContaFinanceira | null; onClose: () => void }) {
  const colors = useTheme();
  const tabBarSpace = useTabBarSpace();
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
    queryFn: ({ pageParam }) => contaFinanceiraService.listarMovimentos(carteira!.id, pageParam),
    initialPageParam: 0,
    getNextPageParam: last => (last.number + 1 < last.totalPages ? last.number + 1 : undefined),
    enabled: carteira != null,
  });

  const reconciliacaoQuery = useQuery({
    queryKey: ['carteira-reconciliacao', carteira?.id],
    queryFn: () => contaFinanceiraService.reconciliar(carteira!.id),
    enabled: carteira != null,
  });

  const movimentos = useMemo(() => data?.pages.flatMap(p => p.content) ?? [], [data]);
  const reconciliacao = reconciliacaoQuery.data;
  const reconciliacaoOk = reconciliacao?.status === 'OK';

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
          {reconciliacao && (
            <View
              style={{
                borderRadius: 999,
                backgroundColor: reconciliacaoOk ? colors.successBg : colors.dangerBg,
                paddingHorizontal: 10,
                paddingVertical: 5,
                marginRight: 10,
              }}
              accessible
              accessibilityLabel={reconciliacaoOk ? 'Saldo reconciliado com o ledger' : 'Saldo divergente do ledger'}
            >
              <Text style={{ color: reconciliacaoOk ? colors.success : colors.danger, fontSize: 11, fontWeight: '700' }}>
                {reconciliacaoOk ? 'OK' : 'Divergente'}
              </Text>
            </View>
          )}
          <TouchableOpacity onPress={onClose} accessibilityRole="button" style={{ minHeight: 44, justifyContent: 'center' }}>
            <Text style={{ color: colors.brand, fontSize: 15, fontWeight: '600' }}>Fechar</Text>
          </TouchableOpacity>
        </View>

        {reconciliacao && (
          <View style={{ marginHorizontal: 16, marginTop: 12, borderRadius: 12, borderWidth: 1, borderColor: reconciliacaoOk ? colors.successBg : colors.danger, backgroundColor: reconciliacaoOk ? colors.successBg : colors.dangerBg, padding: 12 }}>
            <Text style={{ color: reconciliacaoOk ? colors.success : colors.danger, fontSize: 13, fontWeight: '700' }}>
              {reconciliacaoOk ? 'Saldo conferido' : 'Saldo precisa de revisão'}
            </Text>
            <Text style={{ color: colors.textSecondary, fontSize: 11, marginTop: 4 }}>
              Conta: {formatCurrency(Number(reconciliacao.saldoMaterializado ?? 0))} · Ledger: {formatCurrency(Number(reconciliacao.saldoLedger ?? 0))} · Diferença: {formatCurrency(Number(reconciliacao.diferenca ?? 0))}
            </Text>
          </View>
        )}

        <FlatList
          data={movimentos}
          keyExtractor={m => m.id.toString()}
          contentContainerStyle={{ padding: 16, paddingBottom: tabBarSpace }}
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
  const [extratoDe, setExtratoDe] = useState<ContaFinanceira | null>(null);
  // Drill-down (PR-F3-08): ?contaId= abre o extrato da conta direto por rota
  const { contaId } = useLocalSearchParams<{ contaId?: string }>();
  const contaIdAberto = useRef<string | null>(null);
  const [nome, setNome] = useState('');
  const [tipo, setTipo] = useState<ContaFinanceiraRequest['subtipo'] | null>('DINHEIRO');
  const [saldo, setSaldo] = useState('');
  const [nomeError, setNomeError] = useState<string | null>(null);
  const [tipoError, setTipoError] = useState<string | null>(null);
  const [saldoError, setSaldoError] = useState<string | null>(null);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['contas-financeiras'],
    queryFn: () => contaFinanceiraService.listar(),
  });
  const contasOrdenadas = useMemo(
    () => [...(data?.content ?? [])].sort((a, b) => a.natureza.localeCompare(b.natureza) || a.nome.localeCompare(b.nome)),
    [data?.content],
  );

  // Abre o extrato da conta chegada por rota assim que a lista carrega
  useEffect(() => {
    if (!contaId || contaIdAberto.current === contaId) return;
    const conta = data?.content?.find(c => String(c.id) === String(contaId));
    if (conta) {
      contaIdAberto.current = contaId;
      setExtratoDe(conta);
    }
  }, [contaId, data?.content]);

  const criarMutation = useMutation({
    mutationFn: (req: ContaFinanceiraRequest) => contaFinanceiraService.criar(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contas-financeiras'] });
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
    const req: ContaFinanceiraRequest = {
      nome: nome.trim(), natureza: 'ATIVO', subtipo: tipo!, liquidez: 'IMEDIATA',
      moeda: 'BRL', saldoInicial: Number(v),
    };
    try {
      await criarMutation.mutateAsync(req);
    } catch (err: any) {
      setNomeError(err?.userMessage ?? 'Erro ao criar conta.');
    }
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={{ paddingTop: insets.top + 16, paddingHorizontal: 16, paddingBottom: 12 }}>
        <BackButton />
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
          data={contasOrdenadas}
          keyExtractor={item => item.id.toString()}
          renderItem={({ item: c, index }) => (
            <>
            {(index === 0 || contasOrdenadas[index - 1]?.natureza !== c.natureza) && (
              <Text style={{ color: colors.textSecondary, fontSize: 12, fontWeight: '700', marginHorizontal: 16, marginTop: index === 0 ? 4 : 12, marginBottom: 8 }}>
                {c.natureza === 'ATIVO' ? 'Ativos' : 'Passivos'}
              </Text>
            )}
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
                  <Text style={{ color: colors.textSecondary, fontSize: 12 }}>
                    {SUBTIPO_LABEL[c.subtipo]} · {c.liquidez} · {c.estadoConciliacao}
                  </Text>
                </View>
                <Text style={{ color: colors.textSecondary, fontSize: 12 }}>Extrato ›</Text>
              </View>
              <Text style={{ color: colors.textPrimary, fontSize: 18, fontWeight: '700', marginTop: 8 }}>{formatCurrency(Number(c.saldo ?? 0))}</Text>
              {c.banco && <Text style={{ color: colors.textMuted, fontSize: 11, marginTop: 6 }}>{c.banco}</Text>}
              {contaGerenciada(c) && <Text style={{ color: colors.brandFg, fontSize: 11, marginTop: 6 }}>Somente leitura · gerenciada no módulo de origem</Text>}
            </TouchableOpacity>
            </>
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
            <TextInput accessibilityLabel="Nome da carteira" value={nome} onChangeText={setNome} placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />
            {nomeError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{nomeError}</Text>}

            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Tipo</Text>
            <View style={{ flexDirection: 'row', gap: 8, marginBottom: 8 }}>
              {(['DINHEIRO','CORRENTE','POUPANCA','PAGAMENTO'] as ContaFinanceiraRequest['subtipo'][]).map(t => (
                <TouchableOpacity key={t} onPress={() => setTipo(t)} style={{ paddingHorizontal: 12, paddingVertical: 6, borderRadius: 20, backgroundColor: tipo === t ? colors.brand + '26' : colors.card, borderWidth: 1, borderColor: tipo === t ? colors.brand : colors.border }}>
                  <Text style={{ color: tipo === t ? colors.brand : colors.textSecondary }}>{SUBTIPO_LABEL[t]}</Text>
                </TouchableOpacity>
              ))}
            </View>
            {tipoError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{tipoError}</Text>}

            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Saldo inicial</Text>
            <TextInput accessibilityLabel="Saldo inicial" value={saldo} onChangeText={(t) => setSaldo(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />
            {saldoError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{saldoError}</Text>}
          </ScrollView>
        </View>
      </Modal>
    </View>
  );
}
