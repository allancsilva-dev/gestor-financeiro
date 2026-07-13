import { z } from 'zod';

const emptyToUndefined = (value: unknown) => value === '' || value == null ? undefined : value;

const requiredNumber = (message: string) => z.preprocess(
  emptyToUndefined,
  z.coerce.number({ error: message }).finite(message),
);

export const requiredTextSchema = (label: string, max: number) => z.string()
  .trim()
  .min(1, `${label} obrigatório`)
  .max(max, `${label} deve ter no máximo ${max} caracteres`);

export const emailSchema = z.string().trim().min(1, 'Email obrigatório').email('Email inválido').max(254);
export const passwordSchema = z.string().min(8, 'Senha deve ter ao menos 8 caracteres')
  .regex(/[A-Za-z]/, 'Senha deve conter uma letra').regex(/\d/, 'Senha deve conter um número');
export const positiveMoneySchema = requiredNumber('Informe um valor válido')
  .pipe(z.number().positive('Valor deve ser positivo').max(99_999_999.99, 'Valor acima do limite permitido'));
export const nonNegativeMoneySchema = requiredNumber('Informe um valor válido')
  .pipe(z.number().min(0, 'Valor não pode ser negativo').max(99_999_999.99, 'Valor acima do limite permitido'));
export const positiveIdSchema = requiredNumber('Selecione uma opção')
  .pipe(z.number().int().positive('Selecione uma opção'));
export const dayOfMonthSchema = requiredNumber('Informe um dia válido')
  .pipe(z.number().int('Dia deve ser inteiro').min(1, 'Dia deve estar entre 1 e 31').max(31, 'Dia deve estar entre 1 e 31'));
