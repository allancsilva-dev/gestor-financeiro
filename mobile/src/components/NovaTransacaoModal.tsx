import React, { useEffect, useRef, useState } from 'react';
import { View, Text, Modal, TouchableOpacity, ActivityIndicator, ScrollView } from 'react-native';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { transacaoService } from '../services/transacaoService';
import { categoriaService } from '../services/categoriaService';
import contaFinanceiraService from '../services/contaFinanceiraService';
import cartaoService from '../services/cartaoService';
import { useTheme } from '../theme';
import { parseDateBR, isValidDateBR, parseCurrencyBR, maskCurrencyInput, maskDateInput, todayBR } from '../utils/format';
import { TransacaoRequest, TipoTransacao, SugestaoCategoria } from '../types';
import { getLancamentoPrefs, setLancamentoPrefs } from '../store/lancamentoPrefs';
import { CATEGORIAS_INICIAIS } from '../domain/categoriasIniciais';
import { CATEGORY_COLORS } from '../utils/format';
import { isValidDayOfMonth } from '../utils/validate';
import Chip from './ui/Chip';
import Field from './ui/Field';

// Pré-preenchimento do "Repetir lançamento" (PR-F3-05): exige confirmação
// explícita no Salvar — nunca grava sozinho.
export interface LancamentoInicial {
  descricao: string;
  valor: number;
  tipo: TipoTransacao;
  categoriaId?: number;
  cartaoId?: number;
}

interface NovaTransacaoModalProps {
  visible: boolean;
  onClose: () => void;
  onSaved?: () => void;
  initialTipo?: TipoTransacao;
  initialData?: LancamentoInicial | null;
}

const DEBOUNCE_SUGESTAO_MS = 600;

