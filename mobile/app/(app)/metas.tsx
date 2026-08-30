import React, { useEffect, useRef, useState } from 'react';
import { View, Text, FlatList, TouchableOpacity, ScrollView, Alert, TextInput } from 'react-native';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { metaService } from '../../src/services/metaService';
import contaFinanceiraService from '../../src/services/contaFinanceiraService';
import { formatCurrency, formatDate, parseDateBR, isValidDateBR, parseCurrencyBR, maskCurrencyInput, maskDateInput } from '../../src/utils/format';
import { ContaFinanceira, Meta, MetaRequest, ModalidadeMeta, StatusMeta } from '../../src/types';
import { useTheme, useTabBarSpace, numeric, radius, screenPadding, spacing, typography } from '../../src/theme';
import { paletaDaMeta } from '../../src/theme/metaCores';
import { mensagemDeErro } from '../../src/utils/erros';
import SkeletonBox from '../../src/components/ui/SkeletonBox';
import Card from '../../src/components/ui/Card';
import IconTile from '../../src/components/ui/IconTile';
import Badge from '../../src/components/ui/Badge';
import ProgressBar from '../../src/components/ui/ProgressBar';
import Field from '../../src/components/ui/Field';
import Chip from '../../src/components/ui/Chip';
import { acoesDaMeta } from '../../src/domain/metaPolicy';
import CardMeta from '../../src/components/metas/CardMeta';
import CabecalhoDeTela from '../../src/components/ui/CabecalhoDeTela';
import CabecalhoSecao from '../../src/components/ui/CabecalhoSecao';
import EstadoVazio from '../../src/components/ui/EstadoVazio';
import Botao from '../../src/components/ui/Botao';
import FolhaModal from '../../src/components/ui/FolhaModal';
import RotuloDeGrupo from '../../src/components/ui/RotuloDeGrupo';
import SeletorDeEmoji from '../../src/components/ui/SeletorDeEmoji';
import { EMOJI_GENERICO, emojiDaCategoria, emojiSugerido } from '../../src/domain/iconeCategoria';
import { e } from '../../src/theme/escala';

/**
 * O iOS não apresenta uma `pageSheet` enquanto a anterior ainda está fechando:
 * a segunda simplesmente não aparece. Por isso o encadeamento espera a animação.
 */