export const isoDateSchema = z.string().refine(value => {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}, 'Data inválida');
export const hexColorSchema = z.string().regex(/^#[0-9A-Fa-f]{6}$/, 'Cor inválida');

export const loginSchema = z.object({ email: emailSchema, senha: z.string().min(1, 'Senha obrigatória') });
export const registerSchema = z.object({
  nome: z.string().trim().min(2, 'Nome deve ter ao menos 2 caracteres').max(100),
  email: emailSchema,
  senha: passwordSchema,
  confirmarSenha: z.string(),
  aceitaTermos: z.literal(true, { error: 'Aceite da política é obrigatório' }),
}).refine(data => data.senha === data.confirmarSenha, { path: ['confirmarSenha'], message: 'Senhas não coincidem' });

const transacaoBaseShape = {
  descricao: requiredTextSchema('Descrição', 255),
  valor: positiveMoneySchema,
  tipo: z.enum(['ENTRADA', 'SAIDA']),
  data: isoDateSchema,
  contaId: positiveIdSchema,
  parcelado: z.boolean().default(false),
  totalParcelas: z.preprocess(emptyToUndefined, z.coerce.number().int().min(2, 'Mínimo de 2 parcelas').max(48, 'Máximo de 48 parcelas').optional()),
};
const parcelamentoValido = (data: { parcelado: boolean; totalParcelas?: number }) => !data.parcelado || data.totalParcelas != null;
export const transacaoSchema = z.object({ ...transacaoBaseShape, categoriaId: positiveIdSchema })
  .refine(parcelamentoValido, { path: ['totalParcelas'], message: 'Informe o total de parcelas' });
export const transacaoFormSchema = z.object({ ...transacaoBaseShape,
  categoriaNome: requiredTextSchema('Categoria', 100),
}).refine(parcelamentoValido, { path: ['totalParcelas'], message: 'Informe o total de parcelas' });

export const carteiraSchema = z.object({
  nome: requiredTextSchema('Nome', 100),
  tipo: z.enum(['DINHEIRO', 'CONTA_BANCARIA', 'POUPANCA']),
  saldo: nonNegativeMoneySchema,
  banco: z.string().trim().max(100, 'Banco deve ter no máximo 100 caracteres').optional(),
}).refine(data => data.tipo !== 'CONTA_BANCARIA' || Boolean(data.banco), {
  path: ['banco'], message: 'Banco obrigatório',
});
export const movimentacaoCarteiraSchema = z.object({ valor: positiveMoneySchema });

export const categoriaSchema = z.object({
  nome: requiredTextSchema('Nome', 100),
  cor: hexColorSchema,
  icone: requiredTextSchema('Ícone', 10),
  valorEsperado: nonNegativeMoneySchema,
});

export const contaFixaSchema = z.object({
  nome: requiredTextSchema('Nome', 100),
  valorPlanejado: positiveMoneySchema,
  diaVencimento: dayOfMonthSchema,
  categoriaId: positiveIdSchema,
  observacoes: z.string().trim().max(500, 'Observações devem ter no máximo 500 caracteres').optional(),
});
export const pagamentoSchema = z.object({ valor: positiveMoneySchema, carteiraId: positiveIdSchema });

export const metaSchema = z.object({
  nome: requiredTextSchema('Nome', 100),
  valorTotal: positiveMoneySchema,
  valorMensal: nonNegativeMoneySchema,
  cor: hexColorSchema,
  icone: z.string().trim().max(20, 'Ícone deve ter no máximo 20 caracteres').optional(),
  descricao: z.string().trim().max(500, 'Descrição deve ter no máximo 500 caracteres').optional(),
  dataPrevista: z.preprocess(emptyToUndefined, isoDateSchema.optional()),
});
export const aporteMetaSchema = pagamentoSchema;

export const contaSchema = z.object({
  nome: requiredTextSchema('Nome', 100),
  tipo: z.enum(['CREDITO', 'DEBITO', 'DINHEIRO', 'POUPANCA']),
  limiteTotal: nonNegativeMoneySchema,
  diaFechamento: dayOfMonthSchema,
  diaVencimento: dayOfMonthSchema,
  cor: hexColorSchema,
});

export const ativoSchema = z.object({
  ticker: requiredTextSchema('Ticker', 20).transform(value => value.toUpperCase()),
  nome: requiredTextSchema('Nome', 100),
  tipo: z.enum(['ACAO', 'FII', 'ETF', 'RENDA_FIXA', 'CRIPTO', 'OUTRO']),
  valorAtual: z.preprocess(emptyToUndefined, positiveMoneySchema.optional()),
});
export const movimentacaoAtivoSchema = z.object({
  tipo: z.enum(['COMPRA', 'VENDA', 'DIVIDENDO', 'BONIFICACAO']),
  data: isoDateSchema,
  quantidade: positiveMoneySchema,
  precoUnitario: nonNegativeMoneySchema,
}).refine(data => data.tipo === 'BONIFICACAO' || data.precoUnitario > 0, {
  path: ['precoUnitario'], message: 'Preço unitário deve ser positivo',
});

export const orcamentoSchema = z.object({
  mes: z.number().int().min(1).max(12),
  ano: z.number().int().min(2000).max(2200),
  categorias: z.array(z.object({ categoriaId: positiveIdSchema, valorLimite: positiveMoneySchema }))
    .min(1, 'Adicione pelo menos uma categoria com valor limite'),
});

export const faturaPagamentoSchema = (saldoRestante: number) => pagamentoSchema.refine(
  data => data.valor <= saldoRestante,
  { path: ['valor'], message: 'Valor não pode superar o saldo restante' },
);

export const onboardingContaSchema = z.object({
  nome: requiredTextSchema('Nome', 100),
  tipo: z.enum(['CREDITO', 'DEBITO', 'DINHEIRO', 'POUPANCA']),
  limiteTotal: nonNegativeMoneySchema,
});
export const onboardingRendaSchema = z.object({
  nome: requiredTextSchema('Nome', 100),
  valor: positiveMoneySchema,
  diaVencimento: dayOfMonthSchema,
});
export const onboardingCategoriasSchema = z.array(z.string()).min(1, 'Selecione pelo menos uma categoria');
export const onboardingMetaSchema = z.object({
  nome: requiredTextSchema('Nome', 100),
  valorTotal: positiveMoneySchema,
  valorMensal: nonNegativeMoneySchema,
  dataPrevista: z.preprocess(emptyToUndefined, isoDateSchema.optional()),
});

export type TransacaoInput = z.output<typeof transacaoSchema>;
export type CarteiraInput = z.output<typeof carteiraSchema>;
export type CategoriaInput = z.output<typeof categoriaSchema>;
export type ContaFixaInput = z.output<typeof contaFixaSchema>;
export type MetaInput = z.output<typeof metaSchema>;
export type ContaInput = z.output<typeof contaSchema>;
