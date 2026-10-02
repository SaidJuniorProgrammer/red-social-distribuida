package com.redsocial.exception;

public class RegistrationConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final String field;

    public RegistrationConflictException(String code, String field, String message) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public String getCode() {
        return code;
    }

    public String getField() {
        return field;
    }
}
