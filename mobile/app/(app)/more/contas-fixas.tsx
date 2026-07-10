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

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['contas-fixas'],
    queryFn: () => contaFixaService.listar(),
  });

  const contas = data?.content ?? [];
  const emAberto = contas.filter(cf => cf.status === 'PENDENTE' || cf.status === 'ATRASADO');
  const totalEmAberto = emAberto.reduce((acc, cf) => acc + Number(cf.valorPlanejado ?? 0), 0);

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
      setModalPagarVisible(false);
      setValorPago('');
    },
    onError: (err: any) => setErroPagar(err?.userMessage ?? 'Erro ao registrar pagamento.'),
  });

  const pularMes = (cf: ContaFixa) => {
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
  const [descricaoError, setDescricaoError] = useState<string | null>(null);
  const [valorError, setValorError] = useState<string | null>(null);
  const [diaError, setDiaError] = useState<string | null>(null);
  const [categoriaError, setCategoriaError] = useState<string | null>(null);
  const [erroCriar, setErroCriar] = useState<string | null>(null);

  const { data: categorias = [] } = useQuery({ queryKey: ['categorias'], queryFn: () => categoriaService.listar() });

  const limparCriar = () => {
    setDescricaoCriar(''); setValorCriar(''); setDiaCriar(''); setCategoriaCriarId(null); setRecorrenteCriar(true);
    setDescricaoError(null); setValorError(null); setDiaError(null); setCategoriaError(null); setErroCriar(null);
  };

  const criarMutation = useMutation({
    mutationFn: (req: ContaFixaRequest) => contaFixaService.criar(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contas-fixas'] });
      setModalCriarVisible(false);
      limparCriar();
    },
    onError: (err: any) => setErroCriar(err?.userMessage ?? 'Erro ao criar conta fixa.'),
  });

  const renderItem = ({ item: cf }: { item: ContaFixa }) => {
    const pendente = cf.status === 'PENDENTE' || cf.status === 'ATRASADO';
    return (
      <Card radius={20} style={{ marginBottom: 12 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
          <IconTile tone={cf.status === 'ATRASADO' ? 'danger' : cf.status === 'PAGO' ? 'success' : 'brand'} size={44}>
            {cf.categoria?.icone || '📌'}
          </IconTile>
          <View style={{ flex: 1, minWidth: 0 }}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
              <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '700', flex: 1 }} numberOfLines={1}>{cf.nome}</Text>
              <Badge status={cf.status} />
            </View>
            <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 2 }} numberOfLines={1}>
              {cf.categoria?.nome ? `${cf.categoria.nome} · ` : ''}Vence dia {cf.diaVencimento}
            </Text>
          </View>
        </View>

        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 12 }}>
          <Text style={{ color: colors.textPrimary, fontSize: 17, fontWeight: '700', fontVariant: ['tabular-nums'] }}>
            {formatCurrency(Number(cf.valorPlanejado ?? 0))}
          </Text>
          {pendente && (
            <View style={{ flexDirection: 'row', gap: 8 }}>
              {cf.recorrente !== false && (
                <TouchableOpacity
                  onPress={() => pularMes(cf)}
                  disabled={pulandoId === cf.id}
                  accessibilityRole="button"
                  accessibilityLabel={`Pular ${cf.nome} este mês`}
                  style={{ minHeight: 36, paddingVertical: 8, paddingHorizontal: 14, borderRadius: 999, borderWidth: 1, borderColor: colors.border, justifyContent: 'center' }}
                >
                  {pulandoId === cf.id
                    ? <ActivityIndicator size="small" color={colors.textSecondary} />
                    : <Text style={{ color: colors.textSecondary, fontSize: 12, fontWeight: '600' }}>Pular</Text>}
                </TouchableOpacity>
              )}
              <TouchableOpacity
                onPress={() => { setSelecionada(cf); setValorPago(formatNumber(Number(cf.valorPlanejado ?? 0))); setErroPagar(null); setErroCarteira(null); setCarteiraPagamentoId(carteiras.length === 1 ? carteiras[0].id : null); setModalPagarVisible(true); }}
                accessibilityRole="button"
                accessibilityLabel={`Pagar ${cf.nome}`}
                style={{ minHeight: 36, paddingVertical: 8, paddingHorizontal: 16, borderRadius: 999, backgroundColor: colors.brand, justifyContent: 'center' }}
              >
                <Text style={{ color: colors.brandText, fontSize: 12, fontWeight: '700' }}>Pagar</Text>
              </TouchableOpacity>
            </View>
          )}
        </View>
      </Card>
    );
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={{ paddingTop: insets.top + 16, paddingHorizontal: 16, paddingBottom: 12 }}>
        <Text style={{ color: colors.textPrimary, fontSize: 23, fontWeight: '800', letterSpacing: -0.4 }}>Contas Fixas</Text>
        <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4 }}>
          {emAberto.length > 0
            ? `${formatCurrency(totalEmAberto)} em aberto · ${emAberto.length} ${emAberto.length === 1 ? 'conta' : 'contas'}`
            : 'Despesas mensais e vencimentos'}
        </Text>
      </View>

      {isLoading ? (
        <View style={{ paddingHorizontal: 16, gap: 12 }}>
          {[1, 2, 3].map(i => <SkeletonBox key={i} width="100%" height={110} borderRadius={20} />)}
        </View>
      ) : isError ? (
        <View style={{ alignItems: 'center', padding: 48 }}>
          <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>Erro ao carregar contas fixas</Text>
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
              <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600', textAlign: 'center' }}>Nenhuma conta fixa ainda</Text>
              <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4, textAlign: 'center' }}>Toque no + para cadastrar aluguel, energia, internet ou outras contas mensais.</Text>
            </View>
          )}
        />
      )}

      <Fab onPress={() => setModalCriarVisible(true)} accessibilityLabel="Criar conta fixa" />

      <Modal visible={modalPagarVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity onPress={() => { setModalPagarVisible(false); setValorPago(''); setErroPagar(null); }} accessibilityRole="button">
              <Text style={{ color: colors.brandFg, fontSize: 15 }}>Cancelar</Text>
            </TouchableOpacity>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '600' }}>Pagar</Text>
            <TouchableOpacity
              disabled={pagarMutation.status === 'pending'}
              accessibilityRole="button"
              onPress={() => {
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

            <Text style={{ color: colors.textSecondary, fontSize: 10, letterSpacing: 0.8, marginTop: 8, marginBottom: 6, textTransform: 'uppercase' }}>Pagar com</Text>
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
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '600' }}>Nova Conta Fixa</Text>
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
                if (hasErr) return;
                criarMutation.mutate({
                  descricao: descricaoCriar.trim(),
                  valor: Number(v),
                  diaVencimento: dia,
                  categoriaId: categoriaCriarId!,
                  recorrente: recorrenteCriar,
                });
              }}
            >
              {criarMutation.status === 'pending'
                ? <ActivityIndicator color={colors.brand} size="small" />
                : <Text style={{ color: colors.brandFg, fontSize: 15, fontWeight: '600' }}>Salvar</Text>}
            </TouchableOpacity>
          </View>
          <ScrollView contentContainerStyle={{ padding: 16 }} keyboardShouldPersistTaps="handled">
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
