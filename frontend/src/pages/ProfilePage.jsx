import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import PostCard from '../components/PostCard.jsx'
import UserList from '../components/UserList.jsx'
import useAuth from '../hooks/useAuth.js'
import usePostReactions from '../hooks/usePostReactions.js'
import useFollowActions, { updateConnections } from '../hooks/useFollowActions.js'
import { getFollowing, getUserProfile } from '../services/profile.js'
import { formatDisplayName, getInitial } from '../utils/userDisplay.js'

const PROFILE_SECTIONS = [
  { id: 'posts', label: 'Publicaciones' },
  { id: 'followers', label: 'Seguidores' },
  { id: 'following', label: 'Seguidos' },
]

function ProfilePage() {
  const { username } = useParams()
  const { user } = useAuth()
  const profileUsername = username ?? user.username
  return <ProfileContent key={profileUsername} username={profileUsername} />
}

function ProfileContent({ username }) {
  const { user } = useAuth()
  const isOwnProfile = username === user.username
  const [viewerFollowing, setViewerFollowing] = useState([])
  const [profile, setProfile] = useState({
    followers: [],
    following: [],
    posts: [],
  })
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [activeSection, setActiveSection] = useState('posts')
  const following = isOwnProfile ? profile.following : viewerFollowing
  const { toggleLike, pendingPostIds, likeError } = usePostReactions((update) => {
    setProfile((current) => ({ ...current, posts: update(current.posts) }))
  })

  const { toggleFollow: follow, pendingUsers, followError } = useFollowActions(following, (targetUsername, isFollowing) => {
    const update = (list) => updateConnections(list, targetUsername, isFollowing)
    if (isOwnProfile) {
      setProfile((current) => ({ ...current, following: update(current.following) }))
    } else {
      setViewerFollowing(update)
      if (targetUsername === username) {
        setProfile((current) => ({
          ...current,
          followers: updateConnections(current.followers, user.username, isFollowing),
        }))
      }
    }
  })

  useEffect(() => {
    if (!user?.username) return undefined

    const abortController = new AbortController()

    Promise.all([
      getUserProfile(username, { signal: abortController.signal }),
      isOwnProfile ? Promise.resolve([]) : getFollowing(user.username, { signal: abortController.signal }),
    ]).then(([result, viewerConnections]) => {
        if (!abortController.signal.aborted) {
          setProfile(result)
          setViewerFollowing(viewerConnections)
        }
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
  }, [user?.username, username, isOwnProfile])

  return (
    <main className="profile-page">
      <header className="profile-hero">
        <div className="profile-hero__summary">
          <span className="profile-hero__avatar" aria-hidden="true">
            {getInitial(username)}
          </span>
          <div className="profile-hero__identity">
            <h1>{formatDisplayName(username)}</h1>
            <span>@{username}</span>
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
          {!isOwnProfile && (
            <UserList users={[username]} label="Acciones del perfil" currentUser={user.username}
              following={following} pendingUsers={pendingUsers} onToggle={follow} />
          )}
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
            {activeSection === 'posts' && <ProfilePosts posts={profile.posts} isOwnProfile={isOwnProfile} onToggleLike={toggleLike} pendingPostIds={pendingPostIds} />}
            {activeSection === 'followers' && (
              <UserList
                emptyMessage={isOwnProfile ? 'Todavía no tienes seguidores.' : 'Esta cuenta todavía no tiene seguidores.'}
                label="Lista de seguidores"
                users={profile.followers}
                currentUser={user.username}
                following={following}
                pendingUsers={pendingUsers}
                onToggle={follow}
              />
            )}
            {activeSection === 'following' && (
              <UserList
                emptyMessage={isOwnProfile ? 'Todavía no sigues a ninguna cuenta.' : 'Esta cuenta todavía no sigue a nadie.'}
                label="Lista de seguidos"
                users={profile.following}
                currentUser={user.username}
                following={following}
                pendingUsers={pendingUsers}
                onToggle={follow}
              />
            )}
            {followError && <p className="form-message form-message--error" role="alert">{followError}</p>}
            {likeError && <p className="form-message form-message--error" role="alert">{likeError}</p>}
          </section>
        </div>
      )}
    </main>
  )
}

function ProfilePosts({ posts, isOwnProfile, onToggleLike, pendingPostIds }) {
  if (posts.length === 0) {
    return (
      <div className="profile-empty">
        <span aria-hidden="true">◇</span>
        <p>{isOwnProfile ? 'Aún no has publicado contenido.' : 'Esta cuenta aún no ha publicado contenido.'}</p>
      </div>
    )
  }

  return (
    <div className="profile-posts__list" aria-label={isOwnProfile ? 'Publicaciones propias' : 'Publicaciones del usuario'}>
      {posts.map((post) => (
        <PostCard key={post.id} post={post} onToggleLike={onToggleLike} isPending={pendingPostIds.includes(post.id)} />
      ))}
    </div>
  )
}

export default ProfilePage
