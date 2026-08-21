import React, { useMemo, useRef, useState } from 'react';
import { Modal, ScrollView, Text, TextInput, TouchableOpacity, View } from 'react-native';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Ionicons from '@expo/vector-icons/Ionicons';
import { iconeDecorativo } from '../../../src/utils/acessibilidade';
import { useTheme, useTabBarSpace, spacing, typography, radius } from '../../../src/theme';
import CarrosselCartoes, { useMedidasCartao } from '../../../src/components/carteira/CarrosselCartoes';
import CartaoFisico from '../../../src/components/carteira/CartaoFisico';
import ResumoCartao from '../../../src/components/carteira/ResumoCartao';
import LinhaFatura from '../../../src/components/carteira/LinhaFatura';
import { posicaoDaFatura } from '../../../src/domain/carteiraFormat';
import NovaTransacaoModal from '../../../src/components/NovaTransacaoModal';
import SkeletonBox from '../../../src/components/ui/SkeletonBox';
import { identidadeDoCartao } from '../../../src/domain/emissores';
import { useAuth } from '../../../src/context/AuthContext';
import cartaoService from '../../../src/services/cartaoService';
import { BandeiraCartao, CarteiraCartao, CartaoRequest } from '../../../src/types';
import Field from '../../../src/components/ui/Field';
import { CORES_SUGERIDAS } from '../../../src/domain/emissores';

const BANDEIRAS: BandeiraCartao[] = ['VISA', 'MASTERCARD', 'ELO', 'AMEX', 'HIPERCARD', 'OUTRA'];
const BANDEIRA_ROTULO: Record<BandeiraCartao, string> = {
  VISA: 'Visa', MASTERCARD: 'Mastercard', ELO: 'Elo',
  AMEX: 'Amex', HIPERCARD: 'Hipercard', OUTRA: 'Outra',
};
import { maskCurrencyInput, parseCurrencyBR } from '../../../src/utils/format';

/**
 * Carteira: carrossel de cartões, resumo do selecionado e as faturas da
 * janela. Réplica da referência medida em mobile/.design/MEDICOES-carteira.md.
 *
 * A rota continua /more/faturas — cinco pontos de navegação apontam para ela
 * (ajustes, notificações, home, ComposicaoMetricaModal, rotaDaNavegacao).
 */
