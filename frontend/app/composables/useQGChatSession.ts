const tokenKey = 'qgchat.token'

export const qgChatSession = {
  getToken() {
    if (typeof window === 'undefined') return null

    try {
      return localStorage.getItem(tokenKey)
    } catch {
      return null
    }
  },

  setToken(token: string) {
    if (typeof window === 'undefined') return

    localStorage.setItem(tokenKey, token)
  },

  clearToken() {
    if (typeof window === 'undefined') return

    localStorage.removeItem(tokenKey)
  },

  goTo(path: string) {
    if (typeof window === 'undefined') return

    window.location.assign(path)
  }
}
