import api from './api.js'

function getCollection(data, key) {
  if (Array.isArray(data)) return data
  return Array.isArray(data?.[key]) ? data[key] : []
}

function normalizeUsername(value) {
  if (typeof value === 'string') return value
  return value?.username ?? value?.seguidor ?? value?.seguido ?? ''
}

function normalizePost(post, username) {
  const reactions = Number(post.reacciones)

  return {
    id: post.id_post,
    author: post.autor ?? username,
    text: post.texto,
    mediaUrl: post.media_url,
    publishedAt: post.fecha_publicacion,
    reactions: Number.isFinite(reactions) ? Math.max(0, reactions) : 0,
  }
}

export async function getUserProfile(username, { signal } = {}) {
  const encodedUsername = encodeURIComponent(username)
  const requestConfig = { signal }
  const [followersResponse, followingResponse, postsResponse] = await Promise.all([
    api.get(`/usuarios/${encodedUsername}/seguidores`, requestConfig),
    api.get(`/usuarios/${encodedUsername}/seguidos`, requestConfig),
    api.get(`/usuarios/${encodedUsername}/posts`, requestConfig),
  ])

  const followers = getCollection(followersResponse.data, 'seguidores')
    .map(normalizeUsername)
    .filter(Boolean)
  const following = getCollection(followingResponse.data, 'seguidos')
    .map(normalizeUsername)
    .filter(Boolean)
  const posts = getCollection(postsResponse.data, 'posts')
    .map((post) => normalizePost(post, username))

  return { followers, following, posts }
}

export function followUser(username, targetUsername) {
  return api.post(`/usuarios/${encodeURIComponent(username)}/seguir/${encodeURIComponent(targetUsername)}`)
}
