import { aporteMensalNecessario, previsaoDeConclusao, rotuloDeRitmo } from '../domain/metaProjecao';
import { Meta, StatusMeta } from '../types';
import { formatCurrency } from '../utils/format';

const HOJE = new Date(2026, 7, 20, 12, 0, 0); // 20/08/2026

const meta = (extras: Partial<Meta> = {}, status: StatusMeta = 'ATIVA'): Meta => ({
  id: 1, nome: 'Viagem ao Japão', valorTotal: 15000, valorReservado: 9750, status,
  ativa: status === 'ATIVA', modalidade: 'RESERVA_VIRTUAL', ...extras,
});

describe('aporte mensal necessário', () => {
  it('divide o que falta pelos meses até o prazo', () => {
    // faltam 5.250 em 7 meses (ago/2026 → mar/2027)
    expect(aporteMensalNecessario(meta({ dataPrevista: '2027-03-20' }), HOJE)).toBeCloseTo(750, 2);
  });

  it('não projeta sem prazo', () => {
    expect(aporteMensalNecessario(meta(), HOJE)).toBeNull();
  });

  it('não projeta meta já cumprida', () => {
    expect(aporteMensalNecessario(meta({ valorReservado: 15000, dataPrevista: '2027-03-20' }), HOJE)).toBeNull();
  });

  it('cobra tudo de uma vez com prazo vencido', () => {
    expect(aporteMensalNecessario(meta({ dataPrevista: '2026-01-10' }), HOJE)).toBeCloseTo(5250, 2);
  });
});

describe('previsão de conclusão', () => {
  it('conta os meses no ritmo declarado', () => {
    const p = previsaoDeConclusao(meta({ valorTotal: 20000, valorReservado: 16400, valorMensal: 225 }), HOJE);
    expect(p).not.toBeNull();
    expect(p!.meses).toBe(16); // 3.600 / 225
    expect(p!.dataAlvo.getFullYear()).toBe(2027);
    expect(p!.dataAlvo.getMonth()).toBe(11); // dezembro
  });

  it('não projeta sem aporte declarado', () => {
    expect(previsaoDeConclusao(meta(), HOJE)).toBeNull();
  });

  it('arredonda o mês quebrado para cima', () => {
    expect(previsaoDeConclusao(meta({ valorMensal: 2000 }), HOJE)!.meses).toBe(3); // 5.250 / 2.000
  });
});

describe('rótulo de ritmo', () => {
  it('cobra o aporte que o prazo exige quando o ritmo declarado não dá conta', () => {
    const r = rotuloDeRitmo(meta({ dataPrevista: '2027-03-20', valorMensal: 300 }), HOJE);
    expect(r).toEqual({ texto: `Precisa de ${formatCurrency(750)}/mês`, tom: 'atencao' });
  });

  it('mostra a data alvo quando o ritmo declarado fecha no prazo', () => {
    const r = rotuloDeRitmo(
      meta({ valorTotal: 20000, valorReservado: 16400, valorMensal: 1000, dataPrevista: '2028-01-10' }),
      HOJE,
    );
    expect(r).toEqual({ texto: 'Atingindo em 4m · dezembro de 2026', tom: 'noTrilho' });
  });

  it('projeta pelo aporte quando não há prazo', () => {
    const r = rotuloDeRitmo(meta({ valorMensal: 1000 }), HOJE);
    expect(r?.tom).toBe('noTrilho');
    expect(r?.texto).toMatch(/^Atingindo em 6m · /);
  });

  it('cala sem prazo e sem aporte', () => {
    expect(rotuloDeRitmo(meta(), HOJE)).toBeNull();
  });

  it('cala na meta concluída', () => {
    expect(rotuloDeRitmo(meta({ dataPrevista: '2027-03-20' }, 'CONCLUIDA'), HOJE)).toBeNull();
  });

  it('cala quando a meta já bateu o total', () => {
    expect(rotuloDeRitmo(meta({ valorReservado: 15000, dataPrevista: '2027-03-20' }), HOJE)).toBeNull();
  });
});
