import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../src/App.jsx'
import AuthProvider from '../src/context/AuthProvider.jsx'
import { AUTH_STORAGE_KEY } from '../src/context/authStorage.js'
import api from '../src/services/api.js'
import { AUTH_ERROR_CODES } from '../src/services/authErrors.js'
import { createJwt } from './testUtils.js'

function renderApp(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

function fillLogin(username = 'oscar', password = 'password123') {
  fireEvent.change(screen.getByLabelText('Nombre de usuario'), {
    target: { value: username },
  })
  fireEvent.change(screen.getByLabelText('Contraseña'), {
    target: { value: password },
  })
}

function fillRegister() {
  fireEvent.change(screen.getByLabelText('Nombre de usuario'), {
    target: { value: 'oscar' },
  })
  fireEvent.change(screen.getByLabelText('Correo electrónico'), {
    target: { value: 'oscar@example.com' },
  })
  fireEvent.change(screen.getByLabelText('Contraseña'), {
    target: { value: 'password123' },
  })
  fireEvent.change(screen.getByLabelText('Confirmar contraseña'), {
    target: { value: 'password123' },
  })
}

afterEach(() => {
  localStorage.clear()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('inicio de sesión', () => {
  it('muestra validaciones y permite alternar la contraseña', () => {
    renderApp('/login')
    fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    expect(screen.getByText('Ingresa tu nombre de usuario.')).toBeInTheDocument()
    expect(screen.getByText('Ingresa tu contraseña.')).toBeInTheDocument()

    const password = screen.getByLabelText('Contraseña')
    fireEvent.change(password, { target: { value: 'secreta123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Mostrar contraseña' }))
    expect(password).toHaveAttribute('type', 'text')
  })

  it('muestra un error específico para credenciales incorrectas', async () => {
    vi.spyOn(api, 'post').mockRejectedValue({ response: { status: 401 } })
    renderApp('/login')
    fillLogin()

    fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Usuario o contraseña incorrectos.',
    )
  })

  it('guarda el JWT y entra al feed protegido', async () => {
    vi.spyOn(api, 'post').mockResolvedValue({ data: { token: 'jwt-prueba' } })
    renderApp('/login')
    fillLogin()

    fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Hola, @oscar' }),
    ).toBeInTheDocument()
    expect(JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY))).toEqual({
      token: 'jwt-prueba',
      user: { username: 'oscar' },
    })
  })

  it('muestra un error de conexión cuando la petición falla', async () => {
    vi.spyOn(api, 'post').mockRejectedValue(new Error('network'))
    renderApp('/login')
    fillLogin()
    fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No pudimos conectar con el servidor.',
    )
  })

  it('adjunta el JWT a las peticiones posteriores', async () => {
    localStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({ token: 'jwt-activo', user: { username: 'oscar' } }),
    )

    const response = await api.get('/ruta-protegida', {
      adapter: async (config) => ({
        config,
        data: config.headers.Authorization,
        headers: {},
        status: 200,
        statusText: 'OK',
      }),
    })

    expect(response.data).toBe('Bearer jwt-activo')
  })

  it('cierra la sesión cuando el JWT vence mientras la aplicación está abierta', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-09-30T12:00:00Z'))
    localStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({
        token: createJwt(Math.floor(Date.now() / 1000) + 1),
        user: { username: 'oscar' },
      }),
    )
    renderApp('/feed')

    expect(screen.getByText('Sesión activa')).toBeInTheDocument()

    await act(async () => {
      vi.advanceTimersByTime(1000)
    })

    expect(screen.getByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
  })

  it('cierra una sesión activa cuando la API responde 401', async () => {
    localStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({ token: 'jwt-activo', user: { username: 'oscar' } }),
    )
    renderApp('/feed')

    await act(async () => {
      await expect(
        api.get('/ruta-protegida', {
          adapter: async (config) => Promise.reject({
            config,
            response: { status: 401 },
          }),
        }),
      ).rejects.toMatchObject({ response: { status: 401 } })
    })

    expect(screen.getByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
  })

  it('mantiene la sesión en memoria si localStorage no permite guardarla', async () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('Almacenamiento no disponible')
    })
    vi.spyOn(api, 'post').mockResolvedValue({ data: { token: 'jwt-prueba' } })
    renderApp('/login')
    fillLogin()

    fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Hola, @oscar' }),
    ).toBeInTheDocument()
  })

  it('cierra la sesión aunque localStorage no permita eliminarla', async () => {
    localStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({ token: 'jwt-activo', user: { username: 'oscar' } }),
    )
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new DOMException('Almacenamiento no disponible')
    })
    renderApp('/feed')

    fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }))

    expect(screen.getByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
  })
})

describe('registro', () => {
  it('valida el correo, la longitud y la confirmación de contraseña', () => {
    renderApp('/registro')
    fireEvent.change(screen.getByLabelText('Nombre de usuario'), {
      target: { value: 'oscar' },
    })
    fireEvent.change(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'correo-invalido' },
    })
    fireEvent.change(screen.getByLabelText('Contraseña'), {
      target: { value: 'corta' },
    })
    fireEvent.change(screen.getByLabelText('Confirmar contraseña'), {
      target: { value: 'diferente' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    expect(screen.getByText('Ingresa un correo electrónico válido.')).toBeInTheDocument()
    expect(screen.getByText('La contraseña debe tener al menos 8 caracteres.')).toBeInTheDocument()
    expect(screen.getByText('Las contraseñas no coinciden.')).toBeInTheDocument()
  })

  it('envía el contrato del backend y regresa al login', async () => {
    const request = vi.spyOn(api, 'post').mockResolvedValue({
      data: { mensaje: 'Usuario registrado con éxito' },
    })
    renderApp('/registro')
    fillRegister()

    fireEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    expect(
      await screen.findByText('Cuenta creada correctamente. Ya puedes iniciar sesión.'),
    ).toBeInTheDocument()
    expect(request).toHaveBeenCalledWith('/auth/register', {
      username: 'oscar',
      email: 'oscar@example.com',
      password: 'password123',
    })
  })

  it('muestra un error cuando el registro falla', async () => {
    vi.spyOn(api, 'post').mockRejectedValue(new Error('server'))
    renderApp('/registro')
    fillRegister()
    fireEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(
        'No pudimos conectar con el servidor.',
      )
    })
  })

  it('muestra junto al campo cuando el nombre de usuario ya existe', async () => {
    vi.spyOn(api, 'post').mockRejectedValue({
      response: {
        status: 409,
        data: {
          code: AUTH_ERROR_CODES.usernameExists,
          field: 'username',
          message: 'Ese nombre de usuario ya está registrado.',
        },
      },
    })
    renderApp('/registro')
    fillRegister()
    fireEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Ese nombre de usuario ya está registrado. Prueba con otro.',
    )
    expect(screen.getByLabelText('Nombre de usuario')).toHaveFocus()
  })

  it('muestra junto al campo cuando el correo ya existe', async () => {
    vi.spyOn(api, 'post').mockRejectedValue({
      response: {
        status: 409,
        data: {
          code: AUTH_ERROR_CODES.emailExists,
          field: 'email',
          message: 'Ese correo electrónico ya está registrado.',
        },
      },
    })
    renderApp('/registro')
    fillRegister()
    fireEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Ese correo electrónico ya está registrado. Usa otro o inicia sesión.',
    )
    expect(screen.getByLabelText('Correo electrónico')).toHaveFocus()
  })
})
