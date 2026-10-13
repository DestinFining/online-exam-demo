import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [
    vue(),
    /**
     * Register the Element Plus components used in templates on demand instead
     * of shipping the whole library through `app.use(ElementPlus)`. The
     * resolver also imports each component's own stylesheet, so the global
     * `element-plus/dist/index.css` import is gone (see src/main.ts).
     */
    Components({ resolvers: [ElementPlusResolver({ importStyle: 'css' })], dts: false }),
  ],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  build: {
    rollupOptions: {
      output: {
        /**
         * Keep the third party libraries in their own long lived chunks: a code
         * change then only invalidates the small application chunk. ECharts is
         * additionally behind the lazy statistics routes, so it leaves the
         * initial payload entirely.
         */
        manualChunks(id: string) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('echarts') || id.includes('zrender')) return 'vendor-echarts'
          if (id.includes('element-plus')) return 'vendor-element-plus'
          if (id.includes('vue-router') || id.includes('pinia') || id.includes('@vue/')) return 'vendor-vue'
          return 'vendor'
        },
      },
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/actuator': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
