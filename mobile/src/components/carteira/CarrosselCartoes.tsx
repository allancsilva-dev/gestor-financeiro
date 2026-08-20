import React, { useCallback, useMemo } from 'react';
import { NativeScrollEvent, NativeSyntheticEvent, ScrollView, useWindowDimensions, View } from 'react-native';
import { useTheme } from '../../theme';
import { CarteiraCartao } from '../../types';
import CartaoFisico, { CARTAO_RAZAO } from './CartaoFisico';
import SkeletonBox from '../ui/SkeletonBox';

const GAP = 10;
// A referência mede 287dp de cartão numa tela de 360dp — 0.797 da largura.
// O teto evita um cartão gigante em tablet.
const PROPORCAO_TELA = 0.797;
const LARGURA_MAX = 340;

export const useMedidasCartao = () => {
  const { width } = useWindowDimensions();
  const largura = Math.round(Math.min(width * PROPORCAO_TELA, LARGURA_MAX));
  return {
    largura,
    altura: Math.round(largura / CARTAO_RAZAO),
    // Padding centralizado: é o que faz o próximo cartão espiar na borda,
    // como na referência.
    lateral: Math.max(Math.round((width - largura) / 2), 16),
    passo: largura + GAP,
  };
};

interface Props {
  cartoes: CarteiraCartao[];
  indice: number;
  onIndice: (i: number) => void;
  titular?: string | null;
  carregando?: boolean;
}

export default function CarrosselCartoes({ cartoes, indice, onIndice, titular, carregando }: Props) {
  const colors = useTheme();
  const { largura, altura, lateral, passo } = useMedidasCartao();

  // snapToOffsets, não snapToInterval: com paddingHorizontal o interval
  // desalinha um pouco a cada item e o cartão nunca para centralizado.
  const offsets = useMemo(() => cartoes.map((_, i) => i * passo), [cartoes, passo]);

  const aoParar = useCallback(
    (e: NativeSyntheticEvent<NativeScrollEvent>) => {
      const i = Math.round(e.nativeEvent.contentOffset.x / passo);
      const limitado = Math.min(Math.max(i, 0), Math.max(cartoes.length - 1, 0));
      if (limitado !== indice) onIndice(limitado);
    },
    [passo, cartoes.length, indice, onIndice],
  );

  if (carregando) {
    return (
      <View style={{ flexDirection: 'row', gap: GAP, paddingHorizontal: lateral }}>
        <SkeletonBox width={largura} height={altura} borderRadius={18} />
        <SkeletonBox width={largura} height={altura} borderRadius={18} />
      </View>
    );
  }

  return (
    <View>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        decelerationRate="fast"
        snapToOffsets={offsets}
        disableIntervalMomentum
        onMomentumScrollEnd={aoParar}
        contentContainerStyle={{ gap: GAP, paddingHorizontal: lateral }}
      >
        {cartoes.map(c => (
          <CartaoFisico
            key={c.cartaoId}
            largura={largura}
            nome={c.nome}
            banco={c.banco}
            cor={c.cor}
            ultimosDigitos={c.ultimosDigitos}
            bandeira={c.bandeira}
            titular={titular}
          />
        ))}
      </ScrollView>

      {cartoes.length > 1 && (
        // Decorativos: quem navega é o scroll, e o leitor de tela já anuncia
        // cada cartão. Anunciar os pontos só duplicaria o mesmo estado.
        <View
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants"
          style={{ flexDirection: 'row', justifyContent: 'center', gap: 6, marginTop: 19 }}
        >
          {cartoes.map((c, i) => (
            <View
              key={c.cartaoId}
              style={{
                width: 6,
                height: 6,
                borderRadius: 3,
                backgroundColor: i === indice ? colors.brand : colors.textMuted,
                opacity: i === indice ? 1 : 0.4,
              }}
            />
          ))}
        </View>
      )}
    </View>
  );
}
