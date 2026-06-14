export default defineNuxtConfig({
  compatibilityDate: '2026-06-14',
  devtools: { enabled: true },
  ssr: false,

  modules: [
    '@pinia/nuxt',
    '@nuxt/ui'
  ],

  css: ['~/assets/css/main.css'],

  runtimeConfig: {
    public: {
      apiBase: import.meta.env.NUXT_PUBLIC_API_BASE || '/api',
      wsBase: import.meta.env.NUXT_PUBLIC_WS_BASE || 'ws://localhost:8082/ws/chat'
    }
  },

  nitro: {
    devProxy: {
      '/api': {
        target: 'http://localhost:8082/api',
        changeOrigin: true,
        prependPath: false
      }
    }
  }
})