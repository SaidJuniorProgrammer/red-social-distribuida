package com.redsocial.dto;

public record SugerenciaResponse(
        String recomendado,
        long conexiones_en_comun
) {
}