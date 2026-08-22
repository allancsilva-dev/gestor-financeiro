import React, { useState } from 'react';
import { Redirect, Tabs, useRouter } from 'expo-router';
import { Text, TouchableOpacity, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { LinearGradient } from 'expo-linear-gradient';
import Ionicons from '@expo/vector-icons/Ionicons';
import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { useReducedMotion } from 'react-native-reanimated';
import { useTheme, radius, spacing, typography, tabBar } from '../../src/theme';
import NovaTransacaoModal from '../../src/components/NovaTransacaoModal';
import { useAuth } from '../../src/context/AuthContext';

// Medidas da referência (mobile/.design/referencia-home.png, 591px ↔ 360dp):
// painel flutuante de 69 de altura com 15 de margem lateral, tile da aba ativa
// 43x24, barra indicadora 38x4 colada no topo do painel e FAB de 53.
const BARRA_ALTURA = tabBar.altura;
const BARRA_MARGEM = tabBar.margem;
const TILE_LARGURA = 43;
const TILE_ALTURA = 24;
const INDICADOR_LARGURA = 38;
const INDICADOR_ALTURA = 4;
const FAB_TAMANHO = 53;

type IconeProps = { color: string; focused: boolean };

/**
 * Ícone da aba: quando ativa, ganha o tile arredondado ciano atrás e a barra
 * indicadora no topo do painel — os dois detalhes que a referência usa para
 * marcar a posição.
 */
const ItemAba = ({
  focused,
  color,
  children,
  tileBg,
  indicador,
}: IconeProps & { children: React.ReactNode; tileBg: string; indicador: string }) => (
  <View style={{ alignItems: 'center', justifyContent: 'center' }}>
    <View
      style={{
        position: 'absolute',
        top: -((BARRA_ALTURA - TILE_ALTURA) / 2) - INDICADOR_ALTURA + 2,
        width: INDICADOR_LARGURA,
        height: INDICADOR_ALTURA,
        borderRadius: radius.pill,
        backgroundColor: focused ? indicador : 'transparent',
      }}
    />
    <View
      style={{
        width: TILE_LARGURA,
        height: TILE_ALTURA,
        borderRadius: radius.md,
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: focused ? tileBg : 'transparent',
      }}
    >
      {children}
    </View>
  </View>
);

export default function AppLayout() {
  const colors = useTheme();
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const { needsOnboarding } = useAuth();
  const [novaTransacaoVisible, setNovaTransacaoVisible] = useState(false);
  const reduceMotion = useReducedMotion();

  if (needsOnboarding) return <Redirect href="/onboarding" />;

  const aba = (nome: React.ComponentProps<typeof Ionicons>['name']) =>
    ({ color, focused }: IconeProps) => (
      <ItemAba focused={focused} color={color} tileBg={colors.tabActiveBg} indicador={colors.brand}>
        <Ionicons name={nome} size={20} color={color} />
      </ItemAba>
    );

  return (
    <>
      <Tabs screenOptions={{
        headerShown: false,
        animation: reduceMotion ? 'none' : 'fade',
        transitionSpec: reduceMotion ? undefined : { animation: 'timing', config: { duration: 150 } },
        sceneStyle: { backgroundColor: colors.bg },
        tabBarStyle: {
          position: 'absolute',
          left: BARRA_MARGEM,
          right: BARRA_MARGEM,
          bottom: insets.bottom > 0 ? insets.bottom : 12,
          height: BARRA_ALTURA,
          borderRadius: radius.xxl,
          backgroundColor: colors.navBg,
          borderTopWidth: 0,
          borderWidth: 1,
          borderColor: colors.navBorder,
          paddingBottom: 0,
          paddingTop: 0,
          elevation: 0,
        },
        tabBarItemStyle: { height: BARRA_ALTURA, paddingVertical: spacing.sm },
        tabBarActiveTintColor: colors.brand,
        tabBarInactiveTintColor: colors.textSecondary,
        tabBarLabelStyle: { ...typography.tabLabel, marginTop: spacing.xs },
      }}>
        <Tabs.Screen name="(inicio)" options={{ title: 'Início', tabBarIcon: aba('home') }} />
        <Tabs.Screen name="analises" options={{ title: 'Análises', tabBarIcon: aba('stats-chart') }} />
        <Tabs.Screen name="nova" options={{ title: '', tabBarButton: () => (
          <TouchableOpacity
            onPress={() => setNovaTransacaoVisible(true)}
            activeOpacity={0.85}
            accessibilityRole="button"
            accessibilityLabel="Nova transação"
            style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}
          >
            <LinearGradient
              colors={[colors.fabFrom, colors.fabTo]}
              start={{ x: 0, y: 0 }}
              end={{ x: 1, y: 1 }}
              style={{
                width: FAB_TAMANHO, height: FAB_TAMANHO, borderRadius: FAB_TAMANHO / 2,
                alignItems: 'center', justifyContent: 'center',
                shadowColor: colors.brand, shadowOpacity: 0.55, shadowRadius: 16,
                shadowOffset: { width: 0, height: 6 }, elevation: 10,
              }}
            >
              <Ionicons name="add" size={30} color={colors.brandText} />
            </LinearGradient>
          </TouchableOpacity>
        ) }} />
        <Tabs.Screen name="metas" options={{
          title: 'Metas',
          tabBarIcon: ({ color, focused }: IconeProps) => (
            <ItemAba focused={focused} color={color} tileBg={colors.tabActiveBg} indicador={colors.brand}>
              <MaterialCommunityIcons name="target" size={20} color={color} />
            </ItemAba>
          ),
        }} />
        <Tabs.Screen name="ajustes" options={{ title: 'Ajustes', tabBarIcon: aba('settings-sharp') }} />

        {/* Fora da barra: a referência move a lista de operações para dentro da
            Home. Transações virou filha da pilha de (inicio), então a aba
            Início continua marcada durante o drill-down (PR-F3-08). */}
        <Tabs.Screen name="perfil" options={{ href: null }} />
        <Tabs.Screen name="notificacoes" options={{ href: null }} />
        <Tabs.Screen name="more" options={{ href: null }} />
      </Tabs>

      <NovaTransacaoModal
        visible={novaTransacaoVisible}
        onClose={() => setNovaTransacaoVisible(false)}
        onSaved={() => router.push('/transacoes')}
      />
    </>
  );
}
