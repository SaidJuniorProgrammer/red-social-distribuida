import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { expect, it, vi } from 'vitest'
import AppLayout from '../src/components/AppLayout.jsx'
import AuthContext from '../src/context/authContext.js'

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
