import { useEffect, useRef, useState } from 'react'
import { searchRegisteredUsers } from '../services/users.js'
import useFollowActions, { updateConnections } from '../hooks/useFollowActions.js'
import { getFollowing, getSuggestions } from '../services/profile.js'
import useAuth from '../hooks/useAuth.js'
import UserList from '../components/UserList.jsx'

function ExplorePage() {
  const { user } = useAuth()
  const [following, setFollowing] = useState([])
  const [suggestions, setSuggestions] = useState([])
  const [connectionsReady, setConnectionsReady] = useState(false)
  const [connectionError, setConnectionError] = useState('')
  const [suggestionError, setSuggestionError] = useState('')
  const [revision, setRevision] = useState(0)
  const { toggleFollow, pendingUsers, followError } = useFollowActions(following, (username, isFollowing) => {
    setFollowing((current) => updateConnections(current, username, isFollowing))
  })
  const [query, setQuery] = useState('')
  const [users, setUsers] = useState([])
  const [status, setStatus] = useState('')
  const [isSearching, setIsSearching] = useState(false)
  const searchRequestRef = useRef(null)

  useEffect(() => () => searchRequestRef.current?.abort(), [])

  useEffect(() => {
    const controller = new AbortController()
    getFollowing(user.username, { signal: controller.signal }).then((connections) => {
      if (controller.signal.aborted) return
      setFollowing(connections)
      setConnectionsReady(true)
      setConnectionError('')
    }).catch(() => {
      if (!controller.signal.aborted) setConnectionError('No pudimos cargar tus conexiones.')
    })
    getSuggestions(user.username, { signal: controller.signal }).then((recommended) => {
      if (controller.signal.aborted) return
      setSuggestions(recommended)
      setSuggestionError('')
    }).catch(() => {
      if (!controller.signal.aborted) setSuggestionError('No pudimos cargar las sugerencias.')
    })
    return () => controller.abort()
  }, [user.username, revision])

  const connectionProps = { currentUser: user.username, following, pendingUsers, onToggle: toggleFollow, disabled: !connectionsReady }

  const search = async (event) => {
    event.preventDefault()
    const normalizedQuery = query.trim()
    if (!normalizedQuery) return

    searchRequestRef.current?.abort()
    const abortController = new AbortController()
    searchRequestRef.current = abortController
    setIsSearching(true)
    setStatus('')
    try {
      const results = await searchRegisteredUsers(normalizedQuery, {
        signal: abortController.signal,
      })
      if (abortController.signal.aborted) return

      setUsers(results)
      setStatus(results.length === 0
        ? 'No encontramos cuentas registradas con ese nombre.'
        : '')
    } catch {
      if (!abortController.signal.aborted) {
        setUsers([])
        setStatus('No pudimos realizar la búsqueda en este momento.')
      }
    } finally {
      if (searchRequestRef.current === abortController) {
        searchRequestRef.current = null
        setIsSearching(false)
      }
    }
  }

  const changeQuery = ({ target }) => {
    searchRequestRef.current?.abort()
    searchRequestRef.current = null
    setQuery(target.value)
    setUsers([])
    setStatus('')
    setIsSearching(false)
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
              onChange={changeQuery}
            />
            <button className="secondary-button" type="submit" disabled={isSearching || !query.trim()}>
              {isSearching ? 'Buscando…' : 'Buscar'}
            </button>
          </div>
        </form>

        {status && <p className="explore-status" role="status">{status}</p>}
        {followError && <p className="form-message form-message--error" role="alert">{followError}</p>}
        {connectionError && <p className="form-message form-message--error" role="alert">{connectionError}</p>}
        {(connectionError || suggestionError) && <button type="button" className="secondary-button" onClick={() => setRevision((value) => value + 1)}>Reintentar conexiones</button>}
        {users.length > 0 && (
          <UserList users={users.map(({ username }) => username)} label="Usuarios encontrados" {...connectionProps} />
        )}
      </section>
      <section className="explore-card" aria-label="Personas sugeridas">
        <h2>Personas que podrías conocer</h2>
        <p>Recomendaciones según tus conexiones en común.</p>
        {suggestionError && <p role="alert" className="form-message form-message--error">{suggestionError}</p>}
        <UserList users={suggestions.map(({ recomendado }) => recomendado)} label="Personas sugeridas"
          details={Object.fromEntries(suggestions.map((item) => [item.recomendado, `${item.conexiones_en_comun} conexiones en común`]))}
          emptyMessage={connectionsReady ? 'No hay sugerencias por ahora.' : 'Las sugerencias aparecerán cuando carguen tus conexiones.'}
          {...connectionProps} />
      </section>
    </main>
  )
}

export default ExplorePage
