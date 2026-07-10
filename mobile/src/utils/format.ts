// Utilitários de formatação — sempre usar aqui (não usar Intl direto nos componentes)
import { TipoCarteira, TipoConta, TipoMovimentoCarteira, StatusPagamento } from '../types';

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

// Máscara de moeda para digitação, centavos primeiro: dígitos viram centavos (digitar 1500 → "15,00")
export const maskCurrencyInput = (text: string): string => {
  const digits = text.replace(/\D/g, '').slice(0, 12);
  if (!digits) return '';
  const cents = digits.padStart(3, '0');
  const int = cents.slice(0, -2).replace(/^0+(?=\d)/, '');
  return `${int.replace(/\B(?=(\d{3})+(?!\d))/g, '.')},${cents.slice(-2)}`;
};

// Máscara de data para digitação: insere as barras de DD/MM/AAAA automaticamente
export const maskDateInput = (text: string): string => {
  const digits = text.replace(/\D/g, '').slice(0, 8);
  if (digits.length <= 2) return digits;
  if (digits.length <= 4) return `${digits.slice(0, 2)}/${digits.slice(2)}`;
  return `${digits.slice(0, 2)}/${digits.slice(2, 4)}/${digits.slice(4)}`;
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

export const TIPO_MOVIMENTO_LABEL: Record<TipoMovimentoCarteira, string> = {
  ENTRADA: 'Entrada',
  SAIDA: 'Saída',
  AJUSTE_MANUAL: 'Ajuste manual',
  TRANSFERENCIA_ENTRADA: 'Transferência recebida',
  TRANSFERENCIA_SAIDA: 'Transferência enviada',
  RESERVA_META: 'Reserva para meta',
  RESGATE_META: 'Resgate de meta',
  ESTORNO: 'Estorno',
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
