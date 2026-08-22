import React, { useEffect, useMemo, useRef, useState } from 'react';
import { View, Text, TouchableOpacity, FlatList, ScrollView } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { useQuery, useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import contaFinanceiraService, { contaGerenciada } from '../../../src/services/contaFinanceiraService';
import { TIPO_MOVIMENTO_LABEL, formatCurrency, formatDateTime, parseCurrencyBR, maskCurrencyInput } from '../../../src/utils/format';
import { camposDeErro, mensagemDeErro } from '../../../src/utils/erros';
import { ContaFinanceira, ContaFinanceiraRequest, SubtipoContaFinanceira } from '../../../src/types';
import {
  useTheme, useTabBarSpace, numeric, radius, screenPadding, spacing, typography,
} from '../../../src/theme';
import Badge from '../../../src/components/ui/Badge';
import CabecalhoSubTela from '../../../src/components/ui/CabecalhoSubTela';
import Card from '../../../src/components/ui/Card';
import Chip from '../../../src/components/ui/Chip';
import EstadoVazio from '../../../src/components/ui/EstadoVazio';
import Fab from '../../../src/components/ui/Fab';
import Field from '../../../src/components/ui/Field';
import FolhaModal from '../../../src/components/ui/FolhaModal';
import RotuloDeGrupo from '../../../src/components/ui/RotuloDeGrupo';
import SkeletonBox from '../../../src/components/ui/SkeletonBox';

// Extrato do ledger — fonte de confiança do saldo da conta
const SUBTIPO_LABEL: Record<SubtipoContaFinanceira, string> = {
  DINHEIRO: 'Dinheiro', CORRENTE: 'Conta corrente', POUPANCA: 'Poupança',
  PAGAMENTO: 'Conta de pagamento', COFRE: 'Cofre', CUSTODIA: 'Custódia', CARTAO: 'Cartão',
};

/** Tipos que o usuário cria à mão. Cofre, custódia e cartão nascem de outro módulo. */
const SUBTIPOS_CRIAVEIS: ContaFinanceiraRequest['subtipo'][] = ['DINHEIRO', 'CORRENTE', 'POUPANCA', 'PAGAMENTO'];

type CampoDaConta = 'nome' | 'tipo' | 'saldo';

const MAPA_DE_CAMPOS: Record<string, CampoDaConta> = {
  nome: 'nome',
  subtipo: 'tipo',
  saldoInicial: 'saldo',
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
    <FolhaModal
      visible={carteira != null}
      titulo={carteira?.nome ?? 'Extrato'}
      rotuloFechar="Fechar"
      onFechar={onClose}
    >
      <View style={{
        flexDirection: 'row', alignItems: 'center', gap: spacing.sm,
        paddingHorizontal: screenPadding, paddingTop: spacing.md,
      }}>
        <Text style={{ ...typography.meta, ...numeric, color: colors.textSecondary, flex: 1 }}>
          Extrato · saldo {formatCurrency(Number(carteira?.saldo ?? 0))}
        </Text>
        {!!reconciliacao && (
          <Badge tone={reconciliacaoOk ? 'success' : 'danger'}>
            {reconciliacaoOk ? 'OK' : 'Divergente'}
          </Badge>
        )}
      </View>

      {/* O saldo da conta é materializado; o ledger é a verdade. Quando divergem,
          a conta precisa de revisão — e o usuário precisa ver os dois números. */}
      {!!reconciliacao && (
        <View style={{
          marginHorizontal: screenPadding, marginTop: spacing.md,
          borderRadius: radius.md, borderWidth: 1,
          borderColor: reconciliacaoOk ? colors.successBg : colors.danger,
          backgroundColor: reconciliacaoOk ? colors.successBg : colors.dangerBg,
          padding: spacing.md,
        }}>
          <Text style={{ ...typography.rowTitle, fontWeight: '700', color: reconciliacaoOk ? colors.success : colors.danger }}>
            {reconciliacaoOk ? 'Saldo conferido' : 'Saldo precisa de revisão'}
          </Text>
          <Text style={{ ...typography.meta, ...numeric, color: colors.textSecondary, marginTop: spacing.xs }}>
            Conta: {formatCurrency(Number(reconciliacao.saldoMaterializado ?? 0))} · Ledger: {formatCurrency(Number(reconciliacao.saldoLedger ?? 0))} · Diferença: {formatCurrency(Number(reconciliacao.diferenca ?? 0))}
          </Text>
        </View>
      )}

      <FlatList
        data={movimentos}
        keyExtractor={m => m.id.toString()}
        contentContainerStyle={{ padding: screenPadding, paddingBottom: tabBarSpace }}
        onEndReached={() => { if (hasNextPage && !isFetchingNextPage) fetchNextPage(); }}
        onEndReachedThreshold={0.4}
        ListFooterComponent={isFetchingNextPage ? (
          <View style={{ paddingVertical: spacing.md }}>
            <SkeletonBox width="100%" height={64} borderRadius={radius.md} />
          </View>
        ) : null}
        ListEmptyComponent={isLoading ? (
          <View style={{ gap: spacing.sm }}>
            {[1, 2, 3, 4, 5].map(i => <SkeletonBox key={i} width="100%" height={64} borderRadius={radius.md} />)}
          </View>
        ) : isError ? (
          <EstadoVazio
            emoji="📶"
            titulo="Não deu para carregar o extrato"
            texto="Verifique sua conexão e tente de novo."
            acao={{ rotulo: 'Tentar de novo', onPress: () => refetch() }}
          />
        ) : (
          <EstadoVazio
            emoji="🧾"
            titulo="Sem movimentos ainda"
            texto="Transações e ajustes nesta conta aparecem aqui."
          />
        )}
        renderItem={({ item: m }) => {
          const credita = Number(m.valorAssinado) >= 0;
          return (
            <View style={{
              backgroundColor: colors.card, borderRadius: radius.md,
              borderWidth: 1, borderColor: colors.border,
              padding: spacing.md, marginBottom: spacing.sm,
            }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: spacing.sm }}>
                <Text numberOfLines={1} style={{ ...typography.body, fontWeight: '600', color: colors.textPrimary, flex: 1 }}>
                  {m.descricao || TIPO_MOVIMENTO_LABEL[m.tipo]}
                </Text>
                <Text style={{ ...typography.body, ...numeric, fontWeight: '700', color: credita ? colors.success : colors.danger }}>
                  {credita ? '+' : '−'} {formatCurrency(Math.abs(Number(m.valorAssinado)))}
                </Text>
              </View>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: spacing.sm, marginTop: spacing.xs }}>
                <Text style={{ ...typography.meta, color: colors.textSecondary }}>
                  {TIPO_MOVIMENTO_LABEL[m.tipo]} · {formatDateTime(m.dataMovimento)}
                </Text>
                <Text style={{ ...typography.meta, ...numeric, color: colors.textSecondary }}>
                  Saldo: {formatCurrency(Number(m.saldoResultante))}
                </Text>
              </View>
            </View>
          );
        }}
      />
    </FolhaModal>
  );
}

