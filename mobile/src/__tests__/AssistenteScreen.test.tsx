import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import AssistenteScreen from '../../app/(app)/more/assistente';
import assistantService from '../services/assistantService';

const mockRecorder = {
  isRecording: false, uri: 'file:///voice.m4a', prepareToRecordAsync: jest.fn(), record: jest.fn(), stop: jest.fn(),
};
const mockRecordingState = { isRecording: false, durationMillis: 0 };
const mockDeleteAudio = jest.fn();

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 59, bottom: 34, left: 0, right: 0 }),
}));
jest.mock('expo-router', () => ({ useRouter: () => ({ replace: jest.fn(), back: jest.fn() }) }));
jest.mock('@expo/vector-icons/Ionicons', () => 'Ionicons');
jest.mock('expo-audio', () => ({
  AudioModule: { requestRecordingPermissionsAsync: jest.fn().mockResolvedValue({ granted: true }) },
  RecordingPresets: { HIGH_QUALITY: {} }, setAudioModeAsync: jest.fn(),
  useAudioRecorder: () => mockRecorder, useAudioRecorderState: () => mockRecordingState,
}), { virtual: true });
jest.mock('expo-file-system', () => ({
  File: jest.fn().mockImplementation(() => ({ exists: true, delete: mockDeleteAudio })),
}));
jest.mock('../services/assistantService', () => ({
  __esModule: true,
  assistantIdempotencyKey: jest.fn(() => 'assistant:message:test-key'),
  default: { sendMessage: jest.fn(), transcribeAudio: jest.fn(), createWhatsappLink: jest.fn(), patchDraft: jest.fn(), confirmDraft: jest.fn() },
}));
jest.mock('../components/NovaTransacaoModal', () => ({
  __esModule: true,
  default: ({ visible, initialData }: { visible: boolean; initialData?: { descricao?: string } }) => {
    const { Text: MockText } = require('react-native');
    return visible ? <MockText>Revisão aberta: {initialData?.descricao}</MockText> : null;
  },
}));

describe('AssistenteScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('explica que nada é lançado automaticamente', () => {
    render(<AssistenteScreen />);
    expect(screen.getByText(/Você sempre revisa antes de salvar/)).toBeTruthy();
    expect(screen.getByText(/Nada é lançado sem sua confirmação/)).toBeTruthy();
  });

  it('envia texto e abre o formulário existente para rascunho completo', async () => {
    (assistantService.sendMessage as jest.Mock).mockResolvedValue({
      conversationId: 9,
      outcome: 'COMPLETE',
      reply: 'Rascunho pronto. Revise os dados antes de confirmar.',
      draft: {
        id: 12, version: 0, tipo: 'SAIDA', valor: 85, descricao: 'Gasolina', data: '2026-08-27',
        carteiraId: 3, categoriaId: 4, missingFields: [], expiresAt: '2026-08-28T12:00:00',
      },
    });
    render(<AssistenteScreen />);
    fireEvent.changeText(screen.getByLabelText('Mensagem'), 'gasolina 85 no Nubank hoje');
    await act(async () => fireEvent.press(screen.getByLabelText('Enviar')));

    await waitFor(() => expect(assistantService.sendMessage).toHaveBeenCalledWith(
      'gasolina 85 no Nubank hoje', null, expect.stringMatching(/^assistant:message:/)));
    expect(screen.getByText('Rascunho pronto. Revise os dados antes de confirmar.')).toBeTruthy();
    expect(screen.getByText('Revisão aberta: Gasolina')).toBeTruthy();
  });

  it('preserva a mensagem quando a rede falha', async () => {
    (assistantService.sendMessage as jest.Mock).mockRejectedValue({ userMessage: 'Sem conexão. Verifique sua internet.' });
    render(<AssistenteScreen />);
    fireEvent.changeText(screen.getByLabelText('Mensagem'), 'mercado 50 ontem');
    await act(async () => fireEvent.press(screen.getByLabelText('Enviar')));

    await waitFor(() => expect(screen.getByText('Sem conexão. Verifique sua internet.')).toBeTruthy());
    expect(screen.getByDisplayValue('mercado 50 ontem')).toBeTruthy();
  });

  it('reutiliza a chave idempotente ao reenviar a mesma mensagem após falha', async () => {
    (assistantService.sendMessage as jest.Mock)
      .mockRejectedValueOnce({ userMessage: 'Sem conexão. Verifique sua internet.' })
      .mockResolvedValueOnce({
        conversationId: 9,
        outcome: 'COMPLETE',
        reply: 'Rascunho pronto. Revise os dados antes de confirmar.',
        draft: { id: 12, version: 0, tipo: 'SAIDA', valor: 50, descricao: 'Mercado', data: '2026-08-28',
          carteiraId: 3, categoriaId: 4, missingFields: [], expiresAt: '2026-08-29T12:00:00' },
      });
    render(<AssistenteScreen />);
    fireEvent.changeText(screen.getByLabelText('Mensagem'), 'mercado 50 hoje');
    await act(async () => fireEvent.press(screen.getByLabelText('Enviar')));
    await waitFor(() => expect(screen.getByDisplayValue('mercado 50 hoje')).toBeTruthy());
    await act(async () => fireEvent.press(screen.getByLabelText('Enviar')));

    await waitFor(() => expect(assistantService.sendMessage).toHaveBeenCalledTimes(2));
    expect((assistantService.sendMessage as jest.Mock).mock.calls[0][2])
      .toBe((assistantService.sendMessage as jest.Mock).mock.calls[1][2]);
  });

  it('exibe o transcript antes de abrir a revisão do mesmo formulário', async () => {
    mockRecordingState.isRecording = true;
    (assistantService.transcribeAudio as jest.Mock).mockResolvedValue({
      transcript: 'gasolina 85 hoje',
      message: {
        conversationId: 9, outcome: 'COMPLETE', reply: 'Rascunho pronto. Revise os dados antes de confirmar.',
        draft: { id: 12, version: 0, tipo: 'SAIDA', valor: 85, descricao: 'Gasolina', data: '2026-08-27',
          carteiraId: 3, categoriaId: 4, missingFields: [], expiresAt: '2026-08-28T12:00:00' },
      },
    });
    render(<AssistenteScreen />);
    await act(async () => fireEvent.press(screen.getByText(/Parar gravação/)));

    await waitFor(() => expect(assistantService.transcribeAudio).toHaveBeenCalledWith('file:///voice.m4a', null));
    expect(screen.getByText('gasolina 85 hoje')).toBeTruthy();
    expect(screen.getByText('Revisão aberta: Gasolina')).toBeTruthy();
    expect(mockDeleteAudio).toHaveBeenCalledTimes(1);
    mockRecordingState.isRecording = false;
  });

  it('mostra código de vínculo WhatsApp sem pedir telefone no app', async () => {
    process.env.EXPO_PUBLIC_ASSISTANT_WHATSAPP_ENABLED = 'true';
    (assistantService.createWhatsappLink as jest.Mock).mockResolvedValue({ code: 'ABCDEFGH2345', expiresAt: '2026-08-27T20:30:00' });
    render(<AssistenteScreen />);
    await act(async () => fireEvent.press(screen.getByText('Conectar WhatsApp')));
    expect(await screen.findByText('ABCDEFGH2345')).toBeTruthy();
    expect(screen.getByText(/Uso único, válido por 10 minutos/)).toBeTruthy();
    delete process.env.EXPO_PUBLIC_ASSISTANT_WHATSAPP_ENABLED;
  });
});
