import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import AuthContext from '../src/context/authContext.js'
import CreatePostPage from '../src/pages/CreatePostPage.jsx'
import api from '../src/services/api.js'

const user = { username: 'gualter' }

function renderCreatePost() {
  return render(
    <MemoryRouter initialEntries={['/publicar']}>
      <AuthContext.Provider value={{ user }}>
        <Routes>
          <Route path="/publicar" element={<CreatePostPage />} />
          <Route path="/feed" element={<h1>Inicio actualizado</h1>} />
        </Routes>
      </AuthContext.Provider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  Object.defineProperty(URL, 'createObjectURL', {
    configurable: true,
    value: vi.fn(() => 'blob:preview'),
  })
  Object.defineProperty(URL, 'revokeObjectURL', {
    configurable: true,
    value: vi.fn(),
  })
})

afterEach(() => {
  vi.restoreAllMocks()
})

it('crea una publicación de solo texto y vuelve a Inicio', async () => {
  const request = vi.spyOn(api, 'post').mockResolvedValue({
    data: { id_post: 'p1', texto: 'Hola red' },
  })
  renderCreatePost()

  fireEvent.change(screen.getByLabelText('Contenido de la publicación'), {
    target: { value: '  Hola red  ' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Publicar' }))

  expect(await screen.findByRole('heading', { name: 'Inicio actualizado' })).toBeInTheDocument()
  expect(request).toHaveBeenCalledWith('/posts', expect.any(FormData))
  const formData = request.mock.calls[0][1]
  expect(formData.get('texto')).toBe('Hola red')
  expect(formData.get('archivo')).toBeNull()
})

it('permite previsualizar, reemplazar y quitar una imagen', () => {
  renderCreatePost()
  const imageInput = screen.getByLabelText('Seleccionar imagen para la publicación')
  const image = new File(['imagen'], 'foto.png', { type: 'image/png' })

  fireEvent.change(imageInput, { target: { files: [image] } })

  expect(screen.getByRole('img', { name: 'Vista previa de la imagen seleccionada' }))
    .toHaveAttribute('src', 'blob:preview')
  expect(screen.getByText('Cambiar imagen')).toBeInTheDocument()

  fireEvent.click(screen.getByRole('button', { name: 'Quitar imagen' }))
  expect(screen.queryByRole('img', { name: 'Vista previa de la imagen seleccionada' }))
    .not.toBeInTheDocument()
})

it('envía la imagen seleccionada junto al texto', async () => {
  const request = vi.spyOn(api, 'post').mockResolvedValue({ data: { id_post: 'p2' } })
  renderCreatePost()
  const image = new File(['imagen'], 'foto.webp', { type: 'image/webp' })

  fireEvent.change(screen.getByLabelText('Contenido de la publicación'), {
    target: { value: 'Publicación con imagen' },
  })
  fireEvent.change(screen.getByLabelText('Seleccionar imagen para la publicación'), {
    target: { files: [image] },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Publicar' }))

  await screen.findByRole('heading', { name: 'Inicio actualizado' })
  const formData = request.mock.calls[0][1]
  expect(formData.get('archivo')).toBe(image)
})

it('rechaza archivos inválidos, imágenes demasiado grandes y texto vacío', () => {
  renderCreatePost()
  const imageInput = screen.getByLabelText('Seleccionar imagen para la publicación')

  fireEvent.change(imageInput, {
    target: { files: [new File(['texto'], 'nota.txt', { type: 'text/plain' })] },
  })
  expect(screen.getByRole('alert')).toHaveTextContent('archivo de imagen válido')

  const oversizedImage = new File(
    [new Uint8Array((10 * 1024 * 1024) + 1)],
    'grande.png',
    { type: 'image/png' },
  )
  fireEvent.change(imageInput, { target: { files: [oversizedImage] } })
  expect(screen.getByRole('alert')).toHaveTextContent('no puede superar los 10 MB')

  fireEvent.change(screen.getByLabelText('Contenido de la publicación'), {
    target: { value: '   ' },
  })
  fireEvent.submit(screen.getByRole('button', { name: 'Publicar' }).closest('form'))
  expect(screen.getByRole('alert')).toHaveTextContent('Escribe algo antes de publicar')
})

it('muestra el error devuelto por el backend y permite reintentar', async () => {
  vi.spyOn(api, 'post').mockRejectedValue({
    response: { data: { message: 'No se pudo guardar la publicación.' } },
  })
  renderCreatePost()

  fireEvent.change(screen.getByLabelText('Contenido de la publicación'), {
    target: { value: 'Contenido temporal' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Publicar' }))

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'No se pudo guardar la publicación.',
  )
  await waitFor(() => expect(screen.getByRole('button', { name: 'Publicar' })).toBeEnabled())
})
