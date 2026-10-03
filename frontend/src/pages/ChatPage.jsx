import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import BrandMark from '../components/BrandMark.jsx'
import useAuth from '../hooks/useAuth.js'
import useChat from '../hooks/useChat.js'
import { CHAT_CONNECTION_STATUS } from '../services/websocket.js'

const statusLabels = {
  [CHAT_CONNECTION_STATUS.connecting]: 'Conectando…',
  [CHAT_CONNECTION_STATUS.connected]: 'En línea',
  [CHAT_CONNECTION_STATUS.disconnected]: 'Sin conexión',
  [CHAT_CONNECTION_STATUS.error]: 'Error de conexión',
}

function formatMessageTime(timestamp) {
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return ''

  return new Intl.DateTimeFormat('es-EC', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function ChatPage() {
  const { logout, user } = useAuth()
  const { connectionError, messages, sendMessage, status } = useChat()
  const [recipient, setRecipient] = useState('')
  const [content, setContent] = useState('')
  const [sendError, setSendError] = useState('')
  const messagesEndRef = useRef(null)
  const isConnected = status === CHAT_CONNECTION_STATUS.connected
  const statusLabel = statusLabels[status] ?? 'Sin conexión'

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView?.({ behavior: 'smooth' })
  }, [messages])

  const handleSubmit = (event) => {
    event.preventDefault()
    setSendError('')

    try {
      sendMessage(recipient, content)
      setContent('')
    } catch (error) {
      setSendError(error.message)
    }
  }

  return (
    <main className="chat-shell">
      <aside className="chat-sidebar">
        <BrandMark />
        <div>
          <p className="chat-sidebar__eyebrow">Sesión activa</p>
          <strong>@{user?.username}</strong>
        </div>
        <nav aria-label="Navegación principal">
          <Link to="/feed">Inicio</Link>
          <Link className="chat-sidebar__active" to="/chat">Mensajes</Link>
        </nav>
        <button className="secondary-button" type="button" onClick={logout}>
          Cerrar sesión
        </button>
      </aside>

      <section className="chat-panel" aria-label="Conversación">
        <header className="chat-header">
          <div>
            <p>Comunicación distribuida</p>
            <h1>Chat en tiempo real</h1>
          </div>
          <p className={`chat-status chat-status--${status}`} role="status">
            <span aria-hidden="true" />
            {statusLabel}
          </p>
        </header>

        <div className="chat-recipient">
          <label htmlFor="chat-recipient">Enviar mensajes a</label>
          <div>
            <span aria-hidden="true">@</span>
            <input
              id="chat-recipient"
              type="text"
              value={recipient}
              placeholder="nombre_de_usuario"
              autoComplete="off"
              onChange={({ target }) => {
                setRecipient(target.value)
                setSendError('')
              }}
            />
          </div>
        </div>

        {(connectionError || sendError) && (
          <p className="form-message form-message--error chat-alert" role="alert">
            {sendError || connectionError}
          </p>
        )}

        <div
          className="chat-messages"
          aria-live="polite"
          aria-relevant="additions"
        >
          {messages.length === 0 ? (
            <div className="chat-empty">
              <span aria-hidden="true">↗</span>
              <h2>Inicia una conversación</h2>
              <p>Elige un usuario y envíale un mensaje. La respuesta aparecerá aquí al instante.</p>
            </div>
          ) : (
            messages.map((message) => {
              const isOwnMessage = message.emisor === user?.username

              return (
                <article
                  className={`chat-message${isOwnMessage ? ' chat-message--own' : ''}`}
                  key={message.id}
                >
                  <div>
                    <strong>{isOwnMessage ? 'Tú' : `@${message.emisor}`}</strong>
                    <time dateTime={message.timestamp}>
                      {formatMessageTime(message.timestamp)}
                    </time>
                  </div>
                  <p>{message.contenido}</p>
                </article>
              )
            })
          )}
          <div ref={messagesEndRef} />
        </div>

        <form className="chat-composer" onSubmit={handleSubmit}>
          <label className="sr-only" htmlFor="chat-message">Mensaje</label>
          <textarea
            id="chat-message"
            rows="2"
            value={content}
            placeholder={
              isConnected ? 'Escribe un mensaje…' : 'Esperando conexión…'
            }
            disabled={!isConnected}
            onChange={({ target }) => {
              setContent(target.value)
              setSendError('')
            }}
          />
          <button
            className="primary-button"
            type="submit"
            disabled={!isConnected || !recipient.trim() || !content.trim()}
          >
            Enviar
          </button>
        </form>
      </section>
    </main>
  )
}

export default ChatPage
