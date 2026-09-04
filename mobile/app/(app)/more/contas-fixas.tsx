import React, { useState } from 'react';
import { View, Text, FlatList, ScrollView, Switch, Alert } from 'react-native';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { contaFixaService } from '../../../src/services/contaFixaService';
import cartaoService from '../../../src/services/cartaoService';
import { categoriaService } from '../../../src/services/categoriaService';
import contaFinanceiraService from '../../../src/services/contaFinanceiraService';
import Badge from '../../../src/components/ui/Badge';
import Card from '../../../src/components/ui/Card';
import Chip from '../../../src/components/ui/Chip';
import IconTile from '../../../src/components/ui/IconTile';
import Field from '../../../src/components/ui/Field';
import { ContaFixa, ContaFixaRequest, FrequenciaRecorrencia } from '../../../src/types';
import { proximaCobranca, rotuloCadencia, usaAncora } from '../../../src/domain/recorrencia';
import { vencimentoDaCompraNoCartao } from '../../../src/domain/fatura';
import {
  useTheme, useTabBarSpace, numeric, radius, screenPadding, spacing, typography,
} from '../../../src/theme';
import { competenciaAtual, competenciaIso } from '../../../src/domain/periodo';
import { mensagemDeErro } from '../../../src/utils/erros';
import { parseCurrencyBR, maskCurrencyInput, maskDateInput, formatCurrency, formatNumber, formatDateOnlyBR, isValidDateBR, parseDateBR } from '../../../src/utils/format';
import Botao from '../../../src/components/ui/Botao';
import CabecalhoSubTela from '../../../src/components/ui/CabecalhoSubTela';
import EstadoVazio from '../../../src/components/ui/EstadoVazio';
import FolhaModal from '../../../src/components/ui/FolhaModal';
import RotuloDeGrupo from '../../../src/components/ui/RotuloDeGrupo';
import SeletorDeFrequencia from '../../../src/components/ui/SeletorDeFrequencia';
import SkeletonBox from '../../../src/components/ui/SkeletonBox';
import Fab from '../../../src/components/ui/Fab';
import type { ContaFinanceira } from '../../../src/types';
import { emojiDaCategoria } from '../../../src/domain/iconeCategoria';

type Aba = 'TODAS' | 'ASSINATURAS' | 'CANCELADAS';

const ABAS: { chave: Aba; rotulo: string }[] = [
  { chave: 'TODAS', rotulo: 'Todas' },
  // Assinatura é a cobrança que cai na fatura de um cartão. Filtro client-side: a
  // listagem de ativas já veio inteira, e uma query só para isso seria round-trip à toa.
  { chave: 'ASSINATURAS', rotulo: 'Assinaturas' },
  { chave: 'CANCELADAS', rotulo: 'Canceladas' },
];

