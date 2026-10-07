import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import AuthContext from '../src/context/authContext.js'
import ProfilePage from '../src/pages/ProfilePage.jsx'
import api from '../src/services/api.js'

const user = { username: 'oscar' }

function renderProfile() {
  return render(
    <AuthContext.Provider value={{ user }}>
      <ProfilePage />
    </AuthContext.Provider>,
  )
}

function mockProfileRequests({ followers = [], following = [], posts = [] } = {}) {
  return vi.spyOn(api, 'get')
    .mockResolvedValueOnce({ data: { seguidores: followers } })
    .mockResolvedValueOnce({ data: { seguidos: following } })
    .mockResolvedValueOnce({ data: { posts } })
}

afterEach(() => {
  vi.restoreAllMocks()
})

it('muestra publicaciones, seguidores, seguidos y sus contadores', async () => {
  const request = mockProfileRequests({
    followers: ['said', { username: 'estalin' }],
    following: [{ seguido: 'ana' }],
    posts: [{
      id_post: 'p1',
      texto: 'Mi primera publicación',
      media_url: 'https://cdn.example/p1.png',
      fecha_publicacion: '2026-10-05T14:00:00Z',
      reacciones: 4,
    }],
  })
  renderProfile()

  expect(await screen.findByText('Mi primera publicación')).toBeInTheDocument()
  expect(screen.getByText('4 reacciones')).toBeInTheDocument()
  expect(screen.getByRole('img', { name: 'Contenido publicado por @oscar' })).toHaveAttribute(
    'src',
    'https://cdn.example/p1.png',
  )
  expect(screen.getAllByRole('definition').map((item) => item.textContent)).toEqual([
    '1',
    '2',
    '1',
  ])

  fireEvent.click(screen.getByRole('tab', { name: /Seguidores/ }))
  expect(screen.getByText('@said')).toBeInTheDocument()
  expect(screen.getByText('@estalin')).toBeInTheDocument()
  expect(screen.getByRole('tab', { name: /Seguidores/ })).toHaveAttribute('aria-selected', 'true')

  expect(within(screen.getByRole('list', { name: 'Lista de seguidores' })).getAllByRole('listitem')).toHaveLength(2)
  expect(screen.getByRole('button', { name: 'Seguir a @said' })).toBeEnabled()

  fireEvent.click(screen.getByRole('tab', { name: /Seguidos/ }))
  expect(screen.getByText('@ana')).toBeInTheDocument()
  expect(within(screen.getByRole('tabpanel')).queryByRole('button')).not.toBeInTheDocument()
  expect(request).toHaveBeenNthCalledWith(
    1,
    '/usuarios/oscar/seguidores',
    expect.objectContaining({ signal: expect.any(AbortSignal) }),
  )
  expect(request).toHaveBeenNthCalledWith(
    2,
    '/usuarios/oscar/seguidos',
    expect.objectContaining({ signal: expect.any(AbortSignal) }),
  )
  expect(request).toHaveBeenNthCalledWith(
    3,
    '/usuarios/oscar/posts',
    expect.objectContaining({ signal: expect.any(AbortSignal) }),
  )
})

it('acepta colecciones directas y descarta usuarios sin nombre', async () => {
  vi.spyOn(api, 'get')
    .mockResolvedValueOnce({ data: [{ seguidor: 'said' }, {}] })
    .mockResolvedValueOnce({ data: ['estalin'] })
    .mockResolvedValueOnce({
      data: [{
        id_post: 'p1',
        autor: 'oscar',
        texto: 'Sin contador válido',
        reacciones: 'ninguna',
      }],
    })
  renderProfile()

  expect(await screen.findByText('Sin contador válido')).toBeInTheDocument()
  expect(screen.getByText('0 reacciones')).toBeInTheDocument()

  fireEvent.click(screen.getByRole('tab', { name: /Seguidores/ }))
  expect(screen.getByLabelText('Lista de seguidores')).toHaveTextContent('@said')

  fireEvent.click(screen.getByRole('tab', { name: /Seguidos/ }))
  expect(screen.getByLabelText('Lista de seguidos')).toHaveTextContent('@estalin')
})

