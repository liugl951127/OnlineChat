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
          additionalData: ''
        }
      }
    },
    // v2.2.92: 浏览器兼容目标 (与 .browserslistrc 一致)
    // 兼容国内常用浏览器: 360/QQ/搜狗 (Chrome 78+ 内核) / Safari 13+ / Firefox 68+
    // Element Plus 2.7+ 用 ES2020 特性, esbuild 自动降级
    build: {
      outDir: 'dist',
      sourcemap: false,
      target: ['chrome78', 'firefox68', 'safari13', 'edge88'],
      cssTarget: ['chrome78', 'firefox68', 'safari13', 'edge88'],
      chunkSizeWarningLimit: 1500,
      rollupOptions: {
        output: {
          // 拆 chunk: Element Plus / Vue 单独 vendor
          // 老浏览器首次加载快 (避免单 chunk 太大)
          // v2.2.92: vite 8/rolldown 要求 manualChunks 是 function
          manualChunks(id) {
            if (id.includes('node_modules/element-plus/')) return 'element-plus'
            if (id.includes('node_modules/vue') ||
                id.includes('node_modules/vue-router') ||
                id.includes('node_modules/pinia')) return 'vue-vendor'
          }
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
            if (p.endsWith('/callback-json') || p.endsWith('/silent-login') ||
                p.endsWith('/login') || p.endsWith('/register') || p.endsWith('/verify/phone') ||
                p.includes('/authorize-json') || p.includes('/js-sign')) {
              return undefined  // 走 proxy
            }
            if ((p.endsWith('/authorize') || p.endsWith('/callback')) &&
                !p.endsWith('/callback-json')) {
              return '/index.html'  // 返回 SPA 入口
            }
            return undefined
          }
        }
      }
    },
    test: {
      environment: 'jsdom',
      globals: true
    }
  }
})
