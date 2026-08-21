import { ApiErrorWithMessage } from '../types';

/**
 * Leitura do erro enriquecido pelo interceptor (`src/services/api.ts`).
 * As telas nunca devem ler `error.response.data` na mão: o envelope do backend
 * (`{ code, message, details }`) chega aqui já normalizado.
 */
const comoErroApi = (erro: unknown): ApiErrorWithMessage | null =>
  erro && typeof erro === 'object' ? (erro as ApiErrorWithMessage) : null;

export const mensagemDeErro = (
  erro: unknown,
  padrao = 'Erro inesperado. Tente novamente.',
): string => comoErroApi(erro)?.userMessage ?? padrao;

export const codigoDoErro = (erro: unknown): string | undefined => comoErroApi(erro)?.codigo;

export const statusDoErro = (erro: unknown): number | undefined => comoErroApi(erro)?.status;

/** Segundos a esperar em 429 (rate limit ou conta bloqueada). */
export const segundosParaTentarDeNovo = (erro: unknown): number | undefined =>
  comoErroApi(erro)?.retryAfterSegundos;

/** Sessão perdida no meio de um fluxo: a tela precisa oferecer "Entrar de novo". */
export const ehSessaoExpirada = (erro: unknown): boolean => {
  const status = statusDoErro(erro);
  return status === 401 || status === 403;
};

/**
 * Traduz o `details` do backend (campo → mensagem) para os campos da tela.
 * `mapa` liga a chave do DTO ao identificador local, ex.:
 * `{ email: 'email', passwordMatch: 'confirmPassword', 'carteira.nome': 'nome' }`.
 * Chaves não mapeadas ficam de fora — quem chama mostra `mensagemDeErro` como
 * faixa geral para não engolir o que sobrou.
 */
export const camposDeErro = <C extends string>(
  erro: unknown,
  mapa: Record<string, C>,
): Partial<Record<C, string>> => {
  const campos = comoErroApi(erro)?.campos;
  if (!campos) return {};

  const resultado: Partial<Record<C, string>> = {};
  for (const [chaveBackend, mensagem] of Object.entries(campos)) {
    const destino = mapa[chaveBackend];
    if (destino && !resultado[destino]) resultado[destino] = mensagem;
  }
  return resultado;
};

/** Chaves cruas de `details`, na ordem em que o backend mandou. */
export const chavesDeErro = (erro: unknown): string[] =>
  Object.keys(comoErroApi(erro)?.campos ?? {});

/** True quando o erro trouxe pelo menos um campo já mapeado para a tela. */
export const temErroDeCampo = <C extends string>(
  erro: unknown,
  mapa: Record<string, C>,
): boolean => Object.keys(camposDeErro(erro, mapa)).length > 0;
