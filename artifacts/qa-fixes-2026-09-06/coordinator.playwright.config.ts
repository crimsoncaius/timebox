import original from '../../frontend/playwright.config'
import { fileURLToPath } from 'node:url'

export default {
  ...original,
  testDir: fileURLToPath(new URL('../../frontend/e2e', import.meta.url)),
  outputDir: fileURLToPath(new URL('./e2e-results', import.meta.url)),
  reporter: 'list',
  webServer: (original.webServer as Array<Record<string, unknown>>).map((server, index) => ({
    ...server,
    reuseExistingServer: false,
    ...(index === 0 ? {env: {
      ...(server.env as Record<string, string>),
      DATABASE_URL: 'sqlite:///C:/Users/Caius/Desktop/timebox/artifacts/qa-fixes-2026-09-06/coordinator-e2e.sqlite',
    }} : {}),
  })),
}
