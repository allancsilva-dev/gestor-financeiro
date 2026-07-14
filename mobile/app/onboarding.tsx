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
import { onboardingService, OnboardingFinalizarRequest } from '../src/services/onboardingService';
import { ApiErrorWithMessage, TipoCarteira, TipoConta } from '../src/types';
import { useAuth } from '../src/context/AuthContext';
import { CATEGORY_COLORS, isValidDateBR, maskCurrencyInput, parseCurrencyBR, parseDateBR } from '../src/utils/format';
import { isValidDayOfMonth } from '../src/utils/validate';
import Field from '../src/components/ui/Field';
import Chip from '../src/components/ui/Chip';

const PASSOS = ['Conta', 'Cartão', 'Categorias', 'Renda', 'Meta', 'Confirmar'];

// Cores da paleta canônica de categorias (CATEGORY_COLORS) — mesma do seletor em Mais > Categorias
const CATEGORIAS_SUGERIDAS = [
  { nome: 'Alimentação', cor: CATEGORY_COLORS[2], icone: '🍔' }, // vermelho
  { nome: 'Transporte', cor: CATEGORY_COLORS[3], icone: '🚗' }, // amarelo
  { nome: 'Moradia', cor: CATEGORY_COLORS[4], icone: '🏠' }, // roxo
  { nome: 'Saúde', cor: CATEGORY_COLORS[5], icone: '🏥' }, // rosa
  { nome: 'Educação', cor: CATEGORY_COLORS[6], icone: '📚' }, // azul royal
  { nome: 'Lazer', cor: CATEGORY_COLORS[1], icone: '🎮' }, // verde
  { nome: 'Vestuário', cor: CATEGORY_COLORS[0], icone: '👕' }, // ciano
  { nome: 'Assinaturas', cor: CATEGORY_COLORS[7], icone: '📱' }, // laranja
  { nome: 'Outros', cor: CATEGORY_COLORS[8], icone: '📦' }, // cinza neutro
];

