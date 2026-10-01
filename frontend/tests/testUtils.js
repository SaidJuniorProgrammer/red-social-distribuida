export function createJwt(expiration) {
  const payload = btoa(JSON.stringify({ exp: expiration }))
    .replaceAll('+', '-')
    .replaceAll('/', '_')
    .replace(/=+$/, '')

  return `header.${payload}.signature`
}
