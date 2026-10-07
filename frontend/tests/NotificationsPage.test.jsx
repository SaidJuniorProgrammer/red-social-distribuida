import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import AuthContext from '../src/context/authContext.js'
import NotificationsProvider from '../src/context/NotificationsProvider.jsx'
import NotificationsPage from '../src/pages/NotificationsPage.jsx'
import api from '../src/services/api.js'

function renderNotifications() {
  return render(<MemoryRouter><AuthContext.Provider value={{ user: { username: 'oscar' } }}>
    <NotificationsProvider><NotificationsPage /></NotificationsProvider>
  </AuthContext.Provider></MemoryRouter>)
}

afterEach(() => { vi.restoreAllMocks(); vi.useRealTimers(); vi.unstubAllGlobals() })

it('muestra la actividad sin permiso de notificaciones, ordenada de reciente a antigua', async () => {
  vi.stubGlobal('Notification', { permission: 'denied' })
  vi.spyOn(api, 'get').mockResolvedValue({ data: { notificaciones: [
    { idNotificacion: 'n1', tipo: 'POST', actor: 'ana', mensaje: 'Antes', referencia: '/feed', fecha: '2026-10-01T10:00:00Z', leida: false },
    { idNotificacion: 'n2', tipo: 'LIKE', actor: 'said', mensaje: 'Después', referencia: '/feed', fecha: '2026-10-02T10:00:00Z', leida: false },
  ] } })
  renderNotifications()
  await screen.findByText('Después')
  expect(screen.getAllByRole('listitem').map((item) => item.textContent)).toEqual([
    expect.stringContaining('Después'), expect.stringContaining('Antes'),
  ])
  expect(screen.getAllByRole('link', { name: 'Abrir notificación' })[0]).toHaveAttribute('href', '/feed')
  expect(api.get).toHaveBeenCalledWith('/notificaciones', expect.objectContaining({ signal: expect.any(AbortSignal) }))
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

it('muestra los tipos, navega a su destino y marca como leída al abrir', async () => {
  vi.stubGlobal('Notification', { permission: 'denied' })
  vi.spyOn(api, 'get').mockResolvedValue({ data: { notificaciones: [
    { idNotificacion: 'n-follow', tipo: 'FOLLOW', actor: 'ana', mensaje: 'Ana te siguió', referencia: '/perfil/ana', fecha: '2026-10-03T10:00:00Z', leida: false },
    { idNotificacion: 'n-message', tipo: 'MENSAJE', actor: 'said', mensaje: 'Hola', referencia: '/chat?usuario=said', fecha: '2026-10-02T10:00:00Z', leida: false },
    { idNotificacion: 'n-like', tipo: 'LIKE', actor: 'maria', mensaje: 'Le gustó tu publicación', referencia: '/feed', fecha: '2026-10-01T10:00:00Z', leida: true },
  ] } })
  const markRead = vi.spyOn(api, 'put').mockResolvedValue({ data: {} })
  renderNotifications()

  expect(await screen.findByText('Ana te siguió')).toBeInTheDocument()
  const links = screen.getAllByRole('link', { name: 'Abrir notificación' })
  expect(links[0]).toHaveAttribute('href', '/perfil/ana')
  expect(links[1]).toHaveAttribute('href', '/chat?usuario=said')
  expect(links[2]).toHaveAttribute('href', '/feed')

  fireEvent.click(links[0])
  await waitFor(() => expect(markRead).toHaveBeenCalledWith('/notificaciones/n-follow/leer'))
})

it('marca todas como leídas desde la cabecera y oculta el botón', async () => {
  vi.stubGlobal('Notification', { permission: 'denied' })
  vi.spyOn(api, 'get').mockResolvedValue({ data: { notificaciones: [
    { idNotificacion: 'a', tipo: 'LIKE', actor: 'ana', mensaje: 'Uno', referencia: '/feed', fecha: '2026-10-02T10:00:00Z', leida: false },
    { idNotificacion: 'b', tipo: 'POST', actor: 'said', mensaje: 'Dos', referencia: '/feed', fecha: '2026-10-01T10:00:00Z', leida: false },
  ] } })
  const markAll = vi.spyOn(api, 'put').mockResolvedValue({ data: {} })
  renderNotifications()

  fireEvent.click(await screen.findByRole('button', { name: 'Marcar todas como leídas' }))

  await waitFor(() => expect(markAll).toHaveBeenCalledWith('/notificaciones/leer-todas'))
  await waitFor(() => expect(
    screen.queryByRole('button', { name: 'Marcar todas como leídas' }),
  ).not.toBeInTheDocument())
})

it('conserva una notificación abierta aunque falle marcar todas como leídas', async () => {
  vi.stubGlobal('Notification', { permission: 'denied' })
  vi.spyOn(api, 'get').mockResolvedValue({ data: { notificaciones: [
    { idNotificacion: 'a', tipo: 'LIKE', actor: 'ana', mensaje: 'Uno', referencia: '/feed', fecha: '2026-10-02T10:00:00Z', leida: false },
    { idNotificacion: 'b', tipo: 'POST', actor: 'said', mensaje: 'Dos', referencia: '/feed', fecha: '2026-10-01T10:00:00Z', leida: false },
  ] } })
  vi.spyOn(api, 'put').mockImplementation((url) => (
    url.endsWith('/leer-todas') ? Promise.reject(new Error('fallo')) : Promise.resolve({ data: {} })
  ))
  renderNotifications()

  fireEvent.click(await screen.findByRole('button', { name: 'Marcar todas como leídas' }))
  fireEvent.click(screen.getAllByRole('link', { name: 'Abrir notificación' })[0])

  await waitFor(() => expect(api.put).toHaveBeenCalledWith('/notificaciones/a/leer'))
  await waitFor(() => expect(screen.getByText('Dos').closest('li')).toHaveClass('notification-inbox__unread'))
  expect(screen.getByText('Uno').closest('li')).not.toHaveClass('notification-inbox__unread')
})
