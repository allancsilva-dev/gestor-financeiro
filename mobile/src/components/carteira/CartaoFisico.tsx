import React from 'react';
import { Image, Text, View } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import Ionicons from '@expo/vector-icons/Ionicons';
import { estiloDoLogo, identidadeDoCartao } from '../../domain/emissores';
import { tamanhoLogoNoTile } from '../../domain/logosEmissores';
import ChipCartao from './ChipCartao';
import BandeiraMarca from './BandeiraMarca';

// Proporção real de cartão de crédito (ISO/IEC 7810 ID-1 = 1.586). A referência
// mede 287x182dp, razão 1.577 — a diferença é o arredondamento do JPEG.
export const CARTAO_RAZAO = 1.586;

interface Props {
  nome: string;
  banco?: string | null;
  cor?: string | null;
  ultimosDigitos?: string | null;
  bandeira?: string | null;
  titular?: string | null;
  largura: number;
}

const Pontos = ({ tinta, tamanho }: { tinta: string; tamanho: number }) => (
  <View style={{ flexDirection: 'row', gap: tamanho * 0.85 }}>
    {[0, 1, 2, 3].map(i => (
      <View
        key={i}
        style={{
          width: tamanho,
          height: tamanho,
          borderRadius: tamanho / 2,
          backgroundColor: tinta,
          opacity: 0.85,
        }}
      />
    ))}
  </View>
);

/**
 * A face do cartão. O gradiente e o monograma vêm do catálogo de emissores
 * (src/domain/emissores.ts), que já garante contraste AA nas duas pontas —
 * por isso aqui a tinta é usada direto, sem checagem extra.
 *
 * Escala tudo a partir de `largura` para o cartão não deformar entre um iPhone
 * SE e um Pro Max.
 */
