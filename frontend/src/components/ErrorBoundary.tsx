import { Component, ErrorInfo, ReactNode } from 'react';

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
}

class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    // Logging local até integrar observabilidade central.
    console.error('Erro capturado pelo ErrorBoundary:', error, errorInfo);
  }

  handleRetry = () => {
    this.setState({ hasError: false });
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex items-center justify-center bg-slate-900 p-6">
          <div className="max-w-md w-full bg-slate-800 border border-slate-700 rounded-xl p-6 text-center">
            <h1 className="text-xl font-bold text-white mb-2">Algo deu errado</h1>
            <p className="text-slate-300 mb-6">A tela encontrou um erro inesperado. Tente novamente.</p>
            <button
              type="button"
              onClick={this.handleRetry}
              className="px-4 py-2 rounded-lg bg-orange-500 hover:bg-orange-600 text-white font-medium"
            >
              Tentar novamente
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
