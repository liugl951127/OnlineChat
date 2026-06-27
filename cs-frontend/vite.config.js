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
          target: env.VITE_API_BASE || 'http://127.0.0.1:9000',
          changeOrigin: true,
          rewrite: p => p.replace(/^\/api/, '/api')
        },
        '/auth': {
          target: env.VITE_API_BASE || 'http://127.0.0.1:9000',
          changeOrigin: true,
          // 只代理 callback-json / silent / login 等业务端点，
          // authorize/callback 走前端路由处理（避免循环 302）
          bypass: (req) => {
            const p = req.url.split('?')[0]
            // 代理：后端 callback-json / silent / login 等
            if (p.endsWith('/callback-json') || p.endsWith('/silent-login') ||
                p.endsWith('/login') || p.endsWith('/register') || p.endsWith('/verify/phone') ||
                p.includes('/authorize-json') || p.includes('/js-sign')) {
              return undefined  // 走 proxy
            }
            // 不代理：authorize / callback（这些走前端 vue-router）
            // 注意：/callback-json 已经上面拦截，这里严格匹配 /callback
            if ((p.endsWith('/authorize') || p.endsWith('/callback')) &&
                !p.endsWith('/callback-json')) {
              return '/index.html'  // 返回 SPA 入口，让 vue-router 处理
            }
            return undefined  // 默认走 proxy
          }
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