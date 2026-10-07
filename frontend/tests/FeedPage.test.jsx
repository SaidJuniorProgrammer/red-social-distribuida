import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import AuthContext from '../src/context/authContext.js'
import FeedPage from '../src/pages/FeedPage.jsx'
import api from '../src/services/api.js'

const user = { username: 'oscar' }

function renderFeed() {
  return render(
    <MemoryRouter>
      <AuthContext.Provider value={{ user }}>
        <FeedPage />
      </AuthContext.Provider>
    </MemoryRouter>,
  )
}

function createPost(overrides = {}) {
  return {
    id_post: 'post/1',
    autor: 'said',
    texto: 'Una publicación distribuida',
    media_url: null,
    fecha_publicacion: '2026-10-05T15:30:00Z',
    reacciones: 2,
    ...overrides,
  }
}

afterEach(() => {
  vi.restoreAllMocks()
})

it('carga el feed y permite dar y quitar Me gusta', async () => {
  vi.spyOn(api, 'get').mockResolvedValue({ data: { feed: [createPost()] } })
  const createLike = vi.spyOn(api, 'post').mockResolvedValue({ data: {} })
  const removeLike = vi.spyOn(api, 'delete').mockResolvedValue({ data: {} })
  renderFeed()

  expect(await screen.findByText('Una publicación distribuida')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /Crear publicación/ })).toHaveAttribute(
    'href',
    '/publicar',
  )
  expect(api.get).toHaveBeenCalledWith('/feed/oscar', expect.objectContaining({
    signal: expect.any(AbortSignal),
  }))

  fireEvent.click(screen.getByRole('button', { name: 'Me gusta' }))
  expect(await screen.findByLabelText('3 reacciones')).toBeInTheDocument()
  await waitFor(() => expect(createLike).toHaveBeenCalledWith('/posts/post%2F1/like/oscar'))

  const unlikeButton = await screen.findByRole('button', { name: 'Quitar Me gusta' })
  await waitFor(() => expect(unlikeButton).toBeEnabled())
  fireEvent.click(unlikeButton)

  expect(await screen.findByLabelText('2 reacciones')).toBeInTheDocument()
  await waitFor(() => expect(removeLike).toHaveBeenCalledWith('/posts/post%2F1/like/oscar'))
})

it('muestra el estado inicial de una reacción y contenido multimedia', async () => {
  vi.spyOn(api, 'get').mockResolvedValue({
    data: {
      feed: [createPost({
        liked: true,
        media_url: 'https://cdn.example/post.png',
        fecha_publicacion: 'fecha externa',
      })],
    },
  })
  renderFeed()

  expect(await screen.findByRole('button', { name: 'Quitar Me gusta' })).toHaveAttribute(
    'aria-pressed',
    'true',
  )
  expect(screen.getByRole('img', { name: 'Contenido publicado por @said' })).toHaveAttribute(
    'src',
    'https://cdn.example/post.png',
  )
  expect(screen.getByText('fecha externa')).toBeInTheDocument()
})

it('conserva la publicación cuando su imagen no está disponible', async () => {
  vi.spyOn(api, 'get').mockResolvedValue({
    data: {
      feed: [createPost({
        media_url: 'https://cdn.example/no-existe.png',
        fecha_publicacion: '2026-10-05T15:30:00Z',
      })],
    },
  })
  renderFeed()

  const image = await screen.findByRole('img', { name: 'Contenido publicado por @said' })
  fireEvent.error(image)

  expect(screen.getByText('Una publicación distribuida')).toBeInTheDocument()
  expect(screen.getByText('@said')).toBeInTheDocument()
  expect(screen.getByLabelText('2 reacciones')).toBeInTheDocument()
  expect(screen.getByRole('status')).toHaveTextContent('Contenido multimedia no disponible')
})

it('restaura el contador e informa cuando falla la reacción', async () => {
  vi.spyOn(api, 'get').mockResolvedValue({ data: { feed: [createPost()] } })
  vi.spyOn(api, 'post').mockRejectedValue(new Error('Servidor no disponible'))
  renderFeed()

  fireEvent.click(await screen.findByRole('button', { name: 'Me gusta' }))

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'No pudimos actualizar tu reacción. Inténtalo de nuevo.',
  )
  expect(screen.getByLabelText('2 reacciones')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Me gusta' })).toBeEnabled()
})

it('presenta estados vacíos y errores de carga', async () => {
  const request = vi.spyOn(api, 'get')
    .mockResolvedValueOnce({ data: { feed: null } })
    .mockRejectedValueOnce(new Error('Servidor no disponible'))

  const firstRender = renderFeed()
  expect(await screen.findByRole('heading', { name: 'Tu feed está al día' })).toBeInTheDocument()
  firstRender.unmount()

  renderFeed()
  expect(await screen.findByRole('alert')).toHaveTextContent(
    'No pudimos cargar las publicaciones en este momento.',
  )
  expect(request).toHaveBeenCalledTimes(2)
})

it('normaliza contadores inválidos y cancela la carga al desmontar', async () => {
  let resolveRequest
  vi.spyOn(api, 'get')
    .mockResolvedValueOnce({ data: { feed: [createPost({ reacciones: 'sin contador' })] } })
    .mockImplementationOnce(() => new Promise((resolve) => {
      resolveRequest = resolve
    }))

  const firstRender = renderFeed()
  expect(await screen.findByLabelText('0 reacciones')).toBeInTheDocument()
  firstRender.unmount()

  const secondRender = renderFeed()
  secondRender.unmount()
  resolveRequest({ data: { feed: [createPost()] } })
})
