/**
 * Run from the repository root. Starts nothing by itself — delegates to
 * frontend `npm run screenshots`, which uses Playwright webServer to boot
 * API + Vite when they are not already up.
 */
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const frontend = path.join(root, 'frontend')
const result = spawnSync('npm', ['run', 'screenshots'], {
  cwd: frontend,
  stdio: 'inherit',
  shell: true,
  env: process.env,
})
process.exit(result.status ?? 1)
