import { defineConfig, devices } from '@playwright/test';

// Ambiente E2E dedicado: scripts/e2e-web.sh sobe PostgreSQL efêmero + backend (porta 8081)
// e então roda esta suíte; o Vite dev server é iniciado aqui via webServer.
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 60_000,
    env: {
      VITE_API_URL: process.env.VITE_API_URL ?? 'http://localhost:8081/api',
    },
  },
});
