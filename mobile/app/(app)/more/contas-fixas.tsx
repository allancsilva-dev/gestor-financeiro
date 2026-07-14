import React, { useState } from 'react';
import { View, Text, TouchableOpacity, FlatList, Modal, ScrollView, ActivityIndicator, Switch, Alert } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { contaFixaService } from '../../../src/services/contaFixaService';
import { categoriaService } from '../../../src/services/categoriaService';
import { carteiraService } from '../../../src/services/carteiraService';
import Badge from '../../../src/components/ui/Badge';
import Card from '../../../src/components/ui/Card';
import Chip from '../../../src/components/ui/Chip';
import IconTile from '../../../src/components/ui/IconTile';
import Field from '../../../src/components/ui/Field';
import { ContaFixa, ContaFixaRequest } from '../../../src/types';
import { useTheme } from '../../../src/theme';
import BackButton from '../../../src/components/ui/BackButton';
import { parseCurrencyBR, maskCurrencyInput, formatCurrency, formatNumber } from '../../../src/utils/format';
import SkeletonBox from '../../../src/components/ui/SkeletonBox';
import Fab from '../../../src/components/ui/Fab';

export default function ContasFixasScreen() {
  const colors = useTheme();
  const queryClient = useQueryClient();
  const insets = useSafeAreaInsets();
  const [modalPagarVisible, setModalPagarVisible] = useState(false);
  const [modalCriarVisible, setModalCriarVisible] = useState(false);
  const [selecionada, setSelecionada] = useState<ContaFixa | null>(null);
  const [valorPago, setValorPago] = useState('');
  const [erroPagar, setErroPagar] = useState<string | null>(null);
  const [carteiraPagamentoId, setCarteiraPagamentoId] = useState<number | null>(null);
  const [erroCarteira, setErroCarteira] = useState<string | null>(null);
  const [pulandoId, setPulandoId] = useState<number | null>(null);
  const [editando, setEditando] = useState<ContaFixa | null>(null);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['contas-fixas'],
    queryFn: () => contaFixaService.listar(),
  });

  const contas = data?.content ?? [];
  const emAberto = contas.filter(cf => cf.status === 'PENDENTE' || cf.status === 'ATRASADO');
  const totalAReceber = emAberto.filter(cf => cf.tipo === 'ENTRADA').reduce((acc, cf) => acc + Number(cf.valorPlanejado ?? 0), 0);
  const totalAPagar = emAberto.filter(cf => cf.tipo !== 'ENTRADA').reduce((acc, cf) => acc + Number(cf.valorPlanejado ?? 0), 0);

  const { data: carteirasData } = useQuery({
    queryKey: ['carteiras'],
    queryFn: () => carteiraService.listar(),
  });
  const carteiras = carteirasData?.content ?? [];

  const pagarMutation = useMutation({
    mutationFn: ({ id, valor, carteiraId }: { id: number; valor: number; carteiraId: number }) =>
      contaFixaService.marcarComoPaga(id, valor, carteiraId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contas-fixas'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard-resumo'] });
      queryClient.invalidateQueries({ queryKey: ['carteiras'] });
      queryClient.invalidateQueries({ queryKey: ['transacoes-recentes'] });
      queryClient.invalidateQueries({ queryKey: ['recorrencias-falhas'] });
      setModalPagarVisible(false);
      setValorPago('');
    },
    onError: (err: any) => setErroPagar(err?.userMessage ?? 'Erro ao registrar pagamento.'),
  });

  const pularMes = (cf: ContaFixa) => {
    if (pulandoId != null || pagarMutation.status === 'pending') return;
    Alert.alert('Pular este mês?', `${cf.nome} não será cobrada neste mês.`, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Pular',
        onPress: () => {
          setPulandoId(cf.id);
          contaFixaService.pularMes(cf.id)
            .then(() => refetch())
            .catch((err: any) => Alert.alert('Não foi possível pular', err?.userMessage ?? 'Tente novamente.'))
            .finally(() => setPulandoId(null));
        },
      },
    ]);
  };

  // criar conta fixa
  const [descricaoCriar, setDescricaoCriar] = useState('');
  const [valorCriar, setValorCriar] = useState('');
  const [diaCriar, setDiaCriar] = useState('');
  const [categoriaCriarId, setCategoriaCriarId] = useState<number | null>(null);
  const [recorrenteCriar, setRecorrenteCriar] = useState(true);
  const [tipoCriar, setTipoCriar] = useState<'ENTRADA' | 'SAIDA'>('SAIDA');
  const [automaticaCriar, setAutomaticaCriar] = useState(false);
  const [carteiraCriarId, setCarteiraCriarId] = useState<number | null>(null);
  const [descricaoError, setDescricaoError] = useState<string | null>(null);
  const [valorError, setValorError] = useState<string | null>(null);
  const [diaError, setDiaError] = useState<string | null>(null);
  const [categoriaError, setCategoriaError] = useState<string | null>(null);
  const [erroCriar, setErroCriar] = useState<string | null>(null);

  const { data: categorias = [] } = useQuery({ queryKey: ['categorias'], queryFn: () => categoriaService.listar() });

  const limparCriar = () => {
    setDescricaoCriar(''); setValorCriar(''); setDiaCriar(''); setCategoriaCriarId(null); setRecorrenteCriar(true);
    setTipoCriar('SAIDA'); setAutomaticaCriar(false); setCarteiraCriarId(null); setEditando(null);
    setDescricaoError(null); setValorError(null); setDiaError(null); setCategoriaError(null); setErroCriar(null);
  };

  const criarMutation = useMutation({
    mutationFn: (req: ContaFixaRequest) => editando
      ? contaFixaService.atualizar(editando.id, req)
      : contaFixaService.criar(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contas-fixas'] });
      setModalCriarVisible(false);
      limparCriar();
    },
    onError: (err: any) => setErroCriar(err?.userMessage ?? 'Erro ao criar conta fixa.'),
  });

  const abrirEdicao = (cf: ContaFixa) => {
    setEditando(cf);
    setDescricaoCriar(cf.nome);
    setValorCriar(formatNumber(Number(cf.valorPlanejado)));
    setDiaCriar(String(cf.diaVencimento));
    setCategoriaCriarId(cf.categoria?.id ?? null);
    setRecorrenteCriar(cf.recorrente !== false);
    setTipoCriar(cf.tipo ?? 'SAIDA');
    setAutomaticaCriar(Boolean(cf.execucaoAutomatica));
    setCarteiraCriarId(cf.carteira?.id ?? null);
    setModalCriarVisible(true);
  };

  const renderItem = ({ item: cf }: { item: ContaFixa }) => {
    const pendente = cf.status === 'PENDENTE' || cf.status === 'ATRASADO';
    const agora = new Date();
    const mesAtual = `${agora.getFullYear()}-${String(agora.getMonth() + 1).padStart(2, '0')}`;
    const realizavel = !cf.dataProximoVencimento || cf.dataProximoVencimento.slice(0, 7) <= mesAtual;
    return (
      <Card radius={20} style={{ marginBottom: 12 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
          <IconTile tone={cf.tipo === 'ENTRADA' ? 'success' : cf.status === 'ATRASADO' ? 'danger' : cf.status === 'PAGO' ? 'success' : 'brand'} size={44}>
            {cf.categoria?.icone || '📌'}
          </IconTile>
          <View style={{ flex: 1, minWidth: 0 }}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
              <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '700', flex: 1 }} numberOfLines={1}>{cf.nome}</Text>
              <Badge status={cf.status} />
            </View>
            <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 2 }} numberOfLines={1}>
              {cf.categoria?.nome ? `${cf.categoria.nome} · ` : ''}{cf.execucaoAutomatica ? 'Automática' : 'Manual'} · dia {cf.diaVencimento}
            </Text>
          </View>
        </View>

        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 12 }}>
          <Text style={{ color: cf.tipo === 'ENTRADA' ? colors.success : colors.danger, fontSize: 17, fontWeight: '700', fontVariant: ['tabular-nums'] }}>
            {cf.tipo === 'ENTRADA' ? '+' : '−'} {formatCurrency(Number(cf.valorPlanejado ?? 0))}
          </Text>
          <View style={{ flexDirection: 'row', gap: 8 }}>
            <TouchableOpacity
              onPress={() => abrirEdicao(cf)}
              accessibilityRole="button"
              accessibilityLabel={`Editar ${cf.nome}`}
              style={{ minHeight: 44, paddingHorizontal: 10, justifyContent: 'center' }}
            >
              <Text style={{ color: colors.brandFg, fontSize: 12, fontWeight: '600' }}>Editar</Text>
            </TouchableOpacity>
          {pendente && (
            <>
              {cf.recorrente !== false && (
                <TouchableOpacity
                onPress={() => pularMes(cf)}
                  disabled={pulandoId != null || pagarMutation.status === 'pending'}
                  accessibilityRole="button"
                  accessibilityLabel={`Pular ${cf.nome} este mês`}
                  style={{ minHeight: 44, paddingVertical: 8, paddingHorizontal: 14, borderRadius: 999, borderWidth: 1, borderColor: colors.border, justifyContent: 'center' }}
                >
                  {pulandoId === cf.id
                    ? <ActivityIndicator size="small" color={colors.textSecondary} />
                    : <Text style={{ color: colors.textSecondary, fontSize: 12, fontWeight: '600' }}>Pular</Text>}
                </TouchableOpacity>
              )}
              {realizavel && <TouchableOpacity
                disabled={pulandoId != null || pagarMutation.status === 'pending'}
                onPress={() => { setSelecionada(cf); setValorPago(formatNumber(Number(cf.valorPlanejado ?? 0))); setErroPagar(null); setErroCarteira(null); setCarteiraPagamentoId(cf.carteira?.id ?? (carteiras.length === 1 ? carteiras[0].id : null)); setModalPagarVisible(true); }}
                accessibilityRole="button"
                accessibilityLabel={`${cf.tipo === 'ENTRADA' ? 'Receber' : 'Pagar'} ${cf.nome}`}
                style={{ minHeight: 44, paddingVertical: 8, paddingHorizontal: 16, borderRadius: 999, backgroundColor: colors.brand, justifyContent: 'center' }}
              >
                <Text style={{ color: colors.brandText, fontSize: 12, fontWeight: '700' }}>{cf.tipo === 'ENTRADA' ? 'Receber' : 'Pagar'}</Text>
              </TouchableOpacity>}
            </>
          )}
          </View>
        </View>
      </Card>
    );
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={{ paddingTop: insets.top + 16, paddingHorizontal: 16, paddingBottom: 12 }}>
        <BackButton />
        <Text style={{ color: colors.textPrimary, fontSize: 23, fontWeight: '800', letterSpacing: -0.4 }}>Recorrências</Text>
        <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4 }}>
          {emAberto.length > 0
            ? `${formatCurrency(totalAReceber)} a receber · ${formatCurrency(totalAPagar)} a pagar`
            : 'Entradas e saídas que se repetem'}
        </Text>
      </View>

      {isLoading ? (
        <View style={{ paddingHorizontal: 16, gap: 12 }}>
          {[1, 2, 3].map(i => <SkeletonBox key={i} width="100%" height={110} borderRadius={20} />)}
        </View>
      ) : isError ? (
        <View style={{ alignItems: 'center', padding: 48 }}>
          <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>Erro ao carregar recorrências</Text>
          <TouchableOpacity onPress={() => refetch()} style={{ marginTop: 8, minHeight: 44, justifyContent: 'center' }} accessibilityRole="button">
            <Text style={{ color: colors.brandFg, fontWeight: '600' }}>Tentar novamente</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <FlatList
          data={contas}
          keyExtractor={item => item.id.toString()}
          contentContainerStyle={{ paddingHorizontal: 16, paddingBottom: 96 }}
          renderItem={renderItem}
          ListEmptyComponent={() => (
            <View style={{ alignItems: 'center', paddingHorizontal: 32, paddingVertical: 48 }}>
              <Text style={{ fontSize: 40, marginBottom: 12 }}>🧾</Text>
              <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600', textAlign: 'center' }}>Nenhuma recorrência ainda</Text>
              <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4, textAlign: 'center' }}>Toque no + para cadastrar salário, aluguel ou outros valores recorrentes.</Text>
            </View>
          )}
        />
      )}

      <Fab onPress={() => { limparCriar(); setModalCriarVisible(true); }} accessibilityLabel="Criar recorrência" />

      <Modal visible={modalPagarVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity onPress={() => { setModalPagarVisible(false); setValorPago(''); setErroPagar(null); }} accessibilityRole="button">
              <Text style={{ color: colors.brandFg, fontSize: 15 }}>Cancelar</Text>
            </TouchableOpacity>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '600' }}>{selecionada?.tipo === 'ENTRADA' ? 'Receber' : 'Pagar'}</Text>
            <TouchableOpacity
              disabled={pagarMutation.status === 'pending'}
              accessibilityRole="button"
              onPress={() => {
                if (pagarMutation.status === 'pending') return;
                setErroPagar(null); setErroCarteira(null);
                const v = parseCurrencyBR(valorPago);
                if (isNaN(v) || v <= 0) { setErroPagar('Valor deve ser positivo.'); return; }
                if (!carteiraPagamentoId) { setErroCarteira('Selecione de onde sai o pagamento.'); return; }
                pagarMutation.mutate({ id: selecionada!.id, valor: v, carteiraId: carteiraPagamentoId });
              }}
            >
              {pagarMutation.status === 'pending'
                ? <ActivityIndicator color={colors.brand} size="small" />
                : <Text style={{ color: colors.brandFg, fontSize: 15, fontWeight: '600' }}>Confirmar</Text>}
            </TouchableOpacity>
          </View>
          <ScrollView contentContainerStyle={{ padding: 16 }} keyboardShouldPersistTaps="handled">
            {selecionada && (
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 16 }}>
                <IconTile size={44}>{selecionada.categoria?.icone || '📌'}</IconTile>
                <View style={{ flex: 1, minWidth: 0 }}>
                  <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '700' }} numberOfLines={1}>{selecionada.nome}</Text>
                  <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 2 }}>
                    Planejado: {formatCurrency(Number(selecionada.valorPlanejado ?? 0))} · vence dia {selecionada.diaVencimento}
                  </Text>
                </View>
              </View>
            )}
            <Field label="Valor pago" value={valorPago} onChangeText={(t) => setValorPago(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={erroPagar} autoFocus />

            <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginTop: 8, marginBottom: 6, textTransform: 'uppercase' }}>{selecionada?.tipo === 'ENTRADA' ? 'Receber em' : 'Pagar com'}</Text>
            {carteiras.length === 0 ? (
              <Text style={{ color: colors.textSecondary, fontSize: 13 }}>Você ainda não tem contas. Crie uma em Mais → Contas para registrar o pagamento.</Text>
            ) : (
              <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }} keyboardShouldPersistTaps="handled">
                {carteiras.map(c => (
                  <Chip
                    key={c.id}
                    label={`${c.nome} · ${formatCurrency(Number(c.saldo ?? 0))}`}
                    selected={carteiraPagamentoId === c.id}
                    onPress={() => { setCarteiraPagamentoId(c.id); setErroCarteira(null); }}
                  />
                ))}
              </ScrollView>
            )}
            {erroCarteira && <Text style={{ color: colors.danger, fontSize: 12, marginTop: 8 }}>{erroCarteira}</Text>}
          </ScrollView>
        </View>
      </Modal>

      <Modal visible={modalCriarVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity onPress={() => { setModalCriarVisible(false); limparCriar(); }} accessibilityRole="button">
              <Text style={{ color: colors.brandFg, fontSize: 15 }}>Cancelar</Text>
            </TouchableOpacity>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '600' }}>{editando ? 'Editar recorrência' : 'Nova recorrência'}</Text>
            <TouchableOpacity
              disabled={criarMutation.status === 'pending'}
              accessibilityRole="button"
              onPress={() => {
                setDescricaoError(null); setValorError(null); setDiaError(null); setCategoriaError(null); setErroCriar(null);
                let hasErr = false;
                if (!descricaoCriar.trim()) { setDescricaoError('Descrição obrigatória.'); hasErr = true; }
                const v = parseCurrencyBR(valorCriar);
                if (isNaN(v) || v <= 0) { setValorError('Valor deve ser positivo.'); hasErr = true; }
                const dia = Number(diaCriar);
                if (!Number.isInteger(dia) || dia < 1 || dia > 31) { setDiaError('Dia deve ser um número entre 1 e 31.'); hasErr = true; }
                if (!categoriaCriarId) { setCategoriaError('Selecione uma categoria.'); hasErr = true; }
                if (automaticaCriar && !carteiraCriarId) { setErroCriar('Selecione a carteira da execução automática.'); hasErr = true; }
                if (hasErr) return;
                criarMutation.mutate({
                  descricao: descricaoCriar.trim(),
                  valor: Number(v),
                  diaVencimento: dia,
                  categoriaId: categoriaCriarId!,
                  recorrente: recorrenteCriar,
                  tipo: tipoCriar,
                  execucaoAutomatica: automaticaCriar,
                  carteiraId: automaticaCriar ? carteiraCriarId! : undefined,
                });
              }}
            >
              {criarMutation.status === 'pending'
                ? <ActivityIndicator color={colors.brand} size="small" />
                : <Text style={{ color: colors.brandFg, fontSize: 15, fontWeight: '600' }}>Salvar</Text>}
            </TouchableOpacity>
          </View>
          <ScrollView contentContainerStyle={{ padding: 16 }} keyboardShouldPersistTaps="handled">
            <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Tipo</Text>
            <View style={{ flexDirection: 'row', gap: 8, marginBottom: 16 }}>
              <Chip label="↑ Entrada" selected={tipoCriar === 'ENTRADA'} onPress={() => setTipoCriar('ENTRADA')} />
              <Chip label="↓ Saída" selected={tipoCriar === 'SAIDA'} onPress={() => setTipoCriar('SAIDA')} />
            </View>
            <Field label="Descrição" value={descricaoCriar} onChangeText={setDescricaoCriar} placeholder="Ex: Aluguel" error={descricaoError} autoFocus />
            <Field label="Valor" value={valorCriar} onChangeText={(t) => setValorCriar(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" error={valorError} />
            <Field label="Dia de vencimento" value={diaCriar} onChangeText={setDiaCriar} keyboardType="number-pad" placeholder="Ex: 10" maxLength={2} error={diaError} />

            <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Categoria</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }} style={{ marginBottom: 8 }} keyboardShouldPersistTaps="handled">
              {categorias.map(cat => (
                <Chip key={cat.id} label={`${cat.icone ? cat.icone + ' ' : ''}${cat.nome}`} selected={categoriaCriarId === cat.id} onPress={() => setCategoriaCriarId(cat.id)} />
              ))}
            </ScrollView>
            {categoriaError && <Text style={{ color: colors.danger, fontSize: 12, marginBottom: 8 }}>{categoriaError}</Text>}

            <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginTop: 12, marginBottom: 6, textTransform: 'uppercase' }}>Execução</Text>
            <View style={{ flexDirection: 'row', gap: 8, marginBottom: 10 }}>
              <Chip label="Manual" selected={!automaticaCriar} onPress={() => setAutomaticaCriar(false)} />
              <Chip label="Automática" selected={automaticaCriar} onPress={() => setAutomaticaCriar(true)} />
            </View>
            {automaticaCriar && (
              <>
                <Text style={{ color: colors.textSecondary, fontSize: 12, marginBottom: 8 }}>Escolha a conta que receberá ou pagará no vencimento.</Text>
                <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
                  {carteiras.map(c => <Chip key={c.id} label={`${c.nome} · ${formatCurrency(Number(c.saldo ?? 0))}`} selected={carteiraCriarId === c.id} onPress={() => setCarteiraCriarId(c.id)} />)}
                </ScrollView>
              </>
            )}

            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 12 }}>
              <View style={{ flex: 1, marginRight: 12 }}>
                <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>Repete todo mês</Text>
                <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 2 }}>Desative para contas de um mês só.</Text>
              </View>
              <Switch
                value={recorrenteCriar}
                onValueChange={setRecorrenteCriar}
                trackColor={{ true: colors.brand }}
                accessibilityLabel="Repete todo mês"
              />
            </View>

            {erroCriar && <Text style={{ color: colors.danger, marginTop: 16 }}>{erroCriar}</Text>}
          </ScrollView>
        </View>
      </Modal>
    </View>
  );
}
