package com.redsocial.exception;

public class RegistrationValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;

    public RegistrationValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
