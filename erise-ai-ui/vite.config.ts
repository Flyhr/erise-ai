import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const isDev = mode === 'development'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: 5173,
      strictPort: true,
      proxy: isDev
        ? {
            '/api': {
              target: process.env.VITE_DEV_PROXY_TARGET || 'http://localhost:8088',
              changeOrigin: true,
            },
            '/actuator': {
              target: process.env.VITE_DEV_PROXY_TARGET || 'http://localhost:8088',
              changeOrigin: true,
            },
          }
        : undefined,
    },
    test: {
      environment: 'jsdom',
      globals: true,
      exclude: ['tests/e2e/**', 'node_modules/**'],
    },
  }
})