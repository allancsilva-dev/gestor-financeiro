import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Text, TextInput, TouchableOpacity, useWindowDimensions, View } from 'react-native';
import { useRouter } from 'expo-router';
import Ionicons from '@expo/vector-icons/Ionicons';
import { useTheme, radius, spacing, typography, numeric } from '../src/theme';
import { onboardingService } from '../src/services/onboardingService';
import { OnboardingFinalizarRequest } from '../src/services/onboardingService';
import { useAuth } from '../src/context/AuthContext';
import { CATEGORIAS_INICIAIS } from '../src/domain/categoriasIniciais';
import CartaoFisico from '../src/components/carteira/CartaoFisico';
import {
  formatCurrency,
  isValidDateBR,
  maskCurrencyInput,
  maskDateInput,
  parseCurrencyBR,
  parseDateBR,
} from '../src/utils/format';
import { isValidDayOfMonth } from '../src/utils/validate';
import { camposDeErro, chavesDeErro, ehSessaoExpirada, mensagemDeErro } from '../src/utils/erros';
import { lerRascunho, limparRascunho, salvarRascunho } from '../src/store/onboardingRascunho';
import Botao from '../src/components/ui/Botao';
import Card from '../src/components/ui/Card';
import Chip from '../src/components/ui/Chip';
import Field from '../src/components/ui/Field';
import IconTile from '../src/components/ui/IconTile';
import TelaFluxo from '../src/components/ui/TelaFluxo';

type TipoContaInicial = 'DINHEIRO' | 'CONTA_BANCARIA' | 'POUPANCA';
type Passo = 'conta' | 'renda' | 'categorias' | 'cartao' | 'meta' | 'revisao';

const PASSOS: Passo[] = ['conta', 'renda', 'categorias', 'cartao', 'meta', 'revisao'];

// Chave do backend (details do 400) → passo dono do campo. Quando a validação
// falha no envio único, o wizard volta para onde o dado foi digitado em vez de
// mostrar "dados inválidos" na tela de revisão.
const PASSO_DO_CAMPO: Record<string, Passo> = {
  'carteira.nome': 'conta',
  'carteira.saldo': 'conta',
  'carteira.subtipo': 'conta',
  'renda.nome': 'renda',
  'renda.valor': 'renda',
  'renda.diaVencimento': 'renda',
  'cartao.nome': 'cartao',
  'cartao.limiteTotal': 'cartao',
  'cartao.diaFechamento': 'cartao',
  'cartao.diaVencimento': 'cartao',
  'meta.nome': 'meta',
  'meta.valorTotal': 'meta',
};

const MAPA_DE_CAMPOS = {
  'carteira.nome': 'contaNome',
  'carteira.saldo': 'contaSaldo',
  'renda.nome': 'rendaNome',
  'renda.valor': 'rendaValor',
  'renda.diaVencimento': 'rendaDia',
  'cartao.nome': 'cartaoNome',
  'cartao.limiteTotal': 'cartaoLimite',
  'cartao.diaFechamento': 'cartaoFechamento',
  'cartao.diaVencimento': 'cartaoVencimento',
  'meta.nome': 'metaNome',
  'meta.valorTotal': 'metaValor',
} as const;

type CampoDaTela = (typeof MAPA_DE_CAMPOS)[keyof typeof MAPA_DE_CAMPOS];
type Erros = Partial<Record<CampoDaTela, string>>;

/**
 * Onboarding: é aqui que os dados do usuário entram pela primeira vez. Um passo
 * obrigatório (conta principal) e quatro opcionais, puláveis em um toque; tudo
 * vai num único POST idempotente no fim (`/v1/onboarding/finalizar`, que já
 * aceita carteira + renda + categorias + cartão + meta desde o PR-F3-03).
 */
