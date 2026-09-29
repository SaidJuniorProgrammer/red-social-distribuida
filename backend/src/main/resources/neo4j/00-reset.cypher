// ============================================================
// 00-reset.cypher — BORRADO TOTAL de la base (solo entorno de pruebas)
// ============================================================
// ⚠️  PELIGRO: este script borra TODOS los nodos y relaciones de la
//     base de datos a la que te conectes. Ejecútalo ÚNICAMENTE contra
//     una base desechable de desarrollo, NUNCA contra datos reales.
//
// Uso típico (re-sembrar desde cero):
//     cypher-shell ... < 00-reset.cypher
//     cypher-shell ... < 01-schema.cypher
//     cypher-shell ... < 02-seed.cypher
//
// Se mantiene separado de 02-seed.cypher a propósito, para que sembrar
// datos de ejemplo no borre por accidente lo que ya exista.
// ============================================================

MATCH (n) DETACH DELETE n;
