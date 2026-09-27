// ============================================================
// 02-seed.cypher — Datos de prueba para demo / presentación
// Crea usuarios, relaciones SIGUE, posts, reacciones y un chat.
// Convención: snake_case, alineado con docs/CONTRATOS.md
// OJO: password_hash aquí es un valor de ejemplo, no una contraseña real.
// ============================================================

// Limpieza previa (solo para el entorno de pruebas)
MATCH (n) DETACH DELETE n;

// --- Usuarios ---
CREATE (anthony:Usuario {id_usuario:'u1', username:'anthony', email:'anthony@demo.com', nombre:'Anthony', bio:'Dev backend',  password_hash:'demo-hash', fecha_registro: datetime()})
CREATE (carlos:Usuario  {id_usuario:'u2', username:'carlos',  email:'carlos@demo.com',  nombre:'Carlos',  bio:'Fan del grafo', password_hash:'demo-hash', fecha_registro: datetime()})
CREATE (said:Usuario    {id_usuario:'u3', username:'said',    email:'said@demo.com',    nombre:'Said',    bio:'Frontend',      password_hash:'demo-hash', fecha_registro: datetime()})
CREATE (oscar:Usuario   {id_usuario:'u4', username:'oscar',   email:'oscar@demo.com',   nombre:'Oscar',   bio:'DevOps',        password_hash:'demo-hash', fecha_registro: datetime()})
CREATE (estalin:Usuario {id_usuario:'u5', username:'estalin', email:'estalin@demo.com', nombre:'Estalin', bio:'Base de datos', password_hash:'demo-hash', fecha_registro: datetime()})

// --- Relaciones SIGUE (dirigidas) ---
// anthony -> carlos -> said : sirve para la recomendación de 2 niveles
CREATE (anthony)-[:SIGUE {desde: datetime()}]->(carlos)
CREATE (carlos)-[:SIGUE {desde: datetime()}]->(said)
CREATE (said)-[:SIGUE {desde: datetime()}]->(oscar)
CREATE (estalin)-[:SIGUE {desde: datetime()}]->(carlos)
CREATE (anthony)-[:SIGUE {desde: datetime()}]->(estalin)

// --- Publicaciones ---
CREATE (carlos)-[:PUBLICA]->(p1:Post {id_post:'p1', texto:'Mi primer post en la red social', fecha_publicacion: datetime()})
CREATE (said)-[:PUBLICA]->(p2:Post   {id_post:'p2', texto:'Probando el feed distribuido', fecha_publicacion: datetime(), media_url:'red-social-media/p2/foto.jpg', media_tipo:'image'})
CREATE (oscar)-[:PUBLICA]->(p3:Post  {id_post:'p3', texto:'Docker levantado con un solo comando', fecha_publicacion: datetime()})

// --- Reacciones (tipo guardado en la relación) ---
CREATE (anthony)-[:REACCIONA {tipo_reaccion:'LIKE', fecha: datetime()}]->(p1)
CREATE (estalin)-[:REACCIONA {tipo_reaccion:'LOVE', fecha: datetime()}]->(p1)

// --- Conversacion + mensajes (historial del chat) ---
CREATE (conv:Conversacion {id_conversacion:'c1', fecha_creacion: datetime()})
CREATE (anthony)-[:PARTICIPA]->(conv)
CREATE (carlos)-[:PARTICIPA]->(conv)
CREATE (m1:Mensaje {id_mensaje:'m1', contenido:'Hola Carlos!', fecha_envio: datetime(), leido:true})
CREATE (m2:Mensaje {id_mensaje:'m2', contenido:'Hola Anthony, todo listo?', fecha_envio: datetime(), leido:false})
CREATE (anthony)-[:ENVIA]->(m1)-[:EN]->(conv)
CREATE (carlos)-[:ENVIA]->(m2)-[:EN]->(conv);
