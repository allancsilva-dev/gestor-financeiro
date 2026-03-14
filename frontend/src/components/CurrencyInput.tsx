import { ChangeEvent, InputHTMLAttributes } from 'react';
import { formatCurrencyInput, parseCurrencyInput } from '../utils/currency';

type CurrencyInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'value' | 'onChange'> & {
  value: number | null;
  onValueChange: (value: number | null) => void;
};

export default function CurrencyInput({ value, onValueChange, ...rest }: CurrencyInputProps) {
  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    const parsed = parseCurrencyInput(event.target.value);
    onValueChange(parsed);
  };

  return (
    <input
      {...rest}
      type="text"
      inputMode="numeric"
      value={formatCurrencyInput(value)}
      onChange={handleChange}
    />
  );
}
