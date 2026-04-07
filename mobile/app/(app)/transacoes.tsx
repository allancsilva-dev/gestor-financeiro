import React, { useMemo, useState } from 'react';
import { View, Text, FlatList, RefreshControl, Modal, TextInput, TouchableOpacity, ActivityIndicator, ScrollView } from 'react-native';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { transacaoService } from '../../src/services/transacaoService';
import { categoriaService } from '../../src/services/categoriaService';
import { useTheme } from '../../src/theme';
import { useAuth } from '../../src/context/AuthContext';
import { formatCurrency, formatDate, parseDateBR, isValidDateBR, getInitials } from '../../src/utils/format';
import { Transacao, TransacaoRequest, TipoTransacao } from '../../src/types';
import SkeletonBox from '../../src/components/ui/SkeletonBox';

export default function Transacoes() {
  const colors = useTheme();
  const { usuario } = useAuth();
  const queryClient = useQueryClient();

  const [filtro, setFiltro] = useState<'TODOS' | TipoTransacao>('TODOS');
  const [modalVisible, setModalVisible] = useState(false);
  const [salvando, setSalvando] = useState(false);
  const [erroForm, setErroForm] = useState<string | null>(null);
  const [descricaoError, setDescricaoError] = useState<string | null>(null);
  const [valorError, setValorError] = useState<string | null>(null);
  const [dataError, setDataError] = useState<string | null>(null);
  const [categoriaError, setCategoriaError] = useState<string | null>(null);

  const [descricao, setDescricao] = useState('');
  const [valor, setValor] = useState('');
  const [data, setData] = useState('');
  const [tipo, setTipo] = useState<TipoTransacao>('SAIDA');
  const [categoriaId, setCategoriaId] = useState<number | null>(null);
  const [observacoes, setObservacoes] = useState('');

  const { data: paginatedData, isLoading, isError, refetch, isRefetching } = useQuery({
    queryKey: ['transacoes'],
    queryFn: () => transacaoService.listar(0, 20),
  });

  const { data: categorias = [] } = useQuery({
    queryKey: ['categorias'],
    queryFn: () => categoriaService.listar(),
  });

  const transacoesFiltradas = useMemo(() => {
    const lista = paginatedData?.content ?? [];
    if (filtro === 'ENTRADA') return lista.filter(t => t.tipo === 'ENTRADA');
    if (filtro === 'SAIDA') return lista.filter(t => t.tipo === 'SAIDA');
    return lista;
  }, [paginatedData, filtro]);

  const parseBRCurrency = (v: string) => {
    const cleaned = v.replace(/\./g, '').replace(/,/g, '.').trim();
    const n = parseFloat(cleaned);
    return isNaN(n) ? NaN : n;
  };

  const resetErrors = () => {
    setDescricaoError(null); setValorError(null); setDataError(null); setCategoriaError(null); setErroForm(null);
  };

  const handleSalvar = async () => {
    resetErrors();
    let hasError = false;
    if (!descricao.trim() || descricao.trim().length < 3) { setDescricaoError('Descrição deve ter entre 3 e 255 caracteres.'); hasError = true; }
    const valorNum = parseBRCurrency(valor);
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
        observacoes: observacoes.trim() || undefined,
      };
      await transacaoService.criar(request);
      queryClient.invalidateQueries({ queryKey: ['transacoes'] });
      queryClient.invalidateQueries({ queryKey: ['transacoes-recentes'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-resumo'] });
      setModalVisible(false);
      setDescricao(''); setValor(''); setData(''); setTipo('SAIDA'); setCategoriaId(null); setObservacoes('');
      resetErrors();
    } catch (err: any) {
      setErroForm(err?.userMessage ?? 'Erro ao salvar. Tente novamente.');
    } finally {
      setSalvando(false);
    }
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={{ padding: 16 }}>
        <Text style={{ color: colors.textPrimary, fontSize: 20, fontWeight: '700' }}>Transações</Text>
        <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 6 }}>{usuario?.nome ?? ''}</Text>
      </View>

      <View style={{ flexDirection: 'row', gap: 8, paddingHorizontal: 16, marginBottom: 8, paddingVertical: 8 }}>
        {(['TODOS','ENTRADA','SAIDA'] as Array<'TODOS'|TipoTransacao>).map(ch => (
          <TouchableOpacity key={ch} onPress={() => setFiltro(ch)} style={{ height: 28, paddingHorizontal: 12, borderRadius: 20, borderWidth: 1, borderColor: filtro === ch ? colors.brand : colors.border, backgroundColor: filtro === ch ? colors.brand + '26' : colors.card, justifyContent: 'center' }}>
            <Text style={{ color: filtro === ch ? colors.brand : colors.textSecondary, fontSize: 12, fontWeight: filtro === ch ? '600' : '400' }}>{ch === 'TODOS' ? 'Todos' : ch === 'ENTRADA' ? 'Entradas' : 'Saídas'}</Text>
          </TouchableOpacity>
        ))}
      </View>

      <FlatList
        data={transacoesFiltradas}
        keyExtractor={item => item.id.toString()}
        refreshControl={<RefreshControl refreshing={isRefetching} onRefresh={refetch} />}
        ListEmptyComponent={isLoading ? (
          <View style={{ padding: 16, gap: 8 }}>
            {[1,2,3,4,5].map(i => <SkeletonBox key={i} width="100%" height={64} />)}
          </View>
        ) : (
          <View style={{ alignItems: 'center', padding: 48 }}>
            <Text style={{ color: colors.textSecondary, fontSize: 15 }}>Nenhuma transação encontrada</Text>
            <Text style={{ color: colors.textMuted, fontSize: 13, marginTop: 4 }}>Adicione sua primeira transação</Text>
          </View>
        )}
        renderItem={({ item: t }) => (
          <View style={{ height: 64, flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <View style={{ width: 40, height: 40, borderRadius: 8, backgroundColor: t.tipo === 'ENTRADA' ? colors.successBg : colors.dangerBg, alignItems: 'center', justifyContent: 'center' }}>
              <Text style={{ color: t.tipo === 'ENTRADA' ? colors.success : colors.danger, fontSize: 20 }}>{t.tipo === 'ENTRADA' ? '↑' : '↓'}</Text>
            </View>
            <View style={{ flex: 1 }}>
              <Text style={{ color: colors.textPrimary, fontSize: 14, fontWeight: '500' }} numberOfLines={1}>{t.descricao}</Text>
              <Text style={{ color: colors.textSecondary, fontSize: 12 }}>{t.categoria?.nome ?? 'Sem categoria'} · {formatDate(t.data)}</Text>
            </View>
            <Text style={{ color: t.tipo === 'ENTRADA' ? colors.success : colors.danger, fontSize: 14, fontWeight: '600' }}>{formatCurrency(Number(t.valorTotal ?? 0))}</Text>
          </View>
        )}
      />

      <TouchableOpacity onPress={() => setModalVisible(true)} style={{ position: 'absolute', bottom: 24, right: 16, width: 56, height: 56, borderRadius: 28, backgroundColor: colors.brand, alignItems: 'center', justifyContent: 'center' }}>
        <Text style={{ color: colors.brandText, fontSize: 28, lineHeight: 30 }}>+</Text>
      </TouchableOpacity>

      <Modal visible={modalVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity onPress={() => { setModalVisible(false); setDescricao(''); setValor(''); setData(''); setTipo('SAIDA'); setCategoriaId(null); setObservacoes(''); resetErrors(); }}>
              <Text style={{ color: colors.brand, fontSize: 15 }}>Cancelar</Text>
            </TouchableOpacity>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '600' }}>Nova Transação</Text>
            <TouchableOpacity onPress={handleSalvar} disabled={salvando}>
              {salvando ? <ActivityIndicator color={colors.brand} size="small" /> : <Text style={{ color: colors.brand, fontSize: 15, fontWeight: '600' }}>Salvar</Text>}
            </TouchableOpacity>
          </View>
          <ScrollView contentContainerStyle={{ padding: 16 }}>
            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Tipo</Text>
            <View style={{ flexDirection: 'row', gap: 8, marginBottom: 16 }}>
              {(['ENTRADA','SAIDA'] as TipoTransacao[]).map(t => (
                <TouchableOpacity key={t} onPress={() => setTipo(t)} style={{ paddingHorizontal: 12, paddingVertical: 6, borderRadius: 20, backgroundColor: tipo === t ? colors.brand + '26' : colors.card, borderWidth: 1, borderColor: tipo === t ? colors.brand : colors.border }}>
                  <Text style={{ color: tipo === t ? colors.brand : colors.textSecondary }}>{t === 'ENTRADA' ? 'Entrada' : 'Saída'}</Text>
                </TouchableOpacity>
              ))}
            </View>

            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Descrição</Text>
            <TextInput value={descricao} onChangeText={setDescricao} placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />
            {descricaoError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{descricaoError}</Text>}

            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Valor</Text>
            <TextInput value={valor} onChangeText={setValor} keyboardType="decimal-pad" placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />
            {valorError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{valorError}</Text>}

            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Data</Text>
            <TextInput value={data} onChangeText={setData} placeholder="DD/MM/AAAA" placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />
            {dataError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{dataError}</Text>}

            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Categoria</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginBottom: 8 }}>
              {categorias.map(cat => (
                <TouchableOpacity key={cat.id} onPress={() => setCategoriaId(cat.id)} style={{ paddingHorizontal: 12, paddingVertical: 6, borderRadius: 20, marginRight: 8, backgroundColor: categoriaId === cat.id ? colors.brand + '26' : colors.card, borderWidth: 1, borderColor: categoriaId === cat.id ? colors.brand : colors.border }}>
                  <Text style={{ color: categoriaId === cat.id ? colors.brand : colors.textSecondary }}>{cat.nome}</Text>
                </TouchableOpacity>
              ))}
            </ScrollView>
            {categoriaError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{categoriaError}</Text>}

            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Observações</Text>
            <TextInput value={observacoes} onChangeText={setObservacoes} multiline placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 16, height: 100 }} />

            {erroForm && <Text style={{ color: colors.danger, marginBottom: 8 }}>{erroForm}</Text>}
          </ScrollView>
        </View>
      </Modal>
    </View>
  );
}
