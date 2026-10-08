import { useContext } from 'react'
import NotificationsContext from '../context/notificationsContext.js'

function useNotifications() {
  const context = useContext(NotificationsContext)
  if (!context) throw new Error('useNotifications debe utilizarse dentro de NotificationsProvider')
  return context
}

export default useNotifications
