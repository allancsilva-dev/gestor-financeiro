import { 
  Home, 
  Zap, 
  Film, 
  Heart, 
  Scissors, 
  BookOpen, 
  Wifi, 
  ShoppingBag, 
  PiggyBank, 
  Dog, 
  Gift, 
  Users,
  Car,
  Utensils,
  ShoppingCart,
  Plane,
  Coffee,
  Phone,
  Briefcase,
  LucideIcon
} from 'lucide-react';

export interface CategoriaPreDefinida {
  id: string;
  nome: string;
  icon: LucideIcon;
  cor: string;
  corBg: string;
  corBorder: string;
}

export const CATEGORIAS_PRE_DEFINIDAS: CategoriaPreDefinida[] = [
  { 
    id: 'moradia', 
    nome: 'Moradia', 
    icon: Home, 
    cor: '#3B82F6',
    corBg: '#DBEAFE',
    corBorder: '#93C5FD'
  },
  { 
    id: 'alimentacao', 
    nome: 'Alimentação', 
    icon: Utensils, 
    cor: '#EF4444',
    corBg: '#FEE2E2',
    corBorder: '#FCA5A5'
  },
  { 
    id: 'mercado', 
    nome: 'Mercado', 
    icon: ShoppingCart, 
    cor: '#10B981',
    corBg: '#D1FAE5',
    corBorder: '#6EE7B7'
  },
  { 
    id: 'transporte', 
    nome: 'Transporte', 
    icon: Car, 
    cor: '#8B5CF6',
    corBg: '#EDE9FE',
    corBorder: '#C4B5FD'
  },
  { 
    id: 'contas', 
    nome: 'Contas da Casa', 
    icon: Zap, 
    cor: '#F59E0B',
    corBg: '#FEF3C7',
    corBorder: '#FCD34D'
  },
  { 
    id: 'lazer', 
    nome: 'Lazer', 
    icon: Film, 
    cor: '#A855F7',
    corBg: '#F3E8FF',
    corBorder: '#D8B4FE'
  },
  { 
    id: 'saude', 
    nome: 'Saúde', 
    icon: Heart, 
    cor: '#EF4444',
    corBg: '#FEE2E2',
    corBorder: '#FCA5A5'
  },
  { 
    id: 'cuidados', 
    nome: 'Cuidados Pessoais', 
    icon: Scissors, 
    cor: '#EC4899',
    corBg: '#FCE7F3',
    corBorder: '#F9A8D4'
  },
  { 
    id: 'educacao', 
    nome: 'Educação', 
    icon: BookOpen, 
    cor: '#6366F1',
    corBg: '#E0E7FF',
    corBorder: '#A5B4FC'
  },
  { 
    id: 'assinaturas', 
    nome: 'Assinaturas', 
    icon: Wifi, 
    cor: '#06B6D4',
    corBg: '#CFFAFE',
    corBorder: '#67E8F9'
  },
  { 
    id: 'vestuario', 
    nome: 'Vestuário', 
    icon: ShoppingBag, 
    cor: '#10B981',
    corBg: '#D1FAE5',
    corBorder: '#6EE7B7'
  },
  { 
    id: 'investimentos', 
    nome: 'Investimentos', 
    icon: PiggyBank, 
    cor: '#059669',
    corBg: '#D1FAE5',
    corBorder: '#6EE7B7'
  },
  { 
    id: 'animais', 
    nome: 'Pets', 
    icon: Dog, 
    cor: '#F97316',
    corBg: '#FFEDD5',
    corBorder: '#FDBA74'
  },
  { 
    id: 'presentes', 
    nome: 'Presentes', 
    icon: Gift, 
    cor: '#F43F5E',
    corBg: '#FFE4E6',
    corBorder: '#FDA4AF'
  },
  { 
    id: 'viagem', 
    nome: 'Viagem', 
    icon: Plane, 
    cor: '#3B82F6',
    corBg: '#DBEAFE',
    corBorder: '#93C5FD'
  },
  { 
    id: 'cafe', 
    nome: 'Cafés e Lanches', 
    icon: Coffee, 
    cor: '#92400E',
    corBg: '#FEF3C7',
    corBorder: '#FCD34D'
  },
  { 
    id: 'telefone', 
    nome: 'Telefone/Internet', 
    icon: Phone, 
    cor: '#06B6D4',
    corBg: '#CFFAFE',
    corBorder: '#67E8F9'
  },
  { 
    id: 'trabalho', 
    nome: 'Trabalho', 
    icon: Briefcase, 
    cor: '#6366F1',
    corBg: '#E0E7FF',
    corBorder: '#A5B4FC'
  },
  { 
    id: 'dependentes', 
    nome: 'Dependentes', 
    icon: Users, 
    cor: '#8B5CF6',
    corBg: '#EDE9FE',
    corBorder: '#C4B5FD'
  },
];
