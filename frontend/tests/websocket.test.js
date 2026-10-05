import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  CHAT_CONNECTION_STATUS,
  ChatWebSocketClient,
  buildAuthenticationSubprotocols,
  buildChatWebSocketUrl,
  parseChatMessage,
} from '../src/services/websocket.js'

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
  it('construye la ruta y los subprotocolos de autenticación', () => {
    expect(buildChatWebSocketUrl('wss://api.example.test/')).toBe(
      'wss://api.example.test/chat',
    )
    expect(buildAuthenticationSubprotocols('jwt-prueba')).toEqual([
      'bearer-token-carrier',
      encodeURIComponent(
        'quarkus-http-upgrade#Authorization#Bearer jwt-prueba',
      ),
    ])
    expect(() => buildAuthenticationSubprotocols('')).toThrow(
      'La sesión no tiene un token válido para abrir el chat.',
    )
  })

  it('abre la conexión, recibe mensajes y envía el contrato esperado', () => {
    const statuses = []
    const receivedMessages = []
    const deliveryErrors = []
    const client = new ChatWebSocketClient({
      token: 'jwt-prueba',
      username: 'oscar',
      WebSocketImpl: MockWebSocket,
      onMessage: (message) => receivedMessages.push(message),
      onDeliveryError: (message) => deliveryErrors.push(message),
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
    socket.receive({
      type: 'history',
      messages: [
        {
          id: 'mensaje-anterior',
          emisor_id: 'oscar',
          destinatario_id: 'said',
          contenido: 'Mensaje guardado',
          timestamp: '2026-09-30T12:00:00Z',
        },
      ],
    })
    const sentMessage = client.sendMessage('said', 'Todo listo')
    socket.receive({
      type: 'delivery_error',
      destinatario_id: 'said',
      message: 'Said no está conectado.',
    })

    expect(socket.url).toBe('ws://localhost:8080/chat')
    expect(socket.protocols[0]).toBe('bearer-token-carrier')
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
      {
        id: 'mensaje-anterior',
        emisor: 'oscar',
        destinatario: 'said',
        contenido: 'Mensaje guardado',
        timestamp: '2026-09-30T12:00:00Z',
      },
    ])
    expect(JSON.parse(socket.sentMessages[0])).toEqual({
      destinatario_id: 'said',
      contenido: 'Todo listo',
      timestamp: expect.any(String),
    })
    expect(sentMessage.id).toEqual(expect.any(String))
    expect(deliveryErrors).toEqual(['Said no está conectado.'])
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

  it('ignora eventos tardíos de una conexión descartada', () => {
    const statuses = []
    const receivedMessages = []
    const client = new ChatWebSocketClient({
      token: 'jwt-prueba',
      username: 'oscar',
      WebSocketImpl: MockWebSocket,
      onMessage: (message) => receivedMessages.push(message),
      onStatusChange: (status) => statuses.push(status),
    })

    client.connect()
    const discardedSocket = MockWebSocket.instances[0]
    client.disconnect()
    client.connect()
    const activeSocket = MockWebSocket.instances[1]
    activeSocket.open()

    discardedSocket.receive({
      emisor: 'said',
      destinatario: 'oscar',
      contenido: 'Mensaje tardío',
    })
    discardedSocket.onerror?.()

    expect(receivedMessages).toEqual([])
    expect(statuses.at(-1)).toBe(CHAT_CONNECTION_STATUS.connected)
  })
})
