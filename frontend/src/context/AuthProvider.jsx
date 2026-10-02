import { useEffect, useMemo, useState } from 'react'
import AuthContext from './authContext.js'
import {
  clearSession,
  getSessionExpiration,
  readSession,
  subscribeToSessionChanges,
  writeSession,
} from './authStorage.js'
import { loginUser, registerUser } from '../services/authService.js'

const MAX_TIMEOUT_DELAY = 2_147_483_647

function AuthProvider({ children }) {
  const [session, setSession] = useState(readSession)

  useEffect(() => subscribeToSessionChanges(setSession), [])

  useEffect(() => {
    const expiration = getSessionExpiration(session)
    if (expiration === null) return undefined

    let timeoutId

    const expireSessionWhenNeeded = () => {
      const remainingTime = expiration - Date.now()

      if (remainingTime <= 0) {
        clearSession()
        return
      }

      timeoutId = window.setTimeout(
        expireSessionWhenNeeded,
        Math.min(remainingTime, MAX_TIMEOUT_DELAY),
      )
    }

    expireSessionWhenNeeded()
    return () => window.clearTimeout(timeoutId)
  }, [session])

  const login = async (credentials) => {
    const { token } = await loginUser(credentials)
    const nextSession = {
      token,
      user: { username: credentials.username },
    }

    writeSession(nextSession)
    setSession(nextSession)
    return nextSession
  }

  const register = (userData) => registerUser(userData)

  const logout = () => {
    setSession(null)
    clearSession()
  }

  const value = useMemo(
    () => ({
      isAuthenticated: Boolean(session?.token),
      login,
      logout,
      register,
      token: session?.token ?? null,
      user: session?.user ?? null,
    }),
    [session],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export default AuthProvider
