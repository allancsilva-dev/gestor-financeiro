import * as SecureStore from 'expo-secure-store';
import Constants from 'expo-constants';

// Checklist de setup da home (PR-F3-10): opcional e dispensável — a decisão
// de ocultar fica somente no dispositivo, como as demais preferências.
const CHECKLIST_DISMISSED_KEY = 'homeChecklistDismissed';

const isLocalE2E = Constants.expoConfig?.extra?.appEnv === 'local-e2e';
const volatileStore: Record<string, string | undefined> = {};

export const dismissHomeChecklist = async () => {
  if (isLocalE2E) { volatileStore[CHECKLIST_DISMISSED_KEY] = '1'; return; }
  await SecureStore.setItemAsync(CHECKLIST_DISMISSED_KEY, '1');
};

export const isHomeChecklistDismissed = async (): Promise<boolean> => {
  if (isLocalE2E) return volatileStore[CHECKLIST_DISMISSED_KEY] === '1';
  return (await SecureStore.getItemAsync(CHECKLIST_DISMISSED_KEY)) === '1';
};
