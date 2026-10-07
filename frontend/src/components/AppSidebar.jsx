import { useEffect, useRef } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import useAuth from '../hooks/useAuth.js'
import useNotifications from '../hooks/useNotifications.js'
import { formatDisplayName, getInitial } from '../utils/userDisplay.js'
import BrandMark from './BrandMark.jsx'

const navigationItems = [
  { icon: '⌂', label: 'Inicio', to: '/feed' },
  { icon: '#', label: 'Explorar', to: '/explorar' },
  { icon: '♡', label: 'Notificaciones', to: '/notificaciones' },
  { icon: '✉', label: 'Mensajes', to: '/chat' },
  { icon: '○', label: 'Perfil', to: '/perfil' },
  { icon: '▣', label: 'Guardados', to: '/guardados' },
  { icon: '◎', label: 'Comunidades', to: '/comunidades' },
]

function AppSidebar() {
  const { logout, user } = useAuth()
  const { unreadCount } = useNotifications()
  const location = useLocation()
  const activeLinkRef = useRef(null)

  useEffect(() => {
    activeLinkRef.current?.scrollIntoView?.({
      block: 'nearest',
      inline: 'center',
    })
  }, [location.pathname])

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

          const hasUnread = item.to === '/notificaciones' && unreadCount > 0
          const ariaLabel = hasUnread
            ? `${item.label} (${unreadCount > 99 ? '99+' : unreadCount} sin leer)`
            : item.label

          return (
            <NavLink
              className={({ isActive }) => (
                isActive ? 'messages-sidebar__active' : undefined
              )}
              aria-label={ariaLabel}
              end={item.to === '/feed'}
              key={item.label}
              ref={location.pathname === item.to ? activeLinkRef : null}
              to={item.to}
            >
              {content}
              {item.to === '/notificaciones' && unreadCount > 0 && (
                <span className="notifications-badge" aria-hidden="true">
                  {unreadCount > 99 ? '99+' : unreadCount}
                </span>
              )}
            </NavLink>
          )
        })}
      </nav>

      <NavLink className="primary-button messages-sidebar__post" to="/publicar">
        <span aria-hidden="true">＋</span>
        Postear
      </NavLink>

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
