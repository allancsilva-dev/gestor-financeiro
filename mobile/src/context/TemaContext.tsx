import React, { createContext, useContext, useEffect, useState } from 'react';
import { useColorScheme } from 'react-native';
import { getTemaPreferido, setTemaPreferido, TemaPreferido } from '../store/temaPreferido';

export type Esquema = 'light' | 'dark';

interface TemaContextType {
  /** O que o usuário escolheu em Ajustes. */
  preferencia: TemaPreferido;
  /** O esquema efetivo, já resolvido contra o SO quando a escolha é `sistema`. */
  esquema: Esquema;
  setPreferencia: (tema: TemaPreferido) => Promise<void>;
}

const TemaContext = createContext<TemaContextType | undefined>(undefined);

export const TemaProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const doSistema = useColorScheme();
  const [preferencia, setPreferenciaState] = useState<TemaPreferido>('sistema');

  // Até carregar do SecureStore vale `sistema` — é o comportamento anterior, então
  // o primeiro frame não pisca num tema que o usuário não escolheu.
  useEffect(() => {
    let vivo = true;
    getTemaPreferido().then(tema => { if (vivo) setPreferenciaState(tema); });
    return () => { vivo = false; };
  }, []);

  const setPreferencia = async (tema: TemaPreferido) => {
    setPreferenciaState(tema);
    await setTemaPreferido(tema);
  };

  const esquema: Esquema =
    preferencia === 'sistema' ? (doSistema === 'dark' ? 'dark' : 'light') :
    preferencia === 'escuro' ? 'dark' : 'light';

  return (
    <TemaContext.Provider value={{ preferencia, esquema, setPreferencia }}>
      {children}
    </TemaContext.Provider>
  );
};

/**
 * Sem provider acima (componente renderizado solto em teste), cai no SO — o
 * comportamento que o app inteiro tinha antes da escolha de tema existir.
 */
export const useTemaOpcional = (): TemaContextType | undefined => useContext(TemaContext);

export const useTema = (): TemaContextType => {
  const ctx = useContext(TemaContext);
  if (!ctx) throw new Error('useTema precisa de TemaProvider');
  return ctx;
};
