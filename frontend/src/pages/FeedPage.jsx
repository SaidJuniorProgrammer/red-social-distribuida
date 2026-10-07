import { useEffect, useState } from 'react'
import PostCard from '../components/PostCard.jsx'
import useAuth from '../hooks/useAuth.js'
import { getFeed, likePost, unlikePost } from '../services/feed.js'

function FeedPage() {
  const { user } = useAuth()
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
      </header>

      {likeError && <p className="form-message form-message--error feed-alert" role="alert">{likeError}</p>}
      {isLoading && <p className="feed-status" role="status">Cargando publicaciones…</p>}
      {!isLoading && loadError && <p className="feed-status feed-status--error" role="alert">{loadError}</p>}
      {!isLoading && !loadError && posts.length === 0 && (
        <section className="feed-empty">
          <span aria-hidden="true">◇</span>
          <h2>Tu feed está al día</h2>
          <p>Cuando las personas que sigues publiquen algo, aparecerá aquí.</p>
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
