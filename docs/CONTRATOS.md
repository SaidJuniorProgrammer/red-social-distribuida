# Contratos de API y Modelo de Datos

## 1. Modelo de Base de Datos (Neo4j)
Para mantener consistencia con los endpoints, Estalin deberá crear los nodos con estas propiedades exactas:

*   **Nodo `(:Usuario)`**: `id_usuario` (UUID), `username`, `email`, `password_hash`, `fecha_registro`.
*   **Nodo `(:Post)`**: `id_post`, `texto`, `media_url` (URL de S3), `fecha_publicacion`.
*   **Nodo `(:Notificacion)`**: `id_notificacion`, `tipo` (`FOLLOW` | `MENSAJE` | `POST` | `LIKE`), `mensaje`, `referencia` (ruta interna, p. ej. `/feed` o `/perfil/carlos`), `fecha` (ISO-8601), `leido` (boolean). Detalle completo en `docs/modelo-grafo.md`.
*   **Relaciones**: `[:SIGUE]`, `[:PUBLICA]`, `[:REACCIONA] {tipo: "LIKE"}`, `[:RECIBE]` (`(:Usuario)-[:RECIBE]->(:Notificacion)`, destinatario de la alerta), `[:ORIGINADA_POR]` (`(:Notificacion)-[:ORIGINADA_POR]->(:Usuario)`, usuario que provocó el evento).

> **Notas para Notificacion:** se listan ordenadas de la más reciente a la más antigua por `fecha` (LIMIT 50). Existe índice/constraint sobre `id_notificacion` para evitar duplicados.

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

---

## 3. Notificaciones y Web Push (Frontend <-> Backend)

Todas las rutas van bajo el prefijo `/api`. Salvo `vapid-public-key`, requieren el header `Authorization: Bearer <jwt>`; el backend deriva el usuario desde el token (el frontend **no** envía el username en el cuerpo de la suscripción).

### Notificaciones: Obtener llave pública VAPID
*   **Ruta:** `GET /api/push/vapid-public-key`
*   **React envía:** Nada.
*   **Quarkus responde (200 OK):**
    ```json
    { "publicKey": "BPZ...base64url" }
    ```

### Notificaciones: Registrar suscripción del navegador
*   **Ruta:** `POST /api/push/subscribe` (autenticada)
*   **React envía:**
    ```json
    {
      "endpoint": "https://fcm.googleapis.com/fcm/send/abc123",
      "keys": { "p256dh": "BOrr...", "auth": "k9Xy..." }
    }
    ```
*   **Quarkus responde (201 Created):** mensaje de confirmación. El usuario se toma del JWT.

### Notificaciones: Cancelar suscripción
*   **Ruta:** `DELETE /api/push/subscribe?endpoint=<endpoint>` (autenticada)
*   **React envía:** el `endpoint` como parámetro de consulta (sin cuerpo).
*   **Quarkus responde:** `200 OK` si se eliminó, `404 Not Found` si no existía (el frontend trata 404 como éxito silencioso).

### Notificaciones: Listar bandeja del usuario
*   **Ruta:** `GET /api/notificaciones` (autenticada; el usuario se toma del JWT, **no** va en la ruta)
*   **React envía:** Nada. El frontend hace *polling* cada 30 s.
*   **Quarkus responde (200 OK):**
    ```json
    {
      "notificaciones": [
        {
          "idNotificacion": "FOLLOW_ana_oscar",
          "tipo": "FOLLOW",
          "actor": "ana",
          "destinatario": "oscar",
          "mensaje": "Ana empezó a seguirte",
          "referencia": "/perfil/ana",
          "fecha": "2026-10-07T10:00:00Z",
          "leida": false
        }
      ]
    }
    ```
    > Los nombres de campo son **camelCase** (serialización por defecto de los `record` de Quarkus). El frontend acepta además los alias en snake_case por compatibilidad.

#### Campos que el frontend consume de cada notificación
| Campo | Necesidad | Comportamiento si falta |
| --- | --- | --- |
| `idNotificacion` | **Requerido** para persistir "leída" | Sin él, marcar como leída solo actúa en memoria. Alias: `id_notificacion`, `id`. |
| `tipo` | Recomendado (`FOLLOW`/`MENSAJE`/`POST`/`LIKE`) | Se infiere del texto de `mensaje` (frágil). No distingue mayúsculas. |
| `actor` | Recomendado | Alias: `autor`, `usuario_origen`. Sin actor, seguimiento→`/explorar`, mensaje→`/chat`. |
| `mensaje` | Requerido | Se muestra como cuerpo de la notificación en la bandeja. |
| `referencia` | Opcional (ruta **interna**: debe empezar con `/` y no `//`) | Se calcula por tipo: seguimiento→`/perfil/{actor}`, mensaje→`/chat?usuario={actor}`, like/post→`/feed`. El backend envía `/mensajes` para avisos de chat y el frontend lo traduce a `/chat?usuario={actor}`. Alias: `url_interna`, `url`. |
| `fecha` | Requerido (ISO-8601) | El frontend ordena desc. Alias: `timestamp`. |
| `leida` | **Requerido** para el contador de no leídas | Sin él, todo cuenta como no leído. Alias: `leido`, `read`. |

### Notificaciones: Marcar una como leída
*   **Ruta:** `PUT /api/notificaciones/{idNotificacion}/leer` (autenticada)
*   **React envía:** Nada (el `id` va en la ruta).
*   **Quarkus responde:** `200 OK` `{ "status": "success" }`, o `404 Not Found` si no existe para ese usuario.

### Notificaciones: endpoints adicionales del backend
El frontend calcula el total de no leídas a partir de la lista, por lo que estos endpoints existen pero el frontend actual aún no los consume:
*   `GET /api/notificaciones/no-leidas` → `{ "no_leidas": 3 }`.
*   `PUT /api/notificaciones/leer-todas` → `{ "status": "success", "actualizadas": 3 }`.

### Web Push: Payload del mensaje enviado al navegador
Es el JSON que el backend envía al servicio Push y que recibe el *service worker* (`frontend/public/service-worker.js`):
```json
{
  "idNotificacion": "POST_ana_post-9",
  "tipo": "POST",
  "actor": "ana",
  "destinatario": "oscar",
  "titulo": "Nueva publicación de @ana",
  "mensaje": "Ana compartió algo nuevo",
  "referencia": "/feed",
  "fecha": "2026-10-07T10:00:00Z"
}
```
*   `titulo` → título de la notificación del sistema (si falta: "Nueva actividad en Pachyweb").
*   `mensaje` → cuerpo (si falta: texto por defecto).
*   `referencia` → destino interno al hacer clic; el *service worker* solo navega si el origen coincide con el de la app (ignora URLs externas).
*   `idNotificacion` → agrupa la alerta (`tag`) para no apilar duplicados en pantalla.