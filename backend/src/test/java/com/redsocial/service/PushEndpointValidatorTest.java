package com.redsocial.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushEndpointValidatorTest {

    private InetAddress[] resolvedAddresses;
    private PushEndpointValidator validator;

    @BeforeEach
    void configurarValidador() throws Exception {
        resolvedAddresses = new InetAddress[]{InetAddress.getByAddress(new byte[]{8, 8, 8, 8})};
        validator = new PushEndpointValidator() {
            @Override
            protected InetAddress[] resolver(String host) {
                return resolvedAddresses;
            }
        };
        validator.allowedHosts = List.of("fcm.googleapis.com", "*.notify.windows.com");
    }

    @Test
    void aceptaSoloServiciosPushHttpsPermitidosConDireccionesPublicas() {
        assertTrue(validator.esSeguro("https://fcm.googleapis.com/fcm/send/suscripcion"));
        assertTrue(validator.esSeguro("https://wns2.notify.windows.com/w/token"));

        assertFalse(validator.esSeguro("http://fcm.googleapis.com/fcm/send/suscripcion"));
        assertFalse(validator.esSeguro("https://servicio-no-permitido.example/push"));
        assertFalse(validator.esSeguro("https://usuario@fcm.googleapis.com/push"));
        assertFalse(validator.esSeguro("https://fcm.googleapis.com:8443/push"));
    }

    @Test
    void rechazaDestinosLocalesAunqueUsenUnNombrePermitido() throws Exception {
        resolvedAddresses = new InetAddress[]{InetAddress.getByAddress(new byte[]{127, 0, 0, 1})};

        assertFalse(validator.esSeguro("https://fcm.googleapis.com/fcm/send/suscripcion"));
    }
}
