import { useState } from 'react';
import { CATEGORIAS_PRE_DEFINIDAS } from '../data/categoriasPreDefinidas';
import { Plus, Check, X } from 'lucide-react';

interface CategoriaDropdownProps {
  value: string;
  onChange: (categoria: { nome: string; cor: string; icone: string }) => void;
}

export default function CategoriaDropdown({ value, onChange }: CategoriaDropdownProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [showCustomInput, setShowCustomInput] = useState(false);
  const [customNome, setCustomNome] = useState('');

  const handleSelect = (categoria: any) => {
    onChange({
      nome: categoria.nome,
      cor: categoria.cor,
      icone: categoria.id
    });
    setIsOpen(false);
  };

  const handleAddCustom = () => {
    if (customNome.trim()) {
      onChange({
        nome: customNome,
        cor: '#6B7280',
        icone: 'tag'
      });
      setCustomNome('');
      setShowCustomInput(false);
      setIsOpen(false);
    }
  };

  const categoriaAtual = CATEGORIAS_PRE_DEFINIDAS.find(c => c.nome === value);

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="w-full text-left px-4 py-2 border border-gray-300 rounded-lg hover:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all bg-white"
      >
        {value ? (
          <div className="flex items-center gap-2">
            {categoriaAtual && (
              <div 
                className="p-1.5 rounded-lg flex items-center justify-center"
                style={{ backgroundColor: categoriaAtual.corBg }}
              >
                <categoriaAtual.icon className="w-4 h-4" style={{ color: categoriaAtual.cor }} />
              </div>
            )}
            <span className="font-medium text-gray-700">{value}</span>
          </div>
        ) : (
          <span className="text-gray-500">Selecione uma categoria</span>
        )}
      </button>

      {isOpen && (
        <>
          {/* Overlay para fechar ao clicar fora */}
          <div 
            className="fixed inset-0 z-10" 
            onClick={() => setIsOpen(false)}
          ></div>

          <div className="absolute z-20 mt-2 w-full bg-white border border-gray-200 rounded-lg shadow-xl max-h-96 overflow-y-auto">
            <div className="p-2 space-y-1">
              {CATEGORIAS_PRE_DEFINIDAS.map((cat) => {
                const Icon = cat.icon;
                return (
                  <button
                    key={cat.id}
                    type="button"
                    onClick={() => handleSelect(cat)}
                    className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-gray-100 transition-colors ${
                      value === cat.nome ? 'bg-blue-50' : ''
                    }`}
                  >
                    <div 
                      className="p-2 rounded-lg flex items-center justify-center"
                      style={{ backgroundColor: cat.corBg }}
                    >
                      <Icon className="w-5 h-5" style={{ color: cat.cor }} />
                    </div>
                    <span className="text-sm font-medium text-gray-700">{cat.nome}</span>
                  </button>
                );
              })}
              
              <div className="border-t border-gray-200 mt-2 pt-2">
                {!showCustomInput ? (
                  <button
                    type="button"
                    onClick={() => setShowCustomInput(true)}
                    className="w-full flex items-center gap-2 px-3 py-2.5 rounded-lg hover:bg-gray-100 text-blue-600 font-medium"
                  >
                    <Plus className="w-5 h-5" />
                    Criar categoria personalizada
                  </button>
                ) : (
                  <div className="space-y-2">
                    <input
                      type="text"
                      value={customNome}
                      onChange={(e) => setCustomNome(e.target.value)}
                      placeholder="Nome da categoria"
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                      autoFocus
                      onKeyPress={(e) => e.key === 'Enter' && handleAddCustom()}
                    />
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={handleAddCustom}
                        className="flex-1 flex items-center justify-center gap-2 p-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                      >
                        <Check className="w-4 h-4" />
                        Criar
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setShowCustomInput(false);
                          setCustomNome('');
                        }}
                        className="flex-1 flex items-center justify-center gap-2 p-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300"
                      >
                        <X className="w-4 h-4" />
                        Cancelar
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}