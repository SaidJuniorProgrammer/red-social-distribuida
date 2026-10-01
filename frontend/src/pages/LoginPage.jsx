import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import AuthLayout from '../components/AuthLayout.jsx'
import FormField from '../components/FormField.jsx'
import PasswordField from '../components/PasswordField.jsx'
import useAuth from '../hooks/useAuth.js'
import useFocusOnError from '../hooks/useFocusOnError.js'
import { getLoginErrorMessage } from '../services/authErrors.js'
import { validateLoginForm } from '../utils/authValidation.js'

const initialForm = { username: '', password: '' }

function LoginPage() {
  const { isAuthenticated, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [form, setForm] = useState(initialForm)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  useFocusOnError(errors, 'login')

  if (isAuthenticated) return <Navigate to="/feed" replace />

  const updateField = ({ target }) => {
    setForm((current) => ({ ...current, [target.name]: target.value }))
    setErrors((current) => ({ ...current, [target.name]: undefined }))
    setFormError('')
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    const nextErrors = validateLoginForm(form)
    setErrors(nextErrors)
    setFormError('')

    if (Object.keys(nextErrors).length > 0) {
      return
    }

    setIsSubmitting(true)
    try {
      await login({ username: form.username.trim(), password: form.password })
      navigate(location.state?.from ?? '/feed', { replace: true })
    } catch (error) {
      setFormError(getLoginErrorMessage(error))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthLayout
      eyebrow="Protocolo Pachyweb v2.4"
      title="Soberanía descentralizada."
      description="Ingresa a tu nodo de la red para interactuar de forma libre, federada y distribuida, sin custodios de identidad ni censura estructural."
      visual={
        <div className="network-card" aria-label="Estado del nodo local">
          <div className="network-card__header">
            <div>
              <strong>mainnet-node-lavanda</strong>
              <span>pachyweb://nodo.local</span>
            </div>
            <span className="status-pill">99.99% uptime</span>
          </div>
          <div className="network-chart" aria-hidden="true">
            <span />
          </div>
          <div className="network-card__stats">
            <span>Latencia federada</span>
            <strong>14.2 ms</strong>
          </div>
        </div>
      }
    >
      <div className="auth-card__heading">
        <span className="auth-card__icon" aria-hidden="true">↪</span>
        <div>
          <p>Pachyweb</p>
          <h2>Iniciar sesión</h2>
        </div>
      </div>

      {location.state?.registrationSuccess && (
        <p className="form-message form-message--success" role="status" aria-live="polite">
          Cuenta creada correctamente. Ya puedes iniciar sesión.
        </p>
      )}

      <form className="auth-form" onSubmit={handleSubmit} noValidate aria-busy={isSubmitting}>
        <FormField
          id="login-username"
          name="username"
          label="Nombre de usuario"
          hint="Usa el nombre que elegiste al crear tu cuenta."
          autoComplete="username"
          autoFocus
          placeholder="tu_usuario"
          value={form.username}
          error={errors.username}
          disabled={isSubmitting}
          onChange={updateField}
        />
        <PasswordField
          id="login-password"
          name="password"
          label="Contraseña"
          hint="La contraseña distingue mayúsculas y minúsculas."
          autoComplete="current-password"
          placeholder="Tu contraseña"
          value={form.password}
          error={errors.password}
          disabled={isSubmitting}
          onChange={updateField}
        />

        {formError && (
          <p className="form-message form-message--error" role="alert">
            {formError}
          </p>
        )}

        <button className="primary-button" type="submit" disabled={isSubmitting}>
          {isSubmitting && <span className="spinner" aria-hidden="true" />}
          {isSubmitting ? 'Iniciando sesión…' : 'Iniciar sesión'}
        </button>
      </form>

      <p className="auth-card__switch">
        ¿No tienes una cuenta? <Link to="/registro">Crear una cuenta</Link>
      </p>
    </AuthLayout>
  )
}

export default LoginPage
