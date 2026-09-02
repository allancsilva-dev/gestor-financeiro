import React, { useEffect, useRef, useState } from 'react';
import { View, Text, Modal, TouchableOpacity, ActivityIndicator, ScrollView, TextInput, Switch, Alert } from 'react-native';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { transacaoService } from '../services/transacaoService';
import { contaFixaService } from '../services/contaFixaService';
import assistantService, { assistantIdempotencyKey } from '../services/assistantService';
import { categoriaService } from '../services/categoriaService';
import contaFinanceiraService from '../services/contaFinanceiraService';
import cartaoService from '../services/cartaoService';
import { useModalTopInset, useTheme } from '../theme';
import { parseDateBR, isValidDateBR, parseCurrencyBR, maskCurrencyInput, maskDateInput, todayBR, formatDateOnlyBR } from '../utils/format';
import { TransacaoRequest, TipoTransacao, SugestaoCategoria, Alerta, FrequenciaRecorrencia } from '../types';
import { FREQUENCIAS, isSubMensal, nomeFrequencia, proximaCobranca, rotuloProximaCobranca } from '../domain/recorrencia';
import { vencimentoDaCompraNoCartao } from '../domain/fatura';
import { getLancamentoPrefs, setLancamentoPrefs } from '../store/lancamentoPrefs';
import { CATEGORIAS_INICIAIS } from '../domain/categoriasIniciais';
import { CATEGORY_COLORS } from '../utils/format';
import { isValidDayOfMonth } from '../utils/validate';
import Chip from './ui/Chip';
import Field from './ui/Field';
import { useInvalidarAposTransacao } from '../hooks/useInvalidarAposTransacao';

// Pré-preenchimento do "Repetir lançamento" (PR-F3-05): exige confirmação
// explícita no Salvar — nunca grava sozinho.
export interface LancamentoInicial {
  descricao: string;
  valor: number;
  tipo: TipoTransacao;
  categoriaId?: number;
  carteiraId?: number;
  cartaoId?: number;
  parcelas?: number;
  data?: string;
  mode?: 'ASSISTANT_DRAFT';
  draftId?: number;
  draftVersion?: number;
}

interface NovaTransacaoModalProps {
  visible: boolean;
  onClose: () => void;
  onSaved?: () => void;
  initialTipo?: TipoTransacao;
  initialData?: LancamentoInicial | null;
  /**
   * Pré-seleciona o cartão sem a semântica de "repetir lançamento" do
   * initialData: o formulário abre vazio, só já apontando para o cartão.
   */
  cartaoIdInicial?: number | null;
}

const DEBOUNCE_SUGESTAO_MS = 600;

