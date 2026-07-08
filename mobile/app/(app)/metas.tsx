import React, { useState } from 'react';
import { View, Text, FlatList, TouchableOpacity, Modal, ScrollView, TextInput } from 'react-native';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { metaService } from '../../src/services/metaService';
import { formatCurrency, formatPercent, formatDate, parseDateBR, isValidDateBR, parseCurrencyBR } from '../../src/utils/format';
import { Meta, MetaRequest } from '../../src/types';
import { useTheme } from '../../src/theme';
import SkeletonBox from '../../src/components/ui/SkeletonBox';

export default function Metas() {
  const colors = useTheme();
  const queryClient = useQueryClient();

  const [modalAdicionarVisible, setModalAdicionarVisible] = useState(false);
  const [modalCriarVisible, setModalCriarVisible] = useState(false);
  const [metaSelecionada, setMetaSelecionada] = useState<Meta | null>(null);
  const [erroCriar, setErroCriar] = useState<string | null>(null);
  const [valorAdicionar, setValorAdicionar] = useState('0');
  const [erroAdicionar, setErroAdicionar] = useState<string | null>(null);

  const [nomeCriar, setNomeCriar] = useState('');
  const [valorTotalCriar, setValorTotalCriar] = useState('');
  const [dataLimiteCriar, setDataLimiteCriar] = useState('');
  const [descricaoCriar, setDescricaoCriar] = useState('');
  const [nomeError, setNomeError] = useState<string | null>(null);
  const [valorTotalError, setValorTotalError] = useState<string | null>(null);
  const [dataLimiteError, setDataLimiteError] = useState<string | null>(null);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['metas'],
    queryFn: () => metaService.listar(),
  });

  const adicionarMutation = useMutation({
    mutationFn: ({ id, valor }: { id: number; valor: number }) => metaService.adicionarValor(id, valor),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['metas'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-resumo'] });
      setModalAdicionarVisible(false);
    },
    onError: (err: any) => setErroAdicionar(err?.userMessage ?? 'Erro ao adicionar.'),
  });

  const criarMutation = useMutation({
    mutationFn: (data: MetaRequest) => metaService.criar(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['metas'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-resumo'] });
      setModalCriarVisible(false);
    },
    onError: (err: any) => setErroCriar(err?.userMessage ?? 'Erro ao criar meta.'),
  });

  const renderItem = ({ item: meta }: { item: Meta }) => {
    const progresso = Number(meta.valorTotal ?? 0) > 0
      ? Math.min((Number(meta.valorReservado ?? 0) / Number(meta.valorTotal ?? 0)) * 100, 100)
      : 0;

    return (
      <View style={{ backgroundColor: colors.card, borderRadius: 12, borderWidth: 1, borderColor: colors.border, padding: 16, marginBottom: 12, marginHorizontal: 16 }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
          <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600', flex: 1 }}>{meta.nome}</Text>
          <View style={{ backgroundColor: meta.ativa ? colors.successBg : colors.dangerBg, paddingHorizontal: 8, paddingVertical: 2, borderRadius: 20 }}>
            <Text style={{ color: meta.ativa ? colors.success : colors.danger, fontSize: 10, fontWeight: '700' }}>{meta.ativa ? 'Ativa' : 'Concluída'}</Text>
          </View>
        </View>

        <Text style={{ color: colors.textSecondary, fontSize: 13, marginBottom: 10 }}>{formatCurrency(Number(meta.valorReservado ?? 0))} de {formatCurrency(Number(meta.valorTotal ?? 0))}</Text>

        <View style={{ backgroundColor: colors.border, height: 6, borderRadius: 3, marginBottom: 6 }}>
          <View style={{ backgroundColor: progresso >= 100 ? colors.success : colors.brand, height: 6, borderRadius: 3, width: `${progresso}%` as any }} />
        </View>

        <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
          <Text style={{ color: colors.textSecondary, fontSize: 11 }}>{formatPercent(progresso)}</Text>
          {meta.dataPrevista && <Text style={{ color: colors.textMuted, fontSize: 11 }}>até {formatDate(meta.dataPrevista)}</Text>}
        </View>

        <TouchableOpacity onPress={() => { setMetaSelecionada(meta); setModalAdicionarVisible(true); }} style={{ marginTop: 12, paddingVertical: 6, paddingHorizontal: 12, borderRadius: 6, borderWidth: 1, borderColor: colors.brand, alignSelf: 'flex-start' }}>
          <Text style={{ color: colors.brand, fontSize: 12, fontWeight: '600' }}>+ Adicionar</Text>
        </TouchableOpacity>
      </View>
    );
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={{ padding: 16 }}>
        <Text style={{ color: colors.textPrimary, fontSize: 18, fontWeight: '700' }}>Metas</Text>
      </View>

      {isLoading ? (
        <View style={{ padding: 16 }}>
          {[1,2,3].map(i => <SkeletonBox key={i} width="100%" height={120} />)}
        </View>
      ) : isError ? (
        <View style={{ alignItems: 'center', padding: 48 }}>
          <Text style={{ color: colors.textSecondary }}>Erro ao carregar metas</Text>
        </View>
      ) : (
        <FlatList data={data?.content ?? []} keyExtractor={m => m.id.toString()} renderItem={renderItem} ListEmptyComponent={() => (
          <View style={{ alignItems: 'center', padding: 48 }}><Text style={{ color: colors.textSecondary }}>Nenhuma meta encontrada</Text></View>
        )} />
      )}

      <TouchableOpacity onPress={() => setModalCriarVisible(true)} style={{ position: 'absolute', bottom: 24, right: 16, width: 56, height: 56, borderRadius: 28, backgroundColor: colors.brand, alignItems: 'center', justifyContent: 'center' }}>
        <Text style={{ color: colors.brandText, fontSize: 28, lineHeight: 30 }}>+</Text>
      </TouchableOpacity>

      {/* Modal adicionar valor (simplified) */}
      <Modal visible={modalAdicionarVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity onPress={() => { setModalAdicionarVisible(false); setValorAdicionar('0'); setErroAdicionar(null); }}><Text style={{ color: colors.brand }}>Cancelar</Text></TouchableOpacity>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '600' }}>Adicionar Valor</Text>
            <TouchableOpacity disabled={adicionarMutation.status === 'pending'} onPress={() => {
              setErroAdicionar(null);
              const v = parseCurrencyBR(valorAdicionar);
              if (isNaN(v) || v <= 0) { setErroAdicionar('Valor deve ser positivo.'); return; }
              adicionarMutation.mutateAsync({ id: metaSelecionada!.id, valor: v });
            }}><Text style={{ color: adicionarMutation.status === 'pending' ? colors.textMuted : colors.brand }}>Adicionar</Text></TouchableOpacity>
          </View>
          <ScrollView contentContainerStyle={{ padding: 16 }}>
            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Valor</Text>
            <TextInput value={valorAdicionar} onChangeText={setValorAdicionar} keyboardType="decimal-pad" placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />
            {erroAdicionar && <Text style={{ color: colors.danger, marginBottom: 8 }}>{erroAdicionar}</Text>}
          </ScrollView>
        </View>
      </Modal>

      {/* Modal criar meta (simplified) */}
      <Modal visible={modalCriarVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity onPress={() => { setModalCriarVisible(false); setNomeCriar(''); setValorTotalCriar(''); setDataLimiteCriar(''); setDescricaoCriar(''); setNomeError(null); setValorTotalError(null); setDataLimiteError(null); setErroCriar(null); }}><Text style={{ color: colors.brand }}>Cancelar</Text></TouchableOpacity>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '600' }}>Criar Meta</Text>
            <TouchableOpacity disabled={criarMutation.status === 'pending'} onPress={() => {
              setNomeError(null); setValorTotalError(null); setDataLimiteError(null); setErroCriar(null);
              let hasErr = false;
              if (!nomeCriar.trim() || nomeCriar.trim().length < 3) { setNomeError('Nome obrigatório (mínimo 3 caracteres).'); hasErr = true; }
              const v = parseCurrencyBR(valorTotalCriar);
              if (isNaN(v) || v <= 0) { setValorTotalError('Valor total obrigatório e positivo.'); hasErr = true; }
              if (dataLimiteCriar && !isValidDateBR(dataLimiteCriar)) { setDataLimiteError('Data inválida. Use o formato DD/MM/AAAA.'); hasErr = true; }
              if (hasErr) return;
              const payload: MetaRequest = {
                nome: nomeCriar.trim(),
                valorTotal: Number(v),
                dataLimite: dataLimiteCriar ? parseDateBR(dataLimiteCriar) : undefined,
                descricao: descricaoCriar || undefined,
              };
              criarMutation.mutate(payload);
            }}><Text style={{ color: criarMutation.status === 'pending' ? colors.textMuted : colors.brand }}>Salvar</Text></TouchableOpacity>
          </View>
          <ScrollView contentContainerStyle={{ padding: 16 }}>
            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Nome</Text>
            <TextInput value={nomeCriar} onChangeText={setNomeCriar} placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />
            {nomeError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{nomeError}</Text>}

            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Valor total</Text>
            <TextInput value={valorTotalCriar} onChangeText={setValorTotalCriar} keyboardType="decimal-pad" placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />
            {valorTotalError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{valorTotalError}</Text>}

            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Data limite</Text>
            <TextInput value={dataLimiteCriar} onChangeText={setDataLimiteCriar} placeholder="DD/MM/AAAA" placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />
            {dataLimiteError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{dataLimiteError}</Text>}

            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Descrição (opcional)</Text>
            <TextInput value={descricaoCriar} onChangeText={setDescricaoCriar} placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />
            {erroCriar && <Text style={{ color: colors.danger, marginTop: 8 }}>{erroCriar}</Text>}
          </ScrollView>
        </View>
      </Modal>
    </View>
  );
}
