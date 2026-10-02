export const AUTH_ERROR_CODES = Object.freeze({
  connection: 'CONNECTION_ERROR',
  invalidCredentials: 'INVALID_CREDENTIALS',
  invalidRegistration: 'INVALID_REGISTRATION',
  usernameExists: 'USERNAME_ALREADY_EXISTS',
  emailExists: 'EMAIL_ALREADY_EXISTS',
  loginFailed: 'LOGIN_FAILED',
  registrationFailed: 'REGISTRATION_FAILED',
})

export class AuthRequestError extends Error {
  constructor(code, message, { field = null, status = null } = {}) {
    super(message)
    this.name = 'AuthRequestError'
    this.code = code
    this.field = field
    this.status = status
  }
}

export function normalizeAuthError(error, fallbackCode, fallbackMessage) {
  const response = error?.response

  if (!response) {
    return new AuthRequestError(
      AUTH_ERROR_CODES.connection,
      'No pudimos conectar con el servidor.',
    )
  }

  const serverError = response.data

  return new AuthRequestError(
    serverError?.code ?? fallbackCode,
    serverError?.message ?? fallbackMessage,
    {
      field: serverError?.field,
      status: response.status,
    },
  )
}

export function getRegistrationFeedback(error) {
  switch (error?.code) {
    case AUTH_ERROR_CODES.usernameExists:
      return {
        field: 'username',
        message: 'Ese nombre de usuario ya está registrado. Prueba con otro.',
      }
    case AUTH_ERROR_CODES.emailExists:
      return {
        field: 'email',
        message: 'Ese correo electrónico ya está registrado. Usa otro o inicia sesión.',
      }
    case AUTH_ERROR_CODES.invalidRegistration:
      return { field: error.field, message: error.message }
    case AUTH_ERROR_CODES.connection:
      return {
        field: null,
        message: 'No pudimos conectar con el servidor. Verifica que el backend esté activo.',
      }
    default:
      return {
        field: null,
        message: 'No pudimos crear la cuenta. Inténtalo nuevamente.',
      }
  }
}

export function getLoginErrorMessage(error) {
  if (error?.code === AUTH_ERROR_CODES.invalidCredentials || error?.status === 401) {
    return 'Usuario o contraseña incorrectos.'
  }

  if (error?.code === AUTH_ERROR_CODES.connection) {
    return 'No pudimos conectar con el servidor. Verifica que el backend esté activo.'
  }

  return 'No pudimos iniciar sesión. Inténtalo nuevamente.'
}
