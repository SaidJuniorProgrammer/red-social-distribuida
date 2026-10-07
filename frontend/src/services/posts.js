import api from './api.js'

export async function createPost({ image, text }) {
  const formData = new FormData()
  formData.append('texto', text.trim())
  if (image) formData.append('archivo', image)

  const { data } = await api.post('/posts', formData, {
    headers: { 'Content-Type': undefined },
  })
  return data
}