export default function OnboardingScreen() {
  const colors = useTheme();
  const router = useRouter();
  const { updateUsuario } = useAuth();
  const insets = useSafeAreaInsets();
  const [passo, setPasso] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [carteira, setCarteira] = useState({ nome: 'Conta Principal', tipo: 'CONTA_BANCARIA' as TipoCarteira, saldo: '' });
  const [conta, setConta] = useState({ nome: 'Cartão Principal', tipo: 'CREDITO' as TipoConta, limiteTotal: '' });
  const [categoriasSelecionadas, setCategoriasSelecionadas] = useState<string[]>(CATEGORIAS_SUGERIDAS.map((c) => c.nome));
  const [renda, setRenda] = useState({ nome: 'Salário', valor: '', diaVencimento: '1' });
  const [pularRenda, setPularRenda] = useState(false);
  const [meta, setMeta] = useState({ nome: '', valorTotal: '', valorMensal: '', dataPrevista: '' });
  const [pularMeta, setPularMeta] = useState(false);

  const validarPasso = (): string | null => {
    if (passo === 0) {
      if (carteira.nome.trim().length < 2) return 'Informe o nome da conta principal.';
      const saldo = parseCurrencyBR(carteira.saldo || '0');
      if (!Number.isFinite(saldo) || saldo < 0) return 'Saldo inicial deve ser zero ou positivo.';
    }
    if (passo === 1) {
      if (conta.nome.trim().length < 2) return 'Informe o nome do cartão ou conta.';
      const limite = parseCurrencyBR(conta.limiteTotal || '0');
      if (conta.tipo === 'CREDITO' && (!Number.isFinite(limite) || limite <= 0)) return 'Limite do cartão deve ser maior que zero.';
    }
    if (passo === 2 && categoriasSelecionadas.length === 0) {
      return 'Selecione ao menos uma categoria.';
    }
    if (passo === 3 && !pularRenda) {
      if (renda.nome.trim().length < 2) return 'Informe o nome da renda ou marque para configurar depois.';
      const valorRenda = parseCurrencyBR(renda.valor || '0');
      if (!Number.isFinite(valorRenda) || valorRenda <= 0) return 'Informe um valor mensal maior que zero ou marque para configurar depois.';
      if (!isValidDayOfMonth(renda.diaVencimento)) return 'Dia de recebimento deve estar entre 1 e 31.';
    }
    if (passo === 4 && !pularMeta) {
      if (meta.nome.trim().length < 2) return 'Informe o nome da meta ou marque para configurar depois.';
      const valorTotal = parseCurrencyBR(meta.valorTotal || '0');
      const valorMensal = meta.valorMensal ? parseCurrencyBR(meta.valorMensal) : undefined;
      if (!Number.isFinite(valorTotal) || valorTotal <= 0) return 'Informe um valor total maior que zero ou marque para configurar depois.';
      if (valorMensal !== undefined && (!Number.isFinite(valorMensal) || valorMensal <= 0)) return 'Valor mensal da meta deve ser maior que zero.';
      if (meta.dataPrevista && !isValidDateBR(meta.dataPrevista)) return 'Data da meta inválida. Use DD/MM/AAAA.';
    }
    return null;
  };

  const handleAvancar = () => {
    const erro = validarPasso();
    if (erro) {
      setError(erro);
      return;
    }
    setError(null);
    setPasso(passo + 1);
  };

  const montarRequest = (): OnboardingFinalizarRequest => {
    const categorias = categoriasSelecionadas.map((nome) => {
      const sugerida = CATEGORIAS_SUGERIDAS.find((c) => c.nome === nome);
      return { nome, cor: sugerida?.cor ?? CATEGORY_COLORS[8], icone: sugerida?.icone ?? '📌' };
    });

    return {
      carteira: {
        nome: carteira.nome.trim(),
        tipo: carteira.tipo,
        saldo: parseCurrencyBR(carteira.saldo || '0'),
      },
      conta: {
        nome: conta.nome.trim(),
        tipo: conta.tipo,
        limiteTotal: conta.tipo === 'CREDITO' ? parseCurrencyBR(conta.limiteTotal || '0') : undefined,
      },
      categorias,
      renda: pularRenda ? undefined : {
        nome: renda.nome.trim(),
        valor: parseCurrencyBR(renda.valor || '0'),
        diaVencimento: parseInt(renda.diaVencimento || '1', 10),
      },
      meta: pularMeta ? undefined : {
        nome: meta.nome.trim(),
        valorTotal: parseCurrencyBR(meta.valorTotal || '0'),
        valorMensal: meta.valorMensal ? parseCurrencyBR(meta.valorMensal) : undefined,
        dataLimite: meta.dataPrevista ? parseDateBR(meta.dataPrevista) : undefined,
      },
    };
  };

  const handleFinalizar = async () => {
    const erro = validarPasso();
    if (erro) {
      setError(erro);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const user = await onboardingService.finalizar(montarRequest());
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
      contentContainerStyle={{ paddingTop: insets.top + 16, paddingHorizontal: 16, paddingBottom: 40 }}
    >
      <View style={styles.progressBar}>
        {PASSOS.map((_, i) => (
          <View
            key={i}
            style={{
              flex: 1,
              height: 3,
              borderRadius: 2,
              marginHorizontal: 1,
              backgroundColor: i <= passo ? colors.brand : colors.border,
              opacity: i === passo ? 1 : 0.4,
            }}
          />
        ))}
      </View>
      <Text style={[styles.stepLabel, { color: colors.textSecondary }]}>Passo {passo + 1} de {PASSOS.length}</Text>
      <Text style={[styles.title, { color: colors.textPrimary }]}>{PASSOS[passo]}</Text>

      {passo === 0 && (
        <View style={styles.form}>
          <Field
            label="Nome"
            value={carteira.nome}
            onChangeText={(t) => setCarteira((c) => ({ ...c, nome: t }))}
            placeholder="Ex: Conta Principal"
          />
          <Text style={[styles.label, { color: colors.textSecondary }]}>TIPO</Text>
          <View style={styles.chipRow}>
            {(['CONTA_BANCARIA', 'DINHEIRO', 'POUPANCA'] as TipoCarteira[]).map((t) => (
              <Chip
                key={t}
                label={t === 'CONTA_BANCARIA' ? 'Bancária' : t === 'DINHEIRO' ? 'Dinheiro' : 'Poupança'}
                selected={carteira.tipo === t}
                onPress={() => setCarteira((c) => ({ ...c, tipo: t }))}
              />
            ))}
          </View>
          <Field
            label="Saldo inicial (R$)"
            value={carteira.saldo}
            onChangeText={(t) => setCarteira((c) => ({ ...c, saldo: maskCurrencyInput(t) }))}
            keyboardType="number-pad"
            placeholder="0,00"
          />
        </View>
      )}

      {passo === 1 && (
        <View style={styles.form}>
          <Field
            label="Nome"
            value={conta.nome}
            onChangeText={(t) => setConta((c) => ({ ...c, nome: t }))}
            placeholder="Ex: Cartão Nubank"
          />
          <Text style={[styles.label, { color: colors.textSecondary }]}>TIPO</Text>
          <View style={styles.chipRow}>
            {(['CREDITO', 'DEBITO', 'DINHEIRO'] as TipoConta[]).map((t) => (
              <Chip
                key={t}
                label={t === 'CREDITO' ? 'Crédito' : t === 'DEBITO' ? 'Débito' : 'Dinheiro'}
                selected={conta.tipo === t}
                onPress={() => setConta((c) => ({ ...c, tipo: t }))}
              />
            ))}
          </View>
          {conta.tipo === 'CREDITO' && (
            <Field
              label="Limite (R$)"
              value={conta.limiteTotal}
              onChangeText={(t) => setConta((c) => ({ ...c, limiteTotal: maskCurrencyInput(t) }))}
              keyboardType="number-pad"
              placeholder="0,00"
            />
          )}
        </View>
      )}

      {passo === 2 && (
        <View style={styles.form}>
          <Text style={[styles.hint, { color: colors.textSecondary }]}>Selecione as categorias iniciais:</Text>
          <View style={styles.grid}>
            {CATEGORIAS_SUGERIDAS.map((cat) => {
              const sel = categoriasSelecionadas.includes(cat.nome);
              return (
                <TouchableOpacity
                  key={cat.nome}
                  onPress={() => setCategoriasSelecionadas((p) => (p.includes(cat.nome) ? p.filter((c) => c !== cat.nome) : [...p, cat.nome]))}
                  accessibilityRole="checkbox"
                  accessibilityState={{ checked: sel }}
                  accessibilityLabel={cat.nome}
                  style={[styles.gridItem, { backgroundColor: sel ? colors.brandBg : colors.card, borderColor: sel ? colors.brand : colors.border }]}
                >
                  <Text style={{ fontSize: 20 }}>{cat.icone}</Text>
                  <Text style={{ color: sel ? colors.brandFg : colors.textSecondary, fontSize: 12, marginTop: 4, textAlign: 'center' }}>{cat.nome}</Text>
                </TouchableOpacity>
              );
            })}
          </View>
        </View>
      )}

      {passo === 3 && (
        <View style={styles.form}>
          <Text style={[styles.hint, { color: colors.textSecondary }]}>Sua renda principal (opcional):</Text>
          <Field
            label="Nome"
            value={renda.nome} onChangeText={(t) => setRenda((r) => ({ ...r, nome: t }))}
            placeholder="Ex: Salário" editable={!pularRenda}
            style={{ opacity: pularRenda ? 0.4 : 1 }}
          />
          <Field
            label="Valor mensal (R$)"
            value={renda.valor} onChangeText={(t) => setRenda((r) => ({ ...r, valor: maskCurrencyInput(t) }))}
            keyboardType="number-pad" placeholder="0,00" editable={!pularRenda}
            style={{ opacity: pularRenda ? 0.4 : 1 }}
          />
          <Field
            label="Dia do recebimento"
            value={renda.diaVencimento} onChangeText={(t) => setRenda((r) => ({ ...r, diaVencimento: t }))}
            keyboardType="number-pad" placeholder="1" editable={!pularRenda}
            style={{ opacity: pularRenda ? 0.4 : 1 }}
          />
          <TouchableOpacity
            onPress={() => setPularRenda(!pularRenda)}
            accessibilityRole="checkbox"
            accessibilityState={{ checked: pularRenda }}
            accessibilityLabel="Pular — configuro depois"
            style={{ flexDirection: 'row', alignItems: 'center', gap: 8, minHeight: 44 }}
          >
            <View style={{ width: 20, height: 20, borderRadius: 4, borderWidth: 2, borderColor: pularRenda ? colors.brand : colors.textSecondary, backgroundColor: pularRenda ? colors.brand : 'transparent', alignItems: 'center', justifyContent: 'center' }}>
              {pularRenda && <Text style={{ color: colors.brandText, fontSize: 14 }}>✓</Text>}
            </View>
            <Text style={{ color: colors.textSecondary, fontSize: 13 }}>Pular — configuro depois</Text>
          </TouchableOpacity>
        </View>
      )}

      {passo === 4 && (
        <View style={styles.form}>
          <Text style={[styles.hint, { color: colors.textSecondary }]}>Sua primeira meta financeira (opcional):</Text>
          <Field
            label="Nome"
            value={meta.nome} onChangeText={(t) => setMeta((m) => ({ ...m, nome: t }))}
            placeholder="Ex: Reserva de emergência" editable={!pularMeta}
            style={{ opacity: pularMeta ? 0.4 : 1 }}
          />
          <Field
            label="Valor total (R$)"
            value={meta.valorTotal} onChangeText={(t) => setMeta((m) => ({ ...m, valorTotal: maskCurrencyInput(t) }))}
            keyboardType="number-pad" placeholder="0,00" editable={!pularMeta}
            style={{ opacity: pularMeta ? 0.4 : 1 }}
          />
          <TouchableOpacity
            onPress={() => setPularMeta(!pularMeta)}
            accessibilityRole="checkbox"
            accessibilityState={{ checked: pularMeta }}
            accessibilityLabel="Pular — configuro depois"
            style={{ flexDirection: 'row', alignItems: 'center', gap: 8, minHeight: 44 }}
          >
            <View style={{ width: 20, height: 20, borderRadius: 4, borderWidth: 2, borderColor: pularMeta ? colors.brand : colors.textSecondary, backgroundColor: pularMeta ? colors.brand : 'transparent', alignItems: 'center', justifyContent: 'center' }}>
              {pularMeta && <Text style={{ color: colors.brandText, fontSize: 14 }}>✓</Text>}
            </View>
            <Text style={{ color: colors.textSecondary, fontSize: 13 }}>Pular — configuro depois</Text>
          </TouchableOpacity>
        </View>
      )}

      {passo === 5 && (
        <View style={styles.form}>
          <Text style={[styles.hint, { color: colors.textSecondary, textAlign: 'center' }]}>Tudo pronto! Clique abaixo para começar a usar o Gestor Financeiro.</Text>
        </View>
      )}

      {error ? <Text accessibilityRole="alert" accessibilityLiveRegion="assertive" style={{ color: colors.danger, marginTop: 16 }}>{error}</Text> : null}

      <View style={styles.buttons}>
        {passo > 0 && (
          <TouchableOpacity onPress={() => setPasso(passo - 1)} accessibilityRole="button" style={[styles.btnSecondary, { borderColor: colors.border }]}>
            <Text style={{ color: colors.textSecondary, fontWeight: '600' }}>Voltar</Text>
          </TouchableOpacity>
        )}
        <View style={{ flex: 1 }} />
        {passo < 5 ? (
          <TouchableOpacity onPress={handleAvancar} disabled={loading} accessibilityRole="button" style={[styles.btnPrimary, { backgroundColor: colors.brand, opacity: loading ? 0.6 : 1 }]}>
            {loading ? (
              <ActivityIndicator color={colors.brandText} />
            ) : (
              <Text style={{ color: colors.brandText, fontWeight: '700' }}>Continuar</Text>
            )}
          </TouchableOpacity>
        ) : (
          <TouchableOpacity onPress={handleFinalizar} disabled={loading} accessibilityRole="button" style={[styles.btnPrimary, { backgroundColor: colors.brand, opacity: loading ? 0.6 : 1 }]}>
            {loading ? (
              <ActivityIndicator color={colors.brandText} />
            ) : (
              <Text style={{ color: colors.brandText, fontWeight: '700' }}>Começar</Text>
            )}
          </TouchableOpacity>
        )}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  progressBar: { flexDirection: 'row', marginBottom: 12 },
  stepLabel: { fontSize: 11, textAlign: 'center', marginBottom: 4 },
  title: { fontSize: 22, fontWeight: '700', textAlign: 'center', marginBottom: 24 },
  form: {},
  label: { fontSize: 10, letterSpacing: 0.8, textTransform: 'uppercase', marginBottom: 6 },
  hint: { fontSize: 13, marginBottom: 12 },
  chipRow: { flexDirection: 'row', gap: 8, marginBottom: 16 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  gridItem: {
    width: '31%',
    aspectRatio: 1,
    borderRadius: 10,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttons: { flexDirection: 'row', marginTop: 32, alignItems: 'center', gap: 12 },
  btnSecondary: { paddingHorizontal: 16, minHeight: 48, justifyContent: 'center', borderRadius: 12, borderWidth: 1 },
  btnPrimary: { paddingHorizontal: 32, minHeight: 48, borderRadius: 12, alignItems: 'center', justifyContent: 'center' },
});
