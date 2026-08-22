import React from 'react';
import { ActivityIndicator, Modal, Text, TouchableOpacity, View } from 'react-native';
import { useTheme, spacing, typography } from '../../theme';

interface AcaoFolha {
  rotulo: string;
  onPress: () => void;
  carregando?: boolean;
  desabilitado?: boolean;
}

interface Props {
  visible: boolean;
  titulo: string;
  /** Chamado pelo botão de saída e pelo gesto/botão físico de voltar. */
  onFechar: () => void;
  /** "Cancelar" quando a folha edita algo, "Fechar" quando ela só mostra. */
  rotuloFechar?: string;
  /** Ação de confirmação à direita. Sem ela o título fica centrado mesmo assim. */
  acao?: AcaoFolha;
  children: React.ReactNode;
}

/**
 * A folha modal do app: `pageSheet` com barra saída / título / ação.
 *
 * Esse bloco estava copiado 17 vezes em 10 arquivos, cada cópia com o seu corpo
 * de título e a sua cor de link — e várias sem `accessibilityRole` no botão. O
 * conteúdo continua sendo da tela; a casca é daqui.
 */
export default function FolhaModal({
  visible,
  titulo,
  onFechar,
  rotuloFechar = 'Cancelar',
  acao,
  children,
}: Props) {
  const colors = useTheme();
  const inativo = acao?.desabilitado || acao?.carregando;

  return (
    <Modal
      visible={visible}
      animationType="slide"
      presentationStyle="pageSheet"
      onRequestClose={onFechar}
    >
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
        <View
          style={{
            flexDirection: 'row',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: spacing.md,
            padding: spacing.lg,
            borderBottomWidth: 1,
            borderBottomColor: colors.border,
          }}
        >
          <TouchableOpacity
            onPress={onFechar}
            accessibilityRole="button"
            hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
          >
            <Text style={{ ...typography.value, fontWeight: '500', color: colors.brandFg }}>
              {rotuloFechar}
            </Text>
          </TouchableOpacity>

          <Text
            accessibilityRole="header"
            numberOfLines={1}
            style={{ ...typography.cardTitle, color: colors.textPrimary, flexShrink: 1 }}
          >
            {titulo}
          </Text>

          {acao ? (
            <TouchableOpacity
              onPress={acao.onPress}
              disabled={inativo}
              accessibilityRole="button"
              accessibilityState={{ disabled: !!inativo, busy: !!acao.carregando }}
              hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
              style={{ opacity: inativo ? 0.6 : 1 }}
            >
              {acao.carregando ? (
                <ActivityIndicator color={colors.brandFg} size="small" />
              ) : (
                <Text style={{ ...typography.value, color: colors.brandFg }}>{acao.rotulo}</Text>
              )}
            </TouchableOpacity>
          ) : (
            // Espaçador: sem ele o título encosta na borda em vez de ficar centrado
            <View style={{ width: 48 }} />
          )}
        </View>

        {children}
      </View>
    </Modal>
  );
}
