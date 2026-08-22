import React from 'react';
import { Text, View } from 'react-native';
import { useTheme, spacing, typography } from '../../theme';
import Botao from './Botao';

interface Props {
  emoji: string;
  titulo: string;
  texto?: string;
  acao?: { rotulo: string; onPress: () => void };
}

/**
 * Estado vazio/erro com voz própria. Existe para que "sem dado", "deu erro" e
 * "params inválidos" não colapsem todos na mesma tela zerada — que era
 * exatamente o problema da tela de fatura.
 */
export default function EstadoVazio({ emoji, titulo, texto, acao }: Props) {
  const colors = useTheme();
  return (
    <View style={{ alignItems: 'center', paddingVertical: spacing.xxxl + spacing.xxl, paddingHorizontal: spacing.xxl }}>
      {/* O emoji é a ilustração do estado, não texto de leitura — corpo próprio */}
      <Text style={{ fontSize: 36 }}>{emoji}</Text>
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
