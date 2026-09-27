package com.redsocial;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

@Path("/api/health")
public class HealthController {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> checkHealth() {
        return Map.of("status", "ok");
    }
}