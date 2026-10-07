import api from './api.js'
import { normalizePost } from './feed.js'

function getCollection(data, key) {
  if (Array.isArray(data)) return data
  return Array.isArray(data?.[key]) ? data[key] : []
}

function normalizeUsername(value) {
  if (typeof value === 'string') return value
  return value?.username ?? value?.seguidor ?? value?.seguido ?? ''
}

export async function getFollowing(username, { signal } = {}) {
  const { data } = await api.get(`/usuarios/${encodeURIComponent(username)}/seguidos`, { signal })
  return getCollection(data, 'seguidos').map(normalizeUsername).filter(Boolean)
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

export function unfollowUser(username, targetUsername) {
  return api.delete(`/usuarios/${encodeURIComponent(username)}/seguir/${encodeURIComponent(targetUsername)}`)
}

export async function getSuggestions(username, { signal } = {}) {
  const { data } = await api.get(`/usuarios/${encodeURIComponent(username)}/sugerencias`, { signal })
  return getCollection(data, 'sugerencias').filter((item) => item.recomendado)
}
