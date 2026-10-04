export function formatDisplayName(username = '') {
  const normalizedUsername = username.trim()
  return normalizedUsername
    ? normalizedUsername.charAt(0).toUpperCase() + normalizedUsername.slice(1)
    : 'Nueva conversación'
}

export function getInitial(username = '') {
  return username.trim().charAt(0).toUpperCase() || '?'
}
