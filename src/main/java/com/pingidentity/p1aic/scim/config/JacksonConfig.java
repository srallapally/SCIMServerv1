package com.pingidentity.p1aic.scim.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;

/**
 * Jackson ObjectMapper configuration for SCIM 2.0 compliance.
 *
 * <p>This configuration ensures:</p>
 * <ul>
 *   <li>Null fields are excluded from JSON output (NON_NULL inclusion)</li>
 *   <li>Unknown properties are ignored during deserialization</li>
 *   <li>Empty arrays/objects can be optionally excluded</li>
 * </ul>
 *
 * <p>Per RFC 7644 Section 3.4.2, the ListResponse should only contain:</p>
 * <ul>
 *   <li>schemas (REQUIRED)</li>
 *   <li>totalResults (REQUIRED)</li>
 *   <li>Resources (REQUIRED when returning resources)</li>
 *   <li>startIndex (OPTIONAL)</li>
 *   <li>itemsPerPage (OPTIONAL)</li>
 * </ul>
 *
 * <p>By excluding null fields, we prevent the UnboundID SDK's BaseScimResource
 * fields (id, externalId, meta) from appearing in ListResponse output.</p>
 */
@Provider
public class JacksonConfig implements ContextResolver<ObjectMapper> {

    private final ObjectMapper objectMapper;

    public JacksonConfig() {
        objectMapper = new ObjectMapper();

        // BEGIN: SCIM 2.0 compliance - exclude null fields
        // This prevents "id": null, "externalId": null, "meta": null from appearing
        // in ListResponse output, which violates RFC 7644 Section 3.4.2
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // END: SCIM 2.0 compliance

        // Ignore unknown properties during deserialization (defensive coding)
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Don't fail on empty beans
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    @Override
    public ObjectMapper getContext(Class<?> type) {
        return objectMapper;
    }

    /**
     * Get the configured ObjectMapper instance.
     * Useful for manual serialization/deserialization.
     *
     * @return the configured ObjectMapper
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}