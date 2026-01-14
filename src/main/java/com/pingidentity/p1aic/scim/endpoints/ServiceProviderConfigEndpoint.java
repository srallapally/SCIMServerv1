package com.pingidentity.p1aic.scim.endpoints;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.schema.ScimSchemaUrns;
// BEGIN: Added UriInfo import for dynamic URL resolution
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
// END: Added UriInfo import for dynamic URL resolution
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
 *
 * FIX: Removed GenericScimResource wrapper to avoid null attributes (id, externalId)
 * being added to the response. SCIM 2.0 (RFC 7643) forbids null attribute values.
 * ServiceProviderConfig is a singleton resource and does not require id/externalId.
 *
 * FIX: Added UriInfo to dynamically resolve meta.location from the actual request URL.
 * This ensures the location matches the public URL used by the client, even behind
 * proxies, load balancers, or Cloud Run.
 *
 * FIX: Added required 'id' attribute per RFC 7643 Section 3.1.
 */
@Path("/ServiceProviderConfig")
@Produces({"application/scim+json", "application/json"})
public class ServiceProviderConfigEndpoint {

    private static final Logger LOGGER = Logger.getLogger(ServiceProviderConfigEndpoint.class.getName());

    // BEGIN: Fixed ID for ServiceProviderConfig singleton resource per RFC 7643
    private static final String SERVICE_PROVIDER_CONFIG_ID = "ServiceProviderConfig";
    // END: Fixed ID for ServiceProviderConfig singleton resource per RFC 7643

    private final ObjectMapper objectMapper;

    /**
     * Constructor initializes ObjectMapper and configuration.
     */
    public ServiceProviderConfigEndpoint() {
        // BEGIN: Configure ObjectMapper to exclude null values per RFC 7643
        this.objectMapper = new ObjectMapper();
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // END: Configure ObjectMapper to exclude null values per RFC 7643
    }

    /**
     * Get the Service Provider Configuration.
     *
     * GET /ServiceProviderConfig
     *
     * @param uriInfo injected by JAX-RS to get the actual request URI
     * @return the service provider configuration
     */
    @GET
    // BEGIN: Added UriInfo parameter to get actual request URL for meta.location
    public Response getServiceProviderConfig(@Context UriInfo uriInfo) {
        // END: Added UriInfo parameter to get actual request URL for meta.location

        // BEGIN: Removed try-catch - ScimExceptionMapper handles exceptions globally
        LOGGER.info("Getting service provider configuration");

        // BEGIN: Return ObjectNode directly instead of GenericScimResource
        // This avoids the SDK adding null id/externalId attributes
        ObjectNode spConfig = buildServiceProviderConfig(uriInfo);

        return Response.ok(spConfig).build();
        // END: Return ObjectNode directly instead of GenericScimResource
        // END: Removed try-catch - ScimExceptionMapper handles exceptions globally
    }

    /**
     * Build the Service Provider Configuration.
     *
     * Returns ObjectNode directly to avoid GenericScimResource adding
     * null values for id, externalId which violates RFC 7643.
     *
     * @param uriInfo used to construct the canonical meta.location URL
     */
    // BEGIN: Added UriInfo parameter
    private ObjectNode buildServiceProviderConfig(UriInfo uriInfo) {
        // END: Added UriInfo parameter
        ObjectNode spConfig = objectMapper.createObjectNode();

        // Add schemas
        ArrayNode schemas = objectMapper.createArrayNode();
        schemas.add(ScimSchemaUrns.SERVICE_PROVIDER_CONFIG);
        spConfig.set("schemas", schemas);

        // BEGIN: Added required 'id' attribute per RFC 7643 Section 3.1
        // All SCIM resources, including singleton config resources, MUST have an id
        spConfig.put("id", SERVICE_PROVIDER_CONFIG_ID);
        // END: Added required 'id' attribute per RFC 7643 Section 3.1

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
        // BEGIN: Use UriInfo to get the actual request URL for meta.location
        // This ensures the location matches the public URL used by the client,
        // including the correct hostname and /scim/v2 path prefix.
        // uriInfo.getAbsolutePath() returns the full URI used in the request.
        String location = uriInfo.getAbsolutePath().toString();
        meta.put("location", location);
        // END: Use UriInfo to get the actual request URL for meta.location
        spConfig.set("meta", meta);
        return spConfig;
    }
}