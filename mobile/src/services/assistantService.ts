import api from './api';
import { TipoTransacao } from '../types';

export type AssistantOutcome = 'COMPLETE' | 'NEEDS_ONE_FIELD' | 'NEEDS_FORM' | 'NOT_FINANCIAL';

export interface AssistantDraft {
  id: number;
  version: number;
  tipo: TipoTransacao | null;
  valor: number | null;
  descricao: string | null;
  data: string | null;
  carteiraId: number | null;
  categoriaId: number | null;
  missingFields: string[];
  expiresAt: string;
}

export interface AssistantMessageResponse {
  conversationId: number;
  outcome: AssistantOutcome;
  reply: string;
  draft: AssistantDraft | null;
}

export interface AssistantAudioResponse {
  transcript: string;
  message: AssistantMessageResponse;
}
export interface WhatsappLinkResponse { code: string; expiresAt: string }

export interface AssistantDraftPatch {
  version: number;
  tipo: TipoTransacao;
  valor: number;
  descricao: string;
  data: string;
  carteiraId: number;
  categoriaId: number;
}

export const assistantIdempotencyKey = (scope: string) =>
  `assistant:${scope}:${Date.now()}:${Math.random().toString(36).slice(2, 10)}`;

const assistantService = {
  sendMessage: (text: string, conversationId?: number | null, key = assistantIdempotencyKey('message')) =>
    api.post<AssistantMessageResponse>('/v1/assistant/messages', { text, conversationId: conversationId ?? null }, {
      headers: { 'Idempotency-Key': key },
    }).then(response => response.data),

  patchDraft: (draftId: number, data: AssistantDraftPatch, key = assistantIdempotencyKey(`draft:${draftId}`)) =>
    api.patch<AssistantDraft>(`/v1/assistant/drafts/${draftId}`, data, {
      headers: { 'Idempotency-Key': key },
    }).then(response => response.data),

  confirmDraft: (draftId: number, version: number, key = assistantIdempotencyKey(`confirm:${draftId}`)) =>
    api.post(`/v1/assistant/drafts/${draftId}/confirm`, { version }, {
      headers: { 'Idempotency-Key': key },
    }).then(response => response.data),

  cancelDraft: (draftId: number, key = assistantIdempotencyKey(`cancel:${draftId}`)) =>
    api.post(`/v1/assistant/drafts/${draftId}/cancel`, null, {
      headers: { 'Idempotency-Key': key },
    }),

  transcribeAudio: async (uri: string, conversationId?: number | null, key = assistantIdempotencyKey('audio')) => {
    const attempt = () => {
      const data = new FormData();
      data.append('audio', { uri, name: 'lancamento.m4a', type: 'audio/mp4' } as unknown as Blob);
      if (conversationId != null) data.append('conversationId', String(conversationId));
      return api.post<AssistantAudioResponse>('/v1/assistant/audio', data, {
        headers: { 'Content-Type': 'multipart/form-data', 'Idempotency-Key': key }, timeout: 50_000,
      });
    };
    try { return (await attempt()).data; }
    catch (error: any) {
      const status = error?.response?.status;
      if (status !== undefined && status !== 429 && status < 500) throw error;
      return (await attempt()).data;
    }
  },

  createWhatsappLink: (key = assistantIdempotencyKey('whatsapp-link')) =>
    api.post<WhatsappLinkResponse>('/v1/assistant/whatsapp/link', null, {
      headers: { 'Idempotency-Key': key },
    }).then(response => response.data),
};

export default assistantService;
