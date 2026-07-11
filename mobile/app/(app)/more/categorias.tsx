import React, { useState } from 'react';
import { View, Text, TouchableOpacity, FlatList, Modal, ScrollView } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { categoriaService } from '../../../src/services/categoriaService';
import { CATEGORY_COLORS, formatCurrency } from '../../../src/utils/format';
import { Categoria, CategoriaRequest } from '../../../src/types';
import { useTheme } from '../../../src/theme';
import BackButton from '../../../src/components/ui/BackButton';
import SkeletonBox from '../../../src/components/ui/SkeletonBox';
import Field from '../../../src/components/ui/Field';
import Fab from '../../../src/components/ui/Fab';

export default function CategoriasScreen() {
  const colors = useTheme();
  const insets = useSafeAreaInsets();
  const queryClient = useQueryClient();
  const [modalVisible, setModalVisible] = useState(false);
  const [nome, setNome] = useState('');
  const [corSelecionada, setCorSelecionada] = useState(CATEGORY_COLORS[0]);
  const [nomeError, setNomeError] = useState<string | null>(null);
  const [corError, setCorError] = useState<string | null>(null);

  const { data: categorias = [], isLoading, isError, refetch } = useQuery({
    queryKey: ['categorias'],
    queryFn: () => categoriaService.listar(),
  });

  const criarMutation = useMutation({
    mutationFn: (req: CategoriaRequest) => categoriaService.criar(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categorias'] });
      setModalVisible(false);
      setNome(''); setCorSelecionada(CATEGORY_COLORS[0]); setNomeError(null); setCorError(null);
    },
    onError: (err: any) => {
      setNomeError(err?.userMessage ?? 'Erro ao criar categoria.');
    }
  });

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={{ paddingTop: insets.top + 16, paddingHorizontal: 16, paddingBottom: 12 }}>
        <BackButton />
        <Text style={{ color: colors.textPrimary, fontSize: 23, fontWeight: '800', letterSpacing: -0.4 }}>Categorias</Text>
        <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4 }}>Organize seus gastos por tipo</Text>
      </View>

      {isLoading ? (
        <View style={{ padding: 16 }}>
          {[1,2,3].map(i => <SkeletonBox key={i} width="100%" height={64} />)}
        </View>
      ) : isError ? (
        <View style={{ alignItems: 'center', padding: 48 }}>
          <Text style={{ color: colors.textSecondary }}>Erro ao carregar categorias</Text>
          <TouchableOpacity onPress={() => refetch()} style={{ marginTop: 8 }}>
            <Text style={{ color: colors.brand }}>Tentar novamente</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <FlatList
          data={categorias}
          keyExtractor={item => item.id.toString()}
          renderItem={({ item: cat }) => (
            <View style={{ height: 56, flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
              <View style={{ width: 12, height: 12, borderRadius: 6, backgroundColor: cat.cor ?? colors.textMuted }} />
              <Text style={{ color: colors.textPrimary, fontSize: 14, fontWeight: '500', flex: 1 }}>{cat.nome}</Text>
              <Text style={{ color: colors.textSecondary, fontSize: 12 }}>{formatCurrency(Number(cat.valorGasto ?? 0))}</Text>
              {!cat.ativo && <View style={{ marginLeft: 8, paddingHorizontal: 6, paddingVertical: 2, borderRadius: 6, backgroundColor: colors.border }}><Text style={{ color: colors.textMuted, fontSize: 11 }}>Inativo</Text></View>}
            </View>
          )}
          ListEmptyComponent={() => (
            <View style={{ alignItems: 'center', padding: 48 }}>
              <Text style={{ color: colors.textSecondary }}>Nenhuma categoria encontrada</Text>
            </View>
          )}
        />
      )}

      <Fab onPress={() => setModalVisible(true)} accessibilityLabel="Nova categoria" />

      <Modal visible={modalVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity accessibilityRole="button" style={{ minHeight: 44, justifyContent: 'center' }} onPress={() => { setModalVisible(false); setNome(''); setCorSelecionada(CATEGORY_COLORS[0]); setNomeError(null); setCorError(null); }}>
              <Text style={{ color: colors.brandFg, fontSize: 15 }}>Cancelar</Text>
            </TouchableOpacity>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '600' }}>Nova Categoria</Text>
            <TouchableOpacity accessibilityRole="button" style={{ minHeight: 44, justifyContent: 'center' }} disabled={criarMutation.status === 'pending'} onPress={() => {
              setNomeError(null); setCorError(null);
              let hasErr = false;
              if (!nome.trim()) { setNomeError('Nome obrigatório.'); hasErr = true; }
              if (!CATEGORY_COLORS.includes(corSelecionada)) { setCorError('Selecione uma cor.'); hasErr = true; }
              if (hasErr) return;
              criarMutation.mutate({ nome: nome.trim(), cor: corSelecionada });
            }}>
              <Text style={{ color: criarMutation.status === 'pending' ? colors.textMuted : colors.brandFg, fontSize: 15, fontWeight: '600' }}>Salvar</Text>
            </TouchableOpacity>
          </View>
          <ScrollView contentContainerStyle={{ padding: 16 }}>
            <Field label="Nome" value={nome} onChangeText={setNome} placeholder="Ex: Alimentação" error={nomeError} />

            <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Cor</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 12, marginBottom: 8 }}>
              {CATEGORY_COLORS.map((cor, i) => (
                <TouchableOpacity
                  key={cor}
                  onPress={() => setCorSelecionada(cor)}
                  accessibilityRole="radio"
                  accessibilityState={{ selected: corSelecionada === cor }}
                  accessibilityLabel={`Cor ${i + 1}`}
                  hitSlop={{ top: 6, bottom: 6, left: 6, right: 6 }}
                  style={{ width: 32, height: 32, borderRadius: 16, backgroundColor: cor, borderWidth: corSelecionada === cor ? 3 : 0, borderColor: colors.textPrimary }}
                />
              ))}
            </View>
            {corError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{corError}</Text>}
          </ScrollView>
        </View>
      </Modal>
    </View>
  );
}
