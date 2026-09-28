package com.redsocial;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api")
public class HealthController {

    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public String checkHealth() {
        return "{\"status\":\"ok\"}";
    }
}