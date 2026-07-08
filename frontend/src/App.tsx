import { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
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
const Onboarding = lazy(() => import('./pages/Onboarding'));
const Orcamentos = lazy(() => import('./pages/Orcamentos'));
const Faturas = lazy(() => import('./pages/Faturas'));
const Relatorios = lazy(() => import('./pages/Relatorios'));
const Investimentos = lazy(() => import('./pages/Investimentos'));
const NotFound = lazy(() => import('./pages/NotFound'));

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

function OnboardingGuard({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, needsOnboarding } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" />;
  }

  if (needsOnboarding && location.pathname !== '/onboarding') {
    return <Navigate to="/onboarding" replace />;
  }

  if (!needsOnboarding && location.pathname === '/onboarding') {
    return <Navigate to="/dashboard" replace />;
  }

  return <>{children}</>;
}

function AppRoutes() {
  return (
    <Routes>
      {/* Rotas Públicas */}
      <Route path="/register" element={<Register />} />
      <Route path="/login" element={<Login />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />

      {/* Onboarding — acessível apenas durante fluxo de onboarding */}
      <Route path="/onboarding" element={<OnboardingGuard><Onboarding /></OnboardingGuard>} />

      {/* Rotas Privadas com Guard de Onboarding */}
      <Route path="/dashboard" element={<OnboardingGuard><Dashboard /></OnboardingGuard>} />
      <Route path="/categorias" element={<OnboardingGuard><Categorias /></OnboardingGuard>} />
      <Route path="/contas" element={<OnboardingGuard><Contas /></OnboardingGuard>} />
      <Route path="/transacoes" element={<OnboardingGuard><Transacoes /></OnboardingGuard>} />
      <Route path="/metas" element={<OnboardingGuard><Metas /></OnboardingGuard>} />
      <Route path="/carteira" element={<OnboardingGuard><CarteiraPage /></OnboardingGuard>} />
      <Route path="/contas-fixas" element={<OnboardingGuard><ContasFixas /></OnboardingGuard>} />
      <Route path="/orcamentos" element={<OnboardingGuard><Orcamentos /></OnboardingGuard>} />
      <Route path="/faturas" element={<OnboardingGuard><Faturas /></OnboardingGuard>} />
      <Route path="/relatorios" element={<OnboardingGuard><Relatorios /></OnboardingGuard>} />
      <Route path="/investimentos" element={<OnboardingGuard><Investimentos /></OnboardingGuard>} />

      <Route path="/" element={<Navigate to="/dashboard" />} />
      <Route path="*" element={<NotFound />} />
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