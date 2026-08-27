jest.mock('expo-notifications', () => ({
  getPermissionsAsync: jest.fn(),
  requestPermissionsAsync: jest.fn(),
  getExpoPushTokenAsync: jest.fn(),
  setNotificationChannelAsync: jest.fn(),
  AndroidImportance: { DEFAULT: 3 },
  AndroidNotificationVisibility: { PRIVATE: 0 },
}));

jest.mock('expo-constants', () => ({
  __esModule: true,
  default: { isDevice: true, expoConfig: { extra: { eas: { projectId: 'projeto-1' } } } },
}));

jest.mock('../services/notificacaoService', () => ({
  __esModule: true,
  default: { registrarDispositivo: jest.fn(), revogarDispositivo: jest.fn() },
}));

import * as Notifications from 'expo-notifications';
import notificacaoService from '../services/notificacaoService';
import { registrarDispositivoParaPush, revogarDispositivoDePush } from '../notificacoes/push';

const permissoes = Notifications.getPermissionsAsync as jest.Mock;
const pedirPermissao = Notifications.requestPermissionsAsync as jest.Mock;
const obterToken = Notifications.getExpoPushTokenAsync as jest.Mock;
const servico = notificacaoService as unknown as {
  registrarDispositivo: jest.Mock;
  revogarDispositivo: jest.Mock;
};

const TOKEN = 'ExponentPushToken[abc-123]';

beforeEach(() => {
  jest.clearAllMocks();
  obterToken.mockResolvedValue({ data: TOKEN });
  servico.registrarDispositivo.mockResolvedValue(undefined);
  servico.revogarDispositivo.mockResolvedValue(undefined);
});

describe('registro de push', () => {
  it('pede permissão uma vez e registra o aparelho no servidor', async () => {
    permissoes.mockResolvedValue({ granted: false, canAskAgain: true });
    pedirPermissao.mockResolvedValue({ granted: true });

    await expect(registrarDispositivoParaPush()).resolves.toBe(TOKEN);

    expect(pedirPermissao).toHaveBeenCalledTimes(1);
    expect(servico.registrarDispositivo).toHaveBeenCalledWith(TOKEN, 'IOS');
  });

  it('não pede de novo quando o usuário já disse não', async () => {
    permissoes.mockResolvedValue({ granted: false, canAskAgain: false });

    await expect(registrarDispositivoParaPush()).resolves.toBeNull();

    expect(pedirPermissao).not.toHaveBeenCalled();
    expect(servico.registrarDispositivo).not.toHaveBeenCalled();
  });

  it('falha ao obter token não atrapalha o app', async () => {
    permissoes.mockResolvedValue({ granted: true, canAskAgain: false });
    obterToken.mockRejectedValue(new Error('sem serviço de push'));

    await expect(registrarDispositivoParaPush()).resolves.toBeNull();
    expect(servico.registrarDispositivo).not.toHaveBeenCalled();
  });

  it('sair da conta revoga o aparelho registrado', async () => {
    permissoes.mockResolvedValue({ granted: true, canAskAgain: false });
    await registrarDispositivoParaPush();

    await revogarDispositivoDePush();

    expect(servico.revogarDispositivo).toHaveBeenCalledWith(TOKEN);
  });

  it('revogar sem registro anterior não chama o servidor', async () => {
    await revogarDispositivoDePush();
    expect(servico.revogarDispositivo).not.toHaveBeenCalled();
  });

  it('erro ao revogar não impede a saída da conta', async () => {
    permissoes.mockResolvedValue({ granted: true, canAskAgain: false });
    await registrarDispositivoParaPush();
    servico.revogarDispositivo.mockRejectedValue(new Error('rede'));

    await expect(revogarDispositivoDePush()).resolves.toBeUndefined();
  });
});