const ESPERA_FOLHA = 350;

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
  const queryClient = useQueryClient();

  const [modalAdicionarVisible, setModalAdicionarVisible] = useState(false);
  const [modalRemoverVisible, setModalRemoverVisible] = useState(false);
  const [modalCriarVisible, setModalCriarVisible] = useState(false);
  const [modalDetalheVisible, setModalDetalheVisible] = useState(false);
  const [retornarAoDetalheAposMovimentacao, setRetornarAoDetalheAposMovimentacao] = useState(false);
  const [editandoMeta, setEditandoMeta] = useState<Meta | null>(null);
  const [metaSelecionada, setMetaSelecionada] = useState<Meta | null>(null);
  const [erroCriar, setErroCriar] = useState<string | null>(null);
  const [erroAporte, setErroAporte] = useState<string | null>(null);
  const [carteiraOrigemAporte, setCarteiraOrigemAporte] = useState<number | null>(null);
  const [diaDoAporte] = useState<number>(5);
  const [valorAdicionar, setValorAdicionar] = useState('');
  const [valorRemover, setValorRemover] = useState('');
  const [erroAdicionar, setErroAdicionar] = useState<string | null>(null);
  const [erroRemover, setErroRemover] = useState<string | null>(null);
  const [carteiraOrigemId, setCarteiraOrigemId] = useState<number | null>(null);
  const [carteiraDestinoId, setCarteiraDestinoId] = useState<number | null>(null);
  const [erroCarteira, setErroCarteira] = useState<string | null>(null);
  const [erroCarteiraDestino, setErroCarteiraDestino] = useState<string | null>(null);

  const [nomeCriar, setNomeCriar] = useState('');
  // `null` = acompanha o nome digitado; depois do primeiro toque na grade a
  // escolha do usuário manda. Mesma regra da tela de categorias.
  const [iconeCriar, setIconeCriar] = useState<string | null>(null);
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

  // Um timer só, sempre cancelado: sem isso um `setState` disparava depois da
  // tela sair, e trocar de folha rápido deixava dois agendamentos correndo.
  const timerFolha = useRef<ReturnType<typeof setTimeout> | null>(null);
  const aoFecharAFolha = (acao: () => void) => {
    if (timerFolha.current) clearTimeout(timerFolha.current);
    timerFolha.current = setTimeout(acao, ESPERA_FOLHA);
  };
  useEffect(() => () => {
    if (timerFolha.current) clearTimeout(timerFolha.current);
  }, []);

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
    setIconeCriar(null);
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
    setIconeCriar(emojiDaCategoria(meta, EMOJI_GENERICO));
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

  // Aporte automático: ligar move dinheiro todo mês, então é opt-in explícito por meta.
  const aporteMutation = useMutation({
    mutationFn: ({ id, dados }: {
      id: number;
      dados: { ativo: boolean; dia?: number; carteiraId?: number };
    }) => metaService.configurarAporteAutomatico(id, dados),
    onSuccess: (meta) => {
      setMetaSelecionada(meta);
      setErroAporte(null);
      queryClient.invalidateQueries({ queryKey: ['metas'] });
    },
    onError: (err) => setErroAporte(mensagemDeErro(err)),
  });

  const alternarAporte = (meta: Meta) => {
    if (meta.aporteAutomatico) {
      aporteMutation.mutate({ id: meta.id, dados: { ativo: false } });
      return;
    }
    if (!meta.valorMensal) {
      setErroAporte('Defina quanto guardar por mês antes de automatizar.');
      return;
    }
    if (!carteiraOrigemAporte) {
      setErroAporte('Escolha de qual conta o valor sai.');
      return;
    }
    aporteMutation.mutate({
      id: meta.id,
      dados: { ativo: true, dia: diaDoAporte, carteiraId: carteiraOrigemAporte },
    });
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
      aoFecharAFolha(() => setModalAdicionarVisible(true));
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
    aoFecharAFolha(() => setModalRemoverVisible(true));
  };

  const fecharMovimentacao = (tipo: 'adicionar' | 'remover') => {
    if (tipo === 'adicionar') setModalAdicionarVisible(false);
    else setModalRemoverVisible(false);
    const deveRetornar = retornarAoDetalheAposMovimentacao;
    setRetornarAoDetalheAposMovimentacao(false);
    if (deveRetornar) aoFecharAFolha(() => setModalDetalheVisible(true));
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
      icone: iconeCriar ?? emojiSugerido(nomeCriar) ?? EMOJI_GENERICO,
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
    onError: (erro: unknown) => {
      // backend bloqueia exclusão com valor reservado: resgate primeiro (ADR-0004).
      // A mensagem vem do envelope já normalizado pelo interceptor — ler
      // `response.data` na mão devolvia `undefined` quando o formato mudava.
      Alert.alert('Meta não excluída', mensagemDeErro(erro, 'Não foi possível excluir a meta.'));
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
              {/* A faixa de filtros não está no mock medido — usa token, não `e()`.
                  Rola na horizontal porque "Concluídas" e "Arquivadas" juntas
                  espremem os três chips em tela estreita. */}
              <ScrollView
                horizontal
                showsHorizontalScrollIndicator={false}
                contentContainerStyle={{ gap: spacing.sm, paddingHorizontal: screenPadding, paddingTop: spacing.md }}
              >
                <Chip label="Ativas" selected={statusFiltro === 'ATIVA'} onPress={() => setStatusFiltro('ATIVA')} />
                <Chip label="Concluídas" selected={statusFiltro === 'CONCLUIDA'} onPress={() => setStatusFiltro('CONCLUIDA')} />
                <Chip label="Arquivadas" selected={statusFiltro === 'ARQUIVADA'} onPress={() => setStatusFiltro('ARQUIVADA')} />
              </ScrollView>
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

      <FolhaModal
        visible={modalDetalheVisible}
        titulo="Detalhes da Meta"
        rotuloFechar="Fechar"
        onFechar={() => setModalDetalheVisible(false)}
        acao={metaSelecionada && acoesDaMeta(metaSelecionada).editar
          ? { rotulo: 'Editar', onPress: () => metaSelecionada && abrirEditarMeta(metaSelecionada) }
          : undefined}
      >
        {metaSelecionada && (
          <ScrollView contentContainerStyle={{ padding: spacing.lg }} keyboardShouldPersistTaps="handled">
            <Card radius={radius.xl}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md, marginBottom: spacing.md }}>
                <IconTile tone={metaSelecionada.ativa ? 'brand' : 'success'} size={44}>{emojiDaCategoria(metaSelecionada, '🎯')}</IconTile>
                <View style={{ flex: 1, minWidth: 0 }}>
                  <Text style={{ ...typography.section, color: colors.textPrimary }} numberOfLines={2}>{metaSelecionada.nome}</Text>
                  <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.xxs }}>
                    {metaSelecionada.dataPrevista ? `até ${formatDate(metaSelecionada.dataPrevista)}` : 'Sem data limite'}
                  </Text>
                </View>
                <Badge tone={metaSelecionada.status === 'ARQUIVADA' ? 'info' : metaSelecionada.status === 'CONCLUIDA' ? 'success' : 'brand'}>
                  {metaSelecionada.status === 'ARQUIVADA' ? 'Arquivada' : metaSelecionada.status === 'CONCLUIDA' ? 'Concluída' : 'Ativa'}
                </Badge>
              </View>
              <Text style={{ ...typography.meta, ...numeric, color: colors.textSecondary, marginBottom: spacing.sm }}>
                {formatCurrency(Number(metaSelecionada.valorReservado ?? 0))} de {formatCurrency(Number(metaSelecionada.valorTotal ?? 0))}
              </Text>
              {/* Mesma cor da meta que o card da lista usa: a entidade tem cor
                  própria, então a barra não vira ciano dentro da folha. */}
              <ProgressBar
                value={Number(metaSelecionada.valorTotal ?? 0) > 0 ? Math.min((Number(metaSelecionada.valorReservado ?? 0) / Number(metaSelecionada.valorTotal ?? 0)) * 100, 100) : 0}
                paleta={paletaDaMeta(metaSelecionada, colors.card)}
                accessibilityLabel={`Progresso de ${metaSelecionada.nome}`}
              />
              {metaSelecionada.valorMensal ? (
                <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.md }}>
                  Reserva mensal: {formatCurrency(Number(metaSelecionada.valorMensal))}
                </Text>
              ) : null}
              {metaSelecionada.descricao ? (
                <Text style={{ ...typography.body, color: colors.textSecondary, marginTop: spacing.md }}>{metaSelecionada.descricao}</Text>
              ) : null}
            </Card>

            {acoesDaMeta(metaSelecionada).adicionar && (
              <Card radius={radius.xl} style={{ marginTop: spacing.lg }}>
                <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>Guardar sozinho</Text>
                <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.xxs }}>
                  {metaSelecionada.aporteAutomatico
                    ? `Todo dia ${metaSelecionada.aporteDia}, ${formatCurrency(Number(metaSelecionada.valorMensal ?? 0))} saem da conta escolhida para esta meta.`
                    : 'O app pode separar o valor mensal todo mês. Se faltar saldo, ele avisa em vez de deixar a conta negativa.'}
                </Text>

                {!metaSelecionada.aporteAutomatico && (
                  <>
                    <RotuloDeGrupo>Sai da conta</RotuloDeGrupo>
                    <SeletorDeConta
                      contas={carteiras}
                      selecionada={carteiraOrigemAporte}
                      onSelecionar={id => { setCarteiraOrigemAporte(id); setErroAporte(null); }}
                      vazio="Você ainda não tem contas. Crie uma em Mais → Contas."
                    />
                  </>
                )}

                {erroAporte && (
                  <Text accessibilityRole="alert" style={{ ...typography.meta, color: colors.danger, marginTop: spacing.sm }}>
                    {erroAporte}
                  </Text>
                )}

                <View style={{ marginTop: spacing.md }}>
                  <Botao
                    titulo={metaSelecionada.aporteAutomatico ? 'Desligar' : 'Guardar todo mês'}
                    variante={metaSelecionada.aporteAutomatico ? 'secundario' : 'primario'}
                    onPress={() => alternarAporte(metaSelecionada)}
                    carregando={aporteMutation.status === 'pending'}
                    testID="meta-aporte-automatico"
                  />
                </View>
              </Card>
            )}

            {acoesDaMeta(metaSelecionada).editar && (
              <View style={{ gap: spacing.md, marginTop: spacing.lg }}>
                {acoesDaMeta(metaSelecionada).adicionar && (
                  <Botao titulo="Adicionar valor" onPress={() => abrirAdicionarValor(metaSelecionada, true)} />
                )}
                {acoesDaMeta(metaSelecionada).resgatar && (
                  <Botao titulo="Retirar valor" variante="secundario" onPress={() => abrirRetirarValor(metaSelecionada)} />
                )}
                {acoesDaMeta(metaSelecionada).excluir && (
                  <Botao
                    titulo="Excluir meta"
                    variante="perigo"
                    onPress={() => confirmarExcluirMeta(metaSelecionada)}
                    carregando={deletarMutation.status === 'pending'}
                  />
                )}
              </View>
            )}
          </ScrollView>
        )}
      </FolhaModal>

      <FolhaModal
        visible={modalAdicionarVisible}
        titulo="Adicionar Valor"
        onFechar={() => { fecharMovimentacao('adicionar'); setValorAdicionar(''); setErroAdicionar(null); }}
        acao={{
          rotulo: 'Adicionar',
          carregando: adicionarMutation.status === 'pending',
          onPress: () => {
            setErroAdicionar(null); setErroCarteira(null);
            const v = parseCurrencyBR(valorAdicionar);
            if (isNaN(v) || v <= 0) { setErroAdicionar('Valor deve ser positivo.'); return; }
            if (!carteiraOrigemId) { setErroCarteira('Selecione de onde sai o dinheiro.'); return; }
            adicionarMutation.mutate({ id: metaSelecionada!.id, valor: v, carteiraId: carteiraOrigemId });
          },
        }}
      >
        <ScrollView contentContainerStyle={{ padding: spacing.lg }} keyboardShouldPersistTaps="handled">
          <Field testID="goal-add-value" label="Valor" value={valorAdicionar} onChangeText={(t) => setValorAdicionar(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={erroAdicionar} autoFocus />

          <RotuloDeGrupo>Sai de</RotuloDeGrupo>
          <SeletorDeConta
            contas={carteiras}
            selecionada={carteiraOrigemId}
            onSelecionar={id => { setCarteiraOrigemId(id); setErroCarteira(null); }}
            vazio="Você ainda não tem contas. Crie uma em Mais → Contas para reservar dinheiro."
          />
          {erroCarteira && (
            <Text accessibilityRole="alert" style={{ ...typography.meta, color: colors.danger, marginTop: spacing.sm }}>{erroCarteira}</Text>
          )}
        </ScrollView>
      </FolhaModal>

      <FolhaModal
        visible={modalRemoverVisible}
        titulo="Retirar Valor"
        onFechar={() => { fecharMovimentacao('remover'); setValorRemover(''); setErroRemover(null); }}
        acao={{
          rotulo: 'Retirar',
          carregando: removerMutation.status === 'pending',
          onPress: () => {
            setErroRemover(null); setErroCarteiraDestino(null);
            const v = parseCurrencyBR(valorRemover);
            if (isNaN(v) || v <= 0) { setErroRemover('Valor deve ser positivo.'); return; }
            if (metaSelecionada && v > Number(metaSelecionada.valorReservado ?? 0)) { setErroRemover('Valor maior que o reservado.'); return; }
            if (!carteiraDestinoId) { setErroCarteiraDestino('Selecione para onde volta o dinheiro.'); return; }
            removerMutation.mutate({ id: metaSelecionada!.id, valor: v, carteiraId: carteiraDestinoId });
          },
        }}
      >
        <ScrollView contentContainerStyle={{ padding: spacing.lg }} keyboardShouldPersistTaps="handled">
          <Field label="Valor" value={valorRemover} onChangeText={(t) => setValorRemover(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={erroRemover} autoFocus />

          <RotuloDeGrupo>Volta para</RotuloDeGrupo>
          <SeletorDeConta
            contas={carteiras}
            selecionada={carteiraDestinoId}
            onSelecionar={id => { setCarteiraDestinoId(id); setErroCarteiraDestino(null); }}
            vazio="Você ainda não tem contas. Crie uma em Mais → Contas para receber o valor."
          />
          {erroCarteiraDestino && (
            <Text accessibilityRole="alert" style={{ ...typography.meta, color: colors.danger, marginTop: spacing.sm }}>{erroCarteiraDestino}</Text>
          )}
        </ScrollView>
      </FolhaModal>

      <FolhaModal
        visible={modalCriarVisible}
        titulo={editandoMeta ? 'Editar Meta' : 'Criar Meta'}
        onFechar={() => { setModalCriarVisible(false); resetFormularioMeta(); }}
        acao={{
          rotulo: 'Salvar',
          carregando: criarMutation.status === 'pending' || atualizarMutation.status === 'pending',
          onPress: () => {
            const payload = montarPayloadMeta();
            if (!payload) return;
            if (editandoMeta) {
              atualizarMutation.mutate({ id: editandoMeta.id, payload });
            } else {
              criarMutation.mutate(payload);
            }
          },
        }}
      >
        <ScrollView contentContainerStyle={{ padding: spacing.lg }} keyboardShouldPersistTaps="handled">
            {!editandoMeta ? (
              <View style={{ marginBottom: spacing.lg }}>
                <RotuloDeGrupo primeiro>Como guardar o dinheiro</RotuloDeGrupo>
                {MODALIDADES.map(m => (
                  <TouchableOpacity
                    key={m.id}
                    onPress={() => setModalidadeCriar(m.id)}
                    accessibilityRole="radio"
                    accessibilityState={{ selected: modalidadeCriar === m.id }}
                    style={{
                      borderWidth: 1,
                      borderColor: modalidadeCriar === m.id ? colors.brand : colors.border,
                      backgroundColor: modalidadeCriar === m.id ? colors.brandBg : colors.card,
                      borderRadius: radius.md, padding: spacing.md, marginBottom: spacing.sm,
                    }}
                  >
                    <Text style={{ ...typography.body, fontWeight: '700', color: modalidadeCriar === m.id ? colors.brandFg : colors.textPrimary }}>{m.titulo}</Text>
                    <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.xxs }}>{m.descricao}</Text>
                  </TouchableOpacity>
                ))}
                <Text style={{ ...typography.meta, color: colors.textMuted }}>A escolha é definitiva para esta meta.</Text>
                {modalidadeError && (
                  <Text accessibilityRole="alert" style={{ ...typography.meta, color: colors.danger, marginTop: spacing.xs }}>{modalidadeError}</Text>
                )}
              </View>
            ) : (
              <View style={{ marginBottom: spacing.lg }}>
                <RotuloDeGrupo primeiro>Modalidade</RotuloDeGrupo>
                <Text style={{ ...typography.body, fontWeight: '600', color: colors.textPrimary }}>
                  {editandoMeta.modalidade === 'RESERVA_VIRTUAL' ? 'Reserva virtual' : 'Cofre real'}
                </Text>
                <Text style={{ ...typography.meta, color: colors.textMuted, marginTop: spacing.xxs }}>Definida na criação — não pode ser alterada.</Text>
              </View>
            )}
            <Field testID="goal-name" label="Nome" value={nomeCriar} onChangeText={setNomeCriar} placeholder="Ex: Reserva de emergência" error={nomeError} returnKeyType="next" submitBehavior="submit" onSubmitEditing={() => refValorTotal.current?.focus()} />

            <RotuloDeGrupo>Ícone</RotuloDeGrupo>
            <SeletorDeEmoji
              testID="goal-icon"
              rotulo="Ícone da meta"
              valor={iconeCriar ?? emojiSugerido(nomeCriar) ?? EMOJI_GENERICO}
              onChange={setIconeCriar}
            />
            <Field ref={refValorTotal} testID="goal-total" label="Valor total" value={valorTotalCriar} onChangeText={(t) => setValorTotalCriar(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={valorTotalError} returnKeyType="next" submitBehavior="submit" onSubmitEditing={() => refValorMensal.current?.focus()} />
            <Field ref={refValorMensal} label="Valor mensal (opcional)" value={valorMensalCriar} onChangeText={(t) => setValorMensalCriar(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={valorMensalError} returnKeyType="next" submitBehavior="submit" onSubmitEditing={() => refDataLimite.current?.focus()} />
            <Field ref={refDataLimite} label="Data limite" value={dataLimiteCriar} onChangeText={(t) => setDataLimiteCriar(maskDateInput(t))} placeholder="DD/MM/AAAA" keyboardType="number-pad" error={dataLimiteError} returnKeyType="next" submitBehavior="submit" onSubmitEditing={() => refDescricao.current?.focus()} />
            <Field ref={refDescricao} label="Descrição (opcional)" value={descricaoCriar} onChangeText={setDescricaoCriar} returnKeyType="done" />
            {erroCriar && (
              <Text accessibilityRole="alert" style={{ ...typography.meta, color: colors.danger, marginTop: spacing.sm }}>{erroCriar}</Text>
            )}
        </ScrollView>
      </FolhaModal>
    </View>
  );
}

/**
 * Faixa de contas de onde o dinheiro sai ou para onde volta. As duas folhas de
 * movimentação pediam a mesma coisa, com o mesmo texto de vazio duplicado.
 */
const SeletorDeConta = ({ contas, selecionada, onSelecionar, vazio }: {
  contas: ContaFinanceira[];
  selecionada: number | null;
  onSelecionar: (id: number) => void;
  vazio: string;
}) => {
  const colors = useTheme();
  if (contas.length === 0) {
    return <Text style={{ ...typography.body, color: colors.textSecondary }}>{vazio}</Text>;
  }
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={{ gap: spacing.sm }}
      keyboardShouldPersistTaps="handled"
    >
      {contas.map(c => (
        <Chip
          key={c.id}
          label={`${c.nome} · ${formatCurrency(Number(c.saldo ?? 0))}`}
          selected={selecionada === c.id}
          onPress={() => onSelecionar(c.id)}
        />
      ))}
    </ScrollView>
  );
};
