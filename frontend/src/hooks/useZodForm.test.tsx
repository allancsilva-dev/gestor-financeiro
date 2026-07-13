import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import FieldError from '../components/FieldError';
import { fieldA11y } from '../validation/fieldA11y';
import { categoriaSchema } from '../validation/schemas';
import { useZodForm } from './useZodForm';

function TestForm({ onValid }: { onValid: (data: unknown) => void }) {
  const [nome, setNome] = useState('');
  const validation = useZodForm(categoriaSchema);
  const data = { nome, cor: '#123456', icone: 'tag', valorEsperado: '0' };
  return (
    <form onSubmit={event => {
      event.preventDefault();
      const parsed = validation.validate(data);
      if (parsed) onValid(parsed);
    }}>
      <input
        {...fieldA11y('nome', validation.errors.nome)}
        value={nome}
        onChange={event => {
          const next = { ...data, nome: event.target.value };
          setNome(event.target.value);
          validation.revalidateField('nome', next);
        }}
      />
      <FieldError name="nome" error={validation.errors.nome} />
      <button type="submit">Salvar</button>
    </form>
  );
}

describe('useZodForm', () => {
  it('bloqueia envio inválido, mostra erro acessível e foca o campo', async () => {
    const onValid = vi.fn();
    render(<TestForm onValid={onValid} />);
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    const input = screen.getByRole('textbox');
    expect(onValid).not.toHaveBeenCalled();
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('alert')).toHaveTextContent('Nome obrigatório');
    await waitFor(() => expect(input).toHaveFocus());
  });

  it('revalida após tentativa e envia payload normalizado', () => {
    const onValid = vi.fn();
    render(<TestForm onValid={onValid} />);
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Mercado' } });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    expect(onValid).toHaveBeenCalledWith(expect.objectContaining({ nome: 'Mercado', valorEsperado: 0 }));
  });
});
