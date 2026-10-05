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

export async function registerPushSubscription(subscription) {
  const subscriptionData = subscription.toJSON()
  await api.post('/push/subscribe', {
    endpoint: subscription.endpoint,
    keys: subscriptionData.keys ?? {},
  })
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
  let subscription = await registration.pushManager.getSubscription()

  if (!subscription) {
    const { data } = await api.get('/push/vapid-public-key')
    subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(data.publicKey),
    })
  }

  await registerPushSubscription(subscription)

  return subscription
}
