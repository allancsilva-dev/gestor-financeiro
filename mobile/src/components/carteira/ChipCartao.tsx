import React from 'react';
import { View } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';

/**
 * Chip EMV dourado. Desenhado em View: é um retângulo com quatro traços, não
 * vale o custo de um SVG.
 * Medida da referência: 50px ↔ 30dp de largura.
 */
export default function ChipCartao({ largura = 30 }: { largura?: number }) {
  const altura = Math.round(largura * 0.78);
  const traco = 'rgba(90,60,0,0.35)';
  return (
    <LinearGradient
      colors={['#f6dc94', '#d9b45f', '#b8913f']}
      start={{ x: 0, y: 0 }}
      end={{ x: 1, y: 1 }}
      style={{
        width: largura,
        height: altura,
        borderRadius: 5,
        borderWidth: 0.5,
        borderColor: 'rgba(120,85,10,0.45)',
        overflow: 'hidden',
        justifyContent: 'space-evenly',
        paddingVertical: 3,
      }}
    >
      {/* contatos horizontais */}
      {[0, 1, 2].map(i => (
        <View key={i} style={{ height: 1, backgroundColor: traco, marginHorizontal: 2 }} />
      ))}
      {/* barramento vertical central */}
      <View
        style={{
          position: 'absolute',
          left: largura / 2 - 0.5,
          top: 3,
          bottom: 3,
          width: 1,
          backgroundColor: traco,
        }}
      />
    </LinearGradient>
  );
}
