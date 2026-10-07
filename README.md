# Red Social Distribuida (Pachyweb)

Proyecto integral de diseño e implementación de una aplicación web distribuida que combina distintos mecanismos de comunicación, persistencia orientada a grafos y almacenamiento de objetos.

## Equipo de Desarrollo
- **Said Pinto:** Arquitectura backend, integración continua (CI/CD), modelado de grafos y lógica de negocio.
- **Oscar Gonzabay:** Arquitectura frontend, interfaces con React, consumo de APIs y gestión de estado.
- **Jaen Estalin:** Consultas Cypher, semillas de base de datos y pruebas unitarias.

## Descripción del Proyecto
La aplicación implementa funcionalidades esenciales de una red social. El frontend React consume un backend Quarkus; Neo4j gestiona los datos y relaciones sociales, mientras que el almacenamiento compatible con S3 conserva la multimedia. En el entorno local se usa Adobe S3Mock. REST atiende las operaciones CRUD, WebSocket habilita el chat en tiempo real y Web Push permite enviar notificaciones mediante Service Workers.

## Arquitectura
```mermaid
flowchart TD
    React["React + Vite (Frontend)"]
    Quarkus["Quarkus + Java 17 (Backend)"]
    Neo4j[("Neo4j (Base de datos de grafos)")]
    S3[("Almacenamiento S3-compatible (Adobe S3Mock local)")]
    Usuario(("Usuario"))

    React <-->|REST / HTTP (JSON)| Quarkus
    React <-->|WebSocket| Quarkus
    Quarkus <-->|Bolt / Cypher| Neo4j
    Quarkus -->|API compatible con S3| S3
    Quarkus -.->|Web Push / Service Worker| Usuario
```

## Tecnologías Utilizadas
| Componente | Tecnología | Propósito |
|---|---|---|
| **Frontend** | React, Vite, Tailwind CSS | Interfaz reactiva y diseño adaptativo. |
| **Backend** | Quarkus, Java 17 | API y lógica de negocio. |
| **Base de datos** | Neo4j | Persistencia de usuarios, publicaciones y relaciones sociales. |
| **Almacenamiento** | Adobe S3Mock (local), compatible con S3 | Almacenamiento de imágenes y otros archivos multimedia. |
| **Comunicación** | REST, WebSocket, Web Push | Operaciones CRUD, chat en tiempo real y notificaciones asíncronas. |
| **DevOps y CI/CD** | Docker, GitHub Actions, SonarQube Cloud | Entorno reproducible, pruebas automatizadas y análisis de calidad. |

## Instrucciones de Ejecución
1. Clonar el repositorio y entrar en su carpeta raíz.
2. Iniciar Neo4j y Adobe S3Mock con Docker Compose:
   ```bash
   docker compose up -d
   ```
   El archivo `docker-compose.yml` publica Neo4j en `localhost:7687` (Bolt) y `localhost:7474` (Browser), y S3Mock en `localhost:9090`. El bucket `red-social-media` se crea al iniciar el contenedor.
3. En una terminal, iniciar el backend Quarkus:
   ```powershell
   cd backend
   .\mvnw.cmd quarkus:dev
   ```
   En macOS/Linux, usar `./mvnw quarkus:dev`.
4. En otra terminal, instalar las dependencias e iniciar el frontend:
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
   Vite estará disponible normalmente en `http://localhost:5173`.

### Validación del Proyecto
Los comandos locales que utiliza el workflow de GitHub Actions son:
* **Frontend** (desde `frontend/`): `npm run lint`, `npm run test:coverage` (Vitest) y `npm run build`. La cobertura LCOV se genera en `frontend/coverage/lcov.info`.
* **Backend** (desde `backend/`): `.\mvnw.cmd clean verify` en Windows o `./mvnw clean verify` en macOS/Linux. Maven ejecuta las pruebas y genera el reporte JaCoCo.

El workflow [`.github/workflows/sonar.yml`](.github/workflows/sonar.yml) ejecuta esas validaciones en los eventos y ramas configurados, y después envía el análisis a SonarQube Cloud. La posibilidad de bloquear una fusión depende también de las reglas de protección de ramas de GitHub; el workflow por sí solo no demuestra que exista un umbral de cobertura obligatorio.

### Configuración y Variables de Entorno
La configuración local de Neo4j y S3Mock está definida en [`backend/src/main/resources/application.properties`](backend/src/main/resources/application.properties) y [`docker-compose.yml`](docker-compose.yml). El Compose de desarrollo configura Neo4j con el usuario `neo4j` y la contraseña `password123`; S3Mock escucha en el puerto `9090` y la configuración local del SDK usa credenciales de prueba. **No reutilices esas credenciales en producción.**

