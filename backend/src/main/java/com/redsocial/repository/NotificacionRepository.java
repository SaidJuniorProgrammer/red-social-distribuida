package com.redsocial.repository;

import com.redsocial.dto.NotificacionResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Values;

import java.util.ArrayList;
import java.util.List;

/**
 * Repositorio para gestionar la persistencia y lectura de notificaciones en Neo4j.
 */
@ApplicationScoped
public class NotificacionRepository {

    @Inject
    Driver driver;

    /**
     * Guarda una notificación en la base de datos de forma idempotente para evitar duplicados.
     * 
     * @param idNotificacion identificador único (ej. tipo_actor_referencia)
     * @param tipo           tipo de evento (FOLLOW, LIKE, POST, MENSAJE)
     * @param actor          username del usuario que origina el evento
     * @param destinatario   username del destinatario
     * @param mensaje        texto de la notificación
     * @param referencia     URL interna o recurso asociado
     * @param fecha          fecha de creación
     */
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
                          n.leida = false
            MERGE (orig)-[:GENERA]->(n)
            MERGE (n)-[:DIRIGIDA_A]->(dest)
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

    /**
     * Obtiene el listado de notificaciones de un usuario, ordenadas por las más recientes.
     *
     * @param destinatario username del usuario
     * @return lista de notificaciones
     */
    public List<NotificacionResponse> listarNotificaciones(String destinatario) {
        String query = """
            MATCH (orig:Usuario)-[:GENERA]->(n:Notificacion)-[:DIRIGIDA_A]->(dest:Usuario)
            WHERE dest.id_usuario = $destinatario OR dest.username = $destinatario
            RETURN n.id_notificacion AS id, n.tipo AS tipo, orig.username AS actor,
                   dest.username AS destinatario, n.mensaje AS mensaje, n.referencia AS referencia,
                   n.fecha AS fecha, n.leida AS leida
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
                            row.get("leida").asBoolean(false)
                    ));
                }
                return lista;
            });
        }
    }

    /**
     * Marca una notificación específica como leída.
     *
     * @param idNotificacion identificador de la notificación
     * @param destinatario   username del propietario de la notificación
     * @return true si la notificación existía y se actualizó
     */
    public boolean marcarComoLeida(String idNotificacion, String destinatario) {
        String query = """
            MATCH (n:Notificacion)-[:DIRIGIDA_A]->(dest:Usuario)
            WHERE (dest.id_usuario = $destinatario OR dest.username = $destinatario)
              AND n.id_notificacion = $idNotificacion
            SET n.leida = true
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

    /**
     * Marca todas las notificaciones pendientes de un usuario como leídas.
     *
     * @param destinatario username del usuario
     * @return cantidad de notificaciones actualizadas
     */
    public long marcarTodasComoLeidas(String destinatario) {
        String query = """
            MATCH (n:Notificacion)-[:DIRIGIDA_A]->(dest:Usuario)
            WHERE (dest.id_usuario = $destinatario OR dest.username = $destinatario)
              AND n.leida = false
            SET n.leida = true
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

    /**
     * Cuenta la cantidad de notificaciones que el usuario aún no ha leído.
     *
     * @param destinatario username del usuario
     * @return número de notificaciones no leídas
     */
    public long contarNoLeidas(String destinatario) {
        String query = """
            MATCH (n:Notificacion)-[:DIRIGIDA_A]->(dest:Usuario)
            WHERE (dest.id_usuario = $destinatario OR dest.username = $destinatario)
              AND n.leida = false
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