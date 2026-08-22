import {
  competenciaIso,
  ehCompetenciaCorrente,
  intervaloDoMes,
  intervaloDoPeriodo,
  iso,
  rotuloDeCompetencia,
  somarMeses,
} from '../domain/periodo';

// Os testes rodam com TZ=America/Sao_Paulo (package.json). É de propósito: o
// módulo trabalha em hora local e `toISOString()` devolveria o dia anterior.
describe('iso', () => {
  it('usa os componentes locais, não UTC', () => {
    // 1º de março às 00h em São Paulo é 03h UTC do mesmo dia; às 23h é dia 2 UTC
    expect(iso(new Date(2026, 2, 1, 0, 0))).toBe('2026-03-01');
    expect(iso(new Date(2026, 2, 1, 23, 30))).toBe('2026-03-01');
  });

  it('zera à esquerda mês e dia', () => {
    expect(iso(new Date(2026, 0, 5))).toBe('2026-01-05');
  });
});

describe('intervaloDoMes', () => {
  it('vai do dia 1 ao último dia', () => {
    expect(intervaloDoMes(new Date(2026, 1, 14))).toEqual({ inicio: '2026-02-01', fim: '2026-02-28' });
  });

  it('acerta fevereiro de ano bissexto', () => {
    expect(intervaloDoMes(new Date(2028, 1, 3))).toEqual({ inicio: '2028-02-01', fim: '2028-02-29' });
  });
});

describe('somarMeses', () => {
  it('vira o ano para trás e para frente', () => {
    expect(iso(somarMeses(new Date(2026, 0, 20), -1))).toBe('2025-12-01');
    expect(iso(somarMeses(new Date(2026, 11, 20), 1))).toBe('2027-01-01');
  });

  it('ancora no dia 1, então não escorrega em mês curto', () => {
    // 31/01 + 1 mês pela aritmética ingênua daria 02/03; aqui dá 01/02
    expect(iso(somarMeses(new Date(2026, 0, 31), 1))).toBe('2026-02-01');
  });
});

describe('competência', () => {
  it('formata para o backend e para a tela', () => {
    expect(competenciaIso(3, 2026)).toBe('2026-03');
    expect(rotuloDeCompetencia(3, 2026)).toBe('Março de 2026');
  });

  it('reconhece a competência corrente', () => {
    const hoje = new Date(2026, 7, 21);
    expect(ehCompetenciaCorrente(8, 2026, hoje)).toBe(true);
    expect(ehCompetenciaCorrente(7, 2026, hoje)).toBe(false);
    expect(ehCompetenciaCorrente(8, 2025, hoje)).toBe(false);
  });
});

describe('intervaloDoPeriodo', () => {
  const hoje = new Date(2026, 7, 21); // 21/08/2026

  it('os períodos que incluem hoje terminam hoje, não no fim do mês', () => {
    expect(intervaloDoPeriodo('mes', hoje)).toEqual({ inicio: '2026-08-01', fim: '2026-08-21' });
    expect(intervaloDoPeriodo('tresMeses', hoje)).toEqual({ inicio: '2026-06-01', fim: '2026-08-21' });
    expect(intervaloDoPeriodo('ano', hoje)).toEqual({ inicio: '2026-01-01', fim: '2026-08-21' });
  });

  it('mês passado é fechado nas duas pontas', () => {
    expect(intervaloDoPeriodo('mesPassado', hoje)).toEqual({ inicio: '2026-07-01', fim: '2026-07-31' });
  });

  it('mês passado em janeiro cai no dezembro anterior', () => {
    expect(intervaloDoPeriodo('mesPassado', new Date(2026, 0, 10)))
      .toEqual({ inicio: '2025-12-01', fim: '2025-12-31' });
  });
});
