package com.redsocial.repository;

import com.redsocial.dto.FeedItemResponse;
import com.redsocial.dto.SugerenciaResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Values;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class GrafoSocialRepository {

    @Inject
    Driver driver;

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    // Consulta #3: Feed personalizado ordenado por fecha descendente
    public List<FeedItemResponse> obtenerFeed(String miId) {
        String query = """
            MATCH (yo:Usuario)-[:SIGUE]->(autor:Usuario)-[:PUBLICA]->(p:Post)
            WHERE yo.id_usuario = $miId OR yo.username = $miId OR yo.id = $miId
            OPTIONAL MATCH (p)<-[r:REACCIONA]-()
            RETURN p.id_post AS id_post,
                   autor.username AS autor,
                   p.texto AS texto,
                   p.media_url AS media_url,
                   toString(p.fecha_publicacion) AS fecha_publicacion,
                   count(r) AS reacciones
            ORDER BY p.fecha_publicacion DESC
            LIMIT 20
            """;

        try (var session = driver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run(query, Values.parameters("miId", miId));
                List<FeedItemResponse> feed = new ArrayList<>();
                while (result.hasNext()) {
                    var row = result.next();
                    feed.add(new FeedItemResponse(
                            row.get("id_post").asString(null),
                            row.get("autor").asString(null),
                            row.get("texto").asString(null),
                            row.get("media_url").asString(null),
                            row.get("fecha_publicacion").asString(null),
                            row.get("reacciones").asLong(0L)
                    ));
                }
                return feed;
            });
        }
    }

    // Consulta #8: Seguir a un usuario (MERGE idempotente)
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
                var result = tx.run(query, Values.parameters("miId", miId, "idDestino", idDestino));
                return result.hasNext();
            });
        }
    }

    // Consulta #9: Dejar de seguir a un usuario
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
                var result = tx.run(query, Values.parameters("miId", miId, "idDestino", idDestino));
                return result.hasNext();
            });
        }
    }

    // Consulta #4: Recomendación de usuarios (2 niveles del grafo)
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
                var result = tx.run(query, Values.parameters("miId", miId));
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
}