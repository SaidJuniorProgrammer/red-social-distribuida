package com.redsocial.dto;

public record ApiErrorResponse(String code, String field, String message) {
}
