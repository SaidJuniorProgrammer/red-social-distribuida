import PushNotificationCard from '../components/PushNotificationCard.jsx'
import { Link } from 'react-router-dom'
import useNotifications from '../hooks/useNotifications.js'
import { formatPublishedAt } from '../utils/dateTime.js'
import {
  getNotificationActor,
  getNotificationDestination,
  getNotificationIcon,
  getNotificationKey,
  getNotificationMessage,
  getNotificationTimestamp,
  getNotificationTypeLabel,
  isNotificationRead,
} from '../utils/notifications.js'

function NotificationsPage() {
  const { error, loading, markAllAsRead, markAsRead, notifications, refresh, unreadCount } = useNotifications()

  return (
    <main className="feed-placeholder">
      <header className="feed-placeholder__header">
        <div>
          <p>Actividad de tu cuenta</p>
          <h1>Notificaciones</h1>
        </div>
        <div className="notification-inbox__actions">
          {unreadCount > 0 && (
            <button type="button" className="secondary-button" onClick={() => void markAllAsRead()}>
              Marcar todas como leídas
            </button>
          )}
          <button type="button" className="secondary-button" onClick={() => void refresh()}>Actualizar</button>
        </div>
      </header>
      <section className="notification-inbox" aria-label="Actividad reciente">
        {loading && <p role="status">Cargando notificaciones…</p>}
        {error && <p role="alert" className="form-message form-message--error">{error}</p>}
        {!loading && !error && !notifications.length && <p>No tienes notificaciones por ahora.</p>}
        <ul>
          {notifications.map((item) => {
            const actor = getNotificationActor(item)
            const timestamp = getNotificationTimestamp(item)

            return (
              <li className={`notification-inbox__item${isNotificationRead(item) ? '' : ' notification-inbox__unread'}`}
                key={getNotificationKey(item)}>
                <span className="notification-inbox__icon" aria-hidden="true">{getNotificationIcon(item)}</span>
                <div className="notification-inbox__body">
                  <div className="notification-inbox__heading">
                    {actor && <strong>@{actor}</strong>}
                    <span className="notification-inbox__type">{getNotificationTypeLabel(item)}</span>
                  </div>
                  <p>{getNotificationMessage(item)}</p>
                  {timestamp && <time dateTime={timestamp}>{formatPublishedAt(timestamp)}</time>}
                </div>
                <Link
                  className="primary-button notification-inbox__open"
                  to={getNotificationDestination(item)}
                  onClick={() => void markAsRead(item)}
                >
                  Abrir notificación
                </Link>
              </li>
            )
          })}
        </ul>
        <p className="notification-inbox__note">Puedes consultar la actividad aunque no actives las alertas del navegador.</p>
      </section>
      <PushNotificationCard />
    </main>
  )
}

export default NotificationsPage
