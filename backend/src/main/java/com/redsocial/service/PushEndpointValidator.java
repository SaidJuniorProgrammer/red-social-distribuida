package com.redsocial.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class PushEndpointValidator {

    @ConfigProperty(name = "webpush.allowed-hosts")
    List<String> allowedHosts;

    public boolean esSeguro(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(endpoint.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return false;
            }

            String normalizedHost = IDN.toASCII(host).toLowerCase(Locale.ROOT);
            if (allowedHosts.stream().noneMatch(allowedHost -> coincide(normalizedHost, allowedHost))) {
                return false;
            }

            InetAddress[] addresses = resolver(normalizedHost);
            return addresses.length > 0 && Arrays.stream(addresses).allMatch(this::esDireccionPublica);
        } catch (IllegalArgumentException | UnknownHostException | SecurityException exception) {
            return false;
        }
    }

    protected InetAddress[] resolver(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }

    private boolean coincide(String host, String configuredHost) {
        String pattern = configuredHost.trim().toLowerCase(Locale.ROOT);
        if (pattern.startsWith("*.")) {
            String suffix = pattern.substring(1);
            return host.endsWith(suffix) && host.length() > suffix.length();
        }
        return host.equals(pattern);
    }

    private boolean esDireccionPublica(InetAddress address) {
        return !address.isAnyLocalAddress()
                && !address.isLoopbackAddress()
                && !address.isLinkLocalAddress()
                && !address.isSiteLocalAddress()
                && !address.isMulticastAddress();
    }
}
