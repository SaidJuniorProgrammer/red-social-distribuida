// ============================================================
// 01-schema.cypher — Restricciones e índices del grafo
// Ejecutar UNA vez contra la base (idempotente gracias a IF NOT EXISTS).
// Convención: snake_case, alineado con docs/CONTRATOS.md
// ============================================================

// --- Unicidad de identificadores (crea índice de respaldo automáticamente) ---
CREATE CONSTRAINT usuario_id IF NOT EXISTS
FOR (u:Usuario) REQUIRE u.id_usuario IS UNIQUE;

CREATE CONSTRAINT usuario_username IF NOT EXISTS
FOR (u:Usuario) REQUIRE u.username IS UNIQUE;

CREATE CONSTRAINT usuario_email IF NOT EXISTS
FOR (u:Usuario) REQUIRE u.email IS UNIQUE;

CREATE CONSTRAINT post_id IF NOT EXISTS
FOR (p:Post) REQUIRE p.id_post IS UNIQUE;

CREATE CONSTRAINT conversacion_id IF NOT EXISTS
FOR (c:Conversacion) REQUIRE c.id_conversacion IS UNIQUE;

CREATE CONSTRAINT mensaje_id IF NOT EXISTS
FOR (m:Mensaje) REQUIRE m.id_mensaje IS UNIQUE;

CREATE CONSTRAINT push_subscription_endpoint IF NOT EXISTS
FOR (s:PushSubscription) REQUIRE s.endpoint IS UNIQUE;

// --- Índices para consultas frecuentes ---
CREATE INDEX post_fecha IF NOT EXISTS
FOR (p:Post) ON (p.fecha_publicacion);
