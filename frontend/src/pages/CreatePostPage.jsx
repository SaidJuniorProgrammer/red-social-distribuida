import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import useAuth from '../hooks/useAuth.js'
import PostMedia from '../components/PostMedia.jsx'
import { createPost } from '../services/posts.js'
import { formatDisplayName, getInitial } from '../utils/userDisplay.js'

const MAX_POST_LENGTH = 500
const MAX_IMAGE_SIZE = 10 * 1024 * 1024

function CreatePostPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const imageInputRef = useRef(null)
  const [text, setText] = useState('')
  const [image, setImage] = useState(null)
  const [previewUrl, setPreviewUrl] = useState('')
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl)
  }, [previewUrl])

  const selectImage = ({ target }) => {
    const selectedImage = target.files?.[0]
    setError('')

    if (!selectedImage) {
      setImage(null)
      setPreviewUrl('')
      return
    }

    if (!selectedImage.type.startsWith('image/') && !selectedImage.type.startsWith('video/')) {
      target.value = ''
      setImage(null)
      setPreviewUrl('')
      setError('Selecciona una imagen o un video válido.')
      return
    }

    if (selectedImage.size > MAX_IMAGE_SIZE) {
      target.value = ''
      setImage(null)
      setPreviewUrl('')
      setError('El archivo no puede superar los 10 MB.')
      return
    }

    setImage(selectedImage)
    setPreviewUrl(URL.createObjectURL(selectedImage))
  }

  const removeImage = () => {
    if (imageInputRef.current) imageInputRef.current.value = ''
    setImage(null)
    setPreviewUrl('')
    setError('')
  }

  const submitPost = async (event) => {
    event.preventDefault()
    const normalizedText = text.trim()

    if (!normalizedText) {
      setError('Escribe algo antes de publicar.')
      return
    }

    setIsSubmitting(true)
    setError('')
    try {
      await createPost({ image, text: normalizedText })
      void navigate('/feed', {
        replace: true,
        state: { publicationCreated: true },
      })
    } catch (requestError) {
      setError(
        requestError.response?.data?.message ??
        'No pudimos crear la publicación. Inténtalo de nuevo.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="create-post-page">
      <header className="create-post-page__header">
        <div>
          <p>Comparte con tu red</p>
          <h1>Crear publicación</h1>
        </div>
        <Link className="secondary-button create-post-page__back" to="/feed">
          Volver a Inicio
        </Link>
      </header>

      <form className="post-composer" onSubmit={submitPost}>
        <div className="post-composer__author">
          <span className="messages-avatar" aria-hidden="true">
            {getInitial(user?.username)}
          </span>
          <span>
            <strong>{formatDisplayName(user?.username)}</strong>
            <small>@{user?.username}</small>
          </span>
        </div>

        <label className="sr-only" htmlFor="post-text">Contenido de la publicación</label>
        <textarea
          id="post-text"
          maxLength={MAX_POST_LENGTH}
          placeholder="¿Qué quieres compartir?"
          rows="6"
          value={text}
          disabled={isSubmitting}
          onChange={({ target }) => {
            setText(target.value)
            setError('')
          }}
        />

        <div className="post-composer__counter" aria-live="polite">
          {text.length}/{MAX_POST_LENGTH}
        </div>

        {previewUrl && (
          <div className="post-composer__preview">
            <PostMedia src={previewUrl} type={image?.type.startsWith('video/') ? 'video' : 'image'}
              label="Vista previa del archivo seleccionado" />
            <button type="button" aria-label="Quitar archivo" disabled={isSubmitting} onClick={removeImage}>
              ×
            </button>
          </div>
        )}

        {error && <p className="form-message form-message--error" role="alert">{error}</p>}

        <footer className="post-composer__actions">
          <label className="post-composer__image-button" htmlFor="post-image">
            <span aria-hidden="true">▧</span>
            {image ? 'Cambiar archivo' : 'Agregar imagen o video'}
          </label>
          <input
            className="sr-only"
            id="post-image"
            ref={imageInputRef}
            type="file"
            accept="image/*,video/*"
            aria-label="Seleccionar imagen o video para la publicación"
            disabled={isSubmitting}
            onChange={selectImage}
          />
          <button
            className="primary-button"
            type="submit"
            disabled={isSubmitting || !text.trim()}
          >
            {isSubmitting && <span className="spinner" aria-hidden="true" />}
            {isSubmitting ? 'Publicando…' : 'Publicar'}
          </button>
        </footer>
      </form>
    </main>
  )
}

export default CreatePostPage
