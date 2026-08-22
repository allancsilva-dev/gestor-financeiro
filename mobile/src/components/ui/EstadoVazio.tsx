import React from 'react';
import { Text, View } from 'react-native';
import { useTheme, spacing, typography } from '../../theme';
import Botao from './Botao';

interface Props {
  emoji: string;
  titulo: string;
  texto?: string;
  acao?: { rotulo: string; onPress: () => void };
  /**
   * Dentro de um card a moldura já delimita o vazio: o respiro de tela cheia
   * empurraria o card para o dobro da altura do conteúdo que ele teria.
   */
  compacto?: boolean;
}

/**
 * Estado vazio/erro com voz própria. Existe para que "sem dado", "deu erro" e
 * "params inválidos" não colapsem todos na mesma tela zerada — que era
 * exatamente o problema da tela de fatura.
 */
export default function EstadoVazio({ emoji, titulo, texto, acao, compacto = false }: Props) {
  const colors = useTheme();
  return (
    <View style={{
      alignItems: 'center',
      paddingVertical: compacto ? spacing.lg : spacing.xxxl + spacing.xxl,
      paddingHorizontal: compacto ? 0 : spacing.xxl,
    }}>
      {/* O emoji é a ilustração do estado, não texto de leitura — corpo próprio */}
      <Text style={{ fontSize: compacto ? 28 : 36 }}>{emoji}</Text>
      <Text style={{ ...typography.cardTitle, color: colors.textPrimary, marginTop: spacing.md, textAlign: 'center' }}>
        {titulo}
      </Text>
      {!!texto && (
        <Text style={{ ...typography.body, color: colors.textSecondary, marginTop: spacing.xs, textAlign: 'center' }}>
          {texto}
        </Text>
      )}
      {!!acao && (
        <Botao
          titulo={acao.rotulo}
          onPress={acao.onPress}
          tamanho="pill"
          style={{ marginTop: spacing.lg, paddingHorizontal: spacing.xl }}
        />
      )}
    </View>
  );
}
