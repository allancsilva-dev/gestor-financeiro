import React from 'react';
import { View } from 'react-native';
import Chip from './Chip';
import { FREQUENCIAS, nomeFrequencia } from '../../domain/recorrencia';
import { FrequenciaRecorrencia } from '../../types';
import { spacing } from '../../theme';

interface SeletorDeFrequenciaProps {
  valor: FrequenciaRecorrencia;
  onSelecionar: (frequencia: FrequenciaRecorrencia) => void;
}

/**
 * As 7 periodicidades, todas visíveis.
 *
 * Antes cada tela repetia um `ScrollView horizontal` sem indicador: só ~3,5 chips
 * cabiam na largura de um iPhone, e Trimestral, Semestral e Anual ficavam 100% fora
 * da tela sem nenhuma dica de que existiam — o dono concluiu que "não tem opção de um
 * ano". Grade que quebra linha resolve porque nada depende de descobrir o gesto.
 */
export default function SeletorDeFrequencia({ valor, onSelecionar }: SeletorDeFrequenciaProps) {
  return (
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, marginBottom: spacing.md }}>
      {FREQUENCIAS.map(f => (
        <Chip
          key={f}
          label={nomeFrequencia(f)}
          selected={valor === f}
          onPress={() => onSelecionar(f)}
        />
      ))}
    </View>
  );
}
