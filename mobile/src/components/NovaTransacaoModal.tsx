import React, { useEffect, useState } from 'react';
import { View, Text, Modal, TouchableOpacity, ActivityIndicator, ScrollView } from 'react-native';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { transacaoService } from '../services/transacaoService';
import { categoriaService } from '../services/categoriaService';
import { carteiraService } from '../services/carteiraService';
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

  const [descricao, setDescricao] = useState('');
  const [valor, setValor] = useState('');
  const [data, setData] = useState('');
  const [tipo, setTipo] = useState<TipoTransacao>(initialTipo);
  const [categoriaId, setCategoriaId] = useState<number | null>(null);
  const [carteiraId, setCarteiraId] = useState<number | null>(null);
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

  // Sem carteira a transação não movimenta saldo — pré-seleciona a primeira
  useEffect(() => {
    if (carteiraId == null && carteiras.length > 0) setCarteiraId(carteiras[0].id);
  }, [carteiras, carteiraId]);

  const resetForm = () => {
    setDescricao(''); setValor(''); setData(''); setTipo(initialTipo); setCategoriaId(null); setObservacoes('');
    setDescricaoError(null); setValorError(null); setDataError(null); setCategoriaError(null); setErroForm(null);
  };

  const handleSalvar = async () => {
    setDescricaoError(null); setValorError(null); setDataError(null); setCategoriaError(null); setErroForm(null);
    let hasError = false;
    if (!descricao.trim() || descricao.trim().length < 3) { setDescricaoError('Descrição deve ter entre 3 e 255 caracteres.'); hasError = true; }
    const valorNum = parseCurrencyBR(valor);
    if (!valor || isNaN(valorNum) || valorNum <= 0) { setValorError('Valor deve ser positivo.'); hasError = true; }
    if (!isValidDateBR(data)) { setDataError('Data inválida. Use o formato DD/MM/AAAA.'); hasError = true; }
    if (!categoriaId) { setCategoriaError('Selecione uma categoria.'); hasError = true; }
    if (hasError) return;

    setSalvando(true);
    try {
      const request: TransacaoRequest = {
        descricao: descricao.trim(),
        valor: valorNum,
        data: parseDateBR(data),
        tipo,
        categoriaId: categoriaId!,
        carteiraId: carteiraId ?? undefined,
        observacoes: observacoes.trim() || undefined,
      };
      await transacaoService.criar(request);
      queryClient.invalidateQueries({ queryKey: ['transacoes'] });
      queryClient.invalidateQueries({ queryKey: ['transacoes-recentes'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-resumo'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-projecao'] });
      queryClient.invalidateQueries({ queryKey: ['carteiras'] });
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

          {carteiras.length > 0 && (
            <>
              <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Carteira</Text>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 8 }}>
                {carteiras.map(c => (
                  <Chip key={c.id} label={c.nome} selected={carteiraId === c.id} onPress={() => setCarteiraId(c.id)} />
                ))}
              </View>
            </>
          )}

          <Field label="Observações" value={observacoes} onChangeText={setObservacoes} multiline style={{ height: 100, textAlignVertical: 'top' }} />

          {erroForm && <Text style={{ color: colors.danger, marginBottom: 8 }}>{erroForm}</Text>}
        </ScrollView>
      </View>
    </Modal>
  );
}
