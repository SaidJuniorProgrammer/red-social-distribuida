import { describe, expect, it } from 'vitest'
import { isValidEmail } from '../src/utils/authValidation.js'

describe('validación de correo', () => {
  it.each([
    'correo sin espacios@example.com',
    'usuario@@example.com',
    'usuario@example',
    '@example.com',
    'usuario@example.',
  ])('rechaza %s', (email) => {
    expect(isValidEmail(email)).toBe(false)
  })

  it('acepta un correo con estructura válida', () => {
    expect(isValidEmail('admin@gmail.com')).toBe(true)
  })
})
