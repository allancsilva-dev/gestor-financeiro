import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ContasFinanceiras from './Carteira';
import contaFinanceiraService from '../services/contaFinanceiraService';

vi.mock('../components/Layout', () => ({ default: ({ children }: { children: React.ReactNode }) => <>{children}</> }));
vi.mock('../services/contaFinanceiraService', async importOriginal => {
  const original = await importOriginal<typeof import('../services/contaFinanceiraService')>();
  return { ...original, default: { listarTodas: vi.fn(), listarMovimentos: vi.fn(), ajustar: vi.fn(), criar: vi.fn(), atualizar: vi.fn(), deletar: vi.fn() } };
});

describe('Contas financeiras', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(contaFinanceiraService.listarTodas).mockResolvedValue([
      { id: 1, nome: 'Conta corrente', tipo: 'CONTA_BANCARIA', saldo: 100, natureza: 'ATIVO', subtipo: 'CORRENTE', liquidez: 'IMEDIATA', origemDados: 'MANUAL', estadoConciliacao: 'CONCILIADA', moeda: 'BRL' },
      { id: 2, nome: 'Cartão principal', tipo: 'CARTAO', saldo: 250, natureza: 'PASSIVO', subtipo: 'CARTAO', liquidez: 'BLOQUEADA', origemDados: 'INTEGRACAO', estadoConciliacao: 'PENDENTE', moeda: 'BRL' },
    ]);
  });

  it('agrupa ATIVO/PASSIVO, exibe badges e restringe ações gerenciadas', async () => {
    render(<ContasFinanceiras />);
    expect(await screen.findByRole('heading', { name: 'Ativos' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Passivos' })).toBeInTheDocument();
    expect(screen.getByText('Somente leitura')).toBeInTheDocument();
    expect(screen.getByText('Pendente')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Editar Cartão principal' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Editar Conta corrente' })).toBeInTheDocument();
  });
});
