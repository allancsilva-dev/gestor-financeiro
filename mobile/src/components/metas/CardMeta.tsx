import React, { useMemo } from 'react';
import { View, Text, TouchableOpacity } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';
import { Meta } from '../../types';
import { useTheme, numeric } from '../../theme';
import Botao from '../ui/Botao';
import ProgressBar from '../ui/ProgressBar';
import { e } from '../../theme/escala';
import { paletaDaMeta } from '../../theme/metaCores';
import { acoesDaMeta } from '../../domain/metaPolicy';
import { rotuloDeRitmo } from '../../domain/metaProjecao';
import { formatCurrency, formatPercent } from '../../utils/format';
import Badge from '../ui/Badge';
import SuperficieComBrilho from '../ui/SuperficieComBrilho';
import AnelProgresso from './AnelProgresso';
import { EMOJI_GENERICO, emojiDaCategoria } from '../../domain/iconeCategoria';

interface Props {
  meta: Meta;
  onAbrir: (meta: Meta) => void;
  onDepositar: (meta: Meta) => void;
  onEditar: (meta: Meta) => void;
  onExcluir: (meta: Meta) => void;
}

// Medidas da referência (360dp), convertidas para a tela atual por `e()`
const TILE = e(82);
const ANEL = e(66);
const TOQUE = { top: 12, bottom: 12, left: 10, right: 10 };

/**
 * Card de meta da referência (`mobile/.design/referencia-metas.png`).
 *
 * Fundo neutro com dois brilhos radiais na cor da meta — topo-direita e
 * base-esquerda —, anel de progresso dentro de um tile de 82, e rodapé com o ritmo
 * do mês à esquerda e as ações à direita. Medidas em `.design/MEDICOES-metas.md`.
 */
export default function CardMeta({ meta, onAbrir, onDepositar, onEditar, onExcluir }: Props) {
  const colors = useTheme();
  const paleta = useMemo(() => paletaDaMeta(meta, colors.card), [meta, colors.card]);
  const acoes = acoesDaMeta(meta);

  const total = Number(meta.valorTotal ?? 0);
  const reservado = Number(meta.valorReservado ?? 0);
  const progresso = total > 0 ? Math.min((reservado / total) * 100, 100) : 0;
  const ritmo = rotuloDeRitmo(meta);
  const corDoRitmo = ritmo?.tom === 'atencao' ? colors.warning : colors.brandFg;

  return (
    /*
      O card não é UM botão só: enquanto a raiz inteira era `TouchableOpacity`,
      o iOS fundia tudo num único nó de acessibilidade
      ("Meta Smoke, 0%, ..., Excluir a meta, Editar a meta, Depositar") e os
      três botões da linha de ações deixavam de ser elementos próprios — o
      VoiceOver lia "Depositar" como texto e não tinha como acioná-lo. Agora só
      o bloco de informação abre os detalhes; as ações são irmãs dele.
    */
    <View style={{ marginHorizontal: e(18), marginBottom: e(33) }}>
      <SuperficieComBrilho
        id={meta.id}
        tintaTopo={paleta.tintaTopo}
        tintaBase={paleta.tintaBase}
        borderRadius={e(20)}
        style={{ paddingHorizontal: e(16), paddingVertical: e(10) }}
      >
        <TouchableOpacity
          activeOpacity={0.9}
          accessibilityRole="button"
          accessibilityHint="Abre os detalhes da meta"
          onPress={() => onAbrir(meta)}
          style={{ flexDirection: 'row', alignItems: 'center' }}
        >
          <View
            style={{
              width: TILE, height: TILE, borderRadius: e(18),
              borderWidth: 1, borderColor: colors.border,
              backgroundColor: colors.overlay,
              alignItems: 'center', justifyContent: 'center',
            }}
          >
            <AnelProgresso
              progresso={progresso}
              paleta={paleta}
              emoji={emojiDaCategoria(meta, EMOJI_GENERICO)}
              tamanho={ANEL}
              espessura={e(5)}
              id={meta.id}
              accessibilityLabel={`${formatPercent(progresso)} concluído`}
            />
          </View>

          <View style={{ flex: 1, minWidth: 0, marginLeft: e(11) }}>
            <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: 8 }}>
              <Text
                numberOfLines={1}
                style={{ flex: 1, color: colors.textPrimary, fontSize: e(18), fontWeight: '700', letterSpacing: -0.2 }}
              >
                {meta.nome}
              </Text>
              <Text style={{ color: paleta.percentual, fontSize: e(17), fontWeight: '800', ...numeric }}>
                {formatPercent(progresso, 0)}
              </Text>
            </View>

            <View style={{ flexDirection: 'row', alignItems: 'flex-end', marginTop: e(6) }}>
              <Text numberOfLines={1} style={{ color: colors.textPrimary, fontSize: e(13), fontWeight: '700', ...numeric }}>
                {formatCurrency(reservado)}
              </Text>
              <Text
                numberOfLines={1}
                style={{ flexShrink: 1, color: colors.textSecondary, fontSize: e(12), fontWeight: '500', marginLeft: e(6), ...numeric }}
              >
                de {formatCurrency(total)}
              </Text>
            </View>

            <View style={{ marginTop: e(12) }}>
              <ProgressBar value={progresso} height={e(6)} paleta={paleta} />
            </View>
          </View>
        </TouchableOpacity>

        <View style={{ height: 1, backgroundColor: colors.border, marginTop: e(10) }} />

        <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: e(11) }}>
          <Text
            numberOfLines={2}
            style={{ flex: 1, color: corDoRitmo, fontSize: e(10), lineHeight: e(14), fontWeight: '600', paddingRight: e(4) }}
          >
            {ritmo?.texto ?? ''}
          </Text>

          <TouchableOpacity
            onPress={() => onExcluir(meta)}
            hitSlop={TOQUE}
            accessibilityRole="button"
            accessibilityLabel={`Excluir a meta ${meta.nome}`}
            style={{ paddingHorizontal: e(5) }}
          >
            <Ionicons name="trash-outline" size={e(16)} color={colors.textMuted} />
          </TouchableOpacity>

          <TouchableOpacity
            onPress={() => onEditar(meta)}
            hitSlop={TOQUE}
            accessibilityRole="button"
            accessibilityLabel={`Editar a meta ${meta.nome}`}
            style={{ paddingHorizontal: e(5), marginRight: e(6) }}
          >
            <Ionicons name="pencil-outline" size={e(16)} color={colors.textMuted} />
          </TouchableOpacity>

          {acoes.adicionar ? (
            <Botao
              titulo="Depositar"
              icone="add"
              variante="invertido"
              tamanho="pill"
              onPress={() => onDepositar(meta)}
              dica="Deposita um valor nesta meta"
              hitSlop={{ top: 6, bottom: 6 }}
              style={{ height: e(33), minHeight: e(33), minWidth: e(91), paddingHorizontal: e(6) }}
            />
          ) : (
            <Badge tone={meta.status === 'CONCLUIDA' ? 'success' : 'info'}>
              {meta.status === 'CONCLUIDA' ? 'Concluída' : 'Arquivada'}
            </Badge>
          )}
        </View>
      </SuperficieComBrilho>
    </View>
  );
}
