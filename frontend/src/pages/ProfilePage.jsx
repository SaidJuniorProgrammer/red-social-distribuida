import { useEffect, useRef, useState } from 'react'
import PostCard from '../components/PostCard.jsx'
import useAuth from '../hooks/useAuth.js'
import { followUser, getUserProfile } from '../services/profile.js'
import { formatDisplayName, getInitial } from '../utils/userDisplay.js'

const PROFILE_SECTIONS = [
  { id: 'posts', label: 'Publicaciones' },
  { id: 'followers', label: 'Seguidores' },
  { id: 'following', label: 'Seguidos' },
]

function ProfilePage() {
  const { user } = useAuth()
  const [profile, setProfile] = useState({
    followers: [],
    following: [],
    posts: [],
  })
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [activeSection, setActiveSection] = useState('posts')
  const pendingRequests = useRef(new Set())
  const [pendingUsers, setPendingUsers] = useState([])
  const [followError, setFollowError] = useState('')

  const follow = async (username) => {
    if (pendingRequests.current.has(username) || profile.following.includes(username)) return

    pendingRequests.current.add(username)
    setPendingUsers([...pendingRequests.current])
    setFollowError('')
    try {
      await followUser(user.username, username)
      setProfile((current) => ({
        ...current,
        following: [...new Set([...current.following, username])].sort((a, b) => a.localeCompare(b)),
      }))
    } catch {
      setFollowError(`No pudimos seguir a @${username}. Inténtalo de nuevo.`)
    } finally {
      pendingRequests.current.delete(username)
      setPendingUsers([...pendingRequests.current])
    }
  }

  useEffect(() => {
    if (!user?.username) return undefined

    const abortController = new AbortController()

    getUserProfile(user.username, { signal: abortController.signal })
      .then((result) => {
        if (!abortController.signal.aborted) setProfile(result)
      })
      .catch(() => {
        if (!abortController.signal.aborted) {
          setError('No pudimos cargar tu perfil en este momento.')
        }
      })
      .finally(() => {
        if (!abortController.signal.aborted) setIsLoading(false)
      })

    return () => abortController.abort()
  }, [user?.username])

  return (
    <main className="profile-page">
      <header className="profile-hero">
        <div className="profile-hero__summary">
          <span className="profile-hero__avatar" aria-hidden="true">
            {getInitial(user?.username)}
          </span>
          <div className="profile-hero__identity">
            <h1>{formatDisplayName(user?.username)}</h1>
            <span>@{user?.username}</span>
          </div>
        </div>
        <dl className="profile-stats" aria-label="Resumen del perfil">
          <div>
            <dt>Publicaciones</dt>
            <dd>{profile.posts.length}</dd>
          </div>
          <div>
            <dt>Seguidores</dt>
            <dd>{profile.followers.length}</dd>
          </div>
          <div>
            <dt>Seguidos</dt>
            <dd>{profile.following.length}</dd>
          </div>
        </dl>
      </header>

      {isLoading && <p className="profile-status" role="status">Cargando perfil…</p>}
      {!isLoading && error && <p className="profile-status profile-status--error" role="alert">{error}</p>}

      {!isLoading && !error && (
        <div className="profile-browser">
          <div
            className="profile-tabs"
            data-active={activeSection}
            role="tablist"
            aria-label="Contenido del perfil"
          >
            {PROFILE_SECTIONS.map(({ id, label }) => (
              <button
                id={`profile-tab-${id}`}
                key={id}
                type="button"
                role="tab"
                aria-controls={`profile-panel-${id}`}
                aria-selected={activeSection === id}
                onClick={() => setActiveSection(id)}
              >
                <span>{label}</span>
              </button>
            ))}
          </div>

          <section
            className="profile-panel"
            id={`profile-panel-${activeSection}`}
            role="tabpanel"
            aria-labelledby={`profile-tab-${activeSection}`}
          >
            {activeSection === 'posts' && <ProfilePosts posts={profile.posts} />}
            {activeSection === 'followers' && (
              <UserList
                emptyMessage="Todavía no tienes seguidores."
                label="seguidores"
                users={profile.followers}
                following={profile.following}
                pendingUsers={pendingUsers}
                onFollow={follow}
              />
            )}
            {activeSection === 'following' && (
              <UserList
                emptyMessage="Todavía no sigues a ninguna cuenta."
                label="seguidos"
                users={profile.following}
              />
            )}
            {followError && <p className="form-message form-message--error" role="alert">{followError}</p>}
          </section>
        </div>
      )}
    </main>
  )
}

function ProfilePosts({ posts }) {
  if (posts.length === 0) {
    return (
      <div className="profile-empty">
        <span aria-hidden="true">◇</span>
        <p>Aún no has publicado contenido.</p>
      </div>
    )
  }

  return (
    <div className="profile-posts__list" aria-label="Publicaciones propias">
      {posts.map((post) => (
        <PostCard key={post.id} post={post} />
      ))}
    </div>
  )
}

function UserList({ emptyMessage, label, users, following = [], pendingUsers = [], onFollow }) {
  if (users.length === 0) {
    return (
      <div className="profile-empty">
        <span aria-hidden="true">○</span>
        <p>{emptyMessage}</p>
      </div>
    )
  }

  return (
    <div className="profile-connections">
      <h2>{label === 'seguidores' ? 'Personas que te siguen' : 'Cuentas que sigues'}</h2>
      <ul className="profile-connections__list" aria-label={`Lista de ${label}`}>
        {users.map((username) => (
          <li className="profile-user-card" key={username}>
            <span className="messages-avatar" aria-hidden="true">{getInitial(username)}</span>
            <span className="profile-user-card__identity">
              <strong>{formatDisplayName(username)}</strong>
              <small>@{username}</small>
            </span>
            {onFollow && (
              <button
                className="profile-follow-button"
                type="button"
                disabled={following.includes(username) || pendingUsers.includes(username)}
                aria-label={`${following.includes(username) ? 'Ya sigues a' : 'Seguir a'} @${username}`}
                onClick={() => onFollow(username)}
              >
                {pendingUsers.includes(username) ? 'Siguiendo…' : 'Seguir'}
              </button>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}

export default ProfilePage
