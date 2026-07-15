import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setupTests.ts',
    // e2e/ é do Playwright (scripts/e2e-web.sh), não do Vitest
    exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
  },
})
