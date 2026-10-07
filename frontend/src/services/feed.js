import api from './api.js'

export function normalizePost(post, username) {
  const reactions = Number(post.reacciones)

  return {
    id: post.id_post,
    author: post.autor ?? username,
    text: post.texto,
    mediaUrl: post.media_url,
    mediaType: post.media_tipo,
    publishedAt: post.fecha_publicacion,
    reactions: Number.isFinite(reactions) ? Math.max(0, reactions) : 0,
    liked: Boolean(post.liked ?? post.reaccionado ?? post.me_gusta),
  }
}

export async function getFeed(userId, { signal } = {}) {
  const { data } = await api.get(`/feed/${encodeURIComponent(userId)}`, { signal })
  const posts = Array.isArray(data?.feed) ? data.feed : []

  return posts.map((post) => normalizePost(post))
}

export function likePost(postId, userId) {
  return api.post(
    `/posts/${encodeURIComponent(postId)}/like/${encodeURIComponent(userId)}`,
  )
}

export function unlikePost(postId, userId) {
  return api.delete(
    `/posts/${encodeURIComponent(postId)}/like/${encodeURIComponent(userId)}`,
  )
}
