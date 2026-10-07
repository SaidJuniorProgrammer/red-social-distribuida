// ============================================================
// 03-consultas.cypher — Consultas Cypher no triviales (5+) + escritura del grafo
// Consultas de LECTURA (1-7) y de ESCRITURA del grafo social (8 seguir, 9 dejar de seguir).
// La actividad exige mínimo 5 no triviales, y al menos una que recorra
// relaciones de MÁS de un nivel (la #4 y #6 lo hacen).
// Convención: snake_case, alineado con docs/CONTRATOS.md
// Reemplaza $miId / $idDestino / $otroId por ids reales (ej. 'u1') al probar.
// ============================================================

// ------------------------------------------------------------
// 1) SEGUIDORES de un usuario
//    (quién apunta hacia mí con SIGUE)
// ------------------------------------------------------------
MATCH (seguidor:Usuario)-[:SIGUE]->(yo:Usuario {id_usuario: $miId})
RETURN seguidor.username AS seguidor
ORDER BY seguidor;

// ------------------------------------------------------------
// 2) USUARIOS SEGUIDOS
//    (a quién sigo yo)
// ------------------------------------------------------------
MATCH (yo:Usuario {id_usuario: $miId})-[:SIGUE]->(seguido:Usuario)
RETURN seguido.username AS seguido
ORDER BY seguido;

// ------------------------------------------------------------
// 3) FEED PERSONALIZADO
//    Posts SOLO de los usuarios que sigo, ordenados por fecha.
//    (no es "todos los posts del sistema")
// ------------------------------------------------------------
// Devuelve los campos del contrato GET /api/feed (id_post, autor, texto, media_url).
// Se agrupa por p.id_post (único) para que cada publicación sea UNA fila y las
// reacciones no se mezclen entre posts con mismo texto/fecha.
MATCH (yo:Usuario {id_usuario: $miId})-[:SIGUE]->(autor:Usuario)-[:PUBLICA]->(p:Post)
OPTIONAL MATCH (p)<-[r:REACCIONA]-()
RETURN p.id_post            AS id_post,
       autor.username       AS autor,
       p.texto              AS texto,
       p.media_url          AS media_url,
       p.fecha_publicacion  AS fecha_publicacion,
       count(r)             AS reacciones
ORDER BY p.fecha_publicacion DESC
LIMIT 20;

// ------------------------------------------------------------
// 4) RECOMENDACIÓN DE USUARIOS  (recorre 2 niveles del grafo)
//    "A quién seguir": personas seguidas por la gente que yo sigo,
//    que aún no sigo. Se ordenan por nº de conexiones en común.
// ------------------------------------------------------------
MATCH (yo:Usuario {id_usuario: $miId})-[:SIGUE]->(intermedio:Usuario)-[:SIGUE]->(sugerido:Usuario)
WHERE sugerido <> yo AND NOT (yo)-[:SIGUE]->(sugerido)
RETURN sugerido.username           AS recomendado,
       count(DISTINCT intermedio)  AS conexiones_en_comun
ORDER BY conexiones_en_comun DESC, recomendado
LIMIT 5;

// ------------------------------------------------------------
// 5) SEGUIDOS EN COMÚN entre dos usuarios
//    (recorre relaciones desde dos puntos y las cruza)
// ------------------------------------------------------------
MATCH (a:Usuario {id_usuario: $miId})-[:SIGUE]->(comun:Usuario)<-[:SIGUE]-(b:Usuario {id_usuario: $otroId})
RETURN comun.username AS seguido_en_comun;

// ------------------------------------------------------------
// 6) (EXTRA) ALCANCE DE LA RED: usuarios alcanzables por SIGUE
//    a 1..3 saltos. Camino variable = imposible de forma natural en SQL.
// ------------------------------------------------------------
MATCH (yo:Usuario {id_usuario: $miId})-[:SIGUE*1..3]->(alcanzado:Usuario)
WHERE alcanzado <> yo
RETURN DISTINCT alcanzado.username AS en_mi_red;

