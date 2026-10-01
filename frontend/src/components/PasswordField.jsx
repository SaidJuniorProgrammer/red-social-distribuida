import { useState } from 'react'
import FormField from './FormField.jsx'

function PasswordField({ id, ...props }) {
  const [isVisible, setIsVisible] = useState(false)

  return (
    <div className="password-field">
      <FormField id={id} type={isVisible ? 'text' : 'password'} {...props} />
      <button
        className="password-field__toggle"
        type="button"
        disabled={props.disabled}
        aria-label={isVisible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
        aria-pressed={isVisible}
        onClick={() => setIsVisible((current) => !current)}
      >
        {isVisible ? 'Ocultar' : 'Mostrar'}
      </button>
    </div>
  )
}

export default PasswordField
