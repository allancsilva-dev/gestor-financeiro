import api from './api';

export interface Capacidades {
  assistenteTexto: boolean;
  assistenteAudio: boolean;
  assistenteWhatsapp: boolean;
}

/** Fail-closed: enquanto o servidor não responde, nada é oferecido. */
export const CAPACIDADES_DESLIGADAS: Capacidades = {
  assistenteTexto: false,
  assistenteAudio: false,
  assistenteWhatsapp: false,
};

const capacidadesService = {
  obter: () => api.get<Capacidades>('/v1/capacidades').then(r => r.data),
};

export default capacidadesService;
