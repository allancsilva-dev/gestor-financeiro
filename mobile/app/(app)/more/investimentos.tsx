import React, { useEffect, useMemo, useState } from 'react';
import { View, Text, FlatList, TouchableOpacity, Modal, ScrollView, ActivityIndicator, Alert } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import investimentoService from '../../../src/services/investimentoService';
import { Ativo, AtivoRequest, TipoAtivo, TipoMovimentacaoAtivo } from '../../../src/types';
import { useTheme } from '../../../src/theme';
import BackButton from '../../../src/components/ui/BackButton';
import { formatCurrency, formatDate, isValidDateBR, maskCurrencyInput, maskDateInput, parseCurrencyBR, parseDateBR } from '../../../src/utils/format';
import Card from '../../../src/components/ui/Card';
import Chip from '../../../src/components/ui/Chip';
import Field from '../../../src/components/ui/Field';
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

function AtivoModal({ ativo, visible, onClose }: { ativo: Ativo | null; visible: boolean; onClose: () => void }) {
  const colors = useTheme();
  const queryClient = useQueryClient();
  const [ticker, setTicker] = useState('');
  const [nome, setNome] = useState('');
  const [tipo, setTipo] = useState<TipoAtivo>('ACAO');
  const [valorAtual, setValorAtual] = useState('');
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  useEffect(() => {
    if (!visible) return;
    setTicker(ativo?.ticker ?? '');
    setNome(ativo?.nome ?? '');
    setTipo(ativo?.tipo ?? 'ACAO');
    setValorAtual(maskCurrencyInput(Number(ativo?.valorAtual ?? 0).toFixed(2)));
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
    const req: AtivoRequest = { ticker: ticker.trim().toUpperCase(), nome: nome.trim(), tipo, valorAtual: valor };
    try {
      if (ativo) await investimentoService.atualizar(ativo.id, req);
      else await investimentoService.criar(req);
      queryClient.invalidateQueries({ queryKey: ['investimentos'] });
      onClose();
    } catch (err: any) {
      setErro(err?.userMessage ?? 'Erro ao salvar ativo.');
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Modal visible={visible} animationType="slide" presentationStyle="pageSheet" onRequestClose={onClose}>
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
          <TouchableOpacity onPress={onClose} accessibilityRole="button"><Text style={{ color: colors.brandFg, fontSize: 15 }}>Cancelar</Text></TouchableOpacity>
          <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700' }}>{ativo ? 'Editar ativo' : 'Novo ativo'}</Text>
          <TouchableOpacity onPress={salvar} disabled={salvando} accessibilityRole="button">
            {salvando ? <ActivityIndicator color={colors.brand} size="small" /> : <Text style={{ color: colors.brandFg, fontSize: 15, fontWeight: '700' }}>Salvar</Text>}
          </TouchableOpacity>
        </View>
        <ScrollView contentContainerStyle={{ padding: 16 }} keyboardShouldPersistTaps="handled">
          <Field label="Ticker" value={ticker} onChangeText={setTicker} autoCapitalize="characters" placeholder="PETR4" />
          <Field label="Nome" value={nome} onChangeText={setNome} placeholder="Petrobras PN" />
          <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Tipo</Text>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
            {(Object.keys(TIPO_ATIVO_LABEL) as TipoAtivo[]).map(t => (
              <Chip key={t} label={TIPO_ATIVO_LABEL[t]} selected={tipo === t} onPress={() => setTipo(t)} />
            ))}
          </View>
          <Field label="Preço atual" value={valorAtual} onChangeText={(v) => setValorAtual(maskCurrencyInput(v))} keyboardType="number-pad" placeholder="0,00" />
          {erro && <Text style={{ color: colors.danger, fontSize: 12 }}>{erro}</Text>}
        </ScrollView>
      </View>
    </Modal>
  );
}

function MovimentoModal({ ativo, visible, onClose }: { ativo: Ativo | null; visible: boolean; onClose: () => void }) {
  const colors = useTheme();
  const queryClient = useQueryClient();
  const [tipo, setTipo] = useState<TipoMovimentacaoAtivo>('COMPRA');
  const [data, setData] = useState(hojeBR());
  const [quantidade, setQuantidade] = useState('');
  const [precoUnitario, setPrecoUnitario] = useState('');
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  useEffect(() => {
    if (!visible) return;
    setTipo('COMPRA');
    setData(hojeBR());
    setQuantidade('');
    setPrecoUnitario('');
    setErro(null);
  }, [visible]);

  const salvar = async () => {
    if (!ativo) return;
    const qtd = Number(quantidade.replace(',', '.'));
    const preco = parseCurrencyBR(precoUnitario);
    if (!isValidDateBR(data) || !quantidade || isNaN(qtd) || qtd <= 0 || !precoUnitario || isNaN(preco) || preco < 0) {
      setErro('Informe data, quantidade e preço válidos.');
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
      });
      queryClient.invalidateQueries({ queryKey: ['investimentos'] });
      queryClient.invalidateQueries({ queryKey: ['investimento-movimentacoes', ativo.id] });
      onClose();
    } catch (err: any) {
      setErro(err?.userMessage ?? 'Erro ao registrar movimentação.');
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Modal visible={visible} animationType="slide" presentationStyle="pageSheet" onRequestClose={onClose}>
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
          <TouchableOpacity onPress={onClose} accessibilityRole="button"><Text style={{ color: colors.brandFg, fontSize: 15 }}>Cancelar</Text></TouchableOpacity>
          <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700' }}>Movimentação</Text>
          <TouchableOpacity onPress={salvar} disabled={salvando} accessibilityRole="button">
            {salvando ? <ActivityIndicator color={colors.brand} size="small" /> : <Text style={{ color: colors.brandFg, fontSize: 15, fontWeight: '700' }}>Salvar</Text>}
          </TouchableOpacity>
        </View>
        <ScrollView contentContainerStyle={{ padding: 16 }} keyboardShouldPersistTaps="handled">
          <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Tipo</Text>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
            {(Object.keys(TIPO_MOV_LABEL) as TipoMovimentacaoAtivo[]).map(t => (
              <Chip key={t} label={TIPO_MOV_LABEL[t]} selected={tipo === t} onPress={() => setTipo(t)} />
            ))}
          </View>
          <Field label="Data" value={data} onChangeText={(v) => setData(maskDateInput(v))} keyboardType="number-pad" placeholder="DD/MM/AAAA" />
          <Field label="Quantidade" value={quantidade} onChangeText={(v) => setQuantidade(v.replace(/[^0-9,.]/g, ''))} keyboardType="decimal-pad" placeholder="0" />
          <Field label="Preço unitário" value={precoUnitario} onChangeText={(v) => setPrecoUnitario(maskCurrencyInput(v))} keyboardType="number-pad" placeholder="0,00" />
          {erro && <Text style={{ color: colors.danger, fontSize: 12 }}>{erro}</Text>}
        </ScrollView>
      </View>
    </Modal>
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
          } catch (err: any) {
            Alert.alert('Investimentos', err?.userMessage ?? 'Erro ao excluir ativo.');
          } finally {
            setExcluindo(false);
          }
        },
      },
    ]);
  };

  return (
    <Modal visible={ativo != null} animationType="slide" presentationStyle="pageSheet" onRequestClose={onClose}>
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
          <View style={{ flex: 1 }}>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700' }}>{ativo?.ticker}</Text>
            <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 2 }}>{ativo?.nome}</Text>
          </View>
          <TouchableOpacity onPress={onClose} accessibilityRole="button"><Text style={{ color: colors.brandFg, fontSize: 15, fontWeight: '600' }}>Fechar</Text></TouchableOpacity>
        </View>
        <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 32 }}>
          {ativo && (
            <Card radius={16} style={{ marginBottom: 12 }}>
              <View style={{ flexDirection: 'row', gap: 8 }}>
                <View style={{ flex: 1 }}>
                  <Text style={{ color: colors.textSecondary, fontSize: 11 }}>Quantidade</Text>
                  <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '800', marginTop: 2 }}>{Number(ativo.quantidade ?? 0).toFixed(4)}</Text>
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={{ color: colors.textSecondary, fontSize: 11 }}>Resultado</Text>
                  <Text style={{ color: Number(ativo.lucroPrejuizo ?? 0) >= 0 ? colors.success : colors.danger, fontSize: 16, fontWeight: '800', marginTop: 2 }}>
                    {formatCurrency(Number(ativo.lucroPrejuizo ?? 0))}
                  </Text>
                </View>
              </View>
              <View style={{ flexDirection: 'row', gap: 8, marginTop: 12 }}>
                <TouchableOpacity onPress={() => setMovimentoVisible(true)} accessibilityRole="button" style={{ flex: 1, minHeight: 44, borderRadius: 999, backgroundColor: colors.brandBg, alignItems: 'center', justifyContent: 'center' }}>
                  <Text style={{ color: colors.brandFg, fontSize: 13, fontWeight: '700' }}>Movimentar</Text>
                </TouchableOpacity>
                <TouchableOpacity onPress={() => onEdit(ativo)} accessibilityRole="button" style={{ flex: 1, minHeight: 44, borderRadius: 999, backgroundColor: colors.infoBg, alignItems: 'center', justifyContent: 'center' }}>
                  <Text style={{ color: colors.info, fontSize: 13, fontWeight: '700' }}>Editar</Text>
                </TouchableOpacity>
              </View>
              <TouchableOpacity onPress={excluir} disabled={excluindo} accessibilityRole="button" style={{ marginTop: 10, minHeight: 44, borderRadius: 999, backgroundColor: colors.dangerBg, alignItems: 'center', justifyContent: 'center' }}>
                {excluindo ? <ActivityIndicator color={colors.danger} size="small" /> : <Text style={{ color: colors.danger, fontSize: 13, fontWeight: '700' }}>Excluir ativo</Text>}
              </TouchableOpacity>
            </Card>
          )}

          <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '700', marginBottom: 10 }}>Movimentações</Text>
          {isLoading ? (
            <View style={{ gap: 8 }}>{[1, 2, 3].map(i => <SkeletonBox key={i} width="100%" height={56} />)}</View>
          ) : isError ? (
            <TouchableOpacity onPress={() => refetch()} accessibilityRole="button" style={{ paddingVertical: 12 }}>
              <Text style={{ color: colors.brandFg, fontWeight: '600' }}>Tentar novamente</Text>
            </TouchableOpacity>
          ) : data.length === 0 ? (
            <Text style={{ color: colors.textSecondary, fontSize: 13 }}>Nenhuma movimentação registrada.</Text>
          ) : (
            <View style={{ gap: 8 }}>
              {data.map(m => (
                <View key={m.id} style={{ backgroundColor: colors.card, borderRadius: 12, borderWidth: 1, borderColor: colors.border, padding: 12 }}>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 10 }}>
                    <Text style={{ color: colors.textPrimary, fontSize: 13, fontWeight: '700' }}>{TIPO_MOV_LABEL[m.tipo]}</Text>
                    <Text style={{ color: colors.textPrimary, fontSize: 13, fontWeight: '700' }}>{formatCurrency(Number(m.valorTotal ?? 0))}</Text>
                  </View>
                  <Text style={{ color: colors.textSecondary, fontSize: 11, marginTop: 3 }}>
                    {formatDate(m.data + 'T00:00:00')} · {Number(m.quantidade ?? 0)} x {formatCurrency(Number(m.precoUnitario ?? 0))}
                  </Text>
                </View>
              ))}
            </View>
          )}
        </ScrollView>
        <MovimentoModal ativo={ativo} visible={movimentoVisible} onClose={() => setMovimentoVisible(false)} />
      </View>
    </Modal>
  );
}

