import React from 'react';
import { ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import BackButton from '../../src/components/ui/BackButton';
import { useTheme } from '../../src/theme';

const sections = [
  ['Dados tratados', 'Nome, e-mail, credenciais protegidas e os dados financeiros que você decide cadastrar, como transações, carteiras, contas, metas e anexos.'],
  ['Finalidades', 'Usamos os dados para autenticar sua conta, executar as funções solicitadas, proteger o serviço, oferecer suporte e cumprir obrigações legais. Não vendemos dados pessoais.'],
  ['Base legal e consentimento', 'O tratamento necessário ao funcionamento do serviço decorre da execução do contrato. Tratamentos opcionais dependem de consentimento, que pode ser revogado.'],
  ['Compartilhamento', 'Fornecedores de infraestrutura podem processar o mínimo necessário sob obrigação de segurança e confidencialidade. Dados só são entregues a autoridades quando houver obrigação legal válida.'],
  ['Segurança e retenção', 'Aplicamos controles de acesso, criptografia em trânsito e medidas para prevenir acesso indevido. Mantemos dados pelo período necessário ao serviço e às obrigações legais.'],
  ['Seus direitos', 'Você pode solicitar confirmação, acesso, correção, portabilidade, informação sobre compartilhamento, revogação de consentimento, exportação ou exclusão, observados os prazos legais.'],
  ['Contato', 'Para exercer seus direitos ou esclarecer dúvidas, escreva para contato@nexostech.com.br.'],
] as const;

export default function PrivacyPolicy() {
  const colors = useTheme();
  const insets = useSafeAreaInsets();

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.bg }}
      contentContainerStyle={{ paddingTop: insets.top + 8, paddingHorizontal: 20, paddingBottom: insets.bottom + 32 }}
    >
      <BackButton />
      <Text accessibilityRole="header" style={{ color: colors.textPrimary, fontSize: 26, fontWeight: '800', marginTop: 16 }}>
        Política de privacidade
      </Text>
      <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 6, marginBottom: 22 }}>
        Versão 2026-07 · Gestor Financeiro
      </Text>

      <Text style={{ color: colors.textPrimary, fontSize: 15, lineHeight: 23, marginBottom: 18 }}>
        Esta política explica como seus dados pessoais são tratados no aplicativo Gestor Financeiro, conforme a Lei Geral de Proteção de Dados.
      </Text>

      {sections.map(([title, body]) => (
        <View key={title} style={{ marginBottom: 20 }}>
          <Text accessibilityRole="header" style={{ color: colors.textPrimary, fontSize: 17, fontWeight: '700', marginBottom: 6 }}>
            {title}
          </Text>
          <Text style={{ color: colors.textSecondary, fontSize: 15, lineHeight: 23 }}>{body}</Text>
        </View>
      ))}
    </ScrollView>
  );
}
