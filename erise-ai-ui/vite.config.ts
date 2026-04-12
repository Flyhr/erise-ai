import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const isDev = mode === 'development' || mode === 'dev'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    build: {
      chunkSizeWarningLimit: 1400,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) {
              return
            }
            if (id.includes('@tinymce/tinymce-vue')) {
              return 'tinymce-vue'
            }
            if (id.includes('tinymce/')) {
              return 'tinymce-core'
            }
            if (id.includes('@tiptap')) {
              return 'tiptap'
            }
            if (id.includes('prosemirror')) {
              return 'prosemirror'
            }
            if (id.includes('echarts')) {
              return 'echarts'
            }
            if (id.includes('zrender')) {
              return 'zrender'
            }
            if (id.includes('element-plus') || id.includes('@element-plus')) {
              return 'element-plus'
            }
            if (id.includes('html2canvas')) {
              return 'html2canvas'
            }
            if (id.includes('jspdf')) {
              return 'jspdf'
            }
            if (id.includes('markdown-it')) {
              return 'markdown-it'
            }
            if (id.includes('turndown')) {
              return 'turndown'
            }
            return 'vendor'
          },
        },
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
