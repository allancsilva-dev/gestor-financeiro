import React, { useEffect, useState } from 'react';
import { View, Text, Modal, TouchableOpacity, ActivityIndicator, ScrollView, Alert } from 'react-native';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { transacaoService } from '../services/transacaoService';
import { categoriaService } from '../services/categoriaService';
import { useTheme } from '../theme';
import { parseDateBR, isValidDateBR, parseCurrencyBR, maskCurrencyInput, maskDateInput } from '../utils/format';
import { Transacao, TransacaoRequest } from '../types';
import Field from './ui/Field';
import Chip from './ui/Chip';

interface EditarTransacaoModalProps {
  visible: boolean;
  transacao: Transacao | null;
  onClose: () => void;
}

const isoToBR = (iso: string) => iso.slice(0, 10).split('-').reverse().join('/');

// Sheet "Editar Transação" — aberto ao tocar numa linha da lista de transações.
// Tipo e forma de pagamento ficam fixos; categoria pode mudar sem recriar lançamento.
export default function EditarTransacaoModal({ visible, transacao, onClose }: EditarTransacaoModalProps) {
  const colors = useTheme();
  const queryClient = useQueryClient();

  const [salvando, setSalvando] = useState(false);
  const [excluindo, setExcluindo] = useState(false);
  const [erroForm, setErroForm] = useState<string | null>(null);
  const [descricaoError, setDescricaoError] = useState<string | null>(null);
  const [valorError, setValorError] = useState<string | null>(null);
  const [dataError, setDataError] = useState<string | null>(null);
  const [categoriaError, setCategoriaError] = useState<string | null>(null);

  const [descricao, setDescricao] = useState('');
  const [valor, setValor] = useState('');
  const [data, setData] = useState('');
  const [observacoes, setObservacoes] = useState('');
  const [categoriaId, setCategoriaId] = useState<number | null>(null);

  const compraCartao = transacao?.tipo === 'SAIDA' && transacao?.conta?.tipo === 'CREDITO';

  useEffect(() => {
    if (visible && transacao) {
      setDescricao(transacao.descricao);
      setValor(maskCurrencyInput(Number(transacao.valorTotal ?? 0).toFixed(2)));
      setData(isoToBR(transacao.data));
      setObservacoes(transacao.observacoes ?? '');
      setCategoriaId(transacao.categoria?.id ?? null);
      setDescricaoError(null); setValorError(null); setDataError(null); setCategoriaError(null); setErroForm(null);
    }
  }, [visible, transacao]);

  const { data: categorias = [] } = useQuery({
    queryKey: ['categorias'],
    queryFn: () => categoriaService.listar(),
    enabled: visible,
  });

  const invalidarQueries = () => {
    queryClient.invalidateQueries({ queryKey: ['transacoes'] });
    queryClient.invalidateQueries({ queryKey: ['relatorio'] });
    queryClient.invalidateQueries({ queryKey: ['dashboard-evolucao'] });
    queryClient.invalidateQueries({ queryKey: ['transacoes-recentes'] });
    queryClient.invalidateQueries({ queryKey: ['dashboard-resumo'] });
    queryClient.invalidateQueries({ queryKey: ['dashboard-projecao'] });
    queryClient.invalidateQueries({ queryKey: ['carteiras'] });
    queryClient.invalidateQueries({ queryKey: ['contas'] });
    queryClient.invalidateQueries({ queryKey: ['contas-fatura'] });
    queryClient.invalidateQueries({ queryKey: ['fatura'] });
    queryClient.invalidateQueries({ queryKey: ['categorias'] });
  };

  const handleSalvar = async () => {
    if (!transacao) return;
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
        tipo: transacao.tipo,
        categoriaId: categoriaId!,
        observacoes: observacoes.trim() || undefined,
      };
      await transacaoService.atualizar(transacao.id, request);
      invalidarQueries();
      onClose();
    } catch (err: any) {
      setErroForm(err?.userMessage ?? 'Erro ao salvar. Tente novamente.');
    } finally {
      setSalvando(false);
    }
  };

  const handleExcluir = () => {
    if (!transacao) return;
    Alert.alert(
      'Excluir transação',
      compraCartao
        ? `Excluir "${transacao.descricao}"? Parcelas em faturas abertas serão removidas e o limite liberado. O que já estiver em fatura paga vira estorno na próxima fatura.`
        : `Excluir "${transacao.descricao}"? Essa ação não pode ser desfeita.`,
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Excluir',
          style: 'destructive',
          onPress: async () => {
            setExcluindo(true);
            setErroForm(null);
            try {
              await transacaoService.deletar(transacao.id);
              invalidarQueries();
              onClose();
            } catch (err: any) {
              setErroForm(err?.userMessage ?? 'Erro ao excluir. Tente novamente.');
            } finally {
              setExcluindo(false);
            }
          },
        },
      ],
    );
  };

  const formaPagamento = compraCartao
    ? `Cartão ${transacao?.conta?.nome ?? ''}${transacao?.parcelado && transacao?.totalParcelas ? ` · ${transacao.totalParcelas}x` : ' · à vista'}`
    : transacao?.tipo === 'ENTRADA' ? 'Entrada' : 'Saída';

  return (
    <Modal visible={visible} animationType="slide" presentationStyle="pageSheet" onRequestClose={onClose}>
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
          <TouchableOpacity onPress={onClose} accessibilityRole="button">
            <Text style={{ color: colors.brandFg, fontSize: 15 }}>Cancelar</Text>
          </TouchableOpacity>
          <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700' }}>Editar Transação</Text>
          <TouchableOpacity onPress={handleSalvar} disabled={salvando || excluindo} accessibilityRole="button">
            {salvando ? <ActivityIndicator color={colors.brand} size="small" /> : <Text style={{ color: colors.brandFg, fontSize: 15, fontWeight: '700' }}>Salvar</Text>}
          </TouchableOpacity>
        </View>
        <ScrollView contentContainerStyle={{ padding: 16 }} keyboardShouldPersistTaps="handled">
          <View style={{ backgroundColor: colors.card, borderColor: colors.border, borderWidth: 1, borderRadius: 12, padding: 12, marginBottom: 16 }}>
            <Text style={{ color: colors.textPrimary, fontSize: 13, fontWeight: '600' }}>
              {transacao?.tipo === 'ENTRADA' ? 'Entrada' : 'Saída'}
            </Text>
            <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 2 }}>{formaPagamento}</Text>
            {compraCartao && (
              <Text style={{ color: colors.textSecondary, fontSize: 11, marginTop: 6 }}>
                Compra no cartão: alterar valor ou data ressincroniza as faturas. Parcelas já pagas não mudam — a diferença entra como ajuste na próxima fatura.
              </Text>
            )}
          </View>

          <Field label="Valor" value={valor} onChangeText={(t) => setValor(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={valorError} />
          <Field label="Descrição" value={descricao} onChangeText={setDescricao} placeholder="Ex: Mercado" error={descricaoError} />
          <Field label="Data" value={data} onChangeText={(t) => setData(maskDateInput(t))} placeholder="DD/MM/AAAA" keyboardType="number-pad" error={dataError} />

          <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Categoria</Text>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 8 }}>
            {categorias.map(cat => (
              <Chip
                key={cat.id}
                label={`${cat.icone ? cat.icone + ' ' : ''}${cat.nome}`}
                selected={categoriaId === cat.id}
                onPress={() => { setCategoriaId(cat.id); setCategoriaError(null); }}
              />
            ))}
          </View>
          {categoriaError && <Text style={{ color: colors.danger, fontSize: 12, marginBottom: 8 }}>{categoriaError}</Text>}

          <Field label="Observações" value={observacoes} onChangeText={setObservacoes} multiline style={{ height: 100, textAlignVertical: 'top' }} />

          {erroForm && <Text style={{ color: colors.danger, marginBottom: 8 }}>{erroForm}</Text>}

          <TouchableOpacity
            onPress={handleExcluir}
            disabled={salvando || excluindo}
            accessibilityRole="button"
            accessibilityLabel="Excluir transação"
            style={{ marginTop: 8, height: 48, borderRadius: 10, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.danger }}
          >
            {excluindo ? <ActivityIndicator color={colors.danger} size="small" /> : <Text style={{ color: colors.danger, fontWeight: '700', fontSize: 15 }}>Excluir transação</Text>}
          </TouchableOpacity>
        </ScrollView>
      </View>
    </Modal>
  );
}
