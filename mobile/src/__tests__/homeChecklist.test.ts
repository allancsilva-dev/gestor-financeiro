jest.mock('expo-secure-store', () => ({
  setItemAsync: jest.fn(),
  getItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

import * as SecureStore from 'expo-secure-store';
import { dismissHomeChecklist, isHomeChecklistDismissed } from '../store/homeChecklist';

describe('checklist da home (PR-F3-10): dispensa fica só no dispositivo', () => {
  beforeEach(() => jest.clearAllMocks());

  it('persiste a dispensa', async () => {
    await dismissHomeChecklist();
    expect(SecureStore.setItemAsync).toHaveBeenCalledWith('homeChecklistDismissed', '1');
  });

  it('lê o estado de dispensa', async () => {
    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue('1');
    await expect(isHomeChecklistDismissed()).resolves.toBe(true);
    (SecureStore.getItemAsync as jest.Mock).mockResolvedValue(null);
    await expect(isHomeChecklistDismissed()).resolves.toBe(false);
  });
});
