import React, { useState } from 'react';
import { View, Text, ScrollView, TextInput } from 'react-native';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  useTheme, useTabBarSpace, numeric, radius, screenPadding, spacing, typography,
} from '../../../src/theme';
import { orcamentoService } from '../../../src/services/orcamentoService';
import { categoriaService } from '../../../src/services/categoriaService';
import { AppColors } from '../../../src/theme/colors';
import { OrcamentoResponse, OrcamentoCategoriaItem, PoliticaRollover } from '../../../src/types';
import { competenciaAtual, rotuloDeCompetencia, somarMeses } from '../../../src/domain/periodo';
import { mensagemDeErro, statusDoErro } from '../../../src/utils/erros';
import { formatCurrency, formatNumber, parseCurrencyBR, maskCurrencyInput } from '../../../src/utils/format';
import Botao from '../../../src/components/ui/Botao';
import CabecalhoSubTela from '../../../src/components/ui/CabecalhoSubTela';
import Card from '../../../src/components/ui/Card';
import Chip from '../../../src/components/ui/Chip';
import EstadoVazio from '../../../src/components/ui/EstadoVazio';
import NavegadorDeMes from '../../../src/components/ui/NavegadorDeMes';
import ProgressBar from '../../../src/components/ui/ProgressBar';
import SkeletonBox from '../../../src/components/ui/SkeletonBox';
import { emojiDaCategoria } from '../../../src/domain/iconeCategoria';

/**
 * O que fazer com o que sobra ou falta no fim do mês. O rótulo fala do dinheiro, não da regra:
 * "Sobra passa" é mais claro que SURPLUS_ONLY para quem só quer saber se pode gastar depois.
 */
const POLITICAS: Array<{ valor: PoliticaRollover; rotulo: string }> = [
  { valor: 'NONE', rotulo: 'Recomeça' },
  { valor: 'SURPLUS_ONLY', rotulo: 'Sobra passa' },
  { valor: 'DEFICIT_ONLY', rotulo: 'Excesso passa' },
  { valor: 'BOTH', rotulo: 'Os dois passam' },
];

/** Verde até 75%, âmbar até 100%, vermelho depois. */
function corDoProgresso(percentual: number, colors: AppColors): string {
  if (percentual >= 100) return colors.danger;
  if (percentual >= 75) return colors.warning;
  return colors.success;
}

