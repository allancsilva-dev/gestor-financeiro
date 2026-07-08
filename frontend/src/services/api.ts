import axios from 'axios';

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8081/api';
const API_VERSION = '/v1';
const CSRF_COOKIE_NAME = 'csrfToken';
const CSRF_HEADER_NAME = 'X-CSRF-Token';

let accessToken: string | null = null;

export const setAccessToken = (token: string | null) => {
  accessToken = token;
};

export const getAccessToken = () => accessToken;

export const clearAccessToken = () => {
  accessToken = null;
};

const api = axios.create({
  baseURL: BASE_URL,
  withCredentials: true,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value: string | null) => void;
  reject: (reason?: unknown) => void;
}> = [];

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

const getCookieValue = (name: string): string | null => {
  const cookie = document.cookie
    .split('; ')
    .find((item) => item.startsWith(`${name}=`));

  if (!cookie) {
    return null;
  }

  return decodeURIComponent(cookie.split('=', 2)[1]);
};

const isCsrfProtectedAuthMutation = (url: string, method?: string): boolean => {
  if ((method || 'get').toLowerCase() !== 'post') {
    return false;
  }

  return url.includes('/auth/refresh-token') || url.includes('/auth/logout');
};

const applyCsrfHeader = <T extends Record<string, string>>(headers: T): T => {
  const csrfToken = getCookieValue(CSRF_COOKIE_NAME);
  if (!csrfToken) {
    return headers;
  }

  if (headers && typeof headers.set === 'function') {
    headers.set(CSRF_HEADER_NAME, csrfToken);
    return headers;
  }

  return {
    ...(headers || {}),
    [CSRF_HEADER_NAME]: csrfToken,
  };
};

api.interceptors.request.use((config) => {
  const requestUrl = config.url || '';
  const isAuthEndpoint = requestUrl.startsWith('/auth');

  // Todos os endpoints não-auth usam /api/v1 para versionamento explícito.
  if (!isAuthEndpoint && requestUrl.startsWith('/') && !requestUrl.startsWith(API_VERSION + '/')) {
    config.url = `${API_VERSION}${requestUrl}`;
  }

  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }

  if (isCsrfProtectedAuthMutation(config.url || '', config.method)) {
    config.headers = applyCsrfHeader(config.headers);
  }

  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error?.code === 'ECONNABORTED') {
      error.userMessage = 'A requisição demorou mais que o esperado. Tente novamente.';
    }

    const originalRequest = error.config;
    const requestUrl = originalRequest?.url || '';
    const isAuthEndpoint = requestUrl.includes('/auth/login') || requestUrl.includes('/auth/refresh-token');

    if (error.response?.status === 401 && !originalRequest?._retry && !isAuthEndpoint) {
      
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            if (token) {
              originalRequest.headers.Authorization = `Bearer ${token}`;
            }
            return api(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const response = await axios.post(
          `${BASE_URL}/auth/refresh-token`, 
          {},
          {
            headers: applyCsrfHeader({ 'Content-Type': 'application/json' }),
            withCredentials: true,
          }
        );

        const { accessToken, token } = response.data;
        const novoToken = accessToken || token;

        setAccessToken(novoToken);
        api.defaults.headers.common['Authorization'] = `Bearer ${novoToken}`;
        originalRequest.headers.Authorization = `Bearer ${novoToken}`;

        processQueue(null, novoToken);

        return api(originalRequest);

      } catch (refreshError: unknown) {
        processQueue(refreshError, null);
        clearAccessToken();
        window.location.href = '/login';
        return Promise.reject(refreshError);

      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export default api;
