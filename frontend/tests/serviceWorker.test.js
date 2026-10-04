import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { cwd } from 'node:process'
import { runInNewContext } from 'node:vm'
import { describe, expect, it, vi } from 'vitest'

const workerSource = readFileSync(
  resolve(cwd(), 'public/service-worker.js'),
  'utf8',
)

function createWorker() {
  const listeners = new Map()
  const showNotification = vi.fn().mockResolvedValue(undefined)
  const openWindow = vi.fn().mockResolvedValue(undefined)
  const matchAll = vi.fn().mockResolvedValue([])
  const worker = {
    addEventListener: (eventName, listener) => listeners.set(eventName, listener),
    clients: { matchAll, openWindow },
    location: { origin: 'https://pachyweb.example' },
    registration: { showNotification },
  }

  runInNewContext(workerSource, { Promise, self: worker, URL })
  return { listeners, matchAll, openWindow, showNotification }
}

describe('Service Worker de notificaciones', () => {
  it('muestra la alerta recibida mediante el evento push', async () => {
    const { listeners, showNotification } = createWorker()
    let pendingTask

    listeners.get('push')({
      data: {
        json: () => ({
          id_post: 'post-15',
          mensaje: 'Said publicó una actualización',
          titulo: 'Nueva publicación',
          url: '/feed',
        }),
      },
      waitUntil: (task) => {
        pendingTask = task
      },
    })
    await pendingTask

    expect(showNotification).toHaveBeenCalledWith('Nueva publicación', {
      body: 'Said publicó una actualización',
      data: { url: '/feed' },
      tag: 'post-post-15',
    })
  })

  it('evita abrir destinos externos desde una notificación', async () => {
    const { listeners, openWindow } = createWorker()
    const close = vi.fn()
    let pendingTask

    listeners.get('notificationclick')({
      notification: {
        close,
        data: { url: 'https://sitio-no-confiable.example/phishing' },
      },
      waitUntil: (task) => {
        pendingTask = task
      },
    })
    await pendingTask

    expect(close).toHaveBeenCalledOnce()
    expect(openWindow).toHaveBeenCalledWith('https://pachyweb.example/feed')
  })
})
