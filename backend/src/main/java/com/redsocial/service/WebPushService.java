package com.redsocial.service;

import com.redsocial.dto.PushNotificationPayload;
import com.redsocial.dto.PushSubscriptionRequest;
import com.redsocial.repository.NotificacionRepository;
import com.redsocial.repository.PostRepository;
import com.redsocial.repository.PushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio encargado de gestionar las llaves VAPID, las suscripciones Web Push
 * y la persistencia de notificaciones de eventos (Posts, Likes, Follows, Mensajes).
 */
@ApplicationScoped
public class WebPushService {

    private static final Logger LOG = Logger.getLogger(WebPushService.class);

    @Inject
    PostRepository postRepository;

    @Inject
    NotificacionRepository notificacionRepository;

    @Inject
    WebPushGateway webPushGateway;

    @Inject
    PushSubscriptionRepository pushSubscriptionRepository;

    @ConfigProperty(name = "webpush.vapid.public-key")
    Optional<String> configuredPublicKey;

    @ConfigProperty(name = "webpush.vapid.private-key")
    Optional<String> configuredPrivateKey;

    private String vapidPublicKey;
    private String vapidPrivateKey;

    private final Map<String, Map<String, PushSubscriptionRequest>> suscripciones = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        if (configuredPublicKey.filter(key -> !key.isBlank()).isPresent()
                && configuredPrivateKey.filter(key -> !key.isBlank()).isPresent()) {
            vapidPublicKey = configuredPublicKey.get().trim();
            vapidPrivateKey = configuredPrivateKey.get().trim();
            LOG.info("Llaves VAPID permanentes cargadas desde la configuración.");
        } else {
            generarLlavesVapid();
            LOG.warn("Se generaron llaves VAPID temporales. Configura VAPID_PUBLIC_KEY y VAPID_PRIVATE_KEY en producción.");
        }

