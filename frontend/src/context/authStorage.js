const AUTH_STORAGE_KEY = 'pachyweb.auth'
const sessionListeners = new Set()

let invalidatedStoredValue = null

function isValidSession(value) {
  return (
    value !== null &&
    typeof value === 'object' &&
    typeof value.token === 'string' &&
    value.token.length > 0
  )
}

function decodeJwtPayload(token) {
  const payload = token.split('.')[1]
  if (!payload) return null

  try {
    const normalizedPayload = payload.replaceAll('-', '+').replaceAll('_', '/')
    const paddingLength = (4 - (normalizedPayload.length % 4)) % 4
    const paddedPayload = normalizedPayload.padEnd(
      normalizedPayload.length + paddingLength,
      '=',
    )

    return JSON.parse(atob(paddedPayload))
  } catch {
    return null
  }
}

function notifySessionChange(session) {
  sessionListeners.forEach((listener) => listener(session))
}

export function getSessionExpiration(session) {
  if (!isValidSession(session)) return null

  const expiration = decodeJwtPayload(session.token)?.exp
  return Number.isFinite(expiration) ? expiration * 1000 : null
}

export function isSessionExpired(session, currentTime = Date.now()) {
  const expiration = getSessionExpiration(session)
  return expiration !== null && expiration <= currentTime
}

export function readSession() {
  try {
    const storedValue = localStorage.getItem(AUTH_STORAGE_KEY)
    if (!storedValue || storedValue === invalidatedStoredValue) return null

    const session = JSON.parse(storedValue)
    if (!isValidSession(session)) return null

    if (isSessionExpired(session)) {
      clearSession()
      return null
    }

    return session
  } catch {
    return null
  }
}

export function writeSession(session) {
  invalidatedStoredValue = null

  try {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session))
    return true
  } catch {
    return false
  }
}

export function clearSession() {
  try {
    invalidatedStoredValue = localStorage.getItem(AUTH_STORAGE_KEY)
  } catch {
    invalidatedStoredValue = null
  }

  let sessionWasRemoved

  try {
    localStorage.removeItem(AUTH_STORAGE_KEY)
    sessionWasRemoved = true
  } catch {
    sessionWasRemoved = false
  }

  notifySessionChange(null)
  return sessionWasRemoved
}

export function subscribeToSessionChanges(listener) {
  sessionListeners.add(listener)
  return () => sessionListeners.delete(listener)
}

export { AUTH_STORAGE_KEY }
