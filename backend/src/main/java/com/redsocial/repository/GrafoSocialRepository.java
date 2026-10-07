package com.redsocial.repository;

import com.redsocial.dto.FeedItemResponse;
import com.redsocial.dto.SugerenciaResponse;
import com.redsocial.dto.UsuarioResumenResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;

import java.util.ArrayList;
import java.util.List;

/**
 * Repositorio para gestionar las consultas y relaciones del grafo social en Neo4j.
 */
@ApplicationScoped
public class GrafoSocialRepository {

    private static final String PARAM_MI_ID = "miId";
    private static final String PARAM_ID_DESTINO = "idDestino";
    private static final String PARAM_ID_POST = "idPost";
    private static final String COL_AUTOR = "autor";

    /**
     * Resultado de la operación de reacción LIKE sobre una publicación.
     *
     * @param autor        nombre de usuario del autor del post
     * @param recienCreado true si la reacción se creó por primera vez, false si ya existía
     */
    public record LikeResult(String autor, boolean recienCreado) {
    }

    @Inject
    Driver driver;

    /**
     * Asigna la instancia del driver de Neo4j.
     *
     * @param driver instancia de Driver de Neo4j
     */
    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    /**
     * Obtiene las publicaciones públicas ordenadas por fecha descendente.
     *
     * @param miId identificador o username del usuario
     * @return lista de publicaciones del feed
     */
    public List<FeedItemResponse> obtenerFeed(String miId) {
        String query = """
            MATCH (yo:Usuario)
            WHERE yo.id_usuario = $miId OR yo.username = $miId OR yo.id = $miId
            WITH count(yo) AS usuariosSolicitantes
            WHERE usuariosSolicitantes > 0
            MATCH (autor:Usuario)-[:PUBLICA]->(p:Post)
            WITH DISTINCT autor, p
            OPTIONAL MATCH (p)<-[r:REACCIONA]-()
            WITH autor, p, count(r) AS reacciones
            RETURN p.id_post AS id_post,
                   autor.username AS autor,
                   p.texto AS texto,
                   p.media_url AS media_url,
                   toString(p.fecha_publicacion) AS fecha_publicacion,
                   reacciones,
                   p.media_tipo AS media_tipo,
                   EXISTS { MATCH (lector:Usuario)-[:REACCIONA {tipo_reaccion: 'LIKE'}]->(p)
                            WHERE lector.username = $miId OR lector.id_usuario = $miId OR lector.id = $miId } AS liked
            ORDER BY p.fecha_publicacion DESC
            LIMIT 20
            """;

        try (var session = driver.session()) {
            return session.executeRead(tx -> mapearPosts(tx.run(query, Values.parameters(PARAM_MI_ID, miId))));
        }
    }

    /**
     * Obtiene el historial de publicaciones propias de un usuario para su perfil.
     *
     * @param miId identificador o username del usuario
     * @param lector cuenta autenticada que consulta el perfil; vacío si no hay sesión
     * @return lista de publicaciones del usuario ordenadas por fecha descendente
     */
    public List<FeedItemResponse> obtenerPostsDeUsuario(String miId, String lector) {
        String query = """
            MATCH (yo:Usuario)-[:PUBLICA]->(p:Post)
            WHERE yo.id_usuario = $miId OR yo.username = $miId OR yo.id = $miId
            WITH DISTINCT yo, p
            OPTIONAL MATCH (p)<-[r:REACCIONA]-()
            WITH yo, p, count(r) AS reacciones
            RETURN p.id_post AS id_post,
                   yo.username AS autor,
                   p.texto AS texto,
                   p.media_url AS media_url,
                   toString(p.fecha_publicacion) AS fecha_publicacion,
                   reacciones,
                   p.media_tipo AS media_tipo,
                   EXISTS { MATCH (:Usuario {username: $lector})-[:REACCIONA {tipo_reaccion: 'LIKE'}]->(p) } AS liked
            ORDER BY p.fecha_publicacion DESC
            """;

        try (var session = driver.session()) {
            return session.executeRead(tx -> mapearPosts(tx.run(query, Values.parameters(PARAM_MI_ID, miId, "lector", lector))));
        }
    }

