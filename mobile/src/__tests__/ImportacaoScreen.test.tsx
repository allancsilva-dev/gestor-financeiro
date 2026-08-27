import React from 'react';
import { Alert } from 'react-native';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import * as DocumentPicker from 'expo-document-picker';
import ImportacaoScreen from '../../app/(app)/more/importacao';
import importacaoService from '../services/importacaoService';
import contaFinanceiraService from '../services/contaFinanceiraService';
import { ImportBatch, ImportRecord } from '../types';

jest.mock('@expo/vector-icons/Ionicons', () => 'Ionicons');

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 59, bottom: 34, left: 0, right: 0 }),
}));

jest.mock('expo-router', () => ({
  useRouter: () => ({ push: jest.fn(), back: jest.fn() }),
}));

jest.mock('expo-document-picker', () => ({ getDocumentAsync: jest.fn() }));

jest.mock('../services/importacaoService', () => ({
  __esModule: true,
  default: {
    enviar: jest.fn(),
    consultar: jest.fn(),
    historico: jest.fn(),
    registros: jest.fn(),
    inspecionar: jest.fn(),
    listarMapeamentos: jest.fn(),
    salvarMapeamento: jest.fn(),
    removerMapeamento: jest.fn(),
    preparar: jest.fn(),
    aprovar: jest.fn(),
    lancar: jest.fn(),
    reverter: jest.fn(),
  },
}));

jest.mock('../services/contaFinanceiraService', () => ({
  __esModule: true,
  default: { listarParaCaixa: jest.fn() },
}));

jest.mock('../services/cartaoService', () => ({
  __esModule: true,
  default: { listarTodos: jest.fn() },
}));

const servico = importacaoService as jest.Mocked<typeof importacaoService>;
const contas = contaFinanceiraService as unknown as { listarParaCaixa: jest.Mock };
const picker = DocumentPicker as unknown as { getDocumentAsync: jest.Mock };
const cartoes = jest.requireMock('../services/cartaoService').default as { listarTodos: jest.Mock };

const lote = (over: Partial<ImportBatch> = {}): ImportBatch => ({
  id: 10,
  status: 'PARSED',
  format: 'CSV',
  fileSha256: 'a'.repeat(64),
  totalRecords: 3,
  validRecords: 2,
  invalidRecords: 0,
  pendingReviewRecords: 1,
  duplicateRecords: 0,
  createdAt: '2026-08-27T10:00:00Z',
  updatedAt: '2026-08-27T10:00:00Z',
  ...over,
});

const registro = (over: Partial<ImportRecord> = {}): ImportRecord => ({
  id: 1,
  sourceLine: 2,
  occurredOn: '2026-08-20',
  description: 'Mercado Centro',
  amount: 12.34,
  currency: 'BRL',
  direction: 'SAIDA',
  status: 'VALID',
  ...over,
});

let client: QueryClient | null = null;

const renderizar = () => {
  client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  return render(
    <QueryClientProvider client={client}>
      <ImportacaoScreen />
    </QueryClientProvider>,
  );
};

const escolherArquivo = async () => {
  fireEvent.press(screen.getByTestId('importacao-escolher-arquivo'));
  await waitFor(() => expect(servico.enviar).toHaveBeenCalled());
};

beforeEach(() => {
  jest.clearAllMocks();
  contas.listarParaCaixa.mockResolvedValue([
    { id: 7, nome: 'Conta corrente', natureza: 'ATIVO', subtipo: 'CORRENTE', liquidez: 'IMEDIATA', moeda: 'BRL', saldo: 100, origemDados: 'MANUAL', estadoConciliacao: 'CONCILIADA' },
  ]);
  cartoes.listarTodos.mockResolvedValue([{ id: 4, nome: 'Nubank', limite: 1000, diaFechamento: 20, diaVencimento: 27 }]);
  servico.historico.mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, size: 5, number: 0 } as any);
  servico.registros.mockResolvedValue({ registros: [registro()], proximaLinha: null });
  servico.listarMapeamentos.mockResolvedValue([]);
  picker.getDocumentAsync.mockResolvedValue({
    canceled: false,
    assets: [{ uri: 'file:///extrato.csv', name: 'extrato.csv', mimeType: 'text/csv' }],
  });
});

afterEach(() => {
  // A tela acompanha o lançamento por polling; sem limpar o cache o timer do
  // react-query sobrevive ao teste e o worker do Jest não encerra.
  client?.clear();
  client = null;
});

