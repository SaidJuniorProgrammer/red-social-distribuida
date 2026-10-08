import axios from 'axios'
import { clearSession, readSession } from '../context/authStorage.js'

const api = axios.create({
  baseURL:
    import.meta.env.VITE_API_BASE_URL ?? 'https://pachyweb-backend.onrender.com/api',
  headers: {
    'Content-Type': 'application/json',
  },
})

api.interceptors.request.use((config) => {
  const token = readSession()?.token

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && readSession()?.token) {
      clearSession()
    }

    return Promise.reject(error)
  },
)

export default api
