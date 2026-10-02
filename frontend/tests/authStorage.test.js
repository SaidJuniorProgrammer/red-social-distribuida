import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  AUTH_STORAGE_KEY,
  clearSession,
  readSession,
  writeSession,
} from '../src/context/authStorage.js'
import { createJwt } from './testUtils.js'

afterEach(() => {
  localStorage.clear()
  vi.restoreAllMocks()
})

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

  it('elimina una sesión cuando el JWT ya expiró', () => {
    const session = {
      token: createJwt(Math.floor(Date.now() / 1000) - 1),
      user: { username: 'oscar' },
    }
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session))

    expect(readSession()).toBeNull()
    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
  })

  it('no interrumpe la aplicación cuando localStorage rechaza una escritura', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('Almacenamiento no disponible')
    })

    expect(
      writeSession({ token: 'jwt', user: { username: 'oscar' } }),
    ).toBe(false)
  })

  it('invalida la sesión aunque localStorage no permita eliminarla', () => {
    const session = { token: 'jwt', user: { username: 'oscar' } }
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session))
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new DOMException('Almacenamiento no disponible')
    })

    expect(clearSession()).toBe(false)
    expect(readSession()).toBeNull()
  })
})
