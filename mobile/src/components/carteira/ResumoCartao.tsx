import React from 'react';
import { Text, View } from 'react-native';
import { useTheme, spacing, typography, numeric } from '../../theme';
import ProgressBar from '../ui/ProgressBar';
import { CarteiraCartao } from '../../types';
import { formatCurrency } from '../../utils/format';
import { dataCurta, dataLonga, prazoEmDias } from '../../domain/carteiraFormat';
import { identidadeDoCartao } from '../../domain/emissores';

const Linha = ({ children }: { children: React.ReactNode }) => (
  <View style={{
    flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between',
    paddingHorizontal: spacing.md, gap: spacing.md,
  }}>
    {children}
  </View>
);

/**
 * Painel de resumo do cartão selecionado.
 *
 * Sem fundo próprio de propósito: a medição da referência
 * (mobile/.design/MEDICOES-carteira.md) mostra `colors.bg` puro sob o bloco —
 * o que separa as linhas são as hairlines, não um card.
 */
export default function ResumoCartao({ cartao }: { cartao: CarteiraCartao }) {
  const colors = useTheme();
  const identidade = identidadeDoCartao(cartao);
  const temCredito = cartao.creditoAFavor > 0;
  const venceHoje = cartao.diasParaVencimento;

  const divisor = (
    <View style={{ height: 1, backgroundColor: colors.border, marginVertical: 14 }} />
  );

  return (
    <View style={{ marginHorizontal: spacing.lg }}>
      <Linha>
        <View style={{ flex: 1 }}>
          <Text style={{ ...typography.body, color: colors.textSecondary }}>
            {temCredito ? 'Crédito a favor' : 'Em aberto'}
          </Text>
          <Text
            numberOfLines={1}
            adjustsFontSizeToFit
            minimumFontScale={0.7}
            style={{
              ...numeric,
              color: temCredito ? colors.success : colors.danger,
              fontSize: 26, lineHeight: 32, fontWeight: '800', letterSpacing: -0.6, marginTop: 2,
            }}
          >
            {formatCurrency(temCredito ? cartao.creditoAFavor : cartao.emAberto)}
          </Text>
        </View>
        <View style={{ alignItems: 'flex-end' }}>
          <Text style={{ ...typography.value, ...numeric, color: colors.textPrimary }}>
            {dataCurta(cartao.dataVencimentoAtual)}
          </Text>
          <Text style={{ ...typography.body, color: colors.textSecondary, marginTop: 2 }}>
            {prazoEmDias(venceHoje)}
          </Text>
        </View>
      </Linha>

      {divisor}

      <Linha>
        <View style={{ flex: 1 }}>
          <Text style={{ ...typography.body, color: colors.textSecondary }}>Limite disponível</Text>
          <Text
            numberOfLines={1}
            adjustsFontSizeToFit
            minimumFontScale={0.7}
            style={{
              ...numeric, color: colors.textPrimary,
              fontSize: 22, lineHeight: 28, fontWeight: '800', letterSpacing: -0.5, marginTop: 2,
            }}
          >
            {formatCurrency(cartao.limiteDisponivel)}
          </Text>
        </View>
        <View style={{ alignItems: 'flex-end' }}>
          <Text style={{ ...typography.value, ...numeric, color: colors.textPrimary }}>
            {cartao.percentualUso}%
          </Text>
          <Text style={{ ...typography.meta, ...numeric, color: colors.textMuted, marginTop: 3 }}>
            de {formatCurrency(cartao.limiteTotal)}
          </Text>
        </View>
      </Linha>

      {cartao.limiteTotal > 0 && (
        <View style={{ marginTop: spacing.md, marginHorizontal: spacing.md }}>
          {/* Preenchimento na cor do emissor, não na marca do app: é o que a
              referência mostra (magenta no cartão roxo, não ciano). */}
          <ProgressBar
            value={cartao.percentualUso}
            accessibilityLabel={`Limite usado: ${cartao.percentualUso} por cento`}
            paleta={{ trilha: colors.trilha, fillDe: identidade.from }}
          />
        </View>
      )}

      {divisor}

      <Linha>
        <Text style={{ ...typography.body, color: colors.textSecondary, flex: 1 }}>
          Melhor dia de compra
        </Text>
        <Text style={{ ...typography.value, color: colors.textPrimary, textAlign: 'right' }}>
          {dataLonga(cartao.melhorDiaCompra)} · {prazoEmDias(cartao.diasParaMelhorDia)}
        </Text>
      </Linha>
    </View>
  );
}
