import { Redirect } from 'expo-router';

// Slot da tab central "+": o botão abre o modal Nova Transação no _layout,
// esta rota nunca é exibida — redireciona por segurança (ex: deep link).
export default function Nova() {
  return <Redirect href="/(app)/transacoes" />;
}
