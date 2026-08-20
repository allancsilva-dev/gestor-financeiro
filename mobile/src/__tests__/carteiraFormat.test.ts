import { posicaoDaFatura, competenciaCurta, prazoEmDias, dataCurta, dataLonga } from '../domain/carteiraFormat';

describe('posicaoDaFatura', () => {
  it('classifica a competência contra o mês corrente', () => {
    expect(posicaoDaFatura({ mes: 8, ano: 2026 }, 8, 2026)).toBe('atual');
    expect(posicaoDaFatura({ mes: 9, ano: 2026 }, 8, 2026)).toBe('proxima');
    expect(posicaoDaFatura({ mes: 7, ano: 2026 }, 8, 2026)).toBe('anterior');
    expect(posicaoDaFatura({ mes: 11, ano: 2026 }, 8, 2026)).toBe('futura');
  });

  it('atravessa a virada de ano sem se perder', () => {
    // dezembro -> janeiro é "próxima", não "anterior"
    expect(posicaoDaFatura({ mes: 1, ano: 2027 }, 12, 2026)).toBe('proxima');
    expect(posicaoDaFatura({ mes: 12, ano: 2026 }, 1, 2027)).toBe('anterior');
  });
});

describe('competenciaCurta', () => {
  it('formata como no mockup', () => {
    expect(competenciaCurta(8, 2026)).toBe('Ago/26');
    expect(competenciaCurta(7, 2026)).toBe('Jul/26');
    expect(competenciaCurta(1, 2027)).toBe('Jan/27');
  });
});

describe('prazoEmDias', () => {
  it('usa linguagem humana perto de hoje', () => {
    expect(prazoEmDias(0)).toBe('hoje');
    expect(prazoEmDias(1)).toBe('amanhã');
    expect(prazoEmDias(3)).toBe('em 3d');
    expect(prazoEmDias(8)).toBe('em 8d');
  });

  it('vencido conta para trás, sem sinal negativo solto', () => {
    expect(prazoEmDias(-2)).toBe('há 2d');
  });
});
