import { afterEach, expect, it, vi } from 'vitest'
import api from '../src/services/api.js'
import { searchRegisteredUsers } from '../src/services/users.js'

afterEach(() => {
  vi.restoreAllMocks()
})

it('consulta usuarios registrados y descarta respuestas incompletas', async () => {
  const request = vi.spyOn(api, 'get').mockResolvedValue({
    data: {
      usuarios: [
        { username: 'said' },
        { username: '   ' },
        {},
      ],
    },
  })

  await expect(searchRegisteredUsers('  sa  ')).resolves.toEqual([
    { username: 'said' },
  ])
  expect(request).toHaveBeenCalledWith('/usuarios', {
    params: { query: 'sa' },
    signal: undefined,
  })
})

it('no consulta el backend cuando la búsqueda está vacía', async () => {
  const request = vi.spyOn(api, 'get')

  await expect(searchRegisteredUsers('   ')).resolves.toEqual([])
  expect(request).not.toHaveBeenCalled()
})