describe('tela de importação', () => {
  it('envia o arquivo e mostra o que foi lido, sem lançar nada ainda', async () => {
    servico.enviar.mockResolvedValue(lote());
    servico.consultar.mockResolvedValue(lote());

    renderizar();
    await escolherArquivo();

    expect(await screen.findByText('3 linhas lidas')).toBeTruthy();
    expect(screen.getByText('Onde isto entra?')).toBeTruthy();
    expect(servico.lancar).not.toHaveBeenCalled();
  });

  it('só libera o lançamento depois de escolher a conta de destino', async () => {
    servico.enviar.mockResolvedValue(lote());
    servico.consultar.mockResolvedValue(lote());
    servico.preparar.mockResolvedValue(lote({ status: 'READY_TO_COMMIT' }));
    servico.lancar.mockResolvedValue(lote({ status: 'COMMITTING' }));

    renderizar();
    await escolherArquivo();

    const botaoLancar = await screen.findByTestId('importacao-lancar');
    expect(botaoLancar.props.accessibilityState?.disabled).toBe(true);

    fireEvent.press(await screen.findByText('Conta corrente'));
    await waitFor(() => expect(servico.preparar).toHaveBeenCalledWith(10, { contaFinanceiraId: 7 }));

    fireEvent.press(screen.getByTestId('importacao-lancar'));
    await waitFor(() => expect(servico.lancar).toHaveBeenCalledWith(10));
  });

  it('fatura de cartão é um destino possível, e exclui a conta de caixa', async () => {
    servico.enviar.mockResolvedValue(lote());
    servico.consultar.mockResolvedValue(lote());
    servico.preparar.mockResolvedValue(lote({ status: 'READY_TO_COMMIT' }));

    renderizar();
    await escolherArquivo();

    fireEvent.press(await screen.findByText('💳 Nubank'));

    await waitFor(() => expect(servico.preparar).toHaveBeenCalledWith(10, { cartaoId: 4 }));
  });

  it('arquivo não reconhecido oferece dizer quais são as colunas, e reenvia com o mapeamento', async () => {
    servico.enviar
      .mockRejectedValueOnce(Object.assign(new Error('Falha ao processar importação'), {
        userMessage: 'Falha ao processar importação',
        status: 422,
      }))
      .mockResolvedValueOnce(lote());
    servico.consultar.mockResolvedValue(lote());
    servico.inspecionar.mockResolvedValue({
      delimitador: ';',
      cabecalhos: ['Data Mov', 'Historico', 'Vlr (R$)'],
    });
    servico.salvarMapeamento.mockResolvedValue({
      id: 3, nome: 'Meu banco 1', delimitador: ';', colunas: {},
    });

    renderizar();
    await escolherArquivo();

    fireEvent.press(await screen.findByTestId('importacao-inspecionar'));

    // Data já vem em foco: escolher a coluna preenche o campo selecionado.
    fireEvent.press(await screen.findByText('Data Mov'));
    // Só com data o botão continua travado: sem valor não existe lançamento.
    expect(screen.getByTestId('importacao-salvar-mapeamento').props.accessibilityState?.disabled).toBe(true);

    fireEvent.press(screen.getByText('Valor'));
    fireEvent.press(screen.getByText('Vlr (R$)'));
    fireEvent.press(screen.getByTestId('importacao-salvar-mapeamento'));

    await waitFor(() => expect(servico.salvarMapeamento).toHaveBeenCalledWith(expect.objectContaining({
      delimitador: ';',
      colunas: { date: 'Data Mov', amount: 'Vlr (R$)' },
    })));
    await waitFor(() => expect(servico.enviar).toHaveBeenLastCalledWith(
      expect.objectContaining({ name: 'extrato.csv' }), undefined, 3));
  });

  it('linha em revisão traz o motivo e a ação de aprovar', async () => {
    servico.enviar.mockResolvedValue(lote());
    servico.consultar.mockResolvedValue(lote());
    servico.registros.mockResolvedValue({
      registros: [registro({ id: 4, status: 'PENDING_REVIEW', reasonCode: 'CURRENCY_MISSING' })],
      proximaLinha: null,
    });
    servico.aprovar.mockResolvedValue(registro({ id: 4, status: 'APPROVED' }));

    renderizar();
    await escolherArquivo();

    expect(await screen.findByText('sem moeda')).toBeTruthy();
    fireEvent.press(screen.getByText('Trazer mesmo assim'));
    await waitFor(() => expect(servico.aprovar).toHaveBeenCalledWith(10, 4));
  });

  it('erro do backend aparece na tela em vez de sumir', async () => {
    servico.enviar.mockRejectedValue(
      Object.assign(new Error('Muitas importações em sequência'), {
        userMessage: 'Muitas importações em sequência',
        status: 429,
      }),
    );

    renderizar();
    await escolherArquivo();

    expect(await screen.findByText('Muitas importações em sequência')).toBeTruthy();
  });

  it('lote lançado oferece desfazer, com confirmação', async () => {
    const alerta = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
    servico.enviar.mockResolvedValue(lote({ status: 'COMMITTED', pendingReviewRecords: 0, validRecords: 3 }));
    servico.consultar.mockResolvedValue(lote({ status: 'COMMITTED', pendingReviewRecords: 0, validRecords: 3 }));

    renderizar();
    await escolherArquivo();

    fireEvent.press(await screen.findByText('Desfazer importação'));
    expect(alerta).toHaveBeenCalled();
    expect(servico.reverter).not.toHaveBeenCalled();
    alerta.mockRestore();
  });
});
