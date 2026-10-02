import { afterEach, describe, expect, it, vi } from 'vitest'
import api from '../src/services/api.js'
import { AUTH_ERROR_CODES } from '../src/services/authErrors.js'
import { loginUser, registerUser } from '../src/services/authService.js'

afterEach(() => vi.restoreAllMocks())

describe('servicio de autenticación', () => {
  it('rechaza una respuesta de login sin token', async () => {
    vi.spyOn(api, 'post').mockResolvedValue({ data: {} })

    await expect(
      loginUser({ username: 'oscar', password: 'password123' }),
    ).rejects.toThrow('sesión válida')
  })

  it('devuelve la respuesta del registro', async () => {
    vi.spyOn(api, 'post').mockResolvedValue({ data: { mensaje: 'ok' } })

    await expect(
      registerUser({ username: 'oscar', email: 'o@e.co', password: '12345678' }),
    ).resolves.toEqual({ mensaje: 'ok' })
  })

  it('normaliza un conflicto de registro enviado por el backend', async () => {
    vi.spyOn(api, 'post').mockRejectedValue({
      response: {
        status: 409,
        data: {
          code: AUTH_ERROR_CODES.emailExists,
          field: 'email',
          message: 'Ese correo electrónico ya está registrado.',
        },
      },
    })

    await expect(
      registerUser({ username: 'oscar', email: 'o@e.co', password: '12345678' }),
    ).rejects.toMatchObject({
      code: AUTH_ERROR_CODES.emailExists,
      field: 'email',
      status: 409,
    })
  })

  it('distingue un fallo de conexión', async () => {
    vi.spyOn(api, 'post').mockRejectedValue(new Error('network'))

    await expect(
      loginUser({ username: 'oscar', password: '12345678' }),
    ).rejects.toMatchObject({ code: AUTH_ERROR_CODES.connection })
  })
})
