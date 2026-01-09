package com.pingidentity.p1aic.scim.exceptions;

/**
 * Exception thrown when a SCIM filter cannot be translated to PingIDM query filter syntax.
 *
 * This typically occurs when:
 * - The SCIM filter syntax is invalid
 * - An unsupported operator is used
 * - An attribute cannot be mapped
 * - The filter expression is malformed
 */
public class FilterTranslationException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new FilterTranslationException with no detail message.
     */
    public FilterTranslationException() {
        super();
    }

    /**
     * Constructs a new FilterTranslationException with the specified detail message.
     *
     * @param message the detail message
     */
    public FilterTranslationException(String message) {
        super(message);
    }

    /**
     * Constructs a new FilterTranslationException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public FilterTranslationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new FilterTranslationException with the specified cause.
     *
     * @param cause the cause of this exception
     */
    public FilterTranslationException(Throwable cause) {
        super(cause);
    }
}