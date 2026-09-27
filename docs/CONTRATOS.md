# Contratos de API y Modelo de Datos

## 1. Modelo de Base de Datos (Neo4j)
Para mantener consistencia con los endpoints, Estalin deberá crear los nodos con estas propiedades exactas:

*   **Nodo `(:Usuario)`**: `id_usuario` (UUID), `username`, `email`, `password_hash`, `fecha_registro`.
*   **Nodo `(:Post)`**: `id_post`, `texto`, `media_url` (URL de S3), `fecha_publicacion`.
*   **Relaciones**: `[:SIGUE]`, `[:PUBLICA]`, `[:REACCIONA] {tipo: "LIKE"}`.

---

## 2. Contratos REST (Frontend <-> Backend)

### Auth: Registro de Usuario
*   **Ruta:** `POST /api/auth/register`
*   **React envía:**
    ```json
    {
      "username": "oscar_dev",
      "email": "oscar@email.com",
      "password": "secreta123"
    }
    ```
*   **Quarkus responde (201 Created):**
    ```json
    {
      "status": "success",
      "id_usuario": "uuid-1234",
      "token": "jwt.header.payload.signature"
    }
    ```

### Publicaciones: Crear un Post con Imagen
*   **Ruta:** `POST /api/posts`
*   **React envía (FormData):**
    *   `texto`: "Mi primer post distribuido"
    *   `archivo`: [Fichero Binario JPG/PNG]
*   **Quarkus responde (201 Created):**
    ```json
    {
      "status": "success",
      "post": {
        "id_post": "uuid-9876",
        "texto": "Mi primer post distribuido",
        "media_url": "http://localhost:9000/red-social-media/img-uuid.jpg",
        "fecha_publicacion": "2026-09-27T10:00:00Z"
      }
    }
    ```

### Grafo: Seguir a un Usuario
*   **Ruta:** `POST /api/usuarios/{mi_id}/seguir/{id_destino}`
*   **React envía:** Nada (vacío).
*   **Quarkus responde (200 OK):**
    ```json
    {
      "status": "success",
      "mensaje": "Relación [:SIGUE] creada exitosamente"
    }
    ```

### Grafo: Obtener Feed Personalizado
*   **Ruta:** `GET /api/feed/{mi_id}`
*   **React envía:** Nada.
*   **Quarkus responde (200 OK):**
    ```json
    {
      "feed": [
        {
          "id_post": "uuid-9876",
          "autor": "oscar_dev",
          "texto": "Mi primer post distribuido",
          "media_url": "http://localhost:9000/...",
          "reacciones": 15
        }
      ]
    }
    ```

### Tiempo Real: Chat (WebSocket)
*   **Conexión:** `ws://localhost:8080/chat/{mi_id}`
*   **Mensaje enviado/recibido (JSON Stringificado):**
    ```json
    {
      "emisor_id": "uuid-1234",
      "destinatario_id": "uuid-5555",
      "contenido": "¡Hola Estalin, ya revisé las consultas Cypher!",
      "timestamp": "2026-09-27T11:24:00Z"
    }
    ```