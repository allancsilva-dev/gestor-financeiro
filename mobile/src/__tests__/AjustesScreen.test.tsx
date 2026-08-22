import React from 'react';
import { Alert } from 'react-native';
import { act, render, screen, waitFor, fireEvent } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import Ajustes from '../../app/(app)/ajustes';
import notificacaoService from '../services/notificacaoService';
import usuarioService from '../services/usuarioService';

// jest-expo não resolve o font loader nativo dos vector-icons.
jest.mock('@expo/vector-icons/Ionicons', () => 'Ionicons');

// Insets fixos: o SafeAreaProvider real fica esperando onLayout, que nunca
// chega no react-test-renderer.
jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 59, bottom: 34, left: 0, right: 0 }),
}));

const mockPush = jest.fn();
const mockReplace = jest.fn();
jest.mock('expo-router', () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace }),
}));

const mockLogout = jest.fn().mockResolvedValue(undefined);
jest.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    usuario: { id: 1, nome: 'Allan Carvalho', email: 'allan@example.com', onboardingCompleto: true },
    logout: mockLogout,
  }),
}));

const mockSetPreferencia = jest.fn().mockResolvedValue(undefined);
const mockTema = { preferencia: 'sistema' };
jest.mock('../context/TemaContext', () => ({
  useTema: () => ({ preferencia: mockTema.preferencia, esquema: 'dark', setPreferencia: mockSetPreferencia }),
  useTemaOpcional: () => undefined,
}));

jest.mock('../services/notificacaoService', () => ({
  __esModule: true,
  default: { contarNaoLidas: jest.fn() },
}));

jest.mock('../services/usuarioService', () => ({
  __esModule: true,
  default: { excluirConta: jest.fn() },
}));

jest.mock('../services/importService', () => ({ __esModule: true, default: { csv: jest.fn() } }));
jest.mock('../services/api', () => ({ __esModule: true, default: { get: jest.fn() } }));
jest.mock('expo-sharing', () => ({ isAvailableAsync: jest.fn(), shareAsync: jest.fn() }));
jest.mock('expo-document-picker', () => ({ getDocumentAsync: jest.fn() }));
jest.mock('expo-file-system', () => ({ File: jest.fn(), Paths: { cache: '/cache' } }));

let client: QueryClient;

afterEach(() => {
  client?.clear();
  client?.unmount();
});

// A contagem de não lidas assenta num macrotask (o notifyManager do react-query
// agenda em setTimeout). Sem escoar aqui, o estado cai fora de `act` e vaza warning.
const renderizar = async () => {
  client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  const arvore = render(
    <QueryClientProvider client={client}>
      <Ajustes />
    </QueryClientProvider>,
  );
  await act(async () => { await new Promise(resolve => setTimeout(resolve, 0)); });
  return arvore;
};

/** Dispara o botão do Alert cujo texto casa com `rotulo`. */
const tocarNoAlerta = async (rotulo: string) => {
  const botoes = (Alert.alert as jest.Mock).mock.calls.at(-1)?.[2] ?? [];
  const botao = botoes.find((b: { text: string }) => b.text === rotulo);
  expect(botao).toBeTruthy();
  await act(async () => { await botao.onPress(); });
};

