import { describe, expect, it } from 'vitest'
import {
  getNotificationDestination,
  getNotificationType,
  getNotificationTypeLabel,
} from '../src/utils/notifications.js'

describe('presentación de notificaciones', () => {
  it.each([
    ['FOLLOW', 'seguimiento', '/perfil/ana', 'Nuevo seguimiento'],
    ['MENSAJE', 'mensaje', '/chat?usuario=ana', 'Nuevo mensaje'],
    ['LIKE', 'like', '/feed', 'Nuevo like'],
    ['POST', 'publicacion', '/feed', 'Nueva publicación'],
  ])('adapta el tipo %s del backend', (tipoBackend, tipoNormalizado, destination, label) => {
    const notification = { tipo: tipoBackend, actor: 'ana' }
    expect(getNotificationType(notification)).toBe(tipoNormalizado)
    expect(getNotificationDestination(notification)).toBe(destination)
    expect(getNotificationTypeLabel(notification)).toBe(label)
  })

  it('prioriza la referencia interna enviada por el backend', () => {
    expect(getNotificationDestination({ referencia: '/perfil/carlos', tipo: 'LIKE' })).toBe('/perfil/carlos')
  })

  it('acepta solo destinos internos y descarta URLs externas', () => {
    expect(getNotificationDestination({ referencia: 'https://externo.example', tipo: 'LIKE' })).toBe('/feed')
    expect(getNotificationDestination({ referencia: '//evil.example', tipo: 'MENSAJE', actor: 'ana' })).toBe('/chat?usuario=ana')
  })
})
