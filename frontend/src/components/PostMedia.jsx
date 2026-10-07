import { useState } from 'react'

function PostMedia({ src, type, label, className = '' }) {
  const [failedMedia, setFailedMedia] = useState(null)
  const [attempt, setAttempt] = useState(0)
  const mediaIdentity = `${type}:${src}`
  const hasError = failedMedia === mediaIdentity

  const retry = () => {
    setAttempt((current) => current + 1)
    setFailedMedia(null)
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
      aria-label={label} onError={() => setFailedMedia(mediaIdentity)}>
      Tu navegador no admite este video.
    </video>
  }
  return <img key={attempt} className={className} src={src} alt={label} loading="lazy"
    onError={() => setFailedMedia(mediaIdentity)} />
}

export default PostMedia
