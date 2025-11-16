import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Wallet, LayoutDashboard, CreditCard, ArrowLeftRight, Target, LogOut } from 'lucide-react';

interface LayoutProps {
  children: React.ReactNode;
}

export default function Layout({ children }: LayoutProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const { logout } = useAuth();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const menuItems = [
    { path: '/dashboard', label: 'Dashboard', icon: <LayoutDashboard className="w-5 h-5" />, color: 'text-orange-500' },
    { path: '/carteira', label: 'Carteira', icon: <Wallet className="w-5 h-5" />, color: 'text-teal-500' },
    { path: '/contas', label: 'Cartões', icon: <CreditCard className="w-5 h-5" />, color: 'text-blue-500' },
    { path: '/transacoes', label: 'Transações', icon: <ArrowLeftRight className="w-5 h-5" />, color: 'text-purple-500' },
    { path: '/metas', label: 'Metas', icon: <Target className="w-5 h-5" />, color: 'text-pink-500' },
  ];

  return (
    <div className="flex min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900">
      {/* Menu Lateral */}
      <aside className="w-64 bg-slate-800 border-r border-slate-700 shadow-2xl">
        {/* Header */}
        <div className="p-6 border-b border-slate-700">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-orange-500 to-orange-600 flex items-center justify-center shadow-lg">
              <span className="text-xl">💰</span>
            </div>
            <div>
              <h1 className="text-xl font-bold text-white">Financeiro</h1>
              <p className="text-xs text-gray-400">Gestor Pessoal</p>
            </div>
          </div>
        </div>

        {/* Menu Items */}
        <nav className="mt-6 px-3 space-y-2">
          {menuItems.map((item) => {
            const isActive = location.pathname === item.path;
            return (
              <Link
                key={item.path}
                to={item.path}
                className={`flex items-center gap-3 px-4 py-3 rounded-xl transition-all duration-200 group ${
                  isActive
                    ? 'bg-gradient-to-r from-orange-500 to-orange-600 text-white shadow-lg shadow-orange-500/20'
                    : 'text-gray-400 hover:bg-slate-700/50 hover:text-white'
                }`}
              >
                <span className={`${isActive ? 'text-white' : item.color} transition-colors`}>
                  {item.icon}
                </span>
                <span className="font-medium">{item.label}</span>
                {isActive && (
                  <div className="ml-auto w-2 h-2 rounded-full bg-white animate-pulse"></div>
                )}
              </Link>
            );
          })}
        </nav>

        {/* User Info & Logout */}
        <div className="absolute bottom-0 w-64 p-4 border-t border-slate-700 bg-slate-800">
          {/* User Profile */}
          <div className="mb-4 p-3 bg-slate-700/50 rounded-xl">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-gradient-to-br from-blue-500 to-purple-500 flex items-center justify-center text-white font-bold">
                A
              </div>
              <div className="flex-1">
                <p className="text-sm font-medium text-white">Allan Carvalho</p>
                <p className="text-xs text-gray-400">allan@teste.com</p>
              </div>
            </div>
          </div>

          {/* Logout Button */}
          <button
            onClick={handleLogout}
            className="w-full bg-red-500/10 hover:bg-red-500/20 text-red-400 hover:text-red-300 py-2.5 px-4 rounded-xl transition-all duration-200 flex items-center justify-center gap-2 font-medium border border-red-500/20 hover:border-red-500/30"
          >
            <LogOut className="w-4 h-4" />
            <span>Sair</span>
          </button>
        </div>
      </aside>

      {/* Conteúdo */}
      <main className="flex-1 overflow-auto">
        {children}
      </main>
    </div>
  );
}