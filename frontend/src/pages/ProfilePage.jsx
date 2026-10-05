import { useCallback, useEffect, useRef, useState } from 'react'
import PostCard from '../components/PostCard.jsx'
import useAuth from '../hooks/useAuth.js'
import { getUserProfile } from '../services/profile.js'
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
              <UserSlider
                emptyMessage="Todavía no tienes seguidores."
                label="seguidores"
                users={profile.followers}
              />
            )}
            {activeSection === 'following' && (
              <UserSlider
                emptyMessage="Todavía no sigues a ninguna cuenta."
                label="seguidos"
                users={profile.following}
              />
            )}
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

function UserSlider({ emptyMessage, label, users }) {
  const listRef = useRef(null)
  const [canGoBack, setCanGoBack] = useState(false)
  const [canGoForward, setCanGoForward] = useState(users.length > 1)

  const updateSliderControls = useCallback(() => {
    const list = listRef.current
    if (!list) return

    const hasMeasuredWidth = list.clientWidth > 0
    setCanGoBack(list.scrollLeft > 1)
    setCanGoForward(
      hasMeasuredWidth
        ? list.scrollLeft + list.clientWidth < list.scrollWidth - 1
        : users.length > 1,
    )
  }, [users.length])

  useEffect(() => {
    const list = listRef.current
    if (!list) return undefined

    updateSliderControls()
    list.addEventListener('scroll', updateSliderControls, { passive: true })
    window.addEventListener('resize', updateSliderControls)

    return () => {
      list.removeEventListener('scroll', updateSliderControls)
      window.removeEventListener('resize', updateSliderControls)
    }
  }, [updateSliderControls])

  if (users.length === 0) {
    return (
      <div className="profile-empty">
        <span aria-hidden="true">○</span>
        <p>{emptyMessage}</p>
      </div>
    )
  }

  const moveSlider = (direction) => {
    listRef.current?.scrollBy?.({
      behavior: 'smooth',
      left: direction * 280,
    })
  }

  return (
    <div className="profile-slider">
      <header className="profile-slider__header">
        <div>
          <h2>{label === 'seguidores' ? 'Personas que te siguen' : 'Cuentas que sigues'}</h2>
          <p>Desliza horizontalmente para recorrer la lista.</p>
        </div>
        <div className="profile-slider__controls">
          <button
            type="button"
            aria-label={`Ver ${label} anteriores`}
            disabled={!canGoBack}
            onClick={() => moveSlider(-1)}
          >
            ←
          </button>
          <button
            type="button"
            aria-label={`Ver ${label} siguientes`}
            disabled={!canGoForward}
            onClick={() => moveSlider(1)}
          >
            →
          </button>
        </div>
      </header>
      <ul className="profile-slider__viewport" aria-label={`Lista de ${label}`} ref={listRef}>
        {users.map((username) => (
          <li className="profile-user-card" key={username}>
            <span className="messages-avatar" aria-hidden="true">{getInitial(username)}</span>
            <span>
              <strong>{formatDisplayName(username)}</strong>
              <small>@{username}</small>
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}

export default ProfilePage
