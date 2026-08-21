import { forcaSenha } from '../utils/forcaSenha';
import { isValidPassword } from '../utils/validate';

describe('força da senha', () => {
  it('lista exatamente o que falta para o backend aceitar', () => {
    expect(forcaSenha('abc').pendencias).toEqual(['8 caracteres', '1 número']);
    expect(forcaSenha('12345678').pendencias).toEqual(['1 letra']);
    expect(forcaSenha('senha123').pendencias).toEqual([]);
  });

  it('não marca como aceitável nada que o backend recusaria', () => {
    for (const senha of ['', 'abc', '12345678', 'abcdefgh']) {
      expect(isValidPassword(senha)).toBe(false);
      expect(forcaSenha(senha).pendencias.length).toBeGreaterThan(0);
    }
  });

  it('sobe de nível com tamanho e variedade', () => {
    expect(forcaSenha('senha123').nivel).toBe(2);
    expect(forcaSenha('senhalonga123').nivel).toBe(3);
    expect(forcaSenha('SenhaLonga123!').nivel).toBe(4);
  });
});
