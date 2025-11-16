import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider, useAuth } from './context/AuthContext';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Register from './pages/Register';
import Categorias from './pages/Categorias';
import Contas from './pages/contas';
import Transacoes from './pages/Transacoes';
import Metas from './pages/Metas';
import CarteiraPage from './pages/Carteira';
import ContasFixas from './pages/ContasFixas';

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" />;
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/register" element={<Register />} />
      <Route path="/login" element={<Login />} />
      <Route path="/" element={<Navigate to="/login" />} />
      
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
        <Toaster position="top-right" />
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;