        restaurarSuscripciones();
    }

    public void generarLlavesVapid() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair keyPair = keyGen.generateKeyPair();

            byte[] pubEncoded = keyPair.getPublic().getEncoded();
            byte[] uncompressedPoint = Arrays.copyOfRange(pubEncoded, pubEncoded.length - 65, pubEncoded.length);

            ECPrivateKey privKey = (ECPrivateKey) keyPair.getPrivate();
            byte[] privBytes = normalizarLlavePrivada(privKey.getS().toByteArray());

            Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();
            this.vapidPublicKey = urlEncoder.encodeToString(uncompressedPoint);
            this.vapidPrivateKey = urlEncoder.encodeToString(privBytes);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudieron generar las llaves VAPID", e);
        }
    }

    public String getVapidPublicKey() {
        return vapidPublicKey;
    }

    public String getVapidPrivateKey() {
        return vapidPrivateKey;
    }

    public void registrarSuscripcion(PushSubscriptionRequest request) {
        String username = request.usuario().trim();
        PushSubscriptionRequest normalizedRequest = new PushSubscriptionRequest(
                username, request.endpoint().trim(), request.keys()
        );
        pushSubscriptionRepository.guardar(normalizedRequest);
        
        suscripciones.forEach((registeredUser, userSubscriptions) -> {
            if (!registeredUser.equals(username)) {
                userSubscriptions.remove(normalizedRequest.endpoint());
            }
        });
        suscripciones.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        suscripciones.computeIfAbsent(username, ignored -> new ConcurrentHashMap<>())
                .put(normalizedRequest.endpoint(), normalizedRequest);
    }

    public boolean eliminarSuscripcion(String usuario, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) return false;
        String normalizedUsername = usuario.trim();
        String normalizedEndpoint = endpoint.trim();
        
        Map<String, PushSubscriptionRequest> userSubs = suscripciones.get(normalizedUsername);
        boolean removed = userSubs != null && userSubs.remove(normalizedEndpoint) != null;
        if (userSubs != null && userSubs.isEmpty()) {
            suscripciones.remove(normalizedUsername, userSubs);
        }
        boolean removedDb = pushSubscriptionRepository.eliminar(normalizedUsername, normalizedEndpoint);
        return removed || removedDb;
    }

    /**
     * Notifica a todos los seguidores cuando el autor realiza una nueva publicación.
     */
    public void notificarSeguidoresNuevaPublicacion(String autor, String idPost, String texto) {
        List<String> seguidores = postRepository.obtenerSeguidoresDeAutor(autor);
        String titulo = "Nueva publicación";
        String mensaje = "@" + autor.trim() + " ha publicado algo nuevo.";
        String referencia = "/feed"; // Navega al feed

        for (String seguidor : seguidores) {
            // SOLUCIÓN CODERABBIT: Añadimos el seguidor al ID para no compartir el mismo nodo en Neo4j
            String idNotificacion = "PUBLICACION_" + autor.trim() + "_" + idPost + "_" + seguidor.trim();
            procesarYEnviarNotificacion(idNotificacion, "PUBLICACION", autor.trim(), seguidor, titulo, mensaje, referencia);
        }
    }

    /**
     * Notifica al autor de una publicación cuando recibe un LIKE.
     */
    public void notificarLikePublicacion(String autorPost, String usuarioQueReacciona, String idPost) {
        String idNotificacion = "LIKE_" + usuarioQueReacciona.trim() + "_" + idPost;
        String titulo = "Nuevo Like";
        String mensaje = "@" + usuarioQueReacciona.trim() + " reaccionó a tu publicación.";
        String referencia = "/feed";

        procesarYEnviarNotificacion(idNotificacion, "LIKE", usuarioQueReacciona.trim(), autorPost.trim(), titulo, mensaje, referencia);
    }

    /**
     * Notifica a un usuario cuando recibe un nuevo seguidor.
     */
    public void notificarNuevoSeguidor(String seguidor, String seguido) {
        String idNotificacion = "SEGUIMIENTO_" + seguidor.trim() + "_" + seguido.trim();
        String titulo = "Nuevo Seguidor";
        String mensaje = "@" + seguidor.trim() + " ha comenzado a seguirte.";
        String referencia = "/perfil/" + seguidor.trim(); // Navega al perfil del nuevo seguidor

        procesarYEnviarNotificacion(idNotificacion, "SEGUIMIENTO", seguidor.trim(), seguido.trim(), titulo, mensaje, referencia);
    }

    /**
     * Notifica a un usuario cuando recibe un mensaje privado en el chat.
     */
    public void notificarNuevoMensaje(String emisor, String destinatario, String idMensaje) {
        String idNotificacion = "MENSAJE_" + emisor.trim() + "_" + idMensaje;
        String titulo = "Nuevo Mensaje";
        String mensaje = "@" + emisor.trim() + " te ha enviado un mensaje.";
        String referencia = "/mensajes"; // Navega a la ventana de chat

        procesarYEnviarNotificacion(idNotificacion, "MENSAJE", emisor.trim(), destinatario.trim(), titulo, mensaje, referencia);
    }

    void restaurarSuscripciones() {
        try {
            for (PushSubscriptionRequest subscription : pushSubscriptionRepository.obtenerTodas()) {
                suscripciones.computeIfAbsent(subscription.usuario(), ignored -> new ConcurrentHashMap<>())
                        .put(subscription.endpoint(), subscription);
            }
        } catch (Exception e) {
            LOG.warnf("No se pudieron restaurar las suscripciones Web Push: %s", e.getMessage());
        }
    }

    /**
     * Guarda la notificación en Neo4j y la despacha al Service Worker si el usuario tiene suscripciones activas.
     */
    private void procesarYEnviarNotificacion(
            String idNotificacion, String tipo, String actor, String destinatario,
            String titulo, String mensaje, String referencia
    ) {
        String ahora = Instant.now().toString();

        // 1. Guardar en base de datos de manera persistente (evita duplicados con MERGE)
        try {
            notificacionRepository.guardarNotificacion(idNotificacion, tipo, actor, destinatario, mensaje, referencia, ahora);
        } catch (Exception e) {
            LOG.errorf("Error al guardar notificación en Neo4j para %s: %s", destinatario, e.getMessage());
            return;
        }

        // 2. Construir payload y emitir alerta Push
        PushNotificationPayload payload = new PushNotificationPayload(
                idNotificacion, tipo, actor, destinatario, titulo, mensaje, referencia, ahora
        );

        List<PushSubscriptionRequest> userSubscriptions = new ArrayList<>(
                suscripciones.getOrDefault(destinatario, Map.of()).values()
        );

        for (PushSubscriptionRequest subscription : userSubscriptions) {
            try {
                int statusCode = webPushGateway.enviar(subscription, payload, vapidPublicKey, vapidPrivateKey);
                if (statusCode >= 200 && statusCode < 300) {
                    LOG.infof("Web Push enviado a %s (%s)", destinatario, tipo);
                } else if (statusCode == 404 || statusCode == 410) {
                    eliminarSuscripcion(destinatario, subscription.endpoint());
                    LOG.infof("Suscripción Web Push vencida eliminada para %s", destinatario);
                }
            } catch (Exception e) {
                LOG.warnf("No se pudo enviar Web Push a %s: %s", destinatario, e.getMessage());
            }
        }
    }

    private static byte[] normalizarLlavePrivada(byte[] encodedKey) {
        byte[] normalizedKey = new byte[32];
        int sourceStart = Math.max(0, encodedKey.length - normalizedKey.length);
        int copyLength = Math.min(encodedKey.length, normalizedKey.length);
        System.arraycopy(encodedKey, sourceStart, normalizedKey, normalizedKey.length - copyLength, copyLength);
        return normalizedKey;
    }
}