import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ScrollView,
  ActivityIndicator,
  StyleSheet,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { useQueryClient } from '@tanstack/react-query';
import { useTheme } from '../src/theme';
import { onboardingService } from '../src/services/onboardingService';
import { carteiraService } from '../src/services/carteiraService';
import { contaService } from '../src/services/contaService';
import { categoriaService } from '../src/services/categoriaService';
import { contaFixaService } from '../src/services/contaFixaService';
import { metaService } from '../src/services/metaService';
import { TipoCarteira, TipoConta } from '../src/types';
import { maskCurrencyInput, parseCurrencyBR } from '../src/utils/format';

const PASSOS = ['Carteira', 'Conta', 'Categorias', 'Renda', 'Meta', 'Confirmar'];

const CATEGORIAS_SUGERIDAS = [
  { nome: 'Alimentação', cor: '#EF4444', icone: '🍔' },
  { nome: 'Transporte', cor: '#F59E0B', icone: '🚗' },
  { nome: 'Moradia', cor: '#8B5CF6', icone: '🏠' },
  { nome: 'Saúde', cor: '#EC4899', icone: '🏥' },
  { nome: 'Educação', cor: '#3B82F6', icone: '📚' },
  { nome: 'Lazer', cor: '#10B981', icone: '🎮' },
  { nome: 'Vestuário', cor: '#6366F1', icone: '👕' },
  { nome: 'Assinaturas', cor: '#F97316', icone: '📱' },
  { nome: 'Outros', cor: '#6B7280', icone: '📦' },
];

