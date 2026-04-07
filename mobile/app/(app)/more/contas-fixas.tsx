import React, { useState } from 'react';
import { View, Text, TouchableOpacity, FlatList, Modal, ScrollView, TextInput, ActivityIndicator } from 'react-native';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { contaFixaService } from '../../../src/services/contaFixaService';
import { categoriaService } from '../../../src/services/categoriaService';
import Badge from '../../../src/components/ui/Badge';
import { ContaFixa, ContaFixaRequest } from '../../../src/types';
import { useTheme } from '../../../src/theme';
import SkeletonBox from '../../../src/components/ui/SkeletonBox';

export default function ContasFixasScreen() {
  const colors = useTheme();
  const queryClient = useQueryClient();
  const [modalPagarVisible, setModalPagarVisible] = useState(false);
  const [modalCriarVisible, setModalCriarVisible] = useState(false);
  const [selecionada, setSelecionada] = useState<ContaFixa | null>(null);
  const [valorPago, setValorPago] = useState('0');

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['contas-fixas'],
    queryFn: () => contaFixaService.listar(),
  });

  const pagarMutation = useMutation({
    mutationFn: ({ id, valor }: { id: number; valor: number }) => contaFixaService.marcarComoPaga(id, valor),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contas-fixas'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-resumo'] });
      setModalPagarVisible(false);
    },
  });

  // criar conta fixa
  const [descricaoCriar, setDescricaoCriar] = useState('');
  const [valorCriar, setValorCriar] = useState('');
  const [diaCriar, setDiaCriar] = useState('');
  const [categoriaCriarId, setCategoriaCriarId] = useState<number | null>(null);
  const [recorrenteCriar, setRecorrenteCriar] = useState(false);
  const [descricaoError, setDescricaoError] = useState<string | null>(null);
  const [valorError, setValorError] = useState<string | null>(null);
  const [diaError, setDiaError] = useState<string | null>(null);
  const [categoriaError, setCategoriaError] = useState<string | null>(null);

  const { data: categorias = [] } = useQuery({ queryKey: ['categorias'], queryFn: () => categoriaService.listar() });

  const criarMutation = useMutation({
    mutationFn: (req: ContaFixaRequest) => contaFixaService.criar(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contas-fixas'] });
      setModalCriarVisible(false);
      setDescricaoCriar(''); setValorCriar(''); setDiaCriar(''); setCategoriaCriarId(null); setRecorrenteCriar(false);
    },
    onError: (err: any) => {
      setDescricaoError(err?.userMessage ?? 'Erro ao criar conta fixa.');
    }
  });

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={{ padding: 16 }}>
        <Text style={{ color: colors.textPrimary, fontSize: 18, fontWeight: '700' }}>Contas Fixas</Text>
      </View>

      {isLoading ? (
        <View style={{ padding: 16 }}>
          {[1,2,3].map(i => <SkeletonBox key={i} width="100%" height={72} />)}
        </View>
      ) : isError ? (
        <View style={{ alignItems: 'center', padding: 48 }}>
          <Text style={{ color: colors.textSecondary }}>Erro ao carregar contas fixas</Text>
          <TouchableOpacity onPress={() => refetch()} style={{ marginTop: 8 }}>
            <Text style={{ color: colors.brand }}>Tentar novamente</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <FlatList
          data={data?.content ?? []}
          keyExtractor={item => item.id.toString()}
          renderItem={({ item: cf }) => (
            <View style={{ backgroundColor: colors.card, borderRadius: 12, borderWidth: 1, borderColor: colors.border, padding: 14, marginBottom: 8, marginHorizontal: 16 }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600', flex: 1 }}>{cf.nome}</Text>
                <Badge status={cf.status} />
              </View>
              <Text style={{ color: colors.textSecondary, fontSize: 12 }}>Vence dia {cf.diaVencimento}</Text>
              <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '700', marginTop: 8 }}>{cf.valorPlanejado ? `R$ ${Number(cf.valorPlanejado ?? 0).toFixed(2)}` : 'R$ 0,00'}</Text>
              {(cf.status === 'PENDENTE' || cf.status === 'ATRASADO') && (
                <TouchableOpacity onPress={() => { setSelecionada(cf); setValorPago(String(cf.valorPlanejado ?? 0)); setModalPagarVisible(true); }} style={{ marginTop: 12, paddingVertical: 6, paddingHorizontal: 12, borderRadius: 6, borderWidth: 1, borderColor: colors.brand, alignSelf: 'flex-start' }}>
                  <Text style={{ color: colors.brand, fontSize: 12, fontWeight: '600' }}>Pagar</Text>
                </TouchableOpacity>
              )}
            </View>
          )}
          ListEmptyComponent={() => (
            <View style={{ alignItems: 'center', padding: 48 }}>
              <Text style={{ color: colors.textSecondary }}>Nenhuma conta fixa encontrada</Text>
            </View>
          )}
        />
      )}

      <Modal visible={modalPagarVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity onPress={() => { setModalPagarVisible(false); setValorPago('0'); }}>
              <Text style={{ color: colors.brand, fontSize: 15 }}>Cancelar</Text>
            </TouchableOpacity>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '600' }}>Pagar</Text>
            <TouchableOpacity disabled={pagarMutation.status === 'pending'} onPress={() => {
              const v = parseFloat(valorPago.replace(/\./g, '').replace(/,/g, '.'));
              if (isNaN(v) || v <= 0) return; 
              pagarMutation.mutateAsync({ id: selecionada!.id, valor: v });
            }}>
              <Text style={{ color: colors.brand, fontSize: 15, fontWeight: '600' }}>Confirmar</Text>
            </TouchableOpacity>
          </View>
          <ScrollView contentContainerStyle={{ padding: 16 }}>
            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Valor</Text>
            <TextInput value={valorPago} onChangeText={setValorPago} keyboardType="decimal-pad" placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 16 }} />
          </ScrollView>
        </View>
      </Modal>

      {/* Modal criar conta fixa */}
      <Modal visible={modalCriarVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity onPress={() => { setModalCriarVisible(false); setDescricaoCriar(''); setValorCriar(''); setDiaCriar(''); setCategoriaCriarId(null); setRecorrenteCriar(false); setDescricaoError(null); setValorError(null); setDiaError(null); setCategoriaError(null); }}><Text style={{ color: colors.brand }}>Cancelar</Text></TouchableOpacity>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '600' }}>Nova Conta Fixa</Text>
            <TouchableOpacity disabled={criarMutation.status === 'pending'} onPress={() => {
              setDescricaoError(null); setValorError(null); setDiaError(null); setCategoriaError(null);
              let hasErr = false;
              if (!descricaoCriar.trim()) { setDescricaoError('Descrição obrigatória.'); hasErr = true; }
              const v = parseFloat(valorCriar.replace(/\./g, '').replace(/,/g, '.'));
              if (isNaN(v) || v <= 0) { setValorError('Valor deve ser positivo.'); hasErr = true; }
              const dia = Number(diaCriar);
              if (!Number.isInteger(dia) || dia < 1 || dia > 31) { setDiaError('Dia deve ser um número entre 1 e 31.'); hasErr = true; }
              if (!categoriaCriarId) { setCategoriaError('Selecione uma categoria.'); hasErr = true; }
              if (hasErr) return;
              const payload: ContaFixaRequest = {
                descricao: descricaoCriar.trim(),
                valor: Number(v),
                diaVencimento: dia,
                categoriaId: categoriaCriarId!,
                recorrente: recorrenteCriar,
              };
              criarMutation.mutate(payload);
            }}><Text style={{ color: criarMutation.status === 'pending' ? colors.textMuted : colors.brand }}>Salvar</Text></TouchableOpacity>
          </View>
          <ScrollView contentContainerStyle={{ padding: 16 }}>
            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Descrição</Text>
            <TextInput value={descricaoCriar} onChangeText={setDescricaoCriar} placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />
            {descricaoError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{descricaoError}</Text>}

            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Valor</Text>
            <TextInput value={valorCriar} onChangeText={setValorCriar} keyboardType="decimal-pad" placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />
            {valorError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{valorError}</Text>}

            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Dia de vencimento</Text>
            <TextInput value={diaCriar} onChangeText={setDiaCriar} keyboardType="number-pad" placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />
            {diaError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{diaError}</Text>}

            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Categoria</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginBottom: 8 }}>
              {categorias.map(cat => (
                <TouchableOpacity key={cat.id} onPress={() => setCategoriaCriarId(cat.id)} style={{ paddingHorizontal: 12, paddingVertical: 6, borderRadius: 20, marginRight: 8, backgroundColor: categoriaCriarId === cat.id ? colors.brand + '26' : colors.card, borderWidth: 1, borderColor: categoriaCriarId === cat.id ? colors.brand : colors.border }}>
                  <Text style={{ color: categoriaCriarId === cat.id ? colors.brand : colors.textSecondary }}>{cat.nome}</Text>
                </TouchableOpacity>
              ))}
            </ScrollView>
            {categoriaError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{categoriaError}</Text>}
          </ScrollView>
        </View>
      </Modal>
    </View>
  );
}
