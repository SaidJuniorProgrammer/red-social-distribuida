import { useState } from 'react'
import { searchRegisteredUsers } from '../services/users.js'
import { formatDisplayName, getInitial } from '../utils/userDisplay.js'

function ExplorePage() {
  const [query, setQuery] = useState('')
  const [users, setUsers] = useState([])
  const [status, setStatus] = useState('')
  const [isSearching, setIsSearching] = useState(false)

  const search = async (event) => {
    event.preventDefault()
    const normalizedQuery = query.trim()
    if (!normalizedQuery) return

    setIsSearching(true)
    setStatus('')
    try {
      const results = await searchRegisteredUsers(normalizedQuery)
      setUsers(results)
      setStatus(results.length === 0
        ? 'No encontramos cuentas registradas con ese nombre.'
        : '')
    } catch {
      setUsers([])
      setStatus('No pudimos realizar la búsqueda en este momento.')
    } finally {
      setIsSearching(false)
    }
  }

  return (
    <main className="feed-placeholder explore-page">
      <header className="feed-placeholder__header">
        <div>
          <p>Descubrir personas</p>
          <h1>Explorar</h1>
        </div>
      </header>

      <section className="explore-card">
        <h2>Busca cuentas de Pachyweb</h2>
        <p>Solo aparecerán personas que ya crearon una cuenta en la aplicación.</p>
        <form className="explore-search" onSubmit={search}>
          <label htmlFor="explore-query">Nombre de usuario</label>
          <div>
            <span aria-hidden="true">@</span>
            <input
              id="explore-query"
              type="search"
              value={query}
              placeholder="Busca por nombre"
              onChange={({ target }) => setQuery(target.value)}
            />
            <button className="secondary-button" type="submit" disabled={isSearching || !query.trim()}>
              {isSearching ? 'Buscando…' : 'Buscar'}
            </button>
          </div>
        </form>

        {status && <p className="explore-status" role="status">{status}</p>}
        {users.length > 0 && (
          <ul className="explore-results" aria-label="Usuarios encontrados">
            {users.map(({ username }) => (
              <li key={username}>
                <span className="messages-avatar" aria-hidden="true">{getInitial(username)}</span>
                <span>
                  <strong>{formatDisplayName(username)}</strong>
                  <small>@{username}</small>
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  )
}

export default ExplorePage
