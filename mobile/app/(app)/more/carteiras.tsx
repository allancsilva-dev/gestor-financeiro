import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, View, Text, TouchableOpacity, FlatList, ScrollView, Switch } from 'react-native';
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
import Botao from '../../../src/components/ui/Botao';
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

type CampoDaConta = 'nome' | 'tipo' | 'saldo' | 'banco';

const MAPA_DE_CAMPOS: Record<string, CampoDaConta> = {
  nome: 'nome',
  subtipo: 'tipo',
  saldoInicial: 'saldo',
  banco: 'banco',
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
  // `null` = criando; conta = editando. O formulário é o mesmo nos dois casos porque os campos
  // são os mesmos — o que muda é o destino do salvar e o que já vem preenchido.
  const [editando, setEditando] = useState<ContaFinanceira | null>(null);
  const [nome, setNome] = useState('');
  const [banco, setBanco] = useState('');
  const [tipo, setTipo] = useState<ContaFinanceiraRequest['subtipo'] | null>('DINHEIRO');
  const [saldo, setSaldo] = useState('');
  const [principal, setPrincipal] = useState(false);
  const [erros, setErros] = useState<Partial<Record<CampoDaConta, string>>>({});
  const [erroGeral, setErroGeral] = useState<string | null>(null);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['contas-financeiras'],
    queryFn: () => contaFinanceiraService.listar(),
  });
  // Principal no topo do grupo: é a conta que a pessoa olha primeiro e a que o formulário de
  // lançamento pré-seleciona. Ordem alfabética a esconderia no meio da lista.
  const contasOrdenadas = useMemo(
    () => [...(data?.content ?? [])].sort((a, b) =>
      a.natureza.localeCompare(b.natureza)
      || Number(b.principal) - Number(a.principal)
      || a.nome.localeCompare(b.nome)),
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
    setModalVisible(false); setEditando(null);
    setNome(''); setBanco(''); setSaldo(''); setTipo('DINHEIRO'); setPrincipal(false);
    setErros({}); setErroGeral(null);
  };

  const abrirCriacao = () => {
    setEditando(null);
    setNome(''); setBanco(''); setSaldo(''); setTipo('DINHEIRO'); setPrincipal(false);
    setErros({}); setErroGeral(null);
    setModalVisible(true);
  };

  const abrirEdicao = (conta: ContaFinanceira) => {
    setEditando(conta);
    setNome(conta.nome);
    setBanco(conta.banco ?? '');
    setTipo(conta.subtipo as ContaFinanceiraRequest['subtipo']);
    // Pré-preenchido com o saldo atual de propósito: o PUT converte a DIFERENÇA em
    // AJUSTE_MANUAL no ledger, então salvar sem mexer no campo tem que gerar diferença zero.
    // `Math.max(0, ...)` porque `saldoInicial` é @PositiveOrZero no contrato — saldo negativo
    // em conta manual só nasce de ajuste e não tem como voltar por esta rota.
    setSaldo(maskCurrencyInput(String(Math.round(Math.max(0, Number(conta.saldo ?? 0)) * 100))));
    setPrincipal(conta.principal);
    setErros({}); setErroGeral(null);
    setModalVisible(true);
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

  const atualizarMutation = useMutation({
    mutationFn: ({ id, req }: { id: number; req: ContaFinanceiraRequest }) =>
      contaFinanceiraService.atualizar(id, req),
    onSuccess: () => {
      // Saldo alterado vira movimento: o extrato e as métricas da home também mudaram.
      queryClient.invalidateQueries({ queryKey: ['contas-financeiras'] });
      queryClient.invalidateQueries({ queryKey: ['carteira-movimentos'] });
      queryClient.invalidateQueries({ queryKey: ['carteira-reconciliacao'] });
      queryClient.invalidateQueries({ queryKey: ['home'] });
      fecharFormulario();
    },
    onError: (err: unknown) => {
      setErros(camposDeErro(err, MAPA_DE_CAMPOS));
      setErroGeral(mensagemDeErro(err, 'Erro ao salvar conta.'));
    },
  });

  const deletarMutation = useMutation({
    mutationFn: (id: number) => contaFinanceiraService.deletar(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contas-financeiras'] });
      queryClient.invalidateQueries({ queryKey: ['home'] });
    },
    onError: (err: unknown) => {
      // O backend recusa conta com histórico de movimento — a mensagem dele explica o porquê
      // e sugere renomear, então repassar é melhor do que inventar texto próprio.
      Alert.alert('Conta não excluída', mensagemDeErro(err, 'Não foi possível excluir a conta.'));
    },
  });

  const confirmarExcluir = (conta: ContaFinanceira) => {
    Alert.alert(
      'Excluir conta',
      `Excluir "${conta.nome}"? Contas com histórico de movimentação não podem ser excluídas.`,
      [
        { text: 'Cancelar', style: 'cancel' },
        { text: 'Excluir', style: 'destructive', onPress: () => deletarMutation.mutate(conta.id) },
      ],
    );
  };

  const handleSalvar = () => {
    setErros({}); setErroGeral(null);
    const local: Partial<Record<CampoDaConta, string>> = {};
    if (!nome.trim()) local.nome = 'Nome obrigatório.';
    if (!tipo) local.tipo = 'Tipo obrigatório.';
    const v = parseCurrencyBR(saldo || '0');
    if (isNaN(v) || v < 0) local.saldo = 'Saldo deve ser maior ou igual a zero.';
    if (Object.keys(local).length > 0) { setErros(local); return; }

    const req: ContaFinanceiraRequest = {
      nome: nome.trim(), natureza: 'ATIVO', subtipo: tipo!, liquidez: 'IMEDIATA',
      moeda: 'BRL', banco: banco.trim() || undefined, saldoInicial: Number(v),
      // Só manda `true`: o contrato ignora `false` de propósito, porque desmarcar sem eleger
      // outra deixaria o titular sem conta padrão.
      principal: principal || undefined,
    };
    if (editando) atualizarMutation.mutate({ id: editando.id, req });
    else criarMutation.mutate(req);
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
              acao={{ rotulo: 'Criar conta', onPress: abrirCriacao }}
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
                <Card radius={radius.md} style={{ marginBottom: spacing.sm }}>
                  {/* Só a região informativa abre o extrato. Envolver o card inteiro colapsaria
                      os filhos num nó único de acessibilidade (`accessible` é padrão no
                      Touchable) e Editar/Excluir sumiriam do leitor de tela. */}
                  <TouchableOpacity
                    onPress={() => setExtratoDe(c)}
                    activeOpacity={0.7}
                    accessibilityRole="button"
                    accessibilityLabel={[
                      c.nome,
                      c.principal ? 'conta principal' : null,
                      SUBTIPO_LABEL[c.subtipo],
                      formatCurrency(Number(c.saldo ?? 0)),
                      c.banco,
                    ].filter(Boolean).join(', ')}
                    accessibilityHint="Abre o extrato da conta"
                  >
                    <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: spacing.sm }}>
                      <View style={{ flex: 1, minWidth: 0 }}>
                        <Text numberOfLines={1} style={{ ...typography.rowTitle, color: colors.textPrimary }}>{c.nome}</Text>
                        <Text numberOfLines={1} style={{ ...typography.meta, color: colors.textSecondary }}>
                          {SUBTIPO_LABEL[c.subtipo]} · {c.liquidez} · {c.estadoConciliacao}
                        </Text>
                      </View>
                      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm }}>
                        {c.principal && <Badge tone="success">Principal</Badge>}
                        <Text style={{ ...typography.meta, color: colors.textSecondary }}>Extrato ›</Text>
                      </View>
                    </View>
                    <Text style={{ ...typography.section, ...numeric, color: colors.textPrimary, marginTop: spacing.sm }}>
                      {formatCurrency(Number(c.saldo ?? 0))}
                    </Text>
                    {!!c.banco && (
                      <Text style={{ ...typography.meta, color: colors.textMuted, marginTop: spacing.xs }}>{c.banco}</Text>
                    )}
                  </TouchableOpacity>

                  {contaGerenciada(c) ? (
                    <Text style={{ ...typography.meta, color: colors.brandFg, marginTop: spacing.xs }}>
                      Somente leitura · gerenciada no módulo de origem
                    </Text>
                  ) : (
                    <View style={{ flexDirection: 'row', gap: spacing.sm, marginTop: spacing.md }}>
                      <Botao
                        titulo="Editar"
                        variante="secundario"
                        tamanho="pill"
                        onPress={() => abrirEdicao(c)}
                        accessibilityLabel={`Editar ${c.nome}`}
                        style={{ flex: 1 }}
                      />
                      <Botao
                        titulo="Excluir"
                        variante="perigo"
                        tamanho="pill"
                        onPress={() => confirmarExcluir(c)}
                        accessibilityLabel={`Excluir ${c.nome}`}
                        style={{ flex: 1 }}
                      />
                    </View>
                  )}
                </Card>
              </>
            );
          }}
        />
      )}

      <Fab onPress={abrirCriacao} accessibilityLabel="Nova conta" />

      <ExtratoModal carteira={extratoDe} onClose={() => setExtratoDe(null)} />

      <FolhaModal
        visible={modalVisible}
        titulo={editando ? 'Editar Conta' : 'Nova Conta'}
        onFechar={fecharFormulario}
        acao={{
          rotulo: 'Salvar',
          onPress: handleSalvar,
          carregando: criarMutation.status === 'pending' || atualizarMutation.status === 'pending',
        }}
      >
        <ScrollView contentContainerStyle={{ padding: screenPadding }} keyboardShouldPersistTaps="handled">
          <Field
            testID="conta-form-nome"
            label="Nome"
            value={nome}
            onChangeText={setNome}
            placeholder="Ex.: Conta do dia a dia"
            error={erros.nome}
          />

          <Field
            testID="conta-form-banco"
            label="Banco"
            value={banco}
            onChangeText={setBanco}
            placeholder="Ex.: Nubank, Itaú, Caixa"
            error={erros.banco}
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

          {/* testID porque o rotulo e o input compartilham o texto "Saldo": um seletor por texto
              casa primeiro o <Text> do rotulo, que nao da foco ao campo. */}
          <Field
            testID="conta-form-saldo"
            label={editando ? 'Saldo' : 'Saldo inicial'}
            value={saldo}
            onChangeText={(t) => setSaldo(maskCurrencyInput(t))}
            keyboardType="number-pad"
            placeholder="0,00"
            error={erros.saldo}
          />
          {editando && (
            <Text style={{ ...typography.meta, color: colors.textSecondary, marginBottom: spacing.md }}>
              Mudar o saldo lança um ajuste no extrato com a diferença. O histórico continua inteiro.
            </Text>
          )}

          {/* Desmarcar não é oferecido: o backend ignora `false` porque ficar sem conta padrão
              faria o formulário de lançamento voltar a chutar a primeira da lista. Trocar de
              principal é marcar outra. */}
          <View style={{
            flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
            gap: spacing.md, marginBottom: spacing.md,
          }}>
            <View style={{ flex: 1, minWidth: 0 }}>
              <Text style={{ ...typography.rowTitle, color: colors.textPrimary }}>Conta principal</Text>
              <Text style={{ ...typography.meta, color: colors.textSecondary }}>
                {principal && editando?.principal
                  ? 'Já é a conta padrão dos seus lançamentos.'
                  : 'Vem pré-selecionada quando você lança algo.'}
              </Text>
            </View>
            <Switch
              value={principal}
              onValueChange={setPrincipal}
              disabled={editando?.principal === true}
              trackColor={{ true: colors.brand, false: colors.border }}
              accessibilityLabel="Definir como conta principal"
            />
          </View>

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
