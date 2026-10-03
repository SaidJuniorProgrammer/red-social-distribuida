package com.redsocial.service;

import com.redsocial.dto.PushNotificationPayload;
import com.redsocial.dto.PushSubscriptionRequest;
import com.redsocial.repository.PostRepository;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class WebPushService {

    private static final Logger LOG = Logger.getLogger(WebPushService.class);

    @Inject
    PostRepository postRepository;

    private String vapidPublicKey;
    private String vapidPrivateKey;

    private final Map<String, PushSubscriptionRequest> suscripciones = new ConcurrentHashMap<>();
    private final Map<String, List<PushNotificationPayload>> bandejaPush = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        generarLlavesVapid();
    }

    public void generarLlavesVapid() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair keyPair = keyGen.generateKeyPair();

            byte[] pubEncoded = keyPair.getPublic().getEncoded();
            byte[] uncompressedPoint = Arrays.copyOfRange(pubEncoded, pubEncoded.length - 65, pubEncoded.length);

            ECPrivateKey privKey = (ECPrivateKey) keyPair.getPrivate();
            byte[] privBytes = privKey.getS().toByteArray();

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
        suscripciones.put(request.usuario().trim(), request);
    }

    public boolean eliminarSuscripcion(String usuario) {
        return suscripciones.remove(usuario.trim()) != null;
    }

    public List<PushNotificationPayload> notificarSeguidoresNuevaPublicacion(String autor, String idPost, String texto) {
        List<String> seguidores = postRepository.obtenerSeguidoresDeAutor(autor);
        List<PushNotificationPayload> emitidos = new ArrayList<>();

        String titulo = "Nueva publicación de @" + autor;
        String ahora = Instant.now().toString();

        for (String seguidor : seguidores) {
            PushSubscriptionRequest sub = suscripciones.get(seguidor);
            String endpointDestino = (sub != null && sub.endpoint() != null) ? sub.endpoint() : "webpush://pendiente/" + seguidor;

            PushNotificationPayload payload = new PushNotificationPayload(
                    idPost,
                    autor,
                    seguidor,
                    titulo,
                    texto,
                    endpointDestino,
                    ahora
            );

            bandejaPush.computeIfAbsent(seguidor, k -> new CopyOnWriteArrayList<>()).add(payload);
            emitidos.add(payload);
            LOG.infof("Evento Web Push emitido a seguidor %s (%s)", seguidor, endpointDestino);
        }

        return emitidos;
    }

    public List<PushNotificationPayload> obtenerNotificacionesDeUsuario(String usuario) {
        return bandejaPush.getOrDefault(usuario.trim(), List.of());
    }
}