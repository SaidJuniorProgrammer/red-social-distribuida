import { afterEach, expect, it, vi } from 'vitest'
import api from '../src/services/api.js'
import { createPost } from '../src/services/posts.js'

const originalAdapter = api.defaults.adapter
afterEach(() => {
  api.defaults.adapter = originalAdapter
  vi.restoreAllMocks()
})

it('conserva el formulario multipart al atravesar la configuración JSON de Axios', async () => {
  const adapter = vi.fn(async (config) => ({
    config, status: 201, statusText: 'Created', headers: {}, data: { id_post: 'p1' },
  }))
  api.defaults.adapter = adapter
  const image = new File(['imagen'], 'foto.png', { type: 'image/png' })

  await createPost({ text: ' Hola ', image })

  const [config] = adapter.mock.calls[0]
  expect(config.data).toBeInstanceOf(FormData)
  expect(config.data.get('texto')).toBe('Hola')
  expect(config.data.get('archivo')).toBe(image)
  expect(config.headers.getContentType()).not.toBe('application/json')
})
