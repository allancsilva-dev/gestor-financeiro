import React from 'react';
import { Image } from 'react-native';
import { render, screen } from '@testing-library/react-native';
import CartaoFisico from '../components/carteira/CartaoFisico';
import { identidadeDoCartao } from '../domain/emissores';

jest.mock('@expo/vector-icons/Ionicons', () => 'Ionicons');
jest.mock('expo-linear-gradient', () => ({ LinearGradient: 'LinearGradient' }));

/**
 * A queixa que originou esta tela: o usuário digitou "PicPay" e o cartão saiu
 * com um quadradinho escuro e a letra "P". Estes testes travam o contrário —
 * marca conhecida mostra o logo de verdade, e marca desconhecida continua com
 * o monograma em vez de quebrar.
 */
describe('CartaoFisico', () => {
  it('mostra o logo da marca no tile quando o nome resolve um emissor', () => {
    render(<CartaoFisico nome="PicPay" largura={287} />);
    const imagens = screen.UNSAFE_getAllByType(Image);
    expect(imagens).toHaveLength(1);
    expect(imagens[0].props.source).toBe(identidadeDoCartao({ nome: 'PicPay' }).logo);
    expect(screen.queryByText('P')).toBeNull();
  });

  it('cai no monograma, sem imagem, para emissor fora do catálogo', () => {
    render(<CartaoFisico nome="Cartão do Zé" largura={287} />);
    expect(screen.UNSAFE_queryAllByType(Image)).toHaveLength(0);
    expect(screen.getByText(identidadeDoCartao({ nome: 'Cartão do Zé' }).glifo)).toBeTruthy();
  });

  it('PicPay Epic usa a mesma marca com a cor da variante', () => {
    const epic = identidadeDoCartao({ nome: 'PicPay Epic' });
    render(<CartaoFisico nome="PicPay Epic" largura={287} />);
    expect(screen.UNSAFE_getAllByType(Image)[0].props.source).toBe(epic.logo);
    expect(epic.from).not.toBe(identidadeDoCartao({ nome: 'PicPay' }).from);
  });
});
