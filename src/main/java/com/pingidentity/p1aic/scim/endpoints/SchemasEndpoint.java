package com.pingidentity.p1aic.scim.endpoints;

// BEGIN: Added imports for SCIM 2.0 compliant ListResponse (RFC 7644 Section 3.4.2)
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
// END: Added imports for SCIM 2.0 compliant ListResponse

import com.pingidentity.p1aic.scim.schema.DynamicSchemaManager;
import com.pingidentity.p1aic.scim.schema.ScimSchemaUrns;
import com.unboundid.scim2.common.GenericScimResource;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException;
import com.unboundid.scim2.common.exceptions.BadRequestException;
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
 *
 * SCIM 2.0 COMPLIANCE FIXES (RFC 7643 / RFC 7644):
 * - ListResponse built manually to avoid invalid root attributes (id, externalId, meta)
 *   per RFC 7644 Section 3.4.2
 * - ObjectMapper configured to exclude null values per RFC 7643 Section 2.5
 */
@Path("/Schemas")
@Produces({"application/scim+json", "application/json"})
public class SchemasEndpoint {

    private static final Logger LOGGER = Logger.getLogger(SchemasEndpoint.class.getName());

    // BEGIN: SCIM 2.0 Compliance - ListResponse schema URN (RFC 7644 Section 3.4.2)
    private static final String LIST_RESPONSE_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:ListResponse";
    // END: SCIM 2.0 Compliance

    // BEGIN: Added ObjectMapper for SCIM 2.0 compliant JSON serialization
    private final ObjectMapper objectMapper;
    // END: Added ObjectMapper

    @Inject
    private DynamicSchemaManager schemaManager;

    /**
     * Constructor initializes ObjectMapper with SCIM 2.0 compliant settings.
     */
    public SchemasEndpoint() {
        this.objectMapper = new ObjectMapper();
        // BEGIN: SCIM 2.0 Compliance - Exclude null values (RFC 7643 Section 2.5)
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // END: SCIM 2.0 Compliance
    }

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

        // BEGIN: SCIM 2.0 Compliant ListResponse (RFC 7644 Section 3.4.2)
        // Build response manually to avoid invalid root attributes (id, externalId, meta)
        // that the UnboundID SDK's ListResponse class may include from base class inheritance.
        // A ListResponse is a message wrapper, NOT a SCIM resource.
        ObjectNode responseNode = buildListResponse(allSchemas.size(), start, pageSize, pageResults);
        // END: SCIM 2.0 Compliant ListResponse

        return Response.ok(responseNode).build();
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
     * Build a SCIM 2.0 compliant ListResponse.
     *
     * Per RFC 7644 Section 3.4.2, a ListResponse is a message wrapper (not a resource)
     * and must only contain the following attributes:
     * - schemas (required): ["urn:ietf:params:scim:api:messages:2.0:ListResponse"]
     * - totalResults (required): Total number of results matching the query
     * - Resources (optional): Array of returned resources
     * - startIndex (optional): 1-based index of first result in current page
     * - itemsPerPage (optional): Number of resources returned in current page
     *
     * It must NOT contain id, externalId, or meta at the root level.
     *
     * @param totalResults total number of results
     * @param startIndex 1-based start index
     * @param itemsPerPage number of items per page
     * @param resources list of resources to include
     * @return ObjectNode representing the compliant ListResponse
     */
    private ObjectNode buildListResponse(int totalResults, int startIndex, int itemsPerPage,
                                         List<GenericScimResource> resources) {
        ObjectNode responseNode = objectMapper.createObjectNode();

        // schemas (required) - must be ListResponse schema
        ArrayNode schemasArray = objectMapper.createArrayNode();
        schemasArray.add(LIST_RESPONSE_SCHEMA);
        responseNode.set("schemas", schemasArray);

        // totalResults (required)
        responseNode.put("totalResults", totalResults);

        // startIndex (optional but included for pagination)
        responseNode.put("startIndex", startIndex);

        // itemsPerPage (optional but included for pagination)
        responseNode.put("itemsPerPage", itemsPerPage);

        // Resources (optional) - array of returned resources
        ArrayNode resourcesArray = objectMapper.createArrayNode();
        for (GenericScimResource resource : resources) {
            resourcesArray.add(resource.getObjectNode());
        }
        responseNode.set("Resources", resourcesArray);

        return responseNode;
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