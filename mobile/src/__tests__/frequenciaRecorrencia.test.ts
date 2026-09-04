import {
  FREQUENCIAS,
  isSubMensal,
  nomeFrequencia,
  proximaCobranca,
  rotuloCadencia,
  usaAncora,
} from '../domain/recorrencia';

/**
 * Espelho de exibição do CalendarioRecorrencia.java (V72). Os casos aqui são os mesmos
 * do CalendarioRecorrenciaTest do backend — se as duas pontas divergirem, o app mente
 * sobre uma data que o backend vai calcular de outro jeito.
 */
describe('frequência de recorrência — paridade com o backend', () => {
  it('cobre as sete frequências', () => {
    expect(FREQUENCIAS).toEqual([
      'SEMANAL', 'QUINZENAL', 'MENSAL', 'BIMESTRAL', 'TRIMESTRAL', 'SEMESTRAL', 'ANUAL',
    ]);
  });

  it('só semanal e quinzenal são sub-mensais', () => {
    expect(isSubMensal('SEMANAL')).toBe(true);
    expect(isSubMensal('QUINZENAL')).toBe(true);
    expect(isSubMensal('MENSAL')).toBe(false);
    expect(isSubMensal('ANUAL')).toBe(false);
  });

  /**
   * O clamp reclampa a partir do diaVencimento, não do dia da data atual: sem isso a
   * série derivaria para o dia 28 depois de passar por fevereiro.
   */
  it('dia 31 vira 28 em fevereiro e volta a 31 em março', () => {
    expect(proximaCobranca(31, new Date(2026, 1, 1), 'MENSAL'))
      .toEqual(new Date(2026, 1, 28));
    expect(proximaCobranca(31, new Date(2026, 2, 1), 'MENSAL'))
      .toEqual(new Date(2026, 2, 31));
  });

  it('mensal cai no mês seguinte quando o dia já passou', () => {
    expect(proximaCobranca(10, new Date(2026, 8, 20), 'MENSAL'))
      .toEqual(new Date(2026, 9, 10));
  });

  it('vencer hoje é vencer hoje', () => {
    expect(proximaCobranca(15, new Date(2026, 8, 15), 'MENSAL'))
      .toEqual(new Date(2026, 8, 15));
  });

  it('anual pula doze meses e vira o ano', () => {
    expect(proximaCobranca(10, new Date(2026, 8, 20), 'ANUAL'))
      .toEqual(new Date(2027, 8, 10));
  });

  it('bimestral pula dois meses', () => {
    expect(proximaCobranca(10, new Date(2026, 8, 20), 'BIMESTRAL'))
      .toEqual(new Date(2026, 10, 10));
  });

  it('semanal caminha a partir da âncora preservando o dia da semana', () => {
    const ancora = new Date(2026, 8, 1); // terça
    expect(ancora.getDay()).toBe(2);

    const proxima = proximaCobranca(1, new Date(2026, 8, 20), 'SEMANAL', ancora);

    expect(proxima).toEqual(new Date(2026, 8, 22));
    expect(proxima.getDay()).toBe(2);
  });

  it('quinzenal anda de catorze em catorze dias', () => {
    expect(proximaCobranca(1, new Date(2026, 8, 2), 'QUINZENAL', new Date(2026, 8, 1)))
      .toEqual(new Date(2026, 8, 15));
  });

  it('âncora no futuro é a própria primeira cobrança', () => {
    expect(proximaCobranca(5, new Date(2026, 8, 20), 'QUINZENAL', new Date(2026, 9, 5)))
      .toEqual(new Date(2026, 9, 5));
  });

  it('só MENSAL dispensa âncora', () => {
    expect(usaAncora('MENSAL')).toBe(false);
    expect(usaAncora('ANUAL')).toBe(true);
    expect(usaAncora('BIMESTRAL')).toBe(true);
    expect(usaAncora('SEMANAL')).toBe(true);
  });

  // V73: a série sai da âncora e não de "hoje". É o que deixa o dono escolher o mês da
  // anual — e o que impede que editar o valor em setembro mova o aniversário de março.
  it('anual com âncora fica no mês do aniversário', () => {
    expect(proximaCobranca(15, new Date(2026, 8, 1), 'ANUAL', new Date(2026, 2, 15)))
      .toEqual(new Date(2027, 2, 15));
  });

  it('bimestral com âncora preserva a fase, não recomeça em hoje', () => {
    // Âncora em janeiro: a série é jan, mar, mai, jul, set — nunca outubro.
    expect(proximaCobranca(10, new Date(2026, 8, 20), 'BIMESTRAL', new Date(2026, 0, 10)))
      .toEqual(new Date(2026, 10, 10));
  });

  it('âncora mensal-múltipla no futuro é a própria primeira cobrança', () => {
    expect(proximaCobranca(15, new Date(2026, 8, 1), 'ANUAL', new Date(2027, 2, 15)))
      .toEqual(new Date(2027, 2, 15));
  });

  it('âncora em dia 31 encurta em mês curto', () => {
    expect(proximaCobranca(31, new Date(2026, 1, 1), 'BIMESTRAL', new Date(2025, 11, 31)))
      .toEqual(new Date(2026, 1, 28));
  });

  it('sem frequência informada, comporta-se como mensal', () => {
    expect(proximaCobranca(10, new Date(2026, 8, 20)))
      .toEqual(proximaCobranca(10, new Date(2026, 8, 20), 'MENSAL'));
  });
});

/** "Todo dia 10" mentiria numa recorrência semanal ou anual. */
describe('rótulo de cadência', () => {
  it('mensal diz o dia', () => {
    expect(rotuloCadencia('MENSAL', 10)).toBe('Todo dia 10');
  });

  it('semanal diz o dia da semana da âncora', () => {
    expect(rotuloCadencia('SEMANAL', 1, new Date(2026, 8, 1))).toBe('Toda terça');
  });

  it('quinzenal não finge ter dia do mês', () => {
    expect(rotuloCadencia('QUINZENAL', 1)).toBe('A cada 14 dias');
  });

  it('anual diz o mês quando tem âncora', () => {
    expect(rotuloCadencia('ANUAL', 10, new Date(2026, 8, 10)))
      .toBe('Todo dia 10 de setembro');
  });

  it('trimestral com âncora diz o mês de partida', () => {
    // "A cada 3 meses, dia 15" não diz se cai em março ou em abril.
    expect(rotuloCadencia('TRIMESTRAL', 15, new Date(2026, 2, 15)))
      .toBe('A cada 3 meses, dia 15, a partir de março');
  });

  it('sem âncora, o rótulo não inventa mês', () => {
    expect(rotuloCadencia('TRIMESTRAL', 15)).toBe('A cada 3 meses, dia 15');
  });

  it('todas as frequências têm nome e rótulo', () => {
    FREQUENCIAS.forEach(f => {
      expect(nomeFrequencia(f)).toBeTruthy();
      expect(rotuloCadencia(f, 10)).toBeTruthy();
    });
  });
});
