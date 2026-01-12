package com.pingidentity.p1aic.scim.endpoints;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.config.ScimServerConfig;
import com.pingidentity.p1aic.scim.schema.ScimSchemaUrns;
import com.unboundid.scim2.common.GenericScimResource;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.util.logging.Logger;

/**
 * JAX-RS endpoint for SCIM 2.0 Service Provider Configuration.
 *
 * Provides metadata about the SCIM service provider's capabilities,
 * supported features, and authentication schemes.
 *
 * Path: /scim/v2/ServiceProviderConfig
 *
 * REFACTORED: Removed try-catch blocks - ScimExceptionMapper handles all exceptions globally
 * Note: This endpoint typically doesn't throw ScimException but kept consistent with other endpoints
 */
@Path("/ServiceProviderConfig")
@Produces({"application/scim+json", "application/json"})
public class ServiceProviderConfigEndpoint {

    private static final Logger LOGGER = Logger.getLogger(ServiceProviderConfigEndpoint.class.getName());

    private final ObjectMapper objectMapper;
    private final ScimServerConfig config;

    /**
     * Constructor initializes ObjectMapper and configuration.
     */
    public ServiceProviderConfigEndpoint() {
        this.objectMapper = new ObjectMapper();
        this.config = ScimServerConfig.getInstance();
    }

    /**
     * Get the Service Provider Configuration.
     *
     * GET /ServiceProviderConfig
     *
     * @return the service provider configuration
     */
    @GET
    public Response getServiceProviderConfig() {

        // BEGIN: Removed try-catch - ScimExceptionMapper handles exceptions globally
        LOGGER.info("Getting service provider configuration");

        GenericScimResource spConfig = buildServiceProviderConfig();

        return Response.ok(spConfig).build();
        // END: Removed try-catch - ScimExceptionMapper handles exceptions globally
    }

    /**
     * Build the Service Provider Configuration.
     */
    private GenericScimResource buildServiceProviderConfig() {
        ObjectNode spConfig = objectMapper.createObjectNode();

        // Add schemas
        ArrayNode schemas = objectMapper.createArrayNode();
        schemas.add(ScimSchemaUrns.SERVICE_PROVIDER_CONFIG);
        spConfig.set("schemas", schemas);

        // Documentation URI (optional)
        spConfig.put("documentationUri", "https://docs.pingidentity.com");

        // Patch support
        ObjectNode patch = objectMapper.createObjectNode();
        patch.put("supported", true);
        spConfig.set("patch", patch);

        // Bulk operations support
        ObjectNode bulk = objectMapper.createObjectNode();
        bulk.put("supported", false); // Not implemented yet
        bulk.put("maxOperations", 0);
        bulk.put("maxPayloadSize", 0);
        spConfig.set("bulk", bulk);

        // Filter support
        ObjectNode filter = objectMapper.createObjectNode();
        filter.put("supported", true);
        filter.put("maxResults", 1000);
        spConfig.set("filter", filter);

        // Change password support
        ObjectNode changePassword = objectMapper.createObjectNode();
        changePassword.put("supported", true);
        spConfig.set("changePassword", changePassword);

        // Sort support
        ObjectNode sort = objectMapper.createObjectNode();
        sort.put("supported", false); // Not implemented yet
        spConfig.set("sort", sort);

        // ETag support (for optimistic concurrency control)
        ObjectNode etag = objectMapper.createObjectNode();
        etag.put("supported", true);
        spConfig.set("etag", etag);

        // Authentication schemes
        ArrayNode authSchemes = objectMapper.createArrayNode();

        // OAuth 2.0 Bearer Token
        ObjectNode oauth = objectMapper.createObjectNode();
        oauth.put("type", "oauthbearertoken");
        oauth.put("name", "OAuth 2.0 Bearer Token");
        oauth.put("description", "Authentication scheme using OAuth 2.0 Bearer Token");
        oauth.put("specUri", "https://tools.ietf.org/html/rfc6750");
        oauth.put("documentationUri", "https://docs.pingidentity.com");
        oauth.put("primary", true);
        authSchemes.add(oauth);

        spConfig.set("authenticationSchemes", authSchemes);

        // Add meta
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("resourceType", "ServiceProviderConfig");
        String location = config.getScimServerBaseUrl() + "/ServiceProviderConfig";
        meta.put("location", location);
        spConfig.set("meta", meta);

        return new GenericScimResource(spConfig);
    }

    // BEGIN: Removed buildErrorResponse and escapeJson methods - no longer needed
    // ScimExceptionMapper handles all error response formatting
    // END: Removed buildErrorResponse and escapeJson methods
}