it('presenta estados vacíos cuando la cuenta no tiene actividad', async () => {
  mockProfileRequests()
  renderProfile()

  expect(await screen.findByText('Aún no has publicado contenido.')).toBeInTheDocument()

  fireEvent.click(screen.getByRole('tab', { name: /Seguidores/ }))
  expect(screen.getByText('Todavía no tienes seguidores.')).toBeInTheDocument()

  fireEvent.click(screen.getByRole('tab', { name: /Seguidos/ }))
  expect(screen.getByText('Todavía no sigues a ninguna cuenta.')).toBeInTheDocument()
})

it('informa cuando no se puede cargar el perfil', async () => {
  vi.spyOn(api, 'get').mockRejectedValue(new Error('Servidor no disponible'))
  renderProfile()

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'No pudimos cargar tu perfil en este momento.',
  )
})

it('deshabilita cuentas ya seguidas y actualiza Seguidos después de seguir a un seguidor', async () => {
  mockProfileRequests({ followers: ['said', 'estalin'], following: ['estalin'] })
  let resolveFollow
  const request = vi.spyOn(api, 'post').mockImplementation(() => new Promise((resolve) => {
    resolveFollow = resolve
  }))
  renderProfile()
  await screen.findByText('Aún no has publicado contenido.')
  fireEvent.click(screen.getByRole('tab', { name: 'Seguidores' }))

  expect(screen.getByRole('button', { name: 'Ya sigues a @estalin' })).toBeDisabled()
  const button = screen.getByRole('button', { name: 'Seguir a @said' })
  fireEvent.click(button)
  fireEvent.click(button)
  expect(button).toBeDisabled()
  expect(button).toHaveTextContent('Siguiendo…')
  expect(request).toHaveBeenCalledTimes(1)
  expect(request).toHaveBeenCalledWith('/usuarios/oscar/seguir/said')

  await act(async () => resolveFollow({ data: { status: 'success' } }))
  expect(screen.getByRole('button', { name: 'Ya sigues a @said' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Ya sigues a @said' })).toHaveTextContent('Seguir')
  expect(screen.getAllByRole('definition').map((item) => item.textContent)).toEqual(['0', '2', '2'])
  fireEvent.click(screen.getByRole('tab', { name: 'Seguidos' }))
  expect(screen.getByText('@said')).toBeInTheDocument()
  expect(screen.getByText('@estalin')).toBeInTheDocument()
  expect(within(screen.getByRole('tabpanel')).queryByRole('button')).not.toBeInTheDocument()
})

it('permite reintentar un seguimiento fallido sin alterar el contador', async () => {
  mockProfileRequests({ followers: ['said'] })
  const request = vi.spyOn(api, 'post')
    .mockRejectedValueOnce(new Error('Sin conexión'))
    .mockResolvedValueOnce({ data: { status: 'success' } })
  renderProfile()
  await screen.findByText('Aún no has publicado contenido.')
  fireEvent.click(screen.getByRole('tab', { name: 'Seguidores' }))
  fireEvent.click(screen.getByRole('button', { name: 'Seguir a @said' }))

  expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos seguir a @said.')
  expect(screen.getAllByRole('definition').map((item) => item.textContent)).toEqual(['0', '1', '0'])
  fireEvent.click(screen.getByRole('button', { name: 'Seguir a @said' }))
  await waitFor(() => expect(screen.getByRole('button', { name: 'Ya sigues a @said' })).toBeDisabled())
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  expect(request).toHaveBeenCalledTimes(2)
})

it('cancela las solicitudes pendientes al desmontar la vista', () => {
  vi.spyOn(api, 'get').mockImplementation(() => new Promise(() => {}))
  const view = renderProfile()
  const signals = api.get.mock.calls.map(([, config]) => config.signal)

  view.unmount()

  expect(signals).toHaveLength(3)
  signals.forEach((signal) => expect(signal.aborted).toBe(true))
})
