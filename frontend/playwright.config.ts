import { defineConfig, devices } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const backendDir = path.join(__dirname, '..', 'backend')

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  use: {
    ...devices['Desktop Chrome'],
    baseURL: 'http://127.0.0.1:5174',
  },
  webServer: [
    {
      command: 'uv run uvicorn app.main:app --host 127.0.0.1 --port 8000',
      cwd: backendDir,
      env: {
        ...process.env,
        DATABASE_URL: 'sqlite:///./e2e.sqlite',
        APP_TIMEZONE: 'UTC',
        CORS_ORIGINS: '*',
        AUTO_CREATE_TABLES: '1',
      },
      url: 'http://127.0.0.1:8000/health',
      timeout: 120_000,
      reuseExistingServer: !process.env.CI,
    },
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 5174',
      cwd: __dirname,
      url: 'http://127.0.0.1:5174/',
      timeout: 120_000,
      reuseExistingServer: !process.env.CI,
    },
  ],
})
