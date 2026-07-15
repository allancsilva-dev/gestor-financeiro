import React, { useState } from 'react';
import { View, Text, FlatList, TouchableOpacity, Modal, ScrollView, ActivityIndicator, Alert } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { metaService } from '../../src/services/metaService';
import { carteiraService } from '../../src/services/carteiraService';
import { formatCurrency, formatPercent, formatDate, parseDateBR, isValidDateBR, parseCurrencyBR, maskCurrencyInput, maskDateInput } from '../../src/utils/format';
import { Meta, MetaRequest, StatusMeta } from '../../src/types';
import { useTheme } from '../../src/theme';
import SkeletonBox from '../../src/components/ui/SkeletonBox';
import Card from '../../src/components/ui/Card';
import IconTile from '../../src/components/ui/IconTile';
import Badge from '../../src/components/ui/Badge';
import ProgressBar from '../../src/components/ui/ProgressBar';
import Fab from '../../src/components/ui/Fab';
import Field from '../../src/components/ui/Field';
import Chip from '../../src/components/ui/Chip';

export default function Metas() {
  const colors = useTheme();
  const queryClient = useQueryClient();
  const insets = useSafeAreaInsets();

  const [modalAdicionarVisible, setModalAdicionarVisible] = useState(false);
  const [modalRemoverVisible, setModalRemoverVisible] = useState(false);
  const [modalCriarVisible, setModalCriarVisible] = useState(false);
  const [modalDetalheVisible, setModalDetalheVisible] = useState(false);
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
  const [valorTotalCriar, setValorTotalCriar] = useState('');
  const [valorMensalCriar, setValorMensalCriar] = useState('');
  const [dataLimiteCriar, setDataLimiteCriar] = useState('');
  const [descricaoCriar, setDescricaoCriar] = useState('');
  const [nomeError, setNomeError] = useState<string | null>(null);
  const [valorTotalError, setValorTotalError] = useState<string | null>(null);
  const [valorMensalError, setValorMensalError] = useState<string | null>(null);
  const [dataLimiteError, setDataLimiteError] = useState<string | null>(null);

  const [statusFiltro, setStatusFiltro] = useState<StatusMeta>('ATIVA');

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['metas', statusFiltro],
    queryFn: () => metaService.listar(statusFiltro),
  });

  const { data: carteirasData } = useQuery({
    queryKey: ['carteiras'],
    queryFn: () => carteiraService.listar(),
  });
  const carteiras = carteirasData?.content ?? [];

  const resetFormularioMeta = () => {
    setEditandoMeta(null);
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
    setEditandoMeta(meta);
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

  const montarPayloadMeta = (): MetaRequest | null => {
    setNomeError(null); setValorTotalError(null); setValorMensalError(null); setDataLimiteError(null); setErroCriar(null);
    let hasErr = false;
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
    };
  };

  const adicionarMutation = useMutation({
    mutationFn: ({ id, valor, carteiraId }: { id: number; valor: number; carteiraId: number }) =>
      metaService.adicionarValor(id, valor, carteiraId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['metas'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-resumo'] });
      queryClient.invalidateQueries({ queryKey: ['carteiras'] });
      setModalAdicionarVisible(false);
    },
    onError: (err: any) => setErroAdicionar(err?.userMessage ?? 'Erro ao adicionar.'),
  });

  const removerMutation = useMutation({
    mutationFn: ({ id, valor, carteiraId }: { id: number; valor: number; carteiraId: number }) =>
      metaService.removerValor(id, valor, carteiraId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['metas'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-resumo'] });
      queryClient.invalidateQueries({ queryKey: ['carteiras'] });
      setModalRemoverVisible(false);
      setModalDetalheVisible(false);
    },
    onError: (err: any) => setErroRemover(err?.userMessage ?? 'Erro ao retirar.'),
  });

  const criarMutation = useMutation({
    mutationFn: (payload: MetaRequest) => metaService.criar(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['metas'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-resumo'] });
      setModalCriarVisible(false);
      resetFormularioMeta();
    },
    onError: (err: any) => setErroCriar(err?.userMessage ?? 'Erro ao criar meta.'),
  });

  const atualizarMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: MetaRequest }) => metaService.atualizar(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['metas'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-resumo'] });
      setModalCriarVisible(false);
      resetFormularioMeta();
    },
    onError: (err: any) => setErroCriar(err?.userMessage ?? 'Erro ao salvar meta.'),
  });

  const deletarMutation = useMutation({
    mutationFn: (id: number) => metaService.deletar(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['metas'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-resumo'] });
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

  const renderItem = ({ item: meta }: { item: Meta }) => {
    const progresso = Number(meta.valorTotal ?? 0) > 0
      ? Math.min((Number(meta.valorReservado ?? 0) / Number(meta.valorTotal ?? 0)) * 100, 100)
      : 0;

    // status é canônico; fallback pela `ativa` cobre respostas de backend antigo
    const arquivada = meta.status === 'ARQUIVADA';
    const concluida = meta.status ? meta.status === 'CONCLUIDA' : (!meta.ativa || progresso >= 100);

    return (
      <TouchableOpacity
        activeOpacity={0.82}
        accessibilityRole="button"
        accessibilityLabel={`Abrir detalhes da meta ${meta.nome}`}
        onPress={() => { setMetaSelecionada(meta); setModalDetalheVisible(true); }}
      >
      <Card radius={20} style={{ marginBottom: 12 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 12 }}>
          <IconTile tone={concluida ? 'success' : 'brand'} size={44}>{concluida ? '🏆' : '🎯'}</IconTile>
          <View style={{ flex: 1, minWidth: 0 }}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
              <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '700', flex: 1 }} numberOfLines={1}>{meta.nome}</Text>
              <Badge tone={arquivada ? 'info' : concluida ? 'success' : 'brand'}>
                {arquivada ? 'Arquivada' : concluida ? 'Concluída' : 'Ativa'}
              </Badge>
            </View>
            <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 2, fontVariant: ['tabular-nums'] }}>
              {formatCurrency(Number(meta.valorReservado ?? 0))} de {formatCurrency(Number(meta.valorTotal ?? 0))}
            </Text>
          </View>
        </View>

        <ProgressBar value={progresso} />

        <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 8 }}>
          <Text style={{ color: concluida ? colors.success : colors.brandFg, fontSize: 12, fontWeight: '700' }}>{formatPercent(progresso)}</Text>
          {meta.dataPrevista && <Text style={{ color: colors.textSecondary, fontSize: 11 }}>até {formatDate(meta.dataPrevista)}</Text>}
        </View>

        {!arquivada && (
          <TouchableOpacity
            onPress={() => { setValorAdicionar('0'); setErroAdicionar(null); setErroCarteira(null); setCarteiraOrigemId(carteiras.length === 1 ? carteiras[0].id : null); setMetaSelecionada(meta); setModalAdicionarVisible(true); }}
            accessibilityRole="button"
            accessibilityLabel={`Adicionar valor à meta ${meta.nome}`}
            style={{ marginTop: 12, paddingVertical: 8, paddingHorizontal: 14, borderRadius: 999, borderWidth: 1, borderColor: colors.brand, alignSelf: 'flex-start' }}
          >
            <Text style={{ color: colors.brandFg, fontSize: 12, fontWeight: '600' }}>+ Adicionar</Text>
          </TouchableOpacity>
        )}
      </Card>
      </TouchableOpacity>
    );
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={{ paddingTop: insets.top + 16, paddingHorizontal: 16, paddingBottom: 12 }}>
        <Text style={{ color: colors.textPrimary, fontSize: 23, fontWeight: '800', letterSpacing: -0.4 }}>Planejamento</Text>
        <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4 }}>Seu progresso rumo aos objetivos</Text>
        <View style={{ flexDirection: 'row', gap: 8, marginTop: 12 }}>
          <Chip label="Ativas" selected={statusFiltro === 'ATIVA'} onPress={() => setStatusFiltro('ATIVA')} />
          <Chip label="Concluídas" selected={statusFiltro === 'CONCLUIDA'} onPress={() => setStatusFiltro('CONCLUIDA')} />
          <Chip label="Arquivadas" selected={statusFiltro === 'ARQUIVADA'} onPress={() => setStatusFiltro('ARQUIVADA')} />
        </View>
      </View>

      {isLoading ? (
        <View style={{ paddingHorizontal: 16, gap: 12 }}>
          {[1, 2, 3].map(i => <SkeletonBox key={i} width="100%" height={140} borderRadius={18} />)}
        </View>
      ) : isError ? (
        <View style={{ alignItems: 'center', padding: 48 }}>
          <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>Erro ao carregar metas</Text>
          <TouchableOpacity onPress={() => refetch()} style={{ marginTop: 8 }} accessibilityRole="button">
            <Text style={{ color: colors.brandFg, fontWeight: '600' }}>Tentar novamente</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <FlatList
          data={data?.content ?? []}
          keyExtractor={m => m.id.toString()}
          renderItem={renderItem}
          contentContainerStyle={{ paddingHorizontal: 16, paddingBottom: 96 }}
          ListEmptyComponent={() => (
            <View style={{ alignItems: 'center', padding: 48 }}>
              <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>
                {statusFiltro === 'ATIVA' ? 'Nenhuma meta ainda'
                  : statusFiltro === 'CONCLUIDA' ? 'Nenhuma meta concluída'
                  : 'Nenhuma meta arquivada'}
              </Text>
              {statusFiltro === 'ATIVA' && (
                <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4 }}>Toque no + para criar a primeira</Text>
              )}
            </View>
          )}
        />
      )}

      <Fab onPress={abrirCriarMeta} accessibilityLabel="Criar meta" />

      <Modal visible={modalDetalheVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity onPress={() => setModalDetalheVisible(false)} accessibilityRole="button">
              <Text style={{ color: colors.brandFg, fontSize: 15 }}>Fechar</Text>
            </TouchableOpacity>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700' }}>Detalhes da Meta</Text>
            <TouchableOpacity
              onPress={() => metaSelecionada && abrirEditarMeta(metaSelecionada)}
              accessibilityRole="button"
            >
              <Text style={{ color: colors.brandFg, fontSize: 15, fontWeight: '700' }}>Editar</Text>
            </TouchableOpacity>
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

              <View style={{ gap: 10, marginTop: 16 }}>
                <TouchableOpacity
                  onPress={() => { setValorAdicionar('0'); setErroAdicionar(null); setErroCarteira(null); setCarteiraOrigemId(carteiras.length === 1 ? carteiras[0].id : null); setModalAdicionarVisible(true); }}
                  accessibilityRole="button"
                  style={{ height: 48, borderRadius: 12, backgroundColor: colors.brand, alignItems: 'center', justifyContent: 'center' }}
                >
                  <Text style={{ color: '#ffffff', fontSize: 15, fontWeight: '700' }}>Adicionar valor</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  onPress={() => { setValorRemover('0'); setErroRemover(null); setErroCarteiraDestino(null); setCarteiraDestinoId(carteiras.length === 1 ? carteiras[0].id : null); setModalRemoverVisible(true); }}
                  accessibilityRole="button"
                  style={{ height: 48, borderRadius: 12, borderWidth: 1, borderColor: colors.brand, alignItems: 'center', justifyContent: 'center' }}
                >
                  <Text style={{ color: colors.brandFg, fontSize: 15, fontWeight: '700' }}>Retirar valor</Text>
                </TouchableOpacity>
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
              </View>
            </ScrollView>
          )}
        </View>
      </Modal>

      <Modal visible={modalAdicionarVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity onPress={() => { setModalAdicionarVisible(false); setValorAdicionar(''); setErroAdicionar(null); }} accessibilityRole="button">
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
            <Field label="Valor" value={valorAdicionar} onChangeText={(t) => setValorAdicionar(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={erroAdicionar} autoFocus />

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
            <TouchableOpacity onPress={() => { setModalRemoverVisible(false); setValorRemover(''); setErroRemover(null); }} accessibilityRole="button">
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
            <Field label="Nome" value={nomeCriar} onChangeText={setNomeCriar} placeholder="Ex: Reserva de emergência" error={nomeError} autoFocus />
            <Field label="Valor total" value={valorTotalCriar} onChangeText={(t) => setValorTotalCriar(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={valorTotalError} />
            <Field label="Valor mensal (opcional)" value={valorMensalCriar} onChangeText={(t) => setValorMensalCriar(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={valorMensalError} />
            <Field label="Data limite" value={dataLimiteCriar} onChangeText={(t) => setDataLimiteCriar(maskDateInput(t))} placeholder="DD/MM/AAAA" keyboardType="number-pad" error={dataLimiteError} />
            <Field label="Descrição (opcional)" value={descricaoCriar} onChangeText={setDescricaoCriar} />
            {erroCriar && <Text style={{ color: colors.danger, marginTop: 8 }}>{erroCriar}</Text>}
          </ScrollView>
        </View>
      </Modal>
    </View>
  );
}
