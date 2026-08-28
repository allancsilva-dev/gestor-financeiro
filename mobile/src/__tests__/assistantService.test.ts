jest.mock('../services/api', () => ({
  __esModule: true,
  default: { post: jest.fn(), patch: jest.fn() },
}));

import api from '../services/api';
import assistantService from '../services/assistantService';

describe('assistantService áudio', () => {
  beforeEach(() => jest.clearAllMocks());

  it('repete uma falha de rede com a mesma Idempotency-Key', async () => {
    (api.post as jest.Mock)
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ data: { transcript: 'mercado 50', message: null } });

    await expect(assistantService.transcribeAudio('file:///voice.m4a', 9, 'audio-key-fixed'))
      .resolves.toMatchObject({ transcript: 'mercado 50' });

    expect(api.post).toHaveBeenCalledTimes(2);
    expect((api.post as jest.Mock).mock.calls[0][2].headers['Idempotency-Key']).toBe('audio-key-fixed');
    expect((api.post as jest.Mock).mock.calls[1][2].headers['Idempotency-Key']).toBe('audio-key-fixed');
  });

  it('não repete erro de validação', async () => {
    (api.post as jest.Mock).mockRejectedValue({ response: { status: 400 } });

    await expect(assistantService.transcribeAudio('file:///fake.m4a', null, 'audio-invalid'))
      .rejects.toMatchObject({ response: { status: 400 } });
    expect(api.post).toHaveBeenCalledTimes(1);
  });
});
