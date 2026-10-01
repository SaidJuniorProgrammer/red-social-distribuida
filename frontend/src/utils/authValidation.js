const EMAIL_PATTERN = /^\S+@\S+\.\S+$/

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
  } else if (!EMAIL_PATTERN.test(form.email)) {
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