export default function CartaoFisico({
  nome, banco, cor, ultimosDigitos, bandeira, titular, largura,
}: Props) {
  const id = identidadeDoCartao({ nome, banco, cor });
  const altura = Math.round(largura / CARTAO_RAZAO);
  const k = largura / 287; // fator vindo da medida da referência
  const padding = Math.round(21 * k);
  const { tinta } = id;
  const ladoTileLogo = Math.round(32 * k);
  // Fica maior que o tile nas marcas cujo PNG tem margem transparente. O que
  // transborda, e o `overflow: 'hidden'` corta, é a margem — não o desenho.
  // O tile fica no topo do cartão, então quem decide o contraste do logo é o
  // `from` do gradiente.
  const logoEstilo = estiloDoLogo(id.logoMedido, id.from);
  const ladoLogo = tamanhoLogoNoTile(ladoTileLogo, id.logoPreenchimento, logoEstilo.alvo);

  return (
    <View
      style={{
        width: largura,
        height: altura,
        borderRadius: Math.round(18 * k),
        overflow: 'hidden',
        backgroundColor: id.to,
      }}
    >
      {/* Três camadas, derivadas da grade medida em
          mobile/.design/MEDICOES-carteira.md: a referência é mais clara no
          TOPO-CENTRO e escurece para baixo e para as duas bordas laterais —
          um gradiente diagonal de duas paradas não produz isso. */}
      <LinearGradient
        colors={[id.from, id.to]}
        start={{ x: 0, y: 0 }}
        end={{ x: 0, y: 1 }}
        style={{ ...StyleSheetAbsolute }}
      />
      {/* faixa de brilho central, na horizontal */}
      <LinearGradient
        colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.07)', 'rgba(255,255,255,0)']}
        locations={[0, 0.45, 1]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 0 }}
        style={{ ...StyleSheetAbsolute }}
      />
      {/* vinheta lateral: na referência as duas bordas caem bem abaixo do centro
          já no topo do cartão — sem isso a faixa superior sai chapada */}
      <LinearGradient
        colors={['rgba(0,0,0,0.30)', 'rgba(0,0,0,0)', 'rgba(0,0,0,0.30)']}
        locations={[0, 0.45, 1]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 0 }}
        style={{ ...StyleSheetAbsolute }}
      />
      {/* vinheta inferior: a base do cartão de referência cai bem mais que o topo */}
      <LinearGradient
        colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.34)']}
        start={{ x: 0, y: 0.4 }}
        end={{ x: 0, y: 1 }}
        style={{ ...StyleSheetAbsolute }}
      />
      <View
        style={{
          ...StyleSheetAbsolute,
          borderRadius: Math.round(18 * k),
          borderWidth: 1,
          borderColor: tinta === '#FFFFFF' ? 'rgba(255,255,255,0.14)' : 'rgba(0,0,0,0.12)',
        }}
      />

      <View style={{ flex: 1, padding, justifyContent: 'space-between' }}>
        {/* topo: tile do emissor + wordmark, contactless à direita */}
        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
          <View
            style={{
              width: ladoTileLogo,
              height: ladoTileLogo,
              borderRadius: Math.round(9 * k),
              // Com logo real o tile quase nunca pinta nada: 99 dos 162 assets
              // já são uma placa da marca (o squircle do Itaú, o círculo do
              // Bradesco) e um fundo atrás deles vira moldura sobrando; os
              // outros são marca solta, e quem garante o contraste dela é o
              // tint. `apoio` é a exceção — placa que sumiria neste cartão —, e
              // vem da cor do próprio cartão, não de um branco chapado. Sem
              // logo, o tile fica colorido com o monograma, calculado para AA.
              backgroundColor: id.logo !== null ? logoEstilo.apoio ?? 'transparent' : id.logoBg,
              alignItems: 'center',
              justifyContent: 'center',
              overflow: 'hidden',
            }}
          >
            {id.logo !== null ? (
              <Image
                source={id.logo}
                resizeMode="contain"
                accessibilityIgnoresInvertColors
                tintColor={logoEstilo.tint ?? undefined}
                style={{ width: ladoLogo, height: ladoLogo }}
              />
            ) : (
              <Text
                style={{
                  color: id.logoFg,
                  fontSize: Math.round(15 * k),
                  fontWeight: '800',
                  letterSpacing: -0.3,
                }}
              >
                {id.glifo}
              </Text>
            )}
          </View>
          <Text
            numberOfLines={1}
            adjustsFontSizeToFit
            minimumFontScale={0.7}
            style={{
              flex: 1,
              marginLeft: Math.round(10 * k),
              color: tinta,
              fontSize: Math.round(16 * k),
              fontWeight: '800',
              letterSpacing: 1.2,
            }}
          >
            {id.rotulo}
          </Text>
          <Ionicons
            name="wifi"
            size={Math.round(22 * k)}
            color={tinta}
            style={{ opacity: 0.85, transform: [{ rotate: '90deg' }] }}
          />
        </View>

        <ChipCartao largura={Math.round(30 * k)} />

        {/* número mascarado: só os 4 últimos existem no sistema */}
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: Math.round(16 * k) }}>
          <Pontos tinta={tinta} tamanho={Math.round(4 * k)} />
          <Pontos tinta={tinta} tamanho={Math.round(4 * k)} />
          <Pontos tinta={tinta} tamanho={Math.round(4 * k)} />
          {ultimosDigitos ? (
            <Text
              style={{
                marginLeft: 'auto',
                color: tinta,
                fontSize: Math.round(17 * k),
                fontWeight: '700',
                letterSpacing: 2,
                fontVariant: ['tabular-nums'],
              }}
            >
              {ultimosDigitos}
            </Text>
          ) : (
            <View style={{ marginLeft: 'auto' }}>
              <Pontos tinta={tinta} tamanho={Math.round(4 * k)} />
            </View>
          )}
        </View>

        <View style={{ flexDirection: 'row', alignItems: 'flex-end' }}>
          <View style={{ flex: 1, paddingRight: Math.round(8 * k) }}>
            <Text
              style={{
                color: tinta,
                opacity: 0.6,
                fontSize: Math.round(9 * k),
                fontWeight: '600',
                letterSpacing: 1.6,
              }}
            >
              Titular
            </Text>
            <Text
              numberOfLines={1}
              style={{
                color: tinta,
                fontSize: Math.round(13 * k),
                fontWeight: '700',
                letterSpacing: 0.6,
                marginTop: 2,
              }}
            >
              {(titular ?? nome).toUpperCase()}
            </Text>
          </View>
          <BandeiraMarca bandeira={bandeira} tinta={tinta} escala={k} />
        </View>
      </View>
    </View>
  );
}

// Repetido em quatro camadas; extrair evita o objeto literal duplicado.
const StyleSheetAbsolute = {
  position: 'absolute' as const,
  left: 0,
  right: 0,
  top: 0,
  bottom: 0,
};
