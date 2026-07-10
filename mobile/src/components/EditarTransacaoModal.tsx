import React, { useEffect, useState } from 'react';
import { View, Text, Modal, TouchableOpacity, ActivityIndicator, ScrollView, Alert } from 'react-native';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import * as DocumentPicker from 'expo-document-picker';
import * as ImagePicker from 'expo-image-picker';
import { transacaoService } from '../services/transacaoService';
import { categoriaService } from '../services/categoriaService';
import parcelaService from '../services/parcelaService';
import anexoService, { UploadFile } from '../services/anexoService';
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
const bytesLabel = (bytes: number) => {
  if (!bytes) return '0 KB';
  if (bytes < 1024 * 1024) return `${Math.max(bytes / 1024, 1).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

// Sheet "Editar Transação" — aberto ao tocar numa linha da lista de transações.
// Tipo e forma de pagamento ficam fixos; categoria pode mudar sem recriar lançamento.
export default function EditarTransacaoModal({ visible, transacao, onClose }: EditarTransacaoModalProps) {
  const colors = useTheme();
  const queryClient = useQueryClient();

  const [salvando, setSalvando] = useState(false);
  const [excluindo, setExcluindo] = useState(false);
  const [parcelaActionId, setParcelaActionId] = useState<number | null>(null);
  const [anexoAction, setAnexoAction] = useState<'arquivo' | 'camera' | number | null>(null);
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

  const parcelasQuery = useQuery({
    queryKey: ['parcelas', transacao?.id],
    queryFn: () => parcelaService.listarPorTransacao(transacao!.id),
    enabled: visible && Boolean(transacao?.parcelado && transacao?.id),
  });

  const anexosQuery = useQuery({
    queryKey: ['anexos', transacao?.id],
    queryFn: () => anexoService.listar(transacao!.id),
    enabled: visible && Boolean(transacao?.id),
  });

  const invalidarQueries = () => {
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
    queryClient.invalidateQueries({ queryKey: ['categorias'] });
    queryClient.invalidateQueries({ queryKey: ['parcelas', transacao?.id] });
    queryClient.invalidateQueries({ queryKey: ['anexos', transacao?.id] });
  };

  const handleToggleParcela = async (parcelaId: number, paga: boolean) => {
    setParcelaActionId(parcelaId);
    setErroForm(null);
    try {
      if (paga) {
        await parcelaService.despagar(parcelaId);
      } else {
        await parcelaService.pagar(parcelaId);
      }
      invalidarQueries();
      await parcelasQuery.refetch();
    } catch (err: any) {
      setErroForm(err?.userMessage ?? 'Erro ao atualizar parcela. Tente novamente.');
    } finally {
      setParcelaActionId(null);
    }
  };

  const uploadAnexo = async (file: UploadFile, action: 'arquivo' | 'camera') => {
    if (!transacao) return;
    setAnexoAction(action);
    setErroForm(null);
    try {
      await anexoService.upload(transacao.id, file);
      await anexosQuery.refetch();
    } catch (err: any) {
      setErroForm(err?.userMessage ?? 'Erro ao enviar anexo. Tente novamente.');
    } finally {
      setAnexoAction(null);
    }
  };

  const handleSelecionarArquivo = async () => {
    const result = await DocumentPicker.getDocumentAsync({
      type: ['image/*', 'application/pdf'],
      multiple: false,
      copyToCacheDirectory: true,
    });
    if (result.canceled || !result.assets?.[0]) return;
    const asset = result.assets[0];
    await uploadAnexo({
      uri: asset.uri,
      name: asset.name || 'comprovante',
      type: asset.mimeType || 'application/octet-stream',
    }, 'arquivo');
  };

  const handleTirarFoto = async () => {
    const permission = await ImagePicker.requestCameraPermissionsAsync();
    if (!permission.granted) {
      Alert.alert('Comprovante', 'Permita acesso à câmera para anexar uma foto.');
      return;
    }
    const result = await ImagePicker.launchCameraAsync({
      mediaTypes: ['images'],
      quality: 0.85,
    });
    if (result.canceled || !result.assets?.[0]) return;
    const asset = result.assets[0];
    await uploadAnexo({
      uri: asset.uri,
      name: asset.fileName || `comprovante-${Date.now()}.jpg`,
      type: asset.mimeType || 'image/jpeg',
    }, 'camera');
  };

  const handleExcluirAnexo = (id: number, nome: string) => {
    Alert.alert('Excluir anexo', `Excluir "${nome}"?`, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Excluir',
        style: 'destructive',
        onPress: async () => {
          setAnexoAction(id);
          setErroForm(null);
          try {
            await anexoService.deletar(id);
            await anexosQuery.refetch();
          } catch (err: any) {
            setErroForm(err?.userMessage ?? 'Erro ao excluir anexo. Tente novamente.');
          } finally {
            setAnexoAction(null);
          }
        },
      },
    ]);
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

          {transacao?.parcelado && (
            <View style={{ backgroundColor: colors.card, borderColor: colors.border, borderWidth: 1, borderRadius: 12, padding: 12, marginBottom: 16 }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
                <View style={{ flex: 1 }}>
                  <Text style={{ color: colors.textPrimary, fontSize: 14, fontWeight: '700' }}>Parcelas</Text>
                  <Text style={{ color: colors.textSecondary, fontSize: 11, marginTop: 2 }}>Controle individual de pagamento</Text>
                </View>
                {parcelasQuery.isFetching && <ActivityIndicator color={colors.brand} size="small" />}
              </View>

              {parcelasQuery.isError ? (
                <View style={{ paddingTop: 12 }}>
                  <Text style={{ color: colors.textSecondary, fontSize: 12 }}>Erro ao carregar parcelas.</Text>
                  <TouchableOpacity onPress={() => parcelasQuery.refetch()} accessibilityRole="button" style={{ marginTop: 6, minHeight: 36, justifyContent: 'center' }}>
                    <Text style={{ color: colors.brandFg, fontSize: 13, fontWeight: '600' }}>Tentar novamente</Text>
                  </TouchableOpacity>
                </View>
              ) : parcelasQuery.data?.content.length === 0 ? (
                <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 12 }}>Nenhuma parcela encontrada.</Text>
              ) : (
                <View style={{ marginTop: 10, gap: 8 }}>
                  {parcelasQuery.data?.content.map(p => {
                    const paga = p.status === 'PAGO';
                    return (
                      <View key={p.id} style={{ flexDirection: 'row', alignItems: 'center', gap: 10, paddingTop: 8, borderTopWidth: 1, borderTopColor: colors.border }}>
                        <View style={{ flex: 1, minWidth: 0 }}>
                          <Text style={{ color: colors.textPrimary, fontSize: 13, fontWeight: '600' }}>
                            {p.numeroParcela}/{p.totalParcelas} · R$ {Number(p.valor ?? 0).toFixed(2).replace('.', ',')}
                          </Text>
                          <Text style={{ color: colors.textSecondary, fontSize: 11, marginTop: 2 }}>
                            Vence {isoToBR(p.dataVencimento)} · {paga ? 'Paga' : p.status === 'ATRASADO' ? 'Atrasada' : 'Pendente'}
                          </Text>
                        </View>
                        <TouchableOpacity
                          onPress={() => handleToggleParcela(p.id, paga)}
                          disabled={parcelaActionId != null}
                          accessibilityRole="button"
                          accessibilityLabel={paga ? `Desfazer pagamento da parcela ${p.numeroParcela}` : `Pagar parcela ${p.numeroParcela}`}
                          style={{
                            minHeight: 36,
                            borderRadius: 999,
                            paddingHorizontal: 12,
                            alignItems: 'center',
                            justifyContent: 'center',
                            backgroundColor: paga ? colors.successBg : colors.brandBg,
                          }}
                        >
                          {parcelaActionId === p.id ? (
                            <ActivityIndicator color={paga ? colors.success : colors.brandFg} size="small" />
                          ) : (
                            <Text style={{ color: paga ? colors.success : colors.brandFg, fontSize: 12, fontWeight: '700' }}>
                              {paga ? 'Desfazer' : 'Pagar'}
                            </Text>
                          )}
                        </TouchableOpacity>
                      </View>
                    );
                  })}
                </View>
              )}
            </View>
          )}

          <View style={{ backgroundColor: colors.card, borderColor: colors.border, borderWidth: 1, borderRadius: 12, padding: 12, marginBottom: 16 }}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
              <View style={{ flex: 1 }}>
                <Text style={{ color: colors.textPrimary, fontSize: 14, fontWeight: '700' }}>Comprovantes</Text>
                <Text style={{ color: colors.textSecondary, fontSize: 11, marginTop: 2 }}>Fotos, PDFs ou imagens da transação</Text>
              </View>
              {anexosQuery.isFetching && <ActivityIndicator color={colors.brand} size="small" />}
            </View>

            <View style={{ flexDirection: 'row', gap: 8, marginTop: 12 }}>
              <TouchableOpacity
                onPress={handleTirarFoto}
                disabled={anexoAction != null}
                accessibilityRole="button"
                style={{ flex: 1, minHeight: 40, borderRadius: 999, backgroundColor: colors.brandBg, alignItems: 'center', justifyContent: 'center' }}
              >
                {anexoAction === 'camera' ? <ActivityIndicator color={colors.brandFg} size="small" /> : <Text style={{ color: colors.brandFg, fontSize: 13, fontWeight: '700' }}>Câmera</Text>}
              </TouchableOpacity>
              <TouchableOpacity
                onPress={handleSelecionarArquivo}
                disabled={anexoAction != null}
                accessibilityRole="button"
                style={{ flex: 1, minHeight: 40, borderRadius: 999, backgroundColor: colors.infoBg, alignItems: 'center', justifyContent: 'center' }}
              >
                {anexoAction === 'arquivo' ? <ActivityIndicator color={colors.info} size="small" /> : <Text style={{ color: colors.info, fontSize: 13, fontWeight: '700' }}>Arquivo</Text>}
              </TouchableOpacity>
            </View>

            {anexosQuery.isError ? (
              <View style={{ paddingTop: 12 }}>
                <Text style={{ color: colors.textSecondary, fontSize: 12 }}>Erro ao carregar comprovantes.</Text>
                <TouchableOpacity onPress={() => anexosQuery.refetch()} accessibilityRole="button" style={{ marginTop: 6, minHeight: 36, justifyContent: 'center' }}>
                  <Text style={{ color: colors.brandFg, fontSize: 13, fontWeight: '600' }}>Tentar novamente</Text>
                </TouchableOpacity>
              </View>
            ) : anexosQuery.data?.length === 0 ? (
              <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 12 }}>Nenhum comprovante anexado.</Text>
            ) : (
              <View style={{ marginTop: 10, gap: 8 }}>
                {anexosQuery.data?.map(a => (
                  <View key={a.id} style={{ flexDirection: 'row', alignItems: 'center', gap: 10, paddingTop: 8, borderTopWidth: 1, borderTopColor: colors.border }}>
                    <View style={{ flex: 1, minWidth: 0 }}>
                      <Text style={{ color: colors.textPrimary, fontSize: 13, fontWeight: '600' }} numberOfLines={1}>{a.nome}</Text>
                      <Text style={{ color: colors.textSecondary, fontSize: 11, marginTop: 2 }}>
                        {bytesLabel(Number(a.tamanho ?? 0))}
                      </Text>
                    </View>
                    <TouchableOpacity
                      onPress={() => handleExcluirAnexo(a.id, a.nome)}
                      disabled={anexoAction != null}
                      accessibilityRole="button"
                      accessibilityLabel={`Excluir comprovante ${a.nome}`}
                      style={{ minHeight: 36, borderRadius: 999, paddingHorizontal: 12, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.dangerBg }}
                    >
                      {anexoAction === a.id ? <ActivityIndicator color={colors.danger} size="small" /> : <Text style={{ color: colors.danger, fontSize: 12, fontWeight: '700' }}>Excluir</Text>}
                    </TouchableOpacity>
                  </View>
                ))}
              </View>
            )}
          </View>

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
