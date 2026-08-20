import React from 'react';
import { View, Text, AccessibilityProps } from 'react-native';
import Svg, { Circle, Defs, LinearGradient, Stop } from 'react-native-svg';
import { PaletaMeta } from '../../theme/metaCores';
import { e } from '../../theme/escala';

interface Props extends AccessibilityProps {
  /** 0 a 100. */
  progresso: number;
  paleta: PaletaMeta;
  /** Emoji da meta, centrado no anel. */
  emoji: string;
  tamanho?: number;
  espessura?: number;
  /** Sufixo para os ids do SVG — precisa ser único por card na lista. */
  id: string | number;
}

/**
 * Anel de progresso da meta: trilha completa, arco na cor da meta partindo das 12h
 * no sentido horário e um halo largo por baixo do arco para o brilho da referência.
 */
export default function AnelProgresso({
  progresso, paleta, emoji, tamanho = e(66), espessura = e(5), id, ...a11y
}: Props) {
  const pct = Math.max(0, Math.min(100, progresso));
  const raio = (tamanho - espessura) / 2;
  const circunferencia = 2 * Math.PI * raio;
  const preenchido = (circunferencia * pct) / 100;
  const gradId = `anel-${id}`;

  const arco = (largura: number, opacidade: number) => (
    <Circle
      cx={tamanho / 2}
      cy={tamanho / 2}
      r={raio}
      stroke={`url(#${gradId})`}
      strokeWidth={largura}
      strokeOpacity={opacidade}
      strokeLinecap="round"
      strokeDasharray={`${preenchido} ${circunferencia}`}
      fill="none"
      // 12h como origem: o SVG começa às 3h
      transform={`rotate(-90 ${tamanho / 2} ${tamanho / 2})`}
    />
  );

  return (
    <View
      style={{ width: tamanho, height: tamanho, alignItems: 'center', justifyContent: 'center' }}
      accessibilityRole="progressbar"
      accessibilityValue={{ min: 0, max: 100, now: Math.round(pct) }}
      {...a11y}
    >
      <Svg width={tamanho} height={tamanho} style={{ position: 'absolute' }}>
        <Defs>
          <LinearGradient id={gradId} x1="0" y1="0" x2="1" y2="1">
            <Stop offset="0" stopColor={paleta.fillDe} />
            <Stop offset="1" stopColor={paleta.fillPara} />
          </LinearGradient>
        </Defs>
        <Circle
          cx={tamanho / 2}
          cy={tamanho / 2}
          r={raio}
          stroke={paleta.trilha}
          strokeWidth={espessura}
          fill="none"
        />
        {pct > 0 && arco(espessura * 1.8, 0.08)}
        {pct > 0 && arco(espessura, 1)}
      </Svg>
      <Text style={{ fontSize: e(22) }} allowFontScaling={false}>{emoji}</Text>
    </View>
  );
}
