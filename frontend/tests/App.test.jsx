import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import App from '../src/App.jsx'

const routes = [
  { path: '/login', heading: 'Iniciar sesión' },
  { path: '/registro', heading: 'Crear una cuenta' },
  { path: '/feed', heading: 'Feed' },
  { path: '/perfil', heading: 'Perfil' },
]

describe.each(routes)('ruta $path', ({ heading, path }) => {
  it('muestra la página provisional correspondiente', () => {
    render(
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>,
    )

    expect(
      screen.getByRole('heading', { level: 1, name: heading }),
    ).toBeInTheDocument()
  })
})

describe.each(['/', '/ruta-inexistente'])('redirección desde %s', (path) => {
  it('envía al usuario a la página de inicio de sesión', async () => {
    render(
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>,
    )

    expect(
      await screen.findByRole('heading', {
        level: 1,
        name: 'Iniciar sesión',
      }),
    ).toBeInTheDocument()
  })
})
