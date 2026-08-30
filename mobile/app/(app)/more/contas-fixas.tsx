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
import { ContaFixa, ContaFixaRequest } from '../../../src/types';
import {
  useTheme, useTabBarSpace, numeric, radius, screenPadding, spacing, typography,
} from '../../../src/theme';
import { competenciaAtual, competenciaIso } from '../../../src/domain/periodo';
import { mensagemDeErro } from '../../../src/utils/erros';
import { parseCurrencyBR, maskCurrencyInput, formatCurrency, formatNumber } from '../../../src/utils/format';
import Botao from '../../../src/components/ui/Botao';
import CabecalhoSubTela from '../../../src/components/ui/CabecalhoSubTela';
import EstadoVazio from '../../../src/components/ui/EstadoVazio';
import FolhaModal from '../../../src/components/ui/FolhaModal';
import RotuloDeGrupo from '../../../src/components/ui/RotuloDeGrupo';
import SkeletonBox from '../../../src/components/ui/SkeletonBox';
import Fab from '../../../src/components/ui/Fab';
import type { ContaFinanceira } from '../../../src/types';
import { emojiDaCategoria } from '../../../src/domain/iconeCategoria';

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

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['contas-fixas'],
    queryFn: () => contaFixaService.listar(),
  });

  const contas = data?.content ?? [];
  const emAberto = contas.filter(cf => cf.status === 'PENDENTE' || cf.status === 'ATRASADO');
  const totalAReceber = emAberto.filter(cf => cf.tipo === 'ENTRADA').reduce((acc, cf) => acc + Number(cf.valorPlanejado ?? 0), 0);
  const totalAPagar = emAberto.filter(cf => cf.tipo !== 'ENTRADA').reduce((acc, cf) => acc + Number(cf.valorPlanejado ?? 0), 0);

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

  // criar conta fixa
  const [descricaoCriar, setDescricaoCriar] = useState('');
  const [valorCriar, setValorCriar] = useState('');
  const [diaCriar, setDiaCriar] = useState('');
  const [categoriaCriarId, setCategoriaCriarId] = useState<number | null>(null);
  const [recorrenteCriar, setRecorrenteCriar] = useState(true);
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
    setModalCriarVisible(true);
  };

  const { mes, ano } = competenciaAtual();
  const competenciaDeHoje = competenciaIso(mes, ano);

  const renderItem = ({ item: cf }: { item: ContaFixa }) => {
    const pendente = cf.status === 'PENDENTE' || cf.status === 'ATRASADO';
    // Vencimento no futuro ainda não pode ser quitado: a competência é a régua.
    const realizavel = !cf.dataProximoVencimento || cf.dataProximoVencimento.slice(0, 7) <= competenciaDeHoje;
    const ocupado = pulandoId != null || pagarMutation.status === 'pending';
    const acaoDeQuitar = cf.tipo === 'ENTRADA' ? 'Receber' : 'Pagar';
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
              <Badge status={cf.status} />
            </View>
            {/* Duas linhas: com o cartão no meio, uma linha só cortava o dia do
                vencimento — justamente o dado que diz quando a cobrança cai. */}
            <Text numberOfLines={2} style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.xxs }}>
              {cf.categoria?.nome ? `${cf.categoria.nome} · ` : ''}{cf.cartao ? `${cf.cartao.nome} · ` : ''}{cf.execucaoAutomatica ? 'Automática' : 'Manual'} · dia {cf.diaVencimento}
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
          {pendente && cf.recorrente !== false && (
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
          {pendente && realizavel && (
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

      {isLoading ? (
        <View style={{ paddingHorizontal: screenPadding, gap: spacing.md }}>
          {[1, 2, 3].map(i => <SkeletonBox key={i} width="100%" height={110} borderRadius={radius.xl} />)}
        </View>
      ) : isError ? (
        <EstadoVazio
          emoji="📶"
          titulo="Não deu para carregar suas recorrências"
          texto="Verifique sua conexão e tente de novo."
          acao={{ rotulo: 'Tentar de novo', onPress: () => refetch() }}
        />
      ) : (
        <FlatList
          data={contas}
          keyExtractor={item => item.id.toString()}
          contentContainerStyle={{ paddingHorizontal: screenPadding, paddingBottom: tabBarSpace }}
          renderItem={renderItem}
          ListHeaderComponent={sugestoes.length === 0 ? null : (
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
            <EstadoVazio
              emoji="🧾"
              titulo="Nenhuma recorrência ainda"
              texto="Cadastre salário, aluguel ou outros valores recorrentes."
              acao={{ rotulo: 'Cadastrar recorrência', onPress: () => { limparCriar(); setModalCriarVisible(true); } }}
            />
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
            const dia = Number(diaCriar);
            if (!Number.isInteger(dia) || dia < 1 || dia > 31) { setDiaError('Dia deve ser um número entre 1 e 31.'); hasErr = true; }
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
              ...(usaCartao
                ? { cartaoId: automaticaCriar ? cartaoCriarId! : undefined }
                : { carteiraId: automaticaCriar ? carteiraCriarId! : undefined }),
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
          <Field label="Dia de vencimento" value={diaCriar} onChangeText={setDiaCriar} keyboardType="number-pad" placeholder="Ex: 10" maxLength={2} error={diaError} />

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
          {automaticaCriar && (
            <>
              {tipoCriar === 'SAIDA' && (
                <>
                  <RotuloDeGrupo>Cobrar em</RotuloDeGrupo>
                  <View style={{ flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.md }}>
                    <Chip label="Conta" selected={destinoCriar === 'CONTA'} onPress={() => { setDestinoCriar('CONTA'); setCartaoCriarId(null); }} />
                    <Chip label="Cartão" selected={destinoCriar === 'CARTAO'} onPress={() => { setDestinoCriar('CARTAO'); setCarteiraCriarId(null); }} />
                  </View>
                </>
              )}
              {tipoCriar === 'SAIDA' && destinoCriar === 'CARTAO' ? (
                <>
                  <Text style={{ ...typography.meta, color: colors.textSecondary, marginBottom: spacing.sm }}>
                    A assinatura entra na fatura do cartão todo mês.
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

          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: spacing.md, marginTop: spacing.md }}>
            <View style={{ flex: 1 }}>
              <Text style={{ ...typography.cardTitle, fontWeight: '600', color: colors.textPrimary }}>Repete todo mês</Text>
              <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.xxs }}>Desative para contas de um mês só.</Text>
            </View>
            <Switch
              value={recorrenteCriar}
              onValueChange={setRecorrenteCriar}
              trackColor={{ true: colors.brand }}
              accessibilityLabel="Repete todo mês"
            />
          </View>

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
