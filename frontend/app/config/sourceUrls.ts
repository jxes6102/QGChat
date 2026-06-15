export interface SourceUrlConfig {
  apiBase: string
  wsBase: string
}

export const sourceUrlDefaults = {
  apiBase: 'http://localhost:8082/api',
  wsBase: 'ws://localhost:8082/ws/chat'
} satisfies SourceUrlConfig

type RuntimeGlobal = typeof globalThis & {
  process?: {
    env?: Record<string, string | undefined>
  }
}

const env = (globalThis as RuntimeGlobal).process?.env || {}

export const sourceUrls: SourceUrlConfig = {
  apiBase: env.NUXT_PUBLIC_API_BASE || sourceUrlDefaults.apiBase,
  wsBase: env.NUXT_PUBLIC_WS_BASE || sourceUrlDefaults.wsBase
}
