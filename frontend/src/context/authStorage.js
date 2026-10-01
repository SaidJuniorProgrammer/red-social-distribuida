const AUTH_STORAGE_KEY = 'pachyweb.auth'

function isValidSession(value) {
  return (
    value !== null &&
    typeof value === 'object' &&
    typeof value.token === 'string' &&
    value.token.length > 0
  )
}

export function readSession() {
  try {
    const storedValue = localStorage.getItem(AUTH_STORAGE_KEY)
    if (!storedValue) return null

    const session = JSON.parse(storedValue)
    return isValidSession(session) ? session : null
  } catch {
    return null
  }
}

export function writeSession(session) {
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session))
}

export function clearSession() {
  localStorage.removeItem(AUTH_STORAGE_KEY)
}

export { AUTH_STORAGE_KEY }
