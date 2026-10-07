import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import PushNotificationCard from '../src/components/PushNotificationCard.jsx'
import api from '../src/services/api.js'

function installBrowserMocks({ existingSubscription = null } = {}) {
  const newSubscription = {
    endpoint: 'https://push.example/oscar',
    options: { applicationServerKey: new Uint8Array([1, 2, 3, 4]) },
    toJSON: () => ({ keys: { p256dh: 'publica', auth: 'secreta' } }),
    unsubscribe: vi.fn().mockResolvedValue(true),
  }
  const registration = {
    pushManager: {
      getSubscription: vi.fn().mockResolvedValue(existingSubscription),
      subscribe: vi.fn().mockResolvedValue(newSubscription),
    },
  }

  vi.stubGlobal('Notification', {
    permission: 'default',
    requestPermission: vi.fn().mockImplementation(async () => {
      Notification.permission = 'granted'
      return 'granted'
    }),
  })
  vi.stubGlobal('PushManager', class PushManager {})
  Object.defineProperty(navigator, 'serviceWorker', {
    configurable: true,
    value: {
      ready: Promise.resolve(registration),
      register: vi.fn().mockResolvedValue(registration),
    },
  })
  vi.spyOn(api, 'get').mockResolvedValue({ data: { publicKey: 'AQIDBA' } })
  vi.spyOn(api, 'post').mockResolvedValue({ data: {} })
  return { newSubscription, registration }
}

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  Object.defineProperty(navigator, 'serviceWorker', {
    configurable: true,
    value: undefined,
  })
})

it('activa las notificaciones a petición del usuario', async () => {
  installBrowserMocks()
  render(<PushNotificationCard />)

  const activateButton = await screen.findByRole('button', { name: 'Activar' })
  fireEvent.click(activateButton)

  expect(await screen.findByRole('status')).toHaveTextContent(
    'Notificaciones activadas',
  )
  expect(Notification.requestPermission).toHaveBeenCalledOnce()
  await waitFor(() => expect(api.post).toHaveBeenCalledOnce())
})

it('vincula una suscripción existente con la cuenta que inició sesión', async () => {
  const existingSubscription = {
    endpoint: 'https://push.example/cuenta-anterior',
    options: { applicationServerKey: new Uint8Array([1, 2, 3, 4]) },
    toJSON: () => ({ keys: { p256dh: 'publica', auth: 'secreta' } }),
    unsubscribe: vi.fn().mockResolvedValue(true),
  }
  installBrowserMocks({ existingSubscription })
  Notification.permission = 'granted'
  render(<PushNotificationCard />)

  expect(await screen.findByRole('status')).toHaveTextContent(
    'Notificaciones activadas',
  )
  expect(api.post).toHaveBeenCalledWith('/push/subscribe', {
    endpoint: existingSubscription.endpoint,
    keys: { p256dh: 'publica', auth: 'secreta' },
  })
})

it('explica cuando el navegador no admite notificaciones', async () => {
  vi.stubGlobal('Notification', undefined)
  vi.stubGlobal('PushManager', undefined)
  render(<PushNotificationCard />)

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Este navegador no admite notificaciones web',
  )
})

it('explica cómo habilitar un permiso bloqueado', async () => {
  installBrowserMocks()
  Notification.permission = 'denied'
  render(<PushNotificationCard />)

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Puedes habilitarlas desde la configuración del navegador',
  )
  expect(screen.getByRole('button', { name: 'Activar' })).toBeDisabled()
})

it('desactiva la suscripción y no la reactiva al volver a entrar', async () => {
  const subscription = {
    endpoint: 'https://push.example/oscar',
    options: { applicationServerKey: new Uint8Array([1, 2, 3, 4]) },
    toJSON: () => ({ keys: { p256dh: 'publica', auth: 'secreta' } }),
    unsubscribe: vi.fn().mockResolvedValue(true),
  }
  const { registration } = installBrowserMocks({ existingSubscription: subscription })
  Notification.permission = 'granted'
  const remove = vi.spyOn(api, 'delete').mockResolvedValue({ data: {} })
  const view = render(<PushNotificationCard />)
  fireEvent.click(await screen.findByRole('button', { name: 'Desactivar' }))
  await waitFor(() => expect(screen.getByRole('button', { name: 'Activar' })).toBeEnabled())
  expect(remove).toHaveBeenCalledWith('/push/subscribe', { params: { endpoint: subscription.endpoint } })
  expect(subscription.unsubscribe).toHaveBeenCalledOnce()
  view.unmount()
  registration.pushManager.getSubscription.mockResolvedValue(null)
  api.post.mockClear()
  render(<PushNotificationCard />)
  await waitFor(() => expect(screen.getByRole('button', { name: 'Activar' })).toBeEnabled())
  expect(api.post).not.toHaveBeenCalled()
})

it('mantiene la suscripción y permite reintentar si falla la desactivación', async () => {
  const { newSubscription, registration } = installBrowserMocks()
  registration.pushManager.getSubscription.mockResolvedValue(newSubscription)
  Notification.permission = 'granted'
  vi.spyOn(api, 'delete').mockRejectedValueOnce(new Error('Sin conexión')).mockRejectedValueOnce({ response: { status: 404 } })
  render(<PushNotificationCard />)
  fireEvent.click(await screen.findByRole('button', { name: 'Desactivar' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Sin conexión')
  expect(newSubscription.unsubscribe).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'Desactivar' }))
  await waitFor(() => expect(screen.getByRole('button', { name: 'Activar' })).toBeEnabled())
  expect(newSubscription.unsubscribe).toHaveBeenCalledOnce()
})
