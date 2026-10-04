import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import useAuth from '../hooks/useAuth.js'
import {
  CHAT_CONNECTION_STATUS,
  ChatWebSocketClient,
} from '../services/websocket.js'
import ChatContext from './chatContext.js'

const RECONNECT_DELAY = 2_000

function ChatProvider({ children }) {
  const { isAuthenticated, token, user } = useAuth()
  const [messages, setMessages] = useState([])
  const [status, setStatus] = useState(CHAT_CONNECTION_STATUS.disconnected)
  const [connectionError, setConnectionError] = useState('')
  const clientRef = useRef(null)
  const reconnectTimeoutRef = useRef(null)
  const shouldReconnectRef = useRef(false)

  useEffect(() => {
    if (!isAuthenticated || !user?.username) {
      return undefined
    }

    shouldReconnectRef.current = true

    const connect = () => {
      const client = new ChatWebSocketClient({
        token,
        username: user.username,
        onMessage: (message) => {
          setMessages((currentMessages) =>
            currentMessages.some(({ id }) => id === message.id)
              ? currentMessages
              : [...currentMessages, message],
          )
        },
        onStatusChange: (nextStatus, errorMessage = '') => {
          setStatus(nextStatus)
          if (errorMessage || nextStatus === CHAT_CONNECTION_STATUS.connected) {
            setConnectionError(errorMessage)
          }

          if (
            nextStatus === CHAT_CONNECTION_STATUS.disconnected &&
            shouldReconnectRef.current
          ) {
            window.clearTimeout(reconnectTimeoutRef.current)
            reconnectTimeoutRef.current = window.setTimeout(
              connect,
              RECONNECT_DELAY,
            )
          }
        },
      })

      clientRef.current = client

      try {
        client.connect()
      } catch (error) {
        setStatus(CHAT_CONNECTION_STATUS.error)
        setConnectionError(error.message)
      }
    }

    connect()

    return () => {
      shouldReconnectRef.current = false
      window.clearTimeout(reconnectTimeoutRef.current)
      clientRef.current?.disconnect()
      clientRef.current = null
      setMessages([])
      setConnectionError('')
    }
  }, [isAuthenticated, token, user?.username])

  const sendMessage = useCallback((recipient, content) => {
    const normalizedRecipient = recipient.trim()
    const normalizedContent = content.trim()

    if (!normalizedRecipient || !normalizedContent) {
      throw new Error('El destinatario y el mensaje son obligatorios.')
    }

    const sentMessage = clientRef.current?.sendMessage(
      normalizedRecipient,
      normalizedContent,
    )

    if (!sentMessage) {
      throw new Error('El chat todavía no está conectado.')
    }

    setMessages((currentMessages) =>
      currentMessages.some(({ id }) => id === sentMessage.id)
        ? currentMessages
        : [...currentMessages, sentMessage],
    )
    return sentMessage
  }, [])

  const value = useMemo(
    () => ({ connectionError, messages, sendMessage, status }),
    [connectionError, messages, sendMessage, status],
  )

  return <ChatContext.Provider value={value}>{children}</ChatContext.Provider>
}

export default ChatProvider
