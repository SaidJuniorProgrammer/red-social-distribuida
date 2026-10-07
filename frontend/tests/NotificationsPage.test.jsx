import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import AuthContext from '../src/context/authContext.js'
import NotificationsPage from '../src/pages/NotificationsPage.jsx'
import api from '../src/services/api.js'

function renderNotifications() {
  return render(<MemoryRouter><AuthContext.Provider value={{ user: { username: 'oscar' } }}>
    <NotificationsPage />
  </AuthContext.Provider></MemoryRouter>)
}

afterEach(() => { vi.restoreAllMocks(); vi.useRealTimers(); vi.unstubAllGlobals() })

it('muestra la actividad sin permiso de notificaciones, ordenada de reciente a antigua', async () => {
  vi.stubGlobal('Notification', { permission: 'denied' })
  vi.spyOn(api, 'get').mockResolvedValue({ data: { notificaciones: [
    { id_post: 'p1', autor: 'ana', titulo: 'Nueva publicación', mensaje: 'Antes', timestamp: '2026-10-01T10:00:00Z' },
    { id_post: 'p2', autor: 'said', titulo: 'Nuevo Like', mensaje: 'Después', timestamp: '2026-10-02T10:00:00Z' },
  ] } })
  renderNotifications()
  await screen.findByText('Después')
  expect(screen.getAllByRole('listitem').map((item) => item.textContent)).toEqual([
    expect.stringContaining('Después'), expect.stringContaining('Antes'),
  ])
  expect(screen.getByRole('link', { name: 'Ver perfil de @said' })).toHaveAttribute('href', '/perfil/said')
  expect(api.get).toHaveBeenCalledWith('/push/notificaciones/oscar', expect.objectContaining({ signal: expect.any(AbortSignal) }))
})

it('permite reintentar una carga fallida y muestra una bandeja vacía', async () => {
  vi.spyOn(api, 'get').mockRejectedValueOnce(new Error('Sin conexión')).mockResolvedValue({ data: {} })
  renderNotifications()
  await screen.findByText('No pudimos cargar la actividad. Inténtalo nuevamente.')
  fireEvent.click(screen.getByRole('button', { name: 'Actualizar' }))
  expect(await screen.findByText('No tienes notificaciones por ahora.')).toBeInTheDocument()
  expect(screen.queryByText('No pudimos cargar la actividad. Inténtalo nuevamente.')).not.toBeInTheDocument()
})

it('actualiza periódicamente y cancela las solicitudes al salir', async () => {
  vi.useFakeTimers()
  vi.spyOn(api, 'get').mockResolvedValue({ data: { notificaciones: [] } })
  const view = renderNotifications()
  await act(async () => {})
  await act(async () => { await vi.advanceTimersByTimeAsync(30_000) })
  expect(api.get).toHaveBeenCalledTimes(2)
  const signal = api.get.mock.calls[0][1].signal
  view.unmount()
  expect(signal.aborted).toBe(true)
  await vi.advanceTimersByTimeAsync(60_000)
  expect(api.get).toHaveBeenCalledTimes(2)
})

it('ignora respuestas que llegan después de salir de la pantalla', async () => {
  let resolve
  vi.spyOn(api, 'get').mockImplementation(() => new Promise((done) => { resolve = done }))
  const view = renderNotifications()
  view.unmount()
  await act(async () => resolve({ data: { notificaciones: [] } }))
  await waitFor(() => expect(screen.queryByText('No tienes notificaciones por ahora.')).not.toBeInTheDocument())
})
