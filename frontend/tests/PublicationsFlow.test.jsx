import { fireEvent, render, screen } from '@testing-library/react'
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import AuthContext from '../src/context/authContext.js'
import CreatePostPage from '../src/pages/CreatePostPage.jsx'
import FeedPage from '../src/pages/FeedPage.jsx'
import ProfilePage from '../src/pages/ProfilePage.jsx'
import api from '../src/services/api.js'

afterEach(() => vi.restoreAllMocks())

it('publica, vuelve a Inicio y consulta la misma publicación en el perfil del dueño', async () => {
  const posts = []
  vi.spyOn(api, 'post').mockImplementation(async (url, form) => {
    expect(url).toBe('/posts')
    expect(form.get('autor')).toBeNull()
    const post = { id_post: 'nuevo', autor: 'oscar', texto: form.get('texto'), reacciones: 0 }
    posts.push(post)
    return { data: post }
  })
  const get = vi.spyOn(api, 'get').mockImplementation(async (url) => {
    if (url === '/feed/oscar') return { data: { feed: posts } }
    if (url === '/usuarios/oscar/posts') return { data: { posts } }
    return { data: [] }
  })
  render(
    <MemoryRouter initialEntries={['/publicar']}>
      <AuthContext.Provider value={{ user: { username: 'oscar' } }}>
        <Link to="/perfil">Mi perfil</Link>
        <Routes>
          <Route path="/publicar" element={<CreatePostPage />} />
          <Route path="/feed" element={<FeedPage />} />
          <Route path="/perfil" element={<ProfilePage />} />
        </Routes>
      </AuthContext.Provider>
    </MemoryRouter>,
  )
  fireEvent.change(screen.getByLabelText('Contenido de la publicación'), {
    target: { value: 'Mi publicación de prueba' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Publicar' }))
  expect(await screen.findByText('Tu publicación se creó correctamente.')).toBeInTheDocument()
  expect(await screen.findByText('Mi publicación de prueba')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('link', { name: 'Mi perfil' }))
  expect(await screen.findByText('Mi publicación de prueba')).toBeInTheDocument()
  expect(screen.getByLabelText('Publicaciones propias')).toHaveTextContent('@oscar')
  expect(get).toHaveBeenCalledWith('/usuarios/oscar/posts', expect.any(Object))
})
