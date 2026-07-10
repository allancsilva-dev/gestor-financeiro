import React, { useEffect, useState } from 'react';
import { View, Text, Modal, TouchableOpacity, ActivityIndicator, ScrollView } from 'react-native';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { transacaoService } from '../services/transacaoService';
import { categoriaService } from '../services/categoriaService';
import { carteiraService } from '../services/carteiraService';
import { contaService } from '../services/contaService';
import { useTheme } from '../theme';
import { parseDateBR, isValidDateBR, parseCurrencyBR, maskCurrencyInput, maskDateInput } from '../utils/format';
import { TransacaoRequest, TipoTransacao } from '../types';
import Chip from './ui/Chip';
import Field from './ui/Field';

interface NovaTransacaoModalProps {
  visible: boolean;
  onClose: () => void;
  onSaved?: () => void;
  initialTipo?: TipoTransacao;
}

// Sheet "Nova Transação" — aberto pelo + central da tab bar e pelos atalhos da home
export default function NovaTransacaoModal({ visible, onClose, onSaved, initialTipo = 'SAIDA' }: NovaTransacaoModalProps) {
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
  const [contaId, setContaId] = useState<number | null>(null);
  const [parcelado, setParcelado] = useState(false);
  const [totalParcelas, setTotalParcelas] = useState('');
  const [observacoes, setObservacoes] = useState('');

  useEffect(() => {
    if (visible) setTipo(initialTipo);
  }, [visible, initialTipo]);

  const { data: categorias = [] } = useQuery({
    queryKey: ['categorias'],
    queryFn: () => categoriaService.listar(),
  });

  const { data: carteirasPage } = useQuery({
    queryKey: ['carteiras'],
    queryFn: () => carteiraService.listar(),
  });
  const carteiras = carteirasPage?.content ?? [];

  const { data: contasPage } = useQuery({
    queryKey: ['contas'],
    queryFn: () => contaService.listar(),
  });
  const cartoes = (contasPage?.content ?? []).filter(c => c.tipo === 'CREDITO');

  // Sem carteira a transação não movimenta saldo — pré-seleciona a primeira
  useEffect(() => {
    if (carteiraId == null && carteiras.length > 0) setCarteiraId(carteiras[0].id);
  }, [carteiras, carteiraId]);

  useEffect(() => {
    if (contaId == null && cartoes.length > 0) setContaId(cartoes[0].id);
  }, [cartoes, contaId]);

  useEffect(() => {
    if (tipo === 'ENTRADA') {
      setFormaPagamento('CARTEIRA');
      setParcelado(false);
      setTotalParcelas('');
    }
  }, [tipo]);

  const resetForm = () => {
    setDescricao(''); setValor(''); setData(''); setTipo(initialTipo); setFormaPagamento('CARTEIRA'); setCategoriaId(null); setContaId(null); setParcelado(false); setTotalParcelas(''); setObservacoes('');
    setDescricaoError(null); setValorError(null); setDataError(null); setCategoriaError(null); setPagamentoError(null); setErroForm(null);
  };

  const handleSalvar = async () => {
    setDescricaoError(null); setValorError(null); setDataError(null); setCategoriaError(null); setPagamentoError(null); setErroForm(null);
    let hasError = false;
    if (!descricao.trim() || descricao.trim().length < 3) { setDescricaoError('Descrição deve ter entre 3 e 255 caracteres.'); hasError = true; }
    const valorNum = parseCurrencyBR(valor);
    if (!valor || isNaN(valorNum) || valorNum <= 0) { setValorError('Valor deve ser positivo.'); hasError = true; }
    if (!isValidDateBR(data)) { setDataError('Data inválida. Use o formato DD/MM/AAAA.'); hasError = true; }
    if (!categoriaId) { setCategoriaError('Selecione uma categoria.'); hasError = true; }
    if (tipo === 'SAIDA' && formaPagamento === 'CARTAO' && !contaId) { setPagamentoError('Selecione um cartão.'); hasError = true; }
    if (tipo === 'SAIDA' && formaPagamento === 'CARTEIRA' && !carteiraId) { setPagamentoError('Selecione uma carteira.'); hasError = true; }
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
        request.contaId = contaId ?? undefined;
        request.parcelado = parcelado;
        request.totalParcelas = parcelado ? parcelasNum : undefined;
      } else {
        request.carteiraId = carteiraId ?? undefined;
      }
      await transacaoService.criar(request);
      queryClient.invalidateQueries({ queryKey: ['transacoes'] });
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

  return (
    <Modal visible={visible} animationType="slide" presentationStyle="pageSheet" onRequestClose={onClose}>
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
          <TouchableOpacity onPress={() => { resetForm(); onClose(); }} accessibilityRole="button">
            <Text style={{ color: colors.brandFg, fontSize: 15 }}>Cancelar</Text>
          </TouchableOpacity>
          <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700' }}>Nova Transação</Text>
          <TouchableOpacity onPress={handleSalvar} disabled={salvando} accessibilityRole="button">
            {salvando ? <ActivityIndicator color={colors.brand} size="small" /> : <Text style={{ color: colors.brandFg, fontSize: 15, fontWeight: '700' }}>Salvar</Text>}
          </TouchableOpacity>
        </View>
        <ScrollView contentContainerStyle={{ padding: 16 }} keyboardShouldPersistTaps="handled">
          <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Tipo</Text>
          <View style={{ flexDirection: 'row', gap: 8, marginBottom: 16 }}>
            {(['ENTRADA', 'SAIDA'] as TipoTransacao[]).map(t => (
              <Chip key={t} label={t === 'ENTRADA' ? 'Entrada' : 'Saída'} selected={tipo === t} onPress={() => setTipo(t)} />
            ))}
          </View>

          <Field label="Valor" value={valor} onChangeText={(t) => setValor(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={valorError} autoFocus />
          <Field label="Descrição" value={descricao} onChangeText={setDescricao} placeholder="Ex: Mercado" error={descricaoError} />
          <Field label="Data" value={data} onChangeText={(t) => setData(maskDateInput(t))} placeholder="DD/MM/AAAA" keyboardType="number-pad" error={dataError} />

          <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Categoria</Text>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 8 }}>
            {categorias.map(cat => (
              <Chip
                key={cat.id}
                label={`${cat.icone ? cat.icone + ' ' : ''}${cat.nome}`}
                selected={categoriaId === cat.id}
                onPress={() => setCategoriaId(cat.id)}
              />
            ))}
          </View>
          {categoriaError && <Text style={{ color: colors.danger, fontSize: 12, marginBottom: 8 }}>{categoriaError}</Text>}

          {tipo === 'SAIDA' && (
            <>
              <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Pagar com</Text>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
                <Chip label="Carteira" selected={formaPagamento === 'CARTEIRA'} onPress={() => { setFormaPagamento('CARTEIRA'); setParcelado(false); }} />
                <Chip label="Cartão" selected={formaPagamento === 'CARTAO'} onPress={() => setFormaPagamento('CARTAO')} />
              </View>
            </>
          )}

          {formaPagamento === 'CARTEIRA' && carteiras.length > 0 && (
            <>
              <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Carteira</Text>
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
                  <Chip key={c.id} label={c.nome} selected={contaId === c.id} onPress={() => setContaId(c.id)} />
                ))}
              </View>
              {cartoes.length === 0 && <Text style={{ color: colors.textSecondary, fontSize: 12, marginBottom: 8 }}>Cadastre um cartão em Faturas.</Text>}
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 8 }}>
                <Chip label="À vista" selected={!parcelado} onPress={() => { setParcelado(false); setTotalParcelas(''); }} />
                <Chip label="Parcelado" selected={parcelado} onPress={() => setParcelado(true)} />
              </View>
              {parcelado && (
                <Field label="Parcelas" value={totalParcelas} onChangeText={(t) => setTotalParcelas(t.replace(/\D/g, '').slice(0, 2))} keyboardType="number-pad" placeholder="Ex: 6" />
              )}
            </>
          )}
          {pagamentoError && <Text style={{ color: colors.danger, fontSize: 12, marginBottom: 8 }}>{pagamentoError}</Text>}

          <Field label="Observações" value={observacoes} onChangeText={setObservacoes} multiline style={{ height: 100, textAlignVertical: 'top' }} />

          {erroForm && <Text style={{ color: colors.danger, marginBottom: 8 }}>{erroForm}</Text>}
        </ScrollView>
      </View>
    </Modal>
  );
}
