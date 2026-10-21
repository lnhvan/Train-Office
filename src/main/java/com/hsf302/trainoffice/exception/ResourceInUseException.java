package com.hsf302.trainoffice.exception;

public class ResourceInUseException extends IllegalStateException {
    public ResourceInUseException(String message) {
        super(message);
    }

    public ResourceInUseException(String message, Throwable cause) {
        super(message, cause);
    }
}
