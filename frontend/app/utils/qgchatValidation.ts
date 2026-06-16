const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const usernamePattern = /^[A-Za-z0-9_]{3,50}$/

export const isValidEmail = (value: string) => emailPattern.test(value.trim())

export const isValidUsername = (value: string) => usernamePattern.test(value.trim())

export const isValidOptionalUrl = (value: string) => {
  const normalized = value.trim()
  if (!normalized) return true

  try {
    const url = new URL(normalized)
    return ['http:', 'https:'].includes(url.protocol)
  } catch {
    return false
  }
}

export const uniqueCsvItems = (value: string) =>
  Array.from(new Set(
    value
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
  ))
