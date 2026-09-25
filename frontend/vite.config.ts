/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const defaultProxyTarget = mode === 'review' ? 'http://127.0.0.1:8001' : 'http://127.0.0.1:8000'

  return {
    plugins: [react(), tailwindcss(), ...(mode === 'switch-prototype' ? [{
      name: 'throwaway-switch-prototype-isolation',
      configureServer(server: import('vite').ViteDevServer) {
        server.middlewares.use('/api', (request, response) => {
          response.setHeader('Content-Type', 'application/json')
          if (request.method === 'GET' && request.url === '/health') {
            response.end(JSON.stringify({ today: '2026-09-25' }))
          } else {
            response.statusCode = 403
            response.end(JSON.stringify({ detail: 'Prototype uses in-memory sample data only.' }))
          }
        })
      },
    }] : [])],
    server: {
      port: 5174,
      strictPort: true,
      proxy: {
        '/api': {
          target: env.VITE_API_PROXY_TARGET || defaultProxyTarget,
          changeOrigin: true,
          rewrite: (path: string) => path.replace(/^\/api/, ''),
        },
      },
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
      exclude: ['**/node_modules/**', '**/e2e/**', '**/dist/**'],
    },
  }
})
