import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './components/AppLayout.jsx'
import PagePlaceholder from './components/PagePlaceholder.jsx'
import ProtectedRoute from './components/ProtectedRoute.jsx'
import ChatPage from './pages/ChatPage.jsx'
import ExplorePage from './pages/ExplorePage.jsx'
import FeedPage from './pages/FeedPage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import NotificationsPage from './pages/NotificationsPage.jsx'
import ProfilePage from './pages/ProfilePage.jsx'
import RegisterPage from './pages/RegisterPage.jsx'

function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/registro" element={<RegisterPage />} />
      <Route
        element={
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/feed" element={<FeedPage />} />
        <Route path="/explorar" element={<ExplorePage />} />
        <Route path="/notificaciones" element={<NotificationsPage />} />
        <Route path="/chat" element={<ChatPage />} />
        <Route
          path="/guardados"
          element={(
            <PagePlaceholder
              title="Guardados"
              description="Aquí aparecerán las publicaciones que guardes cuando esa función esté disponible."
            />
          )}
        />
        <Route
          path="/comunidades"
          element={(
            <PagePlaceholder
              title="Comunidades"
              description="Esta sección ya es accesible y queda lista para incorporar las comunidades."
            />
          )}
        />
        <Route path="/perfil" element={<ProfilePage />} />
      </Route>
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  )
}

export default App