export default function ContasFixasScreen() {
  const colors = useTheme();
  const tabBarSpace = useTabBarSpace();
  const queryClient = useQueryClient();
  const [modalPagarVisible, setModalPagarVisible] = useState(false);
  const [modalCriarVisible, setModalCriarVisible] = useState(false);
  const [selecionada, setSelecionada] = useState<ContaFixa | null>(null);
  const [valorPago, setValorPago] = useState('');
  const [erroPagar, setErroPagar] = useState<string | null>(null);
  const [carteiraPagamentoId, setCarteiraPagamentoId] = useState<number | null>(null);
  const [erroCarteira, setErroCarteira] = useState<string | null>(null);
  const [pulandoId, setPulandoId] = useState<number | null>(null);
  const [editando, setEditando] = useState<ContaFixa | null>(null);
  const [aba, setAba] = useState<Aba>('TODAS');

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['contas-fixas'],
    queryFn: () => contaFixaService.listar(),
  });

  // Segunda query, só quando a aba é aberta: cancelada nunca entra na listagem padrão.
  // A chave é prefixada por ['contas-fixas'] de propósito — invalidateQueries casa por
  // prefixo, então cancelar/reativar e as invalidações globais já a alcançam.
  const {
    data: canceladasData,
    isLoading: carregandoCanceladas,
    isError: erroCanceladas,
    refetch: recarregarCanceladas,
  } = useQuery({
    queryKey: ['contas-fixas', 'canceladas'],
    queryFn: () => contaFixaService.listar({ ativo: false }),
    enabled: aba === 'CANCELADAS',
  });

  const contas = data?.content ?? [];
  const emAberto = contas.filter(cf => cf.status === 'PENDENTE' || cf.status === 'ATRASADO');
  const totalAReceber = emAberto.filter(cf => cf.tipo === 'ENTRADA').reduce((acc, cf) => acc + Number(cf.valorPlanejado ?? 0), 0);
  const totalAPagar = emAberto.filter(cf => cf.tipo !== 'ENTRADA').reduce((acc, cf) => acc + Number(cf.valorPlanejado ?? 0), 0);

  // O resumo do cabeçalho sai sempre das ativas, nunca da lista exibida: na aba
  // Canceladas ele mostraria um total fantasma de cobranças que não vão acontecer.
  const canceladas = canceladasData?.content ?? [];
  const listaExibida = aba === 'CANCELADAS'
    ? canceladas
    : aba === 'ASSINATURAS'
      ? contas.filter(cf => cf.cartao != null)
      : contas;

  const { data: carteirasData } = useQuery({
    queryKey: ['contas-financeiras-caixa'],
    queryFn: () => contaFinanceiraService.listarParaCaixa(),
  });
  const carteiras = carteirasData ?? [];

  const { data: cartoesData } = useQuery({
    queryKey: ['cartoes'],
    queryFn: () => cartaoService.listarTodos(),
  });
  const cartoes = cartoesData ?? [];

  const pagarMutation = useMutation({
    mutationFn: ({ id, valor, carteiraId }: { id: number; valor: number; carteiraId?: number }) =>
      contaFixaService.marcarComoPaga(id, valor, carteiraId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contas-fixas'] });
      queryClient.invalidateQueries({ queryKey: ['carteiras'] });
      queryClient.invalidateQueries({ queryKey: ['transacoes-recentes'] });
      queryClient.invalidateQueries({ queryKey: ['recorrencias-falhas'] });
      setModalPagarVisible(false);
      setValorPago('');
    },
    onError: (err: unknown) => setErroPagar(mensagemDeErro(err, 'Erro ao registrar pagamento.')),
  });

  const pularMes = (cf: ContaFixa) => {
    if (pulandoId != null || pagarMutation.status === 'pending') return;
    Alert.alert('Pular este mês?', `${cf.nome} não será cobrada neste mês.`, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Pular',
        onPress: () => {
          setPulandoId(cf.id);
          contaFixaService.pularMes(cf.id)
            .then(() => refetch())
            .catch((err: unknown) => Alert.alert('Não foi possível pular', mensagemDeErro(err, 'Tente novamente.')))
            .finally(() => setPulandoId(null));
        },
      },
    ]);
  };

  const cancelarMutation = useMutation({
    mutationFn: (id: number) => contaFixaService.deletar(id),
    // Prefixo: alcança também ['contas-fixas','canceladas'], onde a conta reaparece.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['contas-fixas'] }),
    onError: (err: unknown) =>
      Alert.alert('Não foi possível cancelar', mensagemDeErro(err, 'Tente novamente.')),
  });

  const reativarMutation = useMutation({
    mutationFn: (id: number) => contaFixaService.reativar(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['contas-fixas'] }),
    // Reativar com o cartão excluído volta 422 com a mensagem de negócio pronta
    // (o interceptor já normaliza o envelope); repassá-la diz o que fazer.
    onError: (err: unknown) =>
      Alert.alert('Não foi possível reativar', mensagemDeErro(err, 'Tente novamente.')),
  });

  const confirmarCancelamento = (cf: ContaFixa) => {
    Alert.alert(
      'Cancelar assinatura?',
      `${cf.nome} deixa de ser cobrada. As cobranças que já entraram na fatura continuam lá.`,
      [
        { text: 'Voltar', style: 'cancel' },
        {
          text: 'Cancelar assinatura',
          style: 'destructive',
          onPress: () => cancelarMutation.mutate(cf.id),
        },
      ],
    );
  };

  // criar conta fixa
  const [descricaoCriar, setDescricaoCriar] = useState('');
  const [valorCriar, setValorCriar] = useState('');
  const [diaCriar, setDiaCriar] = useState('');
  const [categoriaCriarId, setCategoriaCriarId] = useState<number | null>(null);
  const [recorrenteCriar, setRecorrenteCriar] = useState(true);
  const [frequenciaCriar, setFrequenciaCriar] = useState<FrequenciaRecorrencia>('MENSAL');
  const [ancoraCriar, setAncoraCriar] = useState('');
  const [tipoCriar, setTipoCriar] = useState<'ENTRADA' | 'SAIDA'>('SAIDA');
  const [automaticaCriar, setAutomaticaCriar] = useState(false);
  const [carteiraCriarId, setCarteiraCriarId] = useState<number | null>(null);
  // Um destino, nunca dois: a cobrança sai do caixa ou do cartão (V67)
  const [destinoCriar, setDestinoCriar] = useState<'CONTA' | 'CARTAO'>('CONTA');
  const [cartaoCriarId, setCartaoCriarId] = useState<number | null>(null);
  const [descricaoError, setDescricaoError] = useState<string | null>(null);
  const [valorError, setValorError] = useState<string | null>(null);
  const [diaError, setDiaError] = useState<string | null>(null);
  const [categoriaError, setCategoriaError] = useState<string | null>(null);
  const [erroCriar, setErroCriar] = useState<string | null>(null);

  const { data: categorias = [] } = useQuery({ queryKey: ['categorias'], queryFn: () => categoriaService.listar() });

  // Padrões detectados no histórico. São sugestões: viram recorrência só quando o dono confirma.
  const { data: sugestoes = [] } = useQuery({
    queryKey: ['recorrencia-sugestoes'],
    queryFn: () => contaFixaService.listarSugestoes(),
  });

  const confirmarSugestao = useMutation({
    mutationFn: (id: number) => contaFixaService.confirmarSugestao(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recorrencia-sugestoes'] });
      queryClient.invalidateQueries({ queryKey: ['contas-fixas'] });
    },
  });

  const descartarSugestao = useMutation({
    mutationFn: (id: number) => contaFixaService.descartarSugestao(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['recorrencia-sugestoes'] }),
  });

  const limparCriar = () => {
    setDescricaoCriar(''); setValorCriar(''); setDiaCriar(''); setCategoriaCriarId(null); setRecorrenteCriar(true);
    setTipoCriar('SAIDA'); setAutomaticaCriar(false); setCarteiraCriarId(null); setEditando(null);
    setDestinoCriar('CONTA'); setCartaoCriarId(null);
    setFrequenciaCriar('MENSAL'); setAncoraCriar('');
    setDescricaoError(null); setValorError(null); setDiaError(null); setCategoriaError(null); setErroCriar(null);
  };

  const criarMutation = useMutation({
    mutationFn: (req: ContaFixaRequest) => editando
      ? contaFixaService.atualizar(editando.id, req)
      : contaFixaService.criar(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contas-fixas'] });
      setModalCriarVisible(false);
      limparCriar();
    },
    onError: (err: unknown) => setErroCriar(mensagemDeErro(err, 'Erro ao criar conta fixa.')),
  });

  const abrirEdicao = (cf: ContaFixa) => {
    setEditando(cf);
    setDescricaoCriar(cf.nome);
    setValorCriar(formatNumber(Number(cf.valorPlanejado)));
    setDiaCriar(String(cf.diaVencimento));
    setCategoriaCriarId(cf.categoria?.id ?? null);
    setRecorrenteCriar(cf.recorrente !== false);
    setTipoCriar(cf.tipo ?? 'SAIDA');
    setAutomaticaCriar(Boolean(cf.execucaoAutomatica));
    setCarteiraCriarId(cf.carteira?.id ?? null);
    // Sem restaurar o destino, salvar a edição de uma assinatura apagaria o cartão
    setDestinoCriar(cf.cartao ? 'CARTAO' : 'CONTA');
    setCartaoCriarId(cf.cartao?.id ?? null);
    // Sem restaurar a frequência, editar uma assinatura anual a devolveria para mensal
    const frequencia = cf.frequencia ?? 'MENSAL';
    setFrequenciaCriar(frequencia);
    // Auto-cura: linha criada entre V72 e V73 tem frequência não-mensal sem âncora, e o
    // campo agora é obrigatório. Sem isto, abrir para trocar o nome pediria uma data que
    // o usuário nunca informou — o próximo vencimento é a melhor aproximação que existe.
    const ancora = cf.dataAncora ?? (usaAncora(frequencia) ? cf.dataProximoVencimento : null);
    setAncoraCriar(ancora ? formatDateOnlyBR(ancora) : '');
    setModalCriarVisible(true);
  };

  /**
   * O que vai acontecer ao salvar. É determinístico (FaturaDatas + o dia escolhido),
   * e antes disso a tela só dizia "entra na fatura do cartão todo mês" — sem dizer
   * qual fatura, nem que a primeira cobrança pode sair na hora.
   */
  const explicacaoDaCobranca = (() => {
    const cartao = cartoes.find(c => c.id === cartaoCriarId);
    if (!cartao) return 'A assinatura entra na fatura do cartão a cada cobrança.';

    const comAncora = usaAncora(frequenciaCriar);
    const dia = comAncora
      ? (isValidDateBR(ancoraCriar) ? new Date(parseDateBR(ancoraCriar) + 'T00:00:00').getDate() : NaN)
      : Number(diaCriar);
    if (!Number.isInteger(dia) || dia < 1 || dia > 31) {
      return `A assinatura entra na fatura do ${cartao.nome} a cada cobrança.`;
    }

    const primeira = proximaCobranca(
      dia,
      new Date(),
      frequenciaCriar,
      comAncora && isValidDateBR(ancoraCriar) ? new Date(parseDateBR(ancoraCriar) + 'T00:00:00') : null,
    );
    const vencimento = vencimentoDaCompraNoCartao(primeira, cartao.diaFechamento, cartao.diaVencimento);
    const dois = (n: number) => String(n).padStart(2, '0');
    const rotulo = `${dois(vencimento.getDate())}/${dois(vencimento.getMonth() + 1)}/${vencimento.getFullYear()}`;

    const hoje = new Date();
    const saiAgora = automaticaCriar
      && primeira <= new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate());

    return saiAgora
      ? `A primeira cobrança sai agora e entra na fatura que vence em ${rotulo}.`
      : `Entra na fatura do ${cartao.nome} que vence em ${rotulo}.`;
  })();

  const { mes, ano } = competenciaAtual();
  const competenciaDeHoje = competenciaIso(mes, ano);

  const renderItem = ({ item: cf }: { item: ContaFixa }) => {
    const pendente = cf.status === 'PENDENTE' || cf.status === 'ATRASADO';
    // Vencimento no futuro ainda não pode ser quitado: a competência é a régua.
    const realizavel = !cf.dataProximoVencimento || cf.dataProximoVencimento.slice(0, 7) <= competenciaDeHoje;
    const ocupado = pulandoId != null || pagarMutation.status === 'pending';
    const acaoDeQuitar = cf.tipo === 'ENTRADA' ? 'Receber' : 'Pagar';
    const cancelada = cf.ativo === false;
    // `ativo=false` mistura duas coisas: cancelamento e fim de ciclo. avancarOcorrencia
    // marca ativo=false + PAGO ao encerrar uma conta de um mês só (ContaFixaService),
    // e essa cumpriu o que prometeu — oferecer "Reativar" ali não faria sentido.
    // Separar de verdade exigiria uma coluna de motivo no backend.
    const concluida = cancelada && cf.recorrente === false && cf.status === 'PAGO';
    return (
      <Card radius={radius.xl} style={{ marginBottom: spacing.md }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
          {/* A cor da categoria vence o tom semântico (contrato do IconTile), como
              no detalhe desta tela e no resto do app. O status não se perde: quem
              o comunica no card é o <Badge status> da linha ao lado. O `tone`
              segue valendo para categoria sem cor, ou sem categoria. */}
          <IconTile
            cor={cf.categoria?.cor}
            tone={cf.tipo === 'ENTRADA' ? 'success' : cf.status === 'ATRASADO' ? 'danger' : cf.status === 'PAGO' ? 'success' : 'brand'}
            size={44}
          >
            {emojiDaCategoria(cf.categoria, '📌')}
          </IconTile>
          <View style={{ flex: 1, minWidth: 0 }}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: spacing.sm }}>
              <Text numberOfLines={1} style={{ ...typography.cardTitle, color: colors.textPrimary, flex: 1 }}>{cf.nome}</Text>
              {cancelada
                ? <Badge tone={concluida ? 'success' : 'danger'}>{concluida ? 'Concluída' : 'Cancelada'}</Badge>
                : <Badge status={cf.status} />}
            </View>
            {/* Duas linhas: com o cartão no meio, uma linha só cortava o dia do
                vencimento — justamente o dado que diz quando a cobrança cai. */}
            <Text numberOfLines={2} style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.xxs }}>
              {cf.categoria?.nome ? `${cf.categoria.nome} · ` : ''}{cf.cartao ? `${cf.cartao.nome} · ` : ''}{cf.execucaoAutomatica ? 'Automática' : 'Manual'} · {rotuloCadencia(cf.frequencia ?? 'MENSAL', cf.diaVencimento, cf.dataAncora ? new Date(cf.dataAncora + 'T00:00:00') : null).toLowerCase()}
            </Text>
          </View>
        </View>

        <Text style={{
          ...typography.section, ...numeric, marginTop: spacing.md,
          color: cf.tipo === 'ENTRADA' ? colors.success : colors.danger,
        }}>
          {cf.tipo === 'ENTRADA' ? '+' : '−'} {formatCurrency(Number(cf.valorPlanejado ?? 0))}
        </Text>

        {/* Os botões saem da linha do valor e ganham corpo de leitura: em caixa
            estreita três pills de 44 ao lado do valor estouravam o card.
            Rótulo é o texto visível; o nome da conta vai para a dica. */}
        <View style={{
          flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'flex-end',
          gap: spacing.sm, marginTop: spacing.md,
        }}>
          <Botao
            titulo="Editar"
            variante="texto"
            tamanho="pill"
            onPress={() => abrirEdicao(cf)}
            dica={`Edita ${cf.nome}`}
          />
          {cancelada && !concluida && (
            <Botao
              titulo="Reativar"
              tamanho="pill"
              onPress={() => reativarMutation.mutate(cf.id)}
              desabilitado={reativarMutation.isPending}
              carregando={reativarMutation.isPending && reativarMutation.variables === cf.id}
              accessibilityLabel={`Reativar ${cf.nome}`}
            />
          )}
          {!cancelada && pendente && cf.recorrente !== false && (
            <Botao
              titulo="Pular"
              variante="secundario"
              tamanho="pill"
              onPress={() => pularMes(cf)}
              desabilitado={ocupado}
              carregando={pulandoId === cf.id}
              dica={`Pula ${cf.nome} neste mês`}
            />
          )}
          {!cancelada && pendente && realizavel && (
            <Botao
              titulo={acaoDeQuitar}
              tamanho="pill"
              desabilitado={ocupado}
              dica={`${acaoDeQuitar} ${cf.nome}`}
              onPress={() => {
                setSelecionada(cf);
                setValorPago(formatNumber(Number(cf.valorPlanejado ?? 0)));
                setErroPagar(null);
                setErroCarteira(null);
                setCarteiraPagamentoId(cf.carteira?.id ?? (carteiras.length === 1 ? carteiras[0].id : null));
                setModalPagarVisible(true);
              }}
            />
          )}
          {/* Quarto botão da linha; ela já tem flexWrap, então em caixa estreita quebra
              em vez de estourar. `texto` e não `perigo` de propósito: um botão vermelho
              cheio brigaria com o "Pagar" primário ao lado, e quem carrega o peso
              destrutivo é a confirmação. */}
          {!cancelada && (
            <Botao
              titulo="Cancelar"
              variante="texto"
              tamanho="pill"
              onPress={() => confirmarCancelamento(cf)}
              desabilitado={cancelarMutation.isPending}
              carregando={cancelarMutation.isPending && cancelarMutation.variables === cf.id}
              accessibilityLabel={`Cancelar ${cf.nome}`}
              dica="Para de cobrar. As cobranças já lançadas permanecem."
            />
          )}
        </View>
      </Card>
    );
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <CabecalhoSubTela
        titulo="Recorrências"
        apoio={
          <Text style={{ ...typography.body, ...numeric, color: colors.textSecondary }}>
            {emAberto.length > 0
              ? `${formatCurrency(totalAReceber)} a receber · ${formatCurrency(totalAPagar)} a pagar`
              : 'Entradas e saídas que se repetem'}
          </Text>
        }
      />

      <View style={{
        flexDirection: 'row', gap: spacing.sm,
        paddingHorizontal: screenPadding, paddingBottom: spacing.md,
      }}>
        {ABAS.map(({ chave, rotulo }) => (
          <Chip key={chave} label={rotulo} selected={aba === chave} onPress={() => setAba(chave)} />
        ))}
      </View>

      {isLoading || (aba === 'CANCELADAS' && carregandoCanceladas) ? (
        <View style={{ paddingHorizontal: screenPadding, gap: spacing.md }}>
          {[1, 2, 3].map(i => <SkeletonBox key={i} width="100%" height={110} borderRadius={radius.xl} />)}
        </View>
      ) : isError || (aba === 'CANCELADAS' && erroCanceladas) ? (
        // Sem este guard a aba Canceladas anunciaria "Nada cancelado" quando a busca
        // falhou — dizer que não há nada é diferente de não ter conseguido perguntar.
        <EstadoVazio
          emoji="📶"
          titulo="Não deu para carregar suas recorrências"
          texto="Verifique sua conexão e tente de novo."
          acao={{
            rotulo: 'Tentar de novo',
            onPress: () => { if (isError) refetch(); if (erroCanceladas) recarregarCanceladas(); },
          }}
        />
      ) : (
        <FlatList
          data={listaExibida}
          keyExtractor={item => item.id.toString()}
          contentContainerStyle={{ paddingHorizontal: screenPadding, paddingBottom: tabBarSpace }}
          renderItem={renderItem}
          ListHeaderComponent={aba !== 'TODAS' || sugestoes.length === 0 ? null : (
            <Card radius={radius.xl} style={{ marginBottom: spacing.md }}>
              <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>
                Isto se repete todo mês
              </Text>
              <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.xxs }}>
                Encontramos no seu histórico. Vira recorrência só se você quiser — e continua sem
                lançar sozinho.
              </Text>
              {sugestoes.map(sugestao => (
                <View
                  key={sugestao.id}
                  style={{
                    paddingTop: spacing.md, marginTop: spacing.md,
                    borderTopWidth: 1, borderTopColor: colors.border, gap: spacing.xs,
                  }}
                >
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
                    <Text numberOfLines={1} style={{ ...typography.rowTitle, color: colors.textPrimary, flex: 1 }}>
                      {sugestao.descricao}
                    </Text>
                    <Text style={{ ...typography.value, ...numeric, color: colors.textPrimary }}>
                      {formatCurrency(sugestao.valorMedio)}
                    </Text>
                  </View>
                  <Text style={{ ...typography.meta, color: colors.textSecondary }}>
                    {sugestao.ocorrencias} vezes · todo dia {sugestao.diaTipico}
                    {sugestao.categoriaNome ? ` · ${sugestao.categoriaNome}` : ''}
                  </Text>
                  <View style={{ flexDirection: 'row', gap: spacing.sm }}>
                    <Botao
                      titulo="É recorrente"
                      tamanho="pill"
                      onPress={() => confirmarSugestao.mutate(sugestao.id)}
                      carregando={confirmarSugestao.isPending}
                      accessibilityLabel={`Transformar ${sugestao.descricao} em recorrência`}
                    />
                    <Botao
                      titulo="Não é"
                      variante="texto"
                      tamanho="pill"
                      onPress={() => descartarSugestao.mutate(sugestao.id)}
                      accessibilityLabel={`Descartar sugestão de ${sugestao.descricao}`}
                    />
                  </View>
                </View>
              ))}
            </Card>
          )}
          ListEmptyComponent={() => (
            aba === 'CANCELADAS' ? (
              <EstadoVazio
                emoji="🗂️"
                titulo="Nada cancelado"
                texto="O que você cancelar aparece aqui, e pode voltar a ser cobrado."
              />
            ) : aba === 'ASSINATURAS' ? (
              <EstadoVazio
                emoji="💳"
                titulo="Nenhuma assinatura no cartão"
                texto="Assinaturas são as recorrências que entram na fatura de um cartão."
                acao={{ rotulo: 'Cadastrar assinatura', onPress: () => { limparCriar(); setModalCriarVisible(true); } }}
              />
            ) : (
              <EstadoVazio
                emoji="🧾"
                titulo="Nenhuma recorrência ainda"
                texto="Cadastre salário, aluguel ou outros valores recorrentes."
                acao={{ rotulo: 'Cadastrar recorrência', onPress: () => { limparCriar(); setModalCriarVisible(true); } }}
              />
            )
          )}
        />
      )}

      <Fab onPress={() => { limparCriar(); setModalCriarVisible(true); }} accessibilityLabel="Criar recorrência" />

      <FolhaModal
        visible={modalPagarVisible}
        titulo={selecionada?.tipo === 'ENTRADA' ? 'Receber' : 'Pagar'}
        onFechar={() => { setModalPagarVisible(false); setValorPago(''); setErroPagar(null); }}
        acao={{
          rotulo: 'Confirmar',
          carregando: pagarMutation.status === 'pending',
          onPress: () => {
            if (pagarMutation.status === 'pending') return;
            setErroPagar(null); setErroCarteira(null);
            const v = parseCurrencyBR(valorPago);
            if (isNaN(v) || v <= 0) { setErroPagar('Valor deve ser positivo.'); return; }
            // Assinatura de cartão entra na fatura: não há conta de onde sair
            const noCartao = Boolean(selecionada?.cartao);
            if (!noCartao && !carteiraPagamentoId) { setErroCarteira('Selecione de onde sai o pagamento.'); return; }
            pagarMutation.mutate({ id: selecionada!.id, valor: v, carteiraId: noCartao ? undefined : carteiraPagamentoId! });
          },
        }}
      >
        <ScrollView contentContainerStyle={{ padding: screenPadding }} keyboardShouldPersistTaps="handled">
          {selecionada && (
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md, marginBottom: spacing.lg }}>
              <IconTile size={44} cor={selecionada.categoria?.cor}>{emojiDaCategoria(selecionada.categoria, '📌')}</IconTile>
              <View style={{ flex: 1, minWidth: 0 }}>
                <Text numberOfLines={1} style={{ ...typography.cardTitle, color: colors.textPrimary }}>{selecionada.nome}</Text>
                <Text style={{ ...typography.meta, ...numeric, color: colors.textSecondary, marginTop: spacing.xxs }}>
                  Planejado: {formatCurrency(Number(selecionada.valorPlanejado ?? 0))} · vence dia {selecionada.diaVencimento}
                </Text>
              </View>
            </View>
          )}
          <Field label="Valor pago" value={valorPago} onChangeText={(t) => setValorPago(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={erroPagar} autoFocus />

          {selecionada?.cartao ? (
            <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.sm }}>
              Entra na fatura do {selecionada.cartao.nome}.
            </Text>
          ) : (
          <>
          <RotuloDeGrupo>{selecionada?.tipo === 'ENTRADA' ? 'Receber em' : 'Pagar com'}</RotuloDeGrupo>
          <FaixaDeContas
            contas={carteiras}
            selecionada={carteiraPagamentoId}
            onSelecionar={id => { setCarteiraPagamentoId(id); setErroCarteira(null); }}
            vazio="Você ainda não tem contas. Crie uma em Mais → Contas para registrar o pagamento."
          />
          {erroCarteira && (
            <Text accessibilityRole="alert" style={{ ...typography.meta, color: colors.danger, marginTop: spacing.sm }}>{erroCarteira}</Text>
          )}
          </>
          )}
        </ScrollView>
      </FolhaModal>

      <FolhaModal
        visible={modalCriarVisible}
        titulo={editando ? 'Editar recorrência' : 'Nova recorrência'}
        onFechar={() => { setModalCriarVisible(false); limparCriar(); }}
        acao={{
          rotulo: 'Salvar',
          carregando: criarMutation.status === 'pending',
          onPress: () => {
            setDescricaoError(null); setValorError(null); setDiaError(null); setCategoriaError(null); setErroCriar(null);
            let hasErr = false;
            if (!descricaoCriar.trim()) { setDescricaoError('Descrição obrigatória.'); hasErr = true; }
            const v = parseCurrencyBR(valorCriar);
            if (isNaN(v) || v <= 0) { setValorError('Valor deve ser positivo.'); hasErr = true; }
            const comAncora = usaAncora(frequenciaCriar);
            // Fora de MENSAL, a série sai da âncora; o dia do mês é derivado dela.
            if (comAncora && !isValidDateBR(ancoraCriar)) {
              setDiaError('Informe a data da primeira cobrança (DD/MM/AAAA).');
              hasErr = true;
            }
            const dia = comAncora
              ? (isValidDateBR(ancoraCriar) ? new Date(parseDateBR(ancoraCriar) + 'T00:00:00').getDate() : 0)
              : Number(diaCriar);
            if (!comAncora && (!Number.isInteger(dia) || dia < 1 || dia > 31)) { setDiaError('Dia deve ser um número entre 1 e 31.'); hasErr = true; }
            if (!categoriaCriarId) { setCategoriaError('Selecione uma categoria.'); hasErr = true; }
            const usaCartao = tipoCriar === 'SAIDA' && destinoCriar === 'CARTAO';
            if (automaticaCriar && usaCartao && !cartaoCriarId) { setErroCriar('Selecione o cartão da cobrança.'); hasErr = true; }
            if (automaticaCriar && !usaCartao && !carteiraCriarId) { setErroCriar('Selecione a conta da execução automática.'); hasErr = true; }
            if (hasErr) return;
            criarMutation.mutate({
              descricao: descricaoCriar.trim(),
              valor: Number(v),
              diaVencimento: dia,
              categoriaId: categoriaCriarId!,
              recorrente: recorrenteCriar,
              tipo: tipoCriar,
              execucaoAutomatica: automaticaCriar,
              frequencia: frequenciaCriar,
              ...(comAncora ? { dataAncora: parseDateBR(ancoraCriar) } : {}),
              // O destino vale também em execução manual: é onde a cobrança cai
              // quando o usuário registra. Só é obrigatório na automática.
              ...(usaCartao
                ? { cartaoId: cartaoCriarId ?? undefined }
                : { carteiraId: carteiraCriarId ?? undefined }),
            });
          },
        }}
      >
        <ScrollView contentContainerStyle={{ padding: screenPadding }} keyboardShouldPersistTaps="handled">
          <RotuloDeGrupo primeiro>Tipo</RotuloDeGrupo>
          <View style={{ flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.lg }}>
            <Chip label="↑ Entrada" selected={tipoCriar === 'ENTRADA'} onPress={() => { setTipoCriar('ENTRADA'); setDestinoCriar('CONTA'); }} />
            <Chip label="↓ Saída" selected={tipoCriar === 'SAIDA'} onPress={() => setTipoCriar('SAIDA')} />
          </View>
          <Field label="Descrição" value={descricaoCriar} onChangeText={setDescricaoCriar} placeholder="Ex: Aluguel" error={descricaoError} autoFocus />
          <Field label="Valor" value={valorCriar} onChangeText={(t) => setValorCriar(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={valorError} />
          <RotuloDeGrupo>Com que frequência</RotuloDeGrupo>
          <SeletorDeFrequencia valor={frequenciaCriar} onSelecionar={setFrequenciaCriar} />

          {usaAncora(frequenciaCriar) ? (
            // "Dia do mês" não descreve estas séries: em semanal/quinzenal o que fixa o
            // dia da semana e a paridade é a data da primeira cobrança, e de bimestral a
            // anual é ela que fixa o mês do aniversário ("todo 15 de março").
            <Field label="Primeira cobrança" value={ancoraCriar} onChangeText={(t) => setAncoraCriar(maskDateInput(t))} keyboardType="number-pad" placeholder="DD/MM/AAAA" maxLength={10} error={diaError} />
          ) : (
            <Field label="Dia de vencimento" value={diaCriar} onChangeText={setDiaCriar} keyboardType="number-pad" placeholder="Ex: 10" maxLength={2} error={diaError} />
          )}

          <RotuloDeGrupo>Categoria</RotuloDeGrupo>
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={{ gap: spacing.sm }}
            style={{ marginBottom: spacing.sm }}
            keyboardShouldPersistTaps="handled"
          >
            {categorias.map(cat => (
              <Chip key={cat.id} label={`${emojiDaCategoria(cat, '📌')} ${cat.nome}`} selected={categoriaCriarId === cat.id} onPress={() => setCategoriaCriarId(cat.id)} />
            ))}
          </ScrollView>
          {categoriaError && (
            <Text accessibilityRole="alert" style={{ ...typography.meta, color: colors.danger, marginBottom: spacing.sm }}>{categoriaError}</Text>
          )}

          <RotuloDeGrupo>Execução</RotuloDeGrupo>
          <View style={{ flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.md }}>
            <Chip label="Manual" selected={!automaticaCriar} onPress={() => setAutomaticaCriar(false)} />
            <Chip label="Automática" selected={automaticaCriar} onPress={() => setAutomaticaCriar(true)} />
          </View>
          {/*
            "Cobrar em" fica fora do guard de execução automática de propósito: o
            backend aceita destino de cartão com execução manual (resolverDestino só
            exige destino quando automática). Escondendo aqui, quem escolhia "Manual"
            nunca via que dava para cobrar no cartão.
          */}
          {tipoCriar === 'SAIDA' && (
            <>
              <RotuloDeGrupo>Cobrar em</RotuloDeGrupo>
              <View style={{ flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.md }}>
                <Chip label="Conta" selected={destinoCriar === 'CONTA'} onPress={() => { setDestinoCriar('CONTA'); setCartaoCriarId(null); }} />
                <Chip label="Cartão" selected={destinoCriar === 'CARTAO'} onPress={() => { setDestinoCriar('CARTAO'); setCarteiraCriarId(null); }} />
              </View>
            </>
          )}
          {(
            <>
              {tipoCriar === 'SAIDA' && destinoCriar === 'CARTAO' ? (
                <>
                  <Text style={{ ...typography.meta, color: colors.textSecondary, marginBottom: spacing.sm }}>
                    {explicacaoDaCobranca}
                  </Text>
                  {cartoes.length === 0 ? (
                    <Text style={{ ...typography.meta, color: colors.textSecondary, marginBottom: spacing.sm }}>
                      Você ainda não tem cartões. Crie um em Mais → Carteira.
                    </Text>
                  ) : (
                    <ScrollView
                      horizontal
                      showsHorizontalScrollIndicator={false}
                      contentContainerStyle={{ gap: spacing.sm }}
                      style={{ marginBottom: spacing.sm }}
                      keyboardShouldPersistTaps="handled"
                    >
                      {cartoes.map(c => (
                        <Chip key={c.id} label={c.nome} selected={cartaoCriarId === c.id} onPress={() => setCartaoCriarId(c.id)} />
                      ))}
                    </ScrollView>
                  )}
                </>
              ) : (
                <>
                  <Text style={{ ...typography.meta, color: colors.textSecondary, marginBottom: spacing.sm }}>
                    Escolha a conta que receberá ou pagará no vencimento.
                  </Text>
                  <FaixaDeContas
                    contas={carteiras}
                    selecionada={carteiraCriarId}
                    onSelecionar={setCarteiraCriarId}
                    vazio="Você ainda não tem contas. Crie uma em Mais → Contas."
                  />
                </>
              )}
            </>
          )}

          {/* Só na criação: ContaFixaService.atualizar nunca chama setRecorrente, então na
              edição o switch era um no-op que prometia o que não entregava. Quem quer
              parar de cobrar usa "Cancelar" no card. */}
          {!editando && (
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: spacing.md, marginTop: spacing.md }}>
              <View style={{ flex: 1 }}>
                <Text style={{ ...typography.cardTitle, fontWeight: '600', color: colors.textPrimary }}>Repete sempre</Text>
                <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.xxs }}>Desative para uma cobrança única.</Text>
              </View>
              <Switch
                value={recorrenteCriar}
                onValueChange={setRecorrenteCriar}
                trackColor={{ true: colors.brand }}
                accessibilityLabel="Repete sempre"
              />
            </View>
          )}

          {erroCriar && (
            <Text accessibilityRole="alert" accessibilityLiveRegion="polite" style={{ ...typography.meta, color: colors.danger, marginTop: spacing.lg }}>
              {erroCriar}
            </Text>
          )}
        </ScrollView>
      </FolhaModal>
    </View>
  );
}

/**
 * Faixa de contas de caixa. Aparece na folha de pagamento e na de execução
 * automática — as duas pediam a mesma coisa, com o texto de vazio duplicado.
 */
const FaixaDeContas = ({ contas, selecionada, onSelecionar, vazio }: {
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
