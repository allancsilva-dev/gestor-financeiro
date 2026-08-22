// Utilitários de formatação — sempre usar aqui (não usar Intl direto nos componentes)
import { TipoMovimentoCarteira, StatusPagamento } from '../types';

export const formatCurrency = (value: number): string =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);

// Data ISO (YYYY-MM-DD) para DD/MM/AAAA sem passar por Date — evita o
// deslocamento de fuso do new Date('YYYY-MM-DD') (UTC) em telas de vencimento
export const formatDateOnlyBR = (iso: string): string => {
  const [year, month, day] = iso.split('-');
  return `${day}/${month}/${year}`;
};

const ISO_DATE_ONLY = /^\d{4}-\d{2}-\d{2}$/;

// Strings date-only da API (LocalDate) não passam por Date: new Date('2026-08-19')
// é meia-noite UTC e volta um dia em UTC-3.
// Data por extenso do cabeçalho da home: "sábado, 18 de julho"
export const formatDateLongBR = (date: Date = new Date()): string => {
  const texto = new Intl.DateTimeFormat('pt-BR', {
    weekday: 'long', day: 'numeric', month: 'long',
  }).format(date);
  return texto.charAt(0).toUpperCase() + texto.slice(1);
};

export const formatDate = (date: Date | string): string =>
  typeof date === 'string' && ISO_DATE_ONLY.test(date)
    ? formatDateOnlyBR(date)
    : new Intl.DateTimeFormat('pt-BR').format(new Date(date));

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

// Data de hoje no formato DD/MM/AAAA (default do lançamento rápido, PR-F3-05)
export const todayBR = (): string => {
  const d = new Date();
  const day = String(d.getDate()).padStart(2, '0');
  const month = String(d.getMonth() + 1).padStart(2, '0');
  return `${day}/${month}/${d.getFullYear()}`;
};

// Converte data do formato DD/MM/AAAA para YYYY-MM-DD (necessário para enviar ao backend)
export const parseDateBR = (dataBR: string): string => {
  const [day, month, year] = dataBR.split('/');
  return `${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}`;
};

// Verifica formato DD/MM/AAAA e se a data existe no calendario.
export const isValidDateBR = (value: string): boolean => {
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value);
  if (!match) return false;
  const day = Number(match[1]);
  const month = Number(match[2]);
  const year = Number(match[3]);
  if (year < 1900 || year > 2100 || month < 1 || month > 12 || day < 1) return false;
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
};

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

/**
 * Paleta de categoria. A **ordem importa**: `src/domain/categoriasIniciais.ts` e
 * `NovaTransacaoModal` escolhem por índice.
 */
export const CATEGORY_COLORS = [
  '#00c8ff',
  '#2ed573',
  '#ff4757',
  '#ffa502',
  '#8b2fff',
  '#ff6b81',
  '#1e90ff',
  '#ff6348',
  '#747d8c',
];

/**
 * Nome de cada cor da paleta. O nome era um comentário ao lado do hex, então o
 * seletor de cor só sabia dizer "Cor 1", "Cor 2" — posição não é cor, e quem usa
 * leitor de tela não tinha como saber o que estava escolhendo.
 */
export const NOME_DA_COR: Record<string, string> = {
  '#00c8ff': 'ciano',
  '#2ed573': 'verde',
  '#ff4757': 'vermelho',
  '#ffa502': 'amarelo',
  '#8b2fff': 'roxo',
  '#ff6b81': 'rosa',
  '#1e90ff': 'azul royal',
  '#ff6348': 'laranja',
  '#747d8c': 'cinza neutro',
};
