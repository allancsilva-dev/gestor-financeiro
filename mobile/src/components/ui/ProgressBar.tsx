import React from 'react';
import { View } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { useTheme, radius } from '../../theme';
import { misturar } from '../../theme/metaCores';

interface ProgressBarProps {
  value: number; // 0–100
  height?: number;
  /**
   * Cores da entidade dona da barra (meta, cartão, orçamento). Sem isto a barra
   * usa a marca — que é o certo para progresso genérico e o errado para uma
   * entidade que já carrega cor própria.
   */
  paleta?: { trilha: string; fillDe: string; fillPara?: string };
  /** Rótulo do que a barra mede, para o leitor de tela. */
  accessibilityLabel?: string;
}

/**
 * A barra de progresso do app — uma só.
 *
 * Antes existiam três: esta (trilha `colors.border`, preenchimento sólido),
 * a de `ResumoCartao` e a de `CardMeta`. A trilha em `colors.border` era o bug
 * que `src/__tests__/tema.test.ts` documenta: fina demais, sumia no tema claro.
 * Agora a trilha é `colors.trilha` (travada em teste) e o preenchimento é
 * gradiente — sólido puro fica opaco demais (DESIGN.md:83-84).
 */
export default function ProgressBar({ value, height = 6, paleta, accessibilityLabel }: ProgressBarProps) {
  const colors = useTheme();
  const pct = Math.max(0, Math.min(100, value));
  const completa = pct >= 100;

  const trilha = paleta?.trilha ?? colors.trilha;
  const de = paleta?.fillDe ?? (completa ? colors.success : colors.brand);
  // Sem a segunda parada, clareia a primeira: quem tem só a cor da entidade
  // não precisa saber derivar gradiente — nem carregar um hex na tela.
  const para = paleta?.fillPara ?? misturar(de, '#ffffff', 0.35);

  return (
    <View
      accessibilityRole="progressbar"
      accessibilityLabel={accessibilityLabel}
      accessibilityValue={{ min: 0, max: 100, now: Math.round(pct) }}
      style={{ height, borderRadius: radius.pill, backgroundColor: trilha, overflow: 'hidden' }}
    >
      <LinearGradient
        colors={[de, para]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 0 }}
        style={{ width: `${pct}%`, height: '100%', borderRadius: radius.pill }}
      />
    </View>
  );
}
