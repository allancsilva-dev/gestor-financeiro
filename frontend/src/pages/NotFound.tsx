import { Link } from 'react-router-dom';

export default function NotFound() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-900">
      <div className="text-center">
        <h1 className="text-6xl font-bold text-slate-400 mb-4">404</h1>
        <p className="text-slate-300 text-lg mb-6">Pagina nao encontrada</p>
        <Link to="/dashboard" className="inline-block px-6 py-3 bg-orange-500 hover:bg-orange-600 text-white rounded-lg transition-colors">
          Voltar ao Dashboard
        </Link>
      </div>
    </div>
  );
}
