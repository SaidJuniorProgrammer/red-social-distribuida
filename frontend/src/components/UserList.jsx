import { Link } from 'react-router-dom'
import { formatDisplayName, getInitial } from '../utils/userDisplay.js'

function UserList({ users, label, currentUser, following = [], pendingUsers = [], onToggle, disabled = false, emptyMessage, details = {} }) {
  if (!users.length) return <p className="profile-empty">{emptyMessage}</p>
  return (
    <ul className="profile-connections__list" aria-label={label}>
      {users.map((username) => (
        <li className="profile-user-card" key={username}>
          <span className="messages-avatar" aria-hidden="true">{getInitial(username)}</span>
          <Link className="profile-user-card__identity" to={`/perfil/${encodeURIComponent(username)}`} aria-label={`Ver perfil de @${username}`}>
            <strong>{formatDisplayName(username)}</strong>
            <small>@{username}</small>
            {details[username] && <small>{details[username]}</small>}
          </Link>
          {username !== currentUser && onToggle && (
            <button className="profile-follow-button" type="button"
              disabled={disabled || pendingUsers.includes(username)}
              aria-label={`${following.includes(username) ? 'Dejar de seguir a' : 'Seguir a'} @${username}`}
              onClick={() => onToggle(username)}>
              {pendingUsers.includes(username) ? 'Guardando…' : following.includes(username) ? 'Dejar de seguir' : 'Seguir'}
            </button>
          )}
        </li>
      ))}
    </ul>
  )
}

export default UserList
