import { useQuery } from '@tanstack/react-query';
import capacidadesService, { CAPACIDADES_DESLIGADAS, Capacidades } from '../services/capacidadesService';

/**
 * O que o servidor tem ligado agora.
 *
 * Antes isto era `process.env.EXPO_PUBLIC_ASSISTANT_TEXT_ENABLED`, inlinado pelo Metro em build
 * time: ligar o assistente no servidor não bastava, era preciso publicar app novo. Agora quem
 * decide é o backend, e o app só precisa perguntar.
 *
 * Enquanto carrega ou se a chamada falha, devolve tudo desligado — a mesma postura fail-closed do
 * ADR-0017. Oferecer uma rota que responderia 404 é pior do que escondê-la por um instante.
 */
export function useCapacidades(): Capacidades {
  const { data } = useQuery({
    queryKey: ['capacidades'],
    queryFn: () => capacidadesService.obter(),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });
  return data ?? CAPACIDADES_DESLIGADAS;
}
