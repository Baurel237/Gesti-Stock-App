package com.stock.api.service;

/**
 * Levée quand un refresh token est absent, invalide ou expiré.
 * Mappée en 401 par GlobalExceptionHandler via AuthenticationException.
 */
public class AuthRefreshException extends org.springframework.security.core.AuthenticationException {

    public AuthRefreshException(String msg) {
        super(msg);
    }
}
