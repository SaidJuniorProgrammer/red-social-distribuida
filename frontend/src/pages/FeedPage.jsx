import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import useAuth from '../hooks/useAuth.js'
import { getFeed, likePost, unlikePost } from '../services/feed.js'
import { formatDisplayName, getInitial } from '../utils/userDisplay.js'

function formatPublishedAt(value) {
  if (!value) return ''

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value

  return new Intl.DateTimeFormat('es-EC', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

function FeedPage() {
  const { user } = useAuth()
  const location = useLocation()
  const [posts, setPosts] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [likeError, setLikeError] = useState('')
  const [pendingPostIds, setPendingPostIds] = useState([])

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

  const toggleLike = async (post) => {
    if (pendingPostIds.includes(post.id)) return

    const nextLiked = !post.liked
    const reactionChange = nextLiked ? 1 : -1
    setLikeError('')
    setPendingPostIds((current) => [...current, post.id])
    setPosts((current) => current.map((item) => (
      item.id === post.id
        ? {
            ...item,
            liked: nextLiked,
            reactions: Math.max(0, item.reactions + reactionChange),
          }
        : item
    )))

    try {
      const updateReaction = nextLiked ? likePost : unlikePost
      await updateReaction(post.id, user.username)
    } catch {
      setPosts((current) => current.map((item) => (
        item.id === post.id
          ? {
              ...item,
              liked: post.liked,
              reactions: post.reactions,
            }
          : item
      )))
      setLikeError('No pudimos actualizar tu reacción. Inténtalo de nuevo.')
    } finally {
      setPendingPostIds((current) => current.filter((id) => id !== post.id))
    }
  }

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
              <article className="post-card" key={post.id}>
                <header className="post-card__header">
                  <span className="messages-avatar" aria-hidden="true">
                    {getInitial(post.author)}
                  </span>
                  <div>
                    <strong>{formatDisplayName(post.author)}</strong>
                    <span>@{post.author}</span>
                  </div>
                  {post.publishedAt && <time dateTime={post.publishedAt}>{formatPublishedAt(post.publishedAt)}</time>}
                </header>

                <p className="post-card__text">{post.text}</p>
                {post.mediaUrl && (
                  <img
                    className="post-card__media"
                    src={post.mediaUrl}
                    alt={`Contenido publicado por @${post.author}`}
                  />
                )}

                <footer className="post-card__footer">
                  <button
                    className={post.liked ? 'like-button like-button--active' : 'like-button'}
                    type="button"
                    aria-pressed={post.liked}
                    aria-label={post.liked ? 'Quitar Me gusta' : 'Me gusta'}
                    disabled={isPending}
                    onClick={() => toggleLike(post)}
                  >
                    <span aria-hidden="true">{post.liked ? '♥' : '♡'}</span>
                    <span>{post.liked ? 'Te gusta' : 'Me gusta'}</span>
                    <strong aria-label={`${post.reactions} reacciones`}>{post.reactions}</strong>
                  </button>
                </footer>
              </article>
            )
          })}
        </section>
      )}
    </main>
  )
}

export default FeedPage
