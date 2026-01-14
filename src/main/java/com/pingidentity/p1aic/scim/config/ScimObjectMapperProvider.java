package com.pingidentity.p1aic.scim.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;

/**
 * Custom Jackson ObjectMapper configuration for SCIM 2.0 compliance.
 *
 * This provider ensures all JAX-RS endpoints use a consistently configured
 * ObjectMapper that adheres to SCIM 2.0 requirements.
 *
 * SCIM 2.0 COMPLIANCE (RFC 7643 Section 2.5):
 * "Unassigned attributes, the null value, or an empty array (in the case of
 * a multi-valued attribute) SHALL be represented by the absence of the
 * attribute in the JSON representation."
 *
 * This means null values must NEVER be serialized in SCIM responses.
 *
 * Usage: Register this provider with your JAX-RS application by either:
 * 1. Adding @Provider annotation (auto-discovered if package scanning enabled)
 * 2. Explicitly registering in ResourceConfig: register(ScimObjectMapperProvider.class)
 */
@Provider
public class ScimObjectMapperProvider implements ContextResolver<ObjectMapper> {

    private final ObjectMapper objectMapper;

    /**
     * Constructor configures ObjectMapper for SCIM 2.0 compliance.
     */
    public ScimObjectMapperProvider() {
        this.objectMapper = new ObjectMapper();

        // BEGIN: SCIM 2.0 Compliance - Exclude null values (RFC 7643 Section 2.5)
        // Null values must be omitted from JSON responses, not serialized as "null"
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // END: SCIM 2.0 Compliance

        // Don't fail on unknown properties (forward compatibility)
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Write dates as ISO-8601 strings, not timestamps
        // SCIM uses ISO 8601 format: "2024-01-15T10:30:00Z"
        this.objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        // Register Java 8 date/time module for proper DateTime handling
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Returns the configured ObjectMapper for the given type.
     *
     * @param type the class type being serialized/deserialized
     * @return the configured ObjectMapper instance
     */
    @Override
    public ObjectMapper getContext(Class<?> type) {
        return objectMapper;
    }

    /**
     * Get the configured ObjectMapper for direct usage.
     *
     * Useful when you need to use the same ObjectMapper configuration
     * outside of JAX-RS context (e.g., in utility classes).
     *
     * @return the configured ObjectMapper instance
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}