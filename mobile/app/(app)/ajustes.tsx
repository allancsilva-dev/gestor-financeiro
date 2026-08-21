import React, { useState } from 'react';
import { View, Text, TouchableOpacity, Alert, Platform, ScrollView, Modal, ActivityIndicator } from 'react-native';
import { useRouter } from 'expo-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import Ionicons from '@expo/vector-icons/Ionicons';
import * as Sharing from 'expo-sharing';
import { File, Paths } from 'expo-file-system';
import * as DocumentPicker from 'expo-document-picker';
import { useTheme, useTabBarSpace, spacing, radius, typography } from '../../src/theme';
import { misturar } from '../../src/theme/metaCores';
import { useAuth } from '../../src/context/AuthContext';
import { useTema } from '../../src/context/TemaContext';
import { TemaPreferido } from '../../src/store/temaPreferido';
import api from '../../src/services/api';
import importService from '../../src/services/importService';
import notificacaoService from '../../src/services/notificacaoService';
import usuarioService from '../../src/services/usuarioService';
import { getInitials } from '../../src/utils/format';
import { mensagemDeErro } from '../../src/utils/erros';
import CabecalhoDeTela from '../../src/components/ui/CabecalhoDeTela';
import CabecalhoSecao from '../../src/components/ui/CabecalhoSecao';
import SuperficieComBrilho from '../../src/components/ui/SuperficieComBrilho';
import Card from '../../src/components/ui/Card';
import Chip from '../../src/components/ui/Chip';
import Field from '../../src/components/ui/Field';
import IconTile from '../../src/components/ui/IconTile';
import ListRow from '../../src/components/ui/ListRow';
import Entrance from '../../src/components/ui/Entrance';

// Ferramentas do app. Os rótulos "Categorias", "Carteira", "Contas" e "Relatórios"
// são tocados por texto em .maestro/financial-critical.yaml — não renomear.
const FERRAMENTAS: Array<{
  label: string; sub: string; rota: string | null; icone: string; desabilitado?: boolean;
}> = [
  { label: 'Visão financeira', sub: 'Métricas oficiais', rota: '/more/visao-financeira', icone: '🧭' },
  { label: 'Contas', sub: 'Saldos e dinheiro', rota: '/more/carteiras', icone: '🏦' },
  { label: 'Recorrências', sub: 'Entradas e saídas', rota: '/more/contas-fixas', icone: '📅' },
  { label: 'Orçamentos', sub: 'Por categoria', rota: '/more/orcamentos', icone: '📊' },
  { label: 'Carteira', sub: 'Cartões e faturas', rota: '/more/faturas', icone: '💳' },
  { label: 'Relatórios', sub: 'Gráficos', rota: '/analises', icone: '📈' },
  { label: 'Categorias', sub: 'Organizar', rota: '/more/categorias', icone: '🏷' },
  { label: 'Investimentos', sub: 'Posições', rota: '/more/investimentos', icone: '📦' },
  { label: 'Entrada por IA', sub: 'Lançar conversando', rota: null, icone: '🤖', desabilitado: true },
];

const TEMAS: Array<{ id: TemaPreferido; label: string }> = [
  { id: 'sistema', label: 'Sistema' },
  { id: 'claro', label: 'Claro' },
  { id: 'escuro', label: 'Escuro' },
];

