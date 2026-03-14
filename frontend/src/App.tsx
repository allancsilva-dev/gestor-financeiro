import { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider, useAuth } from './context/AuthContext';
import ErrorBoundary from './components/ErrorBoundary';

const Login = lazy(() => import('./pages/Login'));
const Dashboard = lazy(() => import('./pages/Dashboard'));
const Register = lazy(() => import('./pages/Register'));
const ForgotPassword = lazy(() => import('./pages/ForgotPassword'));
const ResetPassword = lazy(() => import('./pages/ResetPassword'));
const Categorias = lazy(() => import('./pages/Categorias'));
const Contas = lazy(() => import('./pages/contas'));
const Transacoes = lazy(() => import('./pages/Transacoes'));
const Metas = lazy(() => import('./pages/Metas'));
const CarteiraPage = lazy(() => import('./pages/Carteira'));
const ContasFixas = lazy(() => import('./pages/ContasFixas'));

function RouteFallback() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-900">
      <div className="text-center">
        <div className="inline-block animate-spin rounded-full h-10 w-10 border-b-2 border-orange-500"></div>
        <p className="mt-3 text-slate-300">Carregando tela...</p>
      </div>
    </div>
  );
}

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" />;
}

function AppRoutes() {
  return (
    <Routes>
      {/* Rotas Públicas */}
      <Route path="/register" element={<Register />} />
      <Route path="/login" element={<Login />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route path="/" element={<Navigate to="/login" />} />
      
      {/* Rotas Privadas */}
      <Route
        path="/dashboard"
        element={
          <PrivateRoute>
            <Dashboard />
          </PrivateRoute>
        }
      />
      
      <Route
        path="/categorias"
        element={
          <PrivateRoute>
            <Categorias />
          </PrivateRoute>
        }
      />
      
      <Route
        path="/contas"
        element={
          <PrivateRoute>
            <Contas />
          </PrivateRoute>
        }
      />
      
      <Route
        path="/transacoes"
        element={
          <PrivateRoute>
            <Transacoes />
          </PrivateRoute>
        }
      />
      
      <Route
        path="/metas"
        element={
          <PrivateRoute>
            <Metas />
          </PrivateRoute>
        }
      />
      
      <Route
        path="/carteira"
        element={
          <PrivateRoute>
            <CarteiraPage />
          </PrivateRoute>
        }
      />
      
      <Route
        path="/contas-fixas"
        element={
          <PrivateRoute>
            <ContasFixas />
          </PrivateRoute>
        }
      />
    </Routes>
  );
}

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ErrorBoundary>
          <Toaster position="top-right" />
          <Suspense fallback={<RouteFallback />}>
            <AppRoutes />
          </Suspense>
        </ErrorBoundary>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;