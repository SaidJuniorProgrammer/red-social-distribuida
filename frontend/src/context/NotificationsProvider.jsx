import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import useAuth from '../hooks/useAuth.js'
import { getNotifications, markAllNotificationsAsRead, markNotificationAsRead } from '../services/webpush.js'
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
  const pendingBulkKeys = useRef(new Set())

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
    const notificationKey = getNotificationKey(notification)
    const notificationId = getNotificationId(notification)
    // Si un "marcar todas" en vuelo ya la marcó de forma optimista, la reclamamos como lectura
    // individual (la sacamos del bulk) para que un rollback de "marcar todas" no la revierta.
    const coveredByPendingBulk = pendingBulkKeys.current.delete(notificationKey)
    if (isNotificationRead(notification) && !coveredByPendingBulk) return

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

  const markAllAsRead = useCallback(async () => {
    const unreadKeys = notifications
      .filter((notification) => !isNotificationRead(notification))
      .map((notification) => getNotificationKey(notification))
    if (!unreadKeys.length) return

    const unreadKeySet = new Set(unreadKeys)
    unreadKeys.forEach((key) => {
      readOverrides.current.add(key)
      pendingBulkKeys.current.add(key)
    })
    setNotifications((current) => current.map((item) => (
      unreadKeySet.has(getNotificationKey(item)) ? { ...item, leida: true } : item
    )))

    try {
      await markAllNotificationsAsRead()
      setError('')
      unreadKeys.forEach((key) => pendingBulkKeys.current.delete(key))
    } catch {
      // Revertir solo las que siguen a cargo del bulk; las abiertas de forma individual se conservan.
      const keysToRevert = unreadKeys.filter((key) => pendingBulkKeys.current.delete(key))
      const revertSet = new Set(keysToRevert)
      keysToRevert.forEach((key) => readOverrides.current.delete(key))
      setNotifications((current) => current.map((item) => (
        revertSet.has(getNotificationKey(item)) ? { ...item, leida: false } : item
      )))
      setError('No pudimos marcar las notificaciones como leídas.')
    }
  }, [notifications])

  const value = useMemo(() => ({
    error,
    loading,
    markAllAsRead,
    markAsRead,
    notifications,
    refresh: () => refresh(),
    unreadCount: notifications.filter((notification) => !isNotificationRead(notification)).length,
  }), [error, loading, markAllAsRead, markAsRead, notifications, refresh])

  return <NotificationsContext.Provider value={value}>{children}</NotificationsContext.Provider>
}

export default NotificationsProvider
