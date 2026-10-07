package com.redsocial.repository;

import com.redsocial.dto.NotificacionResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Values;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class NotificacionRepository {

    @Inject
    Driver driver;

    public void guardarNotificacion(String idNotificacion, String tipo, String actor, 
                                    String destinatario, String mensaje, String referencia, String fecha) {
        String query = """
            MATCH (dest:Usuario), (orig:Usuario)
            WHERE (dest.id_usuario = $destinatario OR dest.username = $destinatario)
              AND (orig.id_usuario = $actor OR orig.username = $actor)
            MERGE (n:Notificacion {id_notificacion: $idNotificacion})
            ON CREATE SET n.tipo = $tipo, 
                          n.mensaje = $mensaje, 
                          n.referencia = $referencia, 
                          n.fecha = $fecha, 
                          n.leido = false
            MERGE (dest)-[:RECIBE]->(n)
            MERGE (n)-[:ORIGINADA_POR]->(orig)
            """;

        try (var session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run(query, Values.parameters(
                        "idNotificacion", idNotificacion,
                        "tipo", tipo,
                        "actor", actor,
                        "destinatario", destinatario,
                        "mensaje", mensaje,
                        "referencia", referencia,
                        "fecha", fecha
                ));
                return null;
            });
        }
    }

    public List<NotificacionResponse> listarNotificaciones(String destinatario) {
        String query = """
            MATCH (dest:Usuario)-[:RECIBE]->(n:Notificacion)-[:ORIGINADA_POR]->(orig:Usuario)
            WHERE dest.id_usuario = $destinatario OR dest.username = $destinatario
            RETURN n.id_notificacion AS id, n.tipo AS tipo, orig.username AS actor,
                   dest.username AS destinatario, n.mensaje AS mensaje, n.referencia AS referencia,
                   n.fecha AS fecha, n.leido AS leido
            ORDER BY n.fecha DESC
            LIMIT 50
            """;

        try (var session = driver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run(query, Values.parameters("destinatario", destinatario));
                List<NotificacionResponse> lista = new ArrayList<>();
                while (result.hasNext()) {
                    var row = result.next();
                    lista.add(new NotificacionResponse(
                            row.get("id").asString(),
                            row.get("tipo").asString(),
                            row.get("actor").asString(),
                            row.get("destinatario").asString(),
                            row.get("mensaje").asString(),
                            row.get("referencia").asString(),
                            row.get("fecha").asString(),
                            row.get("leido").asBoolean(false)
                    ));
                }
                return lista;
            });
        }
    }

    public boolean marcarComoLeida(String idNotificacion, String destinatario) {
        String query = """
            MATCH (dest:Usuario)-[:RECIBE]->(n:Notificacion)
            WHERE (dest.id_usuario = $destinatario OR dest.username = $destinatario)
              AND n.id_notificacion = $idNotificacion
            SET n.leido = true
            RETURN n.id_notificacion
            """;

        try (var session = driver.session()) {
            return session.executeWrite(tx -> {
                var result = tx.run(query, Values.parameters(
                        "idNotificacion", idNotificacion,
                        "destinatario", destinatario
                ));
                return result.hasNext();
            });
        }
    }

    public long marcarTodasComoLeidas(String destinatario) {
        String query = """
            MATCH (dest:Usuario)-[:RECIBE]->(n:Notificacion)
            WHERE (dest.id_usuario = $destinatario OR dest.username = $destinatario)
              AND n.leido = false
            SET n.leido = true
            RETURN count(n) AS actualizadas
            """;

        try (var session = driver.session()) {
            return session.executeWrite(tx -> {
                var result = tx.run(query, Values.parameters("destinatario", destinatario));
                if (result.hasNext()) {
                    return result.next().get("actualizadas").asLong(0L);
                }
                return 0L;
            });
        }
    }

    public long contarNoLeidas(String destinatario) {
        String query = """
            MATCH (dest:Usuario)-[:RECIBE]->(n:Notificacion)
            WHERE (dest.id_usuario = $destinatario OR dest.username = $destinatario)
              AND n.leido = false
            RETURN count(n) AS no_leidas
            """;

        try (var session = driver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run(query, Values.parameters("destinatario", destinatario));
                if (result.hasNext()) {
                    return result.next().get("no_leidas").asLong(0L);
                }
                return 0L;
            });
        }
    }
}