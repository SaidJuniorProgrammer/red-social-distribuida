import { Outlet, useLocation } from 'react-router-dom'
import AppSidebar from './AppSidebar.jsx'

function AppLayout() {
  const location = useLocation()

  return (
    <div className="app-layout">
      <AppSidebar />
      <div className="app-layout__content">
        <div className="page-transition" key={location.pathname}>
          <Outlet />
        </div>
      </div>
    </div>
  )
}

export default AppLayout
