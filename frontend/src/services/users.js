import api from './api.js'

export async function searchRegisteredUsers(query, options = {}) {
  const normalizedQuery = query.trim()
  if (!normalizedQuery) return []

  const response = await api.get('/usuarios', {
    params: { query: normalizedQuery },
    signal: options.signal,
  })

  if (!Array.isArray(response.data?.usuarios)) return []

  return response.data.usuarios.filter(
    (user) => typeof user?.username === 'string' && user.username.trim(),
  )
}
