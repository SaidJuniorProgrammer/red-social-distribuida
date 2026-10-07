import { Outlet, useLocation } from 'react-router-dom'
import AppSidebar from './AppSidebar.jsx'

function AppLayout() {
  const { pathname } = useLocation()
  return (
    <div className={`app-layout${pathname === '/chat' ? ' app-layout--chat' : ''}`}>
      <AppSidebar />
      <div className="app-layout__content">
        <Outlet />
      </div>
    </div>
  )
}

export default AppLayout
