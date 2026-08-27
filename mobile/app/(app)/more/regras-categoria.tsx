import React, { useState } from 'react';
import { Alert, ScrollView, Text, TextInput, View } from 'react-native';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  useTheme, useTabBarSpace, radius, screenPadding, spacing, typography,
} from '../../../src/theme';
import regraCategoriaService from '../../../src/services/regraCategoriaService';
import { categoriaService } from '../../../src/services/categoriaService';
import { RegraCategoria, TipoCasamentoRegra } from '../../../src/types';
import { mensagemDeErro } from '../../../src/utils/erros';
import Botao from '../../../src/components/ui/Botao';
import CabecalhoSubTela from '../../../src/components/ui/CabecalhoSubTela';
import Card from '../../../src/components/ui/Card';
import Chip from '../../../src/components/ui/Chip';
import EstadoVazio from '../../../src/components/ui/EstadoVazio';
import SkeletonBox from '../../../src/components/ui/SkeletonBox';

/**
 * Como o texto encosta na descrição. Os rótulos falam do que o usuário vê no extrato, não do
 * vocabulário do backend.
 */
const CASAMENTOS: Array<{ valor: TipoCasamentoRegra; rotulo: string }> = [
  { valor: 'CONTEM', rotulo: 'Contém' },
  { valor: 'COMECA_COM', rotulo: 'Começa com' },
  { valor: 'IGUAL', rotulo: 'Igual a' },
];

const explicacao = (regra: RegraCategoria): string => {
  const como = regra.tipoCasamento === 'IGUAL' ? 'for igual a'
    : regra.tipoCasamento === 'COMECA_COM' ? 'começar com' : 'tiver';
  const escopo = regra.tipoTransacao === 'ENTRADA' ? ' (só entradas)'
    : regra.tipoTransacao === 'SAIDA' ? ' (só saídas)' : '';
  return `Quando a descrição ${como} “${regra.padrao}”${escopo}`;
};

