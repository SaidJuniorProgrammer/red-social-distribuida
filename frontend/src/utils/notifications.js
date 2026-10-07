const NOTIFICATION_TYPES = Object.freeze({
  follow: 'seguimiento',
  like: 'like',
  message: 'mensaje',
  publication: 'publicacion',
})

function normalizedText(value) {
  return String(value ?? '').trim().toLocaleLowerCase('es')
}

export function getNotificationType(notification) {
  const explicitType = normalizedText(notification.tipo ?? notification.type)
  if (['follow', 'seguimiento'].includes(explicitType)) return NOTIFICATION_TYPES.follow
  if (['message', 'mensaje'].includes(explicitType)) return NOTIFICATION_TYPES.message
  if (['like', 'reaccion', 'reacción'].includes(explicitType)) return NOTIFICATION_TYPES.like
  if (['post', 'publication', 'publicacion', 'publicación'].includes(explicitType)) return NOTIFICATION_TYPES.publication

  const description = normalizedText(`${notification.titulo} ${notification.mensaje}`)
  if (description.includes('mensaje')) return NOTIFICATION_TYPES.message
  if (description.includes('sigu')) return NOTIFICATION_TYPES.follow
  if (description.includes('like') || description.includes('reaccion')) return NOTIFICATION_TYPES.like
  return NOTIFICATION_TYPES.publication
}

export function getNotificationId(notification) {
  return notification.idNotificacion ?? notification.id_notificacion ?? notification.id ?? null
}

export function getNotificationActor(notification) {
  return notification.actor ?? notification.autor ?? notification.usuario_origen ?? ''
}

export function getNotificationTimestamp(notification) {
  return notification.fecha ?? notification.timestamp ?? ''
}

export function getNotificationMessage(notification) {
  return notification.mensaje ?? notification.titulo ?? ''
}

export function getNotificationKey(notification) {
  return getNotificationId(notification) ?? [
    getNotificationType(notification),
    getNotificationActor(notification),
    notification.referencia,
    getNotificationTimestamp(notification),
    notification.titulo,
    notification.mensaje,
  ].join(':')
}

export function isNotificationRead(notification) {
  return notification.leida === true || notification.leido === true || notification.read === true
}

function safeInternalDestination(value) {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//')
    ? value
    : null
}

// El backend usa /mensajes como referencia de los avisos de chat; en el frontend la ruta es /chat.
function isBackendChatReference(destination) {
  return destination?.split(/[?#]/)[0] === '/mensajes'
}

export function getNotificationDestination(notification) {
  const suppliedDestination = safeInternalDestination(
    notification.referencia ?? notification.url_interna ?? notification.url,
  )
  if (suppliedDestination && !isBackendChatReference(suppliedDestination)) return suppliedDestination

  const actor = encodeURIComponent(getNotificationActor(notification))
  switch (getNotificationType(notification)) {
    case NOTIFICATION_TYPES.follow:
      return actor ? `/perfil/${actor}` : '/explorar'
    case NOTIFICATION_TYPES.message:
      return actor ? `/chat?usuario=${actor}` : '/chat'
    default:
      return '/feed'
  }
}

export function getNotificationTypeLabel(notification) {
  switch (getNotificationType(notification)) {
    case NOTIFICATION_TYPES.follow:
      return 'Nuevo seguimiento'
    case NOTIFICATION_TYPES.message:
      return 'Nuevo mensaje'
    case NOTIFICATION_TYPES.like:
      return 'Nuevo like'
    default:
      return 'Nueva publicación'
  }
}
