import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';

export default function Dashboard() {
  const { usuario, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="min-h-screen bg-gray-100">
      <nav className="bg-white shadow-lg">
        <div className="max-w-7xl mx-auto px-4 py-4 flex justify-between items-center">
          <h1 className="text-2xl font-bold text-gray-800">
            💰 Gestor Financeiro
          </h1>
          <button
            onClick={handleLogout}
            className="bg-red-500 hover:bg-red-600 text-white px-4 py-2 rounded-lg transition"
          >
            Sair
          </button>
        </div>
      </nav>

      <div className="max-w-7xl mx-auto px-4 py-8">
        <div className="bg-white rounded-lg shadow-md p-6">
          <h2 className="text-3xl font-bold text-gray-800 mb-4">
            Bem-vindo, {usuario?.nome}! 🎉
          </h2>
          <p className="text-gray-600 mb-2">
            <strong>Email:</strong> {usuario?.email}
          </p>
          <p className="text-gray-600">
            <strong>ID:</strong> {usuario?.id}
          </p>
          
          <div className="mt-6 p-4 bg-blue-50 rounded-lg">
            <p className="text-blue-800">
              ✅ Frontend funcionando com mock!
            </p>
            <p className="text-sm text-blue-600 mt-2">
              Próximo passo: conectar com o backend real.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}