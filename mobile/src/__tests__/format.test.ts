import { formatDate, formatDateOnlyBR, formatDateTime, parseDateBR } from '../utils/format';

// A API devolve LocalDate como string YYYY-MM-DD. Passar isso por new Date()
// resulta em meia-noite UTC e exibe o dia anterior em UTC-3 (America/Sao_Paulo).
describe('formatação de datas date-only da API', () => {
  test.each([
    ['2026-08-19', '19/08/2026'],
    ['2026-01-01', '01/01/2026'],
    ['2026-12-31', '31/12/2026'],
  ])('formatDate("%s") não desloca o fuso', (iso, esperado) => {
    expect(formatDate(iso)).toBe(esperado);
  });

  it('mantém ISO com hora e objeto Date pelo Intl', () => {
    expect(formatDate('2026-08-19T23:30:00')).toBe('19/08/2026');
    expect(formatDate(new Date(2026, 7, 19))).toBe('19/08/2026');
    expect(formatDateTime('2026-08-19T23:30:00')).toBe('19/08/2026, 23:30');
  });

  it('faz ida e volta sem perder o dia (default do input de meta)', () => {
    expect(formatDateOnlyBR('2026-08-19')).toBe('19/08/2026');
    expect(parseDateBR(formatDate('2026-08-19'))).toBe('2026-08-19');
  });
});
