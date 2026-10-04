import PushNotificationCard from '../components/PushNotificationCard.jsx'
import useAuth from '../hooks/useAuth.js'

function FeedPage() {
  const { user } = useAuth()

  return (
    <main className="feed-placeholder">
      <header className="feed-placeholder__header">
        <div>
          <p>Sesión activa</p>
          <h1>Hola, @{user?.username}</h1>
        </div>
      </header>
      <section className="feed-placeholder__card">
        <span className="auth-card__icon" aria-hidden="true">✓</span>
        <h2>Tu identidad está conectada</h2>
        <p>
          La ruta está protegida y tu sesión se mantiene en este navegador.
          Las publicaciones se incorporarán en el issue correspondiente.
        </p>
      </section>
      <PushNotificationCard username={user?.username} />
    </main>
  )
}

export default FeedPage
