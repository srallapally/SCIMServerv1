package com.pingidentity.p1aic.scim.endpoints;

import com.pingidentity.p1aic.scim.schema.DynamicSchemaManager;
import com.pingidentity.p1aic.scim.schema.ScimSchemaUrns;
import com.unboundid.scim2.common.GenericScimResource;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException;
import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.messages.ListResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.logging.Logger;

/**
 * JAX-RS endpoint for SCIM 2.0 Schema discovery.
 *
 * Provides access to SCIM schema definitions that describe the structure
 * and attributes of User and Group resources.
 *
 * Path: /scim/v2/Schemas
 *
 * REFACTORED: Removed try-catch blocks - ScimExceptionMapper handles all exceptions globally
 */
@Path("/Schemas")
@Produces("application/scim+json")
public class SchemasEndpoint {

    private static final Logger LOGGER = Logger.getLogger(SchemasEndpoint.class.getName());

    @Inject
    private DynamicSchemaManager schemaManager;

    /**
     * Get all SCIM schemas.
     *
     * GET /Schemas
     *
     * @param startIndex 1-based start index for pagination (optional)
     * @param count number of results to return (optional)
     * @return ListResponse containing all schemas
     */
    @GET
    public Response getAllSchemas(
            @QueryParam("startIndex") Integer startIndex,
            @QueryParam("count") Integer count) throws ScimException {

        // BEGIN: Removed try-catch - ScimExceptionMapper handles exceptions globally
        LOGGER.info("SchemaManager instance hashcode: " + System.identityHashCode(schemaManager));
        LOGGER.info("SchemaManager initialized: "+ schemaManager.isInitialized());

        LOGGER.info("Getting all schemas");

        // Check if schema manager is initialized
        if (!schemaManager.isInitialized()) {
            throw new BadRequestException("Schema manager not initialized");
        }

        // Get all schemas from the manager
        List<GenericScimResource> allSchemas = schemaManager.getAllSchemas();

        if (allSchemas.isEmpty()) {
            LOGGER.warning("No schemas available");
        }

        // Apply pagination if requested
        int start = (startIndex != null && startIndex > 0) ? startIndex : 1;
        int pageSize = (count != null && count > 0) ? count : allSchemas.size();

        // Calculate pagination bounds
        int fromIndex = Math.max(0, start - 1);
        int toIndex = Math.min(allSchemas.size(), fromIndex + pageSize);

        // Get the page of results
        List<GenericScimResource> pageResults = allSchemas.subList(fromIndex, toIndex);

        // Build ListResponse
        ListResponse<GenericScimResource> listResponse =
                new ListResponse<>(allSchemas.size(), pageResults, start, pageSize);

        return Response.ok(listResponse).build();
        // END: Removed try-catch - ScimExceptionMapper handles exceptions globally
    }

    /**
     * Get a specific schema by URN.
     *
     * GET /Schemas/{urn}
     *
     * The URN can be provided in different formats:
     * - Full URN: urn:ietf:params:scim:schemas:core:2.0:User
     * - Without "urn:" prefix: ietf:params:scim:schemas:core:2.0:User
     *
     * @param urn the schema URN (can be URL-encoded)
     * @return the schema resource
     */
    @GET
    @Path("/{urn:.+}")
    public Response getSchema(@PathParam("urn") String urn) throws ScimException {

        // BEGIN: Removed try-catch - ScimExceptionMapper handles exceptions globally
        LOGGER.info("Getting schema: " + urn);

        // Check if schema manager is initialized
        if (!schemaManager.isInitialized()) {
            throw new BadRequestException("Schema manager not initialized");
        }

        // Normalize the URN (add "urn:" prefix if missing)
        String normalizedUrn = normalizeUrn(urn);

        // Get the schema from the manager
        GenericScimResource schema = schemaManager.getSchema(normalizedUrn);

        if (schema == null) {
            LOGGER.warning("Schema not found: " + normalizedUrn);
            throw new ResourceNotFoundException("Schema not found: " + normalizedUrn);
        }

        return Response.ok(schema).build();
        // END: Removed try-catch - ScimExceptionMapper handles exceptions globally
    }

    /**
     * Normalize a schema URN by ensuring it has the "urn:" prefix.
     *
     * Examples:
     * - "urn:ietf:params:scim:schemas:core:2.0:User" -> "urn:ietf:params:scim:schemas:core:2.0:User"
     * - "ietf:params:scim:schemas:core:2.0:User" -> "urn:ietf:params:scim:schemas:core:2.0:User"
     * - "User" -> try to match against known schemas
     */
    private String normalizeUrn(String urn) {
        if (urn == null || urn.isEmpty()) {
            return urn;
        }

        // Already has "urn:" prefix
        if (urn.startsWith("urn:")) {
            return urn;
        }

        // Try common short names
        String lowerUrn = urn.toLowerCase();
        if ("user".equals(lowerUrn)) {
            return ScimSchemaUrns.CORE_USER_SCHEMA;
        } else if ("group".equals(lowerUrn)) {
            return ScimSchemaUrns.CORE_GROUP_SCHEMA;
        } else if ("enterpriseuser".equals(lowerUrn)) {
            return ScimSchemaUrns.ENTERPRISE_USER_EXTENSION;
        }

        // Add "urn:" prefix
        return "urn:" + urn;
    }

    // BEGIN: Removed buildErrorResponse and escapeJson methods - no longer needed
    // ScimExceptionMapper handles all error response formatting
    // END: Removed buildErrorResponse and escapeJson methods
}