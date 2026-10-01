import { useMemo, useState } from 'react'
import AuthContext from './authContext.js'
import { clearSession, readSession, writeSession } from './authStorage.js'
import { loginUser, registerUser } from '../services/authService.js'

function AuthProvider({ children }) {
  const [session, setSession] = useState(readSession)

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
    clearSession()
    setSession(null)
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
