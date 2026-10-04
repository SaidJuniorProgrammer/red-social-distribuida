import { NavLink } from 'react-router-dom'
import useAuth from '../hooks/useAuth.js'
import { formatDisplayName, getInitial } from '../utils/userDisplay.js'
import BrandMark from './BrandMark.jsx'

const navigationItems = [
  { icon: '⌂', label: 'Inicio', to: '/feed' },
  { icon: '#', label: 'Explorar' },
  { icon: '♡', label: 'Notificaciones', to: '/notificaciones' },
  { icon: '✉', label: 'Mensajes', to: '/chat' },
  { icon: '▣', label: 'Guardados' },
  { icon: '◎', label: 'Comunidades' },
  { icon: '○', label: 'Perfil', to: '/perfil' },
]

function AppSidebar() {
  const { logout, user } = useAuth()

  return (
    <aside className="messages-sidebar" aria-label="Barra lateral principal">
      <div className="messages-sidebar__brand">
        <BrandMark />
        <span>v2.4 federated</span>
      </div>

      <nav aria-label="Navegación principal">
        {navigationItems.map((item) => {
          const content = (
            <>
              <span className="messages-sidebar__icon" aria-hidden="true">{item.icon}</span>
              <span>{item.label}</span>
            </>
          )

          return item.to ? (
            <NavLink
              className={({ isActive }) => (
                isActive ? 'messages-sidebar__active' : undefined
              )}
              end={item.to === '/feed'}
              key={item.label}
              to={item.to}
            >
              {content}
            </NavLink>
          ) : (
            <span className="messages-sidebar__item" key={item.label}>
              {content}
            </span>
          )
        })}
      </nav>

      <button className="primary-button messages-sidebar__post" type="button" disabled>
        Postear
      </button>

      <div className="messages-profile">
        <span className="messages-avatar" aria-hidden="true">
          {getInitial(user?.username)}
        </span>
        <div>
          <strong>{formatDisplayName(user?.username)}</strong>
          <span>@{user?.username}</span>
        </div>
        <button type="button" aria-label="Cerrar sesión" onClick={logout}>↪</button>
      </div>
    </aside>
  )
}

export default AppSidebar
