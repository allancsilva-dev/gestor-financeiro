import React, { useEffect, useMemo, useRef, useState } from 'react';
import { View, Text, FlatList, ScrollView, TouchableOpacity, Alert } from 'react-native';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import investimentoService from '../../../src/services/investimentoService';
import contaFinanceiraService from '../../../src/services/contaFinanceiraService';
import { Ativo, AtivoRequest, LiquidezContaFinanceira, TipoAtivo, TipoMovimentacaoAtivo } from '../../../src/types';
import {
  useTheme, useTabBarSpace, cardRadius, numeric, radius, screenPadding, spacing, typography,
} from '../../../src/theme';
import { mensagemDeErro } from '../../../src/utils/erros';
import { formatCurrency, formatDate, isValidDateBR, maskCurrencyInput, maskDateInput, parseCurrencyBR, parseDateBR } from '../../../src/utils/format';
import Botao from '../../../src/components/ui/Botao';
import CabecalhoSubTela from '../../../src/components/ui/CabecalhoSubTela';
import Card from '../../../src/components/ui/Card';
import Chip from '../../../src/components/ui/Chip';
import EstadoVazio from '../../../src/components/ui/EstadoVazio';
import Fab from '../../../src/components/ui/Fab';
import Field from '../../../src/components/ui/Field';
import FolhaModal from '../../../src/components/ui/FolhaModal';
import RotuloDeGrupo from '../../../src/components/ui/RotuloDeGrupo';
import SkeletonBox from '../../../src/components/ui/SkeletonBox';

const TIPO_ATIVO_LABEL: Record<TipoAtivo, string> = {
  ACAO: 'Ação',
  FII: 'FII',
  RENDA_FIXA: 'Renda fixa',
  CRIPTO: 'Cripto',
  OUTRO: 'Outro',
};

const TIPO_MOV_LABEL: Record<TipoMovimentacaoAtivo, string> = {
  COMPRA: 'Compra',
  VENDA: 'Venda',
  DIVIDENDO: 'Dividendo',
  BONIFICACAO: 'Bonificação',
};

const hojeBR = () => new Date().toLocaleDateString('pt-BR');

/** Par rótulo/valor dos painéis de posição. */
const Metrica = ({ rotulo, valor, cor }: { rotulo: string; valor: string; cor?: string }) => {
  const colors = useTheme();
  return (
    <View style={{ flex: 1 }}>
      <Text style={{ ...typography.meta, color: colors.textSecondary }}>{rotulo}</Text>
      <Text
        numberOfLines={1}
        adjustsFontSizeToFit
        minimumFontScale={0.7}
        style={{ ...typography.value, ...numeric, fontWeight: '800', color: cor ?? colors.textPrimary, marginTop: spacing.xxs }}
      >
        {valor}
      </Text>
    </View>
  );
};

/** Faixa de chips de escolha única, com o rótulo do grupo em cima. */
const FaixaDeChips = <T extends string>({ rotulo, opcoes, rotuloDe, atual, onEscolher }: {
  rotulo: string;
  opcoes: readonly T[];
  rotuloDe?: (opcao: T) => string;
  atual: T | undefined;
  onEscolher: (opcao: T) => void;
}) => (
  <>
    <RotuloDeGrupo>{rotulo}</RotuloDeGrupo>
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, marginBottom: spacing.md }}>
      {opcoes.map(o => (
        <Chip key={o} label={rotuloDe ? rotuloDe(o) : o} selected={atual === o} onPress={() => onEscolher(o)} />
      ))}
    </View>
  </>
);

const Erro = ({ texto }: { texto: string }) => {
  const colors = useTheme();
  return (
    <Text accessibilityRole="alert" style={{ ...typography.meta, color: colors.danger }}>{texto}</Text>
  );
};

