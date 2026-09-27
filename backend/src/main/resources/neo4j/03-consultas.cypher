// ============================================================
// 03-consultas.cypher — Consultas Cypher no triviales (5+)
// La actividad exige mínimo 5, y al menos una que recorra
// relaciones de MÁS de un nivel (la #4 y #6 lo hacen).
// Convención: snake_case, alineado con docs/CONTRATOS.md
// Reemplaza $miId por un id real (ej. 'u1') al probar en Neo4j Browser.
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
MATCH (yo:Usuario {id_usuario: $miId})-[:SIGUE]->(:Usuario)-[:PUBLICA]->(p:Post)
OPTIONAL MATCH (p)<-[r:REACCIONA]-()
RETURN p.texto             AS publicacion,
       p.fecha_publicacion AS fecha,
       count(r)            AS reacciones
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
