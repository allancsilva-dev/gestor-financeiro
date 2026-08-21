import React, { useState } from 'react';
import { View, StyleSheet, LayoutChangeEvent, StyleProp, ViewStyle } from 'react-native';
import Svg, { Defs, RadialGradient, Rect, Stop } from 'react-native-svg';
import { useTheme } from '../../theme';

interface Props {
  /** Sufixo dos ids do SVG — precisa ser único por superfície na mesma tela. */
  id: string | number;
  /** Brilho do topo-direita. */
  tintaTopo: string;
  /** Brilho da base-esquerda. */
  tintaBase: string;
  borderRadius?: number;
  style?: StyleProp<ViewStyle>;
  children?: React.ReactNode;
}

/**
 * Superfície do card de meta: base neutra (`colors.card`) com dois brilhos
 * radiais — topo-direita e base-esquerda, centro neutro. A medição em
 * `.design/MEDICOES-metas.md` mostra que não é gradiente linear.
 *
 * O tamanho do `Svg` vem do `onLayout`, não de `width="100%"`: a porcentagem
 * resolve contra a caixa de padding e deixa uma costura vertical na borda.
 */
export default function SuperficieComBrilho({
  id, tintaTopo, tintaBase, borderRadius = 20, style, children,
}: Props) {
  const colors = useTheme();
  const [caixa, setCaixa] = useState({ largura: 0, altura: 0 });
  const medir = (ev: LayoutChangeEvent) =>
    setCaixa({ largura: ev.nativeEvent.layout.width, altura: ev.nativeEvent.layout.height });

  return (
    <View
      onLayout={medir}
      style={[{ backgroundColor: colors.card, borderRadius, overflow: 'hidden' }, style]}
    >
      <Svg
        width={caixa.largura}
        height={caixa.altura}
        style={StyleSheet.absoluteFill}
        pointerEvents="none"
      >
        <Defs>
          <RadialGradient id={`topo-${id}`} cx="92%" cy="4%" rx="62%" ry="82%">
            <Stop offset="0" stopColor={tintaTopo} stopOpacity="1" />
            <Stop offset="1" stopColor={tintaTopo} stopOpacity="0" />
          </RadialGradient>
          <RadialGradient id={`base-${id}`} cx="4%" cy="100%" rx="55%" ry="72%">
            <Stop offset="0" stopColor={tintaBase} stopOpacity="1" />
            <Stop offset="1" stopColor={tintaBase} stopOpacity="0" />
          </RadialGradient>
        </Defs>
        <Rect x="0" y="0" width={caixa.largura} height={caixa.altura} fill={`url(#topo-${id})`} />
        <Rect x="0" y="0" width={caixa.largura} height={caixa.altura} fill={`url(#base-${id})`} />
      </Svg>
      {children}
    </View>
  );
}
