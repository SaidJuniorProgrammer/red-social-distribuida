import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
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

  constructor(url) {
    this.url = url
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
  expect(socket.url).toBe('ws://localhost:8080/chat/oscar')

  await act(async () => socket.open())
  fireEvent.click(screen.getByRole('link', { name: 'Mensajes' }))
  expect(screen.getByRole('status')).toHaveTextContent('En línea')

  fireEvent.change(screen.getByLabelText('Enviar mensajes a'), {
    target: { value: 'said' },
  })
  fireEvent.change(screen.getByLabelText('Mensaje'), {
    target: { value: 'Hola desde React' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Enviar' }))

  expect(screen.getByText('Hola desde React')).toBeInTheDocument()
  expect(JSON.parse(socket.sentMessages[0])).toMatchObject({
    emisor_id: 'oscar',
    destinatario_id: 'said',
    contenido: 'Hola desde React',
  })

  await act(async () => {
    socket.receive({
      emisor_id: 'said',
      destinatario_id: 'oscar',
      contenido: 'Recibido en tiempo real',
      timestamp: '2026-10-01T15:30:00Z',
    })
  })

  expect(screen.getByText('Recibido en tiempo real')).toBeInTheDocument()
  expect(screen.getByText('@said')).toBeInTheDocument()

  fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }))
  expect(socket.closeArgs).toEqual([1000, 'Sesión finalizada'])
  expect(
    await screen.findByRole('heading', { name: 'Iniciar sesión' }),
  ).toBeInTheDocument()
})