// Sheet "Nova Transação" — aberto pelo + central da tab bar, pelos atalhos da
// home e pelo "Repetir lançamento". Fluxo principal: valor → descrição →
// confirmar; data default hoje; observações/parcelamento em "Mais detalhes".
export default function NovaTransacaoModal({ visible, onClose, onSaved, initialTipo = 'SAIDA', initialData = null, cartaoIdInicial = null }: NovaTransacaoModalProps) {
  const colors = useTheme();
  const topo = useModalTopInset();
  const queryClient = useQueryClient();
  const invalidarCacheDeTransacao = useInvalidarAposTransacao();

  const [salvando, setSalvando] = useState(false);

  // Foco encadeado pelo teclado: tocar no campo seguinte com o teclado aberto
  // erra o alvo, porque o sheet sobe junto.
  const refDescricao = useRef<TextInput>(null);
  const refData = useRef<TextInput>(null);
  const [erroForm, setErroForm] = useState<string | null>(null);
  const [frequencia, setFrequencia] = useState<FrequenciaRecorrencia>('MENSAL');
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
  // Assinatura (Netflix, aluguel): vira recorrência, não lançamento único
  const [repeteTodoMes, setRepeteTodoMes] = useState(false);
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
  const assistantSave = useRef<{
    draftId: number;
    fingerprint: string;
    version: number;
    confirmKey: string;
  } | null>(null);

  useEffect(() => {
    assistantSave.current = null;
  }, [visible, initialData?.draftId, initialData?.draftVersion]);

  // Abertura: data default hoje, pré-preenchimento do repetir e última
  // conta/cartão usados no dispositivo (nunca sobrescreve o repetir)
  useEffect(() => {
    if (!visible) return;
    setTipo(initialData?.tipo ?? initialTipo);
    setData(initialData?.data ? formatDateOnlyBR(initialData.data) : todayBR());
    if (initialData) {
      setDescricao(initialData.descricao);
      setValor(maskCurrencyInput(String(Math.round(initialData.valor * 100))));
      if (initialData.categoriaId != null) {
        setCategoriaId(initialData.categoriaId);
        categoriaEscolhidaManualmente.current = true;
      }
      if (initialData.carteiraId != null) {
        setFormaPagamento('CARTEIRA');
        setCarteiraId(initialData.carteiraId);
        prefsAplicadas.current = true;
      }
      if (initialData.cartaoId != null) {
        setFormaPagamento('CARTAO');
        setCartaoId(initialData.cartaoId);
        // O "3x" que a pessoa falou precisa aparecer na revisão, não sumir no caminho.
        if (initialData.parcelas != null && initialData.parcelas >= 2) {
          setParcelado(true);
          setTotalParcelas(String(initialData.parcelas));
        }
        prefsAplicadas.current = true;
        return;
      }
    }
    if (cartaoIdInicial != null) {
      setFormaPagamento('CARTAO');
      setCartaoId(cartaoIdInicial);
      prefsAplicadas.current = true;
      return;
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
    // deps de proposito so [visible]: o efeito restaura as prefs uma vez por
    // abertura do sheet; incluir os setters reaplicaria a pref por cima da
    // escolha do usuario.
  }, [visible, cartaoIdInicial]);

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

  // Sem carteira a transação não movimenta saldo — pré-seleciona a conta principal do titular.
  // Antes caía em `carteiras[0]`, que é ordem de listagem, não escolha de ninguém: quem tinha
  // várias contas via a errada pré-marcada toda vez.
  useEffect(() => {
    if (carteiraId != null || carteiras.length === 0) return;
    setCarteiraId((carteiras.find(c => c.principal) ?? carteiras[0]).id);
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
    // deps de proposito sem categoriaId: a sugestao so deve reagir ao que o
    // usuario digita, nunca a categoria que ela mesma acabou de definir.
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
    setDescricao(''); setValor(''); setData(''); setTipo(initialTipo); setFormaPagamento('CARTEIRA'); setCategoriaId(null); setCartaoId(null); setParcelado(false); setTotalParcelas(''); setObservacoes(''); setRepeteTodoMes(false);
    setDescricaoError(null); setValorError(null); setDataError(null); setCategoriaError(null); setPagamentoError(null); setErroForm(null);
    setMaisDetalhes(false); setSugestao(null);
    setCriarCartaoAberto(false); setNovaCategoriaNome(''); setSetupError(null);
    setNovoCartao({ nome: 'Cartão Principal', limite: '', fechamento: '5', vencimento: '12' });
    categoriaEscolhidaManualmente.current = false;
    prefsAplicadas.current = false;
    assistantSave.current = null;
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
    if (formaPagamento === 'CARTAO' && parcelado && !repeteTodoMes && (isNaN(parcelasNum) || parcelasNum < 2 || parcelasNum > 48)) {
      setPagamentoError('Informe entre 2 e 48 parcelas.');
      hasError = true;
    }
    if (hasError) return;

    setSalvando(true);
    let alertas: Alerta[] = [];
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
        request.parcelado = parcelado && !repeteTodoMes;
        request.totalParcelas = request.parcelado ? parcelasNum : undefined;
      } else {
        request.carteiraId = carteiraId ?? undefined;
      }
      if (initialData?.mode === 'ASSISTANT_DRAFT' && initialData.draftId != null && initialData.draftVersion != null) {
        if (!request.carteiraId && !request.cartaoId) throw new Error('Conta financeira ou cartão obrigatório');
        const patch = {
          version: assistantSave.current?.version ?? initialData.draftVersion,
          tipo: request.tipo,
          valor: request.valor,
          descricao: request.descricao,
          data: request.data,
          carteiraId: request.carteiraId,
          categoriaId: request.categoriaId,
          cartaoId: request.cartaoId,
          parcelas: request.parcelado ? request.totalParcelas : undefined,
        };
        const fingerprint = JSON.stringify({ ...patch, version: undefined });
        let pending = assistantSave.current;
        if (!pending || pending.draftId !== initialData.draftId || pending.fingerprint !== fingerprint) {
          const updated = await assistantService.patchDraft(
            initialData.draftId,
            patch,
            assistantIdempotencyKey(`draft:${initialData.draftId}`),
          );
          pending = {
            draftId: initialData.draftId,
            fingerprint,
            version: updated.version,
            confirmKey: assistantIdempotencyKey(`confirm:${initialData.draftId}`),
          };
          assistantSave.current = pending;
        }
        await assistantService.confirmDraft(initialData.draftId, pending.version, pending.confirmKey);
      } else if (repeteTodoMes) {
        // O que se repete todo mês é compromisso, não lançamento avulso: vira
        // recorrência e o backend passa a lançar sozinho, mês a mês.
        const recorrencia = await contaFixaService.criar({
          descricao: request.descricao,
          valor: request.valor,
          diaVencimento: new Date(request.data + 'T00:00:00').getDate(),
          categoriaId: request.categoriaId,
          tipo: request.tipo,
          recorrente: true,
          execucaoAutomatica: true,
          frequencia,
          ...(isSubMensal(frequencia) ? { dataAncora: request.data } : {}),
          observacoes: request.observacoes,
          ...(request.cartaoId ? { cartaoId: request.cartaoId } : { carteiraId: request.carteiraId }),
        });
        alertas = recorrencia.alertas ?? [];
      } else {
        const criada = await transacaoService.criar(request);
        alertas = criada.alertas ?? [];
      }
      // Última conta/cartão ficam somente no dispositivo (PR-F3-05)
      setLancamentoPrefs(tipo === 'SAIDA' && formaPagamento === 'CARTAO'
        ? { formaPagamento: 'CARTAO', cartaoId: cartaoId ?? undefined }
        : { formaPagamento: 'CARTEIRA', carteiraId: carteiraId ?? undefined }
      ).catch(() => {}); // preferência é conveniência: falha não bloqueia o salvamento
      invalidarCacheDeTransacao();
      resetForm();
      onClose();
      onSaved?.();
      // Aviso do backend que não impede a operação (ex.: limite do cartão estourado).
      // Vem depois do onClose de propósito: o lançamento deu certo, isto é informação.
      if (alertas.length > 0) {
        Alert.alert(alertas[0].titulo, alertas[0].mensagem);
      }
    } catch (err: any) {
      setErroForm(err?.userMessage ?? 'Erro ao salvar. Tente novamente.');
    } finally {
      setSalvando(false);
    }
  };

  /**
   * O switch cria uma recorrência automática, não um lançamento — e antes disso nada
   * na tela dizia isso, nem em qual fatura a cobrança cairia.
   */
  const explicacaoDaRecorrencia = (() => {
    if (tipo === 'SAIDA' && formaPagamento === 'CARTAO') {
      const cartao = cartoes.find(c => c.id === cartaoId);
      if (cartao && isValidDateBR(data)) {
        const primeira = proximaCobranca(
          new Date(parseDateBR(data) + 'T00:00:00').getDate(),
          new Date(),
          frequencia,
          isSubMensal(frequencia) ? new Date(parseDateBR(data) + 'T00:00:00') : null,
        );
        const vencimento = vencimentoDaCompraNoCartao(primeira, cartao.diaFechamento, cartao.diaVencimento);
        const dois = (n: number) => String(n).padStart(2, '0');
        return `Entra na fatura do ${cartao.nome} que vence em `
          + `${dois(vencimento.getDate())}/${dois(vencimento.getMonth() + 1)}/${vencimento.getFullYear()}.`
          + ' O app cobra sozinho a cada vez.';
      }
      return 'Vira uma assinatura: o app lança na fatura do cartão a cada cobrança.';
    }
    return 'Vira uma recorrência: o app lança sozinho a cada cobrança.';
  })();

  const tituloModal = initialData?.mode === 'ASSISTANT_DRAFT'
    ? 'Revisar lançamento'
    : initialData ? 'Repetir lançamento' : 'Nova Transação';

  return (
    <Modal visible={visible} animationType="slide" presentationStyle="pageSheet" statusBarTranslucent onRequestClose={onClose}>
      <View style={{ flex: 1, backgroundColor: colors.bg, paddingTop: topo }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
          <TouchableOpacity onPress={() => { resetForm(); onClose(); }} accessibilityRole="button">
            <Text style={{ color: colors.brandFg, fontSize: 15 }}>Cancelar</Text>
          </TouchableOpacity>
          <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700' }}>{tituloModal}</Text>
          <TouchableOpacity
            testID="transaction-save"
            onPress={handleSalvar}
            disabled={salvando}
            accessibilityRole="button"
          >
            {salvando ? <ActivityIndicator color={colors.brand} size="small" /> : <Text style={{ color: colors.brandFg, fontSize: 15, fontWeight: '700' }}>Salvar</Text>}
          </TouchableOpacity>
        </View>
        <ScrollView contentContainerStyle={{ padding: 16 }} keyboardShouldPersistTaps="handled">
          {initialData && (
            <Text style={{ color: colors.textSecondary, fontSize: 12, marginBottom: 12 }}>
              {initialData.mode === 'ASSISTANT_DRAFT'
                ? 'Rascunho preparado pelo Assistente. Confira cada campo antes de salvar.'
                : 'Dados pré-preenchidos do lançamento anterior. Revise e confirme em Salvar.'}
            </Text>
          )}
          <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Tipo</Text>
          <View style={{ flexDirection: 'row', gap: 8, marginBottom: 16 }}>
            {(['ENTRADA', 'SAIDA'] as TipoTransacao[]).map(t => (
              <Chip key={t} label={t === 'ENTRADA' ? 'Entrada' : 'Saída'} selected={tipo === t} onPress={() => setTipo(t)} />
            ))}
          </View>

          <Field testID="transaction-value" label="Valor" value={valor} onChangeText={(t) => setValor(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={valorError} autoFocus returnKeyType="next" submitBehavior="submit" onSubmitEditing={() => refDescricao.current?.focus()} />
          <Field ref={refDescricao} testID="transaction-description" label="Descrição" value={descricao} onChangeText={setDescricao} placeholder="Ex: Mercado" error={descricaoError} returnKeyType="next" submitBehavior="submit" onSubmitEditing={() => refData.current?.focus()} />
          <Field ref={refData} testID="transaction-date" label="Data" value={data} onChangeText={(t) => setData(maskDateInput(t))} placeholder="DD/MM/AAAA" keyboardType="number-pad" error={dataError} returnKeyType="done" />

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
                accessibilityHint="Cria nove categorias de gasto de uma vez"
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
                  accessibilityHint="Cria a categoria digitada acima"
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
                {initialData?.mode !== 'ASSISTANT_DRAFT' && (
                  <Chip label="Cartão" selected={formaPagamento === 'CARTAO'} onPress={() => setFormaPagamento('CARTAO')} />
                )}
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
              {tipo === 'SAIDA' && initialData?.mode !== 'ASSISTANT_DRAFT' && (
                <View style={{ marginBottom: 12 }}>
                  <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                    <Text style={{ color: colors.textPrimary, fontSize: 14 }}>Repete todo mês</Text>
                    <Switch
                      testID="transaction-recurring"
                      value={repeteTodoMes}
                      onValueChange={(v) => { setRepeteTodoMes(v); if (v) { setParcelado(false); setTotalParcelas(''); } }}
                    />
                  </View>
                  {repeteTodoMes && (
                    <ScrollView
                      horizontal
                      showsHorizontalScrollIndicator={false}
                      contentContainerStyle={{ gap: 8 }}
                      style={{ marginTop: 8 }}
                      keyboardShouldPersistTaps="handled"
                    >
                      {FREQUENCIAS.map(f => (
                        <Chip
                          key={f}
                          label={nomeFrequencia(f)}
                          selected={frequencia === f}
                          onPress={() => setFrequencia(f)}
                        />
                      ))}
                    </ScrollView>
                  )}
                  {repeteTodoMes && isValidDateBR(data) && (
                    <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 4 }}>
                      Primeira cobrança em {rotuloProximaCobranca(
                        new Date(parseDateBR(data) + 'T00:00:00').getDate(),
                        new Date(),
                        frequencia,
                        isSubMensal(frequencia) ? new Date(parseDateBR(data) + 'T00:00:00') : null,
                      )}
                    </Text>
                  )}
                  {repeteTodoMes && (
                    <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 4 }}>
                      {explicacaoDaRecorrencia}
                    </Text>
                  )}
                </View>
              )}
              {tipo === 'SAIDA' && formaPagamento === 'CARTAO' && !repeteTodoMes && (
                <>
                  <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Parcelamento</Text>
                  <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 8 }}>
                    <Chip label="À vista" selected={!parcelado} onPress={() => { setParcelado(false); setTotalParcelas(''); }} />
                    <Chip label="Parcelado" selected={parcelado} onPress={() => setParcelado(true)} />
                  </View>
                  {parcelado && (
                    <Field testID="transaction-installments" label="Parcelas" value={totalParcelas} onChangeText={(t) => setTotalParcelas(t.replace(/\D/g, '').slice(0, 2))} keyboardType="number-pad" placeholder="Ex: 6" />
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
