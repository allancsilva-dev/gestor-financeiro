import React from 'react';
import { Text, View } from 'react-native';

/**
 * Marca da bandeira no canto do cartão. Mastercard são dois círculos
 * sobrepostos — desenháveis com borderRadius; as demais são wordmark, que é
 * como aparecem no plástico. Sem asset de terceiro embarcado.
 */
export default function BandeiraMarca({
  bandeira,
  tinta,
  escala = 1,
}: {
  bandeira?: string | null;
  tinta: string;
  escala?: number;
}) {
  if (!bandeira) return null;
  const chave = bandeira.toUpperCase();

  if (chave === 'MASTERCARD') {
    const d = Math.round(24 * escala);
    return (
      <View style={{ flexDirection: 'row', width: d * 1.62, height: d, alignItems: 'center' }}>
        <View style={{ width: d, height: d, borderRadius: d / 2, backgroundColor: '#EB001B' }} />
        <View
          style={{
            width: d,
            height: d,
            borderRadius: d / 2,
            backgroundColor: '#F79E1B',
            marginLeft: -d * 0.38,
            opacity: 0.92,
          }}
        />
      </View>
    );
  }

  const wordmark: Record<string, { texto: string; italico?: boolean; tamanho: number }> = {
    VISA: { texto: 'VISA', italico: true, tamanho: 18 },
    ELO: { texto: 'elo', tamanho: 18 },
    AMEX: { texto: 'AMEX', tamanho: 14 },
    HIPERCARD: { texto: 'Hipercard', tamanho: 12 },
    OUTRA: { texto: '', tamanho: 0 },
  };
  const marca = wordmark[chave];
  if (!marca || !marca.texto) return null;

  return (
    <Text
      style={{
        color: tinta,
        fontSize: Math.round(marca.tamanho * escala),
        fontWeight: '800',
        letterSpacing: 0.5,
        fontStyle: marca.italico ? 'italic' : 'normal',
      }}
    >
      {marca.texto}
    </Text>
  );
}
