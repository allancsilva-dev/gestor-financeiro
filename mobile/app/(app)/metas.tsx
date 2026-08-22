import React, { useRef, useState } from 'react';
import { View, Text, FlatList, TouchableOpacity, Modal, ScrollView, ActivityIndicator, Alert, TextInput } from 'react-native';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { metaService } from '../../src/services/metaService';
import contaFinanceiraService from '../../src/services/contaFinanceiraService';
import { useRouter } from 'expo-router';
import { formatCurrency, formatPercent, formatDate, formatDateOnlyBR, parseDateBR, isValidDateBR, parseCurrencyBR, maskCurrencyInput, maskDateInput } from '../../src/utils/format';
import { Meta, MetaRequest, ModalidadeMeta, StatusMeta } from '../../src/types';
import { useTheme, useTabBarSpace } from '../../src/theme';
import SkeletonBox from '../../src/components/ui/SkeletonBox';
import Card from '../../src/components/ui/Card';
import IconTile from '../../src/components/ui/IconTile';
import Badge from '../../src/components/ui/Badge';
import ProgressBar from '../../src/components/ui/ProgressBar';
import Field from '../../src/components/ui/Field';
import Chip from '../../src/components/ui/Chip';
import { acoesDaMeta, duracaoDaMetaConcluidaEmDias } from '../../src/domain/metaPolicy';
import CardMeta from '../../src/components/metas/CardMeta';
import CabecalhoDeTela from '../../src/components/ui/CabecalhoDeTela';
import CabecalhoSecao from '../../src/components/ui/CabecalhoSecao';
import EstadoVazio from '../../src/components/ui/EstadoVazio';
import { e } from '../../src/theme/escala';

// Textos do glossário (ADR-0012) — a escolha é definitiva (PR-F3-11)
const MODALIDADES: Array<{ id: ModalidadeMeta; titulo: string; descricao: string }> = [
  {
    id: 'COFRE_REAL',
    titulo: 'Cofre real',
    descricao: 'O dinheiro sai da sua conta e fica guardado num cofre com extrato próprio.',
  },
  {
    id: 'RESERVA_VIRTUAL',
    titulo: 'Reserva virtual',
    descricao: 'O dinheiro continua na sua conta, marcado como reservado; reduz só o disponível para gastar.',
  },
];