// ------------------------------------------------------------
// 7) (EXTRA) MUTUALIDAD: ¿quién me sigue de vuelta?
// ------------------------------------------------------------
MATCH (yo:Usuario {id_usuario: $miId})-[:SIGUE]->(otro:Usuario)
WHERE (otro)-[:SIGUE]->(yo)
RETURN otro.username AS se_siguen_mutuamente;

// ============================================================
// ESCRITURA DEL GRAFO SOCIAL
// ============================================================

// ------------------------------------------------------------
// 8) SEGUIR a un usuario
//    MERGE evita relaciones SIGUE duplicadas (idempotente).
//    ON CREATE guarda la fecha solo la primera vez.
//    El WHERE impide que alguien se siga a sí mismo.
// ------------------------------------------------------------
MATCH (a:Usuario {id_usuario: $miId}), (b:Usuario {id_usuario: $idDestino})
WHERE a <> b
MERGE (a)-[r:SIGUE]->(b)
  ON CREATE SET r.desde = datetime()
RETURN a.username AS sigo_ahora_a, b.username AS seguido;

// ------------------------------------------------------------
// 9) DEJAR DE SEGUIR a un usuario
//    Borra únicamente la relación SIGUE, no los nodos.
// ------------------------------------------------------------
MATCH (a:Usuario {id_usuario: $miId})-[r:SIGUE]->(b:Usuario {id_usuario: $idDestino})
DELETE r
RETURN a.username AS dejo_de_seguir_a, b.username AS ex_seguido;

// ============================================================
// REACCIONES (issue #30) — crear/eliminar [:REACCIONA {tipo_reaccion}]
// Se usa tipo_reaccion, igual que el modelo y el seed (02-seed.cypher).
// ============================================================

// ------------------------------------------------------------
// 10) DAR LIKE a una publicación
//     MERGE asegura UNA sola relacion REACCIONA por usuario-post.
//     ON CREATE fija la fecha la primera vez; el SET siempre deja
//     tipo_reaccion = 'LIKE' (convierte una reaccion previa, p.ej. LOVE).
// ------------------------------------------------------------
MATCH (u:Usuario {id_usuario: $miId}), (p:Post {id_post: $idPost})
MERGE (u)-[r:REACCIONA]->(p)
  ON CREATE SET r.fecha = datetime()
SET r.tipo_reaccion = 'LIKE'
RETURN u.username AS usuario, p.id_post AS post, r.tipo_reaccion AS reaccion;

// ------------------------------------------------------------
// 11) QUITAR EL LIKE de una publicación
//     Borra unicamente la relacion REACCIONA, no los nodos.
// ------------------------------------------------------------
MATCH (u:Usuario {id_usuario: $miId})-[r:REACCIONA {tipo_reaccion:'LIKE'}]->(p:Post {id_post: $idPost})
DELETE r
RETURN u.username AS usuario, p.id_post AS post;

// ============================================================
// PERFIL DE USUARIO (issue #31)
// ============================================================

// ------------------------------------------------------------
// 12) HISTORIAL DE PUBLICACIONES de un usuario
//     Todas las publicaciones propias del usuario (su muro), de la
//     mas reciente a la mas antigua, con su conteo de reacciones.
//     Se agrupa por p.id_post (unico) para no mezclar publicaciones.
// ------------------------------------------------------------
MATCH (u:Usuario {id_usuario: $miId})-[:PUBLICA]->(p:Post)
OPTIONAL MATCH (p)<-[r:REACCIONA]-()
RETURN p.id_post            AS id_post,
       p.texto              AS texto,
       p.media_url          AS media_url,
       p.fecha_publicacion  AS fecha_publicacion,
       count(r)             AS reacciones
ORDER BY p.fecha_publicacion DESC;

