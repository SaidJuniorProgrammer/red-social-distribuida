import { Outlet } from 'react-router-dom'
import AppSidebar from './AppSidebar.jsx'

function AppLayout() {
  return (
    <div className="app-layout">
      <AppSidebar />
      <div className="app-layout__content">
        <Outlet />
      </div>
    </div>
  )
}

export default AppLayout
