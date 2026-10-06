package com.redsocial.service;

import com.redsocial.dto.PushNotificationPayload;
import com.redsocial.dto.PushSubscriptionRequest;
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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servicio encargado de gestionar las llaves VAPID, las suscripciones Web Push
 * y el envío de notificaciones de nuevas publicaciones y reacciones.
 */
@ApplicationScoped
public class WebPushService {

    private static final Logger LOG = Logger.getLogger(WebPushService.class);

    @Inject
    PostRepository postRepository;

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
    private final Map<String, List<PushNotificationPayload>> bandejaPush = new ConcurrentHashMap<>();

    /**
     * Inicializa las llaves VAPID y restaura las suscripciones almacenadas al arrancar el servicio.
     */
    @PostConstruct
    void init() {
        if (configuredPublicKey.filter(key -> !key.isBlank()).isPresent()
                && configuredPrivateKey.filter(key -> !key.isBlank()).isPresent()) {
            vapidPublicKey = configuredPublicKey.get().trim();
            vapidPrivateKey = configuredPrivateKey.get().trim();
        } else {
            generarLlavesVapid();
            LOG.warn("Se generaron llaves VAPID temporales. Configura VAPID_PUBLIC_KEY y VAPID_PRIVATE_KEY en producción.");
        }

        restaurarSuscripciones();
    }

    /**
     * Genera un par de llaves criptográficas VAPID sobre la curva elíptica P-256 (secp256r1).
     */
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

    /**
     * Devuelve la llave pública VAPID en formato Base64URL.
     *
     * @return llave pública VAPID
     */
    public String getVapidPublicKey() {
        return vapidPublicKey;
    }

    /**
     * Devuelve la llave privada VAPID en formato Base64URL.
     *
     * @return llave privada VAPID
     */
    public String getVapidPrivateKey() {
        return vapidPrivateKey;
    }

