// Força da senha. A régua obrigatória é a do backend (@ValidPassword: mínimo 8,
// ao menos 1 letra e 1 número, espelhada em `isValidPassword`); os pontos
// extras são só orientação visual e nunca bloqueiam o cadastro.
export type NivelDeSenha = 0 | 1 | 2 | 3 | 4;

export interface ForcaDaSenha {
  nivel: NivelDeSenha;
  rotulo: string;
  /** O que falta para a senha ser aceita pelo backend. Vazio = já serve. */
  pendencias: string[];
}

const ROTULOS: Record<NivelDeSenha, string> = {
  0: 'Muito fraca',
  1: 'Fraca',
  2: 'Razoável',
  3: 'Boa',
  4: 'Forte',
};

export const forcaSenha = (senha: string): ForcaDaSenha => {
  const pendencias: string[] = [];
  if (senha.length < 8) pendencias.push('8 caracteres');
  if (!/[A-Za-z]/.test(senha)) pendencias.push('1 letra');
  if (!/\d/.test(senha)) pendencias.push('1 número');

  if (senha.length === 0) return { nivel: 0, rotulo: ROTULOS[0], pendencias };

  let pontos = 0;
  if (pendencias.length === 0) pontos += 2;
  if (senha.length >= 12) pontos += 1;
  if (/[^A-Za-z0-9]/.test(senha) || (/[a-z]/.test(senha) && /[A-Z]/.test(senha))) pontos += 1;

  const nivel = Math.min(pontos, 4) as NivelDeSenha;
  return { nivel, rotulo: ROTULOS[nivel], pendencias };
};
