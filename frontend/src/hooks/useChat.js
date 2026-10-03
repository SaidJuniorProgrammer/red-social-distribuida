import { useContext } from 'react'
import ChatContext from '../context/chatContext.js'

function useChat() {
  const context = useContext(ChatContext)

  if (!context) {
    throw new Error('useChat debe utilizarse dentro de ChatProvider')
  }

  return context
}

export default useChat
