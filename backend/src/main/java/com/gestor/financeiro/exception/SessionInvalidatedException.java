package com.gestor.financeiro.exception;

public class SessionInvalidatedException extends RuntimeException {

    public SessionInvalidatedException(String message) {
        super(message);
    }
}