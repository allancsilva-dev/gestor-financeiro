import api from './api';
import {
  ImportBatch, ImportInspecao, ImportMapeamento, ImportRecord, ImportRecordPage, ImportRecordStatus,
  PagedResponse,
} from '../types';
import { UploadFile } from './anexoService';

const baseUrl = '/v1/importacoes';

/**
 * Pipeline canônico: o arquivo vira lote em revisão e **nada** entra no extrato antes de o
 * usuário confirmar. O endpoint legado (`/v1/importar/csv`) gravava direto e está desligado.
 */
const importacaoService = {
  enviar: (file: UploadFile, idempotencyKey?: string, mapeamentoId?: number) => {
    const form = new FormData();
    form.append('file', { uri: file.uri, name: file.name, type: file.type } as any);
    if (mapeamentoId != null) form.append('mapeamentoId', String(mapeamentoId));
    return api
      .post<ImportBatch>(baseUrl, form, {
        headers: {
          'Content-Type': 'multipart/form-data',
          ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
        },
      })
      .then(r => r.data);
  },

  /** Lê só os cabeçalhos do arquivo, para o usuário montar um mapeamento. Nenhuma linha trafega. */
  inspecionar: (file: UploadFile) => {
    const form = new FormData();
    form.append('file', { uri: file.uri, name: file.name, type: file.type } as any);
    return api
      .post<ImportInspecao>(`${baseUrl}/inspecionar`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then(r => r.data);
  },

  listarMapeamentos: () =>
    api.get<ImportMapeamento[]>(`${baseUrl}/mapeamentos`).then(r => r.data),

  salvarMapeamento: (dados: {
    nome: string;
    instituicao?: string;
    delimitador?: string;
    colunas: Record<string, string>;
  }) => api.post<ImportMapeamento>(`${baseUrl}/mapeamentos`, dados).then(r => r.data),

  removerMapeamento: (id: number) =>
    api.delete<void>(`${baseUrl}/mapeamentos/${id}`).then(() => undefined),

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
