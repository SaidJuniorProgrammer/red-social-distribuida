import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  CHAT_CONNECTION_STATUS,
  ChatWebSocketClient,
  buildChatWebSocketUrl,
  parseChatMessage,
} from '../src/services/websocket.js'

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

  close() {
    this.readyState = MockWebSocket.CLOSED
    this.onclose?.()
  }
}

afterEach(() => {
  MockWebSocket.instances = []
  vi.unstubAllEnvs()
})

describe('cliente WebSocket del chat', () => {
  it('construye una ruta segura para el nombre de usuario', () => {
    expect(buildChatWebSocketUrl('óscar demo', 'wss://api.example.test/')).toBe(
      'wss://api.example.test/chat/%C3%B3scar%20demo',
    )
  })

  it('abre la conexión, recibe mensajes y envía el contrato esperado', () => {
    const statuses = []
    const receivedMessages = []
    const client = new ChatWebSocketClient({
      username: 'oscar',
      WebSocketImpl: MockWebSocket,
      onMessage: (message) => receivedMessages.push(message),
      onStatusChange: (status) => statuses.push(status),
    })

    client.connect()
    const socket = MockWebSocket.instances[0]
    socket.open()
    socket.receive({
      id: 'mensaje-1',
      emisor: 'said',
      destinatario: 'oscar',
      contenido: 'Hola, Oscar',
      timestamp: '2026-10-01T15:00:00Z',
    })
    const sentMessage = client.sendMessage('said', 'Todo listo')

    expect(socket.url).toBe('ws://localhost:8080/chat/oscar')
    expect(statuses).toEqual([
      CHAT_CONNECTION_STATUS.connecting,
      CHAT_CONNECTION_STATUS.connected,
    ])
    expect(receivedMessages).toEqual([
      {
        id: 'mensaje-1',
        emisor: 'said',
        destinatario: 'oscar',
        contenido: 'Hola, Oscar',
        timestamp: '2026-10-01T15:00:00Z',
      },
    ])
    expect(JSON.parse(socket.sentMessages[0])).toEqual({
      emisor_id: 'oscar',
      destinatario_id: 'said',
      contenido: 'Todo listo',
      timestamp: expect.any(String),
    })
    expect(sentMessage.id).toEqual(expect.any(String))
  })

  it('normaliza el contrato anterior basado en campos con sufijo id', () => {
    expect(
      parseChatMessage(
        JSON.stringify({
          id: 'mensaje-2',
          emisor_id: 'said',
          destinatario_id: 'oscar',
          contenido: 'Mensaje compatible',
          timestamp: '2026-10-01T15:10:00Z',
        }),
      ),
    ).toMatchObject({
      emisor: 'said',
      destinatario: 'oscar',
      contenido: 'Mensaje compatible',
    })
  })
})
