import api from './api.js'

const SERVICE_WORKER_URL = '/service-worker.js'

export function isWebPushSupported() {
  return Boolean(
    globalThis.Notification &&
    globalThis.PushManager &&
    navigator.serviceWorker,
  )
}

export function urlBase64ToUint8Array(value) {
  const padding = '='.repeat((4 - (value.length % 4)) % 4)
  const base64 = `${value}${padding}`.replaceAll('-', '+').replaceAll('_', '/')
  const decoded = atob(base64)

  return Uint8Array.from(decoded, (character) => character.charCodeAt(0))
}

async function getServiceWorkerRegistration() {
  await navigator.serviceWorker.register(SERVICE_WORKER_URL)
  return navigator.serviceWorker.ready
}

export async function getCurrentPushSubscription() {
  if (!isWebPushSupported()) return null

  const registration = await getServiceWorkerRegistration()
  return registration.pushManager.getSubscription()
}

export async function unsubscribeUserFromPush() {
  const subscription = await getCurrentPushSubscription()
  if (!subscription) return
  try {
    await api.delete('/push/subscribe', { params: { endpoint: subscription.endpoint } })
  } catch (error) {
    if (error.response?.status !== 404) throw error
  }
  await subscription.unsubscribe()
}

export async function disconnectUserFromPush(token) {
  const subscription = await getCurrentPushSubscription()
  if (!subscription) return

  try {
    await api.delete('/push/subscribe', {
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      params: { endpoint: subscription.endpoint },
    })
  } catch {
    // El cierre de sesión debe retirar el acceso local aunque el servidor no responda.
  }
  await subscription.unsubscribe()
}

export async function syncGrantedPushSubscription() {
  if (!isWebPushSupported() || Notification.permission !== 'granted') return null
  return subscribeUserToPush()
}

export async function getNotifications({ signal } = {}) {
  const { data } = await api.get('/notificaciones', { signal })
  return Array.isArray(data?.notificaciones) ? data.notificaciones : []
}

export async function registerPushSubscription(subscription) {
  const subscriptionData = subscription.toJSON()
  await api.post('/push/subscribe', {
    endpoint: subscription.endpoint,
    keys: subscriptionData.keys ?? {},
  })
}

export async function markNotificationAsRead(notificationId) {
  await api.put(`/notificaciones/${encodeURIComponent(notificationId)}/leer`)
}

function hasApplicationServerKey(subscription, expectedKey) {
  const currentKey = subscription.options?.applicationServerKey
  if (!currentKey) return false

  const currentBytes = new Uint8Array(currentKey)
  return currentBytes.length === expectedKey.length &&
    currentBytes.every((value, index) => value === expectedKey[index])
}

export async function subscribeUserToPush() {
  if (!isWebPushSupported()) {
    throw new Error('Este navegador no admite notificaciones web.')
  }

  const permission = Notification.permission === 'default'
    ? await Notification.requestPermission()
    : Notification.permission

  if (permission !== 'granted') {
    throw new Error('Debes permitir las notificaciones para activar las alertas.')
  }

  const registration = await getServiceWorkerRegistration()
  const { data } = await api.get('/push/vapid-public-key')
  const applicationServerKey = urlBase64ToUint8Array(data.publicKey)
  let subscription = await registration.pushManager.getSubscription()

  if (subscription && !hasApplicationServerKey(subscription, applicationServerKey)) {
    await subscription.unsubscribe()
    await api.delete('/push/subscribe', {
      params: { endpoint: subscription.endpoint },
    }).catch(() => undefined)
    subscription = null
  }

  if (!subscription) {
    subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey,
    })
  }

  await registerPushSubscription(subscription)

  return subscription
}
