package com.network.monitor.common.exception;

public class AuthRequiredException extends RuntimeException {
    
    public AuthRequiredException(String message) {
        super(message);
    }
    
    public AuthRequiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
