import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import useAuth from '../hooks/useAuth.js'
import { getNotifications, markNotificationAsRead } from '../services/webpush.js'
import {
  getNotificationId,
  getNotificationKey,
  getNotificationTimestamp,
  isNotificationRead,
} from '../utils/notifications.js'
import NotificationsContext from './notificationsContext.js'

const REFRESH_INTERVAL = 30_000

function NotificationsProvider({ children }) {
  const { user } = useAuth()
  const [notifications, setNotifications] = useState([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const readOverrides = useRef(new Set())

  const refresh = useCallback(async ({ signal } = {}) => {
    if (!user?.username) return
    try {
      const result = await getNotifications({ signal })
      if (signal?.aborted) return
      const sortedNotifications = [...result]
        .sort((a, b) => getNotificationTimestamp(b).localeCompare(getNotificationTimestamp(a)))
        .map((notification) => (
          readOverrides.current.has(getNotificationKey(notification))
            ? { ...notification, leida: true }
            : notification
        ))
      setNotifications(sortedNotifications)
      setError('')
    } catch {
      if (!signal?.aborted) setError('No pudimos cargar la actividad. Inténtalo nuevamente.')
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [user.username])

  useEffect(() => {
    const controller = new AbortController()
    let timer

    const refreshPeriodically = async () => {
      await refresh({ signal: controller.signal })
      if (!controller.signal.aborted) timer = window.setTimeout(refreshPeriodically, REFRESH_INTERVAL)
    }

    void refreshPeriodically()
    return () => {
      controller.abort()
      window.clearTimeout(timer)
    }
  }, [refresh])

  const markAsRead = useCallback(async (notification) => {
    if (isNotificationRead(notification)) return

    const notificationKey = getNotificationKey(notification)
    const notificationId = getNotificationId(notification)
    readOverrides.current.add(notificationKey)
    setNotifications((current) => current.map((item) => (
      getNotificationKey(item) === notificationKey
        ? { ...item, leida: true }
        : item
    )))

    if (!notificationId) return
    try {
      await markNotificationAsRead(notificationId)
      setError('')
    } catch {
      readOverrides.current.delete(notificationKey)
      setNotifications((current) => current.map((item) => (
        getNotificationKey(item) === notificationKey
          ? { ...item, leida: false }
          : item
      )))
      setError('No pudimos marcar la notificación como leída.')
    }
  }, [])

  const value = useMemo(() => ({
    error,
    loading,
    markAsRead,
    notifications,
    refresh: () => refresh(),
    unreadCount: notifications.filter((notification) => !isNotificationRead(notification)).length,
  }), [error, loading, markAsRead, notifications, refresh])

  return <NotificationsContext.Provider value={value}>{children}</NotificationsContext.Provider>
}

export default NotificationsProvider
