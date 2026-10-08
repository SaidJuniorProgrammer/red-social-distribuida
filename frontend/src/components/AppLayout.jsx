import { Outlet, useLocation } from 'react-router-dom'
import NotificationsProvider from '../context/NotificationsProvider.jsx'
import AppSidebar from './AppSidebar.jsx'

function AppLayout() {
  const { pathname } = useLocation()
  return (
    <NotificationsProvider>
      <div className={`app-layout${pathname === '/chat' ? ' app-layout--chat' : ''}`}>
        <AppSidebar />
        <div className="app-layout__content">
          <Outlet />
        </div>
      </div>
    </NotificationsProvider>
  )
}

export default AppLayout