export default function CarteirasScreen() {
  const colors = useTheme();
  const tabBarSpace = useTabBarSpace();
  const queryClient = useQueryClient();
  const [modalVisible, setModalVisible] = useState(false);
  const [extratoDe, setExtratoDe] = useState<ContaFinanceira | null>(null);
  // Drill-down (PR-F3-08): ?contaId= abre o extrato da conta direto por rota
  const { contaId } = useLocalSearchParams<{ contaId?: string }>();
  const contaIdAberto = useRef<string | null>(null);
  const [nome, setNome] = useState('');
  const [tipo, setTipo] = useState<ContaFinanceiraRequest['subtipo'] | null>('DINHEIRO');
  const [saldo, setSaldo] = useState('');
  const [erros, setErros] = useState<Partial<Record<CampoDaConta, string>>>({});
  const [erroGeral, setErroGeral] = useState<string | null>(null);

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

  const fecharFormulario = () => {
    setModalVisible(false);
    setNome(''); setSaldo(''); setTipo('DINHEIRO');
    setErros({}); setErroGeral(null);
  };

  const criarMutation = useMutation({
    mutationFn: (req: ContaFinanceiraRequest) => contaFinanceiraService.criar(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contas-financeiras'] });
      fecharFormulario();
    },
    onError: (err: unknown) => {
      // Erro de campo mora no campo; o resto vai para a faixa geral. Antes tudo
      // caía em `nomeError`, então "saldo inválido" aparecia sob o nome.
      setErros(camposDeErro(err, MAPA_DE_CAMPOS));
      setErroGeral(mensagemDeErro(err, 'Erro ao criar conta.'));
    },
  });

  const handleSalvar = () => {
    setErros({}); setErroGeral(null);
    const local: Partial<Record<CampoDaConta, string>> = {};
    if (!nome.trim()) local.nome = 'Nome obrigatório.';
    if (!tipo) local.tipo = 'Tipo obrigatório.';
    const v = parseCurrencyBR(saldo || '0');
    if (isNaN(v) || v < 0) local.saldo = 'Saldo deve ser maior ou igual a zero.';
    if (Object.keys(local).length > 0) { setErros(local); return; }

    criarMutation.mutate({
      nome: nome.trim(), natureza: 'ATIVO', subtipo: tipo!, liquidez: 'IMEDIATA',
      moeda: 'BRL', saldoInicial: Number(v),
    });
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <CabecalhoSubTela
        titulo="Contas"
        apoio={<Text style={{ ...typography.body, color: colors.textSecondary }}>Onde seu dinheiro está guardado</Text>}
      />

      {isLoading ? (
        <View style={{ paddingHorizontal: screenPadding, gap: spacing.sm }}>
          {[1, 2, 3].map(i => <SkeletonBox key={i} width="100%" height={96} borderRadius={radius.lg} />)}
        </View>
      ) : isError ? (
        <EstadoVazio
          emoji="📶"
          titulo="Não deu para carregar suas contas"
          texto="Verifique sua conexão e tente de novo."
          acao={{ rotulo: 'Tentar de novo', onPress: () => refetch() }}
        />
      ) : (
        <FlatList
          data={contasOrdenadas}
          keyExtractor={item => item.id.toString()}
          contentContainerStyle={{ paddingHorizontal: screenPadding, paddingBottom: tabBarSpace }}
          ListEmptyComponent={(
            <EstadoVazio
              emoji="🏦"
              titulo="Nenhuma conta cadastrada"
              texto="Cadastre onde seu dinheiro fica guardado para acompanhar o saldo."
              acao={{ rotulo: 'Criar conta', onPress: () => setModalVisible(true) }}
            />
          )}
          renderItem={({ item: c, index }) => {
            const primeiroDaNatureza = index === 0 || contasOrdenadas[index - 1]?.natureza !== c.natureza;
            return (
              <>
                {primeiroDaNatureza && (
                  <RotuloDeGrupo primeiro={index === 0}>
                    {c.natureza === 'ATIVO' ? 'Ativos' : 'Passivos'}
                  </RotuloDeGrupo>
                )}
                {/* Card composto: sem rótulo curado, o leitor de tela lê nome,
                    subtipo, saldo e banco — antes só "Ver extrato da conta X". */}
                <TouchableOpacity
                  onPress={() => setExtratoDe(c)}
                  activeOpacity={0.7}
                  accessibilityRole="button"
                  accessibilityHint="Abre o extrato da conta"
                >
                  <Card radius={radius.md} style={{ marginBottom: spacing.sm }}>
                    <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: spacing.sm }}>
                      <View style={{ flex: 1, minWidth: 0 }}>
                        <Text numberOfLines={1} style={{ ...typography.rowTitle, color: colors.textPrimary }}>{c.nome}</Text>
                        <Text numberOfLines={1} style={{ ...typography.meta, color: colors.textSecondary }}>
                          {SUBTIPO_LABEL[c.subtipo]} · {c.liquidez} · {c.estadoConciliacao}
                        </Text>
                      </View>
                      <Text style={{ ...typography.meta, color: colors.textSecondary }}>Extrato ›</Text>
                    </View>
                    <Text style={{ ...typography.section, ...numeric, color: colors.textPrimary, marginTop: spacing.sm }}>
                      {formatCurrency(Number(c.saldo ?? 0))}
                    </Text>
                    {!!c.banco && (
                      <Text style={{ ...typography.meta, color: colors.textMuted, marginTop: spacing.xs }}>{c.banco}</Text>
                    )}
                    {contaGerenciada(c) && (
                      <Text style={{ ...typography.meta, color: colors.brandFg, marginTop: spacing.xs }}>
                        Somente leitura · gerenciada no módulo de origem
                      </Text>
                    )}
                  </Card>
                </TouchableOpacity>
              </>
            );
          }}
        />
      )}

      <Fab onPress={() => setModalVisible(true)} accessibilityLabel="Nova conta" />

      <ExtratoModal carteira={extratoDe} onClose={() => setExtratoDe(null)} />

      <FolhaModal
        visible={modalVisible}
        titulo="Nova Conta"
        onFechar={fecharFormulario}
        acao={{ rotulo: 'Salvar', onPress: handleSalvar, carregando: criarMutation.status === 'pending' }}
      >
        <ScrollView contentContainerStyle={{ padding: screenPadding }} keyboardShouldPersistTaps="handled">
          <Field
            label="Nome"
            value={nome}
            onChangeText={setNome}
            placeholder="Ex.: Conta corrente"
            error={erros.nome}
          />

          <RotuloDeGrupo>Tipo</RotuloDeGrupo>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, marginBottom: spacing.md }}>
            {SUBTIPOS_CRIAVEIS.map(t => (
              <Chip key={t} label={SUBTIPO_LABEL[t]} selected={tipo === t} onPress={() => setTipo(t)} />
            ))}
          </View>
          {!!erros.tipo && (
            <Text accessibilityRole="alert" style={{ ...typography.meta, color: colors.danger, marginBottom: spacing.sm }}>
              {erros.tipo}
            </Text>
          )}

          <Field
            label="Saldo inicial"
            value={saldo}
            onChangeText={(t) => setSaldo(maskCurrencyInput(t))}
            keyboardType="number-pad"
            placeholder="0,00"
            error={erros.saldo}
          />

          {!!erroGeral && (
            <Text accessibilityRole="alert" accessibilityLiveRegion="polite" style={{ ...typography.meta, color: colors.danger }}>
              {erroGeral}
            </Text>
          )}
        </ScrollView>
      </FolhaModal>
    </View>
  );
}
