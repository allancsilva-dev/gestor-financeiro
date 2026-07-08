import api from './api';

export interface OnboardingStatus {
  onboardingCompleto: boolean;
}

export const onboardingService = {
  getStatus: async (): Promise<OnboardingStatus> => {
    const { data } = await api.get<OnboardingStatus>('/v1/onboarding/status');
    return data;
  },

  completar: async (): Promise<OnboardingStatus> => {
    const { data } = await api.post<OnboardingStatus>('/v1/onboarding/completar');
    return data;
  },
};
