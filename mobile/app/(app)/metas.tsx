import React, { useState } from 'react';
import { View, Text, FlatList, TouchableOpacity, Modal, ScrollView, ActivityIndicator } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { metaService } from '../../src/services/metaService';
import { formatCurrency, formatPercent, formatDate, parseDateBR, isValidDateBR, parseCurrencyBR, maskCurrencyInput, maskDateInput } from '../../src/utils/format';
import { Meta, MetaRequest } from '../../src/types';
import { useTheme } from '../../src/theme';
import SkeletonBox from '../../src/components/ui/SkeletonBox';
import Card from '../../src/components/ui/Card';
import IconTile from '../../src/components/ui/IconTile';
import Badge from '../../src/components/ui/Badge';
import ProgressBar from '../../src/components/ui/ProgressBar';
import Fab from '../../src/components/ui/Fab';
import Field from '../../src/components/ui/Field';

export default function Metas() {
  const colors = useTheme();
  const queryClient = useQueryClient();
  const insets = useSafeAreaInsets();

  const [modalAdicionarVisible, setModalAdicionarVisible] = useState(false);
  const [modalCriarVisible, setModalCriarVisible] = useState(false);
  const [metaSelecionada, setMetaSelecionada] = useState<Meta | null>(null);
  const [erroCriar, setErroCriar] = useState<string | null>(null);
  const [valorAdicionar, setValorAdicionar] = useState('');
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
    mutationFn: (payload: MetaRequest) => metaService.criar(payload),
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

    const concluida = !meta.ativa || progresso >= 100;

    return (
      <Card radius={20} style={{ marginBottom: 12 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 12 }}>
          <IconTile tone={concluida ? 'success' : 'brand'} size={44}>{concluida ? '🏆' : '🎯'}</IconTile>
          <View style={{ flex: 1, minWidth: 0 }}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
              <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '700', flex: 1 }} numberOfLines={1}>{meta.nome}</Text>
              <Badge tone={meta.ativa ? 'brand' : 'success'}>{meta.ativa ? 'Ativa' : 'Concluída'}</Badge>
            </View>
            <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 2, fontVariant: ['tabular-nums'] }}>
              {formatCurrency(Number(meta.valorReservado ?? 0))} de {formatCurrency(Number(meta.valorTotal ?? 0))}
            </Text>
          </View>
        </View>

        <ProgressBar value={progresso} />

        <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 8 }}>
          <Text style={{ color: concluida ? colors.success : colors.brandFg, fontSize: 12, fontWeight: '700' }}>{formatPercent(progresso)}</Text>
          {meta.dataPrevista && <Text style={{ color: colors.textSecondary, fontSize: 11 }}>até {formatDate(meta.dataPrevista)}</Text>}
        </View>

        <TouchableOpacity
          onPress={() => { setValorAdicionar('0'); setErroAdicionar(null); setMetaSelecionada(meta); setModalAdicionarVisible(true); }}
          accessibilityRole="button"
          accessibilityLabel={`Adicionar valor à meta ${meta.nome}`}
          style={{ marginTop: 12, paddingVertical: 8, paddingHorizontal: 14, borderRadius: 999, borderWidth: 1, borderColor: colors.brand, alignSelf: 'flex-start' }}
        >
          <Text style={{ color: colors.brandFg, fontSize: 12, fontWeight: '600' }}>+ Adicionar</Text>
        </TouchableOpacity>
      </Card>
    );
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={{ paddingTop: insets.top + 16, paddingHorizontal: 16, paddingBottom: 12 }}>
        <Text style={{ color: colors.textPrimary, fontSize: 23, fontWeight: '800', letterSpacing: -0.4 }}>Planejamento</Text>
        <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4 }}>Seu progresso rumo aos objetivos</Text>
      </View>

      {isLoading ? (
        <View style={{ paddingHorizontal: 16, gap: 12 }}>
          {[1, 2, 3].map(i => <SkeletonBox key={i} width="100%" height={140} borderRadius={18} />)}
        </View>
      ) : isError ? (
        <View style={{ alignItems: 'center', padding: 48 }}>
          <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>Erro ao carregar metas</Text>
          <TouchableOpacity onPress={() => refetch()} style={{ marginTop: 8 }} accessibilityRole="button">
            <Text style={{ color: colors.brandFg, fontWeight: '600' }}>Tentar novamente</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <FlatList
          data={data?.content ?? []}
          keyExtractor={m => m.id.toString()}
          renderItem={renderItem}
          contentContainerStyle={{ paddingHorizontal: 16, paddingBottom: 96 }}
          ListEmptyComponent={() => (
            <View style={{ alignItems: 'center', padding: 48 }}>
              <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>Nenhuma meta ainda</Text>
              <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4 }}>Toque no + para criar a primeira</Text>
            </View>
          )}
        />
      )}

      <Fab onPress={() => setModalCriarVisible(true)} accessibilityLabel="Criar meta" />

      <Modal visible={modalAdicionarVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity onPress={() => { setModalAdicionarVisible(false); setValorAdicionar(''); setErroAdicionar(null); }} accessibilityRole="button">
              <Text style={{ color: colors.brandFg, fontSize: 15 }}>Cancelar</Text>
            </TouchableOpacity>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '600' }}>Adicionar Valor</Text>
            <TouchableOpacity
              disabled={adicionarMutation.status === 'pending'}
              accessibilityRole="button"
              onPress={() => {
                setErroAdicionar(null);
                const v = parseCurrencyBR(valorAdicionar);
                if (isNaN(v) || v <= 0) { setErroAdicionar('Valor deve ser positivo.'); return; }
                adicionarMutation.mutate({ id: metaSelecionada!.id, valor: v });
              }}
            >
              {adicionarMutation.status === 'pending'
                ? <ActivityIndicator color={colors.brand} size="small" />
                : <Text style={{ color: colors.brandFg, fontSize: 15, fontWeight: '600' }}>Adicionar</Text>}
            </TouchableOpacity>
          </View>
          <ScrollView contentContainerStyle={{ padding: 16 }} keyboardShouldPersistTaps="handled">
            <Field label="Valor" value={valorAdicionar} onChangeText={(t) => setValorAdicionar(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={erroAdicionar} autoFocus />
          </ScrollView>
        </View>
      </Modal>

      <Modal visible={modalCriarVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity
              accessibilityRole="button"
              onPress={() => { setModalCriarVisible(false); setNomeCriar(''); setValorTotalCriar(''); setDataLimiteCriar(''); setDescricaoCriar(''); setNomeError(null); setValorTotalError(null); setDataLimiteError(null); setErroCriar(null); }}
            >
              <Text style={{ color: colors.brandFg, fontSize: 15 }}>Cancelar</Text>
            </TouchableOpacity>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '600' }}>Criar Meta</Text>
            <TouchableOpacity
              disabled={criarMutation.status === 'pending'}
              accessibilityRole="button"
              onPress={() => {
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
              }}
            >
              {criarMutation.status === 'pending'
                ? <ActivityIndicator color={colors.brand} size="small" />
                : <Text style={{ color: colors.brandFg, fontSize: 15, fontWeight: '600' }}>Salvar</Text>}
            </TouchableOpacity>
          </View>
          <ScrollView contentContainerStyle={{ padding: 16 }} keyboardShouldPersistTaps="handled">
            <Field label="Nome" value={nomeCriar} onChangeText={setNomeCriar} placeholder="Ex: Reserva de emergência" error={nomeError} autoFocus />
            <Field label="Valor total" value={valorTotalCriar} onChangeText={(t) => setValorTotalCriar(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={valorTotalError} />
            <Field label="Data limite" value={dataLimiteCriar} onChangeText={(t) => setDataLimiteCriar(maskDateInput(t))} placeholder="DD/MM/AAAA" keyboardType="number-pad" error={dataLimiteError} />
            <Field label="Descrição (opcional)" value={descricaoCriar} onChangeText={setDescricaoCriar} />
            {erroCriar && <Text style={{ color: colors.danger, marginTop: 8 }}>{erroCriar}</Text>}
          </ScrollView>
        </View>
      </Modal>
    </View>
  );
}
