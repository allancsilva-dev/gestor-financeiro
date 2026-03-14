import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ErrorBoundary from './ErrorBoundary';

let shouldCrash = true;

function FlakyComponent() {
  if (shouldCrash) {
    throw new Error('falha intencional');
  }
  return <div>conteudo recuperado</div>;
}

describe('ErrorBoundary', () => {
  beforeEach(() => {
    shouldCrash = true;
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('deve exibir fallback ao capturar erro', () => {
    render(
      <ErrorBoundary>
        <FlakyComponent />
      </ErrorBoundary>
    );

    expect(screen.getByText('Algo deu errado')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeInTheDocument();
  });

  it('deve permitir tentar novamente e recuperar a tela', () => {
    render(
      <ErrorBoundary>
        <FlakyComponent />
      </ErrorBoundary>
    );

    shouldCrash = false;
    fireEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }));

    expect(screen.getByText('conteudo recuperado')).toBeInTheDocument();
  });
});
