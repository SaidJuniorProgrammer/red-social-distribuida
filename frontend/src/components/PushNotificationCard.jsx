import { useEffect, useState } from 'react'
import {
  getCurrentPushSubscription,
  isWebPushSupported,
  subscribeUserToPush,
} from '../services/webpush.js'

const PUSH_STATE = Object.freeze({
  checking: 'checking',
  available: 'available',
  activating: 'activating',
  active: 'active',
  denied: 'denied',
  unsupported: 'unsupported',
})

function PushNotificationCard() {
  const [state, setState] = useState(PUSH_STATE.checking)
  const [errorMessage, setErrorMessage] = useState('')

  useEffect(() => {
    let isCurrent = true

    const checkSubscription = async () => {
      if (!isWebPushSupported()) {
        setState(PUSH_STATE.unsupported)
        return
      }

      if (Notification.permission === 'denied') {
        setState(PUSH_STATE.denied)
        return
      }

      const subscription = await getCurrentPushSubscription()
      if (isCurrent) {
        setState(subscription ? PUSH_STATE.active : PUSH_STATE.available)
      }
    }

    checkSubscription().catch(() => {
      if (isCurrent) setState(PUSH_STATE.available)
    })
    return () => {
      isCurrent = false
    }
  }, [])

  const activateNotifications = async () => {
    setState(PUSH_STATE.activating)
    setErrorMessage('')

    try {
      await subscribeUserToPush()
      setState(PUSH_STATE.active)
    } catch (error) {
      setState(
        Notification.permission === 'denied'
          ? PUSH_STATE.denied
          : PUSH_STATE.available,
      )
      setErrorMessage(error.message)
    }
  }

  const isActive = state === PUSH_STATE.active
  const isActivating = state === PUSH_STATE.activating
  const cannotActivate = [
    PUSH_STATE.checking,
    PUSH_STATE.denied,
    PUSH_STATE.unsupported,
  ].includes(state)

  return (
    <section className="push-card" aria-labelledby="push-title">
      <span className="push-card__icon" aria-hidden="true">◔</span>
      <div className="push-card__content">
        <p className="push-card__eyebrow">Actividad de tu red</p>
        <h2 id="push-title">Notificaciones de publicaciones</h2>
        <p>
          Recibe una alerta cuando alguien que sigues publique algo nuevo,
          incluso si estás en otra pestaña.
        </p>
        {isActive && <p className="push-card__success" role="status">Notificaciones activadas</p>}
        {state === PUSH_STATE.denied && (
          <p className="form-message form-message--error" role="alert">
            Las notificaciones están bloqueadas. Puedes habilitarlas desde la configuración del navegador.
          </p>
        )}
        {state === PUSH_STATE.unsupported && (
          <p className="form-message form-message--error" role="alert">
            Este navegador no admite notificaciones web.
          </p>
        )}
        {errorMessage && state !== PUSH_STATE.denied && (
          <p className="form-message form-message--error" role="alert">{errorMessage}</p>
        )}
      </div>
      <button
        className="secondary-button"
        type="button"
        disabled={cannotActivate || isActive || isActivating}
        onClick={activateNotifications}
      >
        {isActivating ? 'Activando…' : isActive ? 'Activadas' : 'Activar'}
      </button>
    </section>
  )
}

export default PushNotificationCard
