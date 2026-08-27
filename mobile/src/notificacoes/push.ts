import { Platform } from 'react-native';
import Constants from 'expo-constants';
import * as Notifications from 'expo-notifications';
import notificacaoService from '../services/notificacaoService';

/**
 * Registro do aparelho para receber aviso por push.
 *
 * <p>Regras que este módulo carrega:</p>
 * - **Nunca bloqueia o app.** Permissão negada, emulador sem serviço de push ou projeto sem
 *   `projectId` fazem o registro desistir em silêncio: o app continua funcionando e os avisos
 *   seguem chegando na caixa in-app.
 * - **Não insiste em permissão.** Pede uma vez; se o usuário negou, respeita — pedir de novo a
 *   cada abertura é o caminho mais curto para desinstalação.
 * - **Revoga na saída.** Sair da conta desliga o aviso naquele aparelho; caso contrário o próximo
 *   dono da tela receberia notificação da vida financeira de outra pessoa.
 */

/** Emulador e simulador não têm serviço de push; tentar ali só gera erro barulhento. */
const ehDispositivoReal = (): boolean => Constants.isDevice ?? true;

const projectId = (): string | undefined =>
  (Constants.expoConfig?.extra as { eas?: { projectId?: string } } | undefined)?.eas?.projectId
  ?? (Constants as unknown as { easConfig?: { projectId?: string } }).easConfig?.projectId;

const plataforma = (): 'IOS' | 'ANDROID' | null => {
  if (Platform.OS === 'ios') return 'IOS';
  if (Platform.OS === 'android') return 'ANDROID';
  return null;
};

/** Android exige canal declarado, senão o aviso chega sem som e sem prioridade. */
const prepararCanalAndroid = async () => {
  if (Platform.OS !== 'android') return;
  await Notifications.setNotificationChannelAsync('avisos', {
    name: 'Avisos financeiros',
    importance: Notifications.AndroidImportance.DEFAULT,
    lockscreenVisibility: Notifications.AndroidNotificationVisibility.PRIVATE,
  });
};

let tokenRegistrado: string | null = null;

/** Registra o aparelho e devolve o token, ou `null` quando não dá para registrar. */
export const registrarDispositivoParaPush = async (): Promise<string | null> => {
  const destino = plataforma();
  if (!destino || !ehDispositivoReal()) return null;

  try {
    await prepararCanalAndroid();

    const atual = await Notifications.getPermissionsAsync();
    let concedida = atual.granted;
    if (!concedida && atual.canAskAgain) {
      const pedida = await Notifications.requestPermissionsAsync();
      concedida = pedida.granted;
    }
    if (!concedida) return null;

    const id = projectId();
    const token = await Notifications.getExpoPushTokenAsync(id ? { projectId: id } : undefined);
    if (!token?.data) return null;

    await notificacaoService.registrarDispositivo(token.data, destino);
    tokenRegistrado = token.data;
    return token.data;
  } catch {
    // Falta de projectId, ausência de serviço de push ou rede fora: nada disso justifica
    // atrapalhar quem só quer usar o app.
    return null;
  }
};

/** Desliga o aviso neste aparelho. Chamado na saída da conta. */
export const revogarDispositivoDePush = async (): Promise<void> => {
  const token = tokenRegistrado;
  tokenRegistrado = null;
  if (!token) return;
  try {
    await notificacaoService.revogarDispositivo(token);
  } catch {
    // Sair da conta não pode falhar porque o servidor não respondeu ao revogar.
  }
};