Variables de entorno que la aplicación sí admite:

| Variable | Uso |
|---|---|
| `VITE_API_BASE_URL` | URL base de la API REST. Predeterminado: `http://localhost:8080/api`. |
| `VITE_WEBSOCKET_BASE_URL` | URL base del chat por WebSocket. Si se omite, se deriva de `VITE_API_BASE_URL` o se usa `ws://localhost:8080`. |
| `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` | Par de claves estable para Web Push. Si se omiten, el backend genera claves temporales al arrancar; en producción deben configurarse para conservar las suscripciones entre reinicios. |
| `VAPID_SUBJECT` | Identidad VAPID. Predeterminado: `mailto:admin@pachyweb.local`. |
| `WEBPUSH_ALLOWED_HOSTS` | Hosts de servicios Push permitidos por el validador. |
| `WEBPUSH_DELIVERY_TIMEOUT_SECONDS` | Tiempo límite de entrega Web Push. Predeterminado: `10`. |

Los endpoints de SonarQube requieren que `SONAR_TOKEN` esté configurado como secreto del repositorio de GitHub Actions.

## Modelo del Grafo

La documentación completa (propiedades, restricciones y consultas) está en [`docs/modelo-grafo.md`](docs/modelo-grafo.md). Resumen:

**Nodos**
- `(:Usuario)` — `id_usuario`, `username`, `email`, `password_hash`, `nombre`, `bio`, `avatar_url`, `fecha_registro`.
- `(:Post)` — `id_post`, `texto`, `media_url` (referencia al objeto S3-compatible), `media_tipo`, `fecha_publicacion`.
- `(:Mensaje)` — mensajes persistidos por el chat activo. El nodo `(:Conversacion)` forma parte del esquema y las semillas, pero el WebSocket actual no lo utiliza.

**Relaciones**
```text
(:Usuario)-[:SIGUE {desde}]->(:Usuario)                 // grafo social (dirigida)
(:Usuario)-[:PUBLICA]->(:Post)                          // autoría
(:Usuario)-[:REACCIONA {tipo_reaccion, fecha}]->(:Post) // reacciones (LIKE)
(:Usuario)-[:ENVIA]->(:Mensaje)-[:DIRIGIDO_A]->(:Usuario) // chat privado implementado
```

**Decisiones clave**
- `SIGUE` es **dirigida**: la amistad mutua se representa mediante dos relaciones en sentidos opuestos.
- El **tipo de reacción va en la relación** (`REACCIONA {tipo_reaccion}`), no en un nodo aparte.
- La multimedia **no se guarda en Neo4j**: el nodo `Post` conserva `media_url` como referencia al objeto en almacenamiento S3-compatible.
- El esquema y las semillas también incluyen `Conversacion`, `PARTICIPA` y `EN` como modelo de conversación. Sin embargo, el servicio de chat activo persiste mensajes mediante `ENVIA` y `DIRIGIDO_A` directamente entre usuarios; el endpoint WebSocket no usa actualmente nodos `Conversacion`.

## Endpoints Principales (API REST)
Todas las rutas se sirven bajo `http://localhost:8080`. Las rutas marcadas **requieren autenticación** aceptan `Authorization: Bearer <token>`.

| Método y ruta | Acceso | Descripción |
|---|---|---|
| `GET /api/health` | Público | Verificación de disponibilidad del backend. |
| `POST /api/auth/register` | Público | Registra una cuenta; responde `201`, `400` si los datos no son válidos o `409` si username/email ya existen. |
| `POST /api/auth/login` | Público | Inicia sesión y devuelve `{ "token": "..." }`; credenciales inválidas responden `401`. |
| `GET /api/usuarios?query={termino}` | Autenticado | Busca usuarios. |
| `GET /api/feed/{mi_id}` | Público | Valida el usuario indicado y obtiene las 20 publicaciones recientes de todos los autores; no limita el resultado a las cuentas seguidas. |
| `GET /api/usuarios/{mi_id}/posts` | Público | Obtiene las publicaciones del perfil. |
| `GET /api/usuarios/{mi_id}/seguidores` | Público | Lista los seguidores. |
| `GET /api/usuarios/{mi_id}/seguidos` | Público | Lista las cuentas seguidas. |
| `POST /api/usuarios/{mi_id}/seguir/{id_destino}` | Autenticado | Sigue a otro usuario; el usuario de la ruta debe coincidir con la identidad del token. |
| `DELETE /api/usuarios/{mi_id}/seguir/{id_destino}` | Autenticado | Deja de seguir a un usuario. |
| `GET /api/usuarios/{mi_id}/sugerencias` | Público | Recomienda cuentas según conexiones en común. |
| `POST /api/posts` | Autenticado | Crea una publicación mediante `multipart/form-data`. |
| `POST /api/posts/{id_post}/like/{mi_id}` | Autenticado | Registra un LIKE; la cuenta debe coincidir con el token. |
| `DELETE /api/posts/{id_post}/like/{mi_id}` | Autenticado | Elimina el LIKE de la cuenta autenticada. |
| `GET /api/push/vapid-public-key` | Público | Obtiene la clave pública requerida para suscribirse a Web Push. |
| `POST /api/push/subscribe` | Autenticado | Registra la suscripción Push del navegador autenticado. |
| `DELETE /api/push/subscribe?endpoint={endpoint}` | Autenticado | Elimina una suscripción del usuario autenticado. |
| `GET /api/push/notificaciones/{usuario}` | Autenticado | Lee las notificaciones del propio usuario; no permite consultar otra cuenta. |

