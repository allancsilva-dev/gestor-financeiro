type IconOption = {
  value: string;
  label: string;
};

const ICON_OPTIONS: IconOption[] = [
  { value: '🎯', label: 'Meta geral' },
  { value: '✈️', label: 'Viagem' },
  { value: '🏠', label: 'Casa / Imovel' },
  { value: '🚗', label: 'Carro / Veiculo' },
  { value: '📱', label: 'Tecnologia' },
  { value: '🎓', label: 'Educacao' },
  { value: '💍', label: 'Casamento' },
  { value: '🏥', label: 'Saude' },
  { value: '🎮', label: 'Lazer' },
  { value: '💰', label: 'Reserva de emergencia' },
  { value: '📊', label: 'Investimento' },
  { value: '🎁', label: 'Presente' },
  { value: '🧾', label: 'Contas' },
  { value: '🛵', label: 'Mobilidade' },
  { value: '👶', label: 'Familia' },
];

interface IconPickerProps {
  value: string;
  onChange: (value: string) => void;
}

export default function IconPicker({ value, onChange }: IconPickerProps) {
  return (
    <div className="grid grid-cols-4 md:grid-cols-5 gap-2">
      {ICON_OPTIONS.map((option) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            title={option.label}
            onClick={() => onChange(option.value)}
            className={`rounded-lg border px-3 py-2 text-xl transition ${
              selected
                ? 'border-blue-600 bg-blue-50 ring-2 ring-blue-200'
                : 'border-gray-300 bg-white hover:border-blue-400'
            }`}
            aria-label={option.label}
            aria-pressed={selected}
          >
            {option.value}
          </button>
        );
      })}
    </div>
  );
}
