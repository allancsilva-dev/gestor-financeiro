const secureStore: Record<string, string> = {};

jest.mock('expo-secure-store', () => ({
  setItemAsync: jest.fn(async (k: string, v: string) => { secureStore[k] = v; }),
  getItemAsync: jest.fn(async (k: string) => secureStore[k] ?? null),
  deleteItemAsync: jest.fn(async (k: string) => { delete secureStore[k]; }),
}));

jest.mock('axios', () => {
  const post = jest.fn();
  return {
    __esModule: true,
    default: {
      post,
      create: () => ({
        interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } },
        defaults: { headers: { common: {} } },
      }),
    },
  };
});

import axios from 'axios';
import { refreshAccessToken, setOnSessionExpired } from '../services/api';
import { getAccessToken, getRefreshToken, setAccessToken, setRefreshToken } from '../store/auth';

const post = (axios as unknown as { post: jest.Mock }).post;

// Falha do refresh só chegava a limpar credenciais em 401/403. O backend também
// respondia 404 (token sumiu do banco) e 422 (expirado), e nesses casos os
// tokens mortos ficavam guardados: toda request caía em 401 e o app travava
// sem voltar ao login.
const semear = async () => {
  await setAccessToken('access-velho');
  await setRefreshToken('refresh-velho');
};

const recusaComStatus = (status: number) => {
  const erro = new Error('recusado') as Error & { response?: unknown };
  erro.response = { status, data: { code: 'SESSION_EXPIRED' } };
  return erro;
};

const falhaDeRede = () => new Error('Network Error');

describe('refresh recusado encerra a sessão', () => {
  beforeEach(async () => {
    for (const k of Object.keys(secureStore)) delete secureStore[k];
    post.mockReset();
    setOnSessionExpired(null);
  });

  it.each([401, 403, 404, 422])('descarta credenciais e avisa quando o servidor responde %i', async (status) => {
    await semear();
    post.mockRejectedValueOnce(recusaComStatus(status));
    const avisou = jest.fn();
    setOnSessionExpired(avisou);

    const token = await refreshAccessToken();

    expect(token).toBeNull();
    expect(await getAccessToken()).toBeNull();
    expect(await getRefreshToken()).toBeNull();
    expect(avisou).toHaveBeenCalledTimes(1);
  });

  it('preserva os tokens quando o refresh falha por rede', async () => {
    await semear();
    post.mockRejectedValueOnce(falhaDeRede());
    const avisou = jest.fn();
    setOnSessionExpired(avisou);

    const token = await refreshAccessToken();

    expect(token).toBeNull();
    expect(await getAccessToken()).toBe('access-velho');
    expect(await getRefreshToken()).toBe('refresh-velho');
    expect(avisou).not.toHaveBeenCalled();
  });

  it('grava o par rotacionado quando o refresh dá certo', async () => {
    await semear();
    post.mockResolvedValueOnce({ data: { accessToken: 'access-novo', refreshToken: 'refresh-novo' } });

    const token = await refreshAccessToken();

    expect(token).toBe('access-novo');
    expect(await getAccessToken()).toBe('access-novo');
    // A rotação do backend revoga o token usado: guardar o novo é o que impede
    // a próxima renovação de ser lida como reuso e derrubar todas as sessões.
    expect(await getRefreshToken()).toBe('refresh-novo');
  });

  it('compartilha uma única chamada entre requests simultâneas', async () => {
    await semear();
    post.mockResolvedValueOnce({ data: { accessToken: 'access-novo', refreshToken: 'refresh-novo' } });

    const [a, b, c] = await Promise.all([refreshAccessToken(), refreshAccessToken(), refreshAccessToken()]);

    expect(post).toHaveBeenCalledTimes(1);
    expect([a, b, c]).toEqual(['access-novo', 'access-novo', 'access-novo']);
  });
});