export default function Ajustes() {
  const colors = useTheme();
  const tabBarSpace = useTabBarSpace();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { usuario, logout } = useAuth();
  const { preferencia, setPreferencia } = useTema();

  const [modalExcluirVisible, setModalExcluirVisible] = useState(false);
  const [senhaExcluir, setSenhaExcluir] = useState('');
  const [erroExcluir, setErroExcluir] = useState<string | null>(null);
  const [excluindo, setExcluindo] = useState(false);

  const { data: naoLidas } = useQuery({
    queryKey: ['notificacoes', 'nao-lidas'],
    queryFn: () => notificacaoService.contarNaoLidas(),
  });

  // Baixa o CSV pela API autenticada e compartilha o arquivo — nunca expor URL da API
  const exportarDados = async () => {
    try {
      const { data: csv } = await api.get<string>('/v1/exportar/completo', { responseType: 'text' });

      if (Platform.OS === 'web') {
        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'dados-completos.csv';
        a.click();
        URL.revokeObjectURL(url);
        return;
      }

      const file = new File(Paths.cache, 'dados-completos.csv');
      file.create({ overwrite: true });
      file.write(csv);

      if (await Sharing.isAvailableAsync()) {
        await Sharing.shareAsync(file.uri, { mimeType: 'text/csv', dialogTitle: 'Exportar dados' });
      } else {
        Alert.alert('Exportar dados', `Arquivo salvo em:\n${file.uri}`);
      }
    } catch (err: any) {
      Alert.alert('Exportar dados', err?.userMessage ?? 'Não foi possível exportar. Tente novamente.');
    }
  };

  const importarCsv = async () => {
    try {
      const result = await DocumentPicker.getDocumentAsync({
        type: ['text/csv', 'text/comma-separated-values', 'application/vnd.ms-excel'],
        multiple: false,
        copyToCacheDirectory: true,
      });

      if (result.canceled || !result.assets?.[0]) return;
      const asset = result.assets[0];
      const data = await importService.csv({
        uri: asset.uri,
        name: asset.name || 'extrato.csv',
        type: asset.mimeType || 'text/csv',
      });

      Alert.alert(
        'Importar CSV',
        `${data.importadas} importadas · ${data.ignoradas} ignoradas · ${data.erros} erros`
      );
      queryClient.invalidateQueries();
    } catch (err: any) {
      Alert.alert('Importar CSV', err?.userMessage ?? 'Não foi possível importar. Verifique o arquivo e tente novamente.');
    }
  };

  const encerrarSessao = async () => {
    // logout do contexto já revoga o refresh token no servidor e limpa o storage
    await logout();
    try { queryClient.clear(); } catch {}
    router.replace('/(auth)/login');
  };

  const sair = () => {
    Alert.alert('Sair da conta?', 'Você precisará entrar novamente para acessar seus dados.', [
      { text: 'Cancelar', style: 'cancel' },
      { text: 'Sair', style: 'destructive', onPress: encerrarSessao },
    ]);
  };

  // Primeiro aviso no Alert, confirmação por senha no modal: a exclusão é definitiva
  const pedirExclusao = () => {
    Alert.alert(
      'Excluir minha conta?',
      'Todos os seus dados são apagados definitivamente: transações, contas, cartões, metas e histórico. Não dá para desfazer.',
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Continuar',
          style: 'destructive',
          onPress: () => {
            setSenhaExcluir('');
            setErroExcluir(null);
            setModalExcluirVisible(true);
          },
        },
      ]
    );
  };

  const confirmarExclusao = async () => {
    if (!senhaExcluir) { setErroExcluir('Informe sua senha para confirmar.'); return; }
    setErroExcluir(null);
    setExcluindo(true);
    try {
      await usuarioService.excluirConta(senhaExcluir);
      setModalExcluirVisible(false);
      await encerrarSessao();
    } catch (err) {
      // O interceptor já promove a mensagem de BusinessException ("Senha
      // incorreta") a `userMessage` — o contorno local do BUG-0069 saiu junto
      // com a correção na origem (PROB-0083).
      setErroExcluir(mensagemDeErro(err, 'Não foi possível excluir a conta. Tente novamente.'));
    } finally {
      setExcluindo(false);
    }
  };

  // Dois brilhos da mesma família: com `accent` na base o bloco lê como bicolor,
  // o que salta demais no tema claro.
  const brilho = misturar(colors.card, colors.brand, 0.3);
  const brilhoBase = misturar(colors.card, colors.brand, 0.14);

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScrollView contentContainerStyle={{ paddingBottom: tabBarSpace }}>
        <CabecalhoDeTela titulo="Ajustes" />

        {/* Conta — o único bloco com destaque da tela */}
        <Entrance delay={50}>
          <SuperficieComBrilho
            id="conta"
            tintaTopo={brilho}
            tintaBase={brilhoBase}
            borderRadius={radius.xl}
            style={{ marginHorizontal: spacing.lg, marginTop: spacing.sm }}
          >
            <TouchableOpacity
              onPress={() => router.push('/(app)/perfil')}
              activeOpacity={0.85}
              accessibilityRole="button"
              accessibilityLabel="Abrir perfil"
              style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md, padding: spacing.lg }}
            >
              <View style={{
                width: 44, height: 44, borderRadius: 22,
                backgroundColor: colors.brandBg,
                alignItems: 'center', justifyContent: 'center',
              }}>
                <Text style={{ ...typography.cardTitle, color: colors.brandFg }}>
                  {usuario?.nome ? getInitials(usuario.nome) : ''}
                </Text>
              </View>
              <View style={{ flex: 1, minWidth: 0 }}>
                <Text numberOfLines={1} style={{ ...typography.cardTitle, color: colors.textPrimary }}>
                  {usuario?.nome ?? 'Sua conta'}
                </Text>
                <Text numberOfLines={1} style={{ ...typography.meta, color: colors.textSecondary, marginTop: 2 }}>
                  {usuario?.email ?? 'Dados pessoais e senha'}
                </Text>
              </View>
              <Ionicons name="chevron-forward" size={18} color={colors.textMuted} />
            </TouchableOpacity>

            <View style={{ height: 1, backgroundColor: colors.border, marginHorizontal: spacing.lg }} />

            <TouchableOpacity
              onPress={() => router.push('/(app)/notificacoes')}
              activeOpacity={0.85}
              accessibilityRole="button"
              accessibilityLabel={naoLidas ? `Notificações, ${naoLidas} não lidas` : 'Notificações'}
              style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md, padding: spacing.lg, minHeight: 44 }}
            >
              <Ionicons name="notifications-outline" size={20} color={colors.textPrimary} style={{ width: 44, textAlign: 'center' }} />
              <Text style={{ ...typography.label, color: colors.textPrimary, flex: 1 }}>Notificações</Text>
              {!!naoLidas && (
                <View style={{
                  minWidth: 22, height: 22, borderRadius: 11, paddingHorizontal: 6,
                  backgroundColor: colors.brand, alignItems: 'center', justifyContent: 'center',
                }}>
                  <Text style={{ ...typography.meta, fontWeight: '700', color: colors.brandText }}>
                    {naoLidas > 9 ? '9+' : naoLidas}
                  </Text>
                </View>
              )}
              <Ionicons name="chevron-forward" size={18} color={colors.textMuted} />
            </TouchableOpacity>
          </SuperficieComBrilho>
        </Entrance>

        {/* Aparência */}
        <Entrance delay={100}>
          <CabecalhoSecao
            eyebrow="APARÊNCIA"
            titulo="Como o app se apresenta"
            texto="Siga o sistema ou trave num tema. A escolha vale só neste aparelho."
          />
          <View style={{ flexDirection: 'row', gap: spacing.sm, paddingHorizontal: spacing.lg }}>
            {TEMAS.map(t => (
              <Chip
                key={t.id}
                label={t.label}
                selected={preferencia === t.id}
                onPress={() => setPreferencia(t.id)}
              />
            ))}
          </View>
        </Entrance>

        {/* Ferramentas */}
        <Entrance delay={150}>
          <CabecalhoSecao
            eyebrow="FERRAMENTAS"
            titulo="Seu dinheiro por dentro"
            texto="As telas de gestão que não cabem na barra de navegação."
          />
          <View style={{
            flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md,
            paddingHorizontal: spacing.lg,
          }}>
            {FERRAMENTAS.map(item => (
              <TouchableOpacity
                key={item.label}
                onPress={() => item.rota && router.push(item.rota as never)}
                activeOpacity={item.desabilitado ? 1 : 0.85}
                disabled={item.desabilitado}
                accessibilityRole="button"
                accessibilityLabel={item.desabilitado ? `${item.label} (em breve)` : item.label}
                accessibilityState={{ disabled: !!item.desabilitado }}
                style={{
                  // sem `flexGrow`: o item ímpar (IA) esticaria para a largura toda
                  flexBasis: '48%', flexGrow: 0,
                  minHeight: 44,
                  padding: spacing.md,
                  borderRadius: radius.lg,
                  backgroundColor: colors.card,
                  borderWidth: 1,
                  borderColor: colors.border,
                  opacity: item.desabilitado ? 0.55 : 1,
                }}
              >
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  {/* `neutral` do IconTile é `colors.card`, a mesma cor do tile de
                      ferramenta — o quadradinho sumiria. `overlay` é o degrau acima. */}
                  <IconTile tone="neutral" size={40} style={{ backgroundColor: colors.overlay }}>
                    {item.icone}
                  </IconTile>
                  {item.desabilitado && (
                    <View style={{
                      backgroundColor: colors.warningBg,
                      paddingHorizontal: spacing.sm, paddingVertical: 3,
                      borderRadius: radius.pill,
                    }}>
                      <Text style={{
                        ...typography.meta, fontSize: 10, fontWeight: '700',
                        color: colors.warning, textTransform: 'uppercase', letterSpacing: 0.4,
                      }}>
                        Em breve
                      </Text>
                    </View>
                  )}
                </View>
                <Text numberOfLines={1} style={{ ...typography.label, fontWeight: '700', color: colors.textPrimary, marginTop: spacing.md }}>
                  {item.label}
                </Text>
                <Text numberOfLines={1} style={{ ...typography.meta, color: colors.textSecondary, marginTop: 2 }}>
                  {item.sub}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </Entrance>

        {/* Dados e privacidade */}
        <Entrance delay={200}>
          <CabecalhoSecao
            eyebrow="DADOS E PRIVACIDADE"
            titulo="Seus dados são seus"
            texto="Traga um extrato de fora, leve os seus para onde quiser, ou apague tudo."
          />
          <Card radius={radius.xl} padded={false} style={{ marginHorizontal: spacing.lg, paddingHorizontal: spacing.lg }}>
            <ListRow
              icon="⇪"
              iconTone="neutral"
              title="Importar CSV"
              subtitle="Extrato do seu banco"
              onPress={importarCsv}
              trailing={<Ionicons name="chevron-forward" size={18} color={colors.textMuted} />}
            />
            <ListRow
              icon="📥"
              iconTone="neutral"
              title="Exportar dados"
              subtitle="Tudo em um CSV"
              onPress={exportarDados}
              trailing={<Ionicons name="chevron-forward" size={18} color={colors.textMuted} />}
            />
            <ListRow
              icon="🔒"
              iconTone="neutral"
              title="Política de privacidade"
              subtitle="O que guardamos e por quê"
              divider={false}
              onPress={() => router.push('/(auth)/privacidade')}
              trailing={<Ionicons name="chevron-forward" size={18} color={colors.textMuted} />}
            />
          </Card>
        </Entrance>

        {/* Conta: sair e excluir */}
        <View style={{ paddingHorizontal: spacing.lg, marginTop: spacing.xxl, gap: spacing.md }}>
          <TouchableOpacity
            onPress={sair}
            activeOpacity={0.85}
            accessibilityRole="button"
            accessibilityLabel="Sair da conta"
            style={{
              minHeight: 48, borderRadius: radius.md,
              borderWidth: 1, borderColor: colors.border,
              alignItems: 'center', justifyContent: 'center',
            }}
          >
            <Text style={{ ...typography.button, color: colors.textPrimary }}>Sair da conta</Text>
          </TouchableOpacity>

          <TouchableOpacity
            onPress={pedirExclusao}
            activeOpacity={0.85}
            accessibilityRole="button"
            accessibilityLabel="Excluir minha conta"
            style={{ minHeight: 44, alignItems: 'center', justifyContent: 'center' }}
          >
            <Text style={{ ...typography.label, color: colors.danger }}>Excluir minha conta</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>

      <Modal visible={modalExcluirVisible} animationType="slide" presentationStyle="pageSheet">
        <View style={{ flex: 1, backgroundColor: colors.bg }}>
          <View style={{
            flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
            padding: spacing.lg, borderBottomWidth: 1, borderBottomColor: colors.border,
          }}>
            <TouchableOpacity
              onPress={() => setModalExcluirVisible(false)}
              accessibilityRole="button"
              disabled={excluindo}
            >
              <Text style={{ ...typography.label, color: colors.brandFg }}>Cancelar</Text>
            </TouchableOpacity>
            <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>Excluir conta</Text>
            <View style={{ width: 64 }} />
          </View>

          <ScrollView contentContainerStyle={{ padding: spacing.lg }} keyboardShouldPersistTaps="handled">
            <Text style={{ ...typography.body, color: colors.textSecondary, marginBottom: spacing.lg }}>
              Confirme com sua senha. Assim que você continuar, a conta e todos os dados são
              apagados definitivamente.
            </Text>

            <Field
              label="Senha atual"
              value={senhaExcluir}
              onChangeText={setSenhaExcluir}
              secureTextEntry
              autoCapitalize="none"
              error={erroExcluir}
            />

            <TouchableOpacity
              onPress={confirmarExclusao}
              disabled={excluindo}
              activeOpacity={0.85}
              accessibilityRole="button"
              accessibilityLabel="Excluir minha conta definitivamente"
              style={{
                minHeight: 48, borderRadius: radius.md,
                backgroundColor: colors.danger,
                alignItems: 'center', justifyContent: 'center',
                opacity: excluindo ? 0.6 : 1,
              }}
            >
              {excluindo
                ? <ActivityIndicator color={colors.brandText} />
                : <Text style={{ ...typography.button, color: '#ffffff' }}>Excluir definitivamente</Text>}
            </TouchableOpacity>
          </ScrollView>
        </View>
      </Modal>
    </View>
  );
}
