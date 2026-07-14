import Constants from 'expo-constants';
import * as Sentry from '@sentry/react-native';

type ObservabilityExtra = {
  sentryDsn?: string;
  releaseSha?: string;
  appEnv?: string;
};

const extra = (Constants.expoConfig?.extra || {}) as ObservabilityExtra;

Sentry.init({
  dsn: extra.sentryDsn || undefined,
  enabled: Boolean(extra.sentryDsn) && extra.appEnv !== 'local',
  release: extra.releaseSha || 'dev',
  environment: extra.appEnv || 'local',
  sendDefaultPii: false,
  tracesSampleRate: 0,
  enableAutoSessionTracking: true,
  beforeSend(event) {
    delete event.user;
    delete event.request;
    delete event.extra;
    if (event.breadcrumbs) {
      event.breadcrumbs = event.breadcrumbs.map(({ data: _data, ...breadcrumb }) => breadcrumb);
    }
    return event;
  },
});

export { Sentry };