    /**
     * Obtiene la lista de seguidores de un usuario (Consulta N.° 1 de 03-consultas.cypher).
     *
     * @param miId identificador o username del usuario
     * @return lista de usuarios seguidores ordenada alfabéticamente
     */
    public List<UsuarioResumenResponse> obtenerSeguidores(String miId) {
        String query = """
            MATCH (seguidor:Usuario)-[:SIGUE]->(yo:Usuario)
            WHERE yo.id_usuario = $miId OR yo.username = $miId OR yo.id = $miId
            RETURN DISTINCT seguidor.username AS username
            ORDER BY username
            """;

        try (var session = driver.session()) {
            return session.executeRead(tx -> mapearUsuarios(tx.run(query, Values.parameters(PARAM_MI_ID, miId))));
        }
    }

    /**
     * Obtiene la lista de cuentas seguidas por un usuario (Consulta N.° 2 de 03-consultas.cypher).
     *
     * @param miId identificador o username del usuario
     * @return lista de usuarios seguidos ordenada alfabéticamente
     */
    public List<UsuarioResumenResponse> obtenerSeguidos(String miId) {
        String query = """
            MATCH (yo:Usuario)-[:SIGUE]->(seguido:Usuario)
            WHERE yo.id_usuario = $miId OR yo.username = $miId OR yo.id = $miId
            RETURN DISTINCT seguido.username AS username
            ORDER BY username
            """;

        try (var session = driver.session()) {
            return session.executeRead(tx -> mapearUsuarios(tx.run(query, Values.parameters(PARAM_MI_ID, miId))));
        }
    }

    /**
     * Crea de forma idempotente la relación de seguimiento entre dos usuarios (Consulta N.° 8).
     *
     * @param miId      identificador del usuario seguidor
     * @param idDestino identificador del usuario a seguir
     * @return true si ambos usuarios existen y se registró la relación
     */
    public boolean seguirUsuario(String miId, String idDestino) {
        String query = """
            MATCH (a:Usuario), (b:Usuario)
            WHERE (a.id_usuario = $miId OR a.username = $miId OR a.id = $miId)
              AND (b.id_usuario = $idDestino OR b.username = $idDestino OR b.id = $idDestino)
              AND a <> b
            MERGE (a)-[r:SIGUE]->(b)
              ON CREATE SET r.desde = datetime()
            RETURN a.username AS sigo_ahora_a, b.username AS seguido
            """;

        try (var session = driver.session()) {
            return session.executeWrite(tx -> {
                var result = tx.run(query, Values.parameters(PARAM_MI_ID, miId, PARAM_ID_DESTINO, idDestino));
                return result.hasNext();
            });
        }
    }

    /**
     * Elimina la relación de seguimiento entre dos usuarios (Consulta N.° 9).
     *
     * @param miId      identificador del usuario seguidor
     * @param idDestino identificador del usuario seguido
     * @return true si existía la relación y fue eliminada
     */
    public boolean dejarDeSeguirUsuario(String miId, String idDestino) {
        String query = """
            MATCH (a:Usuario)-[r:SIGUE]->(b:Usuario)
            WHERE (a.id_usuario = $miId OR a.username = $miId OR a.id = $miId)
              AND (b.id_usuario = $idDestino OR b.username = $idDestino OR b.id = $idDestino)
            DELETE r
            RETURN a.username AS dejo_de_seguir_a
            """;

        try (var session = driver.session()) {
            return session.executeWrite(tx -> {
                var result = tx.run(query, Values.parameters(PARAM_MI_ID, miId, PARAM_ID_DESTINO, idDestino));
                return result.hasNext();
            });
        }
    }