function AtivoModal({ ativo, visible, onClose }: { ativo: Ativo | null; visible: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [ticker, setTicker] = useState('');
  const [nome, setNome] = useState('');
  const [tipo, setTipo] = useState<TipoAtivo>('ACAO');
  const [valorAtual, setValorAtual] = useState('');
  const [liquidez, setLiquidez] = useState<LiquidezContaFinanceira>('IMEDIATA');
  const [custodiaId, setCustodiaId] = useState<number | undefined>();
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  useEffect(() => {
    if (!visible) return;
    setTicker(ativo?.ticker ?? '');
    setNome(ativo?.nome ?? '');
    setTipo(ativo?.tipo ?? 'ACAO');
    setValorAtual(maskCurrencyInput(Number(ativo?.valorAtual ?? 0).toFixed(2)));
    setLiquidez(ativo?.liquidez ?? 'IMEDIATA');
    setCustodiaId(ativo?.custodiaId ?? undefined);
    setErro(null);
  }, [ativo, visible]);

  const salvar = async () => {
    const valor = parseCurrencyBR(valorAtual);
    if (!ticker.trim() || !nome.trim() || !valorAtual || isNaN(valor) || valor < 0) {
      setErro('Informe ticker, nome e preço atual válido.');
      return;
    }
    setSalvando(true);
    setErro(null);
    const req: AtivoRequest = { ticker: ticker.trim().toUpperCase(), nome: nome.trim(), tipo, valorAtual: valor, liquidez, custodiaId };
    try {
      if (ativo) await investimentoService.atualizar(ativo.id, req);
      else await investimentoService.criar(req);
      queryClient.invalidateQueries({ queryKey: ['investimentos'] });
      onClose();
    } catch (err: unknown) {
      setErro(mensagemDeErro(err, 'Erro ao salvar ativo.'));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <FolhaModal
      visible={visible}
      titulo={ativo ? 'Editar ativo' : 'Novo ativo'}
      onFechar={onClose}
      acao={{ rotulo: 'Salvar', onPress: salvar, carregando: salvando }}
    >
      <ScrollView contentContainerStyle={{ padding: spacing.lg }} keyboardShouldPersistTaps="handled">
        <Field label="Ticker" value={ticker} onChangeText={setTicker} autoCapitalize="characters" placeholder="PETR4" />
        <Field label="Nome" value={nome} onChangeText={setNome} placeholder="Petrobras PN" />
        <FaixaDeChips
          rotulo="Tipo"
          opcoes={Object.keys(TIPO_ATIVO_LABEL) as TipoAtivo[]}
          rotuloDe={t => TIPO_ATIVO_LABEL[t]}
          atual={tipo}
          onEscolher={setTipo}
        />
        <Field label="Preço atual" value={valorAtual} onChangeText={(v) => setValorAtual(maskCurrencyInput(v))} keyboardType="number-pad" placeholder="0,00" />
        <FaixaDeChips
          rotulo="Liquidez"
          opcoes={['IMEDIATA', 'D1', 'D2', 'CARENCIA', 'BLOQUEADA'] as LiquidezContaFinanceira[]}
          atual={liquidez}
          onEscolher={setLiquidez}
        />
        {erro && <Erro texto={erro} />}
      </ScrollView>
    </FolhaModal>
  );
}

function MovimentoModal({ ativo, visible, onClose }: { ativo: Ativo | null; visible: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [tipo, setTipo] = useState<TipoMovimentacaoAtivo>('COMPRA');
  const [data, setData] = useState(hojeBR());
  const [quantidade, setQuantidade] = useState('');
  const [precoUnitario, setPrecoUnitario] = useState('');
  const [externa, setExterna] = useState(false);
  const [carteiraId, setCarteiraId] = useState<number | undefined>();
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);
  // Uma chave por abertura do formulário: retentativa reaproveita, movimentação
  // nova ganha chave nova. É o que impede o duplo clique de duplicar (BACKLOG-0081).
  const chaveIdempotencia = useRef('');
  const { data: contasCaixa = [] } = useQuery({ queryKey: ['contas-financeiras-caixa'], queryFn: () => contaFinanceiraService.listarParaCaixa() });

  useEffect(() => {
    if (!visible) return;
    setTipo('COMPRA');
    setData(hojeBR());
    setQuantidade('');
    setPrecoUnitario('');
    setExterna(false);
    setCarteiraId(undefined);
    setErro(null);
    chaveIdempotencia.current = `mov:${Date.now()}:${Math.random().toString(36).slice(2)}`;
  }, [visible]);

  const salvar = async () => {
    if (!ativo) return;
    const qtd = Number(quantidade.replace(',', '.'));
    const preco = parseCurrencyBR(precoUnitario);
    if (!isValidDateBR(data) || !quantidade || isNaN(qtd) || qtd <= 0 || !precoUnitario || isNaN(preco) || preco < 0) {
      setErro('Informe data, quantidade e preço válidos.');
      return;
    }
    if (!externa && tipo !== 'BONIFICACAO' && !carteiraId) {
      setErro('Escolha a conta de caixa ou marque como operação externa.');
      return;
    }
    setSalvando(true);
    setErro(null);
    try {
      await investimentoService.adicionarMovimentacao(ativo.id, {
        tipo,
        data: parseDateBR(data),
        quantidade: qtd,
        precoUnitario: preco,
        carteiraId: externa ? undefined : carteiraId,
        externa,
      }, chaveIdempotencia.current);
      queryClient.invalidateQueries({ queryKey: ['investimentos'] });
      queryClient.invalidateQueries({ queryKey: ['investimento-movimentacoes', ativo.id] });
      onClose();
    } catch (err: unknown) {
      setErro(mensagemDeErro(err, 'Erro ao registrar movimentação.'));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <FolhaModal
      visible={visible}
      titulo="Movimentação"
      onFechar={onClose}
      acao={{ rotulo: 'Salvar', onPress: salvar, carregando: salvando }}
    >
      <ScrollView contentContainerStyle={{ padding: spacing.lg }} keyboardShouldPersistTaps="handled">
        <FaixaDeChips
          rotulo="Tipo"
          opcoes={Object.keys(TIPO_MOV_LABEL) as TipoMovimentacaoAtivo[]}
          rotuloDe={t => TIPO_MOV_LABEL[t]}
          atual={tipo}
          onEscolher={setTipo}
        />
        <Field label="Data" value={data} onChangeText={(v) => setData(maskDateInput(v))} keyboardType="number-pad" placeholder="DD/MM/AAAA" />
        <Field label="Quantidade" value={quantidade} onChangeText={(v) => setQuantidade(v.replace(/[^0-9,.]/g, ''))} keyboardType="decimal-pad" placeholder="0" />
        <Field label="Preço unitário" value={precoUnitario} onChangeText={(v) => setPrecoUnitario(maskCurrencyInput(v))} keyboardType="number-pad" placeholder="0,00" />

        <RotuloDeGrupo>Origem da operação</RotuloDeGrupo>
        <View style={{ flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.md }}>
          <Chip label="Conta real" selected={!externa} onPress={() => setExterna(false)} />
          <Chip label="Snapshot externo" selected={externa} onPress={() => { setExterna(true); setCarteiraId(undefined); }} />
        </View>

        {/* Bonificação não move caixa: não há conta de onde o dinheiro saia. */}
        {!externa && tipo !== 'BONIFICACAO' && (
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, marginBottom: spacing.md }}>
            {contasCaixa.map(c => (
              <Chip key={c.id} label={c.nome} selected={carteiraId === c.id} onPress={() => setCarteiraId(c.id)} />
            ))}
          </View>
        )}
        {erro && <Erro texto={erro} />}
      </ScrollView>
    </FolhaModal>
  );
}

function DetalheAtivoModal({ ativo, onClose, onEdit }: { ativo: Ativo | null; onClose: () => void; onEdit: (ativo: Ativo) => void }) {
  const colors = useTheme();
  const queryClient = useQueryClient();
  const [movimentoVisible, setMovimentoVisible] = useState(false);
  const [excluindo, setExcluindo] = useState(false);
  const { data = [], isLoading, isError, refetch } = useQuery({
    queryKey: ['investimento-movimentacoes', ativo?.id],
    queryFn: () => investimentoService.listarMovimentacoes(ativo!.id),
    enabled: ativo != null,
  });

  const excluir = () => {
    if (!ativo) return;
    Alert.alert('Excluir ativo', `Excluir ${ativo.ticker}?`, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Excluir',
        style: 'destructive',
        onPress: async () => {
          setExcluindo(true);
          try {
            await investimentoService.deletar(ativo.id);
            queryClient.invalidateQueries({ queryKey: ['investimentos'] });
            onClose();
          } catch (err: unknown) {
            Alert.alert('Investimentos', mensagemDeErro(err, 'Erro ao excluir ativo.'));
          } finally {
            setExcluindo(false);
          }
        },
      },
    ]);
  };

  return (
    <FolhaModal
      visible={ativo != null}
      titulo={ativo?.ticker ?? 'Ativo'}
      rotuloFechar="Fechar"
      onFechar={onClose}
    >
      <ScrollView contentContainerStyle={{ padding: spacing.lg, paddingBottom: spacing.xxxl }}>
        {ativo && (
          <Card radius={radius.lg} style={{ marginBottom: spacing.md }}>
            <Text style={{ ...typography.body, color: colors.textSecondary, marginBottom: spacing.md }}>
              {ativo.nome} · {TIPO_ATIVO_LABEL[ativo.tipo]}
            </Text>
            <View style={{ flexDirection: 'row', gap: spacing.sm }}>
              <Metrica rotulo="Quantidade" valor={Number(ativo.quantidade ?? 0).toFixed(4)} />
              <Metrica
                rotulo="Resultado"
                valor={formatCurrency(Number(ativo.lucroPrejuizo ?? 0))}
                cor={Number(ativo.lucroPrejuizo ?? 0) >= 0 ? colors.success : colors.danger}
              />
            </View>
            <View style={{ flexDirection: 'row', gap: spacing.sm, marginTop: spacing.md }}>
              <Botao titulo="Movimentar" tamanho="pill" onPress={() => setMovimentoVisible(true)} style={{ flex: 1 }} />
              <Botao titulo="Editar" variante="secundario" tamanho="pill" onPress={() => onEdit(ativo)} style={{ flex: 1 }} />
            </View>
            <Botao
              titulo="Excluir ativo"
              variante="perigo"
              tamanho="pill"
              onPress={excluir}
              carregando={excluindo}
              style={{ marginTop: spacing.md }}
            />
          </Card>
        )}

        <Text style={{ ...typography.cardTitle, color: colors.textPrimary, marginBottom: spacing.md }}>Movimentações</Text>
        {isLoading ? (
          <View style={{ gap: spacing.sm }}>
            {[1, 2, 3].map(i => <SkeletonBox key={i} width="100%" height={56} borderRadius={radius.md} />)}
          </View>
        ) : isError ? (
          <EstadoVazio
            compacto
            emoji="📶"
            titulo="Não deu para carregar as movimentações"
            texto="Verifique sua conexão e tente de novo."
            acao={{ rotulo: 'Tentar de novo', onPress: () => refetch() }}
          />
        ) : data.length === 0 ? (
          <EstadoVazio
            compacto
            emoji="🧾"
            titulo="Nenhuma movimentação registrada"
            texto="Compras, vendas e proventos deste ativo aparecem aqui."
          />
        ) : (
          <View style={{ gap: spacing.sm }}>
            {data.map(m => (
              <View
                key={m.id}
                style={{
                  backgroundColor: colors.card, borderRadius: radius.md,
                  borderWidth: 1, borderColor: colors.border, padding: spacing.md,
                }}
              >
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: spacing.md }}>
                  <Text style={{ ...typography.rowTitle, fontWeight: '700', color: colors.textPrimary }}>
                    {TIPO_MOV_LABEL[m.tipo]} · {m.conciliacao}
                  </Text>
                  <Text style={{ ...typography.rowTitle, ...numeric, fontWeight: '700', color: colors.textPrimary }}>
                    {formatCurrency(Number(m.valorTotal ?? 0))}
                  </Text>
                </View>
                {m.operacaoId != null && (
                  <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.xs }}>
                    Operação #{m.operacaoId}
                  </Text>
                )}
                <Text style={{ ...typography.meta, ...numeric, color: colors.textSecondary, marginTop: spacing.xxs }}>
                  {formatDate(m.data)} · {Number(m.quantidade ?? 0)} x {formatCurrency(Number(m.precoUnitario ?? 0))}
                </Text>
              </View>
            ))}
          </View>
        )}
      </ScrollView>
      <MovimentoModal ativo={ativo} visible={movimentoVisible} onClose={() => setMovimentoVisible(false)} />
    </FolhaModal>
  );
}

