import { sourceUrls } from './app/config/sourceUrls'

export default defineNuxtConfig({
  ssr: false,
  spaLoadingTemplate: './spa-loading-template.html',
  compatibilityDate: '2026-06-14',
  devtools: { enabled: false },
  modules: [
    '@pinia/nuxt',
    '@nuxt/ui'
  ],

  css: ['~/assets/css/main.css'],

  runtimeConfig: {
    public: {
      apiBase: sourceUrls.apiBase,
      wsBase: sourceUrls.wsBase
    }
  }
})
