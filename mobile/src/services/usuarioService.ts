import api from './api';
import { Usuario } from '../types';

const usuarioService = {
  me: () => api.get<Usuario>('/v1/usuarios/me').then(r => r.data),

  /**
   * Exclusão definitiva da conta e de todos os dados do titular (LGPD art. 18, V).
   * O backend exige a senha atual no corpo — em DELETE o axios só manda corpo
   * pelo campo `data`.
   */
  excluirConta: (senha: string) =>
    api.delete<void>('/v1/usuarios/me', { data: { senha } }).then(() => undefined),
};

export default usuarioService;
