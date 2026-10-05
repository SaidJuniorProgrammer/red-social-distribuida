import PushNotificationCard from '../components/PushNotificationCard.jsx'

function NotificationsPage() {
  return (
    <main className="feed-placeholder">
      <header className="feed-placeholder__header">
        <div>
          <p>Preferencias</p>
          <h1>Notificaciones</h1>
        </div>
      </header>
      <PushNotificationCard />
    </main>
  )
}

export default NotificationsPage
