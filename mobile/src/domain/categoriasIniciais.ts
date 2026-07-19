import { CategoriaRequest } from '../types';
import { CATEGORY_COLORS } from '../utils/format';

// Pacote inicial de categorias (PR-F3-10): mesmo conjunto que o antigo wizard
// de onboarding oferecia — agora criado sob demanda, com um toque, no primeiro
// lançamento sem categorias.
export const CATEGORIAS_INICIAIS: CategoriaRequest[] = [
  { nome: 'Alimentação', cor: CATEGORY_COLORS[2], icone: '🍔' },
  { nome: 'Transporte', cor: CATEGORY_COLORS[3], icone: '🚗' },
  { nome: 'Moradia', cor: CATEGORY_COLORS[4], icone: '🏠' },
  { nome: 'Saúde', cor: CATEGORY_COLORS[5], icone: '🏥' },
  { nome: 'Educação', cor: CATEGORY_COLORS[6], icone: '📚' },
  { nome: 'Lazer', cor: CATEGORY_COLORS[1], icone: '🎮' },
  { nome: 'Vestuário', cor: CATEGORY_COLORS[0], icone: '👕' },
  { nome: 'Assinaturas', cor: CATEGORY_COLORS[7], icone: '📱' },
  { nome: 'Outros', cor: CATEGORY_COLORS[8], icone: '📦' },
];
