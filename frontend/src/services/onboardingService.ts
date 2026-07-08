import api from './api';

export interface OnboardingStatus {
  onboardingCompleto: boolean;
}

export const onboardingService = {
  getStatus: async (): Promise<OnboardingStatus> => {
    const response = await api.get<OnboardingStatus>('/onboarding/status');
    return response.data;
  },

  completar: async (): Promise<OnboardingStatus> => {
    const response = await api.post<OnboardingStatus>('/onboarding/completar');
    return response.data;
  },
};