export default function CarteiraScreen() {
  const colors = useTheme();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const tabBarSpace = useTabBarSpace();
  const queryClient = useQueryClient();
  const { usuario } = useAuth();
  const { altura: alturaCartao } = useMedidasCartao();

  const [indice, setIndice] = useState(0);
  const [novaDespesaVisible, setNovaDespesaVisible] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  /** null = criando; id = editando aquele cartão. */
  const [editandoId, setEditandoId] = useState<number | null>(null);
  const [nome, setNome] = useState('');
  const [banco, setBanco] = useState('');
  const [limite, setLimite] = useState('');
  const [diaFechamento, setDiaFechamento] = useState('');
  const [diaVencimento, setDiaVencimento] = useState('');
  const [ultimosDigitos, setUltimosDigitos] = useState('');
  const [bandeira, setBandeira] = useState<BandeiraCartao | null>(null);
  const [cor, setCor] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  // Encadeamento de foco: "próximo" no teclado leva ao campo seguinte, e o
  // último submete. É o primeiro fluxo do app com foco encadeado.
  const refBanco = useRef<TextInput>(null);
  const refLimite = useRef<TextInput>(null);
  const refDigitos = useRef<TextInput>(null);
  const refFechamento = useRef<TextInput>(null);
  const refVencimento = useRef<TextInput>(null);

  // Chave sob o prefixo ['cartoes']: as quatro invalidações que já existem no
  // app passam a atualizar a carteira sozinhas, sem novo call site.
  const { data: cartoes = [], isLoading, isError, refetch } = useQuery({
    queryKey: ['cartoes', 'carteira'],
    queryFn: () => cartaoService.carteira(),
  });

  const selecionado = cartoes[Math.min(indice, Math.max(cartoes.length - 1, 0))];
  const identidade = useMemo(
    () => (selecionado ? identidadeDoCartao(selecionado) : null),
    [selecionado],
  );

  const hoje = new Date();
  const hojeMes = hoje.getMonth() + 1;
  const hojeAno = hoje.getFullYear();

  const resetForm = () => {
    setEditandoId(null);
    setNome(''); setBanco(''); setLimite('');
    setDiaFechamento(''); setDiaVencimento('');
    setUltimosDigitos(''); setBandeira(null); setCor(null);
    setFormError(null);
  };

  const abrirNovo = () => { resetForm(); setModalVisible(true); };

  const abrirEdicao = (c: CarteiraCartao) => {
    setEditandoId(c.cartaoId);
    setNome(c.nome);
    setBanco(c.banco ?? '');
    setLimite(maskCurrencyInput(Math.round(Number(c.limiteTotal ?? 0) * 100).toString()));
    setDiaFechamento(c.diaFechamento != null ? String(c.diaFechamento) : '');
    setDiaVencimento(c.diaVencimento != null ? String(c.diaVencimento) : '');
    setUltimosDigitos(c.ultimosDigitos ?? '');
    setBandeira((c.bandeira as BandeiraCartao) ?? null);
    setCor(c.cor ?? null);
    setFormError(null);
    setModalVisible(true);
  };

  const salvarMutation = useMutation({
    mutationFn: (req: CartaoRequest) => (editandoId != null
      ? cartaoService.atualizar(editandoId, req)
      : cartaoService.criar(req)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cartoes'] });
      setModalVisible(false);
      resetForm();
    },
    onError: (err: any) => setFormError(err?.userMessage
      ?? (editandoId != null ? 'Erro ao salvar cartão.' : 'Erro ao cadastrar cartão.')),
  });

  const salvarCartao = () => {
    setFormError(null);
    if (!nome.trim()) { setFormError('Nome do cartão é obrigatório.'); return; }
    const v = parseCurrencyBR(limite);
    if (isNaN(v) || v <= 0) { setFormError('Limite total obrigatório e positivo.'); return; }
    const fech = parseInt(diaFechamento, 10);
    const venc = parseInt(diaVencimento, 10);
    if (isNaN(fech) || fech < 1 || fech > 31) { setFormError('Dia de fechamento deve estar entre 1 e 31.'); return; }
    if (isNaN(venc) || venc < 1 || venc > 31) { setFormError('Dia de vencimento deve estar entre 1 e 31.'); return; }
    // O backend valida ^[0-9]{4}$; vazio é aceito, meio preenchido não.
    if (ultimosDigitos && ultimosDigitos.length !== 4) {
      setFormError('Informe os 4 últimos dígitos, ou deixe em branco.'); return;
    }
    salvarMutation.mutate({
      nome: nome.trim(),
      limiteTotal: v,
      diaFechamento: fech,
      diaVencimento: venc,
      banco: banco.trim() || undefined,
      ultimosDigitos: ultimosDigitos || undefined,
      bandeira: bandeira ?? undefined,
      cor: cor ?? undefined,
    });
  };

  const salvando = salvarMutation.status === 'pending';

  const abrirFatura = (mes: number, ano: number) => {
    if (!selecionado) return;
    router.push({
      pathname: '/more/fatura',
      params: { cartaoId: String(selecionado.cartaoId), mes: String(mes), ano: String(ano), nome: selecionado.nome },
    } as never);
  };

  const campo = {
    borderWidth: 1, borderRadius: radius.md, padding: spacing.md, fontSize: 15,
    marginBottom: 14, backgroundColor: colors.fieldBg, borderColor: colors.border,
    color: colors.textPrimary,
  };
  const rotulo = { ...typography.meta, fontWeight: '600' as const, color: colors.textSecondary, marginBottom: 6, marginTop: spacing.xs };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScrollView contentContainerStyle={{ paddingBottom: tabBarSpace }}>
        <View style={{
          flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
          paddingTop: insets.top + spacing.sm, paddingHorizontal: spacing.lg, paddingBottom: spacing.lg,
        }}>
          <Text style={{ color: colors.textPrimary, fontSize: 26, lineHeight: 32, fontWeight: '800', letterSpacing: -0.6 }}>
            Carteira
          </Text>
          <TouchableOpacity
            onPress={abrirNovo}
            accessibilityRole="button"
            accessibilityLabel="Cartão"
            accessibilityHint="Cadastra um novo cartão"
            style={{ flexDirection: 'row', alignItems: 'center', gap: 4, minHeight: 44, paddingLeft: spacing.md }}
          >
            <Ionicons name="add" size={20} color={colors.brandFg} {...iconeDecorativo} />
            <Text style={{ ...typography.value, color: colors.brandFg }}>Cartão</Text>
          </TouchableOpacity>
        </View>

        {isLoading ? (
          <View style={{ gap: spacing.xl }}>
            <CarrosselCartoes cartoes={[]} indice={0} onIndice={() => {}} carregando />
            <View style={{ paddingHorizontal: spacing.lg, gap: spacing.md }}>
              <SkeletonBox width="100%" height={72} borderRadius={radius.md} />
              <SkeletonBox width="100%" height={72} borderRadius={radius.md} />
            </View>
          </View>
        ) : isError ? (
          <View style={{ alignItems: 'center', paddingVertical: 56, paddingHorizontal: spacing.xxl }}>
            <Text style={{ fontSize: 34 }}>📶</Text>
            <Text style={{ ...typography.cardTitle, color: colors.textPrimary, marginTop: spacing.md, textAlign: 'center' }}>
              Não deu para carregar sua carteira
            </Text>
            <Text style={{ ...typography.body, color: colors.textSecondary, marginTop: spacing.xs, textAlign: 'center' }}>
              Verifique sua conexão e tente de novo.
            </Text>
            <TouchableOpacity
              onPress={() => refetch()}
              accessibilityRole="button"
              accessibilityLabel="Tentar carregar a carteira de novo"
              style={{
                marginTop: spacing.lg, paddingHorizontal: spacing.xl, height: 44,
                borderRadius: radius.md, alignItems: 'center', justifyContent: 'center',
                backgroundColor: colors.brand,
              }}
            >
              <Text style={{ ...typography.button, color: colors.brandText }}>Tentar de novo</Text>
            </TouchableOpacity>
          </View>
        ) : cartoes.length === 0 ? (
          <View style={{ alignItems: 'center', paddingVertical: 56, paddingHorizontal: spacing.xxl }}>
            <Text style={{ fontSize: 40 }}>💳</Text>
            <Text style={{ ...typography.cardTitle, color: colors.textPrimary, marginTop: spacing.md, textAlign: 'center' }}>
              Nenhum cartão cadastrado
            </Text>
            <Text style={{ ...typography.body, color: colors.textSecondary, marginTop: spacing.xs, textAlign: 'center' }}>
              Cadastre seu cartão de crédito com limite, banco e datas de fechamento para acompanhar as faturas.
            </Text>
            <TouchableOpacity
              onPress={abrirNovo}
              accessibilityRole="button"
              accessibilityLabel="Cadastrar cartão"
              style={{
                marginTop: spacing.lg, paddingHorizontal: spacing.xl, height: 44,
                borderRadius: radius.md, alignItems: 'center', justifyContent: 'center',
                backgroundColor: colors.brand,
              }}
            >
              <Text style={{ ...typography.button, color: colors.brandText }}>Cadastrar cartão</Text>
            </TouchableOpacity>
          </View>
        ) : (
          <>
            <CarrosselCartoes
              cartoes={cartoes}
              indice={indice}
              onIndice={setIndice}
              titular={usuario?.nome}
            />

            {selecionado && (
              <>
                <View style={{ marginTop: spacing.xl }}>
                  <ResumoCartao cartao={selecionado} />
                </View>

                {/* Nome e acoes em linhas separadas: dividir a linha com o lapis e o
                    botao de despesa sobrava ~160pt pro nome e truncava qualquer cartao
                    de nome medio ("Nubank Ultraviol..."). */}
                <View style={{
                  flexDirection: 'row', alignItems: 'center',
                  paddingHorizontal: spacing.lg, marginTop: spacing.xxl, gap: spacing.sm,
                }}>
                  <Text numberOfLines={2} style={{ ...typography.section, color: colors.textPrimary, flex: 1 }}>
                    {selecionado.nome}
                  </Text>
                  <TouchableOpacity
                    onPress={() => abrirEdicao(selecionado)}
                    accessibilityRole="button"
                    accessibilityLabel={`Editar cartão ${selecionado.nome}`}
                    hitSlop={8}
                    style={{ minHeight: 44, minWidth: 44, alignItems: 'center', justifyContent: 'center' }}
                  >
                    <Ionicons name="create-outline" size={20} color={colors.textSecondary} />
                  </TouchableOpacity>
                </View>

                <View style={{
                  flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
                  paddingHorizontal: spacing.lg, marginTop: spacing.xs, gap: spacing.md,
                }}>
                  <Text style={{ ...typography.body, color: colors.textSecondary, flex: 1 }}>
                    {selecionado.ultimosDigitos ? `•••• ${selecionado.ultimosDigitos} · ` : ''}
                    Vence dia {selecionado.diaVencimento ?? '—'}
                  </Text>
                  <TouchableOpacity
                    onPress={() => setNovaDespesaVisible(true)}
                    accessibilityRole="button"
                    accessibilityLabel={`Nova despesa no cartão ${selecionado.nome}`}
                    style={{
                      flexDirection: 'row', alignItems: 'center', gap: 4,
                      paddingHorizontal: 14, minHeight: 44, justifyContent: 'center',
                      borderRadius: radius.pill, borderWidth: 1, borderColor: colors.brand,
                    }}
                  >
                    <Ionicons name="add" size={15} color={colors.brandFg} />
                    <Text style={{ ...typography.meta, fontWeight: '600', color: colors.brandFg }}>
                      Nova Despesa
                    </Text>
                  </TouchableOpacity>
                </View>

                <View style={{ paddingHorizontal: spacing.lg, gap: spacing.md - 2, marginTop: spacing.lg }}>
                  {selecionado.faturas.map(f => (
                    <LinhaFatura
                      key={`${f.ano}-${f.mes}`}
                      fatura={f}
                      posicao={posicaoDaFatura(f, hojeMes, hojeAno)}
                      corDestaque={identidade?.from ?? colors.brand}
                      onPress={() => abrirFatura(f.mes, f.ano)}
                    />
                  ))}
                </View>
              </>
            )}
          </>
        )}
      </ScrollView>

      {selecionado && (
        <NovaTransacaoModal
          visible={novaDespesaVisible}
          onClose={() => setNovaDespesaVisible(false)}
          cartaoIdInicial={selecionado.cartaoId}
          onSaved={() => setNovaDespesaVisible(false)}
        />
      )}

      <Modal
        visible={modalVisible}
        animationType="slide"
        presentationStyle="pageSheet"
        onRequestClose={() => { setModalVisible(false); resetForm(); }}
      >
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{
            flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
            padding: spacing.lg, borderBottomWidth: 1, borderBottomColor: colors.border,
          }}>
            <TouchableOpacity
              onPress={() => { setModalVisible(false); resetForm(); }}
              accessibilityRole="button"
              accessibilityLabel="Cancelar"
            >
              <Text style={{ ...typography.value, fontWeight: '500', color: colors.brand }}>Cancelar</Text>
            </TouchableOpacity>
            <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>
              {editandoId != null ? 'Editar Cartão' : 'Novo Cartão'}
            </Text>
            <TouchableOpacity
              disabled={salvando}
              onPress={salvarCartao}
              accessibilityRole="button"
              accessibilityState={{ disabled: salvando }}
            >
              <Text style={{ ...typography.value, color: salvando ? colors.textMuted : colors.brand }}>
                Salvar
              </Text>
            </TouchableOpacity>
          </View>

          <ScrollView contentContainerStyle={{ padding: spacing.lg }} keyboardShouldPersistTaps="handled">
            {/* Prévia ao vivo: o cartão toma a identidade do emissor enquanto o
                usuário digita, com bandeira, final e cor escolhidos. */}
            <View style={{ alignItems: 'center', marginBottom: spacing.xl, height: alturaCartao }}>
              <PreviaCartao
                nome={nome}
                banco={banco}
                cor={cor}
                ultimosDigitos={ultimosDigitos}
                bandeira={bandeira}
                titular={usuario?.nome}
              />
            </View>

            <Field
              testID="card-name"
              label="Nome do cartão"
              value={nome}
              onChangeText={setNome}
              placeholder="Ex.: Nubank Ultravioleta"
              returnKeyType="next"
              submitBehavior="submit"
              onSubmitEditing={() => refBanco.current?.focus()}
            />

            <Field
              ref={refBanco}
              testID="card-bank"
              label="Banco"
              value={banco}
              onChangeText={setBanco}
              placeholder="Ex.: Nubank, Itaú, Inter"
              autoCapitalize="words"
              returnKeyType="next"
              submitBehavior="submit"
              onSubmitEditing={() => refLimite.current?.focus()}
            />

            <Field
              ref={refLimite}
              testID="card-limit"
              label="Limite total"
              value={limite}
              onChangeText={t => setLimite(maskCurrencyInput(t))}
              keyboardType="number-pad"
              placeholder="0,00"
              returnKeyType="next"
              submitBehavior="submit"
              onSubmitEditing={() => refDigitos.current?.focus()}
            />

            <Field
              ref={refDigitos}
              testID="card-digits"
              label="4 últimos dígitos"
              value={ultimosDigitos}
              onChangeText={t => setUltimosDigitos(t.replace(/\D/g, '').slice(0, 4))}
              keyboardType="number-pad"
              maxLength={4}
              placeholder="Ex.: 4291"
              returnKeyType="next"
              submitBehavior="submit"
              onSubmitEditing={() => refFechamento.current?.focus()}
            />

            <View style={{ flexDirection: 'row', gap: spacing.md }}>
              <View style={{ flex: 1 }}>
                <Field
                  ref={refFechamento}
                  testID="card-closing"
                  label="Dia de fechamento"
                  value={diaFechamento}
                  onChangeText={t => setDiaFechamento(t.replace(/\D/g, '').slice(0, 2))}
                  keyboardType="number-pad"
                  placeholder="Ex.: 28"
                  returnKeyType="next"
                  submitBehavior="submit"
                  onSubmitEditing={() => refVencimento.current?.focus()}
                />
              </View>
              <View style={{ flex: 1 }}>
                <Field
                  ref={refVencimento}
                  testID="card-due"
                  label="Dia de vencimento"
                  value={diaVencimento}
                  onChangeText={t => setDiaVencimento(t.replace(/\D/g, '').slice(0, 2))}
                  keyboardType="number-pad"
                  placeholder="Ex.: 5"
                  returnKeyType="done"
                  onSubmitEditing={salvarCartao}
                />
              </View>
            </View>

            <Text style={rotulo}>Bandeira</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, marginBottom: spacing.lg }}>
              {BANDEIRAS.map(b => (
                <TouchableOpacity
                  key={b}
                  onPress={() => setBandeira(bandeira === b ? null : b)}
                  accessibilityRole="button"
                  accessibilityLabel={`Bandeira ${BANDEIRA_ROTULO[b]}`}
                  accessibilityState={{ selected: bandeira === b }}
                  style={{
                    paddingHorizontal: spacing.md, minHeight: 44, justifyContent: 'center',
                    borderRadius: radius.pill, borderWidth: 1,
                    backgroundColor: bandeira === b ? colors.brand : colors.card,
                    borderColor: bandeira === b ? colors.brand : colors.border,
                  }}
                >
                  <Text style={{
                    ...typography.chip,
                    color: bandeira === b ? colors.brandText : colors.textSecondary,
                  }}>
                    {BANDEIRA_ROTULO[b]}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>

            <Text style={rotulo}>Cor do cartão</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md, marginBottom: spacing.lg }}>
              <TouchableOpacity
                onPress={() => setCor(null)}
                accessibilityRole="button"
                accessibilityLabel="Usar a cor do emissor"
                accessibilityState={{ selected: cor === null }}
                style={{
                  minHeight: 44, paddingHorizontal: spacing.md, justifyContent: 'center',
                  borderRadius: radius.pill, borderWidth: 1,
                  borderColor: cor === null ? colors.brand : colors.border,
                  backgroundColor: colors.card,
                }}
              >
                <Text style={{ ...typography.chip, color: cor === null ? colors.brandFg : colors.textSecondary }}>
                  Do emissor
                </Text>
              </TouchableOpacity>
              {CORES_SUGERIDAS.map(c => (
                <TouchableOpacity
                  key={c}
                  onPress={() => setCor(c)}
                  accessibilityRole="button"
                  accessibilityLabel={`Cor ${c}`}
                  accessibilityState={{ selected: cor === c }}
                  style={{
                    width: 44, height: 44, borderRadius: 22, backgroundColor: c,
                    borderWidth: cor === c ? 3 : 1,
                    borderColor: cor === c ? colors.brand : colors.border,
                  }}
                />
              ))}
            </View>

            {formError && <Text style={{ ...typography.body, color: colors.danger }}>{formError}</Text>}
          </ScrollView>
        </View>
      </Modal>
    </View>
  );
}

/** Prévia ao vivo do cartão dentro do formulário de cadastro. */
const PreviaCartao = ({ nome, banco, cor, ultimosDigitos, bandeira, titular }: {
  nome: string; banco: string; cor?: string | null;
  ultimosDigitos?: string | null; bandeira?: string | null; titular?: string | null;
}) => {
  const { largura } = useMedidasCartao();
  return (
    <CartaoFisico
      largura={largura}
      nome={nome || 'Novo cartão'}
      banco={banco}
      cor={cor}
      ultimosDigitos={ultimosDigitos}
      bandeira={bandeira}
      titular={titular}
    />
  );
};
