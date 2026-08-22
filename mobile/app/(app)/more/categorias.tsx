import React, { useState } from 'react';
import { View, Text, TouchableOpacity, FlatList, ScrollView } from 'react-native';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { categoriaService } from '../../../src/services/categoriaService';
import { CATEGORY_COLORS, NOME_DA_COR, formatCurrency } from '../../../src/utils/format';
import { camposDeErro, mensagemDeErro } from '../../../src/utils/erros';
import { CategoriaRequest } from '../../../src/types';
import {
  useTheme, useTabBarSpace, numeric, radius, screenPadding, spacing, typography,
} from '../../../src/theme';
import Badge from '../../../src/components/ui/Badge';
import CabecalhoSubTela from '../../../src/components/ui/CabecalhoSubTela';
import EstadoVazio from '../../../src/components/ui/EstadoVazio';
import Fab from '../../../src/components/ui/Fab';
import Field from '../../../src/components/ui/Field';
import FolhaModal from '../../../src/components/ui/FolhaModal';
import RotuloDeGrupo from '../../../src/components/ui/RotuloDeGrupo';
import SkeletonBox from '../../../src/components/ui/SkeletonBox';

type CampoDaCategoria = 'nome' | 'cor';

const MAPA_DE_CAMPOS: Record<string, CampoDaCategoria> = { nome: 'nome', cor: 'cor' };

export default function CategoriasScreen() {
  const colors = useTheme();
  const tabBarSpace = useTabBarSpace();
  const queryClient = useQueryClient();
  const [modalVisible, setModalVisible] = useState(false);
  const [nome, setNome] = useState('');
  const [corSelecionada, setCorSelecionada] = useState(CATEGORY_COLORS[0]);
  const [erros, setErros] = useState<Partial<Record<CampoDaCategoria, string>>>({});
  const [erroGeral, setErroGeral] = useState<string | null>(null);

  const { data: categorias = [], isLoading, isError, refetch } = useQuery({
    queryKey: ['categorias'],
    queryFn: () => categoriaService.listar(),
  });

  const fecharFormulario = () => {
    setModalVisible(false);
    setNome('');
    setCorSelecionada(CATEGORY_COLORS[0]);
    setErros({});
    setErroGeral(null);
  };

  const criarMutation = useMutation({
    mutationFn: (req: CategoriaRequest) => categoriaService.criar(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categorias'] });
      fecharFormulario();
    },
    onError: (err: unknown) => {
      setErros(camposDeErro(err, MAPA_DE_CAMPOS));
      setErroGeral(mensagemDeErro(err, 'Erro ao criar categoria.'));
    },
  });

  const salvar = () => {
    setErros({}); setErroGeral(null);
    const local: Partial<Record<CampoDaCategoria, string>> = {};
    if (!nome.trim()) local.nome = 'Nome obrigatório.';
    if (!CATEGORY_COLORS.includes(corSelecionada)) local.cor = 'Selecione uma cor.';
    if (Object.keys(local).length > 0) { setErros(local); return; }
    criarMutation.mutate({ nome: nome.trim(), cor: corSelecionada });
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <CabecalhoSubTela
        titulo="Categorias"
        apoio={<Text style={{ ...typography.body, color: colors.textSecondary }}>Organize seus gastos por tipo</Text>}
      />

      {isLoading ? (
        <View style={{ paddingHorizontal: screenPadding, gap: spacing.sm }}>
          {[1, 2, 3].map(i => <SkeletonBox key={i} width="100%" height={56} borderRadius={radius.md} />)}
        </View>
      ) : isError ? (
        <EstadoVazio
          emoji="📶"
          titulo="Não deu para carregar suas categorias"
          texto="Verifique sua conexão e tente de novo."
          acao={{ rotulo: 'Tentar de novo', onPress: () => refetch() }}
        />
      ) : (
        <FlatList
          data={categorias}
          contentContainerStyle={{ paddingBottom: tabBarSpace }}
          keyExtractor={item => item.id.toString()}
          renderItem={({ item: cat }) => (
            <View style={{
              minHeight: 56, flexDirection: 'row', alignItems: 'center', gap: spacing.md,
              paddingHorizontal: screenPadding,
              borderBottomWidth: 1, borderBottomColor: colors.border,
            }}>
              <View style={{
                width: 12, height: 12, borderRadius: radius.pill,
                backgroundColor: cat.cor ?? colors.textMuted,
              }} />
              <Text numberOfLines={1} style={{ ...typography.body, color: colors.textPrimary, flex: 1 }}>{cat.nome}</Text>
              <Text style={{ ...typography.meta, ...numeric, color: colors.textSecondary }}>
                {formatCurrency(Number(cat.valorGasto ?? 0))}
              </Text>
              {!cat.ativo && <Badge tone="info">Inativo</Badge>}
            </View>
          )}
          ListEmptyComponent={() => (
            <EstadoVazio
              emoji="🏷️"
              titulo="Nenhuma categoria encontrada"
              texto="Categorias organizam seus gastos e alimentam os relatórios."
              acao={{ rotulo: 'Criar categoria', onPress: () => setModalVisible(true) }}
            />
          )}
        />
      )}

      <Fab onPress={() => setModalVisible(true)} accessibilityLabel="Nova categoria" />

      <FolhaModal
        visible={modalVisible}
        titulo="Nova Categoria"
        onFechar={fecharFormulario}
        acao={{ rotulo: 'Salvar', onPress: salvar, carregando: criarMutation.status === 'pending' }}
      >
        <ScrollView contentContainerStyle={{ padding: screenPadding }} keyboardShouldPersistTaps="handled">
          <Field
            testID="category-name"
            label="Nome"
            value={nome}
            onChangeText={setNome}
            placeholder="Ex: Alimentação"
            error={erros.nome}
          />

          <RotuloDeGrupo>Cor</RotuloDeGrupo>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md, marginBottom: spacing.sm }}>
            {CATEGORY_COLORS.map(cor => (
              <TouchableOpacity
                key={cor}
                onPress={() => setCorSelecionada(cor)}
                accessibilityRole="radio"
                accessibilityState={{ selected: corSelecionada === cor }}
                // O nome da cor, não a posição dela na paleta.
                accessibilityLabel={NOME_DA_COR[cor] ?? cor}
                hitSlop={{ top: 6, bottom: 6, left: 6, right: 6 }}
                style={{
                  width: 44, height: 44, borderRadius: radius.pill, backgroundColor: cor,
                  borderWidth: corSelecionada === cor ? 3 : 0, borderColor: colors.textPrimary,
                }}
              />
            ))}
          </View>
          {!!erros.cor && (
            <Text accessibilityRole="alert" style={{ ...typography.meta, color: colors.danger, marginBottom: spacing.sm }}>
              {erros.cor}
            </Text>
          )}

          {!!erroGeral && (
            <Text accessibilityRole="alert" accessibilityLiveRegion="polite" style={{ ...typography.meta, color: colors.danger }}>
              {erroGeral}
            </Text>
          )}
        </ScrollView>
      </FolhaModal>
    </View>
  );
}