    /**
     * Registra y persiste una suscripción Web Push asociada a un usuario.
     *
     * @param request datos de la suscripción del navegador
     */
    public void registrarSuscripcion(PushSubscriptionRequest request) {
        String username = request.usuario().trim();
        PushSubscriptionRequest normalizedRequest = new PushSubscriptionRequest(
                username,
                request.endpoint().trim(),
                request.keys()
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

    /**
     * Elimina una suscripción Web Push específica de un usuario en memoria y base de datos.
     *
     * @param usuario  nombre de usuario
     * @param endpoint URL del endpoint Push a eliminar
     * @return true si la suscripción fue eliminada
     */
    public boolean eliminarSuscripcion(String usuario, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }

        String normalizedUsername = usuario.trim();
        String normalizedEndpoint = endpoint.trim();
        Map<String, PushSubscriptionRequest> userSubscriptions = suscripciones.get(normalizedUsername);
        boolean removed = userSubscriptions != null && userSubscriptions.remove(normalizedEndpoint) != null;
        if (userSubscriptions != null && userSubscriptions.isEmpty()) {
            suscripciones.remove(normalizedUsername, userSubscriptions);
        }
        boolean removedFromDatabase = pushSubscriptionRepository.eliminar(normalizedUsername, normalizedEndpoint);
        return removed || removedFromDatabase;
    }

    /**
     * Notifica vía Web Push a todos los seguidores del autor cuando publica un nuevo post.
     *
     * @param autor  username del autor de la publicación
     * @param idPost identificador de la publicación
     * @param texto  contenido de la publicación
     * @return lista de notificaciones emitidas
     */
    public List<PushNotificationPayload> notificarSeguidoresNuevaPublicacion(String autor, String idPost, String texto) {
        List<String> seguidores = postRepository.obtenerSeguidoresDeAutor(autor);
        List<PushNotificationPayload> emitidos = new ArrayList<>();

        String titulo = "Nueva publicación de @" + autor;
        String ahora = Instant.now().toString();

        for (String seguidor : seguidores) {
            enviarNotificacionAUsuario(seguidor, idPost, autor, titulo, texto, ahora, emitidos);
        }

        return emitidos;
    }

    /**
     * Emite una notificación Web Push al autor de una publicación cuando recibe un nuevo LIKE.
     *
     * @param autorPost           username del autor de la publicación
     * @param usuarioQueReacciona username del usuario que dio LIKE
     * @param idPost              identificador de la publicación
     * @return lista de notificaciones emitidas al autor
     */
    public List<PushNotificationPayload> notificarLikePublicacion(String autorPost, String usuarioQueReacciona, String idPost) {
        List<PushNotificationPayload> emitidos = new ArrayList<>();
        String titulo = "Nuevo Like en tu publicación";
        String mensaje = "@" + usuarioQueReacciona.trim() + " reaccionó con LIKE a tu publicación";
        String ahora = Instant.now().toString();

        enviarNotificacionAUsuario(
                autorPost.trim(),
                idPost.trim(),
                usuarioQueReacciona.trim(),
                titulo,
                mensaje,
                ahora,
                emitidos
        );

        return emitidos;
    }

    /**
     * Obtiene el historial de notificaciones Push almacenadas en la bandeja de un usuario.
     *
     * @param usuario nombre de usuario
     * @return lista de notificaciones del usuario
     */
    public List<PushNotificationPayload> obtenerNotificacionesDeUsuario(String usuario) {
        return bandejaPush.getOrDefault(usuario.trim(), List.of());
    }

    /**
     * Restaura desde Neo4j las suscripciones Web Push registradas previamente.
     */
    void restaurarSuscripciones() {
        try {
            for (PushSubscriptionRequest subscription : pushSubscriptionRepository.obtenerTodas()) {
                suscripciones.computeIfAbsent(
                        subscription.usuario(),
                        ignored -> new ConcurrentHashMap<>()
                ).put(subscription.endpoint(), subscription);
            }
        } catch (Exception exception) {
            LOG.warnf("No se pudieron restaurar las suscripciones Web Push: %s", exception.getMessage());
        }
    }

    /**
     * Envía una notificación Push a todas las suscripciones activas de un destinatario y actualiza su bandeja.
     *
     * @param destinatario usuario que recibe la notificación
     * @param idPost       identificador de la publicación relacionada
     * @param autorEvento  usuario que originó el evento
     * @param titulo       título de la notificación
     * @param texto        cuerpo del mensaje
     * @param ahora        marca de tiempo ISO-8601
     * @param emitidos     lista acumuladora de payloads emitidos
     */
    private void enviarNotificacionAUsuario(
            String destinatario,
            String idPost,
            String autorEvento,
            String titulo,
            String texto,
            String ahora,
            List<PushNotificationPayload> emitidos
    ) {
        List<PushSubscriptionRequest> userSubscriptions = new ArrayList<>(
                suscripciones.getOrDefault(destinatario, Map.of()).values()
        );
        if (userSubscriptions.isEmpty()) {
            PushNotificationPayload pending = crearPayload(
                    idPost, autorEvento, destinatario, titulo, texto,
                    "webpush://pendiente/" + destinatario, ahora
            );
            bandejaPush.computeIfAbsent(destinatario, ignored -> new CopyOnWriteArrayList<>()).add(pending);
            emitidos.add(pending);
            return;
        }

        PushNotificationPayload inboxPayload = crearPayload(
                idPost, autorEvento, destinatario, titulo, texto,
                userSubscriptions.get(0).endpoint(), ahora
        );
        for (PushSubscriptionRequest subscription : userSubscriptions) {
            PushNotificationPayload payload = crearPayload(
                    idPost, autorEvento, destinatario, titulo, texto,
                    subscription.endpoint(), ahora
            );
            emitidos.add(payload);
            try {
                int statusCode = webPushGateway.enviar(
                        subscription,
                        payload,
                        vapidPublicKey,
                        vapidPrivateKey
                );
                if (statusCode >= 200 && statusCode < 300) {
                    LOG.infof("Web Push enviado a %s (%s)", destinatario, subscription.endpoint());
                } else if (statusCode == 404 || statusCode == 410) {
                    eliminarSuscripcion(destinatario, subscription.endpoint());
                    LOG.infof("Se eliminó una suscripción Web Push vencida de %s", destinatario);
                } else {
                    LOG.warnf("El servicio Push respondió %d para %s", statusCode, destinatario);
                }
            } catch (Exception exception) {
                LOG.warnf("No se pudo enviar Web Push a %s: %s", destinatario, exception.getMessage());
            }
        }
        bandejaPush.computeIfAbsent(destinatario, ignored -> new CopyOnWriteArrayList<>()).add(inboxPayload);
    }

    /**
     * Normaliza los bytes de la llave privada EC a un arreglo fijo de 32 bytes.
     *
     * @param encodedKey bytes originales de la llave privada
     * @return arreglo normalizado de 32 bytes
     */
    private static byte[] normalizarLlavePrivada(byte[] encodedKey) {
        byte[] normalizedKey = new byte[32];
        int sourceStart = Math.max(0, encodedKey.length - normalizedKey.length);
        int copyLength = Math.min(encodedKey.length, normalizedKey.length);
        System.arraycopy(encodedKey, sourceStart, normalizedKey, normalizedKey.length - copyLength, copyLength);
        return normalizedKey;
    }

    /**
     * Construye una instancia de PushNotificationPayload.
     *
     * @param idPost    identificador de la publicación
     * @param autor     usuario emisor del evento
     * @param seguidor  usuario destinatario
     * @param titulo    título de la notificación
     * @param texto     mensaje de la notificación
     * @param endpoint  endpoint de destino
     * @param timestamp fecha y hora del evento
     * @return instancia de PushNotificationPayload
     */
    private PushNotificationPayload crearPayload(
            String idPost,
            String autor,
            String seguidor,
            String titulo,
            String texto,
            String endpoint,
            String timestamp
    ) {
        return new PushNotificationPayload(
                idPost,
                autor,
                seguidor,
                titulo,
                texto,
                endpoint,
                timestamp
        );
    }
}