export default function InvestimentosScreen() {
  const colors = useTheme();
  const tabBarSpace = useTabBarSpace();
  const [ativoModal, setAtivoModal] = useState<Ativo | null>(null);
  const [ativoModalVisible, setAtivoModalVisible] = useState(false);
  const [detalhe, setDetalhe] = useState<Ativo | null>(null);
  const { data = [], isLoading, isError, refetch } = useQuery({
    queryKey: ['investimentos'],
    queryFn: () => investimentoService.listar(),
  });

  const resumo = useMemo(() => {
    const custo = data.reduce((sum, a) => sum + Number(a.custoTotal ?? 0), 0);
    const mercado = data.reduce((sum, a) => sum + Number(a.quantidade ?? 0) * Number(a.valorAtual ?? 0), 0);
    return { custo, mercado, resultado: mercado - custo };
  }, [data]);

  const abrirNovo = () => {
    setAtivoModal(null);
    setAtivoModalVisible(true);
  };

  const abrirEdicao = (ativo: Ativo) => {
    setAtivoModal(ativo);
    setAtivoModalVisible(true);
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <CabecalhoSubTela
        titulo="Investimentos"
        apoio={<Text style={{ ...typography.body, color: colors.textSecondary }}>Ativos, posição e movimentações</Text>}
      />

      {isLoading ? (
        <View style={{ paddingHorizontal: screenPadding, gap: spacing.sm }}>
          {[1, 2, 3, 4].map(i => <SkeletonBox key={i} width="100%" height={72} borderRadius={cardRadius} />)}
        </View>
      ) : isError ? (
        <EstadoVazio
          emoji="📶"
          titulo="Não deu para carregar seus investimentos"
          texto="Verifique sua conexão e tente de novo."
          acao={{ rotulo: 'Tentar de novo', onPress: () => refetch() }}
        />
      ) : (
        <FlatList
          data={data}
          keyExtractor={item => item.id.toString()}
          contentContainerStyle={{ padding: screenPadding, paddingBottom: tabBarSpace }}
          ListHeaderComponent={(
            <Card style={{ marginBottom: spacing.md }}>
              <View style={{ flexDirection: 'row', gap: spacing.sm }}>
                <Metrica rotulo="Mercado" valor={formatCurrency(resumo.mercado)} />
                <Metrica
                  rotulo="Resultado"
                  valor={formatCurrency(resumo.resultado)}
                  cor={resumo.resultado >= 0 ? colors.success : colors.danger}
                />
              </View>
            </Card>
          )}
          ListEmptyComponent={(
            <EstadoVazio
              emoji="📈"
              titulo="Nenhum ativo cadastrado"
              texto="Toque em + para cadastrar o primeiro investimento."
              acao={{ rotulo: 'Cadastrar ativo', onPress: abrirNovo }}
            />
          )}
          renderItem={({ item }) => {
            const mercado = Number(item.quantidade ?? 0) * Number(item.valorAtual ?? 0);
            const rentabilidade = Number(item.rentabilidade ?? 0);
            return (
              // Card composto: sem `accessibilityLabel` curado, o nó funde os textos
              // do card e o leitor de tela lê ticker, nome, tipo, valor e
              // rentabilidade — o conteúdo real (DESIGN.md:169-172).
              <TouchableOpacity
                onPress={() => setDetalhe(item)}
                activeOpacity={0.7}
                accessibilityRole="button"
                accessibilityHint="Abre a posição e as movimentações do ativo"
              >
                <Card radius={radius.lg} style={{ marginBottom: spacing.sm }}>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: spacing.md }}>
                  <View style={{ flex: 1, minWidth: 0 }}>
                    <Text style={{ ...typography.rowTitle, fontWeight: '800', color: colors.textPrimary }}>{item.ticker}</Text>
                    <Text numberOfLines={1} style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.xxs }}>
                      {item.nome} · {TIPO_ATIVO_LABEL[item.tipo]}
                    </Text>
                  </View>
                  <View style={{ alignItems: 'flex-end' }}>
                    <Text style={{ ...typography.body, ...numeric, fontWeight: '800', color: colors.textPrimary }}>
                      {formatCurrency(mercado)}
                    </Text>
                    <Text style={{
                      ...typography.meta, ...numeric, marginTop: spacing.xxs,
                      color: rentabilidade >= 0 ? colors.success : colors.danger,
                    }}>
                      {rentabilidade.toFixed(2)}%
                    </Text>
                  </View>
                </View>
                </Card>
              </TouchableOpacity>
            );
          }}
        />
      )}

      <Fab onPress={abrirNovo} accessibilityLabel="Novo investimento" />

      <AtivoModal ativo={ativoModal} visible={ativoModalVisible} onClose={() => setAtivoModalVisible(false)} />
      <DetalheAtivoModal
        ativo={detalhe}
        onClose={() => setDetalhe(null)}
        onEdit={(ativo) => {
          setDetalhe(null);
          abrirEdicao(ativo);
        }}
      />
    </View>
  );
}