// ============================================================
// NOTIFICACIONES (issue #44)
// Nodo (:Notificacion) ligado al usuario DESTINATARIO con [:RECIBE] y al
// usuario ORIGEN del evento con [:ORIGINADA_POR].
// tipo: SEGUIMIENTO | MENSAJE | PUBLICACION | LIKE
// Propiedades: id_notificacion, tipo, mensaje, referencia, url_interna,
//              id_origen, fecha, leido
// ============================================================

// ------------------------------------------------------------
// 13) GUARDAR una notificación (idempotente / anti-duplicados)
//     MERGE sobre (:Notificacion {clave_dedup}) — propiedad con constraint de
//     unicidad — garantiza que no se dupliquen ni siquiera ante peticiones
//     concurrentes (la BD serializa el MERGE sobre esa propiedad única).
//     clave_dedup = destinatario|tipo|referencia|origen.
//     Luego se enlazan RECIBE y ORIGINADA_POR entre nodos ya resueltos.
//     WHERE dest <> orig impide auto-notificarse.
// ------------------------------------------------------------
MATCH (dest:Usuario {id_usuario: $idDestinatario}), (orig:Usuario {id_usuario: $idOrigen})
WHERE dest <> orig
WITH dest, orig,
     $idDestinatario + '|' + $tipo + '|' + $referencia + '|' + $idOrigen AS clave
MERGE (n:Notificacion {clave_dedup: clave})
  ON CREATE SET n.id_notificacion = $idNotificacion,
                n.tipo            = $tipo,
                n.referencia      = $referencia,
                n.id_origen       = $idOrigen,
                n.mensaje         = $mensaje,
                n.url_interna     = $urlInterna,
                n.fecha           = datetime(),
                n.leido           = false
MERGE (dest)-[:RECIBE]->(n)
MERGE (n)-[:ORIGINADA_POR]->(orig)
RETURN n.id_notificacion AS id_notificacion, n.leido AS leido;

// ------------------------------------------------------------
// 14) LISTAR las notificaciones de un usuario (más recientes primero)
// ------------------------------------------------------------
MATCH (dest:Usuario {id_usuario: $miId})-[:RECIBE]->(n:Notificacion)
OPTIONAL MATCH (n)-[:ORIGINADA_POR]->(orig:Usuario)
RETURN n.id_notificacion AS id_notificacion,
       n.tipo            AS tipo,
       n.mensaje         AS mensaje,
       n.referencia      AS referencia,
       n.url_interna     AS url_interna,
       orig.username     AS origen,
       n.fecha           AS fecha,
       n.leido           AS leido
ORDER BY n.fecha DESC
LIMIT 50;

// ------------------------------------------------------------
// 15) MARCAR UNA notificación como leída (acotada al destinatario)
// ------------------------------------------------------------
MATCH (dest:Usuario {id_usuario: $miId})-[:RECIBE]->(n:Notificacion {id_notificacion: $idNotificacion})
SET n.leido = true
RETURN n.id_notificacion AS id_notificacion, n.leido AS leido;

// ------------------------------------------------------------
// 16) MARCAR TODAS como leídas
// ------------------------------------------------------------
MATCH (dest:Usuario {id_usuario: $miId})-[:RECIBE]->(n:Notificacion)
WHERE n.leido = false
SET n.leido = true
RETURN count(n) AS marcadas;

// ------------------------------------------------------------
// 17) CONTAR las NO leídas (para el punto/contador de la barra)
// ------------------------------------------------------------
MATCH (dest:Usuario {id_usuario: $miId})-[:RECIBE]->(n:Notificacion)
WHERE n.leido = false
RETURN count(n) AS no_leidas;

// ------------------------------------------------------------
// 18) LIMPIAR notificaciones antiguas (política de retención)
//     Borra las que tengan más de $dias días. DETACH DELETE quita
//     también sus relaciones RECIBE / ORIGINADA_POR.
// ------------------------------------------------------------
MATCH (n:Notificacion)
WHERE n.fecha < datetime() - duration({days: $dias})
DETACH DELETE n;
