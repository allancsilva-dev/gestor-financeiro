import React from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Ionicons from '@expo/vector-icons/Ionicons';
import { useTheme, radius, screenPadding, spacing, typography } from '../../theme';
import Badge, { BadgeTone } from './Badge';
import Entrance from './Entrance';
import PassosProgresso from './PassosProgresso';

interface Props {
  titulo: string;
  subtitulo?: string;
  /** Passo atual do fluxo de entrada (cadastro + conta principal). */
  passo?: number;
  totalDePassos?: number;
  /** Ausente = sem voltar (primeira tela do fluxo). */
  onVoltar?: () => void;
  /** Selo acima do título: "Opcional", "Conta criada" — diz o que é este passo. */
  selo?: { texto: string; tom?: BadgeTone };
  /** Barra fixa no rodapé: botões de ação do passo. */
  rodape?: React.ReactNode;
  children: React.ReactNode;
}

/**
 * Estrutura das telas de entrada (auth + onboarding). Antes cada uma repetia o
 * próprio `StyleSheet` com paddings crus e nenhuma tinha proteção de teclado —
 * no onboarding o campo de saldo ficava embaixo do teclado (o flow do Maestro
 * precisava tocar em ponto fixo da tela para fechá-lo).
 */
export default function TelaFluxo({
  titulo,
  subtitulo,
  passo,
  totalDePassos,
  onVoltar,
  selo,
  rodape,
  children,
}: Props) {
  const colors = useTheme();
  const insets = useSafeAreaInsets();

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <View
          style={{
            paddingTop: insets.top + spacing.md,
            paddingHorizontal: screenPadding,
            paddingBottom: spacing.sm,
            flexDirection: 'row',
            alignItems: 'center',
            gap: spacing.md,
          }}
        >
          {onVoltar ? (
            <TouchableOpacity
              onPress={onVoltar}
              activeOpacity={0.7}
              accessibilityRole="button"
              accessibilityLabel="Voltar"
              hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
              style={{
                width: 36,
                height: 36,
                borderRadius: radius.pill,
                backgroundColor: colors.overlay,
                borderWidth: 1,
                borderColor: colors.border,
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Ionicons name="chevron-back" size={20} color={colors.brandFg} />
            </TouchableOpacity>
          ) : null}

          {passo && totalDePassos ? <PassosProgresso passo={passo} total={totalDePassos} /> : <View style={{ flex: 1 }} />}
        </View>

        <ScrollView
          keyboardShouldPersistTaps="handled"
          contentContainerStyle={{
            paddingHorizontal: screenPadding,
            paddingTop: spacing.lg,
            paddingBottom: spacing.xxl,
            flexGrow: 1,
          }}
        >
          <Entrance>
            {selo ? (
              <View style={{ marginBottom: spacing.sm }}>
                <Badge tone={selo.tom ?? 'brand'}>{selo.texto}</Badge>
              </View>
            ) : null}
            <Text
              accessibilityRole="header"
              style={{ ...typography.screenTitle, color: colors.textPrimary }}
            >
              {titulo}
            </Text>
            {subtitulo ? (
              <Text style={{ ...typography.body, color: colors.textSecondary, marginTop: spacing.sm }}>
                {subtitulo}
              </Text>
            ) : null}
          </Entrance>

          <Entrance delay={60} style={{ marginTop: spacing.xl, flex: 1 }}>
            {children}
          </Entrance>
        </ScrollView>

        {rodape ? (
          <View
            style={{
              paddingHorizontal: screenPadding,
              paddingTop: spacing.md,
              paddingBottom: insets.bottom + spacing.md,
              borderTopWidth: 1,
              borderTopColor: colors.border,
              backgroundColor: colors.bg,
              gap: spacing.sm,
            }}
          >
            {rodape}
          </View>
        ) : null}
      </KeyboardAvoidingView>
    </View>
  );
}
