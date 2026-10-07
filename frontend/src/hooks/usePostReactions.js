import { useRef, useState } from 'react'
import useAuth from './useAuth.js'
import { likePost, unlikePost } from '../services/feed.js'

export default function usePostReactions(updatePosts) {
  const { user } = useAuth()
  const pending = useRef(new Set())
  const [pendingPostIds, setPendingPostIds] = useState([])
  const [likeError, setLikeError] = useState('')

  const toggleLike = async (post) => {
    if (pending.current.has(post.id)) return
    pending.current.add(post.id)
    setPendingPostIds([...pending.current])
    setLikeError('')
    try {
      await (post.liked ? unlikePost : likePost)(post.id, user.username)
      updatePosts((posts) => posts.map((item) => item.id === post.id
        ? { ...item, liked: !post.liked, reactions: Math.max(0, item.reactions + (post.liked ? -1 : 1)) }
        : item))
    } catch {
      setLikeError('No pudimos actualizar tu reacción. Inténtalo de nuevo.')
    } finally {
      pending.current.delete(post.id)
      setPendingPostIds([...pending.current])
    }
  }
  return { toggleLike, pendingPostIds, likeError }
}
