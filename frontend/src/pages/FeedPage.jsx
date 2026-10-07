import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import PostCard from '../components/PostCard.jsx'
import useAuth from '../hooks/useAuth.js'
import { getFeed } from '../services/feed.js'
import usePostReactions from '../hooks/usePostReactions.js'

function FeedPage() {
  const { user } = useAuth()
  const location = useLocation()
  const [posts, setPosts] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const { likeError, pendingPostIds, toggleLike } = usePostReactions(setPosts)

  useEffect(() => {
    if (!user?.username) return undefined

    const abortController = new AbortController()

    getFeed(user.username, { signal: abortController.signal })
      .then((feed) => {
        if (!abortController.signal.aborted) setPosts(feed)
      })
      .catch(() => {
        if (!abortController.signal.aborted) {
          setPosts([])
          setLoadError('No pudimos cargar las publicaciones en este momento.')
        }
      })
      .finally(() => {
        if (!abortController.signal.aborted) setIsLoading(false)
      })

    return () => abortController.abort()
  }, [user?.username])

  return (
    <main className="feed-placeholder">
      <header className="feed-placeholder__header">
        <div>
          <p>Sesión activa</p>
          <h1>Hola, @{user?.username}</h1>
        </div>
        <Link className="primary-button feed-create-link" to="/publicar">
          ＋ Crear publicación
        </Link>
      </header>

      {location.state?.publicationCreated && (
        <p className="form-message form-message--success feed-alert" role="status">
          Tu publicación se creó correctamente.
        </p>
      )}
      {likeError && <p className="form-message form-message--error feed-alert" role="alert">{likeError}</p>}
      {isLoading && <p className="feed-status" role="status">Cargando publicaciones…</p>}
      {!isLoading && loadError && <p className="feed-status feed-status--error" role="alert">{loadError}</p>}
      {!isLoading && !loadError && posts.length === 0 && (
        <section className="feed-empty">
          <span aria-hidden="true">◇</span>
          <h2>Tu feed está al día</h2>
          <p>Cuando alguien publique contenido público, aparecerá aquí.</p>
        </section>
      )}

      {!isLoading && posts.length > 0 && (
        <section className="feed-list" aria-label="Publicaciones">
          {posts.map((post) => {
            const isPending = pendingPostIds.includes(post.id)

            return (
              <PostCard
                isPending={isPending}
                key={post.id}
                onToggleLike={toggleLike}
                post={post}
              />
            )
          })}
        </section>
      )}
    </main>
  )
}

export default FeedPage
