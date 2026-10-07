import { useState } from 'react'

function MediaContent({ src, type, label, className }) {
  const [hasError, setHasError] = useState(false)
  const [attempt, setAttempt] = useState(0)

  const retry = () => {
    setAttempt((current) => current + 1)
    setHasError(false)
  }

  if (hasError) {
    return (
      <div className={`${className} post-media--unavailable`.trim()} role="status">
        <span className="post-media--unavailable__icon" aria-hidden="true">◇</span>
        <strong>Contenido multimedia no disponible</strong>
        <p>No pudimos cargar este archivo.</p>
        <button type="button" className="secondary-button" onClick={retry}>
          Reintentar
        </button>
      </div>
    )
  }

  if (type === 'video') {
    return <video key={attempt} className={className} src={src} controls muted preload="metadata"
      aria-label={label} onError={() => setHasError(true)}>
      Tu navegador no admite este video.
    </video>
  }
  return <img key={attempt} className={className} src={src} alt={label} loading="lazy"
    onError={() => setHasError(true)} />
}

function PostMedia({ src, type, label, className = '' }) {
  const mediaIdentity = `${type}:${src}`
  return <MediaContent key={mediaIdentity} src={src} type={type} label={label}
    className={className} />
}

export default PostMedia