export default function OrcamentoScreen() {
  const colors = useTheme();
  const tabBarSpace = useTabBarSpace();
  const queryClient = useQueryClient();
  const hoje = competenciaAtual();
  const [mes, setMes] = useState(hoje.mes);
  const [ano, setAno] = useState(hoje.ano);
  const [editando, setEditando] = useState(false);
  const [limites, setLimites] = useState<Map<number, string>>(new Map());
  const [politicas, setPoliticas] = useState<Map<number, PoliticaRollover>>(new Map());
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const ehMesCorrente = mes === hoje.mes && ano === hoje.ano;

  const { data, isLoading, isError, refetch } = useQuery<OrcamentoResponse | null>({
    queryKey: ['orcamento', mes, ano],
    queryFn: async () => {
      if (ehMesCorrente) return orcamentoService.buscarAtual();
      try {
        return await orcamentoService.buscarPorMes(mes, ano);
      } catch (err) {
        // 404 é resposta, não falha: aquele mês não tem orçamento. Qualquer
        // outro erro precisa subir — engolir tudo fazia falha de rede virar
        // "Nenhum orçamento para março", com o botão de criar em cima de um
        // orçamento que existe e não carregou.
        if (statusDoErro(err) === 404) return null;
        throw err;
      }
    },
  });

  const { data: categorias = [] } = useQuery({
    queryKey: ['categorias-orcamento'],
    queryFn: () => categoriaService.listar(),
  });

  const iniciarEdicao = () => {
    const map = new Map<number, string>();
    const politicaAtual = new Map<number, PoliticaRollover>();
    data?.categorias?.forEach((c) => {
      map.set(c.categoriaId, formatNumber(Number(c.valorLimite ?? 0)));
      politicaAtual.set(c.categoriaId, c.politicaRollover ?? 'NONE');
    });
    categorias.forEach((c) => {
      if (c.id && !map.has(c.id)) map.set(c.id, '');
      if (c.id && !politicaAtual.has(c.id)) politicaAtual.set(c.id, 'NONE');
    });
    setLimites(map);
    setPoliticas(politicaAtual);
    setSaveError(null);
    setEditando(true);
  };

  const salvar = async () => {
    const cats = Array.from(limites.entries())
      .filter(([, v]) => parseCurrencyBR(v) > 0)
      .map(([categoriaId, valorLimite]) => ({
        categoriaId,
        valorLimite: parseCurrencyBR(valorLimite),
        politicaRollover: politicas.get(categoriaId) ?? 'NONE',
      }));

    // Antes isto era um `return` mudo: o usuário tocava em Salvar e nada
    // acontecia, sem nenhuma pista do porquê.
    if (cats.length === 0) {
      setSaveError('Defina o limite de pelo menos uma categoria antes de salvar.');
      return;
    }
    setSaving(true);
    setSaveError(null);
    try {
      await orcamentoService.criarOuAtualizar({ mes, ano, categorias: cats });
      queryClient.invalidateQueries({ queryKey: ['orcamento'] });
      setEditando(false);
    } catch (err) {
      setSaveError(mensagemDeErro(err, 'Não foi possível salvar o orçamento. Tente novamente.'));
    } finally {
      setSaving(false);
    }
  };

  const mudarMes = (delta: number) => {
    const novo = somarMeses(new Date(ano, mes - 1, 1), delta);
    setMes(novo.getMonth() + 1);
    setAno(novo.getFullYear());
  };

  const disponivel = (data?.valorTotalPlanejado ?? 0) - (data?.valorTotalGasto ?? 0);

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.bg }}
      contentContainerStyle={{ paddingBottom: tabBarSpace }}
      keyboardShouldPersistTaps="handled"
    >
      <CabecalhoSubTela
        titulo="Orçamentos"
        apoio={<Text style={{ ...typography.body, color: colors.textSecondary }}>Limites de gasto por categoria</Text>}
      />

      <View style={{ paddingHorizontal: screenPadding }}>
        <NavegadorDeMes
          rotulo={rotuloDeCompetencia(mes, ano)}
          onAnterior={() => mudarMes(-1)}
          onProximo={() => mudarMes(1)}
        />
      </View>

      <View style={{ paddingHorizontal: screenPadding, marginTop: spacing.lg }}>
        {isLoading ? (
          <View style={{ gap: spacing.md }}>
            <SkeletonBox width="100%" height={72} borderRadius={radius.lg} />
            <SkeletonBox width="100%" height={140} borderRadius={radius.lg} />
          </View>
        ) : isError ? (
          <EstadoVazio
            emoji="📶"
            titulo="Não deu para carregar o orçamento"
            texto="Verifique sua conexão e tente de novo. Criar um orçamento agora poderia duplicar o que já existe."
            acao={{ rotulo: 'Tentar de novo', onPress: () => refetch() }}
          />
        ) : editando ? (
          <View style={{ gap: spacing.md }}>
            <Text style={{ ...typography.body, color: colors.textSecondary }}>Defina limites por categoria:</Text>
            {categorias.map((cat) => (
              <View
                key={cat.id}
                style={{
                  flexDirection: 'row', alignItems: 'center', gap: spacing.sm,
                  backgroundColor: colors.card, borderColor: colors.border,
                  borderWidth: 1, borderRadius: radius.sm, padding: spacing.sm,
                }}
              >
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm }}>
                <Text style={{ ...typography.cardTitle, width: 28 }}>{emojiDaCategoria(cat, '📌')}</Text>
                <Text numberOfLines={1} style={{ flex: 1, ...typography.body, color: colors.textPrimary }}>{cat.nome}</Text>
                <TextInput
                  accessibilityLabel={`Limite para ${cat.nome}`}
                  value={limites.get(cat.id!) || ''}
                  onChangeText={(t) => { const n = new Map(limites); n.set(cat.id!, maskCurrencyInput(t)); setLimites(n); }}
                  keyboardType="number-pad"
                  placeholder="0,00"
                  placeholderTextColor={colors.textMuted}
                  style={{
                    width: 100, textAlign: 'right',
                    backgroundColor: colors.fieldBg, borderColor: colors.border,
                    borderWidth: 1, borderRadius: radius.sm, padding: spacing.sm,
                    color: colors.textPrimary, ...typography.input, ...numeric,
                  }}
                />
                </View>
                <ScrollView
                  horizontal
                  showsHorizontalScrollIndicator={false}
                  contentContainerStyle={{ gap: spacing.xs, paddingTop: spacing.sm }}
                >
                  {POLITICAS.map((opcao) => (
                    <Chip
                      key={opcao.valor}
                      label={opcao.rotulo}
                      selected={(politicas.get(cat.id!) ?? 'NONE') === opcao.valor}
                      onPress={() => {
                        const proximas = new Map(politicas);
                        proximas.set(cat.id!, opcao.valor);
                        setPoliticas(proximas);
                      }}
                    />
                  ))}
                </ScrollView>
              </View>
            ))}
            <Text style={{ ...typography.meta, color: colors.textMuted }}>
              No fim do mês, sobra e excesso podem passar para o mês seguinte. A escolha vale a
              partir do próximo fechamento — mês já fechado não muda.
            </Text>
            {saveError && (
              <Text accessibilityRole="alert" accessibilityLiveRegion="polite" style={{ ...typography.meta, color: colors.danger }}>
                {saveError}
              </Text>
            )}
            <View style={{ flexDirection: 'row', gap: spacing.sm, marginTop: spacing.sm }}>
              <Botao
                titulo="Cancelar"
                variante="secundario"
                onPress={() => { setEditando(false); setSaveError(null); }}
              />
              <Botao titulo="Salvar" onPress={salvar} carregando={saving} style={{ flex: 1 }} />
            </View>
          </View>
        ) : !data?.categorias?.length ? (
          <EstadoVazio
            emoji="📊"
            titulo={`Nenhum orçamento para ${rotuloDeCompetencia(mes, ano)}`}
            texto="Defina quanto pode gastar em cada categoria e acompanhe o consumo no mês."
            acao={{ rotulo: 'Criar orçamento', onPress: iniciarEdicao }}
          />
        ) : (
          <View style={{ gap: spacing.lg }}>
            <Card>
              <View style={{ flexDirection: 'row', gap: spacing.sm }}>
                <Kpi rotulo="Planejado" valor={data.valorTotalPlanejado} cor={colors.textPrimary} />
                <Kpi rotulo="Gasto" valor={data.valorTotalGasto} cor={colors.danger} />
                <Kpi rotulo="Disponível" valor={disponivel} cor={disponivel >= 0 ? colors.success : colors.danger} />
              </View>
            </Card>

            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
              <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>Categorias</Text>
              <Botao titulo="Editar" variante="texto" tamanho="pill" onPress={iniciarEdicao} dica="Ajusta os limites do mês" />
            </View>

            {data.categorias.map((cat: OrcamentoCategoriaItem) => {
              const cor = corDoProgresso(cat.percentualGasto, colors);
              return (
                <View key={cat.id} style={{ gap: spacing.sm }}>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: spacing.sm }}>
                    <View style={{ flexDirection: 'row', gap: spacing.xs + 2, alignItems: 'center', flex: 1, minWidth: 0 }}>
                      <Text style={{ ...typography.cardTitle }}>{cat.categoriaIcone || '📌'}</Text>
                      <Text numberOfLines={1} style={{ ...typography.body, color: colors.textPrimary, flex: 1 }}>
                        {cat.categoriaNome}
                      </Text>
                    </View>
                    <Text style={{ ...typography.meta, ...numeric, fontWeight: '600', color: cor }}>
                      {formatCurrency(cat.valorGasto)} / {formatCurrency(cat.valorDisponivel ?? cat.valorLimite)}
                    </Text>
                  </View>
                  {!!cat.carryIn && (
                    <Text style={{ ...typography.meta, ...numeric, color: colors.textMuted }}>
                      {formatCurrency(cat.valorLimite)} do mês
                      {cat.carryIn > 0
                        ? ` + ${formatCurrency(cat.carryIn)} que sobraram`
                        : ` − ${formatCurrency(Math.abs(cat.carryIn))} que estouraram`} no mês passado
                    </Text>
                  )}
                  <ProgressBar
                    value={Math.min(cat.percentualGasto, 100)}
                    paleta={{ trilha: colors.trilha, fillDe: cor }}
                    accessibilityLabel={`${cat.categoriaNome}, ${Math.round(cat.percentualGasto)} por cento do limite`}
                  />
                </View>
              );
            })}
          </View>
        )}
      </View>
    </ScrollView>
  );
}

const Kpi = ({ rotulo, valor, cor }: { rotulo: string; valor: number; cor: string }) => {
  const colors = useTheme();
  return (
    <View style={{ flex: 1, alignItems: 'center' }}>
      <Text style={{ ...typography.meta, color: colors.textSecondary }}>{rotulo}</Text>
      <Text
        numberOfLines={1}
        adjustsFontSizeToFit
        minimumFontScale={0.7}
        style={{ ...typography.body, ...numeric, fontWeight: '700', color: cor, marginTop: spacing.xxs }}
      >
        {formatCurrency(valor)}
      </Text>
    </View>
  );
};
