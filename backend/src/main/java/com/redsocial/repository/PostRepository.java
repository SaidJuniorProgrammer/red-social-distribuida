package com.redsocial.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Values;

@ApplicationScoped
public class PostRepository {

    @Inject
    Driver driver;

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public boolean crearPost(String idPost, String autor, String texto, String mediaUrl, String mediaTipo, String fechaPublicacion) {
        String query = """
            MATCH (u:Usuario)
            WHERE u.username = $autor OR u.id_usuario = $autor OR u.id = $autor
            CREATE (u)-[:PUBLICA]->(p:Post {
                id_post: $id_post,
                texto: $texto,
                media_url: $media_url,
                media_tipo: $media_tipo,
                fecha_publicacion: datetime($fecha_publicacion)
            })
            RETURN p.id_post AS id_post
            """;

        try (var session = driver.session()) {
            return session.executeWrite(tx -> {
                var result = tx.run(query, Values.parameters(
                        "autor", autor,
                        "id_post", idPost,
                        "texto", texto,
                        "media_url", mediaUrl,
                        "media_tipo", mediaTipo,
                        "fecha_publicacion", fechaPublicacion
                ));
                return result != null && result.hasNext();
            });
        }
    }
}