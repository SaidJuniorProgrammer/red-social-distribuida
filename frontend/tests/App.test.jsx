import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import App from '../src/App.jsx'
import AuthProvider from '../src/context/AuthProvider.jsx'
import { AUTH_STORAGE_KEY } from '../src/context/authStorage.js'

const routes = [
  { path: '/login', heading: 'Iniciar sesión', level: 2 },
  { path: '/registro', heading: 'Crear cuenta', level: 2 },
]

function renderApp(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

afterEach(() => {
  localStorage.clear()
})

describe.each(routes)('ruta $path', ({ heading, level, path }) => {
  it('muestra la página correspondiente', () => {
    renderApp(path)

    expect(
      screen.getByRole('heading', { level, name: heading }),
    ).toBeInTheDocument()
  })
})

describe.each(['/feed', '/notificaciones', '/perfil'])('ruta protegida %s', (path) => {
  it('regresa al inicio de sesión cuando no existe una sesión', async () => {
    renderApp(path)

    expect(
      await screen.findByRole('heading', { level: 2, name: 'Iniciar sesión' }),
    ).toBeInTheDocument()
  })
})

it('muestra las notificaciones dentro del mismo layout protegido', () => {
  localStorage.setItem(
    AUTH_STORAGE_KEY,
    JSON.stringify({ token: 'jwt-activo', user: { username: 'oscar' } }),
  )
  renderApp('/notificaciones')

  expect(
    screen.getByRole('heading', { level: 1, name: 'Notificaciones' }),
  ).toBeInTheDocument()
  expect(
    screen.getByRole('link', { name: 'Notificaciones' }),
  ).toHaveAttribute('aria-current', 'page')
})

it('muestra el destino protegido y permite cerrar sesión', async () => {
  localStorage.setItem(
    AUTH_STORAGE_KEY,
    JSON.stringify({ token: 'jwt-activo', user: { username: 'oscar' } }),
  )
  renderApp('/feed')

  expect(
    screen.getByRole('heading', { level: 1, name: 'Hola, @oscar' }),
  ).toBeInTheDocument()

  expect(
    screen.getByRole('complementary', { name: 'Barra lateral principal' }),
  ).toBeInTheDocument()

  fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }))

  expect(
    await screen.findByRole('heading', { level: 2, name: 'Iniciar sesión' }),
  ).toBeInTheDocument()
  expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
})

describe.each(['/', '/ruta-inexistente'])('redirección desde %s', (path) => {
  it('envía al usuario a la página de inicio de sesión', async () => {
    renderApp(path)

    expect(
      await screen.findByRole('heading', {
        level: 2,
        name: 'Iniciar sesión',
      }),
    ).toBeInTheDocument()
  })
})
