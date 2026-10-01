import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import AuthLayout from '../components/AuthLayout.jsx'
import FormField from '../components/FormField.jsx'
import PasswordField from '../components/PasswordField.jsx'
import useAuth from '../hooks/useAuth.js'
import useFocusOnError from '../hooks/useFocusOnError.js'
import { getRegistrationFeedback } from '../services/authErrors.js'
import { validateRegisterForm } from '../utils/authValidation.js'

const initialForm = {
  username: '',
  email: '',
  password: '',
  passwordConfirmation: '',
}

function RegisterPage() {
  const { isAuthenticated, register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState(initialForm)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  useFocusOnError(errors, 'register')

  if (isAuthenticated) return <Navigate to="/feed" replace />

  const updateField = ({ target }) => {
    setForm((current) => ({ ...current, [target.name]: target.value }))
    setErrors((current) => ({ ...current, [target.name]: undefined }))
    setFormError('')
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    const nextErrors = validateRegisterForm(form)
    setErrors(nextErrors)
    setFormError('')

    if (Object.keys(nextErrors).length > 0) {
      return
    }

    setIsSubmitting(true)
    try {
      await register({
        username: form.username.trim(),
        email: form.email.trim(),
        password: form.password,
      })
      navigate('/login', { replace: true, state: { registrationSuccess: true } })
    } catch (error) {
      const feedback = getRegistrationFeedback(error)

      if (feedback.field) {
        setErrors({ [feedback.field]: feedback.message })
      } else {
        setFormError(feedback.message)
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthLayout
      eyebrow="Nuevo nodo de identidad"
      title="Únete al Fediverso Pachyweb."
      description="Crea tu identificador criptográfico único. Tu perfil, tus contenidos y tus conexiones te pertenecen a ti, alojados en el nodo que elijas."
      visual={
        <ul className="feature-list">
          <li><span>◎</span><div><strong>Identidad soberana</strong><p>Sin depender de una gran corporación tecnológica.</p></div></li>
          <li><span>⌁</span><div><strong>Llaves criptográficas locales</strong><p>Protección de credenciales desde tu navegador.</p></div></li>
          <li><span>↗</span><div><strong>Portabilidad total</strong><p>Tu presencia digital permanece bajo tu control.</p></div></li>
        </ul>
      }
    >
      <div className="auth-card__heading">
        <span className="auth-card__icon" aria-hidden="true">✦</span>
        <div>
          <p>Pachyweb</p>
          <h2>Crear cuenta</h2>
        </div>
      </div>

      <form className="auth-form" onSubmit={handleSubmit} noValidate aria-busy={isSubmitting}>
        <FormField
          id="register-username"
          name="username"
          label="Nombre de usuario"
          hint="Será tu identificador para iniciar sesión."
          autoComplete="username"
          autoFocus
          placeholder="tu_usuario"
          value={form.username}
          error={errors.username}
          disabled={isSubmitting}
          onChange={updateField}
        />
        <FormField
          id="register-email"
          name="email"
          type="email"
          label="Correo electrónico"
          hint="Debe ser un correo válido y no estar registrado."
          autoComplete="email"
          placeholder="tu@correo.com"
          value={form.email}
          error={errors.email}
          disabled={isSubmitting}
          onChange={updateField}
        />
        <PasswordField
          id="register-password"
          name="password"
          label="Contraseña"
          hint="Usa al menos 8 caracteres."
          autoComplete="new-password"
          placeholder="Mínimo 8 caracteres"
          value={form.password}
          error={errors.password}
          disabled={isSubmitting}
          onChange={updateField}
        />
        <PasswordField
          id="register-password-confirmation"
          name="passwordConfirmation"
          label="Confirmar contraseña"
          hint="Escribe nuevamente la misma contraseña."
          autoComplete="new-password"
          placeholder="Repite tu contraseña"
          value={form.passwordConfirmation}
          error={errors.passwordConfirmation}
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
          {isSubmitting ? 'Creando cuenta…' : 'Crear cuenta'}
        </button>
      </form>

      <p className="auth-card__switch">
        ¿Ya tienes una cuenta? <Link to="/login">Iniciar sesión</Link>
      </p>
    </AuthLayout>
  )
}

export default RegisterPage
