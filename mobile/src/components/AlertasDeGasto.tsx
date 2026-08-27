import React, { useCallback, useEffect, useState } from 'react';
import { Text, View } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import { useTheme, numeric, spacing, typography } from '../theme';
import insightsService from '../services/insightsService';
import { CategoriaAlerta, InsightsResponse } from '../types';
import { formatCurrency, formatPercent } from '../utils/format';
import { chaveDoAlerta, dispensarAlerta, listarDispensados } from '../store/alertasDispensados';
import Badge from './ui/Badge';
import Botao from './ui/Botao';
import Card from './ui/Card';
import EstadoVazio from './ui/EstadoVazio';
import SkeletonBox from './ui/SkeletonBox';

/**
 * Alertas de gasto: o que fugiu do padrão neste mês.
 *
 * O backend já calculava tudo isto e nenhuma tela consumia — `insightsService` era código morto.
 * Aqui o número vira frase: quanto saiu do padrão, em qual categoria, e o que dá para fazer.
 *
 * Dispensar é local e vale pela competência (ver `store/alertasDispensados`): o alerta some deste
 * mês e volta no mês que vem se o gasto continuar alto.
 */
export default function AlertasDeGasto() {
  const colors = useTheme();
  const [dispensados, setDispensados] = useState<string[]>([]);

  const { data, isLoading, isError } = useQuery<InsightsResponse>({
    queryKey: ['insights'],
    queryFn: () => insightsService.buscar(),
  });

  useEffect(() => {
    let ativo = true;
    listarDispensados().then((chaves) => {
      if (ativo) setDispensados(chaves);
    });
    return () => { ativo = false; };
  }, []);

  const dispensar = useCallback(async (categoriaNome: string) => {
    const proximos = await dispensarAlerta(chaveDoAlerta(categoriaNome));
    setDispensados(proximos);
  }, []);

  if (isLoading) {
    return (
      <Card>
        <SkeletonBox width="60%" height={20} />
        <View style={{ marginTop: spacing.md }}>
          <SkeletonBox width="100%" height={56} />
        </View>
      </Card>
    );
  }

  // Erro aqui não é tela vazia: o resto do relatório continua útil, então o bloco
  // some em silêncio em vez de gritar uma falha que não bloqueia nada.
  if (isError || !data) return null;

  const visiveis = (data.categoriasAlerta ?? [])
    .filter((alerta) => alerta.acimaMedia)
    .filter((alerta) => !dispensados.includes(chaveDoAlerta(alerta.categoriaNome)));

  // A recomendação é texto livre do backend. Quando ela cita uma categoria que o usuário acabou
  // de dispensar, some junto: manter "Mercado subiu 80%" logo abaixo do alerta dispensado de
  // Mercado seria dizer a mesma coisa duas vezes depois de o usuário pedir para parar.
  const dispensadasAgora = (data.categoriasAlerta ?? [])
    .filter((alerta) => dispensados.includes(chaveDoAlerta(alerta.categoriaNome)))
    .map((alerta) => alerta.categoriaNome.trim().toLowerCase());
  const recomendacoes = (data.recomendacoes ?? []).filter((texto) => {
    const normalizado = texto.toLowerCase();
    return !dispensadasAgora.some((categoria) => normalizado.includes(categoria));
  });

  const previsaoNegativa = data.previsaoSaldoFinal < 0;
  const nadaAAvisar = visiveis.length === 0 && !previsaoNegativa && recomendacoes.length === 0;

  return (
    <Card>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>Alertas</Text>
        {visiveis.length > 0 && <Badge tone="warning">{String(visiveis.length)}</Badge>}
      </View>

      {data.resumo ? (
        <Text style={{ ...typography.body, color: colors.textMuted, marginTop: spacing.xs }}>
          {data.resumo}
        </Text>
      ) : null}

      {nadaAAvisar ? (
        <EstadoVazio
          compacto
          emoji="🌤️"
          titulo="Nada fora do padrão"
          texto="Seus gastos deste mês estão na média dos últimos três."
        />
      ) : null}

      {previsaoNegativa && (
        <View
          style={{
            marginTop: spacing.md,
            padding: spacing.md,
            borderRadius: spacing.md,
            backgroundColor: colors.dangerBg,
          }}
        >
          <Text style={{ ...typography.rowTitle, color: colors.danger }}>
            No ritmo atual, o mês fecha negativo
          </Text>
          <Text style={{ ...typography.meta, ...numeric, color: colors.danger }}>
            Previsão de saldo: {formatCurrency(data.previsaoSaldoFinal)}
          </Text>
        </View>
      )}

      {visiveis.map((alerta) => (
        <LinhaDeAlerta
          key={alerta.categoriaNome}
          alerta={alerta}
          onDispensar={() => dispensar(alerta.categoriaNome)}
        />
      ))}

      {recomendacoes.length > 0 && (
        <View style={{ marginTop: spacing.lg, gap: spacing.xs }}>
          {recomendacoes.map((recomendacao) => (
            <Text key={recomendacao} style={{ ...typography.body, color: colors.textSecondary }}>
              • {recomendacao}
            </Text>
          ))}
        </View>
      )}
    </Card>
  );
}

function LinhaDeAlerta({
  alerta,
  onDispensar,
}: {
  alerta: CategoriaAlerta;
  onDispensar: () => void;
}) {
  const colors = useTheme();
  return (
    <View style={{ marginTop: spacing.md, gap: spacing.xs }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.md }}>
        <Text style={{ ...typography.rowTitle, color: colors.textPrimary, flex: 1 }} numberOfLines={1}>
          {alerta.categoriaNome}
        </Text>
        <Text style={{ ...typography.value, ...numeric, color: colors.warning }}>
          +{formatPercent(alerta.variacaoPercentual, 0)}
        </Text>
      </View>

      <Text style={{ ...typography.meta, ...numeric, color: colors.textMuted }}>
        {formatCurrency(alerta.gastoAtual)} neste mês · média {formatCurrency(alerta.gastoMedio)}
      </Text>

      <View style={{ alignSelf: 'flex-start' }}>
        <Botao
          titulo="Ok, entendi"
          variante="texto"
          tamanho="pill"
          onPress={onDispensar}
          accessibilityLabel={`Dispensar alerta de ${alerta.categoriaNome} até o mês que vem`}
        />
      </View>
    </View>
  );
}
