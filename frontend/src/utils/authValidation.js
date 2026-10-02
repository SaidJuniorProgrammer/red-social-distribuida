export function isValidEmail(email) {
  const atIndex = email.indexOf('@')
  const dotIndex = email.lastIndexOf('.')
  const hasWhitespace = [...email].some((character) => character.trim() === '')

  return (
    atIndex > 0 &&
    atIndex === email.lastIndexOf('@') &&
    dotIndex > atIndex + 1 &&
    dotIndex < email.length - 1 &&
    !hasWhitespace
  )
}

export function validateLoginForm(form) {
  const errors = {}

  if (!form.username.trim()) errors.username = 'Ingresa tu nombre de usuario.'
  if (!form.password) errors.password = 'Ingresa tu contraseña.'

  return errors
}

export function validateRegisterForm(form) {
  const errors = {}

  if (!form.username.trim()) errors.username = 'Ingresa un nombre de usuario.'

  if (!form.email.trim()) {
    errors.email = 'Ingresa tu correo electrónico.'
  } else if (!isValidEmail(form.email)) {
    errors.email = 'Ingresa un correo electrónico válido.'
  }

  if (form.password.length < 8) {
    errors.password = 'La contraseña debe tener al menos 8 caracteres.'
  }

  if (form.password !== form.passwordConfirmation) {
    errors.passwordConfirmation = 'Las contraseñas no coinciden.'
  }

  return errors
}
