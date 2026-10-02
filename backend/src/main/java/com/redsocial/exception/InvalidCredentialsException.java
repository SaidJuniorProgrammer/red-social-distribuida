package com.redsocial.exception;

public class InvalidCredentialsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException() {
        super("Usuario o contraseña incorrectos.");
    }
}
