const DEFAULT_WEBSOCKET_BASE_URL = 'ws://localhost:8080'

function createMessageId() {
  return globalThis.crypto?.randomUUID?.() ??
    `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export const CHAT_CONNECTION_STATUS = Object.freeze({
  connecting: 'connecting',
  connected: 'connected',
  disconnected: 'disconnected',
  error: 'error',
})

function apiUrlToWebSocketUrl(apiUrl) {
  try {
    const url = new URL(apiUrl)
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
    url.pathname = url.pathname.replace(/\/api\/?$/, '')
    url.search = ''
    url.hash = ''
    return url.toString().replace(/\/$/, '')
  } catch {
    return DEFAULT_WEBSOCKET_BASE_URL
  }
}

export function getWebSocketBaseUrl() {
  if (import.meta.env.VITE_WEBSOCKET_BASE_URL) {
    return import.meta.env.VITE_WEBSOCKET_BASE_URL.replace(/\/$/, '')
  }

  if (import.meta.env.VITE_API_BASE_URL) {
    return apiUrlToWebSocketUrl(import.meta.env.VITE_API_BASE_URL)
  }

  return DEFAULT_WEBSOCKET_BASE_URL
}

export function buildChatWebSocketUrl(
  username,
  baseUrl = getWebSocketBaseUrl(),
) {
  return `${baseUrl.replace(/\/$/, '')}/chat/${encodeURIComponent(username)}`
}

export function parseChatMessage(rawMessage) {
  const parsedMessage = JSON.parse(rawMessage)

  if (
    !parsedMessage ||
    typeof parsedMessage !== 'object' ||
    typeof parsedMessage.contenido !== 'string'
  ) {
    throw new TypeError('El mensaje recibido no cumple el contrato del chat.')
  }

  return {
    id: parsedMessage.id ?? createMessageId(),
    emisor: parsedMessage.emisor ?? parsedMessage.emisor_id ?? 'desconocido',
    destinatario:
      parsedMessage.destinatario ?? parsedMessage.destinatario_id ?? '',
    contenido: parsedMessage.contenido,
    timestamp: parsedMessage.timestamp ?? new Date().toISOString(),
  }
}

export class ChatWebSocketClient {
  constructor({ onMessage, onStatusChange, username, WebSocketImpl }) {
    this.username = username
    this.onMessage = onMessage
    this.onStatusChange = onStatusChange
    this.WebSocketImpl = WebSocketImpl ?? globalThis.WebSocket
    this.socket = null
  }

  connect() {
    if (!this.WebSocketImpl) {
      throw new Error('Este navegador no admite conexiones WebSocket.')
    }

    if (
      this.socket?.readyState === this.WebSocketImpl.OPEN ||
      this.socket?.readyState === this.WebSocketImpl.CONNECTING
    ) {
      return
    }

    this.onStatusChange?.(CHAT_CONNECTION_STATUS.connecting)
    const socket = new this.WebSocketImpl(
      buildChatWebSocketUrl(this.username),
    )
    this.socket = socket

    socket.onopen = () => {
      this.onStatusChange?.(CHAT_CONNECTION_STATUS.connected)
    }

    socket.onmessage = ({ data }) => {
      try {
        this.onMessage?.(parseChatMessage(data))
      } catch {
        this.onStatusChange?.(
          CHAT_CONNECTION_STATUS.connected,
          'Se recibió un mensaje con un formato no válido.',
        )
      }
    }

    socket.onerror = () => {
      this.onStatusChange?.(
        CHAT_CONNECTION_STATUS.error,
        'No se pudo mantener la conexión del chat.',
      )
    }

    socket.onclose = () => {
      if (this.socket === socket) {
        this.socket = null
        this.onStatusChange?.(CHAT_CONNECTION_STATUS.disconnected)
      }
    }
  }

  sendMessage(destinatario, contenido) {
    if (
      !this.socket ||
      this.socket.readyState !== this.WebSocketImpl.OPEN
    ) {
      throw new Error('El chat todavía no está conectado.')
    }

    const message = {
      id: createMessageId(),
      emisor: this.username,
      destinatario,
      contenido,
      timestamp: new Date().toISOString(),
    }

    // La pantalla usa nombres breves, pero al enviar respetamos los nombres
    // acordados con el servidor.
    this.socket.send(JSON.stringify({
      emisor_id: message.emisor,
      destinatario_id: message.destinatario,
      contenido: message.contenido,
      timestamp: message.timestamp,
    }))
    return message
  }

  disconnect() {
    const socket = this.socket
    this.socket = null

    if (
      socket &&
      (socket.readyState === this.WebSocketImpl.OPEN ||
        socket.readyState === this.WebSocketImpl.CONNECTING)
    ) {
      socket.close(1000, 'Sesión finalizada')
    }

    this.onStatusChange?.(CHAT_CONNECTION_STATUS.disconnected)
  }
}
