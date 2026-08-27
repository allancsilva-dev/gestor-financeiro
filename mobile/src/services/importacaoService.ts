import api from './api';
import { ImportBatch, ImportRecord, ImportRecordPage, ImportRecordStatus, PagedResponse } from '../types';
import { UploadFile } from './anexoService';

const baseUrl = '/v1/importacoes';

/**
 * Pipeline canônico: o arquivo vira lote em revisão e **nada** entra no extrato antes de o
 * usuário confirmar. O endpoint legado (`/v1/importar/csv`) gravava direto e está desligado.
 */
const importacaoService = {
  enviar: (file: UploadFile, idempotencyKey?: string) => {
    const form = new FormData();
    form.append('file', { uri: file.uri, name: file.name, type: file.type } as any);
    return api
      .post<ImportBatch>(baseUrl, form, {
        headers: {
          'Content-Type': 'multipart/form-data',
          ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
        },
      })
      .then(r => r.data);
  },

  consultar: (id: number) => api.get<ImportBatch>(`${baseUrl}/${id}`).then(r => r.data),

  historico: (page = 0, size = 20) =>
    api.get<PagedResponse<ImportBatch>>(baseUrl, { params: { page, size } }).then(r => r.data),

  registros: (id: number, opcoes: { status?: ImportRecordStatus; aposLinha?: number; tamanho?: number } = {}) =>
    api
      .get<ImportRecordPage>(`${baseUrl}/${id}/registros`, {
        params: {
          status: opcoes.status,
          aposLinha: opcoes.aposLinha ?? 0,
          tamanho: opcoes.tamanho ?? 50,
        },
      })
      .then(r => r.data),

  /** Destino do lote: conta de caixa (extrato) ou cartão (fatura) — um ou outro, nunca os dois. */
  preparar: (id: number, destino: { contaFinanceiraId?: number; cartaoId?: number }) =>
    api.post<ImportBatch>(`${baseUrl}/${id}/preparar`, destino).then(r => r.data),

  aprovar: (id: number, registroId: number, categoriaId?: number) =>
    api
      .post<ImportRecord>(`${baseUrl}/${id}/registros/${registroId}/aprovar`,
        categoriaId ? { categoriaId } : {})
      .then(r => r.data),

  lancar: (id: number) => api.post<ImportBatch>(`${baseUrl}/${id}/commit`).then(r => r.data),

  reverter: (id: number) => api.post<ImportBatch>(`${baseUrl}/${id}/reverter`).then(r => r.data),
};

export default importacaoService;
