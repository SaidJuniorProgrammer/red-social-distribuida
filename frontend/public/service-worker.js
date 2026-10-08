// El backend envia /mensajes como referencia de los avisos de chat; en el frontend la ruta es /chat.
function resolveNotificationDestination(notification) {
  const supplied = notification.referencia ?? notification.url_interna ?? notification.url ?? '/feed'
  if (String(supplied).split(/[?#]/)[0] === '/mensajes') {
    const actor = notification.actor ?? notification.autor
    return actor ? `/chat?usuario=${encodeURIComponent(actor)}` : '/chat'
  }
  return supplied
}

self.addEventListener('push', (event) => {
  let notification = {}

  if (event.data) {
    try {
      notification = event.data.json() ?? {}
    } catch {
      notification = { mensaje: event.data.text() }
    }
  }

  const title = notification.titulo ?? 'Nueva actividad en Pachyweb'
  const destination = resolveNotificationDestination(notification)
  const notificationId = notification.idNotificacion ?? notification.id_notificacion
  const options = {
    body: notification.mensaje ?? 'Hay una nueva publicación en tu red.',
    data: {
      url: destination,
    },
  }
  if (notificationId) {
    options.tag = `notification-${notificationId}`
  } else if (notification.id_post) {
    options.tag = `post-${notification.id_post}`
  }

  event.waitUntil(self.registration.showNotification(title, options))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const fallbackDestination = new URL('/feed', self.location.origin)
  let destination = fallbackDestination.href

  try {
    const requestedDestination = new URL(
      event.notification.data?.url ?? '/feed',
      self.location.origin,
    )
    if (requestedDestination.origin === self.location.origin) {
      destination = requestedDestination.href
    }
  } catch {
    destination = fallbackDestination.href
  }

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true })
      .then((windows) => {
        const openWindow = windows.find((windowClient) =>
          windowClient.url.startsWith(self.location.origin),
        )

        if (openWindow) {
          return openWindow.navigate(destination).then(() => openWindow.focus())
        }

        return self.clients.openWindow(destination)
      }),
  )
})