export default function OnboardingScreen() {
  const colors = useTheme();
  const router = useRouter();
  const { usuario, updateUsuario, logout } = useAuth();
  const { width: larguraDaTela } = useWindowDimensions();

  const [passo, setPasso] = useState<Passo>('conta');
  const [enviando, setEnviando] = useState(false);
  const [erroGeral, setErroGeral] = useState<string | null>(null);
  const [sessaoPerdida, setSessaoPerdida] = useState(false);
  const [erros, setErros] = useState<Erros>({});
  const rascunhoCarregado = useRef(false);

  // O nome não nasce mais "Conta Principal": principal virou uma propriedade da conta
  // (migration V66), não um nome. O que identifica a conta para o titular é o banco.
  const [contaBanco, setContaBanco] = useState('');
  const [contaNome, setContaNome] = useState('');
  // Saldo e o ultimo campo do passo e o teclado o cobre. O RN so rola um TextInput para a area
  // visivel quando ele RECEBE FOCO — entao encadear "proximo" resolve as duas coisas de uma vez:
  // a pessoa nao precisa fechar o teclado nem rolar, e o campo aparece sozinho.
  const contaNomeRef = useRef<TextInput>(null);
  const contaSaldoRef = useRef<TextInput>(null);
  const [contaTipo, setContaTipo] = useState<TipoContaInicial>('CONTA_BANCARIA');
  const [contaSaldo, setContaSaldo] = useState('');

  const [comRenda, setComRenda] = useState(false);
  const [rendaNome, setRendaNome] = useState('Salário');
  const [rendaValor, setRendaValor] = useState('');
  const [rendaDia, setRendaDia] = useState('');

  const [categoriasEscolhidas, setCategoriasEscolhidas] = useState<string[]>(
    CATEGORIAS_INICIAIS.map((c) => c.nome),
  );

  const [comCartao, setComCartao] = useState(false);
  const [cartaoNome, setCartaoNome] = useState('');
  const [cartaoLimite, setCartaoLimite] = useState('');
  const [cartaoFechamento, setCartaoFechamento] = useState('');
  const [cartaoVencimento, setCartaoVencimento] = useState('');

  const [comMeta, setComMeta] = useState(false);
  const [metaNome, setMetaNome] = useState('');
  const [metaValor, setMetaValor] = useState('');
  const [metaData, setMetaData] = useState('');

  // Restaura o que já tinha sido digitado antes de o app ser fechado
  useEffect(() => {
    let ativo = true;
    lerRascunho().then((rascunho) => {
      if (!ativo || !rascunho) { rascunhoCarregado.current = true; return; }
      if (rascunho.conta) {
        setContaNome(rascunho.conta.nome);
        setContaBanco(rascunho.conta.banco ?? '');
        setContaTipo(rascunho.conta.tipo as TipoContaInicial);
        setContaSaldo(rascunho.conta.saldo);
      }
      if (rascunho.renda) {
        setComRenda(true);
        setRendaNome(rascunho.renda.nome);
        setRendaValor(rascunho.renda.valor);
        setRendaDia(rascunho.renda.dia);
      }
      if (rascunho.categorias) setCategoriasEscolhidas(rascunho.categorias);
      if (rascunho.cartao) {
        setComCartao(true);
        setCartaoNome(rascunho.cartao.nome);
        setCartaoLimite(rascunho.cartao.limite);
        setCartaoFechamento(rascunho.cartao.fechamento);
        setCartaoVencimento(rascunho.cartao.vencimento);
      }
      if (rascunho.meta) {
        setComMeta(true);
        setMetaNome(rascunho.meta.nome);
        setMetaValor(rascunho.meta.valor);
        setMetaData(rascunho.meta.data);
      }
      if (rascunho.passo && PASSOS.includes(rascunho.passo as Passo)) setPasso(rascunho.passo as Passo);
      rascunhoCarregado.current = true;
    });
    return () => { ativo = false; };
  }, []);

  // Salva o rascunho a cada mudança — só depois da restauração, para não
  // sobrescrever o que está guardado com os valores iniciais do formulário.
  useEffect(() => {
    if (!rascunhoCarregado.current) return;
    salvarRascunho({
      passo,
      conta: { nome: contaNome, tipo: contaTipo, saldo: contaSaldo, banco: contaBanco },
      renda: comRenda ? { nome: rendaNome, valor: rendaValor, dia: rendaDia } : null,
      categorias: categoriasEscolhidas,
      cartao: comCartao
        ? { nome: cartaoNome, limite: cartaoLimite, fechamento: cartaoFechamento, vencimento: cartaoVencimento }
        : null,
      meta: comMeta ? { nome: metaNome, valor: metaValor, data: metaData } : null,
    });
  }, [
    passo, contaNome, contaBanco, contaTipo, contaSaldo,
    comRenda, rendaNome, rendaValor, rendaDia, categoriasEscolhidas,
    comCartao, cartaoNome, cartaoLimite, cartaoFechamento, cartaoVencimento,
    comMeta, metaNome, metaValor, metaData,
  ]);

  const indice = PASSOS.indexOf(passo);
  const irPara = (destino: Passo) => { setErroGeral(null); setPasso(destino); };
  const avancar = () => irPara(PASSOS[Math.min(indice + 1, PASSOS.length - 1)]);
  const voltar = indice === 0 ? undefined : () => irPara(PASSOS[indice - 1]);

  const limparErro = (campo: CampoDaTela) =>
    setErros((atual) => (atual[campo] ? { ...atual, [campo]: undefined } : atual));

  const validarConta = (): boolean => {
    const novos: Erros = {};
    if (contaNome.trim().length < 2) novos.contaNome = 'Informe o nome da conta (mínimo 2 caracteres).';
    const saldo = parseCurrencyBR(contaSaldo || '0');
    if (!Number.isFinite(saldo) || saldo < 0) novos.contaSaldo = 'Saldo deve ser zero ou positivo.';
    setErros(novos);
    return Object.keys(novos).length === 0;
  };

  const validarRenda = (): boolean => {
    const novos: Erros = {};
    if (rendaNome.trim().length < 2) novos.rendaNome = 'Informe um nome (mínimo 2 caracteres).';
    const valor = parseCurrencyBR(rendaValor || '0');
    if (!Number.isFinite(valor) || valor <= 0) novos.rendaValor = 'Informe um valor maior que zero.';
    if (!isValidDayOfMonth(rendaDia)) novos.rendaDia = 'Dia deve estar entre 1 e 31.';
    setErros(novos);
    return Object.keys(novos).length === 0;
  };

  const validarCartao = (): boolean => {
    const novos: Erros = {};
    if (cartaoNome.trim().length < 2) novos.cartaoNome = 'Informe o nome do cartão.';
    const limite = parseCurrencyBR(cartaoLimite || '0');
    if (!Number.isFinite(limite) || limite < 0) novos.cartaoLimite = 'Limite deve ser zero ou positivo.';
    if (!isValidDayOfMonth(cartaoFechamento)) novos.cartaoFechamento = 'Dia deve estar entre 1 e 31.';
    if (!isValidDayOfMonth(cartaoVencimento)) novos.cartaoVencimento = 'Dia deve estar entre 1 e 31.';
    setErros(novos);
    return Object.keys(novos).length === 0;
  };

  const validarMeta = (): boolean => {
    const novos: Erros = {};
    if (metaNome.trim().length < 2) novos.metaNome = 'Informe o nome da meta.';
    const valor = parseCurrencyBR(metaValor || '0');
    if (!Number.isFinite(valor) || valor <= 0) novos.metaValor = 'Informe um valor maior que zero.';
    setErros(novos);
    // Data é opcional, mas se veio precisa existir no calendário
    const dataInvalida = Boolean(metaData) && !isValidDateBR(metaData);
    if (dataInvalida) setErroGeral('Data inválida. Use o formato DD/MM/AAAA.');
    return !dataInvalida && Object.keys(novos).length === 0;
  };

  const continuar = () => {
    setErroGeral(null);
    if (passo === 'conta' && !validarConta()) return;
    if (passo === 'renda' && comRenda && !validarRenda()) return;
    if (passo === 'cartao' && comCartao && !validarCartao()) return;
    if (passo === 'meta' && comMeta && !validarMeta()) return;
    avancar();
  };

  const pular = () => {
    setErros({});
    setErroGeral(null);
    if (passo === 'renda') setComRenda(false);
    if (passo === 'categorias') setCategoriasEscolhidas([]);
    if (passo === 'cartao') setComCartao(false);
    if (passo === 'meta') setComMeta(false);
    avancar();
  };

  const payload = useMemo((): OnboardingFinalizarRequest => {
    const base: OnboardingFinalizarRequest = {
      carteira: {
        nome: contaNome.trim(),
        subtipo: contaTipo === 'CONTA_BANCARIA' ? 'CORRENTE' : contaTipo,
        saldo: parseCurrencyBR(contaSaldo || '0'),
        banco: contaBanco.trim() || undefined,
      },
    };
    if (comRenda) {
      base.renda = {
        nome: rendaNome.trim(),
        valor: parseCurrencyBR(rendaValor || '0'),
        diaVencimento: Number(rendaDia),
      };
    }
    const categorias = CATEGORIAS_INICIAIS.filter((c) => categoriasEscolhidas.includes(c.nome));
    if (categorias.length > 0) {
      base.categorias = categorias.map((c) => ({ nome: c.nome, cor: c.cor, icone: c.icone }));
    }
    if (comCartao) {
      base.cartao = {
        nome: cartaoNome.trim(),
        limiteTotal: parseCurrencyBR(cartaoLimite || '0'),
        diaFechamento: Number(cartaoFechamento),
        diaVencimento: Number(cartaoVencimento),
      };
    }
    if (comMeta) {
      base.meta = {
        nome: metaNome.trim(),
        valorTotal: parseCurrencyBR(metaValor || '0'),
        dataLimite: metaData ? parseDateBR(metaData) : undefined,
      };
    }
    return base;
  }, [
    contaNome, contaTipo, contaSaldo, comRenda, rendaNome, rendaValor, rendaDia,
    categoriasEscolhidas, comCartao, cartaoNome, cartaoLimite, cartaoFechamento,
    cartaoVencimento, comMeta, metaNome, metaValor, metaData,
  ]);

  const concluir = async () => {
    setEnviando(true);
    setErroGeral(null);
    setSessaoPerdida(false);
    try {
      const user = await onboardingService.finalizar(payload);
      await limparRascunho();
      await updateUsuario(user);
      router.replace('/(app)/');
    } catch (err) {
      const doBackend = camposDeErro(err, MAPA_DE_CAMPOS);
      setErros(doBackend);
      setErroGeral(mensagemDeErro(err, 'Não foi possível salvar sua configuração. Tente novamente.'));
      if (ehSessaoExpirada(err)) setSessaoPerdida(true);

      // Volta para o passo dono do primeiro campo recusado pelo backend — na
      // revisão o usuário não teria onde corrigir.
      const destino = chavesDeErro(err).map((chave) => PASSO_DO_CAMPO[chave]).find(Boolean);
      if (destino) setPasso(destino);
    } finally {
      setEnviando(false);
    }
  };

  const voltarAoLogin = async () => {
    await logout();
    router.replace('/(auth)/login');
  };

  const alternarCategoria = (nome: string) =>
    setCategoriasEscolhidas((atual) =>
      atual.includes(nome) ? atual.filter((n) => n !== nome) : [...atual, nome],
    );

  // Sem isso o usuário não sabe que pode simplesmente seguir em frente: o botão
  // "Pular por agora" sozinho não diz que o passo inteiro é opcional.
  const PASSOS_OPCIONAIS: Passo[] = ['renda', 'categorias', 'cartao', 'meta'];
  const selo = passo === 'conta'
    ? { texto: 'CONTA CRIADA', tom: 'success' as const }
    : PASSOS_OPCIONAIS.includes(passo)
      ? { texto: 'OPCIONAL', tom: 'info' as const }
      : undefined;

  const cabecalho: Record<Passo, { titulo: string; subtitulo: string }> = {
    conta: {
      titulo: 'Sua conta principal',
      subtitulo: 'Sua conta já existe — falta dizer onde seu dinheiro está hoje. É o único passo obrigatório; os próximos você pode pular.',
    },
    renda: {
      titulo: 'Sua renda mensal',
      subtitulo: 'Entra todo mês na mesma data? Cadastre uma vez e ela se repete.',
    },
    categorias: {
      titulo: 'Categorias de gasto',
      subtitulo: 'Escolha as que fazem sentido para você. Dá para mudar depois.',
    },
    cartao: {
      titulo: 'Cartão de crédito',
      subtitulo: 'Com fechamento e vencimento, o app monta a fatura sozinho.',
    },
    meta: {
      titulo: 'Uma meta para começar',
      subtitulo: 'Um objetivo concreto ajuda a guardar dinheiro de verdade.',
    },
    revisao: {
      titulo: 'Tudo pronto?',
      subtitulo: 'Confira o que vamos criar agora. O que faltar você adiciona depois.',
    },
  };

  const rodape = (
    <>
      {passo === 'revisao' ? (
        <>
          <Botao
            testID="onboarding-concluir"
            titulo="Concluir"
            onPress={concluir}
            carregando={enviando}
          />
          {sessaoPerdida ? (
            <Botao titulo="Entrar de novo" variante="secundario" onPress={voltarAoLogin} />
          ) : null}
        </>
      ) : (
        <>
          <Botao testID="onboarding-continuar" titulo="Continuar" onPress={continuar} />
          {passo !== 'conta' ? (
            <Botao testID="onboarding-pular" titulo="Pular por agora" variante="texto" onPress={pular} />
          ) : null}
        </>
      )}
    </>
  );

  return (
    <TelaFluxo
      titulo={cabecalho[passo].titulo}
      subtitulo={cabecalho[passo].subtitulo}
      passo={indice + 1}
      totalDePassos={PASSOS.length}
      onVoltar={voltar}
      selo={selo}
      rodape={rodape}
    >
      {passo === 'conta' ? (
        <View>
          <Field
            testID="onboarding-account-bank"
            label="Banco"
            value={contaBanco}
            onChangeText={(t) => {
              // O nome acompanha o banco enquanto a pessoa não escolheu um próprio: quem tem
              // uma conta só quase sempre a chama pelo banco, e digitar duas vezes é atrito.
              setContaNome((nome) => (nome === contaBanco ? t : nome));
              setContaBanco(t);
              limparErro('contaNome');
            }}
            placeholder="Ex: Nubank, Itaú, Caixa"
            returnKeyType="next"
            onSubmitEditing={() => contaNomeRef.current?.focus()}
            submitBehavior="submit"
          />
          <Field
            ref={contaNomeRef}
            testID="onboarding-account-name"
            label="Nome"
            value={contaNome}
            onChangeText={(t) => { setContaNome(t); limparErro('contaNome'); }}
            placeholder="Ex: Conta do dia a dia"
            error={erros.contaNome}
            returnKeyType="next"
            onSubmitEditing={() => contaSaldoRef.current?.focus()}
            submitBehavior="submit"
          />
          <Text style={{ ...typography.meta, color: colors.textSecondary, marginBottom: spacing.sm, textTransform: 'uppercase', letterSpacing: 0.8 }}>
            Tipo
          </Text>
          <View style={{ flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.lg }}>
            {(['CONTA_BANCARIA', 'DINHEIRO', 'POUPANCA'] as TipoContaInicial[]).map((t) => (
              <Chip
                key={t}
                label={t === 'CONTA_BANCARIA' ? 'Bancária' : t === 'DINHEIRO' ? 'Dinheiro' : 'Poupança'}
                selected={contaTipo === t}
                onPress={() => setContaTipo(t)}
              />
            ))}
          </View>
          <Field
            ref={contaSaldoRef}
            testID="onboarding-account-balance"
            label="Saldo inicial (R$)"
            value={contaSaldo}
            onChangeText={(t) => { setContaSaldo(maskCurrencyInput(t)); limparErro('contaSaldo'); }}
            keyboardType="number-pad"
            placeholder="0,00"
            error={erros.contaSaldo}
          />
        </View>
      ) : null}

      {passo === 'renda' ? (
        <View>
          <Field
            testID="onboarding-income-name"
            label="Nome"
            value={rendaNome}
            onChangeText={(t) => { setRendaNome(t); setComRenda(true); limparErro('rendaNome'); }}
            placeholder="Ex: Salário"
            error={erros.rendaNome}
          />
          <Field
            testID="onboarding-income-value"
            label="Valor (R$)"
            value={rendaValor}
            onChangeText={(t) => { setRendaValor(maskCurrencyInput(t)); setComRenda(true); limparErro('rendaValor'); }}
            keyboardType="number-pad"
            placeholder="0,00"
            error={erros.rendaValor}
          />
          <Field
            testID="onboarding-income-day"
            label="Dia do mês"
            value={rendaDia}
            onChangeText={(t) => { setRendaDia(t.replace(/\D/g, '').slice(0, 2)); setComRenda(true); limparErro('rendaDia'); }}
            keyboardType="number-pad"
            placeholder="5"
            error={erros.rendaDia}
          />
        </View>
      ) : null}

      {passo === 'categorias' ? (
        <Card padded={false}>
          {CATEGORIAS_INICIAIS.map((categoria, i) => {
            const escolhida = categoriasEscolhidas.includes(categoria.nome);
            return (
              <TouchableOpacity
                key={categoria.nome}
                onPress={() => alternarCategoria(categoria.nome)}
                activeOpacity={0.7}
                accessibilityRole="checkbox"
                accessibilityState={{ checked: escolhida }}
                accessibilityLabel={categoria.nome}
                style={{
                  flexDirection: 'row',
                  alignItems: 'center',
                  gap: spacing.md,
                  minHeight: 56,
                  paddingHorizontal: spacing.lg,
                  borderTopWidth: i === 0 ? 0 : 1,
                  borderTopColor: colors.border,
                }}
              >
                <IconTile tone="neutral" size={36} style={{ backgroundColor: colors.overlay }}>
                  {categoria.icone}
                </IconTile>
                <Text style={{ ...typography.cardTitle, color: colors.textPrimary, flex: 1 }}>
                  {categoria.nome}
                </Text>
                <Ionicons
                  name={escolhida ? 'checkmark-circle' : 'ellipse-outline'}
                  size={22}
                  color={escolhida ? colors.brand : colors.textMuted}
                />
              </TouchableOpacity>
            );
          })}
        </Card>
      ) : null}

      {passo === 'cartao' ? (
        <View>
          {/* A face reage ao que está sendo digitado: o catálogo de emissores
              reconhece o banco pelo nome ("Itaú", "nubank roxinho", "Inter") e
              traz cor, monograma e contraste. Sem lista fechada — qualquer nome
              funciona, e o desconhecido ganha uma cor derivada do próprio nome. */}
          <View style={{ alignItems: 'center', marginBottom: spacing.lg }}>
            <CartaoFisico
              nome={cartaoNome || 'Seu cartão'}
              titular={usuario?.nome}
              largura={Math.min(larguraDaTela - spacing.lg * 4, 260)}
            />
          </View>

          <Field
            testID="onboarding-card-name"
            label="Nome do cartão"
            value={cartaoNome}
            onChangeText={(t) => { setCartaoNome(t); setComCartao(true); limparErro('cartaoNome'); }}
            placeholder="Ex: Itaú, Nubank, Inter…"
            error={erros.cartaoNome}
          />
          <Field
            testID="onboarding-card-limit"
            label="Limite (R$)"
            value={cartaoLimite}
            onChangeText={(t) => { setCartaoLimite(maskCurrencyInput(t)); setComCartao(true); limparErro('cartaoLimite'); }}
            keyboardType="number-pad"
            placeholder="0,00"
            error={erros.cartaoLimite}
          />
          <View style={{ flexDirection: 'row', gap: spacing.md }}>
            <View style={{ flex: 1 }}>
              <Field
                testID="onboarding-card-closing"
                label="Fecha dia"
                value={cartaoFechamento}
                onChangeText={(t) => { setCartaoFechamento(t.replace(/\D/g, '').slice(0, 2)); setComCartao(true); limparErro('cartaoFechamento'); }}
                keyboardType="number-pad"
                placeholder="20"
                error={erros.cartaoFechamento}
              />
            </View>
            <View style={{ flex: 1 }}>
              <Field
                testID="onboarding-card-due"
                label="Vence dia"
                value={cartaoVencimento}
                onChangeText={(t) => { setCartaoVencimento(t.replace(/\D/g, '').slice(0, 2)); setComCartao(true); limparErro('cartaoVencimento'); }}
                keyboardType="number-pad"
                placeholder="27"
                error={erros.cartaoVencimento}
              />
            </View>
          </View>
        </View>
      ) : null}

      {passo === 'meta' ? (
        <View>
          <Field
            testID="onboarding-goal-name"
            label="Nome"
            value={metaNome}
            onChangeText={(t) => { setMetaNome(t); setComMeta(true); limparErro('metaNome'); }}
            placeholder="Ex: Reserva de emergência"
            error={erros.metaNome}
          />
          <Field
            testID="onboarding-goal-value"
            label="Quanto quer juntar (R$)"
            value={metaValor}
            onChangeText={(t) => { setMetaValor(maskCurrencyInput(t)); setComMeta(true); limparErro('metaValor'); }}
            keyboardType="number-pad"
            placeholder="0,00"
            error={erros.metaValor}
          />
          <Field
            testID="onboarding-goal-date"
            label="Até quando (opcional)"
            value={metaData}
            onChangeText={(t) => { setMetaData(maskDateInput(t)); setComMeta(true); }}
            keyboardType="number-pad"
            placeholder="DD/MM/AAAA"
          />
        </View>
      ) : null}

      {passo === 'revisao' ? (
        <View style={{ gap: spacing.md }}>
          <ItemDaRevisao
            titulo="Conta principal"
            detalhe={[
              contaBanco.trim() && contaBanco.trim() !== contaNome.trim() ? contaBanco.trim() : null,
              contaNome.trim(),
              formatCurrency(parseCurrencyBR(contaSaldo || '0')),
            ].filter(Boolean).join(' · ')}
            onEditar={() => irPara('conta')}
          />
          <ItemDaRevisao
            titulo="Renda mensal"
            detalhe={comRenda
              ? `${rendaNome.trim()} · ${formatCurrency(parseCurrencyBR(rendaValor || '0'))} no dia ${rendaDia}`
              : null}
            onEditar={() => irPara('renda')}
          />
          <ItemDaRevisao
            titulo="Categorias"
            detalhe={categoriasEscolhidas.length ? `${categoriasEscolhidas.length} selecionadas` : null}
            onEditar={() => irPara('categorias')}
          />
          <ItemDaRevisao
            titulo="Cartão"
            detalhe={comCartao
              ? `${cartaoNome.trim()} · fecha dia ${cartaoFechamento}, vence dia ${cartaoVencimento}`
              : null}
            onEditar={() => irPara('cartao')}
          />
          <ItemDaRevisao
            titulo="Meta"
            detalhe={comMeta ? `${metaNome.trim()} · ${formatCurrency(parseCurrencyBR(metaValor || '0'))}` : null}
            onEditar={() => irPara('meta')}
          />
        </View>
      ) : null}

      {erroGeral ? (
        <Text
          accessibilityRole="alert"
          accessibilityLiveRegion="assertive"
          style={{ ...typography.body, color: colors.danger, marginTop: spacing.lg }}
        >
          {erroGeral}
        </Text>
      ) : null}
    </TelaFluxo>
  );
}

function ItemDaRevisao({
  titulo,
  detalhe,
  onEditar,
}: {
  titulo: string;
  detalhe: string | null;
  onEditar: () => void;
}) {
  const colors = useTheme();
  const pulado = detalhe === null;

  return (
    <Card>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
        <View style={{ flex: 1 }}>
          <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>{titulo}</Text>
          <Text
            style={{
              ...typography.meta,
              ...(pulado ? {} : numeric),
              color: pulado ? colors.textMuted : colors.textSecondary,
              marginTop: spacing.xxs,
            }}
          >
            {pulado ? 'Você pulou — dá para adicionar depois' : detalhe}
          </Text>
        </View>
        <TouchableOpacity
          onPress={onEditar}
          activeOpacity={0.7}
          accessibilityRole="button"
          accessibilityLabel={`Editar ${titulo}`}
          hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
          style={{
            width: 36, height: 36, borderRadius: radius.pill,
            backgroundColor: colors.overlay,
            borderWidth: 1, borderColor: colors.border,
            alignItems: 'center', justifyContent: 'center',
          }}
        >
          <Ionicons name={pulado ? 'add' : 'pencil-outline'} size={16} color={colors.brandFg} />
        </TouchableOpacity>
      </View>
    </Card>
  );
}