export default function RegrasCategoriaScreen() {
  const colors = useTheme();
  const tabBarSpace = useTabBarSpace();
  const queryClient = useQueryClient();

  const [padrao, setPadrao] = useState('');
  const [casamento, setCasamento] = useState<TipoCasamentoRegra>('CONTEM');
  const [categoriaId, setCategoriaId] = useState<number | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  const { data: regras = [], isLoading } = useQuery({
    queryKey: ['regras-categoria'],
    queryFn: () => regraCategoriaService.listar(),
  });

  const { data: categorias = [] } = useQuery({
    queryKey: ['categorias-regras'],
    queryFn: () => categoriaService.listar(),
  });

  const criar = useMutation({
    mutationFn: () => regraCategoriaService.criar({
      padrao: padrao.trim(),
      categoriaId: categoriaId as number,
      tipoCasamento: casamento,
    }),
    onSuccess: () => {
      setPadrao('');
      setCategoriaId(null);
      setErro(null);
      queryClient.invalidateQueries({ queryKey: ['regras-categoria'] });
    },
    onError: (err) => setErro(mensagemDeErro(err)),
  });

  const remover = useMutation({
    mutationFn: (id: number) => regraCategoriaService.remover(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['regras-categoria'] }),
    onError: (err) => setErro(mensagemDeErro(err)),
  });

  const confirmarRemocao = (regra: RegraCategoria) => {
    Alert.alert(
      'Apagar regra',
      `Lançamentos futuros deixam de ir para ${regra.categoriaNome} automaticamente. O que já foi lançado não muda.`,
      [
        { text: 'Cancelar', style: 'cancel' },
        { text: 'Apagar', style: 'destructive', onPress: () => remover.mutate(regra.id) },
      ],
    );
  };

  const podeSalvar = padrao.trim().length >= 2 && categoriaId != null && !criar.isPending;

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <CabecalhoSubTela
        titulo="Categorizar sozinho"
        apoio={
          <Text style={{ ...typography.body, color: colors.textMuted }}>
            Diga uma vez para onde vai cada tipo de lançamento e o app repete — inclusive no extrato
            importado.
          </Text>
        }
      />

      <ScrollView
        contentContainerStyle={{ padding: screenPadding, paddingBottom: tabBarSpace, gap: spacing.lg }}
        keyboardShouldPersistTaps="handled"
      >
        <Card>
          <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>Nova regra</Text>

          <View style={{ flexDirection: 'row', gap: spacing.xs, marginTop: spacing.md }}>
            {CASAMENTOS.map((opcao) => (
              <Chip
                key={opcao.valor}
                label={opcao.rotulo}
                selected={casamento === opcao.valor}
                onPress={() => setCasamento(opcao.valor)}
              />
            ))}
          </View>

          <TextInput
            accessibilityLabel="Texto que aparece na descrição"
            value={padrao}
            onChangeText={setPadrao}
            placeholder="mercado da esquina"
            placeholderTextColor={colors.textMuted}
            autoCapitalize="none"
            style={{
              marginTop: spacing.md,
              backgroundColor: colors.fieldBg,
              borderColor: colors.border,
              borderWidth: 1,
              borderRadius: radius.sm,
              padding: spacing.md,
              color: colors.textPrimary,
              ...typography.input,
            }}
          />

          <Text style={{ ...typography.meta, color: colors.textMuted, marginTop: spacing.sm }}>
            Vai para a categoria:
          </Text>
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={{ gap: spacing.xs, paddingTop: spacing.sm }}
          >
            {categorias.map((categoria) => (
              <Chip
                key={categoria.id}
                label={`${categoria.icone || '📌'} ${categoria.nome}`}
                selected={categoriaId === categoria.id}
                onPress={() => setCategoriaId(categoria.id ?? null)}
              />
            ))}
          </ScrollView>

          {erro && (
            <Text accessibilityRole="alert" style={{ ...typography.meta, color: colors.danger, marginTop: spacing.sm }}>
              {erro}
            </Text>
          )}

          <View style={{ marginTop: spacing.lg }}>
            <Botao
              titulo="Criar regra"
              onPress={() => criar.mutate()}
              carregando={criar.isPending}
              desabilitado={!podeSalvar}
              testID="regra-criar"
              dica={padrao.trim().length < 2 ? 'Escreva pelo menos dois caracteres' : undefined}
            />
          </View>
        </Card>

        <Card>
          <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>Suas regras</Text>
          <Text style={{ ...typography.meta, color: colors.textMuted, marginTop: spacing.xxs }}>
            Decidem de cima para baixo: a primeira que casar vence.
          </Text>

          {isLoading ? (
            <View style={{ gap: spacing.sm, marginTop: spacing.md }}>
              <SkeletonBox width="100%" height={52} />
              <SkeletonBox width="100%" height={52} />
            </View>
          ) : regras.length === 0 ? (
            <EstadoVazio
              compacto
              emoji="🏷️"
              titulo="Nenhuma regra ainda"
              texto="Sem regras, o app continua sugerindo pela sua própria história de lançamentos."
            />
          ) : (
            <View style={{ marginTop: spacing.md }}>
              {regras.map((regra) => (
                <View
                  key={regra.id}
                  style={{
                    paddingVertical: spacing.md,
                    borderTopWidth: 1,
                    borderTopColor: colors.border,
                    gap: spacing.xxs,
                  }}
                >
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
                    <Text style={{ ...typography.rowTitle, color: colors.textPrimary, flex: 1 }}>
                      {regra.categoriaIcone || '📌'} {regra.categoriaNome}
                    </Text>
                    <Botao
                      titulo="Apagar"
                      variante="texto"
                      tamanho="pill"
                      onPress={() => confirmarRemocao(regra)}
                      accessibilityLabel={`Apagar regra de ${regra.categoriaNome}`}
                    />
                  </View>
                  <Text style={{ ...typography.meta, color: colors.textMuted }}>{explicacao(regra)}</Text>
                </View>
              ))}
            </View>
          )}
        </Card>
      </ScrollView>
    </View>
  );
}
