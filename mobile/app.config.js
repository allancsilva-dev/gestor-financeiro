module.exports = ({ config }) => {
  const sentryBuildConfigured = Boolean(
    process.env.SENTRY_AUTH_TOKEN
    && process.env.SENTRY_ORG
    && process.env.SENTRY_PROJECT,
  );

  return {
    ...config,
    plugins: [
      ...(config.plugins || []),
      ...(sentryBuildConfigured ? [[
        '@sentry/react-native/expo',
        {
          organization: process.env.SENTRY_ORG,
          project: process.env.SENTRY_PROJECT,
        },
      ]] : []),
    ],
    extra: {
      ...config.extra,
      apiBaseUrl: process.env.EXPO_PUBLIC_API_BASE_URL
        || config.extra?.apiBaseUrl
        || 'https://financas.nexostech.com.br/api',
      releaseSha: process.env.APP_RELEASE_SHA || 'dev',
      appEnv: process.env.APP_ENV || 'local',
      sentryDsn: process.env.EXPO_PUBLIC_SENTRY_DSN || '',
    },
  };
};
