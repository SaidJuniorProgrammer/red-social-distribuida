import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import PushNotificationCard from '../src/components/PushNotificationCard.jsx'
import api from '../src/services/api.js'

function installBrowserMocks({ existingSubscription = null } = {}) {
  const newSubscription = {
    endpoint: 'https://push.example/oscar',
    toJSON: () => ({ keys: { p256dh: 'publica', auth: 'secreta' } }),
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
    toJSON: () => ({ keys: { p256dh: 'publica', auth: 'secreta' } }),
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
