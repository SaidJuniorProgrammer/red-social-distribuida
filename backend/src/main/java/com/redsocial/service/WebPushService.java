package com.redsocial.service;

import com.redsocial.dto.PushNotificationPayload;
import com.redsocial.dto.PushSubscriptionRequest;
import com.redsocial.repository.PostRepository;
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

@ApplicationScoped
public class WebPushService {

    private static final Logger LOG = Logger.getLogger(WebPushService.class);

    @Inject
    PostRepository postRepository;

    @Inject
    WebPushGateway webPushGateway;

    @ConfigProperty(name = "webpush.vapid.public-key")
    Optional<String> configuredPublicKey;

    @ConfigProperty(name = "webpush.vapid.private-key")
    Optional<String> configuredPrivateKey;

    private String vapidPublicKey;
    private String vapidPrivateKey;

    private final Map<String, Map<String, PushSubscriptionRequest>> suscripciones = new ConcurrentHashMap<>();
    private final Map<String, List<PushNotificationPayload>> bandejaPush = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        if (configuredPublicKey.filter(key -> !key.isBlank()).isPresent()
                && configuredPrivateKey.filter(key -> !key.isBlank()).isPresent()) {
            vapidPublicKey = configuredPublicKey.get().trim();
            vapidPrivateKey = configuredPrivateKey.get().trim();
            return;
        }

        generarLlavesVapid();
        LOG.warn("Se generaron llaves VAPID temporales. Configura VAPID_PUBLIC_KEY y VAPID_PRIVATE_KEY en producción.");
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
        suscripciones.forEach((registeredUser, userSubscriptions) -> {
            if (!registeredUser.equals(username)) {
                userSubscriptions.remove(request.endpoint());
            }
        });
        suscripciones.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        suscripciones.computeIfAbsent(username, ignored -> new ConcurrentHashMap<>())
                .put(request.endpoint(), request);
    }

    public boolean eliminarSuscripcion(String usuario, String endpoint) {
        Map<String, PushSubscriptionRequest> userSubscriptions = suscripciones.get(usuario.trim());
        if (userSubscriptions == null || endpoint == null || endpoint.isBlank()) {
            return false;
        }

        boolean removed = userSubscriptions.remove(endpoint.trim()) != null;
        if (userSubscriptions.isEmpty()) {
            suscripciones.remove(usuario.trim(), userSubscriptions);
        }
        return removed;
    }

    public List<PushNotificationPayload> notificarSeguidoresNuevaPublicacion(String autor, String idPost, String texto) {
        List<String> seguidores = postRepository.obtenerSeguidoresDeAutor(autor);
        List<PushNotificationPayload> emitidos = new ArrayList<>();

        String titulo = "Nueva publicación de @" + autor;
        String ahora = Instant.now().toString();

        for (String seguidor : seguidores) {
            List<PushSubscriptionRequest> userSubscriptions = new ArrayList<>(
                    suscripciones.getOrDefault(seguidor, Map.of()).values()
            );
            if (userSubscriptions.isEmpty()) {
                PushNotificationPayload pending = crearPayload(
                        idPost, autor, seguidor, titulo, texto,
                        "webpush://pendiente/" + seguidor, ahora
                );
                bandejaPush.computeIfAbsent(seguidor, ignored -> new CopyOnWriteArrayList<>()).add(pending);
                emitidos.add(pending);
                continue;
            }

            PushNotificationPayload inboxPayload = crearPayload(
                    idPost, autor, seguidor, titulo, texto,
                    userSubscriptions.get(0).endpoint(), ahora
            );
            for (PushSubscriptionRequest subscription : userSubscriptions) {
                PushNotificationPayload payload = crearPayload(
                        idPost, autor, seguidor, titulo, texto,
                        subscription.endpoint(), ahora
                );
                emitidos.add(payload);
                try {
                    webPushGateway.enviar(subscription, payload, vapidPublicKey, vapidPrivateKey);
                    LOG.infof("Web Push enviado a %s (%s)", seguidor, subscription.endpoint());
                } catch (Exception exception) {
                    LOG.warnf("No se pudo enviar Web Push a %s: %s", seguidor, exception.getMessage());
                }
            }
            bandejaPush.computeIfAbsent(seguidor, ignored -> new CopyOnWriteArrayList<>()).add(inboxPayload);
        }

        return emitidos;
    }

    public List<PushNotificationPayload> obtenerNotificacionesDeUsuario(String usuario) {
        return bandejaPush.getOrDefault(usuario.trim(), List.of());
    }

    private static byte[] normalizarLlavePrivada(byte[] encodedKey) {
        byte[] normalizedKey = new byte[32];
        int sourceStart = Math.max(0, encodedKey.length - normalizedKey.length);
        int copyLength = Math.min(encodedKey.length, normalizedKey.length);
        System.arraycopy(encodedKey, sourceStart, normalizedKey, normalizedKey.length - copyLength, copyLength);
        return normalizedKey;
    }

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
