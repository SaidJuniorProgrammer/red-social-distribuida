import BrandMark from './BrandMark.jsx'

function AuthLayout({ children, description, eyebrow, title, visual }) {
  return (
    <div className="auth-shell">
      <header className="auth-header">
        <BrandMark />
        <span className="node-badge">
          <span className="node-badge__dot" />
          nodo.local
        </span>
      </header>

      <main className="auth-main">
        <section className="auth-story" aria-labelledby="auth-story-title">
          <p className="auth-story__eyebrow">{eyebrow}</p>
          <h1 id="auth-story-title">{title}</h1>
          <p className="auth-story__description">{description}</p>
          {visual}
        </section>

        <section className="auth-card">{children}</section>
      </main>

      <footer className="auth-footer">
        <span>Protocolo federado Pachyweb 2.4</span>
        <span>Cifrado extremo a extremo</span>
      </footer>
    </div>
  )
}

export default AuthLayout
