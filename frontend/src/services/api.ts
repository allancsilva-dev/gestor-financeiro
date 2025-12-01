import axios from 'axios';

// ✅ MUDANÇA 1: Definir a URL base dinamicamente
// Se estiver na Vercel, usa a variável. Se estiver no PC, usa localhost.
const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8081/api';

const api = axios.create({
  baseURL: BASE_URL, // ✅ Usa a constante aqui
  headers: {
    'Content-Type': 'application/json',
  },
});

// Controle de renovação
let isRefreshing = false;
let failedQueue: any[] = [];

const processQueue = (error: any, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

// Interceptor de Request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Interceptor de Response (Renovação Automática)
api.interceptors.response.use(
  (response) => {
    return response;
  },
  async (error) => {
    const originalRequest = error.config;

    // Se erro 401 e não é tentativa de renovação
    if (error.response?.status === 401 && !originalRequest._retry) {
      
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return api(originalRequest);
          })
          .catch((err) => {
            return Promise.reject(err);
          });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      const refreshToken = localStorage.getItem('refreshToken');

      if (!refreshToken) {
        console.error('❌ Sem refresh token');
        isRefreshing = false;
        localStorage.clear();
        window.location.href = '/login';
        return Promise.reject(error);
      }

      try {
        console.log('🔄 Token expirado, renovando automaticamente...');

        // Chamar renovação SEM interceptor (evitar loop)
        // ✅ MUDANÇA 2: Usar a BASE_URL aqui também!
        // Antes estava 'http://localhost:8081/api/auth/refresh-token'
        const response = await axios.post(
          `${BASE_URL}/auth/refresh-token`, 
          { refreshToken },
          {
            headers: {
              'Content-Type': 'application/json',
            },
          }
        );

        const { accessToken, token } = response.data;
        const novoToken = accessToken || token;

        localStorage.setItem('token', novoToken);
        api.defaults.headers.common['Authorization'] = `Bearer ${novoToken}`; // Boa prática adicionar essa linha
        originalRequest.headers.Authorization = `Bearer ${novoToken}`;

        processQueue(null, novoToken);

        console.log('✅ Token renovado automaticamente!');

        return api(originalRequest);

      } catch (refreshError: any) {
        console.error('❌ Erro ao renovar token:', refreshError.response?.data || refreshError.message);

        processQueue(refreshError, null);

        localStorage.clear();
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