import api from './api';
import { Anexo } from '../types';

export interface UploadFile {
  uri: string;
  name: string;
  type: string;
}

const appendFile = (form: FormData, file: UploadFile) => {
  form.append('file', {
    uri: file.uri,
    name: file.name,
    type: file.type,
  } as any);
};

const anexoService = {
  listar: (transacaoId: number) =>
    api.get<Anexo[]>(`/v1/anexos/${transacaoId}`).then(r => r.data),

  upload: (transacaoId: number, file: UploadFile) => {
    const form = new FormData();
    appendFile(form, file);
    return api.post<Anexo>(`/v1/anexos/${transacaoId}`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }).then(r => r.data);
  },

  deletar: (id: number) =>
    api.delete(`/v1/anexos/${id}`),
};

export default anexoService;
