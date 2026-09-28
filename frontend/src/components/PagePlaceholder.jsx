function PagePlaceholder({ description, title }) {
  return (
    <main className="page-placeholder">
      <section className="page-placeholder__content">
        <p className="page-placeholder__eyebrow">Red Social Distribuida</p>
        <h1>{title}</h1>
        <p>{description}</p>
      </section>
    </main>
  )
}

export default PagePlaceholder
