import { useEffect, useMemo, useRef, useState } from 'react'
import BrandMark from '../components/BrandMark.jsx'
import useAuth from '../hooks/useAuth.js'
import useChat from '../hooks/useChat.js'
import { CHAT_CONNECTION_STATUS } from '../services/websocket.js'
import { formatDisplayName, getInitial } from '../utils/userDisplay.js'

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
  const { user } = useAuth()
  const { connectionError, messages, sendMessage, status } = useChat()
  const [recipient, setRecipient] = useState('')
  const [content, setContent] = useState('')
  const [sendError, setSendError] = useState('')
  const [isMobileChatOpen, setIsMobileChatOpen] = useState(false)
  const recipientInputRef = useRef(null)
  const messagesEndRef = useRef(null)
  const activeRecipient = recipient.trim()
  const isConnected = status === CHAT_CONNECTION_STATUS.connected
  const statusLabel = statusLabels[status] ?? 'Sin conexión'
  const newestMessage = messages.at(-1)
  const messageAnnouncement = newestMessage &&
    newestMessage.emisor !== user?.username &&
    newestMessage.emisor !== activeRecipient
    ? `Nuevo mensaje de @${newestMessage.emisor}: ${newestMessage.contenido}`
    : ''

  const conversations = useMemo(() => {
    const conversationsByUsername = new Map()

    messages.forEach((message) => {
      const username = message.emisor === user?.username
        ? message.destinatario
        : message.emisor

      if (username) {
        conversationsByUsername.delete(username)
        conversationsByUsername.set(username, {
          lastMessage: message,
          username,
        })
      }
    })

    if (activeRecipient && !conversationsByUsername.has(activeRecipient)) {
      conversationsByUsername.set(activeRecipient, {
        lastMessage: null,
        username: activeRecipient,
      })
    }

    return [...conversationsByUsername.values()].reverse()
  }, [activeRecipient, messages, user?.username])

  const activeMessages = useMemo(() => {
    if (!activeRecipient) return []

    return messages.filter((message) => (
      message.emisor === user?.username &&
      message.destinatario === activeRecipient
    ) || (
      message.emisor === activeRecipient &&
      message.destinatario === user?.username
    ))
  }, [activeRecipient, messages, user?.username])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView?.({ behavior: 'smooth' })
  }, [activeMessages])

  const startNewConversation = () => {
    setRecipient('')
    setContent('')
    setSendError('')
    setIsMobileChatOpen(false)
    recipientInputRef.current?.focus()
  }

  const selectConversation = (username) => {
    if (username !== activeRecipient) {
      setContent('')
    }
    setRecipient(username)
    setSendError('')
    setIsMobileChatOpen(true)
  }

  const changeRecipient = (nextRecipient) => {
    if (nextRecipient.trim() !== activeRecipient) {
      setContent('')
    }
    setRecipient(nextRecipient)
    setSendError('')
  }

  const submitMessage = () => {
    setSendError('')

    try {
      sendMessage(recipient, content)
      setContent('')
    } catch (error) {
      setSendError(error.message)
    }
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    submitMessage()
  }

  const handleComposerKeyDown = (event) => {
    if (
      event.key !== 'Enter' ||
      event.shiftKey ||
      event.nativeEvent.isComposing ||
      !isConnected ||
      !activeRecipient ||
      !content.trim()
    ) {
      return
    }

    event.preventDefault()
    submitMessage()
  }

  return (
    <main className={`messages-layout${isMobileChatOpen ? ' messages-layout--chat-open' : ''}`}>
      <section className="conversation-list" aria-label="Conversaciones">
        <header className="conversation-list__header">
          <div>
            <span className="conversation-list__mobile-brand"><BrandMark /></span>
            <h1>Mensajes</h1>
            <p>Chat en tiempo real</p>
          </div>
          <button type="button" aria-label="Nueva conversación" onClick={startNewConversation}>＋</button>
        </header>

        <div className="chat-recipient">
          <label htmlFor="chat-recipient">Enviar mensajes a</label>
          <div>
            <span aria-hidden="true">@</span>
            <input
              id="chat-recipient"
              ref={recipientInputRef}
              type="text"
              value={recipient}
              placeholder="nombre_de_usuario"
              autoComplete="off"
              onChange={({ target }) => changeRecipient(target.value)}
            />
          </div>
        </div>

        <div className="conversation-list__summary">
          <h2>Conversaciones de esta sesión</h2>
          <span>{conversations.length} {conversations.length === 1 ? 'activa' : 'activas'}</span>
        </div>

        <div className="conversation-list__items">
          {conversations.length === 0 ? (
            <div className="conversation-list__empty">
              <span aria-hidden="true">✉</span>
              <p>Tus conversaciones aparecerán aquí.</p>
            </div>
          ) : conversations.map(({ lastMessage, username }) => (
            <button
              className={`conversation-card${username === activeRecipient ? ' conversation-card--active' : ''}`}
              type="button"
              key={username}
              onClick={() => selectConversation(username)}
            >
              <span className="messages-avatar conversation-card__avatar" aria-hidden="true">
                {getInitial(username)}
              </span>
              <span className="conversation-card__content">
                <span>
                  <strong>{formatDisplayName(username)}</strong>
                  <time dateTime={lastMessage?.timestamp}>
                    {lastMessage ? formatMessageTime(lastMessage.timestamp) : 'Ahora'}
                  </time>
                </span>
                <small>@{username}</small>
                <p>{lastMessage?.contenido ?? 'Nueva conversación'}</p>
              </span>
            </button>
          ))}
        </div>
        <p className="sr-only" aria-live="polite" aria-atomic="true">
          {messageAnnouncement}
        </p>

        <footer className="conversation-list__footer">
          <span className={`connection-dot connection-dot--${status}`} aria-hidden="true" />
          {isConnected ? 'Sesión WebSocket lista' : statusLabel}
        </footer>
      </section>

      <section className="chat-room" aria-label="Conversación">
        <header className="chat-room__header">
          <button
            className="chat-room__back"
            type="button"
            aria-label="Volver a conversaciones"
            onClick={() => setIsMobileChatOpen(false)}
          >
            ←
          </button>
          <span className="messages-avatar" aria-hidden="true">
            {getInitial(activeRecipient)}
          </span>
          <div className="chat-room__identity">
            <strong>{formatDisplayName(activeRecipient)}</strong>
            <span>{activeRecipient ? `@${activeRecipient}` : 'Selecciona un usuario'}</span>
          </div>
          <p className={`chat-status chat-status--${status}`} role="status">
            <span aria-hidden="true" />
            {statusLabel}
          </p>
        </header>

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
          {activeMessages.length === 0 ? (
            <div className="chat-empty">
              <span aria-hidden="true">✉</span>
              <h2>Inicia una conversación</h2>
              <p>Elige un usuario y envíale un mensaje. La respuesta aparecerá aquí al instante.</p>
            </div>
          ) : (
            <>
              <p className="chat-messages__day">Conversación iniciada hoy</p>
              {activeMessages.map((message) => {
                const isOwnMessage = message.emisor === user?.username

                return (
                  <article
                    className={`chat-message${isOwnMessage ? ' chat-message--own' : ''}`}
                    key={message.id}
                  >
                    {!isOwnMessage && (
                      <span className="messages-avatar chat-message__avatar" aria-hidden="true">
                        {getInitial(message.emisor)}
                      </span>
                    )}
                    <div className="chat-message__bubble">
                      <p>{message.contenido}</p>
                      <time dateTime={message.timestamp}>
                        {formatMessageTime(message.timestamp)}
                      </time>
                    </div>
                  </article>
                )
              })}
            </>
          )}
          <div ref={messagesEndRef} />
        </div>

        <form className="chat-composer" onSubmit={handleSubmit}>
          <label className="sr-only" htmlFor="chat-message">Mensaje</label>
          <div>
            <textarea
              id="chat-message"
              rows="1"
              value={content}
              placeholder={isConnected ? 'Escribe un mensaje…' : 'Esperando conexión…'}
              disabled={!isConnected}
              onChange={({ target }) => {
                setContent(target.value)
                setSendError('')
              }}
              onKeyDown={handleComposerKeyDown}
            />
            <small>Presiona Enter para enviar · Shift+Enter para una nueva línea</small>
          </div>
          <button
            className="primary-button"
            type="submit"
            disabled={!isConnected || !activeRecipient || !content.trim()}
          >
            Enviar <span aria-hidden="true">↗</span>
          </button>
        </form>

        <footer className="chat-room__footer">
          Pachyweb Realtime · nodo de mensajería activo
        </footer>
      </section>
    </main>
  )
}

export default ChatPage
