import { useState, useEffect, FormEvent } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { Wallet, Lock, CheckCircle, XCircle } from 'lucide-react';
import api from '../services/api';
import toast from 'react-hot-toast';

export default function ResetPassword() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  
  const [token, setToken] = useState('');
  const [novaSenha, setNovaSenha] = useState('');
  const [confirmarSenha, setConfirmarSenha] = useState('');
  const [loading, setLoading] = useState(false);
  const [validandoToken, setValidandoToken] = useState(true);
  const [tokenValido, setTokenValido] = useState(false);
  const [sucesso, setSucesso] = useState(false);

  useEffect(() => {
    const tokenParam = searchParams.get('token');
    if (!tokenParam) {
      toast.error('Token não encontrado!');
      navigate('/login');
      return;
    }

    setToken(tokenParam);
    validarToken(tokenParam);
  }, [searchParams, navigate]);

  const validarToken = async (token: string) => {
    try {
      await api.get(`/auth/validate-token?token=${token}`);
      setTokenValido(true);
    } catch (error: any) {
      setTokenValido(false);
      toast.error(error.response?.data || 'Token inválido ou expirado');
    } finally {
      setValidandoToken(false);
    }
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();

    if (novaSenha !== confirmarSenha) {
      toast.error('As senhas não coincidem!');
      return;
    }

    if (novaSenha.length < 6) {
      toast.error('A senha deve ter no mínimo 6 caracteres!');
      return;
    }

    setLoading(true);

    try {
      await api.post('/auth/reset-password', {
        token,
        novaSenha
      });
      
      setSucesso(true);
      toast.success('Senha alterada com sucesso!');
      
      // Redireciona para login após 3 segundos
      setTimeout(() => {
        navigate('/login');
      }, 3000);
    } catch (error: any) {
      toast.error(error.response?.data || 'Erro ao redefinir senha');
    } finally {
      setLoading(false);
    }
  };

  if (validandoToken) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 flex items-center justify-center">
        <div className="text-center">
          <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-orange-500 mb-4"></div>
          <p className="text-gray-400">Validando token...</p>
        </div>
      </div>
    );
  }

  if (!tokenValido) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 flex items-center justify-center p-4">
        <div className="w-full max-w-md">
          <div className="bg-slate-800 rounded-2xl shadow-2xl border border-slate-700 p-8 text-center">
            <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-red-500/20 mb-4">
              <XCircle className="w-10 h-10 text-red-400" />
            </div>
            <h2 className="text-2xl font-bold text-white mb-2">
              Token Inválido
            </h2>
            <p className="text-gray-400 mb-6">
              Este link de recuperação é inválido ou já expirou.
            </p>
            <Link to="/forgot-password">
              <button className="w-full bg-gradient-to-r from-orange-500 to-orange-600 text-white py-3 rounded-xl font-semibold hover:shadow-lg transition-all">
                Solicitar novo link
              </button>
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-20 h-20 rounded-2xl bg-gradient-to-br from-orange-500 to-orange-600 shadow-2xl shadow-orange-500/20 mb-4">
            <Wallet className="w-10 h-10 text-white" />
          </div>
          <h1 className="text-4xl font-bold text-white mb-2">Financeiro</h1>
        </div>

        {/* Card */}
        <div className="bg-slate-800 rounded-2xl shadow-2xl border border-slate-700 p-8">
          {!sucesso ? (
            <>
              <h2 className="text-2xl font-bold text-white mb-2 text-center">
                Redefinir Senha
              </h2>
              <p className="text-gray-400 text-center mb-6">
                Digite sua nova senha
              </p>

              <form onSubmit={handleSubmit} className="space-y-5">
                <div>
                  <label className="block text-sm font-medium text-gray-400 mb-2">
                    Nova Senha
                  </label>
                  <div className="relative">
                    <Lock className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-500 w-5 h-5" />
                    <input
                      type="password"
                      value={novaSenha}
                      onChange={(e) => setNovaSenha(e.target.value)}
                      className="w-full bg-slate-900 border border-slate-700 rounded-xl pl-12 pr-4 py-3 text-white placeholder-gray-500 focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
                      placeholder="••••••••"
                      required
                      minLength={6}
                    />
                  </div>
                  <p className="text-xs text-gray-500 mt-1">
                    Mínimo de 6 caracteres
                  </p>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-400 mb-2">
                    Confirmar Nova Senha
                  </label>
                  <div className="relative">
                    <Lock className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-500 w-5 h-5" />
                    <input
                      type="password"
                      value={confirmarSenha}
                      onChange={(e) => setConfirmarSenha(e.target.value)}
                      className="w-full bg-slate-900 border border-slate-700 rounded-xl pl-12 pr-4 py-3 text-white placeholder-gray-500 focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
                      placeholder="••••••••"
                      required
                    />
                  </div>
                </div>

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full bg-gradient-to-r from-orange-500 to-orange-600 text-white py-3 rounded-xl font-semibold hover:shadow-lg hover:shadow-orange-500/30 transition-all disabled:opacity-50"
                >
                  {loading ? 'Redefinindo...' : 'Redefinir Senha'}
                </button>
              </form>
            </>
          ) : (
            <div className="text-center">
              <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-green-500/20 mb-4">
                <CheckCircle className="w-10 h-10 text-green-400" />
              </div>
              <h2 className="text-2xl font-bold text-white mb-2">
                Senha Alterada!
              </h2>
              <p className="text-gray-400 mb-6">
                Sua senha foi alterada com sucesso!
              </p>
              <p className="text-sm text-gray-500">
                Redirecionando para o login...
              </p>
            </div>
          )}
        </div>

        {/* Footer */}
        <p className="text-center text-gray-500 text-sm mt-6">
          © 2025 Financeiro - Todos os direitos reservados
        </p>
      </div>
    </div>
  );
}