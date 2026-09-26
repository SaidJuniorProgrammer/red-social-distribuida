# Red Social Distribuida 

Proyecto práctico de diseño e implementación de una aplicación web distribuida que integra múltiples mecanismos de comunicación, persistencia y almacenamiento.

## Integrantes
- Said Pinto
- Oscar Gonzabay
- Jaen Estalin

## Descripción del Proyecto
Aplicación web distribuida basada en microservicios y grafos que implementa las funcionalidades esenciales de una red social[cite: 2]. El sistema separa estrictamente las responsabilidades, utilizando bases de datos orientadas a grafos para las relaciones sociales y almacenamiento de objetos para la multimedia[cite: 1, 2].

## Arquitectura
*(Aquí insertaremos el diagrama de arquitectura definitivo mostrando cómo se comunican React, Quarkus, Neo4j y S3)*[cite: 1].

## Tecnologías Utilizadas
| Componente | Tecnología |
|---|---|
| **Frontend** | React[cite: 1, 2] |
| **Backend** | Quarkus + Java[cite: 1, 2] |
| **Base de Datos** | Neo4j (Grafos)[cite: 1, 2] |
| **Almacenamiento** | MinIO (Servicio compatible con S3)[cite: 1, 2] |
| **Tiempo Real** | WebSocket[cite: 1, 2] |
| **Notificaciones** | Web Push[cite: 1, 2] |
| **Despliegue** | Docker & Docker Compose[cite: 1, 2] |

## Instrucciones de Ejecución
1. Clonar el repositorio.
2. Levantar la infraestructura base ejecutando: `docker-compose up -d`[cite: 1].
3. *(Añadir los comandos para levantar Quarkus y React cuando estén configurados)*.

### Variables de Entorno Necesarias
- `NEO4J_URI`=bolt://localhost:7687
- `NEO4J_USER`=neo4j
- `NEO4J_PASSWORD`=...
- `S3_ENDPOINT`=http://localhost:9000
- `S3_ACCESS_KEY`=...
- `S3_SECRET_KEY`=...

## Modelo del Grafo
*(Aquí documentaremos los nodos como `(:Usuario)`, `(:Post)` y sus relaciones como `[:SIGUE]`, `[:PUBLICA]`, `[:REACCIONA]`)*[cite: 1, 2].

## Endpoints Principales (API REST)
- `POST /usuarios` - Registro de usuario[cite: 1].
- `GET /feed` - Obtener el feed personalizado del usuario[cite: 1].
*(Se completará conforme se desarrolle el backend)*

## Mecanismos de Comunicación Justificados
- **Uso de REST:** Explicación de por qué se usa para el CRUD convencional[cite: 1].
- **Uso de WebSocket:** Explicación de la conexión persistente bidireccional para el chat en tiempo real (diferencia con el polling tradicional)[cite: 1, 2].
- **Uso de Web Push:** Explicación del flujo de notificaciones fuera de la aplicación cuando un usuario seguido interactúa[cite: 1, 2].

## Consultas Cypher Desarrolladas
*(Aquí listaremos las 5 consultas Cypher no triviales, incluyendo el algoritmo de recomendación de usuarios que recorre más de 1 nivel)*[cite: 1, 2].

1. Consulta 1: ...
2. Consulta 2: ...
3. Consulta 3: ...
4. Consulta 4: ...
5. Consulta 5: ...

## Decisiones Técnicas Relevantes
*(Espacio para justificar por qué se separaron los archivos en S3, por qué no se usó una base relacional, etc.)*[cite: 1].
