self.addEventListener('push', (event) => {
  let notification = {}

  if (event.data) {
    try {
      notification = event.data.json()
    } catch {
      notification = { mensaje: event.data.text() }
    }
  }

  const title = notification.titulo ?? 'Nueva actividad en Pachyweb'
  const options = {
    body: notification.mensaje ?? 'Hay una nueva publicación en tu red.',
    data: {
      url: notification.url ?? '/feed',
    },
    tag: notification.id_post
      ? `post-${notification.id_post}`
      : 'pachyweb-activity',
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
