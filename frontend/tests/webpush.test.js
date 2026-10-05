import { afterEach, describe, expect, it, vi } from 'vitest'
import api from '../src/services/api.js'
import {
  getCurrentPushSubscription,
  isWebPushSupported,
  subscribeUserToPush,
  urlBase64ToUint8Array,
} from '../src/services/webpush.js'

function installPushMocks({ permission = 'granted', subscription = null } = {}) {
  const subscribe = vi.fn()
  const registration = {
    pushManager: {
      getSubscription: vi.fn().mockResolvedValue(subscription),
      subscribe,
    },
  }
  const register = vi.fn().mockResolvedValue(registration)

  vi.stubGlobal('Notification', {
    permission,
    requestPermission: vi.fn().mockResolvedValue(permission),
  })
  vi.stubGlobal('PushManager', class PushManager {})
  Object.defineProperty(navigator, 'serviceWorker', {
    configurable: true,
    value: { ready: Promise.resolve(registration), register },
  })

  return { register, registration, subscribe }
}

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  Object.defineProperty(navigator, 'serviceWorker', {
    configurable: true,
    value: undefined,
  })
})

describe('suscripción Web Push', () => {
  it('detecta navegadores sin las funciones necesarias', () => {
    vi.stubGlobal('Notification', undefined)
    vi.stubGlobal('PushManager', undefined)

    expect(isWebPushSupported()).toBe(false)
  })

  it('no busca suscripciones si el navegador no tiene soporte', async () => {
    vi.stubGlobal('Notification', undefined)
    vi.stubGlobal('PushManager', undefined)

    await expect(getCurrentPushSubscription()).resolves.toBeNull()
  })

  it('convierte una llave VAPID al formato que espera el navegador', () => {
    expect([...urlBase64ToUint8Array('AQIDBA')]).toEqual([1, 2, 3, 4])
  })

  it('consulta una suscripción existente', async () => {
    const existingSubscription = { endpoint: 'https://push.example/existing' }
    installPushMocks({ subscription: existingSubscription })

    await expect(getCurrentPushSubscription()).resolves.toBe(existingSubscription)
  })

  it('crea la suscripción y la registra con el contrato del backend', async () => {
    const newSubscription = {
      endpoint: 'https://push.example/oscar',
      toJSON: () => ({ keys: { p256dh: 'publica', auth: 'secreta' } }),
    }
    const { subscribe } = installPushMocks()
    subscribe.mockResolvedValue(newSubscription)
    vi.spyOn(api, 'get').mockResolvedValue({ data: { publicKey: 'AQIDBA' } })
    vi.spyOn(api, 'post').mockResolvedValue({ data: {} })

    await expect(subscribeUserToPush()).resolves.toBe(newSubscription)
    expect(subscribe).toHaveBeenCalledWith({
      userVisibleOnly: true,
      applicationServerKey: new Uint8Array([1, 2, 3, 4]),
    })
    expect(api.post).toHaveBeenCalledWith('/push/subscribe', {
      endpoint: 'https://push.example/oscar',
      keys: { p256dh: 'publica', auth: 'secreta' },
    })
  })

  it('reutiliza una suscripción existente sin pedir otra llave', async () => {
    const existingSubscription = {
      endpoint: 'https://push.example/existing',
      options: { applicationServerKey: new Uint8Array([1, 2, 3, 4]) },
      toJSON: () => ({ keys: { p256dh: 'publica', auth: 'secreta' } }),
      unsubscribe: vi.fn().mockResolvedValue(true),
    }
    installPushMocks({ subscription: existingSubscription })
    const getPublicKey = vi.spyOn(api, 'get').mockResolvedValue({
      data: { publicKey: 'AQIDBA' },
    })
    vi.spyOn(api, 'post').mockResolvedValue({ data: {} })

    await expect(subscribeUserToPush()).resolves.toBe(
      existingSubscription,
    )
    expect(getPublicKey).toHaveBeenCalledOnce()
    expect(api.post).toHaveBeenCalledWith('/push/subscribe', {
      endpoint: 'https://push.example/existing',
      keys: { p256dh: 'publica', auth: 'secreta' },
    })
  })

  it('renueva una suscripción creada con otra llave VAPID', async () => {
    const previousSubscription = {
      endpoint: 'https://push.example/anterior',
      options: { applicationServerKey: new Uint8Array([9, 9, 9]) },
      unsubscribe: vi.fn().mockResolvedValue(true),
    }
    const nextSubscription = {
      endpoint: 'https://push.example/renovada',
      options: { applicationServerKey: new Uint8Array([1, 2, 3, 4]) },
      toJSON: () => ({ keys: { p256dh: 'nueva', auth: 'nueva-auth' } }),
    }
    const { subscribe } = installPushMocks({ subscription: previousSubscription })
    subscribe.mockResolvedValue(nextSubscription)
    vi.spyOn(api, 'get').mockResolvedValue({ data: { publicKey: 'AQIDBA' } })
    vi.spyOn(api, 'post').mockResolvedValue({ data: {} })
    vi.spyOn(api, 'delete').mockResolvedValue({ data: {} })

    await expect(subscribeUserToPush()).resolves.toBe(nextSubscription)
    expect(previousSubscription.unsubscribe).toHaveBeenCalledOnce()
    expect(api.delete).toHaveBeenCalledWith('/push/subscribe', {
      params: { endpoint: previousSubscription.endpoint },
    })
    expect(subscribe).toHaveBeenCalledOnce()
  })

  it('no intenta suscribir cuando el permiso fue rechazado', async () => {
    installPushMocks({ permission: 'denied' })
    const getPublicKey = vi.spyOn(api, 'get')

    await expect(subscribeUserToPush()).rejects.toThrow(
      'Debes permitir las notificaciones',
    )
    expect(getPublicKey).not.toHaveBeenCalled()
  })
})
