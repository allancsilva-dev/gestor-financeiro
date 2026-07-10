import api from './api';
import { ImportResult } from '../types';
import { UploadFile } from './anexoService';

const importService = {
  csv: (file: UploadFile) => {
    const form = new FormData();
    form.append('file', {
      uri: file.uri,
      name: file.name,
      type: file.type,
    } as any);
    return api.post<ImportResult>('/v1/importar/csv', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }).then(r => r.data);
  },
};

export default importService;
