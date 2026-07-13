import { isValidDayOfMonth, isValidEmail, isValidPassword } from '../utils/validate';
import { isValidDateBR } from '../utils/format';

describe('validação financeira e auth', () => {
  test.each(['ana@example.com', 'a+b@empresa.com.br'])('aceita email válido %s', email => {
    expect(isValidEmail(email)).toBe(true);
  });
  test.each(['sem-arroba', '@dominio.com', 'a@b'])('rejeita email inválido %s', email => {
    expect(isValidEmail(email)).toBe(false);
  });
  it('valida senha, dia e calendário real', () => {
    expect(isValidPassword('senha123')).toBe(true);
    expect(isValidPassword('12345678')).toBe(false);
    expect(isValidDayOfMonth(31)).toBe(true);
    expect(isValidDayOfMonth(32)).toBe(false);
    expect(isValidDateBR('29/02/2024')).toBe(true);
    expect(isValidDateBR('29/02/2025')).toBe(false);
  });
});
