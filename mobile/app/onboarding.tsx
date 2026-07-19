import React, { useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  ScrollView,
  ActivityIndicator,
  StyleSheet,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { useTheme } from '../src/theme';
import { onboardingService } from '../src/services/onboardingService';
import { ApiErrorWithMessage } from '../src/types';
import { useAuth } from '../src/context/AuthContext';
import { maskCurrencyInput, parseCurrencyBR } from '../src/utils/format';
import Field from '../src/components/ui/Field';
import Chip from '../src/components/ui/Chip';

type TipoContaInicial = 'DINHEIRO' | 'CONTA_BANCARIA' | 'POUPANCA';

// Onboarding mínimo (PR-F3-09): etapa única com a conta principal — nome,
// tipo e saldo inicial (vazio = zero). Envia somente carteira (contrato do
// PR-F3-03); cartão, categorias, renda e metas viram setup progressivo.
export default function OnboardingScreen() {
  const colors = useTheme();
  const router = useRouter();
  const { updateUsuario } = useAuth();
  const insets = useSafeAreaInsets();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [nome, setNome] = useState('Conta Principal');
  const [tipo, setTipo] = useState<TipoContaInicial>('CONTA_BANCARIA');
  const [saldo, setSaldo] = useState('');

  const handleComecar = async () => {
    if (nome.trim().length < 2) {
      setError('Informe o nome da conta principal.');
      return;
    }
    const saldoNum = parseCurrencyBR(saldo || '0');
    if (!Number.isFinite(saldoNum) || saldoNum < 0) {
      setError('Saldo inicial deve ser zero ou positivo.');
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const user = await onboardingService.finalizar({
        carteira: {
          nome: nome.trim(),
          subtipo: tipo === 'CONTA_BANCARIA' ? 'CORRENTE' : tipo,
          saldo: saldoNum,
        },
      });
      await updateUsuario(user);
      router.replace('/(app)/');
    } catch (err) {
      const e = err as ApiErrorWithMessage;
      setError(e.userMessage ?? 'Não foi possível salvar sua configuração. Tente novamente.');
    } finally { setLoading(false); }
  };

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.bg }]}
      contentContainerStyle={{ paddingTop: insets.top + 24, paddingHorizontal: 16, paddingBottom: 40 }}
      keyboardShouldPersistTaps="handled"
    >
      <Text style={[styles.title, { color: colors.textPrimary }]}>Sua conta principal</Text>
      <Text style={[styles.hint, { color: colors.textSecondary }]}>
        Só isso para começar. Cartão, categorias e metas você adiciona depois, quando precisar.
      </Text>

      <View style={styles.form}>
        <Field
          testID="onboarding-account-name"
          label="Nome"
          value={nome}
          onChangeText={setNome}
          placeholder="Ex: Conta Principal"
        />
        <Text style={[styles.label, { color: colors.textSecondary }]}>TIPO</Text>
        <View style={styles.chipRow}>
          {(['CONTA_BANCARIA', 'DINHEIRO', 'POUPANCA'] as TipoContaInicial[]).map((t) => (
            <Chip
              key={t}
              label={t === 'CONTA_BANCARIA' ? 'Bancária' : t === 'DINHEIRO' ? 'Dinheiro' : 'Poupança'}
              selected={tipo === t}
              onPress={() => setTipo(t)}
            />
          ))}
        </View>
        <Field
          testID="onboarding-account-balance"
          label="Saldo inicial (R$)"
          value={saldo}
          onChangeText={(t) => setSaldo(maskCurrencyInput(t))}
          keyboardType="number-pad"
          placeholder="0,00"
        />
      </View>

      {error ? <Text accessibilityRole="alert" accessibilityLiveRegion="assertive" style={{ color: colors.danger, marginTop: 16 }}>{error}</Text> : null}

      <TouchableOpacity
        onPress={handleComecar}
        disabled={loading}
        accessibilityRole="button"
        accessibilityLabel="Começar"
        style={[styles.btnPrimary, { backgroundColor: colors.brand, opacity: loading ? 0.6 : 1 }]}
      >
        {loading ? (
          <ActivityIndicator color={colors.brandText} />
        ) : (
          <Text style={{ color: colors.brandText, fontWeight: '700', fontSize: 16 }}>Começar</Text>
        )}
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  title: { fontSize: 24, fontWeight: '800', letterSpacing: -0.4, marginBottom: 8 },
  hint: { fontSize: 14, lineHeight: 20, marginBottom: 24 },
  form: {},
  label: { fontSize: 10, letterSpacing: 0.8, textTransform: 'uppercase', marginBottom: 6 },
  chipRow: { flexDirection: 'row', gap: 8, marginBottom: 16 },
  btnPrimary: { marginTop: 32, minHeight: 52, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
});
