import { afterEach, describe, expect, it } from 'vitest'
import {
  AUTH_STORAGE_KEY,
  clearSession,
  readSession,
  writeSession,
} from '../src/context/authStorage.js'

afterEach(() => localStorage.clear())

describe('almacenamiento de la sesión', () => {
  it('guarda, lee y elimina una sesión válida', () => {
    const session = { token: 'jwt', user: { username: 'oscar' } }
    writeSession(session)

    expect(readSession()).toEqual(session)
    clearSession()
    expect(readSession()).toBeNull()
  })

  it('ignora contenido inválido en localStorage', () => {
    localStorage.setItem(AUTH_STORAGE_KEY, '{contenido-invalido')
    expect(readSession()).toBeNull()

    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ token: '' }))
    expect(readSession()).toBeNull()
  })
})
