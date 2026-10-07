import { formatPublishedAt } from '../utils/dateTime.js'
import { formatDisplayName, getInitial } from '../utils/userDisplay.js'

function PostCard({ isPending = false, onToggleLike, post }) {
  const canToggleLike = typeof onToggleLike === 'function'

  return (
    <article className="post-card">
      <header className="post-card__header">
        <span className="messages-avatar" aria-hidden="true">
          {getInitial(post.author)}
        </span>
        <div>
          <strong>{formatDisplayName(post.author)}</strong>
          <span>@{post.author}</span>
        </div>
        {post.publishedAt && (
          <time dateTime={post.publishedAt}>{formatPublishedAt(post.publishedAt)}</time>
        )}
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
        {canToggleLike ? (
          <button
            className={post.liked ? 'like-button like-button--active' : 'like-button'}
            type="button"
            aria-pressed={post.liked}
            aria-label={post.liked ? 'Quitar Me gusta' : 'Me gusta'}
            disabled={isPending}
            onClick={() => onToggleLike(post)}
          >
            <span aria-hidden="true">{post.liked ? '♥' : '♡'}</span>
            <span>{post.liked ? 'Te gusta' : 'Me gusta'}</span>
            <strong aria-label={`${post.reactions} reacciones`}>{post.reactions}</strong>
          </button>
        ) : (
          <span className="post-card__reaction-summary" aria-label={`${post.reactions} reacciones`}>
            <span aria-hidden="true">♡</span>
            <strong>{post.reactions}</strong>
            <span className="sr-only">{post.reactions} reacciones</span>
          </span>
        )}
      </footer>
    </article>
  )
}

export default PostCard
