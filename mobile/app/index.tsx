import React from 'react';
import { Redirect } from 'expo-router';
import { View, ActivityIndicator } from 'react-native';
import { useAuth } from '../src/context/AuthContext';
import { useTheme } from '../src/theme';

export default function Index() {
  const { isAuthenticated, isLoading, needsOnboarding } = useAuth();
  const colors = useTheme();

  if (isLoading) {
    return (
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bg }}>
        <ActivityIndicator size="large" color={colors.brand} />
      </View>
    );
  }

  if (isAuthenticated && needsOnboarding) return <Redirect href="/onboarding" />;
  if (isAuthenticated) return <Redirect href="/(app)/" />;
  return <Redirect href="/(auth)/login" />;
}
