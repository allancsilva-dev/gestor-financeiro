import React, { useEffect, useRef, useState } from 'react';
import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, StyleSheet, Modal, TextInput } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTheme } from '../../../src/theme';
import BackButton from '../../../src/components/ui/BackButton';
import faturaService from '../../../src/services/faturaService';
import { contaService } from '../../../src/services/contaService';
import { carteiraService } from '../../../src/services/carteiraService';
import { FaturaResponse, FaturaLancamento, Conta, ContaRequest, Carteira } from '../../../src/types';
import { formatCurrency, formatDate, parseCurrencyBR, maskCurrencyInput } from '../../../src/utils/format';

const MESES = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'];

export default function FaturasScreen() {
  const colors = useTheme();
  const queryClient = useQueryClient();
  const insets = useSafeAreaInsets();
  const now = new Date();
  const [mes, setMes] = useState(now.getMonth() + 1);
  const [ano, setAno] = useState(now.getFullYear());
  const [contaIdx, setContaIdx] = useState(0);
  const [paying, setPaying] = useState(false);
  const payingRef = useRef(false);
  const [payError, setPayError] = useState<string | null>(null);
  const [carteiraPagamentoId, setCarteiraPagamentoId] = useState<number | null>(null);
  const [valorPagamento, setValorPagamento] = useState('');

  const [modalVisible, setModalVisible] = useState(false);
  const [nome, setNome] = useState('');
  const [banco, setBanco] = useState('');
  const [limite, setLimite] = useState('');
  const [diaFechamento, setDiaFechamento] = useState('');
  const [diaVencimento, setDiaVencimento] = useState('');
  const [formError, setFormError] = useState<string | null>(null);

  const { data: contasData } = useQuery({
    queryKey: ['contas-fatura'],
    queryFn: () => contaService.listar(),
  });
  const contasCredito = (contasData?.content ?? []).filter((c: Conta) => c.tipo === 'CREDITO');
  const contaSelecionada = contasCredito[Math.min(contaIdx, Math.max(contasCredito.length - 1, 0))];

  const { data: carteirasData } = useQuery({
    queryKey: ['carteiras'],
    queryFn: () => carteiraService.listar(),
  });
  const carteiras = carteirasData?.content ?? [];

  useEffect(() => {
    if (carteiraPagamentoId == null && carteiras.length > 0) {
      setCarteiraPagamentoId(carteiras[0].id);
    }
  }, [carteiras, carteiraPagamentoId]);

  const { data: fatura, isLoading, refetch } = useQuery<FaturaResponse | null>({
    queryKey: ['fatura', contaSelecionada?.id, mes, ano],
    queryFn: () => {
      if (!contaSelecionada?.id) return null;
      if (mes === now.getMonth() + 1 && ano === now.getFullYear())
        return faturaService.buscarAtual(contaSelecionada.id!);
      return faturaService.buscarPorMes(contaSelecionada.id!, mes, ano).catch(() => null);
    },
    enabled: !!contaSelecionada?.id,
  });

  useEffect(() => {
    const restante = Math.max(Number(fatura?.valorTotal ?? 0) - Number(fatura?.valorPago ?? 0), 0);
    setValorPagamento(restante > 0 ? maskCurrencyInput(Math.round(restante * 100).toString()) : '');
  }, [fatura?.id, fatura?.valorTotal, fatura?.valorPago]);

  const resetForm = () => {
    setNome(''); setBanco(''); setLimite(''); setDiaFechamento(''); setDiaVencimento(''); setFormError(null);
  };

  const criarMutation = useMutation({
    mutationFn: (req: ContaRequest) => contaService.criar(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contas-fatura'] });
      queryClient.invalidateQueries({ queryKey: ['contas'] });
      setModalVisible(false);
      resetForm();
    },
    onError: (err: any) => {
      setFormError(err?.userMessage ?? 'Erro ao cadastrar cartão.');
    },
  });

  const salvarCartao = () => {
    setFormError(null);
    if (!nome.trim()) { setFormError('Nome do cartão é obrigatório.'); return; }
    const v = parseCurrencyBR(limite);
    if (isNaN(v) || v <= 0) { setFormError('Limite total obrigatório e positivo.'); return; }
    const fech = parseInt(diaFechamento, 10);
    const venc = parseInt(diaVencimento, 10);
    if (isNaN(fech) || fech < 1 || fech > 31) { setFormError('Dia de fechamento deve estar entre 1 e 31.'); return; }
    if (isNaN(venc) || venc < 1 || venc > 31) { setFormError('Dia de vencimento deve estar entre 1 e 31.'); return; }
    criarMutation.mutate({
      nome: nome.trim(),
      tipo: 'CREDITO',
      limiteTotal: v,
      diaFechamento: fech,
      diaVencimento: venc,
      banco: banco.trim() || undefined,
    });
  };

  const handlePagar = async () => {
    if (payingRef.current) return;
    if (!fatura || saldoRestante <= 0) return;
    if (!carteiraPagamentoId) {
      setPayError('Selecione a conta de pagamento.');
      return;
    }
    const valor = parseCurrencyBR(valorPagamento);
    if (!Number.isFinite(valor) || valor <= 0) {
      setPayError('Informe um valor de pagamento válido.');
      return;
    }
    if (valor > saldoRestante) {
      setPayError(`Valor máximo: ${formatCurrency(saldoRestante)}.`);
      return;
    }
    setPayError(null);
    payingRef.current = true;
    setPaying(true);
    try {
      const key = `${fatura.id}:${Date.now()}:${Math.random().toString(36).slice(2)}`;
      await faturaService.pagarFatura(fatura.id, valor, carteiraPagamentoId, key);
      queryClient.invalidateQueries({ queryKey: ['carteiras'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-resumo'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-projecao'] });
      queryClient.invalidateQueries({ queryKey: ['contas-fatura'] });
      refetch();
    } catch (err: any) {
      setPayError(err?.userMessage ?? 'Erro ao pagar fatura.');
    } finally {
      payingRef.current = false;
      setPaying(false);
    }
  };

  const mesAnterior = () => { if (mes === 1) { setMes(12); setAno(ano - 1); } else setMes(mes - 1); };
  const mesProximo = () => { if (mes === 12) { setMes(1); setAno(ano + 1); } else setMes(mes + 1); };

  const limiteTotal = Number(contaSelecionada?.limiteTotal ?? 0);
  const gasto = Number(contaSelecionada?.valorGasto ?? 0);
  const creditoCartao = gasto < 0 ? Math.abs(gasto) : 0;
  const usoLimite = limiteTotal > 0 ? Math.min(Math.max(gasto, 0) / limiteTotal, 1) : 0;
  const saldoRestante = Math.max(Number(fatura?.valorTotal ?? 0) - Number(fatura?.valorPago ?? 0), 0);

  // Aberta é estado normal, não alerta: vermelho fica só para vencida
  const statusBadge =
    fatura?.status === 'PAGA' ? { fg: colors.success, bg: colors.success + '20', label: 'PAGA' }
    : fatura?.status === 'VENCIDA' ? { fg: colors.danger, bg: colors.danger + '20', label: 'VENCIDA' }
    : fatura?.status === 'FECHADA' ? { fg: colors.warning, bg: colors.warning + '20', label: 'FECHADA' }
    : { fg: colors.brandFg, bg: colors.brandBg, label: 'ABERTA' };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScrollView style={styles.container} contentContainerStyle={{ paddingBottom: 40 }}>
        <View style={{ paddingTop: insets.top + 16, paddingHorizontal: 16, paddingBottom: 12 }}>
          <BackButton />
          <Text style={{ color: colors.textPrimary, fontSize: 23, fontWeight: '800', letterSpacing: -0.4 }}>Faturas</Text>
          <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4 }}>
            {contasCredito.length > 0
              ? `${contasCredito.length} ${contasCredito.length === 1 ? 'cartão' : 'cartões'} de crédito`
              : 'Cartões de crédito e lançamentos'}
          </Text>
        </View>

        <View style={{ paddingHorizontal: 16 }}>
          <View style={styles.nav}>
            <TouchableOpacity onPress={mesAnterior} style={{ padding: 8 }} accessibilityLabel="Mês anterior">
              <Text style={{ color: colors.textSecondary, fontSize: 18 }}>‹</Text>
            </TouchableOpacity>
            <Text style={[styles.mesAno, { color: colors.textPrimary }]}>{MESES[mes - 1]} {ano}</Text>
            <TouchableOpacity onPress={mesProximo} style={{ padding: 8 }} accessibilityLabel="Próximo mês">
              <Text style={{ color: colors.textSecondary, fontSize: 18 }}>›</Text>
            </TouchableOpacity>
          </View>

          {contasCredito.length > 0 && (
            <View style={{ flexDirection: 'row', gap: 6, marginBottom: 16, flexWrap: 'wrap' }}>
              {contasCredito.map((c: Conta, i: number) => (
                <TouchableOpacity key={c.id} onPress={() => setContaIdx(i)} style={[styles.chip, { backgroundColor: i === contaIdx ? colors.brand : colors.card, borderColor: i === contaIdx ? colors.brand : colors.border }]}>
                  <Text style={{ color: i === contaIdx ? colors.brandText : colors.textSecondary, fontSize: 13, fontWeight: '500' }}>{c.nome}</Text>
                </TouchableOpacity>
              ))}
              <TouchableOpacity onPress={() => setModalVisible(true)} style={[styles.chip, { backgroundColor: colors.brandBg, borderColor: 'transparent' }]} accessibilityLabel="Cadastrar novo cartão">
                <Text style={{ color: colors.brandFg, fontSize: 13, fontWeight: '600' }}>+ Novo cartão</Text>
              </TouchableOpacity>
            </View>
          )}

          {contasCredito.length === 0 ? (
            <View style={{ alignItems: 'center', paddingVertical: 56, paddingHorizontal: 24 }}>
              <Text style={{ fontSize: 40 }}>💳</Text>
              <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600', marginTop: 12, textAlign: 'center' }}>Nenhum cartão cadastrado</Text>
              <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4, textAlign: 'center' }}>
                Cadastre seu cartão de crédito com limite, banco e datas de fechamento para acompanhar as faturas.
              </Text>
              <TouchableOpacity onPress={() => setModalVisible(true)} style={[styles.ctaBtn, { backgroundColor: colors.brand }]}>
                <Text style={{ color: colors.brandText, fontWeight: '700', fontSize: 14 }}>Cadastrar cartão</Text>
              </TouchableOpacity>
            </View>
          ) : isLoading ? (
            <ActivityIndicator color={colors.brand} style={{ marginTop: 40 }} />
          ) : (
            <View style={{ gap: 16, marginTop: 4 }}>
              <View style={[styles.card, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <View style={{ flex: 1, paddingRight: 12 }}>
                    <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700' }}>{contaSelecionada?.nome}</Text>
                    {!!contaSelecionada?.banco && (
                      <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 1 }}>{contaSelecionada.banco}</Text>
                    )}
                    <Text style={{ color: colors.textSecondary, fontSize: 11, marginTop: 4 }}>
                      Fecha: {fatura?.dataFechamento ? formatDate(fatura.dataFechamento) : contaSelecionada?.diaFechamento ? `dia ${contaSelecionada.diaFechamento}` : '—'}
                      {' • '}
                      Vence: {fatura?.dataVencimento ? formatDate(fatura.dataVencimento) : contaSelecionada?.diaVencimento ? `dia ${contaSelecionada.diaVencimento}` : '—'}
                    </Text>
                  </View>
                  <View style={{ alignItems: 'flex-end' }}>
                    <Text style={{ color: colors.textPrimary, fontSize: 22, fontWeight: '800', fontVariant: ['tabular-nums'] }}>{formatCurrency(fatura?.valorTotal ?? 0)}</Text>
                    {fatura && (
                      <View style={[styles.badge, { backgroundColor: statusBadge.bg }]}>
                        <Text style={{ color: statusBadge.fg, fontSize: 10, fontWeight: '600' }}>{statusBadge.label}</Text>
                      </View>
                    )}
                  </View>
                </View>

                {limiteTotal > 0 && (
                  <View style={{ marginTop: 12 }}>
                    <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 }}>
                      <Text style={{ color: colors.textSecondary, fontSize: 11 }}>
                        {creditoCartao > 0 ? 'Crédito disponível' : 'Limite usado'}
                      </Text>
                      <Text style={{ color: colors.textSecondary, fontSize: 11, fontVariant: ['tabular-nums'] }}>
                        {creditoCartao > 0
                          ? `${formatCurrency(creditoCartao)} de crédito`
                          : `${formatCurrency(gasto)} de ${formatCurrency(limiteTotal)}`}
                      </Text>
                    </View>
                    <View style={{ height: 6, borderRadius: 3, backgroundColor: colors.border, overflow: 'hidden' }}>
                      <View style={{ height: 6, borderRadius: 3, width: `${usoLimite * 100}%`, backgroundColor: usoLimite >= 0.9 ? colors.danger : usoLimite >= 0.7 ? colors.warning : colors.brand }} />
                    </View>
                  </View>
                )}
              </View>

              {fatura && fatura.status !== 'PAGA' && saldoRestante > 0 && (
                <View style={{ gap: 10 }}>
                  <Text style={{ color: colors.textPrimary, fontWeight: '600' }}>Pagar com</Text>
                  {fatura.valorPago > 0 && (
                    <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 8 }}>
                      <Text style={{ color: colors.textSecondary, fontSize: 12 }}>Pago: {formatCurrency(fatura.valorPago)}</Text>
                      <Text style={{ color: colors.textPrimary, fontSize: 12, fontWeight: '700' }}>Restante: {formatCurrency(saldoRestante)}</Text>
                    </View>
                  )}
                  <View style={{ flexDirection: 'row', gap: 6, flexWrap: 'wrap' }}>
                    {carteiras.map((c: Carteira) => (
                      <TouchableOpacity key={c.id} onPress={() => setCarteiraPagamentoId(c.id)} style={[styles.chip, { backgroundColor: carteiraPagamentoId === c.id ? colors.brand : colors.card, borderColor: carteiraPagamentoId === c.id ? colors.brand : colors.border }]}>
                        <Text style={{ color: carteiraPagamentoId === c.id ? colors.brandText : colors.textSecondary, fontSize: 13, fontWeight: '500' }}>{c.nome}</Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                  <TextInput
                    accessibilityLabel="Valor do pagamento"
                    value={valorPagamento}
                    onChangeText={(t) => setValorPagamento(maskCurrencyInput(t))}
                    keyboardType="number-pad"
                    placeholder="0,00"
                    placeholderTextColor={colors.textMuted}
                    style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary }]}
                  />
                  {payError && <Text style={{ color: colors.danger, fontSize: 12 }}>{payError}</Text>}
                  <TouchableOpacity onPress={handlePagar} disabled={paying} style={[styles.payBtn, { backgroundColor: colors.success }]}>
                    {paying ? <ActivityIndicator color="white" /> : <Text style={{ color: 'white', fontWeight: '700', fontSize: 15 }}>Pagar Fatura</Text>}
                  </TouchableOpacity>
                </View>
              )}

              <Text style={{ color: colors.textPrimary, fontWeight: '600', marginTop: 4 }}>Lançamentos</Text>
              {!fatura || fatura.lancamentos.length === 0 ? (
                <Text style={{ color: colors.textSecondary, textAlign: 'center', fontSize: 13, padding: 24 }}>
                  Nenhum lançamento em {MESES[mes - 1].toLowerCase()}
                </Text>
              ) : (
                fatura.lancamentos.map((l: FaturaLancamento, i: number) => {
                  // Backend prefixa a descrição com "Estorno:"/"Ajuste:"; o badge assume esse papel
                  const descricao = l.tipo !== 'COMPRA' ? l.descricao.replace(/^(Estorno|Ajuste):\s*/, '') : l.descricao;
                  const tipoBadge = l.tipo === 'ESTORNO'
                    ? { fg: colors.success, bg: colors.success + '20', label: 'ESTORNO' }
                    : l.tipo === 'AJUSTE'
                      ? { fg: colors.warning, bg: colors.warning + '20', label: 'AJUSTE' }
                      : l.tipo === 'CREDITO_ANTERIOR'
                        ? { fg: colors.success, bg: colors.success + '20', label: 'CRÉDITO ANTERIOR' }
                        : l.tipo === 'SALDO_DEVEDOR_ANTERIOR'
                          ? { fg: colors.warning, bg: colors.warning + '20', label: 'SALDO DEVEDOR ANTERIOR' }
                          : null;
                  // Saldo devedor rolado é aviso (dívida carregada), não erro: nunca usar a cor de perigo aqui.
                  const valorColor = l.tipo === 'SALDO_DEVEDOR_ANTERIOR'
                    ? colors.warning
                    : l.valor < 0 ? colors.success : colors.danger;
                  return (
                    <View key={l.transacaoId || i} style={[styles.lancamento, { backgroundColor: colors.card, borderColor: colors.border }]}>
                      <View style={{ width: 32, height: 32, borderRadius: 8, backgroundColor: l.categoriaCor + '20', alignItems: 'center', justifyContent: 'center' }}>
                        <Text style={{ fontSize: 14 }}>{l.categoriaIcone || '💳'}</Text>
                      </View>
                      <View style={{ flex: 1 }}>
                        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                          <Text numberOfLines={1} style={{ color: colors.textPrimary, fontSize: 13, flexShrink: 1 }}>{descricao}</Text>
                          {tipoBadge && (
                            <View style={{ backgroundColor: tipoBadge.bg, paddingHorizontal: 6, paddingVertical: 1, borderRadius: 8 }}>
                              <Text style={{ color: tipoBadge.fg, fontSize: 9, fontWeight: '700' }}>{tipoBadge.label}</Text>
                            </View>
                          )}
                        </View>
                        <Text style={{ color: colors.textSecondary, fontSize: 11 }}>
                          {formatDate(l.data)}
                          {l.totalParcelas ? ` · ${l.parcelaAtual}/${l.totalParcelas}` : ''}
                        </Text>
                      </View>
                      <Text style={{ color: valorColor, fontSize: 13, fontWeight: '600', fontVariant: ['tabular-nums'] }}>{formatCurrency(l.valor)}</Text>
                    </View>
                  );
                })
              )}
            </View>
          )}
        </View>
      </ScrollView>

      <Modal visible={modalVisible} animationType="slide" presentationStyle="pageSheet" onRequestClose={() => { setModalVisible(false); resetForm(); }}>
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity onPress={() => { setModalVisible(false); resetForm(); }}>
              <Text style={{ color: colors.brand, fontSize: 15 }}>Cancelar</Text>
            </TouchableOpacity>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '600' }}>Novo Cartão</Text>
            <TouchableOpacity disabled={criarMutation.status === 'pending'} onPress={salvarCartao}>
              <Text style={{ color: criarMutation.status === 'pending' ? colors.textMuted : colors.brand, fontSize: 15, fontWeight: '600' }}>Salvar</Text>
            </TouchableOpacity>
          </View>
          <ScrollView contentContainerStyle={{ padding: 16 }}>
            <Text style={[styles.label, { color: colors.textSecondary }]}>Nome do cartão</Text>
            <TextInput accessibilityLabel="Nome do cartão" value={nome} onChangeText={setNome} placeholder="Ex.: Nubank Roxinho" placeholderTextColor={colors.textMuted} style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary }]} />

            <Text style={[styles.label, { color: colors.textSecondary }]}>Banco</Text>
            <TextInput accessibilityLabel="Banco do cartão" value={banco} onChangeText={setBanco} placeholder="Ex.: Nubank, Itaú, Inter" placeholderTextColor={colors.textMuted} style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary }]} />

            <Text style={[styles.label, { color: colors.textSecondary }]}>Limite total</Text>
            <TextInput accessibilityLabel="Limite total" value={limite} onChangeText={(t) => setLimite(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" placeholderTextColor={colors.textMuted} style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary }]} />

            <View style={{ flexDirection: 'row', gap: 12 }}>
              <View style={{ flex: 1 }}>
                <Text style={[styles.label, { color: colors.textSecondary }]}>Dia de fechamento</Text>
                <TextInput accessibilityLabel="Dia de fechamento" value={diaFechamento} onChangeText={(t) => setDiaFechamento(t.replace(/\D/g, '').slice(0, 2))} keyboardType="number-pad" placeholder="Ex.: 28" placeholderTextColor={colors.textMuted} style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary }]} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={[styles.label, { color: colors.textSecondary }]}>Dia de vencimento</Text>
                <TextInput accessibilityLabel="Dia de vencimento" value={diaVencimento} onChangeText={(t) => setDiaVencimento(t.replace(/\D/g, '').slice(0, 2))} keyboardType="number-pad" placeholder="Ex.: 5" placeholderTextColor={colors.textMuted} style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary }]} />
              </View>
            </View>

            {formError && <Text style={{ color: colors.danger, marginTop: 4 }}>{formError}</Text>}
          </ScrollView>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  nav: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 },
  mesAno: { fontSize: 16, fontWeight: '700' },
  chip: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 20, borderWidth: 1 },
  card: { borderRadius: 16, borderWidth: 1, padding: 16 },
  badge: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: 10, marginTop: 4 },
  payBtn: { height: 48, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  ctaBtn: { marginTop: 16, paddingHorizontal: 20, height: 44, borderRadius: 12, alignItems: 'center', justifyContent: 'center' },
  lancamento: { flexDirection: 'row', alignItems: 'center', gap: 10, padding: 10, borderRadius: 8, borderWidth: 1 },
  label: { fontSize: 12, fontWeight: '600', marginBottom: 6, marginTop: 4 },
  input: { borderWidth: 1, borderRadius: 10, padding: 12, fontSize: 15, marginBottom: 14 },
});
