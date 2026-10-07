import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import AppLayout from '../src/components/AppLayout.jsx'
import AuthContext from '../src/context/authContext.js'
import api from '../src/services/api.js'

afterEach(() => vi.restoreAllMocks())

it('mantiene el contenido dentro de la SPA sin animar la página completa', () => {
  const { container } = render(
    <MemoryRouter initialEntries={['/perfil']}>
      <AuthContext.Provider value={{ logout: vi.fn(), user: { username: 'oscar' } }}>
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="/perfil" element={<h1>Perfil de Oscar</h1>} />
          </Route>
        </Routes>
      </AuthContext.Provider>
    </MemoryRouter>,
  )

  expect(screen.getByRole('heading', { name: 'Perfil de Oscar' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Perfil' })).toHaveAttribute('aria-current', 'page')
  expect(container.querySelector('.page-transition')).not.toBeInTheDocument()
})

it('muestra en la navegación la cantidad de notificaciones no leídas', async () => {
  vi.spyOn(api, 'get').mockResolvedValue({ data: { notificaciones: [
    { id_notificacion: 'n1', titulo: 'Nueva publicación', leida: false },
    { id_notificacion: 'n2', titulo: 'Nuevo like', leida: true },
  ] } })
  const { container } = render(
    <MemoryRouter initialEntries={['/perfil']}>
      <AuthContext.Provider value={{ logout: vi.fn(), user: { username: 'oscar' } }}>
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="/perfil" element={<h1>Perfil de Oscar</h1>} />
          </Route>
        </Routes>
      </AuthContext.Provider>
    </MemoryRouter>,
  )

  expect(await screen.findByText('1')).toHaveClass('notifications-badge')
  expect(container.querySelectorAll('.notifications-badge')).toHaveLength(1)
  expect(
    await screen.findByRole('link', { name: 'Notificaciones (1 sin leer)' }),
  ).toBeInTheDocument()
})