Para crear publicaciones, el formulario multipart usa el campo obligatorio `texto` (máximo 500 caracteres) y el campo opcional `archivo`. Se admiten imágenes o videos de hasta 10 MB. La respuesta exitosa es `201` e incluye `id_post`, `autor`, `texto`, `media_url`, `media_tipo` y `fecha_publicacion`.

Los errores REST usan una respuesta JSON con `code`, `field` y `message`; `field` puede ser nulo cuando el error no corresponde a un campo concreto. Las rutas de perfil y feed no requieren autenticación en la configuración actual, mientras que las mutaciones y la búsqueda sí aplican controles de acceso.

## Autenticación y Autorización

- El registro valida username, email y una contraseña de al menos 8 caracteres. La contraseña se almacena como hash BCrypt; nunca se guarda en texto plano.
- El inicio de sesión devuelve un JWT firmado cuya identidad es el username y cuya vigencia actual es de 10 minutos.
- El frontend adjunta el JWT como `Bearer` en las llamadas REST. Si recibe un `401`, limpia la sesión local.
- Las operaciones de seguir/dejar de seguir, publicar, dar/quitar LIKE, administrar suscripciones Push y acceder a la búsqueda requieren identidad autenticada. En acciones sobre una cuenta, el backend comprueba que el nombre de usuario de la ruta corresponda al principal del JWT.
- `application.properties` fija el issuer `https://redsocial.com/issuer`, pero la configuración versionada no especifica ubicaciones para las claves JWT. Configura una clave privada de firma y una clave pública de verificación compatibles con SmallRye JWT antes de desplegar; mantén el material criptográfico y los secretos fuera del repositorio.

## Chat en Tiempo Real

El endpoint WebSocket del backend es `ws://localhost:8080/chat` (usar `wss://` detrás de HTTPS). Requiere un JWT y negocia el subprotocolo `bearer-token-carrier`; el cliente transmite el token durante el handshake, no como campo del mensaje.

Al conectar, el servidor envía el historial como un objeto `{ "type": "history", "messages": [...] }`. Para enviar un mensaje, el cliente manda JSON con `destinatario_id` y `contenido`; el servidor deriva `emisor_id` del JWT y asigna el identificador y la marca de tiempo. El mensaje se guarda en Neo4j y, si el destinatario está conectado, se entrega por WebSocket. Si no está conectado, queda disponible en el historial para la siguiente conexión. Los errores de entrega se comunican como eventos `delivery_error`.

## Mecanismos de Comunicación Justificados
- **REST / HTTP:** Utilizado para operaciones transaccionales y CRUD estándar (autenticación, consultas de perfiles, subida de publicaciones y registro de likes) por su naturaleza sin estado y amplia compatibilidad.
- **WebSocket:** Mantiene el canal bidireccional del chat y evita consultas repetidas para comprobar si hay mensajes.
- **Web Push:** El frontend registra `public/service-worker.js` y suscribe el navegador con la clave pública VAPID. El backend envía avisos de nuevas publicaciones a los seguidores y avisos de LIKE al autor. Con el payload actual, el clic en la notificación abre `/feed`; la recepción depende de los permisos del usuario y del soporte del navegador.

Las suscripciones Push se guardan en Neo4j para restaurarlas al iniciar el backend. En cambio, la bandeja consultada por `GET /api/push/notificaciones/{usuario}` es actualmente una estructura en memoria del proceso: esas notificaciones no deben considerarse un historial durable y se pierden al reiniciar el backend.