export default function OnboardingScreen() {
  const colors = useTheme();
  const router = useRouter();
  const queryClient = useQueryClient();
  const insets = useSafeAreaInsets();
  const [passo, setPasso] = useState(0);
  const [loading, setLoading] = useState(false);

  const [carteira, setCarteira] = useState({ nome: 'Carteira Principal', tipo: 'CONTA_BANCARIA' as TipoCarteira, saldo: '' });
  const [conta, setConta] = useState({ nome: 'Cartão Principal', tipo: 'CREDITO' as TipoConta, limiteTotal: '' });
  const [categoriasSelecionadas, setCategoriasSelecionadas] = useState<string[]>(CATEGORIAS_SUGERIDAS.map((c) => c.nome));
  const [renda, setRenda] = useState({ nome: 'Salário', valor: '', diaVencimento: '1' });
  const [pularRenda, setPularRenda] = useState(false);
  const [categoriaIds, setCategoriaIds] = useState<number[]>([]);
  const [meta, setMeta] = useState({ nome: '', valorTotal: '', valorMensal: '', dataPrevista: '' });
  const [pularMeta, setPularMeta] = useState(false);

  // Onboarding pode reexecutar (app fechado antes de completar): cada passo
  // verifica o que já existe por nome antes de criar, para não duplicar.
  const mesmoNome = (a: string, b: string) => a.trim().toLowerCase() === b.trim().toLowerCase();

  const handleAvancar = async () => {
    if (passo === 0) {
      setLoading(true);
      try {
        const existentes = await carteiraService.listar();
        if (!existentes.content?.some((c) => mesmoNome(c.nome, carteira.nome))) {
          await carteiraService.criar({ nome: carteira.nome, tipo: carteira.tipo, saldo: parseCurrencyBR(carteira.saldo || '0') });
        }
        setPasso(1);
      } catch { } finally { setLoading(false); }
      return;
    }
    if (passo === 1) {
      setLoading(true);
      try {
        const existentes = await contaService.listar();
        if (!existentes.content?.some((c) => mesmoNome(c.nome, conta.nome))) {
          await contaService.criar({ nome: conta.nome, tipo: conta.tipo, limiteTotal: conta.tipo === 'CREDITO' ? parseCurrencyBR(conta.limiteTotal || '0') : undefined });
        }
        setPasso(2);
      } catch { } finally { setLoading(false); }
      return;
    }
    if (passo === 2) {
      setLoading(true);
      try {
        const existentes = await categoriaService.listar();
        const ids: number[] = [];
        for (const nome of categoriasSelecionadas) {
          const jaExiste = existentes.find((c) => mesmoNome(c.nome, nome));
          if (jaExiste) { ids.push(jaExiste.id); continue; }
          const s = CATEGORIAS_SUGERIDAS.find((c) => c.nome === nome);
          const criada = await categoriaService.criar({ nome, cor: s?.cor || '#6B7280', icone: s?.icone || '📌' });
          ids.push(criada.id);
        }
        setCategoriaIds(ids);
        setPasso(3);
      } catch { } finally { setLoading(false); }
      return;
    }
    if (passo === 3) {
      if (pularRenda) { setPasso(4); return; }
      setLoading(true);
      try {
        let categoriaId = categoriaIds[0];
        if (!categoriaId) {
          const cats = await categoriaService.listar();
          categoriaId = cats[0]?.id;
        }
        if (!categoriaId) { setPasso(4); return; }
        const existentes = await contaFixaService.listar();
        if (!existentes.content?.some((cf) => mesmoNome(cf.nome, renda.nome))) {
          await contaFixaService.criar({ descricao: renda.nome, valor: parseCurrencyBR(renda.valor || '0'), diaVencimento: parseInt(renda.diaVencimento || '1'), categoriaId, recorrente: true });
        }
        setPasso(4);
      } catch { } finally { setLoading(false); }
      return;
    }
    if (passo === 4) {
      if (pularMeta) { setPasso(5); return; }
      setLoading(true);
      try {
        const existentes = await metaService.listar();
        if (!existentes.content?.some((m) => mesmoNome(m.nome, meta.nome))) {
          await metaService.criar({ nome: meta.nome, valorTotal: parseCurrencyBR(meta.valorTotal || '0'), valorMensal: parseCurrencyBR(meta.valorMensal || '0'), dataLimite: meta.dataPrevista || undefined });
        }
        setPasso(5);
      } catch { } finally { setLoading(false); }
      return;
    }
  };

  const handleFinalizar = async () => {
    setLoading(true);
    try {
      await onboardingService.completar();
      queryClient.invalidateQueries();
      router.replace('/(app)/');
    } catch { } finally { setLoading(false); }
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
              backgroundColor: i < passo ? colors.brand : i === passo ? colors.brand : colors.border,
              opacity: i === passo ? 1 : 0.4,
            }}
          />
        ))}
      </View>
      <Text style={[styles.stepLabel, { color: colors.textSecondary }]}>Passo {passo + 1} de {PASSOS.length}</Text>
      <Text style={[styles.title, { color: colors.textPrimary }]}>{PASSOS[passo]}</Text>

      {passo === 0 && (
        <View style={styles.form}>
          <Text style={[styles.label, { color: colors.textSecondary }]}>NOME</Text>
          <TextInput
            style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary }]}
            value={carteira.nome}
            onChangeText={(t) => setCarteira((c) => ({ ...c, nome: t }))}
            placeholder="Ex: Conta Principal"
            placeholderTextColor={colors.textMuted}
          />
          <Text style={[styles.label, { color: colors.textSecondary, marginTop: 12 }]}>TIPO</Text>
          <View style={styles.chipRow}>
            {(['CONTA_BANCARIA', 'DINHEIRO', 'POUPANCA'] as TipoCarteira[]).map((t) => (
              <TouchableOpacity
                key={t}
                onPress={() => setCarteira((c) => ({ ...c, tipo: t }))}
                style={[styles.chip, { backgroundColor: carteira.tipo === t ? colors.brand : colors.card, borderColor: carteira.tipo === t ? colors.brand : colors.border }]}
              >
                <Text style={{ color: carteira.tipo === t ? colors.brandText : colors.textSecondary, fontSize: 13 }}>
                  {t === 'CONTA_BANCARIA' ? 'Bancária' : t === 'DINHEIRO' ? 'Dinheiro' : 'Poupança'}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
          <Text style={[styles.label, { color: colors.textSecondary, marginTop: 12 }]}>SALDO INICIAL (R$)</Text>
          <TextInput
            style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary }]}
            value={carteira.saldo}
            onChangeText={(t) => setCarteira((c) => ({ ...c, saldo: maskCurrencyInput(t) }))}
            keyboardType="number-pad"
            placeholder="0,00"
            placeholderTextColor={colors.textMuted}
          />
        </View>
      )}

      {passo === 1 && (
        <View style={styles.form}>
          <Text style={[styles.label, { color: colors.textSecondary }]}>NOME</Text>
          <TextInput
            style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary }]}
            value={conta.nome}
            onChangeText={(t) => setConta((c) => ({ ...c, nome: t }))}
            placeholder="Ex: Cartão Nubank"
            placeholderTextColor={colors.textMuted}
          />
          <Text style={[styles.label, { color: colors.textSecondary, marginTop: 12 }]}>TIPO</Text>
          <View style={styles.chipRow}>
            {(['CREDITO', 'DEBITO', 'DINHEIRO'] as TipoConta[]).map((t) => (
              <TouchableOpacity
                key={t}
                onPress={() => setConta((c) => ({ ...c, tipo: t }))}
                style={[styles.chip, { backgroundColor: conta.tipo === t ? colors.brand : colors.card, borderColor: conta.tipo === t ? colors.brand : colors.border }]}
              >
                <Text style={{ color: conta.tipo === t ? colors.brandText : colors.textSecondary, fontSize: 13 }}>
                  {t === 'CREDITO' ? 'Crédito' : t === 'DEBITO' ? 'Débito' : 'Dinheiro'}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
          {conta.tipo === 'CREDITO' && (
            <>
              <Text style={[styles.label, { color: colors.textSecondary, marginTop: 12 }]}>LIMITE (R$)</Text>
              <TextInput
                style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary }]}
                value={conta.limiteTotal}
                onChangeText={(t) => setConta((c) => ({ ...c, limiteTotal: maskCurrencyInput(t) }))}
                keyboardType="number-pad"
                placeholder="0,00"
                placeholderTextColor={colors.textMuted}
              />
            </>
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
                  style={[styles.gridItem, { backgroundColor: sel ? colors.brand + '20' : colors.card, borderColor: sel ? colors.brand : colors.border }]}
                >
                  <Text style={{ fontSize: 20 }}>{cat.icone}</Text>
                  <Text style={{ color: sel ? colors.brand : colors.textSecondary, fontSize: 12, marginTop: 4, textAlign: 'center' }}>{cat.nome}</Text>
                </TouchableOpacity>
              );
            })}
          </View>
        </View>
      )}

      {passo === 3 && (
        <View style={styles.form}>
          <Text style={[styles.hint, { color: colors.textSecondary }]}>Sua renda principal (opcional):</Text>
          <Text style={[styles.label, { color: colors.textSecondary, marginTop: 8 }]}>NOME</Text>
          <TextInput
            style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary, opacity: pularRenda ? 0.4 : 1 }]}
            value={renda.nome} onChangeText={(t) => setRenda((r) => ({ ...r, nome: t }))}
            placeholder="Ex: Salário" placeholderTextColor={colors.textMuted} editable={!pularRenda}
          />
          <Text style={[styles.label, { color: colors.textSecondary, marginTop: 12 }]}>VALOR MENSAL (R$)</Text>
          <TextInput
            style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary, opacity: pularRenda ? 0.4 : 1 }]}
            value={renda.valor} onChangeText={(t) => setRenda((r) => ({ ...r, valor: maskCurrencyInput(t) }))}
            keyboardType="number-pad" placeholder="0,00" placeholderTextColor={colors.textMuted} editable={!pularRenda}
          />
          <Text style={[styles.label, { color: colors.textSecondary, marginTop: 12 }]}>DIA DO RECEBIMENTO</Text>
          <TextInput
            style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary, opacity: pularRenda ? 0.4 : 1 }]}
            value={renda.diaVencimento} onChangeText={(t) => setRenda((r) => ({ ...r, diaVencimento: t }))}
            keyboardType="number-pad" placeholder="1" placeholderTextColor={colors.textMuted} editable={!pularRenda}
          />
          <TouchableOpacity onPress={() => setPularRenda(!pularRenda)} style={{ flexDirection: 'row', alignItems: 'center', marginTop: 16, gap: 8 }}>
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
          <Text style={[styles.label, { color: colors.textSecondary, marginTop: 8 }]}>NOME</Text>
          <TextInput
            style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary, opacity: pularMeta ? 0.4 : 1 }]}
            value={meta.nome} onChangeText={(t) => setMeta((m) => ({ ...m, nome: t }))}
            placeholder="Ex: Reserva de emergência" placeholderTextColor={colors.textMuted} editable={!pularMeta}
          />
          <Text style={[styles.label, { color: colors.textSecondary, marginTop: 12 }]}>VALOR TOTAL (R$)</Text>
          <TextInput
            style={[styles.input, { backgroundColor: colors.card, borderColor: colors.border, color: colors.textPrimary, opacity: pularMeta ? 0.4 : 1 }]}
            value={meta.valorTotal} onChangeText={(t) => setMeta((m) => ({ ...m, valorTotal: maskCurrencyInput(t) }))}
            keyboardType="number-pad" placeholder="0,00" placeholderTextColor={colors.textMuted} editable={!pularMeta}
          />
          <TouchableOpacity onPress={() => setPularMeta(!pularMeta)} style={{ flexDirection: 'row', alignItems: 'center', marginTop: 16, gap: 8 }}>
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

      <View style={styles.buttons}>
        {passo > 0 && (
          <TouchableOpacity onPress={() => setPasso(passo - 1)} style={[styles.btnSecondary, { borderColor: colors.border }]}>
            <Text style={{ color: colors.textSecondary, fontWeight: '600' }}>Voltar</Text>
          </TouchableOpacity>
        )}
        <View style={{ flex: 1 }} />
        {passo < 5 ? (
          <TouchableOpacity onPress={handleAvancar} disabled={loading} style={[styles.btnPrimary, { backgroundColor: colors.brand, opacity: loading ? 0.6 : 1 }]}>
            {loading ? (
              <ActivityIndicator color={colors.brandText} />
            ) : (
              <Text style={{ color: colors.brandText, fontWeight: '700' }}>Continuar</Text>
            )}
          </TouchableOpacity>
        ) : (
          <TouchableOpacity onPress={handleFinalizar} disabled={loading} style={[styles.btnPrimary, { backgroundColor: '#22C55E', opacity: loading ? 0.6 : 1 }]}>
            {loading ? (
              <ActivityIndicator color="white" />
            ) : (
              <Text style={{ color: 'white', fontWeight: '700' }}>Começar</Text>
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
  form: { gap: 4 },
  label: { fontSize: 10, letterSpacing: 0.8 },
  hint: { fontSize: 13, marginBottom: 8 },
  input: { borderWidth: 1, borderRadius: 8, padding: 12, fontSize: 15 },
  chipRow: { flexDirection: 'row', gap: 8, marginTop: 4 },
  chip: { paddingHorizontal: 14, paddingVertical: 8, borderRadius: 20, borderWidth: 1 },
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
  btnSecondary: { paddingHorizontal: 16, paddingVertical: 12, borderRadius: 8, borderWidth: 1 },
  btnPrimary: { paddingHorizontal: 32, paddingVertical: 12, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
});
