import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import ExplorePage from '../src/pages/ExplorePage.jsx'
import api from '../src/services/api.js'
import { MemoryRouter } from 'react-router-dom'
import AuthContext from '../src/context/authContext.js'
import { getFollowing, getSuggestions } from '../src/services/profile.js'

vi.mock('../src/services/profile.js', async (importOriginal) => ({
  ...await importOriginal(),
  getFollowing: vi.fn().mockResolvedValue([]),
  getSuggestions: vi.fn().mockResolvedValue([]),
}))

function renderExplore() {
  return render(<MemoryRouter><AuthContext.Provider value={{ user: { username: 'oscar' } }}>
    <ExplorePage />
  </AuthContext.Provider></MemoryRouter>)
}

afterEach(() => {
  vi.restoreAllMocks()
})

it('mantiene disponible seguir aunque fallen las sugerencias y permite reintentar', async () => {
  getSuggestions.mockRejectedValueOnce(new Error('Sin sugerencias'))
  vi.spyOn(api, 'get').mockResolvedValue({ data: { usuarios: [{ username: 'ana' }] } })
  renderExplore()
  await screen.findByText('No pudimos cargar las sugerencias.')
  fireEvent.change(screen.getByLabelText('Nombre de usuario'), { target: { value: 'ana' } })
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
  expect(await screen.findByRole('button', { name: 'Seguir a @ana' })).toBeEnabled()
  fireEvent.click(screen.getByRole('button', { name: 'Reintentar conexiones' }))
  await waitFor(() => expect(screen.queryByText('No pudimos cargar las sugerencias.')).not.toBeInTheDocument())
})

it('no permite cambiar seguimientos hasta conocer el estado actual', async () => {
  getFollowing.mockRejectedValueOnce(new Error('Sin conexiones'))
  vi.spyOn(api, 'get').mockResolvedValue({ data: { usuarios: [{ username: 'ana' }] } })
  renderExplore()
  await screen.findByText('No pudimos cargar tus conexiones.')
  fireEvent.change(screen.getByLabelText('Nombre de usuario'), { target: { value: 'ana' } })
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
  expect(await screen.findByRole('button', { name: 'Seguir a @ana' })).toBeDisabled()
  fireEvent.click(screen.getByRole('button', { name: 'Reintentar conexiones' }))
  await waitFor(() => expect(screen.getByRole('button', { name: 'Seguir a @ana' })).toBeEnabled())
})

it('muestra sugerencias con acceso al perfil y permite seguir y dejar de seguir', async () => {
  getSuggestions.mockResolvedValueOnce([{ recomendado: 'ana', conexiones_en_comun: 2 }])
  const follow = vi.spyOn(api, 'post').mockResolvedValue({ data: {} })
  const unfollow = vi.spyOn(api, 'delete').mockResolvedValue({ data: {} })
  renderExplore()
  fireEvent.click(await screen.findByRole('button', { name: 'Seguir a @ana' }))
  expect(await screen.findByRole('button', { name: 'Dejar de seguir a @ana' })).toBeEnabled()
  expect(follow).toHaveBeenCalledWith('/usuarios/oscar/seguir/ana')
  expect(screen.getByRole('link', { name: 'Ver perfil de @ana' })).toHaveAttribute('href', '/perfil/ana')
  fireEvent.click(screen.getByRole('button', { name: 'Dejar de seguir a @ana' }))
  await waitFor(() => expect(screen.getByRole('button', { name: 'Seguir a @ana' })).toBeEnabled())
  expect(unfollow).toHaveBeenCalledWith('/usuarios/oscar/seguir/ana')
})

it('permite seguir desde los resultados y conserva el estado si falla dejar de seguir', async () => {
  vi.spyOn(api, 'get').mockResolvedValue({ data: { usuarios: [{ username: 'said' }] } })
  vi.spyOn(api, 'post').mockResolvedValue({ data: {} })
  vi.spyOn(api, 'delete').mockRejectedValue(new Error('Sin conexión'))
  renderExplore()
  fireEvent.change(screen.getByLabelText('Nombre de usuario'), { target: { value: 'said' } })
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
  fireEvent.click(await screen.findByRole('button', { name: 'Seguir a @said' }))
  fireEvent.click(await screen.findByRole('button', { name: 'Dejar de seguir a @said' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos dejar de seguir a @said.')
  expect(screen.getByRole('button', { name: 'Dejar de seguir a @said' })).toBeEnabled()
})

it('muestra únicamente las cuentas registradas devueltas por el servidor', async () => {
  vi.spyOn(api, 'get').mockResolvedValue({
    data: { usuarios: [{ username: 'said' }] },
  })
  renderExplore()

  fireEvent.change(screen.getByLabelText('Nombre de usuario'), {
    target: { value: 'sa' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))

  expect(await screen.findByText('@said')).toBeInTheDocument()
  expect(api.get).toHaveBeenCalledWith('/usuarios', expect.objectContaining({
    params: { query: 'sa' },
  }))
})

it('informa cuando no encuentra cuentas o la búsqueda no está disponible', async () => {
  const request = vi.spyOn(api, 'get')
    .mockResolvedValueOnce({ data: { usuarios: [] } })
    .mockRejectedValueOnce(new Error('Servidor no disponible'))
  renderExplore()

  const input = screen.getByLabelText('Nombre de usuario')
  fireEvent.change(input, { target: { value: 'nadie' } })
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
  expect(await screen.findByRole('status')).toHaveTextContent(
    'No encontramos cuentas registradas con ese nombre.',
  )

  fireEvent.change(input, { target: { value: 'otro' } })
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
  await waitFor(() => expect(request).toHaveBeenCalledTimes(2))
  expect(await screen.findByRole('status')).toHaveTextContent(
    'No pudimos realizar la búsqueda en este momento.',
  )
})

it('ignora una respuesta anterior cuando cambia la consulta', async () => {
  let resolveRequest
  vi.spyOn(api, 'get').mockImplementation(() => new Promise((resolve) => {
    resolveRequest = resolve
  }))
  renderExplore()

  const input = screen.getByLabelText('Nombre de usuario')
  fireEvent.change(input, { target: { value: 'ana' } })
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
  fireEvent.change(input, { target: { value: 'luis' } })

  await act(async () => {
    resolveRequest({ data: { usuarios: [{ username: 'ana' }] } })
  })

  expect(screen.queryByText('@ana')).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Buscar' })).toBeEnabled()
})
