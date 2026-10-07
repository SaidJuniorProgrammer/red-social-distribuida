import PushNotificationCard from '../components/PushNotificationCard.jsx'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import useAuth from '../hooks/useAuth.js'
import { getNotifications } from '../services/webpush.js'
import { formatPublishedAt } from '../utils/dateTime.js'

function NotificationsPage() {
  const { user } = useAuth()
  const [notifications, setNotifications] = useState([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    let timer
    const refresh = async () => {
      try {
        const result = await getNotifications(user.username, { signal: controller.signal })
        if (controller.signal.aborted) return
        setNotifications([...result].sort((a, b) => String(b.timestamp).localeCompare(String(a.timestamp))))
        setError('')
      } catch {
        if (!controller.signal.aborted) setError('No pudimos cargar la actividad. Inténtalo nuevamente.')
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false)
          timer = window.setTimeout(refresh, 30_000)
        }
      }
    }
    refresh()
    return () => {
      controller.abort()
      window.clearTimeout(timer)
    }
  }, [user.username, revision])

  return (
    <main className="feed-placeholder">
      <header className="feed-placeholder__header">
        <div>
          <p>Actividad de tu cuenta</p>
          <h1>Notificaciones</h1>
        </div>
        <button type="button" className="secondary-button" onClick={() => setRevision((value) => value + 1)}>Actualizar</button>
      </header>
      <section className="notification-inbox" aria-label="Actividad reciente">
        {loading && <p role="status">Cargando notificaciones…</p>}
        {error && <p role="alert" className="form-message form-message--error">{error}</p>}
        {!loading && !error && !notifications.length && <p>No tienes notificaciones por ahora.</p>}
        <ul>
          {notifications.map((item, index) => (
            <li key={`${item.id_post}-${item.autor}-${item.timestamp}-${index}`}>
              <strong>{item.titulo}</strong>
              <p>{item.mensaje}</p>
              {item.timestamp && <time dateTime={item.timestamp}>{formatPublishedAt(item.timestamp)}</time>}
              <Link to={`/perfil/${encodeURIComponent(item.autor)}`}>Ver perfil de @{item.autor}</Link>
            </li>
          ))}
        </ul>
        <p className="notification-inbox__note">La actividad se muestra sin activar las alertas del navegador. Por ahora, el historial se reinicia cuando se reinicia el servidor.</p>
      </section>
      <PushNotificationCard />
    </main>
  )
}

export default NotificationsPage
