const BRL_FORMATTER = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
});

export const formatCurrency = (value: number | null | undefined): string => {
  const normalized = typeof value === 'number' && Number.isFinite(value) ? value : 0;
  return BRL_FORMATTER.format(normalized);
};

export const parseCurrencyInput = (rawValue: string): number | null => {
  const digits = rawValue.replace(/\D/g, '');
  if (!digits) {
    return null;
  }
  return Number(digits) / 100;
};

export const formatCurrencyInput = (value: number | null | undefined): string => {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '';
  }
  return formatCurrency(value);
};
