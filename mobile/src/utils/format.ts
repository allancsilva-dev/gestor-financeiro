// Utilitários de formatação — sempre usar aqui (não usar Intl direto nos componentes)
import { TipoCarteira, TipoConta, StatusPagamento } from '../types';

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

// Converte data do formato DD/MM/AAAA para YYYY-MM-DD (necessário para enviar ao backend)
export const parseDateBR = (dataBR: string): string => {
  const [day, month, year] = dataBR.split('/');
  return `${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}`;
};

// Verifica se uma string esta no formato DD/MM/AAAA
export const isValidDateBR = (value: string): boolean =>
  /^\d{2}\/\d{2}\/\d{4}$/.test(value);

// Converte string monetaria BR (1.234,56) para number (1234.56)
export const parseCurrencyBR = (value: string): number => {
  const cleaned = value.replace(/\./g, '').replace(/,/g, '.').trim();
  return parseFloat(cleaned);
};

export const TIPO_CARTEIRA_LABEL: Record<TipoCarteira, string> = {
  DINHEIRO: 'Dinheiro',
  CONTA_BANCARIA: 'Conta Bancária',
  POUPANCA: 'Poupança',
};

export const TIPO_CONTA_LABEL: Record<TipoConta, string> = {
  CREDITO: 'Crédito',
  DEBITO: 'Débito',
  DINHEIRO: 'Dinheiro',
  POUPANCA: 'Poupança',
};

export const STATUS_LABEL: Record<StatusPagamento, string> = {
  PAGO: 'Pago',
  PENDENTE: 'Pendente',
  ATRASADO: 'Atrasado',
  CANCELADO: 'Cancelado',
};

export const CATEGORY_COLORS = [
  '#00c8ff', // azul brand
  '#2ed573', // verde
  '#ff4757', // vermelho
  '#ffa502', // amarelo
  '#8b2fff', // roxo
  '#ff6b81', // rosa
  '#1e90ff', // azul royal
  '#ff6348', // laranja
];