export default function Metas() {
  const colors = useTheme();
  const tabBarSpace = useTabBarSpace();
  const router = useRouter();
  const queryClient = useQueryClient();

  const [modalAdicionarVisible, setModalAdicionarVisible] = useState(false);
  const [modalRemoverVisible, setModalRemoverVisible] = useState(false);
  const [modalCriarVisible, setModalCriarVisible] = useState(false);
  const [modalDetalheVisible, setModalDetalheVisible] = useState(false);
  const [retornarAoDetalheAposMovimentacao, setRetornarAoDetalheAposMovimentacao] = useState(false);
  const [editandoMeta, setEditandoMeta] = useState<Meta | null>(null);
  const [metaSelecionada, setMetaSelecionada] = useState<Meta | null>(null);
  const [erroCriar, setErroCriar] = useState<string | null>(null);
  const [valorAdicionar, setValorAdicionar] = useState('');
  const [valorRemover, setValorRemover] = useState('');
  const [erroAdicionar, setErroAdicionar] = useState<string | null>(null);
  const [erroRemover, setErroRemover] = useState<string | null>(null);
  const [carteiraOrigemId, setCarteiraOrigemId] = useState<number | null>(null);
  const [carteiraDestinoId, setCarteiraDestinoId] = useState<number | null>(null);
  const [erroCarteira, setErroCarteira] = useState<string | null>(null);
  const [erroCarteiraDestino, setErroCarteiraDestino] = useState<string | null>(null);

  const [nomeCriar, setNomeCriar] = useState('');
  // Escolha obrigatória na criação; imutável depois (PR-F3-11)
  const [modalidadeCriar, setModalidadeCriar] = useState<ModalidadeMeta | null>(null);
  const [modalidadeError, setModalidadeError] = useState<string | null>(null);
  const [valorTotalCriar, setValorTotalCriar] = useState('');
  const [valorMensalCriar, setValorMensalCriar] = useState('');
  const [dataLimiteCriar, setDataLimiteCriar] = useState('');
  const [descricaoCriar, setDescricaoCriar] = useState('');
  const [nomeError, setNomeError] = useState<string | null>(null);
  const [valorTotalError, setValorTotalError] = useState<string | null>(null);
  const [valorMensalError, setValorMensalError] = useState<string | null>(null);
  const [dataLimiteError, setDataLimiteError] = useState<string | null>(null);

  const [statusFiltro, setStatusFiltro] = useState<StatusMeta>('ATIVA');

  // Foco encadeado pelo teclado: com o teclado aberto o formulário sobe e tocar
  // no campo seguinte vira acerto de coordenada.
  const refValorTotal = useRef<TextInput>(null);
  const refValorMensal = useRef<TextInput>(null);
  const refDataLimite = useRef<TextInput>(null);
  const refDescricao = useRef<TextInput>(null);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['metas', statusFiltro],
    queryFn: () => metaService.listar(statusFiltro),
  });

  const { data: carteirasData } = useQuery({
    queryKey: ['contas-financeiras-caixa'],
    queryFn: () => contaFinanceiraService.listarParaCaixa(),
  });
  const carteiras = carteirasData ?? [];

  const resetFormularioMeta = () => {
    setEditandoMeta(null);
    setModalidadeCriar(null);
    setModalidadeError(null);
    setNomeCriar('');
    setValorTotalCriar('');
    setValorMensalCriar('');
    setDataLimiteCriar('');
    setDescricaoCriar('');
    setNomeError(null);
    setValorTotalError(null);
    setValorMensalError(null);
    setDataLimiteError(null);
    setErroCriar(null);
  };

  const abrirCriarMeta = () => {
    resetFormularioMeta();
    setModalCriarVisible(true);
  };

  const abrirEditarMeta = (meta: Meta) => {
    // O lápis fica sempre visível no card (como na referência); a política decide se abre
    if (!acoesDaMeta(meta).editar) {
      Alert.alert('Meta arquivada', `"${meta.nome}" está arquivada e não aceita mais edição.`);
      return;
    }
    setEditandoMeta(meta);
    setModalidadeCriar(meta.modalidade ?? 'COFRE_REAL');
    setNomeCriar(meta.nome);
    setValorTotalCriar(maskCurrencyInput(Number(meta.valorTotal ?? 0).toFixed(2)));
    setValorMensalCriar(meta.valorMensal ? maskCurrencyInput(Number(meta.valorMensal).toFixed(2)) : '');
    setDataLimiteCriar(meta.dataPrevista ? formatDate(meta.dataPrevista) : '');
    setDescricaoCriar(meta.descricao ?? '');
    setNomeError(null);
    setValorTotalError(null);
    setValorMensalError(null);
    setDataLimiteError(null);
    setErroCriar(null);
    setModalDetalheVisible(false);
    setModalCriarVisible(true);
  };

  const abrirAdicionarValor = (meta: Meta, origemDetalhe = false) => {
    setValorAdicionar('0');
    setErroAdicionar(null);
    setErroCarteira(null);
    setCarteiraOrigemId(carteiras.length === 1 ? carteiras[0].id : null);
    setMetaSelecionada(meta);
    setRetornarAoDetalheAposMovimentacao(origemDetalhe);
    if (origemDetalhe) {
      setModalDetalheVisible(false);
      setTimeout(() => setModalAdicionarVisible(true), 350);
      return;
    }
    setModalAdicionarVisible(true);
  };

  const abrirRetirarValor = (meta: Meta) => {
    setValorRemover('0');
    setErroRemover(null);
    setErroCarteiraDestino(null);
    setCarteiraDestinoId(carteiras.length === 1 ? carteiras[0].id : null);
    setMetaSelecionada(meta);
    setRetornarAoDetalheAposMovimentacao(true);
    setModalDetalheVisible(false);
    setTimeout(() => setModalRemoverVisible(true), 350);
  };

  const fecharMovimentacao = (tipo: 'adicionar' | 'remover') => {
    if (tipo === 'adicionar') setModalAdicionarVisible(false);
    else setModalRemoverVisible(false);
    const deveRetornar = retornarAoDetalheAposMovimentacao;
    setRetornarAoDetalheAposMovimentacao(false);
    if (deveRetornar) setTimeout(() => setModalDetalheVisible(true), 350);
  };

  const montarPayloadMeta = (): MetaRequest | null => {
    setNomeError(null); setValorTotalError(null); setValorMensalError(null); setDataLimiteError(null); setErroCriar(null); setModalidadeError(null);
    let hasErr = false;
    if (!editandoMeta && modalidadeCriar == null) { setModalidadeError('Escolha como a meta guarda o dinheiro.'); hasErr = true; }
    if (!nomeCriar.trim() || nomeCriar.trim().length < 3) { setNomeError('Nome obrigatório (mínimo 3 caracteres).'); hasErr = true; }
    const valorTotal = parseCurrencyBR(valorTotalCriar);
    if (isNaN(valorTotal) || valorTotal <= 0) { setValorTotalError('Valor total obrigatório e positivo.'); hasErr = true; }
    const valorMensal = valorMensalCriar ? parseCurrencyBR(valorMensalCriar) : undefined;
    if (valorMensalCriar && (valorMensal == null || isNaN(valorMensal) || valorMensal <= 0)) { setValorMensalError('Valor mensal deve ser positivo.'); hasErr = true; }
    if (dataLimiteCriar && !isValidDateBR(dataLimiteCriar)) { setDataLimiteError('Data inválida. Use o formato DD/MM/AAAA.'); hasErr = true; }
    if (hasErr) return null;
    return {
      nome: nomeCriar.trim(),
      valorTotal: Number(valorTotal),
      valorMensal: valorMensal ? Number(valorMensal) : undefined,
      dataLimite: dataLimiteCriar ? parseDateBR(dataLimiteCriar) : undefined,
      descricao: descricaoCriar || undefined,
      modalidade: modalidadeCriar ?? undefined,
    };
  };

  const adicionarMutation = useMutation({
    mutationFn: ({ id, valor, carteiraId }: { id: number; valor: number; carteiraId: number }) =>
      metaService.adicionarValor(id, valor, carteiraId),
    onSuccess: (metaAtualizada) => {
      setMetaSelecionada(metaAtualizada);
      queryClient.invalidateQueries({ queryKey: ['metas'] });
      queryClient.invalidateQueries({ queryKey: ['carteiras'] });
      fecharMovimentacao('adicionar');
    },
    onError: (err: any) => setErroAdicionar(err?.userMessage ?? 'Erro ao adicionar.'),
  });

  const removerMutation = useMutation({
    mutationFn: ({ id, valor, carteiraId }: { id: number; valor: number; carteiraId: number }) =>
      metaService.removerValor(id, valor, carteiraId),
    onSuccess: (metaAtualizada) => {
      setMetaSelecionada(metaAtualizada);
      queryClient.invalidateQueries({ queryKey: ['metas'] });
      queryClient.invalidateQueries({ queryKey: ['carteiras'] });
      fecharMovimentacao('remover');
    },
    onError: (err: any) => setErroRemover(err?.userMessage ?? 'Erro ao retirar.'),
  });

  const criarMutation = useMutation({
    mutationFn: (payload: MetaRequest) => metaService.criar(payload),
    onSuccess: (metaAtualizada) => {
      setMetaSelecionada(metaAtualizada);
      queryClient.invalidateQueries({ queryKey: ['metas'] });
      setModalCriarVisible(false);
      resetFormularioMeta();
    },
    onError: (err: any) => setErroCriar(err?.userMessage ?? 'Erro ao criar meta.'),
  });

  const atualizarMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: MetaRequest }) => metaService.atualizar(id, payload),
    onSuccess: (metaAtualizada) => {
      setMetaSelecionada(metaAtualizada);
      queryClient.invalidateQueries({ queryKey: ['metas'] });
      setModalCriarVisible(false);
      resetFormularioMeta();
    },
    onError: (err: any) => setErroCriar(err?.userMessage ?? 'Erro ao salvar meta.'),
  });

  const deletarMutation = useMutation({
    mutationFn: (id: number) => metaService.deletar(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['metas'] });
      setModalDetalheVisible(false);
      setMetaSelecionada(null);
    },
    onError: (error: any) => {
      // backend bloqueia exclusão com valor reservado: resgate primeiro (ADR-0004)
      const mensagem = error?.response?.data?.message ?? 'Não foi possível excluir a meta.';
      Alert.alert('Meta não excluída', mensagem);
    },
  });

  const confirmarExcluirMeta = (meta: Meta) => {
    if (meta.status !== 'ATIVA') {
      Alert.alert(
        meta.status === 'CONCLUIDA' ? 'Meta concluída' : 'Meta arquivada',
        `"${meta.nome}" não pode mais ser excluída.`,
      );
      return;
    }
    if (Number(meta.valorReservado ?? 0) > 0) {
      Alert.alert(
        'Meta com dinheiro reservado',
        `"${meta.nome}" ainda tem ${formatCurrency(Number(meta.valorReservado))} reservados. Resgate o valor para uma conta antes de excluir.`,
      );
      return;
    }
    Alert.alert(
      'Excluir meta',
      `Excluir "${meta.nome}"? A meta vai para "Arquivadas" e deixa de aceitar movimentações.`,
      [
        { text: 'Cancelar', style: 'cancel' },
        { text: 'Excluir', style: 'destructive', onPress: () => deletarMutation.mutate(meta.id) },
      ],
    );
  };

  const renderItem = ({ item: meta }: { item: Meta }) => (
    <CardMeta
      meta={meta}
      onAbrir={m => { setMetaSelecionada(m); setModalDetalheVisible(true); }}
      onDepositar={m => abrirAdicionarValor(m)}
      onEditar={abrirEditarMeta}
      onExcluir={confirmarExcluirMeta}
    />
  );

  const TITULO_DA_SECAO: Record<StatusMeta, { titulo: string; texto: string }> = {
    ATIVA: {
      titulo: 'Suas metas ativas',
      texto: 'Acompanhe o ritmo, entenda a pressão do mês e escolha onde vale avaliar agora.',
    },
    CONCLUIDA: {
      titulo: 'Suas metas concluídas',
      texto: 'O que você já fechou. Resgate o valor quando quiser usar o dinheiro.',
    },
    ARQUIVADA: {
      titulo: 'Suas metas arquivadas',
      texto: 'Metas fora de circulação. Ficam aqui só para consulta.',
    },
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <CabecalhoDeTela
        titulo="Metas"
        acao={{ icone: 'add', onPress: abrirCriarMeta, accessibilityLabel: 'Criar meta' }}
      />

      {isLoading ? (
        <View style={{ paddingHorizontal: e(18), gap: e(33), paddingTop: e(24) }}>
          {[1, 2, 3].map(i => <SkeletonBox key={i} width="100%" height={e(155)} borderRadius={e(20)} />)}
        </View>
      ) : isError ? (
        <EstadoVazio
          emoji="📶"
          titulo="Não deu para carregar suas metas"
          texto="Verifique sua conexão e tente de novo."
          acao={{ rotulo: 'Tentar de novo', onPress: () => refetch() }}
        />
      ) : (
        <FlatList
          data={data?.content ?? []}
          keyExtractor={m => m.id.toString()}
          renderItem={renderItem}
          contentContainerStyle={{ paddingBottom: tabBarSpace }}
          ListHeaderComponent={
            <>
              <View style={{ flexDirection: 'row', gap: 8, paddingHorizontal: e(16), paddingTop: e(12) }}>
                <Chip label="Ativas" selected={statusFiltro === 'ATIVA'} onPress={() => setStatusFiltro('ATIVA')} />
                <Chip label="Concluídas" selected={statusFiltro === 'CONCLUIDA'} onPress={() => setStatusFiltro('CONCLUIDA')} />
                <Chip label="Arquivadas" selected={statusFiltro === 'ARQUIVADA'} onPress={() => setStatusFiltro('ARQUIVADA')} />
              </View>
              <CabecalhoSecao
                escalar
                eyebrow="OBJETIVOS"
                titulo={TITULO_DA_SECAO[statusFiltro].titulo}
                texto={TITULO_DA_SECAO[statusFiltro].texto}
              />
            </>
          }
          ListEmptyComponent={() => (
            <EstadoVazio
              emoji="🎯"
              titulo={statusFiltro === 'ATIVA' ? 'Nenhuma meta ainda'
                : statusFiltro === 'CONCLUIDA' ? 'Nenhuma meta concluída'
                : 'Nenhuma meta arquivada'}
              texto={statusFiltro === 'ATIVA'
                ? 'Uma meta é um valor com prazo. Comece pela que mais te incomoda hoje.'
                : undefined}
              acao={statusFiltro === 'ATIVA'
                ? { rotulo: 'Criar primeira meta', onPress: abrirCriarMeta }
                : undefined}
            />
          )}
        />
      )}

      <Modal visible={modalDetalheVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity onPress={() => setModalDetalheVisible(false)} accessibilityRole="button">
              <Text style={{ color: colors.brandFg, fontSize: 15 }}>Fechar</Text>
            </TouchableOpacity>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700' }}>Detalhes da Meta</Text>
            {metaSelecionada && acoesDaMeta(metaSelecionada).editar ? (
              <TouchableOpacity
                onPress={() => metaSelecionada && abrirEditarMeta(metaSelecionada)}
                accessibilityRole="button"
              >
                <Text style={{ color: colors.brandFg, fontSize: 15, fontWeight: '700' }}>Editar</Text>
              </TouchableOpacity>
            ) : <View style={{ width: 48 }} />}
          </View>
          {metaSelecionada && (
            <ScrollView contentContainerStyle={{ padding: 16 }} keyboardShouldPersistTaps="handled">
              <Card radius={20}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 12 }}>
                  <IconTile tone={metaSelecionada.ativa ? 'brand' : 'success'} size={44}>{metaSelecionada.icone || '🎯'}</IconTile>
                  <View style={{ flex: 1, minWidth: 0 }}>
                    <Text style={{ color: colors.textPrimary, fontSize: 17, fontWeight: '800' }} numberOfLines={2}>{metaSelecionada.nome}</Text>
                    <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 2 }}>
                      {metaSelecionada.dataPrevista ? `até ${formatDate(metaSelecionada.dataPrevista)}` : 'Sem data limite'}
                    </Text>
                  </View>
                  <Badge tone={metaSelecionada.status === 'ARQUIVADA' ? 'info' : metaSelecionada.status === 'CONCLUIDA' ? 'success' : 'brand'}>
                    {metaSelecionada.status === 'ARQUIVADA' ? 'Arquivada' : metaSelecionada.status === 'CONCLUIDA' ? 'Concluída' : 'Ativa'}
                  </Badge>
                </View>
                <Text style={{ color: colors.textSecondary, fontSize: 12, marginBottom: 8, fontVariant: ['tabular-nums'] }}>
                  {formatCurrency(Number(metaSelecionada.valorReservado ?? 0))} de {formatCurrency(Number(metaSelecionada.valorTotal ?? 0))}
                </Text>
                <ProgressBar value={Number(metaSelecionada.valorTotal ?? 0) > 0 ? Math.min((Number(metaSelecionada.valorReservado ?? 0) / Number(metaSelecionada.valorTotal ?? 0)) * 100, 100) : 0} />
                {metaSelecionada.valorMensal ? (
                  <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 10 }}>Reserva mensal: {formatCurrency(Number(metaSelecionada.valorMensal))}</Text>
                ) : null}
                {metaSelecionada.descricao ? (
                  <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 10 }}>{metaSelecionada.descricao}</Text>
                ) : null}
              </Card>

              {acoesDaMeta(metaSelecionada).editar && (
              <View style={{ gap: 10, marginTop: 16 }}>
                {acoesDaMeta(metaSelecionada).adicionar && (
                <TouchableOpacity
                  onPress={() => abrirAdicionarValor(metaSelecionada, true)}
                  accessibilityRole="button"
                  style={{ height: 48, borderRadius: 12, backgroundColor: colors.brand, alignItems: 'center', justifyContent: 'center' }}
                >
                  <Text style={{ color: '#ffffff', fontSize: 15, fontWeight: '700' }}>Adicionar valor</Text>
                </TouchableOpacity>
                )}
                {acoesDaMeta(metaSelecionada).resgatar && (
                <TouchableOpacity
                  onPress={() => abrirRetirarValor(metaSelecionada)}
                  accessibilityRole="button"
                  style={{ height: 48, borderRadius: 12, borderWidth: 1, borderColor: colors.brand, alignItems: 'center', justifyContent: 'center' }}
                >
                  <Text style={{ color: colors.brandFg, fontSize: 15, fontWeight: '700' }}>Retirar valor</Text>
                </TouchableOpacity>
                )}
                {acoesDaMeta(metaSelecionada).excluir && (
                <TouchableOpacity
                  onPress={() => confirmarExcluirMeta(metaSelecionada)}
                  disabled={deletarMutation.status === 'pending'}
                  accessibilityRole="button"
                  style={{ height: 48, borderRadius: 12, borderWidth: 1, borderColor: colors.danger, alignItems: 'center', justifyContent: 'center' }}
                >
                  {deletarMutation.status === 'pending'
                    ? <ActivityIndicator color={colors.danger} size="small" />
                    : <Text style={{ color: colors.danger, fontSize: 15, fontWeight: '700' }}>Excluir meta</Text>}
                </TouchableOpacity>
                )}
              </View>
              )}
            </ScrollView>
          )}
        </View>
      </Modal>

      <Modal visible={modalAdicionarVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity onPress={() => { fecharMovimentacao('adicionar'); setValorAdicionar(''); setErroAdicionar(null); }} accessibilityRole="button">
              <Text style={{ color: colors.brandFg, fontSize: 15 }}>Cancelar</Text>
            </TouchableOpacity>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '600' }}>Adicionar Valor</Text>
            <TouchableOpacity
              disabled={adicionarMutation.status === 'pending'}
              accessibilityRole="button"
              onPress={() => {
                setErroAdicionar(null); setErroCarteira(null);
                const v = parseCurrencyBR(valorAdicionar);
                if (isNaN(v) || v <= 0) { setErroAdicionar('Valor deve ser positivo.'); return; }
                if (!carteiraOrigemId) { setErroCarteira('Selecione de onde sai o dinheiro.'); return; }
                adicionarMutation.mutate({ id: metaSelecionada!.id, valor: v, carteiraId: carteiraOrigemId });
              }}
            >
              {adicionarMutation.status === 'pending'
                ? <ActivityIndicator color={colors.brand} size="small" />
                : <Text style={{ color: colors.brandFg, fontSize: 15, fontWeight: '600' }}>Adicionar</Text>}
            </TouchableOpacity>
          </View>
          <ScrollView contentContainerStyle={{ padding: 16 }} keyboardShouldPersistTaps="handled">
            <Field testID="goal-add-value" label="Valor" value={valorAdicionar} onChangeText={(t) => setValorAdicionar(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={erroAdicionar} autoFocus />

            <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginTop: 8, marginBottom: 6, textTransform: 'uppercase' }}>Sai de</Text>
            {carteiras.length === 0 ? (
              <Text style={{ color: colors.textSecondary, fontSize: 13 }}>Você ainda não tem contas. Crie uma em Mais → Contas para reservar dinheiro.</Text>
            ) : (
              <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }} keyboardShouldPersistTaps="handled">
                {carteiras.map(c => (
                  <Chip
                    key={c.id}
                    label={`${c.nome} · ${formatCurrency(Number(c.saldo ?? 0))}`}
                    selected={carteiraOrigemId === c.id}
                    onPress={() => { setCarteiraOrigemId(c.id); setErroCarteira(null); }}
                  />
                ))}
              </ScrollView>
            )}
            {erroCarteira && <Text style={{ color: colors.danger, fontSize: 12, marginTop: 8 }}>{erroCarteira}</Text>}
          </ScrollView>
        </View>
      </Modal>

      <Modal visible={modalRemoverVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity onPress={() => { fecharMovimentacao('remover'); setValorRemover(''); setErroRemover(null); }} accessibilityRole="button">
              <Text style={{ color: colors.brandFg, fontSize: 15 }}>Cancelar</Text>
            </TouchableOpacity>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '600' }}>Retirar Valor</Text>
            <TouchableOpacity
              disabled={removerMutation.status === 'pending'}
              accessibilityRole="button"
              onPress={() => {
                setErroRemover(null); setErroCarteiraDestino(null);
                const v = parseCurrencyBR(valorRemover);
                if (isNaN(v) || v <= 0) { setErroRemover('Valor deve ser positivo.'); return; }
                if (metaSelecionada && v > Number(metaSelecionada.valorReservado ?? 0)) { setErroRemover('Valor maior que o reservado.'); return; }
                if (!carteiraDestinoId) { setErroCarteiraDestino('Selecione para onde volta o dinheiro.'); return; }
                removerMutation.mutate({ id: metaSelecionada!.id, valor: v, carteiraId: carteiraDestinoId });
              }}
            >
              {removerMutation.status === 'pending'
                ? <ActivityIndicator color={colors.brand} size="small" />
                : <Text style={{ color: colors.brandFg, fontSize: 15, fontWeight: '600' }}>Retirar</Text>}
            </TouchableOpacity>
          </View>
          <ScrollView contentContainerStyle={{ padding: 16 }} keyboardShouldPersistTaps="handled">
            <Field label="Valor" value={valorRemover} onChangeText={(t) => setValorRemover(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={erroRemover} autoFocus />

            <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginTop: 8, marginBottom: 6, textTransform: 'uppercase' }}>Volta para</Text>
            {carteiras.length === 0 ? (
              <Text style={{ color: colors.textSecondary, fontSize: 13 }}>Você ainda não tem contas. Crie uma em Mais → Contas para receber o valor.</Text>
            ) : (
              <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }} keyboardShouldPersistTaps="handled">
                {carteiras.map(c => (
                  <Chip
                    key={c.id}
                    label={`${c.nome} · ${formatCurrency(Number(c.saldo ?? 0))}`}
                    selected={carteiraDestinoId === c.id}
                    onPress={() => { setCarteiraDestinoId(c.id); setErroCarteiraDestino(null); }}
                  />
                ))}
              </ScrollView>
            )}
            {erroCarteiraDestino && <Text style={{ color: colors.danger, fontSize: 12, marginTop: 8 }}>{erroCarteiraDestino}</Text>}
          </ScrollView>
        </View>
      </Modal>

      <Modal visible={modalCriarVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity
              accessibilityRole="button"
              onPress={() => { setModalCriarVisible(false); resetFormularioMeta(); }}
            >
              <Text style={{ color: colors.brandFg, fontSize: 15 }}>Cancelar</Text>
            </TouchableOpacity>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '600' }}>{editandoMeta ? 'Editar Meta' : 'Criar Meta'}</Text>
            <TouchableOpacity
              disabled={criarMutation.status === 'pending' || atualizarMutation.status === 'pending'}
              accessibilityRole="button"
              onPress={() => {
                const payload = montarPayloadMeta();
                if (!payload) return;
                if (editandoMeta) {
                  atualizarMutation.mutate({ id: editandoMeta.id, payload });
                } else {
                  criarMutation.mutate(payload);
                }
              }}
            >
              {criarMutation.status === 'pending' || atualizarMutation.status === 'pending'
                ? <ActivityIndicator color={colors.brand} size="small" />
                : <Text style={{ color: colors.brandFg, fontSize: 15, fontWeight: '600' }}>Salvar</Text>}
            </TouchableOpacity>
          </View>
          <ScrollView contentContainerStyle={{ padding: 16 }} keyboardShouldPersistTaps="handled">
            {!editandoMeta ? (
              <View style={{ marginBottom: 16 }}>
                <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Como guardar o dinheiro</Text>
                {MODALIDADES.map(m => (
                  <TouchableOpacity
                    key={m.id}
                    onPress={() => setModalidadeCriar(m.id)}
                    accessibilityRole="radio"
                    accessibilityState={{ selected: modalidadeCriar === m.id }}
                    accessibilityLabel={`${m.titulo}: ${m.descricao}`}
                    style={{ borderWidth: 1.5, borderColor: modalidadeCriar === m.id ? colors.brand : colors.border, backgroundColor: modalidadeCriar === m.id ? colors.brandBg : colors.card, borderRadius: 12, padding: 12, marginBottom: 8 }}
                  >
                    <Text style={{ color: modalidadeCriar === m.id ? colors.brandFg : colors.textPrimary, fontSize: 14, fontWeight: '700' }}>{m.titulo}</Text>
                    <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 2 }}>{m.descricao}</Text>
                  </TouchableOpacity>
                ))}
                <Text style={{ color: colors.textMuted, fontSize: 11 }}>A escolha é definitiva para esta meta.</Text>
                {modalidadeError && <Text style={{ color: colors.danger, fontSize: 12, marginTop: 4 }}>{modalidadeError}</Text>}
              </View>
            ) : (
              <View style={{ marginBottom: 16 }}>
                <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Modalidade</Text>
                <Text style={{ color: colors.textPrimary, fontSize: 14, fontWeight: '600' }}>
                  {editandoMeta.modalidade === 'RESERVA_VIRTUAL' ? 'Reserva virtual' : 'Cofre real'}
                </Text>
                <Text style={{ color: colors.textMuted, fontSize: 11, marginTop: 2 }}>Definida na criação — não pode ser alterada.</Text>
              </View>
            )}
            <Field testID="goal-name" label="Nome" value={nomeCriar} onChangeText={setNomeCriar} placeholder="Ex: Reserva de emergência" error={nomeError} returnKeyType="next" submitBehavior="submit" onSubmitEditing={() => refValorTotal.current?.focus()} />
            <Field ref={refValorTotal} testID="goal-total" label="Valor total" value={valorTotalCriar} onChangeText={(t) => setValorTotalCriar(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={valorTotalError} returnKeyType="next" submitBehavior="submit" onSubmitEditing={() => refValorMensal.current?.focus()} />
            <Field ref={refValorMensal} label="Valor mensal (opcional)" value={valorMensalCriar} onChangeText={(t) => setValorMensalCriar(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={valorMensalError} returnKeyType="next" submitBehavior="submit" onSubmitEditing={() => refDataLimite.current?.focus()} />
            <Field ref={refDataLimite} label="Data limite" value={dataLimiteCriar} onChangeText={(t) => setDataLimiteCriar(maskDateInput(t))} placeholder="DD/MM/AAAA" keyboardType="number-pad" error={dataLimiteError} returnKeyType="next" submitBehavior="submit" onSubmitEditing={() => refDescricao.current?.focus()} />
            <Field ref={refDescricao} label="Descrição (opcional)" value={descricaoCriar} onChangeText={setDescricaoCriar} returnKeyType="done" />
            {erroCriar && <Text style={{ color: colors.danger, marginTop: 8 }}>{erroCriar}</Text>}
          </ScrollView>
        </View>
      </Modal>
    </View>
  );
}