## Consultas Cypher Desarrolladas (5 principales)

Los siguientes fragmentos resumen patrones Cypher del proyecto. El catálogo de referencia está en `backend/src/main/resources/neo4j/03-consultas.cypher`; las consultas que atienden cada endpoint están en los repositorios del backend y pueden diferir del modelo conceptual.

**1. Feed público con agregación de Likes:**
La implementación actual de `GET /api/feed/{mi_id}` comprueba que el usuario indicado exista y devuelve las 20 publicaciones más recientes de todos los autores, con el conteo de reacciones y un indicador de LIKE para el lector. No filtra el feed a las cuentas seguidas.
```cypher
MATCH (yo:Usuario)
WHERE yo.id_usuario = $miId OR yo.username = $miId OR yo.id = $miId
WITH count(yo) AS usuariosSolicitantes
WHERE usuariosSolicitantes > 0
MATCH (autor:Usuario)-[:PUBLICA]->(p:Post)
WITH DISTINCT autor, p
OPTIONAL MATCH (p)<-[r:REACCIONA]-()
WITH p, autor, count(r) AS reacciones
RETURN p.id_post AS id_post,
       autor.username AS autor,
       p.texto AS texto,
       p.media_url AS media_url,
       toString(p.fecha_publicacion) AS fecha_publicacion,
       p.media_tipo AS media_tipo,
       reacciones,
       EXISTS {
           MATCH (:Usuario {username: $lector})-[:REACCIONA {tipo_reaccion: 'LIKE'}]->(p)
       } AS liked
ORDER BY p.fecha_publicacion DESC LIMIT 20
```

**2. Sugerencias de amistad (Amigos en común):**
Explora el grafo a 2 niveles de profundidad para recomendar usuarios que mis seguidos siguen, ordenados por conexiones compartidas.
```cypher
MATCH (yo:Usuario)-[:SIGUE]->(intermedio:Usuario)-[:SIGUE]->(sugerido:Usuario)
WHERE yo.username = $miId AND sugerido <> yo AND NOT (yo)-[:SIGUE]->(sugerido)
RETURN sugerido.username AS recomendado, count(DISTINCT intermedio) AS conexiones_en_comun
ORDER BY conexiones_en_comun DESC, recomendado LIMIT 5
```

**3. Idempotencia en relaciones (Seguir Usuario):**
Utiliza `MERGE` en lugar de `CREATE` para evitar relaciones duplicadas si el usuario hace clic múltiples veces en "Seguir".
```cypher
MATCH (a:Usuario), (b:Usuario)
WHERE a.username = $miId AND b.username = $idDestino AND a <> b
MERGE (a)-[r:SIGUE]->(b)
ON CREATE SET r.desde = datetime()
RETURN a.username, b.username
```

**4. Dar Like a una publicación:**
Genera una relación tipada hacia un Post específico e incluye un registro de fecha y hora, asegurándose de no duplicar la reacción si el usuario vuelve a enviar la petición.
```cypher
MATCH (u:Usuario), (p:Post {id_post: $idPost})
WHERE u.username = $miId
WITH u, p LIMIT 1
MERGE (u)-[r:REACCIONA {tipo_reaccion: 'LIKE'}]->(p)
ON CREATE SET r.fecha = datetime()
```

**5. Historial de Perfil y Listados de Conexiones:**
Manejo de grafos dirigidos para recuperar exclusivamente listas de "Seguidores" evaluando la dirección estricta de la relación.
```cypher
MATCH (seguidor:Usuario)-[:SIGUE]->(yo:Usuario)
WHERE yo.username = $miId
RETURN DISTINCT seguidor.username ORDER BY seguidor.username
```

## Decisiones Técnicas Relevantes

1. **Neo4j como motor principal:** En una red social, muchas consultas dependen de las conexiones entre usuarios (seguidores, reacciones y conexiones en común). El modelo de grafos permite recorrer esas relaciones directamente y expresar consultas de varios saltos, como las sugerencias de usuarios, de forma natural.
2. **Desacoplamiento de Multimedia:** Neo4j no está diseñado para almacenar archivos binarios (BLOBs). Guardar imágenes directamente en la base de datos inflaría los respaldos y destruiría el rendimiento. El backend maneja el archivo, obtiene una URL, y Neo4j únicamente almacena esa cadena de texto ligera (`media_url`).
3. **Adobe S3Mock compatible con S3 para desarrollo:** Ejecutar S3Mock en Docker permite probar la integración con almacenamiento de objetos sin depender de una cuenta activa de AWS. Se utiliza la API compatible con S3 mediante el SDK de AWS (`software.amazon.awssdk.services.s3`); en este repositorio el servicio local se publica en el puerto `9090`.
4. **Quarkus para el backend:** Se eligió Quarkus por su enfoque *Container First* y su adecuación a despliegues en contenedores. Sus tiempos de arranque y consumo de recursos pueden resultar favorables para este tipo de servicios.
5. **Web Push para notificaciones asíncronas:** Las notificaciones se gestionan con VAPID y Service Workers, en lugar de depender de consultas periódicas (*polling*). Así, el navegador puede recibir notificaciones aunque la página no esté activa, sujeto a los permisos y soporte del navegador.

