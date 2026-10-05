package com.redsocial.service;

import com.redsocial.dto.PushSubscriptionRequest;
import com.redsocial.repository.PostRepository;
import com.redsocial.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebPushServiceTest {

    @Test
    void restauraLasSuscripcionesGuardadasAlIniciar() {
        PushSubscriptionRequest savedSubscription = subscription("https://fcm.googleapis.com/fcm/send/guardada");
        InMemoryPushSubscriptionRepository repository = new InMemoryPushSubscriptionRepository(savedSubscription);
        List<String> deliveredEndpoints = new ArrayList<>();
        WebPushService service = service(repository, (subscription, payload, publicKey, privateKey) -> {
            deliveredEndpoints.add(subscription.endpoint());
            return 201;
        });

        service.init();
        service.notificarSeguidoresNuevaPublicacion("autor", "post-1", "Nueva publicación");

        assertEquals(List.of(savedSubscription.endpoint()), deliveredEndpoints);
    }

    @Test
    void eliminaUnaSuscripcionCuandoElServicioIndicaQueVencio() {
        PushSubscriptionRequest expiredSubscription = subscription("https://fcm.googleapis.com/fcm/send/vencida");
        InMemoryPushSubscriptionRepository repository = new InMemoryPushSubscriptionRepository(expiredSubscription);
        WebPushService service = service(repository, (subscription, payload, publicKey, privateKey) -> 410);

        service.init();
        service.notificarSeguidoresNuevaPublicacion("autor", "post-1", "Nueva publicación");

        assertTrue(repository.subscriptions.isEmpty());
        assertEquals(List.of("usuario:https://fcm.googleapis.com/fcm/send/vencida"), repository.deletedSubscriptions);
    }

    private WebPushService service(
            PushSubscriptionRepository repository,
            WebPushGateway gateway
    ) {
        WebPushService service = new WebPushService();
        service.postRepository = new PostRepository() {
            @Override
            public List<String> obtenerSeguidoresDeAutor(String autor) {
                return List.of("usuario");
            }
        };
        service.pushSubscriptionRepository = repository;
        service.webPushGateway = gateway;
        service.configuredPublicKey = Optional.of("publica");
        service.configuredPrivateKey = Optional.of("privada");
        return service;
    }

    private PushSubscriptionRequest subscription(String endpoint) {
        return new PushSubscriptionRequest(
                "usuario",
                endpoint,
                Map.of("p256dh", "llave-publica", "auth", "secreto")
        );
    }

    private static class InMemoryPushSubscriptionRepository extends PushSubscriptionRepository {

        private final List<PushSubscriptionRequest> subscriptions = new ArrayList<>();
        private final List<String> deletedSubscriptions = new ArrayList<>();

        private InMemoryPushSubscriptionRepository(PushSubscriptionRequest... initialSubscriptions) {
            subscriptions.addAll(List.of(initialSubscriptions));
        }

        @Override
        public void guardar(PushSubscriptionRequest subscription) {
            subscriptions.removeIf(existing -> existing.endpoint().equals(subscription.endpoint()));
            subscriptions.add(subscription);
        }

        @Override
        public boolean eliminar(String usuario, String endpoint) {
            deletedSubscriptions.add(usuario + ":" + endpoint);
            return subscriptions.removeIf(subscription ->
                    subscription.usuario().equals(usuario) && subscription.endpoint().equals(endpoint)
            );
        }

        @Override
        public List<PushSubscriptionRequest> obtenerTodas() {
            return List.copyOf(subscriptions);
        }
    }
}
