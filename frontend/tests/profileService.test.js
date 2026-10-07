import { afterEach, expect, it, vi } from 'vitest'
import api from '../src/services/api.js'
import { getFollowing, getSuggestions } from '../src/services/profile.js'

afterEach(() => vi.restoreAllMocks())

it('consulta conexiones y recomendaciones con rutas codificadas y filtra entradas inválidas', async () => {
  vi.spyOn(api, 'get').mockResolvedValueOnce({ data: { seguidos: [{ username: 'ana' }, {}] } })
    .mockResolvedValueOnce({ data: { sugerencias: [{ recomendado: 'said', conexiones_en_comun: 2 }, {}] } })
  const signal = new AbortController().signal
  expect(await getFollowing('cuenta/1', { signal })).toEqual(['ana'])
  expect(await getSuggestions('cuenta/1', { signal })).toEqual([{ recomendado: 'said', conexiones_en_comun: 2 }])
  expect(api.get).toHaveBeenCalledWith('/usuarios/cuenta%2F1/seguidos', { signal })
  expect(api.get).toHaveBeenCalledWith('/usuarios/cuenta%2F1/sugerencias', { signal })
})
