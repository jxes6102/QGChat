export default defineNuxtConfig({
  compatibilityDate: '2026-06-14',
  devtools: { enabled: false },
  modules: [
    '@pinia/nuxt',
    '@nuxt/ui'
  ],

  css: ['~/assets/css/main.css'],

  runtimeConfig: {
    public: {
      apiBase: import.meta.env.NUXT_PUBLIC_API_BASE || 'http://localhost:8082/api',
      wsBase: import.meta.env.NUXT_PUBLIC_WS_BASE || 'ws://localhost:8082/ws/chat'
    }
  }
})
