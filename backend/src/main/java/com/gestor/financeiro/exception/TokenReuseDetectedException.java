package com.gestor.financeiro.exception;

public class TokenReuseDetectedException extends RuntimeException {

    public TokenReuseDetectedException(String message) {
        super(message);
    }
}