// Sheet "Nova Transação" — aberto pelo + central da tab bar, pelos atalhos da
// home e pelo "Repetir lançamento". Fluxo principal: valor → descrição →
// confirmar; data default hoje; observações/parcelamento em "Mais detalhes".
export default function NovaTransacaoModal({ visible, onClose, onSaved, initialTipo = 'SAIDA', initialData = null }: NovaTransacaoModalProps) {
  const colors = useTheme();
  const queryClient = useQueryClient();

  const [salvando, setSalvando] = useState(false);
  const [erroForm, setErroForm] = useState<string | null>(null);
  const [descricaoError, setDescricaoError] = useState<string | null>(null);
  const [valorError, setValorError] = useState<string | null>(null);
  const [dataError, setDataError] = useState<string | null>(null);
  const [categoriaError, setCategoriaError] = useState<string | null>(null);
  const [pagamentoError, setPagamentoError] = useState<string | null>(null);

  const [descricao, setDescricao] = useState('');
  const [valor, setValor] = useState('');
  const [data, setData] = useState('');
  const [tipo, setTipo] = useState<TipoTransacao>(initialTipo);
  const [formaPagamento, setFormaPagamento] = useState<'CARTEIRA' | 'CARTAO'>('CARTEIRA');
  const [categoriaId, setCategoriaId] = useState<number | null>(null);
  const [carteiraId, setCarteiraId] = useState<number | null>(null);
  const [cartaoId, setCartaoId] = useState<number | null>(null);
  const [parcelado, setParcelado] = useState(false);
  const [totalParcelas, setTotalParcelas] = useState('');
  const [observacoes, setObservacoes] = useState('');
  const [maisDetalhes, setMaisDetalhes] = useState(false);

  const [sugestao, setSugestao] = useState<SugestaoCategoria | null>(null);

  // Setup progressivo (PR-F3-10): criação contextual quando falta cadastro
  const [criandoPacote, setCriandoPacote] = useState(false);
  const [novaCategoriaNome, setNovaCategoriaNome] = useState('');
  const [criandoCategoria, setCriandoCategoria] = useState(false);
  const [criarCartaoAberto, setCriarCartaoAberto] = useState(false);
  const [novoCartao, setNovoCartao] = useState({ nome: 'Cartão Principal', limite: '', fechamento: '5', vencimento: '12' });
  const [criandoCartao, setCriandoCartao] = useState(false);
  const [setupError, setSetupError] = useState<string | null>(null);
  const categoriaEscolhidaManualmente = useRef(false);
  const prefsAplicadas = useRef(false);

  // Abertura: data default hoje, pré-preenchimento do repetir e última
  // conta/cartão usados no dispositivo (nunca sobrescreve o repetir)
  useEffect(() => {
    if (!visible) return;
    setTipo(initialData?.tipo ?? initialTipo);
    setData(todayBR());
    if (initialData) {
      setDescricao(initialData.descricao);
      setValor(maskCurrencyInput(String(Math.round(initialData.valor * 100))));
      if (initialData.categoriaId != null) {
        setCategoriaId(initialData.categoriaId);
        categoriaEscolhidaManualmente.current = true;
      }
      if (initialData.cartaoId != null) {
        setFormaPagamento('CARTAO');
        setCartaoId(initialData.cartaoId);
        prefsAplicadas.current = true;
        return;
      }
    }
    if (!prefsAplicadas.current) {
      getLancamentoPrefs().then(prefs => {
        if (!prefs) return;
        setFormaPagamento(prefs.formaPagamento);
        if (prefs.carteiraId != null) setCarteiraId(prefs.carteiraId);
        if (prefs.cartaoId != null) setCartaoId(prefs.cartaoId);
        prefsAplicadas.current = true;
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible]);

  const { data: categorias = [] } = useQuery({
    queryKey: ['categorias'],
    queryFn: () => categoriaService.listar(),
  });

  const { data: carteirasPage } = useQuery({
    queryKey: ['contas-financeiras-caixa'],
    queryFn: () => contaFinanceiraService.listarParaCaixa(),
  });
  const carteiras = carteirasPage ?? [];

  const { data: contasPage } = useQuery({
    queryKey: ['cartoes'],
    queryFn: () => cartaoService.listar(),
  });
  const cartoes = contasPage?.content ?? [];

  // Sem carteira a transação não movimenta saldo — pré-seleciona a primeira
  useEffect(() => {
    if (carteiraId == null && carteiras.length > 0) setCarteiraId(carteiras[0].id);
  }, [carteiras, carteiraId]);

  useEffect(() => {
    if (cartaoId == null && cartoes.length > 0) setCartaoId(cartoes[0].id);
  }, [cartoes, cartaoId]);

  useEffect(() => {
    if (tipo === 'ENTRADA') {
      setFormaPagamento('CARTEIRA');
      setParcelado(false);
      setTotalParcelas('');
    }
  }, [tipo]);

  // Sugestão determinística (PR-F3-02) após descrição estável; nunca
  // sobrescreve categoria já escolhida
  useEffect(() => {
    if (!visible) return;
    const estavel = descricao.trim();
    if (estavel.length < 3 || categoriaEscolhidaManualmente.current) {
      setSugestao(null);
      return;
    }
    const timer = setTimeout(() => {
      transacaoService.sugerirCategoria(estavel, tipo)
        .then(s => {
          if (categoriaEscolhidaManualmente.current) return;
          setSugestao(s.categoria ? s : null);
          if (s.categoria && categoriaId == null) setCategoriaId(s.categoria.id);
        })
        .catch(() => setSugestao(null)); // sugestão é opcional: falha não bloqueia o fluxo
    }, DEBOUNCE_SUGESTAO_MS);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [descricao, tipo, visible]);

  const selecionarCategoria = (id: number) => {
    categoriaEscolhidaManualmente.current = true;
    setCategoriaId(id);
  };

  // Um toque cria o pacote inicial de categorias (PR-F3-10)
  const criarPacoteInicial = async () => {
    setCriandoPacote(true);
    setSetupError(null);
    try {
      for (const categoria of CATEGORIAS_INICIAIS) {
        await categoriaService.criar(categoria);
      }
      await queryClient.invalidateQueries({ queryKey: ['categorias'] });
    } catch (err: any) {
      setSetupError(err?.userMessage ?? 'Não foi possível criar as categorias. Tente novamente.');
    } finally {
      setCriandoPacote(false);
    }
  };

  const criarCategoriaUnica = async () => {
    const nome = novaCategoriaNome.trim();
    if (nome.length < 2) {
      setSetupError('Informe o nome da categoria.');
      return;
    }
    setCriandoCategoria(true);
    setSetupError(null);
    try {
      const criada = await categoriaService.criar({ nome, cor: CATEGORY_COLORS[8], icone: '📌' });
      await queryClient.invalidateQueries({ queryKey: ['categorias'] });
      selecionarCategoria(criada.id);
      setNovaCategoriaNome('');
    } catch (err: any) {
      setSetupError(err?.userMessage ?? 'Não foi possível criar a categoria. Tente novamente.');
    } finally {
      setCriandoCategoria(false);
    }
  };

  // CTA de cartão no pagamento com cartão (PR-F3-10)
  const criarCartaoRapido = async () => {
    if (novoCartao.nome.trim().length < 2) { setSetupError('Informe o nome do cartão.'); return; }
    const limite = parseCurrencyBR(novoCartao.limite || '0');
    if (!Number.isFinite(limite) || limite <= 0) { setSetupError('Limite do cartão deve ser maior que zero.'); return; }
    if (!isValidDayOfMonth(novoCartao.fechamento) || !isValidDayOfMonth(novoCartao.vencimento)) {
      setSetupError('Fechamento e vencimento devem estar entre 1 e 31.');
      return;
    }
    setCriandoCartao(true);
    setSetupError(null);
    try {
      const criado = await cartaoService.criar({
        nome: novoCartao.nome.trim(),
        limiteTotal: limite,
        diaFechamento: Number(novoCartao.fechamento),
        diaVencimento: Number(novoCartao.vencimento),
      });
      await queryClient.invalidateQueries({ queryKey: ['cartoes'] });
      setCartaoId(criado.id);
      setCriarCartaoAberto(false);
    } catch (err: any) {
      setSetupError(err?.userMessage ?? 'Não foi possível criar o cartão. Tente novamente.');
    } finally {
      setCriandoCartao(false);
    }
  };

  const resetForm = () => {
    setDescricao(''); setValor(''); setData(''); setTipo(initialTipo); setFormaPagamento('CARTEIRA'); setCategoriaId(null); setCartaoId(null); setParcelado(false); setTotalParcelas(''); setObservacoes('');
    setDescricaoError(null); setValorError(null); setDataError(null); setCategoriaError(null); setPagamentoError(null); setErroForm(null);
    setMaisDetalhes(false); setSugestao(null);
    setCriarCartaoAberto(false); setNovaCategoriaNome(''); setSetupError(null);
    setNovoCartao({ nome: 'Cartão Principal', limite: '', fechamento: '5', vencimento: '12' });
    categoriaEscolhidaManualmente.current = false;
    prefsAplicadas.current = false;
  };

  const handleSalvar = async () => {
    setDescricaoError(null); setValorError(null); setDataError(null); setCategoriaError(null); setPagamentoError(null); setErroForm(null);
    let hasError = false;
    if (!descricao.trim() || descricao.trim().length < 3) { setDescricaoError('Descrição deve ter entre 3 e 255 caracteres.'); hasError = true; }
    const valorNum = parseCurrencyBR(valor);
    if (!valor || isNaN(valorNum) || valorNum <= 0) { setValorError('Valor deve ser positivo.'); hasError = true; }
    if (!isValidDateBR(data)) { setDataError('Data inválida. Use o formato DD/MM/AAAA.'); hasError = true; }
    if (!categoriaId) { setCategoriaError('Selecione uma categoria.'); hasError = true; }
    if (tipo === 'SAIDA' && formaPagamento === 'CARTAO' && !cartaoId) { setPagamentoError('Selecione um cartão.'); hasError = true; }
    if (tipo === 'SAIDA' && formaPagamento === 'CARTEIRA' && !carteiraId) { setPagamentoError('Selecione uma conta.'); hasError = true; }
    const parcelasNum = parseInt(totalParcelas, 10);
    if (formaPagamento === 'CARTAO' && parcelado && (isNaN(parcelasNum) || parcelasNum < 2 || parcelasNum > 48)) {
      setPagamentoError('Informe entre 2 e 48 parcelas.');
      hasError = true;
    }
    if (hasError) return;

    setSalvando(true);
    try {
      const request: TransacaoRequest = {
        descricao: descricao.trim(),
        valor: valorNum,
        data: parseDateBR(data),
        tipo,
        categoriaId: categoriaId!,
        observacoes: observacoes.trim() || undefined,
      };
      if (tipo === 'SAIDA' && formaPagamento === 'CARTAO') {
        request.cartaoId = cartaoId ?? undefined;
        request.parcelado = parcelado;
        request.totalParcelas = parcelado ? parcelasNum : undefined;
      } else {
        request.carteiraId = carteiraId ?? undefined;
      }
      await transacaoService.criar(request);
      // Última conta/cartão ficam somente no dispositivo (PR-F3-05)
      setLancamentoPrefs(tipo === 'SAIDA' && formaPagamento === 'CARTAO'
        ? { formaPagamento: 'CARTAO', cartaoId: cartaoId ?? undefined }
        : { formaPagamento: 'CARTEIRA', carteiraId: carteiraId ?? undefined }
      ).catch(() => {}); // preferência é conveniência: falha não bloqueia o salvamento
      queryClient.invalidateQueries({ queryKey: ['metricas'] });
      queryClient.invalidateQueries({ queryKey: ['compromissos'] });
      queryClient.invalidateQueries({ queryKey: ['transacoes'] });
      queryClient.invalidateQueries({ queryKey: ['relatorio'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-evolucao'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-comparacao-mensal'] });
      queryClient.invalidateQueries({ queryKey: ['transacoes-recentes'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-resumo'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-projecao'] });
      queryClient.invalidateQueries({ queryKey: ['carteiras'] });
      queryClient.invalidateQueries({ queryKey: ['contas'] });
      queryClient.invalidateQueries({ queryKey: ['contas-fatura'] });
      queryClient.invalidateQueries({ queryKey: ['fatura'] });
      resetForm();
      onClose();
      onSaved?.();
    } catch (err: any) {
      setErroForm(err?.userMessage ?? 'Erro ao salvar. Tente novamente.');
    } finally {
      setSalvando(false);
    }
  };

  const tituloModal = initialData ? 'Repetir lançamento' : 'Nova Transação';

  return (
    <Modal visible={visible} animationType="slide" presentationStyle="pageSheet" onRequestClose={onClose}>
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
          <TouchableOpacity onPress={() => { resetForm(); onClose(); }} accessibilityRole="button">
            <Text style={{ color: colors.brandFg, fontSize: 15 }}>Cancelar</Text>
          </TouchableOpacity>
          <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700' }}>{tituloModal}</Text>
          <TouchableOpacity onPress={handleSalvar} disabled={salvando} accessibilityRole="button">
            {salvando ? <ActivityIndicator color={colors.brand} size="small" /> : <Text style={{ color: colors.brandFg, fontSize: 15, fontWeight: '700' }}>Salvar</Text>}
          </TouchableOpacity>
        </View>
        <ScrollView contentContainerStyle={{ padding: 16 }} keyboardShouldPersistTaps="handled">
          {initialData && (
            <Text style={{ color: colors.textSecondary, fontSize: 12, marginBottom: 12 }}>
              Dados pré-preenchidos do lançamento anterior. Revise e confirme em Salvar.
            </Text>
          )}
          <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Tipo</Text>
          <View style={{ flexDirection: 'row', gap: 8, marginBottom: 16 }}>
            {(['ENTRADA', 'SAIDA'] as TipoTransacao[]).map(t => (
              <Chip key={t} label={t === 'ENTRADA' ? 'Entrada' : 'Saída'} selected={tipo === t} onPress={() => setTipo(t)} />
            ))}
          </View>

          <Field testID="transaction-value" label="Valor" value={valor} onChangeText={(t) => setValor(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={valorError} autoFocus />
          <Field testID="transaction-description" label="Descrição" value={descricao} onChangeText={setDescricao} placeholder="Ex: Mercado" error={descricaoError} />
          <Field testID="transaction-date" label="Data" value={data} onChangeText={(t) => setData(maskDateInput(t))} placeholder="DD/MM/AAAA" keyboardType="number-pad" error={dataError} />

          <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Categoria</Text>
          {sugestao?.categoria && categoriaId === sugestao.categoria.id && !categoriaEscolhidaManualmente.current && (
            <Text testID="category-suggestion" style={{ color: colors.brandFg, fontSize: 12, marginBottom: 6 }}>
              Sugerida pelo seu histórico — toque em outra para trocar.
            </Text>
          )}
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 8 }}>
            {categorias.map(cat => (
              <Chip
                key={cat.id}
                label={`${cat.icone ? cat.icone + ' ' : ''}${cat.nome}`}
                selected={categoriaId === cat.id}
                onPress={() => selecionarCategoria(cat.id)}
              />
            ))}
          </View>
          {categorias.length === 0 && (
            <View style={{ marginBottom: 12, gap: 8 }}>
              <Text style={{ color: colors.textSecondary, fontSize: 12 }}>
                Você ainda não tem categorias. Crie antes de salvar:
              </Text>
              <TouchableOpacity
                testID="create-category-pack"
                onPress={criarPacoteInicial}
                disabled={criandoPacote}
                accessibilityRole="button"
                accessibilityLabel="Criar pacote inicial de categorias"
                style={{ minHeight: 44, borderRadius: 12, backgroundColor: colors.brandBg, alignItems: 'center', justifyContent: 'center', opacity: criandoPacote ? 0.6 : 1 }}
              >
                {criandoPacote
                  ? <ActivityIndicator color={colors.brand} size="small" />
                  : <Text style={{ color: colors.brandFg, fontWeight: '700' }}>Criar pacote inicial (9 categorias)</Text>}
              </TouchableOpacity>
              <View style={{ flexDirection: 'row', gap: 8, alignItems: 'flex-start' }}>
                <View style={{ flex: 1 }}>
                  <Field
                    testID="quick-category-name"
                    label="Ou crie uma"
                    value={novaCategoriaNome}
                    onChangeText={setNovaCategoriaNome}
                    placeholder="Ex: Mercado"
                  />
                </View>
                <TouchableOpacity
                  onPress={criarCategoriaUnica}
                  disabled={criandoCategoria}
                  accessibilityRole="button"
                  accessibilityLabel="Criar categoria"
                  style={{ minHeight: 44, marginTop: 22, paddingHorizontal: 14, borderRadius: 12, borderWidth: 1, borderColor: colors.border, alignItems: 'center', justifyContent: 'center', opacity: criandoCategoria ? 0.6 : 1 }}
                >
                  {criandoCategoria
                    ? <ActivityIndicator color={colors.brand} size="small" />
                    : <Text style={{ color: colors.brandFg, fontWeight: '600' }}>Criar</Text>}
                </TouchableOpacity>
              </View>
            </View>
          )}
          {categoriaError && <Text style={{ color: colors.danger, fontSize: 12, marginBottom: 8 }}>{categoriaError}</Text>}

          {tipo === 'SAIDA' && (
            <>
              <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Pagar com</Text>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
                <Chip label="Conta" selected={formaPagamento === 'CARTEIRA'} onPress={() => { setFormaPagamento('CARTEIRA'); setParcelado(false); }} />
                <Chip label="Cartão" selected={formaPagamento === 'CARTAO'} onPress={() => setFormaPagamento('CARTAO')} />
              </View>
            </>
          )}

          {formaPagamento === 'CARTEIRA' && carteiras.length > 0 && (
            <>
              <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Conta</Text>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 8 }}>
                {carteiras.map(c => (
                  <Chip key={c.id} label={c.nome} selected={carteiraId === c.id} onPress={() => setCarteiraId(c.id)} />
                ))}
              </View>
            </>
          )}

          {tipo === 'SAIDA' && formaPagamento === 'CARTAO' && (
            <>
              <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Cartão</Text>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 8 }}>
                {cartoes.map(c => (
                  <Chip key={c.id} label={c.nome} selected={cartaoId === c.id} onPress={() => setCartaoId(c.id)} />
                ))}
              </View>
              {cartoes.length === 0 && !criarCartaoAberto && (
                <TouchableOpacity
                  testID="create-card-cta"
                  onPress={() => setCriarCartaoAberto(true)}
                  accessibilityRole="button"
                  accessibilityLabel="Criar cartão agora"
                  style={{ minHeight: 44, borderRadius: 12, backgroundColor: colors.brandBg, alignItems: 'center', justifyContent: 'center', marginBottom: 8 }}
                >
                  <Text style={{ color: colors.brandFg, fontWeight: '700' }}>Criar cartão agora</Text>
                </TouchableOpacity>
              )}
              {cartoes.length === 0 && criarCartaoAberto && (
                <View style={{ marginBottom: 8 }}>
                  <Field testID="quick-card-name" label="Nome do cartão" value={novoCartao.nome} onChangeText={(t) => setNovoCartao(c => ({ ...c, nome: t }))} placeholder="Ex: Cartão Principal" />
                  <Field testID="quick-card-limit" label="Limite (R$)" value={novoCartao.limite} onChangeText={(t) => setNovoCartao(c => ({ ...c, limite: maskCurrencyInput(t) }))} keyboardType="number-pad" placeholder="0,00" />
                  <View style={{ flexDirection: 'row', gap: 8 }}>
                    <View style={{ flex: 1 }}>
                      <Field label="Fechamento" value={novoCartao.fechamento} onChangeText={(t) => setNovoCartao(c => ({ ...c, fechamento: t.replace(/\D/g, '').slice(0, 2) }))} keyboardType="number-pad" placeholder="5" />
                    </View>
                    <View style={{ flex: 1 }}>
                      <Field label="Vencimento" value={novoCartao.vencimento} onChangeText={(t) => setNovoCartao(c => ({ ...c, vencimento: t.replace(/\D/g, '').slice(0, 2) }))} keyboardType="number-pad" placeholder="12" />
                    </View>
                  </View>
                  <TouchableOpacity
                    onPress={criarCartaoRapido}
                    disabled={criandoCartao}
                    accessibilityRole="button"
                    accessibilityLabel="Salvar cartão"
                    style={{ minHeight: 44, borderRadius: 12, backgroundColor: colors.brandBg, alignItems: 'center', justifyContent: 'center', marginTop: 4, opacity: criandoCartao ? 0.6 : 1 }}
                  >
                    {criandoCartao
                      ? <ActivityIndicator color={colors.brand} size="small" />
                      : <Text style={{ color: colors.brandFg, fontWeight: '700' }}>Salvar cartão</Text>}
                  </TouchableOpacity>
                </View>
              )}
            </>
          )}
          {pagamentoError && <Text style={{ color: colors.danger, fontSize: 12, marginBottom: 8 }}>{pagamentoError}</Text>}

          {/* Fora do fluxo principal (PR-F3-05): observações e parcelamento */}
          <TouchableOpacity
            testID="more-details-toggle"
            onPress={() => setMaisDetalhes(v => !v)}
            accessibilityRole="button"
            style={{ paddingVertical: 10, marginBottom: 4 }}
          >
            <Text style={{ color: colors.brandFg, fontSize: 14, fontWeight: '600' }}>
              {maisDetalhes ? 'Menos detalhes ▲' : 'Mais detalhes ▼'}
            </Text>
          </TouchableOpacity>

          {maisDetalhes && (
            <>
              {tipo === 'SAIDA' && formaPagamento === 'CARTAO' && (
                <>
                  <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Parcelamento</Text>
                  <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 8 }}>
                    <Chip label="À vista" selected={!parcelado} onPress={() => { setParcelado(false); setTotalParcelas(''); }} />
                    <Chip label="Parcelado" selected={parcelado} onPress={() => setParcelado(true)} />
                  </View>
                  {parcelado && (
                    <Field label="Parcelas" value={totalParcelas} onChangeText={(t) => setTotalParcelas(t.replace(/\D/g, '').slice(0, 2))} keyboardType="number-pad" placeholder="Ex: 6" />
                  )}
                </>
              )}
              <Field label="Observações" value={observacoes} onChangeText={setObservacoes} multiline style={{ height: 100, textAlignVertical: 'top' }} />
            </>
          )}

          {setupError && <Text style={{ color: colors.danger, fontSize: 12, marginBottom: 8 }}>{setupError}</Text>}
          {erroForm && <Text style={{ color: colors.danger, marginBottom: 8 }}>{erroForm}</Text>}
        </ScrollView>
      </View>
    </Modal>
  );
}
