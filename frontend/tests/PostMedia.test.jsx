import { fireEvent, render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import PostMedia from '../src/components/PostMedia.jsx'

it('reemplaza una imagen rota por un estado de error y reintenta solo al pulsar el botón', () => {
  render(<PostMedia className="post-card__media" src="https://cdn.example/perdida.jpg"
    type="image" label="Contenido publicado por @oscar" />)

  const image = screen.getByRole('img', { name: 'Contenido publicado por @oscar' })
  fireEvent.error(image)

  expect(screen.queryByRole('img')).not.toBeInTheDocument()
  expect(screen.getByRole('status')).toHaveTextContent('Contenido multimedia no disponible')
  expect(screen.getByText('No pudimos cargar este archivo.')).toBeInTheDocument()

  const retryButton = screen.getByRole('button', { name: 'Reintentar' })
  expect(screen.queryByRole('img')).not.toBeInTheDocument()
  fireEvent.click(retryButton)

  const retriedImage = screen.getByRole('img', { name: 'Contenido publicado por @oscar' })
  expect(retriedImage).toHaveAttribute('src', 'https://cdn.example/perdida.jpg')
  expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
})

it('maneja el error de un video y permite un reintento manual', () => {
  render(<PostMedia src="https://cdn.example/perdido.mp4"
    type="video" label="Video publicado por @oscar" />)

  fireEvent.error(screen.getByLabelText('Video publicado por @oscar'))
  expect(screen.getByRole('status')).toHaveTextContent('Contenido multimedia no disponible')
  expect(screen.getByRole('status')).not.toHaveClass('undefined')

  fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
  const video = screen.getByLabelText('Video publicado por @oscar')
  expect(video.tagName).toBe('VIDEO')
  expect(video).toHaveAttribute('src', 'https://cdn.example/perdido.mp4')
  expect(video).toHaveAttribute('controls')
})

it('vuelve a intentar un archivo al mostrar la secuencia A → B → A', () => {
  const view = render(<PostMedia className="post-card__media" src="https://cdn.example/uno.jpg"
    type="image" label="Primera imagen" />)

  fireEvent.error(screen.getByRole('img', { name: 'Primera imagen' }))
  expect(screen.getByRole('status')).toBeInTheDocument()

  view.rerender(<PostMedia className="post-card__media" src="https://cdn.example/dos.jpg"
    type="image" label="Segunda imagen" />)
  expect(screen.getByRole('img', { name: 'Segunda imagen' })).toHaveAttribute(
    'src',
    'https://cdn.example/dos.jpg',
  )
  expect(screen.queryByRole('status')).not.toBeInTheDocument()

  view.rerender(<PostMedia className="post-card__media" src="https://cdn.example/uno.jpg"
    type="image" label="Primera imagen otra vez" />)
  expect(screen.getByRole('img', { name: 'Primera imagen otra vez' })).toHaveAttribute(
    'src',
    'https://cdn.example/uno.jpg',
  )
  expect(screen.queryByRole('status')).not.toBeInTheDocument()
})
