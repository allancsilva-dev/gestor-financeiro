import React, { useState } from 'react';
import { View, Text, TouchableOpacity, FlatList, Modal, ScrollView, TextInput } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { contaService } from '../../../src/services/contaService';
import { TIPO_CONTA_LABEL, formatCurrency, parseCurrencyBR, maskCurrencyInput } from '../../../src/utils/format';
import { Conta, ContaRequest, TipoConta } from '../../../src/types';
import { useTheme } from '../../../src/theme';
import SkeletonBox from '../../../src/components/ui/SkeletonBox';

export default function ContasScreen() {
  const colors = useTheme();
  const insets = useSafeAreaInsets();
  const queryClient = useQueryClient();
  const [modalVisible, setModalVisible] = useState(false);
  const [nome, setNome] = useState('');
  const [tipo, setTipo] = useState<TipoConta>('DEBITO');
  const [limite, setLimite] = useState('');
  const [banco, setBanco] = useState('');
  const [diaFechamento, setDiaFechamento] = useState('');
  const [diaVencimento, setDiaVencimento] = useState('');
  const [nomeError, setNomeError] = useState<string | null>(null);
  const [tipoError, setTipoError] = useState<string | null>(null);
  const [limiteError, setLimiteError] = useState<string | null>(null);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['contas'],
    queryFn: () => contaService.listar(),
  });

  const criarMutation = useMutation({
    mutationFn: (req: ContaRequest) => contaService.criar(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contas'] });
      setModalVisible(false);
      setNome(''); setLimite(''); setBanco(''); setDiaFechamento(''); setDiaVencimento(''); setTipo('DEBITO'); setNomeError(null); setTipoError(null); setLimiteError(null);
    },
    onError: (err: any) => {
      setNomeError(err?.userMessage ?? 'Erro ao criar cartão.');
    }
  });

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <View style={{ paddingTop: insets.top + 16, paddingHorizontal: 16, paddingBottom: 12 }}>
        <Text style={{ color: colors.textPrimary, fontSize: 23, fontWeight: '800', letterSpacing: -0.4 }}>Cartões</Text>
        <Text style={{ color: colors.textSecondary, fontSize: 13, marginTop: 4 }}>Crédito, débito e pagamento</Text>
      </View>

      {isLoading ? (
        <View style={{ padding: 16 }}>
          {[1,2,3].map(i => <SkeletonBox key={i} width="100%" height={64} />)}
        </View>
      ) : isError ? (
        <View style={{ alignItems: 'center', padding: 48 }}>
          <Text style={{ color: colors.textSecondary }}>Erro ao carregar cartões</Text>
          <TouchableOpacity onPress={() => refetch()} style={{ marginTop: 8 }}>
            <Text style={{ color: colors.brand }}>Tentar novamente</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <FlatList
          data={data?.content ?? []}
          keyExtractor={item => item.id.toString()}
          renderItem={({ item: conta }) => (
            <View style={{ backgroundColor: colors.card, borderRadius: 12, borderWidth: 1, borderColor: colors.border, padding: 14, marginBottom: 8, marginHorizontal: 16 }}>
              <Text style={{ color: colors.textPrimary, fontSize: 15, fontWeight: '600' }}>{conta.nome}</Text>
              <View style={{ marginTop: 6 }}>
                <View style={{ paddingHorizontal: 8, paddingVertical: 4, borderRadius: 8, backgroundColor: colors.card }}>
                  <Text style={{ color: colors.textSecondary }}>{TIPO_CONTA_LABEL[conta.tipo]}</Text>
                </View>
              </View>
              {conta.tipo === 'CREDITO' && (
                <Text style={{ color: colors.textSecondary, fontSize: 12, marginTop: 8 }}>
                  {conta.banco ? `${conta.banco} · ` : ''}Limite: {formatCurrency(Number(conta.limiteTotal ?? 0))}
                </Text>
              )}
            </View>
          )}
          ListEmptyComponent={() => (
            <View style={{ alignItems: 'center', padding: 48 }}>
              <Text style={{ color: colors.textSecondary }}>Nenhum cartão encontrado</Text>
            </View>
          )}
        />
      )}

      <TouchableOpacity
        onPress={() => setModalVisible(true)}
        style={{ position: 'absolute', bottom: 24, right: 16, width: 56, height: 56, borderRadius: 28, backgroundColor: colors.brand, alignItems: 'center', justifyContent: 'center' }}
      >
        <Text style={{ color: colors.brandText, fontSize: 28, lineHeight: 30 }}>+</Text>
      </TouchableOpacity>

      <Modal visible={modalVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: colors.border }}>
            <TouchableOpacity onPress={() => { setModalVisible(false); setNome(''); setLimite(''); setBanco(''); setDiaFechamento(''); setDiaVencimento(''); setTipo('DEBITO'); setNomeError(null); setTipoError(null); setLimiteError(null); }}>
              <Text style={{ color: colors.brand, fontSize: 15 }}>Cancelar</Text>
            </TouchableOpacity>
            <Text style={{ color: colors.textPrimary, fontSize: 16, fontWeight: '600' }}>Novo Cartão</Text>
            <TouchableOpacity disabled={criarMutation.status === 'pending'} onPress={() => {
              setNomeError(null); setTipoError(null); setLimiteError(null);
              let hasErr = false;
              if (!nome.trim()) { setNomeError('Nome obrigatório.'); hasErr = true; }
              if (!tipo) { setTipoError('Tipo obrigatório.'); hasErr = true; }
              if (tipo === 'CREDITO') {
                const v = parseCurrencyBR(limite);
                if (isNaN(v) || v <= 0) { setLimiteError('Limite total obrigatório e positivo.'); hasErr = true; }
              }
              if (hasErr) return;
              const fech = parseInt(diaFechamento, 10);
              const venc = parseInt(diaVencimento, 10);
              criarMutation.mutate({
                nome: nome.trim(),
                tipo,
                limiteTotal: tipo === 'CREDITO' ? parseCurrencyBR(limite) : undefined,
                banco: tipo === 'CREDITO' && banco.trim() ? banco.trim() : undefined,
                diaFechamento: tipo === 'CREDITO' && !isNaN(fech) && fech >= 1 && fech <= 31 ? fech : undefined,
                diaVencimento: tipo === 'CREDITO' && !isNaN(venc) && venc >= 1 && venc <= 31 ? venc : undefined,
              });
            }}>
              <Text style={{ color: criarMutation.status === 'pending' ? colors.textMuted : colors.brand, fontSize: 15, fontWeight: '600' }}>Salvar</Text>
            </TouchableOpacity>
          </View>
          <ScrollView contentContainerStyle={{ padding: 16 }}>
            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Nome</Text>
            <TextInput value={nome} onChangeText={setNome} placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />
            {nomeError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{nomeError}</Text>}

            <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Tipo</Text>
            <View style={{ flexDirection: 'row', gap: 8, marginBottom: 8 }}>
              {(['CREDITO','DEBITO','DINHEIRO','POUPANCA'] as TipoConta[]).map(t => (
                <TouchableOpacity key={t} onPress={() => setTipo(t)} style={{ paddingHorizontal: 12, paddingVertical: 6, borderRadius: 20, backgroundColor: tipo === t ? colors.brand + '26' : colors.card, borderWidth: 1, borderColor: tipo === t ? colors.brand : colors.border }}>
                  <Text style={{ color: tipo === t ? colors.brand : colors.textSecondary }}>{TIPO_CONTA_LABEL[t]}</Text>
                </TouchableOpacity>
              ))}
            </View>
            {tipoError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{tipoError}</Text>}

            {tipo === 'CREDITO' && (
              <>
                <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Limite total</Text>
                <TextInput value={limite} onChangeText={(t) => setLimite(maskCurrencyInput(t))} keyboardType="number-pad" placeholder="0,00" placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />
                {limiteError && <Text style={{ color: colors.danger, marginBottom: 8 }}>{limiteError}</Text>}

                <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Banco</Text>
                <TextInput value={banco} onChangeText={setBanco} placeholder="Ex.: Nubank, Itaú, Inter" placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />

                <View style={{ flexDirection: 'row', gap: 12 }}>
                  <View style={{ flex: 1 }}>
                    <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Dia fechamento</Text>
                    <TextInput value={diaFechamento} onChangeText={(t) => setDiaFechamento(t.replace(/\D/g, '').slice(0, 2))} keyboardType="number-pad" placeholder="Ex.: 28" placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={{ color: colors.textSecondary, fontSize: 9, letterSpacing: 0.8, marginBottom: 6, textTransform: 'uppercase' }}>Dia vencimento</Text>
                    <TextInput value={diaVencimento} onChangeText={(t) => setDiaVencimento(t.replace(/\D/g, '').slice(0, 2))} keyboardType="number-pad" placeholder="Ex.: 5" placeholderTextColor={colors.textMuted} style={{ backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, borderRadius: 8, padding: 12, color: colors.textPrimary, fontSize: 15, marginBottom: 8 }} />
                  </View>
                </View>
              </>
            )}
          </ScrollView>
        </View>
      </Modal>
    </View>
  );
}
