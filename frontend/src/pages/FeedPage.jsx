import BrandMark from '../components/BrandMark.jsx'
import useAuth from '../hooks/useAuth.js'

function FeedPage() {
  const { logout, user } = useAuth()

  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <BrandMark />
        <nav aria-label="Navegación principal">
          <a className="app-sidebar__active" href="/feed">Inicio</a>
          <span>Explorar</span>
          <span>Notificaciones</span>
          <span>Mensajes</span>
          <span>Perfil</span>
        </nav>
        <button className="primary-button" type="button" disabled>Publicar</button>
      </aside>
      <main className="feed-placeholder">
        <header className="feed-placeholder__header">
          <div>
            <p>Sesión activa</p>
            <h1>Hola, @{user?.username}</h1>
          </div>
          <button className="secondary-button" type="button" onClick={logout}>
            Cerrar sesión
          </button>
        </header>
        <section className="feed-placeholder__card">
          <span className="auth-card__icon" aria-hidden="true">✓</span>
          <h2>Tu identidad está conectada</h2>
          <p>
            La ruta está protegida y tu sesión se mantiene en este navegador.
            Las publicaciones se incorporarán en el issue correspondiente.
          </p>
        </section>
      </main>
    </div>
  )
}

export default FeedPage
