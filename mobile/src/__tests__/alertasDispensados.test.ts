jest.mock('expo-secure-store', () => ({
  setItemAsync: jest.fn(),
  getItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

import * as SecureStore from 'expo-secure-store';
import {
  chaveDoAlerta, competenciaDoMes, dispensarAlerta, listarDispensados,
} from '../store/alertasDispensados';

const AGOSTO = new Date(2026, 7, 27);
const SETEMBRO = new Date(2026, 8, 2);

describe('alertas dispensados', () => {
  beforeEach(() => jest.clearAllMocks());

  it('normaliza a chave para o mesmo alerta não voltar por causa de caixa alta', () => {
    expect(chaveDoAlerta('  Alimentação ')).toBe('categoria:alimentação');
    expect(chaveDoAlerta('ALIMENTAÇÃO')).toBe(chaveDoAlerta('alimentação'));
  });

  it('guarda a dispensa junto da competência em que ela vale', async () => {
    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue(null);

    await dispensarAlerta('categoria:mercado', AGOSTO);

    expect(SecureStore.setItemAsync).toHaveBeenCalledWith(
      'alertasDispensados',
      JSON.stringify({ competencia: '2026-08', chaves: ['categoria:mercado'] }),
    );
  });

  it('não repete a mesma chave nem grava de novo', async () => {
    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue(
      JSON.stringify({ competencia: competenciaDoMes(AGOSTO), chaves: ['categoria:mercado'] }));

    await expect(dispensarAlerta('categoria:mercado', AGOSTO)).resolves.toEqual(['categoria:mercado']);
    expect(SecureStore.setItemAsync).not.toHaveBeenCalled();
  });

  it('dispensa do mês passado não silencia o alerta deste mês', async () => {
    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue(
      JSON.stringify({ competencia: '2026-08', chaves: ['categoria:mercado'] }));

    await expect(listarDispensados(AGOSTO)).resolves.toEqual(['categoria:mercado']);
    await expect(listarDispensados(SETEMBRO)).resolves.toEqual([]);
  });

  it('tolera valor ausente, JSON quebrado e formato inesperado', async () => {
    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue(null);
    await expect(listarDispensados(AGOSTO)).resolves.toEqual([]);

    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue('{quebrado');
    await expect(listarDispensados(AGOSTO)).resolves.toEqual([]);

    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue('{"competencia":"2026-08"}');
    await expect(listarDispensados(AGOSTO)).resolves.toEqual([]);
  });
});
