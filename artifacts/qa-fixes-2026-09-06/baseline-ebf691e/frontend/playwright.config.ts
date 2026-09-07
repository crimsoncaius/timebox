import { defineConfig, devices } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const backendDir = path.join(__dirname, '..', 'backend')
const viteBin = path.join(__dirname, 'node_modules', 'vite', 'bin', 'vite.js')
const apiPort = 18001
const webPort = 15174

export default defineConfig({
  testDir: './e2e',
  // The suite intentionally shares one SQLite database and seeds common task types.
  // Serial workers keep those integration fixtures deterministic.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  use: {
    ...devices['Desktop Chrome'],
    baseURL: `http://127.0.0.1:${webPort}`,
  },
  webServer: [
    {
      command: `uv run uvicorn app.main:app --host 127.0.0.1 --port ${apiPort}`,
      cwd: backendDir,
      env: {
        ...process.env,
        DATABASE_URL: 'sqlite:///./e2e.sqlite',
        APP_TIMEZONE: 'UTC',
        CORS_ORIGINS: '*',
        AUTO_CREATE_TABLES: '1',
      },
      url: `http://127.0.0.1:${apiPort}/health`,
      timeout: 120_000,
      reuseExistingServer: !process.env.CI,
    },
    {
      command: `"${process.execPath}" "${viteBin}" --host 127.0.0.1 --port ${webPort}`,
      cwd: __dirname,
      env: {
        ...process.env,
        VITE_API_PROXY_TARGET: `http://127.0.0.1:${apiPort}`,
      },
      url: `http://127.0.0.1:${webPort}/`,
      timeout: 120_000,
      reuseExistingServer: !process.env.CI,
    },
  ],
})
