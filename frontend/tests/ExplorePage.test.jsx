import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import ExplorePage from '../src/pages/ExplorePage.jsx'
import api from '../src/services/api.js'

afterEach(() => {
  vi.restoreAllMocks()
})

it('muestra únicamente las cuentas registradas devueltas por el servidor', async () => {
  vi.spyOn(api, 'get').mockResolvedValue({
    data: { usuarios: [{ username: 'said' }] },
  })
  render(<ExplorePage />)

  fireEvent.change(screen.getByLabelText('Nombre de usuario'), {
    target: { value: 'sa' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))

  expect(await screen.findByText('@said')).toBeInTheDocument()
  expect(api.get).toHaveBeenCalledWith('/usuarios', expect.objectContaining({
    params: { query: 'sa' },
  }))
})

it('informa cuando no encuentra cuentas o la búsqueda no está disponible', async () => {
  const request = vi.spyOn(api, 'get')
    .mockResolvedValueOnce({ data: { usuarios: [] } })
    .mockRejectedValueOnce(new Error('Servidor no disponible'))
  render(<ExplorePage />)

  const input = screen.getByLabelText('Nombre de usuario')
  fireEvent.change(input, { target: { value: 'nadie' } })
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
  expect(await screen.findByRole('status')).toHaveTextContent(
    'No encontramos cuentas registradas con ese nombre.',
  )

  fireEvent.change(input, { target: { value: 'otro' } })
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
  await waitFor(() => expect(request).toHaveBeenCalledTimes(2))
  expect(await screen.findByRole('status')).toHaveTextContent(
    'No pudimos realizar la búsqueda en este momento.',
  )
})

it('ignora una respuesta anterior cuando cambia la consulta', async () => {
  let resolveRequest
  vi.spyOn(api, 'get').mockImplementation(() => new Promise((resolve) => {
    resolveRequest = resolve
  }))
  render(<ExplorePage />)

  const input = screen.getByLabelText('Nombre de usuario')
  fireEvent.change(input, { target: { value: 'ana' } })
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
  fireEvent.change(input, { target: { value: 'luis' } })

  await act(async () => {
    resolveRequest({ data: { usuarios: [{ username: 'ana' }] } })
  })

  expect(screen.queryByText('@ana')).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Buscar' })).toBeEnabled()
})
