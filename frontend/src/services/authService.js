import api from './api.js'
import {
  AUTH_ERROR_CODES,
  AuthRequestError,
  normalizeAuthError,
} from './authErrors.js'

export async function loginUser(credentials) {
  let data

  try {
    const response = await api.post('/auth/login', credentials)
    data = response.data
  } catch (error) {
    throw normalizeAuthError(
      error,
      AUTH_ERROR_CODES.loginFailed,
      'No pudimos iniciar sesión.',
    )
  }

  if (!data?.token) {
    throw new AuthRequestError(
      AUTH_ERROR_CODES.loginFailed,
      'El servidor no devolvió una sesión válida.',
    )
  }

  return data
}

export async function registerUser(userData) {
  try {
    const { data } = await api.post('/auth/register', userData)
    return data
  } catch (error) {
    throw normalizeAuthError(
      error,
      AUTH_ERROR_CODES.registrationFailed,
      'No pudimos crear la cuenta.',
    )
  }
}
