import { useState, FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { Wallet, Mail, ArrowLeft, CheckCircle } from 'lucide-react';
import api from '../services/api';
import toast from 'react-hot-toast';

export default function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [enviado, setEnviado] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setLoading(true);

    try {
      await api.post('/auth/forgot-password', { email });
      setEnviado(true);
      toast.success('Email de recuperação enviado!');
    } catch (error: any) {
      toast.error('Erro ao solicitar recuperação de senha');
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

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
          {!enviado ? (
            <>
              <h2 className="text-2xl font-bold text-white mb-2 text-center">
                Esqueceu sua senha?
              </h2>
              <p className="text-gray-400 text-center mb-6">
                Digite seu email e enviaremos um link para redefinir sua senha
              </p>

              <form onSubmit={handleSubmit} className="space-y-5">
                <div>
                  <label className="block text-sm font-medium text-gray-400 mb-2">
                    Email
                  </label>
                  <div className="relative">
                    <Mail className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-500 w-5 h-5" />
                    <input
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      className="w-full bg-slate-900 border border-slate-700 rounded-xl pl-12 pr-4 py-3 text-white placeholder-gray-500 focus:ring-2 focus:ring-orange-500 focus:border-transparent transition"
                      placeholder="seu@email.com"
                      required
                    />
                  </div>
                </div>

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full bg-gradient-to-r from-orange-500 to-orange-600 text-white py-3 rounded-xl font-semibold hover:shadow-lg hover:shadow-orange-500/30 transition-all disabled:opacity-50"
                >
                  {loading ? 'Enviando...' : 'Enviar link de recuperação'}
                </button>
              </form>
            </>
          ) : (
            <div className="text-center">
              <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-green-500/20 mb-4">
                <CheckCircle className="w-10 h-10 text-green-400" />
              </div>
              <h2 className="text-2xl font-bold text-white mb-2">
                Email enviado!
              </h2>
              <p className="text-gray-400 mb-6">
                Se o email <strong className="text-white">{email}</strong> estiver cadastrado,
                você receberá um link para redefinir sua senha.
              </p>
              <p className="text-sm text-gray-500 mb-6">
                📧 Verifique também sua caixa de spam
              </p>
            </div>
          )}

          {/* Voltar */}
          <Link to="/login">
            <button className="w-full mt-6 text-gray-400 hover:text-white transition flex items-center justify-center gap-2">
              <ArrowLeft className="w-4 h-4" />
              Voltar para o login
            </button>
          </Link>
        </div>

        {/* Footer */}
        <p className="text-center text-gray-500 text-sm mt-6">
          © 2025 Financeiro - Todos os direitos reservados
        </p>
      </div>
    </div>
  );
}