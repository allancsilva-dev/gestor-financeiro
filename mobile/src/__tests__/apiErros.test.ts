jest.mock('expo-secure-store', () => ({
  setItemAsync: jest.fn(), getItemAsync: jest.fn().mockResolvedValue(null), deleteItemAsync: jest.fn(),
}));

import api from '../services/api';
import { ApiErrorWithMessage } from '../types';
import { camposDeErro, ehSessaoExpirada, mensagemDeErro, segundosParaTentarDeNovo } from '../utils/erros';

// O interceptor de resposta é o ponto único que traduz o envelope do backend
// (`{code,message,details}`) para a tela. Chamamos o handler direto — subir um
// servidor só para isso não acrescenta cobertura.
const handlers = (api.interceptors.response as unknown as {
  handlers: Array<{ rejected: (erro: unknown) => Promise<never> }>;
}).handlers;
const rejeitar = handlers[0].rejected;

const erroDe = (
  status: number,
  data: unknown,
  headers: Record<string, string> = {},
  url = '/v1/usuarios/me',
) => ({
  isAxiosError: true,
  config: { url },
  response: { status, data, headers },
});

const capturar = async (erro: unknown): Promise<ApiErrorWithMessage> => {
  try {
    await rejeitar(erro);
    throw new Error('interceptor deveria rejeitar');
  } catch (e) {
    return e as ApiErrorWithMessage;
  }
};

describe('interceptor de erro da API (PROB-0083)', () => {
  it('usa a mensagem de BusinessException quando 422 vem sem details', async () => {
    const erro = await capturar(erroDe(422, { code: 'BUSINESS_ERROR', message: 'Email já cadastrado!', details: null }));
    expect(erro.userMessage).toBe('Email já cadastrado!');
    expect(erro.codigo).toBe('BUSINESS_ERROR');
    expect(erro.status).toBe(422);
  });

  it('mantém o detalhe de campo na frente da mensagem genérica em 400', async () => {
    const erro = await capturar(erroDe(400, {
      code: 'VALIDATION_ERROR',
      message: 'Dados de entrada inválidos',
      details: { email: 'Email invalido' },
    }));
    expect(erro.userMessage).toBe('Dados inválidos: Email invalido');
    expect(erro.campos).toEqual({ email: 'Email invalido' });
  });

  it('preserva todos os campos de details, não só o primeiro', async () => {
    const erro = await capturar(erroDe(400, {
      code: 'VALIDATION_ERROR',
      details: { nome: 'Campo obrigatorio', password: 'Senha fraca' },
    }));
    expect(erro.campos).toEqual({ nome: 'Campo obrigatorio', password: 'Senha fraca' });
    expect(camposDeErro(erro, { password: 'senha' })).toEqual({ senha: 'Senha fraca' });
  });

  it('mostra o motivo do 429 de conta bloqueada', async () => {
    const erro = await capturar(erroDe(429, {
      code: 'ACCOUNT_LOCKED',
      message: 'Conta temporariamente bloqueada. Tente novamente em 900 segundos.',
    }, { 'retry-after': '900' }, '/auth/login'));
    expect(erro.userMessage).toContain('bloqueada');
    expect(erro.codigo).toBe('ACCOUNT_LOCKED');
    expect(segundosParaTentarDeNovo(erro)).toBe(900);
  });

  it('monta mensagem de espera pelo Retry-After quando o 429 não traz texto', async () => {
    const erro = await capturar(erroDe(429, {}, { 'retry-after': '45' }, '/auth/register'));
    expect(erro.userMessage).toBe('Muitas tentativas. Aguarde 45 segundos e tente de novo.');
  });

  it('não tenta renovar token em rota de auth e reporta sessão expirada fora dela', async () => {
    const noAuth = await capturar(erroDe(401, { code: 'UNAUTHORIZED' }, {}, '/auth/login'));
    expect(noAuth.userMessage).toBe('Sessão expirada. Faça login novamente.');
    expect(ehSessaoExpirada(noAuth)).toBe(true);
  });

  it('mantém a mensagem de rede quando não há resposta', async () => {
    const erro = await capturar({ isAxiosError: true, config: { url: '/v1/metas' } });
    expect(mensagemDeErro(erro)).toBe('Sem conexão. Verifique sua internet.');
    expect(erro.campos).toBeUndefined();
  });
});
