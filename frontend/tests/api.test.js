// @vitest-environment node

import { afterEach, describe, expect, it, vi } from 'vitest'
import api from '../src/services/api.js'

describe('cliente HTTP', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.resetModules()
  })

  it('utiliza la URL por defecto de produccion cuando no existe una variable de entorno', () => {
    expect(api.defaults.baseURL).toBe('https://pachyweb-backend.onrender.com/api')
  })

  it('respeta la URL configurada mediante el entorno', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.example.test')

    const { default: api } = await import('../src/services/api.js')

    expect(api.defaults.baseURL).toBe('https://api.example.test')
  })
})