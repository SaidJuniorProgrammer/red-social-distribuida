import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import App from '../src/App.jsx'
import AuthProvider from '../src/context/AuthProvider.jsx'
import ChatProvider from '../src/context/ChatProvider.jsx'
import api from '../src/services/api.js'

class MockWebSocket {
  static CONNECTING = 0
  static OPEN = 1
  static CLOSED = 3
  static instances = []

  constructor(url, protocols) {
    this.url = url
    this.protocols = protocols
    this.readyState = MockWebSocket.CONNECTING
    this.sentMessages = []
    MockWebSocket.instances.push(this)
  }

  open() {
    this.readyState = MockWebSocket.OPEN
    this.onopen?.()
  }

  receive(message) {
    this.onmessage?.({ data: JSON.stringify(message) })
  }

  send(message) {
    this.sentMessages.push(message)
  }

  error() {
    this.onerror?.()
  }

  close(code, reason) {
    this.closeArgs = [code, reason]
    this.readyState = MockWebSocket.CLOSED
    this.onclose?.()
  }
}

function renderApp() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <ChatProvider>
          <App />
        </ChatProvider>
      </AuthProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  MockWebSocket.instances = []
  vi.stubGlobal('WebSocket', MockWebSocket)
})

afterEach(() => {
  localStorage.clear()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

it('conecta al iniciar sesión y actualiza el chat sin recargar la página', async () => {
  vi.spyOn(api, 'post').mockResolvedValue({ data: { token: 'jwt-prueba' } })
  vi.spyOn(api, 'get').mockResolvedValue({
    data: { usuarios: [{ username: 'said' }] },
  })
  renderApp()

  fireEvent.change(screen.getByLabelText('Nombre de usuario'), {
    target: { value: 'oscar' },
  })
  fireEvent.change(screen.getByLabelText('Contraseña'), {
    target: { value: 'password123' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

  await waitFor(() => expect(MockWebSocket.instances).toHaveLength(1))
  const socket = MockWebSocket.instances[0]
  expect(socket.url).toBe('ws://localhost:8080/chat')
  expect(socket.protocols[0]).toBe('bearer-token-carrier')

  await act(async () => socket.open())
  const persistentSidebar = screen.getByRole('complementary', {
    name: 'Barra lateral principal',
  })
  fireEvent.click(screen.getByRole('link', { name: 'Mensajes' }))
  expect(
    screen.getByRole('complementary', { name: 'Barra lateral principal' }),
  ).toBe(persistentSidebar)
  expect(screen.getByRole('link', { name: 'Mensajes' })).toHaveAttribute(
    'aria-current',
    'page',
  )
  expect(screen.getByRole('status')).toHaveTextContent('En línea')

  fireEvent.change(screen.getByLabelText('Buscar usuario registrado'), {
    target: { value: 'said' },
  })
  fireEvent.click(await screen.findByRole('button', { name: /Said.*@said/ }))
  fireEvent.click(screen.getByRole('button', { name: 'Volver a conversaciones' }))
  fireEvent.change(screen.getByLabelText('Mensaje'), {
    target: { value: 'Hola desde React' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Enviar' }))

  expect(screen.queryByText('Hola desde React')).not.toBeInTheDocument()
  expect(JSON.parse(socket.sentMessages[0])).toMatchObject({
    destinatario_id: 'said',
    contenido: 'Hola desde React',
  })

  await act(async () => {
    socket.receive({
      emisor_id: 'oscar',
      destinatario_id: 'said',
      contenido: 'Hola desde React',
      timestamp: '2026-10-01T15:29:00Z',
    })
    socket.receive({
      emisor_id: 'said',
      destinatario_id: 'oscar',
      contenido: 'Recibido en tiempo real',
      timestamp: '2026-10-01T15:30:00Z',
    })
  })

  expect(screen.getAllByText('Recibido en tiempo real')).not.toHaveLength(0)
  expect(screen.getAllByText('@said')).not.toHaveLength(0)

  fireEvent.change(screen.getByLabelText('Mensaje'), {
    target: { value: 'Borrador privado para Said' },
  })
  await act(async () => {
    socket.receive({
      emisor_id: 'carlos',
      destinatario_id: 'oscar',
      contenido: 'Mensaje de Carlos',
      timestamp: '2026-10-01T15:31:00Z',
    })
  })

  expect(
    screen.getByText('Nuevo mensaje de @carlos: Mensaje de Carlos'),
  ).toBeInTheDocument()
  let conversationCards = within(
    screen.getByLabelText('Conversaciones'),
  ).getAllByRole('button').filter((button) => (
    button.classList.contains('conversation-card')
  ))
  expect(conversationCards[0]).toHaveTextContent('@carlos')
  fireEvent.click(screen.getByRole('button', { name: /Carlos.*@carlos/ }))
  expect(screen.getByLabelText('Mensaje')).toHaveValue('')

  await act(async () => {
    socket.receive({
      emisor_id: 'said',
      destinatario_id: 'oscar',
      contenido: 'Último mensaje de Said',
      timestamp: '2026-10-01T15:32:00Z',
    })
  })
  conversationCards = within(
    screen.getByLabelText('Conversaciones'),
  ).getAllByRole('button').filter((button) => (
    button.classList.contains('conversation-card')
  ))
  expect(conversationCards[0]).toHaveTextContent('@said')

  fireEvent.click(screen.getByRole('button', { name: 'Nueva conversación' }))
  expect(screen.getByLabelText('Buscar usuario registrado')).toHaveValue('')
  fireEvent.change(screen.getByLabelText('Buscar usuario registrado'), {
    target: { value: 'said' },
  })
  fireEvent.click(await screen.findByRole('button', { name: /Said.*@said/ }))
  fireEvent.change(screen.getByLabelText('Mensaje'), {
    target: { value: 'Enviado con Enter' },
  })
  fireEvent.keyDown(screen.getByLabelText('Mensaje'), {
    key: 'Enter',
    shiftKey: true,
  })
  expect(socket.sentMessages).toHaveLength(1)
  fireEvent.keyDown(screen.getByLabelText('Mensaje'), { key: 'Enter' })

  expect(JSON.parse(socket.sentMessages[1])).toMatchObject({
    destinatario_id: 'said',
    contenido: 'Enviado con Enter',
  })

  await act(async () => {
    socket.receive({
      type: 'delivery_error',
      destinatario_id: 'said',
      message: 'No se pudo enviar el mensaje porque el usuario no existe.',
    })
  })
  expect(screen.getByRole('alert')).toHaveTextContent(
    'No se pudo enviar el mensaje porque el usuario no existe.',
  )

  fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }))
  expect(socket.closeArgs).toEqual([1000, 'Sesión finalizada'])
  expect(
    await screen.findByRole('heading', { name: 'Iniciar sesión' }),
  ).toBeInTheDocument()
})

it('no permite enviar mensajes a una cuenta que no está registrada', async () => {
  vi.spyOn(api, 'post').mockResolvedValue({ data: { token: 'jwt-prueba' } })
  vi.spyOn(api, 'get').mockResolvedValue({ data: { usuarios: [] } })
  renderApp()

  fireEvent.change(screen.getByLabelText('Nombre de usuario'), {
    target: { value: 'oscar' },
  })
  fireEvent.change(screen.getByLabelText('Contraseña'), {
    target: { value: 'password123' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

  await waitFor(() => expect(MockWebSocket.instances).toHaveLength(1))
  await act(async () => MockWebSocket.instances[0].open())
  fireEvent.click(screen.getByRole('link', { name: 'Mensajes' }))
  fireEvent.change(screen.getByLabelText('Buscar usuario registrado'), {
    target: { value: 'usuario_fantasma' },
  })

  expect(await screen.findByText(
    'No encontramos un usuario registrado con ese nombre.',
  )).toBeInTheDocument()
  expect(screen.getByLabelText('Mensaje')).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Enviar' })).toBeDisabled()
  expect(MockWebSocket.instances[0].sentMessages).toHaveLength(0)
})

it('conserva el error cuando el navegador cierra la conexión', async () => {
  vi.spyOn(api, 'post').mockResolvedValue({ data: { token: 'jwt-prueba' } })
  renderApp()

  fireEvent.change(screen.getByLabelText('Nombre de usuario'), {
    target: { value: 'oscar' },
  })
  fireEvent.change(screen.getByLabelText('Contraseña'), {
    target: { value: 'password123' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

  await waitFor(() => expect(MockWebSocket.instances).toHaveLength(1))
  const socket = MockWebSocket.instances[0]
  await act(async () => socket.open())
  fireEvent.click(screen.getByRole('link', { name: 'Mensajes' }))

  await act(async () => {
    socket.error()
    socket.close()
  })

  expect(screen.getByRole('alert')).toHaveTextContent(
    'No se pudo mantener la conexión del chat.',
  )
  fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }))
})
