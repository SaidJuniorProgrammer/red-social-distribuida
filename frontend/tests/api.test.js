// @vitest-environment node

import { afterEach, describe, expect, it, vi } from 'vitest'
import api from '../src/services/api.js'

describe('cliente HTTP', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.resetModules()
  })

  it('utiliza la URL local cuando no existe una variable de entorno', () => {
    expect(api.defaults.baseURL).toBe('http://localhost:8080/api')
  })

  it('respeta la URL configurada mediante el entorno', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.example.test')

    const { default: api } = await import('../src/services/api.js')

    expect(api.defaults.baseURL).toBe('https://api.example.test')
  })
})