export default function InvestimentosScreen() {
  const colors = useTheme();
  const insets = useSafeAreaInsets();
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
      <View style={{ paddingTop: insets.top + 16, paddingHorizontal: 16, paddingBottom: 12 }}>
        <BackButton />
        <Text style={{ color: colors.textPrimary, fontSize: 23, fontWeight: '800', letterSpacing: -0.4 }}>Investimentos</Text>
        <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4 }}>Ativos, posição e movimentações</Text>
      </View>

      {isLoading ? (
        <View style={{ padding: 16, gap: 8 }}>{[1, 2, 3, 4].map(i => <SkeletonBox key={i} width="100%" height={72} />)}</View>
      ) : isError ? (
        <View style={{ alignItems: 'center', padding: 48 }}>
          <Text style={{ color: colors.textSecondary }}>Erro ao carregar investimentos</Text>
          <TouchableOpacity onPress={() => refetch()} style={{ marginTop: 8 }} accessibilityRole="button"><Text style={{ color: colors.brandFg, fontWeight: '600' }}>Tentar novamente</Text></TouchableOpacity>
        </View>
      ) : (
        <FlatList
          data={data}
          keyExtractor={item => item.id.toString()}
          contentContainerStyle={{ padding: 16, paddingBottom: 96 }}
          ListHeaderComponent={(
            <Card radius={18} style={{ marginBottom: 12 }}>
              <View style={{ flexDirection: 'row', gap: 8 }}>
                <View style={{ flex: 1 }}>
                  <Text style={{ color: colors.textSecondary, fontSize: 11 }}>Mercado</Text>
                  <Text numberOfLines={1} adjustsFontSizeToFit style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '800', marginTop: 2 }}>{formatCurrency(resumo.mercado)}</Text>
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={{ color: colors.textSecondary, fontSize: 11 }}>Resultado</Text>
                  <Text numberOfLines={1} adjustsFontSizeToFit style={{ color: resumo.resultado >= 0 ? colors.success : colors.danger, fontSize: 16, fontWeight: '800', marginTop: 2 }}>{formatCurrency(resumo.resultado)}</Text>
                </View>
              </View>
            </Card>
          )}
          ListEmptyComponent={(
            <View style={{ alignItems: 'center', paddingVertical: 48 }}>
              <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>Nenhum ativo cadastrado</Text>
              <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4, textAlign: 'center' }}>Toque em + para cadastrar o primeiro investimento.</Text>
            </View>
          )}
          renderItem={({ item }) => {
            const mercado = Number(item.quantidade ?? 0) * Number(item.valorAtual ?? 0);
            return (
              <TouchableOpacity onPress={() => setDetalhe(item)} activeOpacity={0.7} accessibilityRole="button" accessibilityLabel={`Abrir investimento ${item.ticker}`}>
                <Card radius={14} style={{ marginBottom: 8 }}>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 12 }}>
                    <View style={{ flex: 1, minWidth: 0 }}>
                      <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '800' }}>{item.ticker}</Text>
                      <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 2 }} numberOfLines={1}>{item.nome} · {TIPO_ATIVO_LABEL[item.tipo]}</Text>
                    </View>
                    <View style={{ alignItems: 'flex-end' }}>
                      <Text style={{ color: colors.textPrimary, fontSize: 14, fontWeight: '800' }}>{formatCurrency(mercado)}</Text>
                      <Text style={{ color: Number(item.rentabilidade ?? 0) >= 0 ? colors.success : colors.danger, fontSize: 11, marginTop: 2 }}>
                        {Number(item.rentabilidade ?? 0).toFixed(2)}%
                      </Text>
                    </View>
                  </View>
                </Card>
              </TouchableOpacity>
            );
          }}
        />
      )}

      <TouchableOpacity
        onPress={abrirNovo}
        accessibilityRole="button"
        accessibilityLabel="Novo investimento"
        style={{ position: 'absolute', bottom: 24, right: 16, width: 56, height: 56, borderRadius: 28, backgroundColor: colors.brand, alignItems: 'center', justifyContent: 'center' }}
      >
        <Text style={{ color: colors.brandText, fontSize: 28, lineHeight: 30 }}>+</Text>
      </TouchableOpacity>

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