describe('AjustesScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockTema.preferencia = 'sistema';
    jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
    (notificacaoService.contarNaoLidas as jest.Mock).mockResolvedValue(0);
  });

  it('abre em Ajustes, não no antigo hub "Mais"', async () => {
    await renderizar();
    await waitFor(() => expect(screen.getByText('Ajustes')).toBeTruthy());
    expect(screen.queryByText('Mais')).toBeNull();
  });

  it('mostra as seções e a conta do usuário', async () => {
    await renderizar();
    await waitFor(() => expect(screen.getByText('Allan Carvalho')).toBeTruthy());
    expect(screen.getByText('allan@example.com')).toBeTruthy();
    expect(screen.getByText('APARÊNCIA')).toBeTruthy();
    expect(screen.getByText('FERRAMENTAS')).toBeTruthy();
    expect(screen.getByText('DADOS E PRIVACIDADE')).toBeTruthy();
  });

  it('leva o badge de não lidas para a tela de notificações', async () => {
    (notificacaoService.contarNaoLidas as jest.Mock).mockResolvedValue(3);
    await renderizar();
    await waitFor(() => expect(screen.getByText('3')).toBeTruthy());
    fireEvent.press(screen.getByLabelText('Notificações, 3 não lidas'));
    expect(mockPush).toHaveBeenCalledWith('/(app)/notificacoes');
  });

  it('mantém as rotas das ferramentas tocadas pelo E2E', async () => {
    await renderizar();
    await waitFor(() => expect(screen.getByText('Categorias')).toBeTruthy());

    for (const [rotulo, rota] of [
      ['Categorias', '/more/categorias'],
      ['Carteira', '/more/faturas'],
      ['Contas', '/more/carteiras'],
      ['Relatórios', '/analises'],
    ] as const) {
      mockPush.mockClear();
      fireEvent.press(screen.getByLabelText(rotulo));
      expect(mockPush).toHaveBeenCalledWith(rota);
    }
  });

  it('não navega no item marcado como "Em breve"', async () => {
    await renderizar();
    await waitFor(() => expect(screen.getByText('Em breve')).toBeTruthy());
    fireEvent.press(screen.getByLabelText('Entrada por IA (em breve)'));
    expect(mockPush).not.toHaveBeenCalled();
  });

  it('dá acesso à política de privacidade a quem já tem conta', async () => {
    await renderizar();
    await waitFor(() => expect(screen.getByText('Política de privacidade')).toBeTruthy());
    // O texto visível é o rótulo: a linha não carrega mais `accessibilityLabel`
    fireEvent.press(screen.getByText('Política de privacidade'));
    expect(mockPush).toHaveBeenCalledWith('/(auth)/privacidade');
  });

  it('persiste o tema escolhido', async () => {
    await renderizar();
    await waitFor(() => expect(screen.getByText('Escuro')).toBeTruthy());
    fireEvent.press(screen.getByText('Escuro'));
    expect(mockSetPreferencia).toHaveBeenCalledWith('escuro');
  });

  it('confirma antes de sair e derruba a sessão', async () => {
    await renderizar();
    await waitFor(() => expect(screen.getByText('Sair da conta')).toBeTruthy());
    fireEvent.press(screen.getByLabelText('Sair da conta'));

    expect(Alert.alert).toHaveBeenCalledWith('Sair da conta?', expect.any(String), expect.any(Array));
    await tocarNoAlerta('Sair');
    expect(mockLogout).toHaveBeenCalled();
    expect(mockReplace).toHaveBeenCalledWith('/(auth)/login');
  });

  it('exclui a conta só depois do aviso e da senha', async () => {
    (usuarioService.excluirConta as jest.Mock).mockResolvedValue(undefined);
    await renderizar();
    await waitFor(() => expect(screen.getByText('Excluir minha conta')).toBeTruthy());

    fireEvent.press(screen.getByLabelText('Excluir minha conta'));
    expect(Alert.alert).toHaveBeenCalledWith('Excluir minha conta?', expect.any(String), expect.any(Array));
    expect(usuarioService.excluirConta).not.toHaveBeenCalled();

    await tocarNoAlerta('Continuar');
    fireEvent.changeText(await screen.findByLabelText('Senha atual'), 'SenhaF0rte!23');
    fireEvent.press(screen.getByLabelText('Excluir minha conta definitivamente'));

    await waitFor(() => expect(usuarioService.excluirConta).toHaveBeenCalledWith('SenhaF0rte!23'));
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/(auth)/login'));
    expect(mockLogout).toHaveBeenCalled();
  });

  it('mostra o erro do backend e mantém a sessão quando a senha está errada', async () => {
    // Formato real do backend depois da correção de PROB-0083: o interceptor de
    // `services/api` promove a `message` de BusinessException a `userMessage`
    // (antes virava "Dados inválidos" e a tela precisava de contorno próprio).
    (usuarioService.excluirConta as jest.Mock).mockRejectedValue({
      userMessage: 'Senha incorreta',
      status: 422,
      codigo: 'BUSINESS_ERROR',
      response: { status: 422, data: { code: 'BUSINESS_ERROR', message: 'Senha incorreta' } },
    });
    await renderizar();
    await waitFor(() => expect(screen.getByText('Excluir minha conta')).toBeTruthy());

    fireEvent.press(screen.getByLabelText('Excluir minha conta'));
    await tocarNoAlerta('Continuar');
    fireEvent.changeText(await screen.findByLabelText('Senha atual'), 'errada');
    fireEvent.press(screen.getByLabelText('Excluir minha conta definitivamente'));

    await waitFor(() => expect(screen.getByText('Senha incorreta')).toBeTruthy());
    expect(mockLogout).not.toHaveBeenCalled();
    expect(mockReplace).not.toHaveBeenCalled();
  });
});
