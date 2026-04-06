// Token apenas em memória — sem persistência nesta fase.
// TODO fase 2: persistir com expo-secure-store para manter sessão entre aberturas.
let _accessToken: string | null = null;

export const setAccessToken = (token: string) => { _accessToken = token; };
export const getAccessToken = () => _accessToken;
export const clearAccessToken = () => { _accessToken = null; };
