import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Wallet, LayoutDashboard, CreditCard, ArrowLeftRight, Target, LogOut, FileText, BarChart3, ReceiptText, FileSearch, TrendingUp } from 'lucide-react';

interface LayoutProps { children: React.ReactNode }

const menuItems = [
  { path: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { path: '/contas-financeiras', label: 'Contas financeiras', icon: Wallet },
  { path: '/contas', label: 'Cartões', icon: CreditCard },
  { path: '/transacoes', label: 'Transações', icon: ArrowLeftRight },
  { path: '/metas', label: 'Metas', icon: Target },
  { path: '/contas-fixas', label: 'Contas Fixas', icon: FileText },
  { path: '/orcamentos', label: 'Orçamentos', icon: BarChart3 },
  { path: '/faturas', label: 'Faturas', icon: ReceiptText },
  { path: '/relatorios', label: 'Relatórios', icon: FileSearch },
  { path: '/investimentos', label: 'Investimentos', icon: TrendingUp },
];

export default function Layout({ children }: LayoutProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const { logout, usuario } = useAuth();

  const handleLogout = () => { logout(); navigate('/login'); };

  return (
    <div className="min-h-screen bg-[#f0f2f8] text-[#1a1d23] lg:flex">
      <aside className="border-b border-violet-100 bg-white lg:sticky lg:top-0 lg:h-screen lg:w-64 lg:border-b-0 lg:border-r">
        <div className="flex h-16 items-center justify-between px-4 lg:h-auto lg:px-5 lg:py-5">
          <Link to="/dashboard" className="flex items-center gap-3 rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet-600">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-violet-600 text-xl text-white" aria-hidden="true">💰</span>
            <span><strong className="block text-base">Financeiro</strong><span className="block text-xs text-slate-600">Gestor pessoal</span></span>
          </Link>
          <button onClick={handleLogout} className="rounded-lg p-2 text-slate-600 hover:bg-red-50 hover:text-red-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet-600 lg:hidden" aria-label="Sair"><LogOut className="h-5 w-5" /></button>
        </div>

        <nav aria-label="Navegação principal" className="flex gap-1 overflow-x-auto px-3 pb-3 lg:block lg:space-y-1 lg:overflow-visible">
          {menuItems.map(({ path, label, icon: Icon }) => {
            const active = location.pathname === path || (path === '/contas-financeiras' && location.pathname === '/carteira');
            return <Link key={path} to={path} aria-current={active ? 'page' : undefined}
              className={`flex min-h-11 shrink-0 items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet-600 ${active ? 'bg-violet-100 text-violet-800' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950'}`}>
              <Icon className="h-5 w-5" aria-hidden="true" /><span>{label}</span>
            </Link>;
          })}
        </nav>

        <div className="hidden border-t border-slate-100 p-4 lg:absolute lg:bottom-0 lg:block lg:w-64">
          <p className="truncate text-sm font-semibold">{usuario?.nome || 'Usuário'}</p>
          <p className="truncate text-xs text-slate-600">{usuario?.email}</p>
          <button onClick={handleLogout} className="mt-3 flex min-h-11 w-full items-center justify-center gap-2 rounded-lg text-sm font-medium text-red-700 hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet-600"><LogOut className="h-4 w-4" /> Sair</button>
        </div>
      </aside>
      <main className="min-w-0 flex-1 p-4 sm:p-6 lg:p-8">{children}</main>
    </div>
  );
}