    /**
     * Obtiene sugerencias de usuarios a seguir basadas en amigos de amigos (Consulta N.° 4).
     *
     * @param miId identificador del usuario
     * @return lista de sugerencias ordenadas por conexiones en común
     */
    public List<SugerenciaResponse> obtenerSugerencias(String miId) {
        String query = """
            MATCH (yo:Usuario)-[:SIGUE]->(intermedio:Usuario)-[:SIGUE]->(sugerido:Usuario)
            WHERE (yo.id_usuario = $miId OR yo.username = $miId OR yo.id = $miId)
              AND sugerido <> yo AND NOT (yo)-[:SIGUE]->(sugerido)
            RETURN sugerido.username AS recomendado,
                   count(DISTINCT intermedio) AS conexiones_en_comun
            ORDER BY conexiones_en_comun DESC, recomendado
            LIMIT 5
            """;

        try (var session = driver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run(query, Values.parameters(PARAM_MI_ID, miId));
                List<SugerenciaResponse> sugerencias = new ArrayList<>();
                while (result.hasNext()) {
                    var row = result.next();
                    sugerencias.add(new SugerenciaResponse(
                            row.get("recomendado").asString(null),
                            row.get("conexiones_en_comun").asLong(0L)
                    ));
                }
                return sugerencias;
            });
        }
    }

    /**
     * Registra de forma idempotente una reacción LIKE sobre una publicación (Consulta N.° 10).
     *
     * @param miId   identificador o username del usuario que reacciona
     * @param idPost identificador único de la publicación
     * @return resultado con el autor del post y si la reacción fue recién creada, o null si no existen
     */
    public LikeResult darLikePost(String miId, String idPost) {
        String query = """
            MATCH (u:Usuario), (p:Post {id_post: $idPost})
            WHERE u.id_usuario = $miId OR u.username = $miId OR u.id = $miId
            WITH u, p LIMIT 1
            OPTIONAL MATCH (autor:Usuario)-[:PUBLICA]->(p)
            WITH u, p, head(collect(autor.username)) AS autorUsername
            OPTIONAL MATCH (u)-[existente:REACCIONA {tipo_reaccion: 'LIKE'}]->(p)
            WITH u, p, autorUsername, (existente IS NULL) AS recienCreado
            MERGE (u)-[r:REACCIONA {tipo_reaccion: 'LIKE'}]->(p)
              ON CREATE SET r.fecha = datetime()
            RETURN autorUsername AS autor, recienCreado AS creado
            """;

        try (var session = driver.session()) {
            return session.executeWrite(tx -> {
                var result = tx.run(query, Values.parameters(PARAM_MI_ID, miId, PARAM_ID_POST, idPost));
                if (result.hasNext()) {
                    var row = result.next();
                    Value autorVal = row.get(COL_AUTOR);
                    String autor = (autorVal != null && !autorVal.isNull()) ? autorVal.asString(null) : null;
                    Value creadoVal = row.get("creado");
                    boolean creado = creadoVal == null || creadoVal.isNull() || creadoVal.asBoolean(true);
                    return new LikeResult(autor, creado);
                }
                return null;
            });
        }
    }

    /**
     * Elimina la reacción LIKE de un usuario sobre una publicación (Consulta N.° 11).
     *
     * @param miId   identificador o username del usuario
     * @param idPost identificador único de la publicación
     * @return true si existía la reacción LIKE y fue eliminada
     */
    public boolean quitarLikePost(String miId, String idPost) {
        String query = """
            MATCH (u:Usuario)-[r:REACCIONA {tipo_reaccion: 'LIKE'}]->(p:Post {id_post: $idPost})
            WHERE u.id_usuario = $miId OR u.username = $miId OR u.id = $miId
            WITH u, r LIMIT 1
            DELETE r
            RETURN u.username AS usuario
            """;

        try (var session = driver.session()) {
            return session.executeWrite(tx -> {
                var result = tx.run(query, Values.parameters(PARAM_MI_ID, miId, PARAM_ID_POST, idPost));
                return result.hasNext();
            });
        }
    }

    /**
     * Convierte un resultado de Neo4j en una lista de FeedItemResponse.
     *
     * @param result resultado de la consulta Cypher
     * @return lista de publicaciones mapeadas
     */
    private List<FeedItemResponse> mapearPosts(Result result) {
        List<FeedItemResponse> posts = new ArrayList<>();
        while (result.hasNext()) {
            var row = result.next();
            posts.add(new FeedItemResponse(
                    row.get("id_post").asString(null),
                    row.get(COL_AUTOR).asString(null),
                    row.get("texto").asString(null),
                    row.get("media_url").asString(null),
                    row.get("fecha_publicacion").asString(null),
                    row.get("reacciones").asLong(0L),
                    row.get("liked").asBoolean(false),
                    row.get("media_tipo").asString(null)
            ));
        }
        return posts;
    }

    /**
     * Convierte un resultado de Neo4j en una lista de UsuarioResumenResponse.
     *
     * @param result resultado de la consulta Cypher
     * @return lista de usuarios mapeados
     */
    private List<UsuarioResumenResponse> mapearUsuarios(Result result) {
        List<UsuarioResumenResponse> usuarios = new ArrayList<>();
        while (result.hasNext()) {
            var row = result.next();
            usuarios.add(new UsuarioResumenResponse(row.get("username").asString(null)));
        }
        return usuarios;
    }
}
