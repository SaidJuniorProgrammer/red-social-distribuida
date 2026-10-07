function PostMedia({ src, type, label, className }) {
  if (type === 'video') {
    return <video className={className} src={src} controls muted preload="metadata" aria-label={label}>
      Tu navegador no admite este video.
    </video>
  }
  return <img className={className} src={src} alt={label} loading="lazy" />
}

export default PostMedia
