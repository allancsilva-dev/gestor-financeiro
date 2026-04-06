// Utilitários de formatação — sempre usar aqui (não usar Intl direto nos componentes)
export const formatCurrency = (value: number): string =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);

export const formatDate = (date: Date | string): string =>
  new Intl.DateTimeFormat('pt-BR').format(new Date(date));

export const formatDateTime = (date: Date | string): string =>
  new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(date));

export const formatNumber = (value: number, decimals = 2): string =>
  new Intl.NumberFormat('pt-BR', { minimumFractionDigits: decimals }).format(value);

export const formatPercent = (value: number, decimals = 1): string =>
  `${formatNumber(value, decimals)}%`;

export const formatPhone = (value: string): string => {
  const digits = value.replace(/\D/g, '');
  if (digits.length === 11) return digits.replace(/(\d{2})(\d{5})(\d{4})/, '($1) $2-$3');
  return digits.replace(/(\d{2})(\d{4})(\d{4})/, '($1) $2-$3');
};

export const getGreeting = (): string => {
  const hour = new Date().getHours();
  if (hour < 12) return 'Bom dia,';
  if (hour < 18) return 'Boa tarde,';
  return 'Boa noite,';
};

export const getInitials = (nome: string): string =>
  nome.trim().split(' ').slice(0, 2).map(n => n[0].toUpperCase()).join('');