## Etapas de Desarrollo

El desarrollo se organizó en fases iterativas:

1. **Cimientos y DevOps:** configuración de Docker para Neo4j y Adobe S3Mock, estructura inicial de Quarkus y React, y automatización de validaciones de CI/CD.
2. **Identidad y grafos:** diseño del modelo de Neo4j, registro e inicio de sesión, y creación de usuarios y relaciones `SIGUE`.
3. **Publicaciones:** carga de multimedia en almacenamiento S3 compatible, creación de publicaciones y desarrollo del feed de publicaciones recientes.
4. **Interactividad y tiempo real:** reacciones, chat con WebSocket e integración de notificaciones Web Push.
5. **Perfiles y calidad:** listados y métricas de perfil, manejo de errores, pruebas y refinamiento de la interfaz.

## Explicación de la Estructura del Proyecto

La estructura de carpetas separa las responsabilidades de cada tecnología.

### Backend (`/backend`)
Sigue el estándar de Maven (`src/main/java` y `src/main/resources`) y organiza la lógica en capas:

* **`config/`**: configuración de seguridad, credenciales de S3 y conexión a Neo4j.
* **`controller/`**: endpoints de la API REST.
* **`websocket/`**: conexión persistente del chat en tiempo real.
* **`model/` y `dto/`**: entidades del grafo y objetos de transferencia de datos.
* **`repository/`**: consultas Cypher para comunicarse con Neo4j.
* **`service/`**: lógica de negocio, integración con S3 y generación de claves VAPID para Web Push.

### Frontend (`/frontend`)
En React, la interfaz está separada de la comunicación con el servidor:

* **`components/` y `pages/`**: vistas modulares para el feed, el perfil y el chat.
* **`services/`**: comunicación con el backend:
  * `api.js`: llamadas REST.
  * `websocket.js`: conexión para el chat.
  * `webpush.js`: registro de notificaciones.
* **`context/`**: estado global, como la autenticación, compartido entre componentes.
* **`public/service-worker.js`**: recepción de notificaciones Web Push en el navegador.

### Infraestructura Base (`docker-compose.yml`)
Permite iniciar Neo4j y Adobe S3Mock localmente con un solo comando y facilita mantener un entorno de desarrollo reproducible.

### Calidad de Código e Integración Continua (CI/CD)
* **GitHub Actions (`.github/workflows/sonar.yml`)**: ejecuta lint, pruebas y build del frontend, además de `mvn clean verify` para el backend; posteriormente inicia el análisis de SonarQube Cloud en las ramas y eventos configurados en el workflow.
* **SonarQube Cloud**: recibe los reportes de cobertura LCOV del frontend y JaCoCo del backend, y analiza el código fuente. No se documenta aquí un umbral fijo de cobertura porque no aparece configurado en el workflow o en `sonar-project.properties`.
* **CodeRabbit AI**: no forma parte del workflow de CI/CD incluido en este repositorio; si se habilita como integración externa, sus revisiones son complementarias a estas validaciones automatizadas.

### Pruebas

Las pruebas existentes cubren los flujos principales en ambos lados de la aplicación:

| Área | Cobertura funcional representativa |
|---|---|
| **Frontend (Vitest)** | Validación y sesión de autenticación, API, feed, perfiles, exploración, publicaciones, chat, Web Push y Service Worker; ubicadas en `frontend/tests/`. |
| **Backend (JUnit / Quarkus)** | Registro e inicio de sesión, grafo social, publicaciones, reacciones, chat, Web Push, repositorios y endpoint de salud; ubicadas en `backend/src/test/java/`. |

Los nombres de las suites muestran las áreas probadas, pero no implican que cada caso límite esté cubierto. Para comprobar el estado actual, ejecuta los comandos de la sección [Validación del Proyecto](#validación-del-proyecto) y consulta los reportes generados.