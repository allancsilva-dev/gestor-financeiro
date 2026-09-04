import React from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { fireEvent, render, screen } from '@testing-library/react-native';
import SeletorDeFrequencia from '../components/ui/SeletorDeFrequencia';

/**
 * O bug não era "falta a opção Anual" — era não dar para vê-la.
 *
 * As 7 frequências viviam num `ScrollView horizontal` sem indicador: cabiam ~3,5 chips
 * na largura de um iPhone e Trimestral, Semestral e Anual ficavam 100% fora da tela.
 * O dono concluiu que "não tem opção de um ano".
 *
 * ARMADILHA: `getByText('Anual')` passava mesmo com o bug — o react-test-renderer
 * monta os filhos de um ScrollView normalmente, sem viewport nem clipping. Por isso o
 * teste aqui é estrutural: o layout precisa quebrar linha, e não rolar.
 */
describe('grade de frequências', () => {
  it('dispõe as opções em grade que quebra linha, sem scroll horizontal', () => {
    render(<SeletorDeFrequencia valor="MENSAL" onSelecionar={jest.fn()} />);

    const grade = screen.UNSAFE_getAllByType(View)[0];
    expect(StyleSheet.flatten(grade.props.style)).toEqual(
      expect.objectContaining({ flexDirection: 'row', flexWrap: 'wrap' }),
    );
    expect(screen.UNSAFE_queryAllByType(ScrollView)).toHaveLength(0);
  });

  it('mostra as 7 periodicidades', () => {
    render(<SeletorDeFrequencia valor="MENSAL" onSelecionar={jest.fn()} />);

    ['Semanal', 'Quinzenal', 'Mensal', 'Bimestral', 'Trimestral', 'Semestral', 'Anual']
      .forEach(rotulo => expect(screen.getByText(rotulo)).toBeTruthy());
  });

  it('devolve a frequência escolhida', () => {
    const onSelecionar = jest.fn();
    render(<SeletorDeFrequencia valor="MENSAL" onSelecionar={onSelecionar} />);

    fireEvent.press(screen.getByText('Anual'));

    expect(onSelecionar).toHaveBeenCalledWith('ANUAL');
  });
});
