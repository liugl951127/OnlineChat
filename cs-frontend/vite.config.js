import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import path from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  return {
    base: '/',
    plugins: [
      vue(),
      AutoImport({ resolvers: [ElementPlusResolver()] }),
      Components({ resolvers: [ElementPlusResolver()] })
    ],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src')
      }
    },
    css: {
      preprocessorOptions: {
        scss: {
          // 启用 SCSS 变量共享（如需）
          additionalData: ''
        }
      }
    },
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: env.VITE_API_BASE || 'http://localhost:9000',
          changeOrigin: true,
          rewrite: p => p.replace(/^\/api/, '/api')
        },
        '/auth': {
          target: env.VITE_API_BASE || 'http://localhost:9000',
          changeOrigin: true
        }
      }
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
      rollupOptions: {
        output: {
          manualChunks: undefined
        }
      },
      chunkSizeWarningLimit: 1500
    },
    test: {
      environment: 'jsdom',
      globals: true
    }
  }
})