package com.pingidentity.p1aic.scim.endpoints;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.config.ScimServerConfig;
import com.pingidentity.p1aic.scim.schema.ScimSchemaUrns;
import com.unboundid.scim2.common.GenericScimResource;
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.messages.ListResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * JAX-RS endpoint for SCIM 2.0 ResourceType discovery.
 *
 * Provides metadata about the types of resources available (User, Group)
 * and their associated schemas and endpoints.
 *
 * Path: /scim/v2/ResourceTypes
 *
 * REFACTORED: Removed try-catch blocks - ScimExceptionMapper handles all exceptions globally
 */
@Path("/ResourceTypes")
@Produces("application/scim+json")
public class ResourceTypesEndpoint {

    private static final Logger LOGGER = Logger.getLogger(ResourceTypesEndpoint.class.getName());

    private final ObjectMapper objectMapper;
    private final ScimServerConfig config;

    /**
     * Constructor initializes ObjectMapper and configuration.
     */
    public ResourceTypesEndpoint() {
        this.objectMapper = new ObjectMapper();
        this.config = ScimServerConfig.getInstance();
    }

    /**
     * Get all resource types.
     *
     * GET /ResourceTypes
     *
     * @param startIndex 1-based start index for pagination (optional)
     * @param count number of results to return (optional)
     * @return ListResponse containing all resource types
     */
    @GET
    public Response getAllResourceTypes(
            @QueryParam("startIndex") Integer startIndex,
            @QueryParam("count") Integer count) throws ScimException {

        // BEGIN: Removed try-catch - ScimExceptionMapper handles exceptions globally
        LOGGER.info("Getting all resource types");

        // Build all resource type definitions
        List<GenericScimResource> resourceTypes = new ArrayList<>();
        resourceTypes.add(buildUserResourceType());
        resourceTypes.add(buildGroupResourceType());

        // Apply pagination if requested
        int start = (startIndex != null && startIndex > 0) ? startIndex : 1;
        int pageSize = (count != null && count > 0) ? count : resourceTypes.size();

        // Calculate pagination bounds
        int fromIndex = Math.max(0, start - 1);
        int toIndex = Math.min(resourceTypes.size(), fromIndex + pageSize);

        // Get the page of results
        List<GenericScimResource> pageResults = resourceTypes.subList(fromIndex, toIndex);

        // Build ListResponse
        ListResponse<GenericScimResource> listResponse =
                new ListResponse<>(resourceTypes.size(), pageResults, start, pageSize);

        return Response.ok(listResponse).build();
        // END: Removed try-catch - ScimExceptionMapper handles exceptions globally
    }

    /**
     * Get a specific resource type by name.
     *
     * GET /ResourceTypes/{name}
     *
     * @param name the resource type name (e.g., "User", "Group")
     * @return the resource type definition
     */
    @GET
    @Path("/{name}")
    public Response getResourceType(@PathParam("name") String name) throws ScimException {

        // BEGIN: Removed try-catch - ScimExceptionMapper handles exceptions globally
        LOGGER.info("Getting resource type: " + name);

        GenericScimResource resourceType = null;

        // Match resource type by name (case-insensitive)
        if ("User".equalsIgnoreCase(name)) {
            resourceType = buildUserResourceType();
        } else if ("Group".equalsIgnoreCase(name)) {
            resourceType = buildGroupResourceType();
        }

        if (resourceType == null) {
            LOGGER.warning("Resource type not found: " + name);
            throw new ResourceNotFoundException("Resource type not found: " + name);
        }

        return Response.ok(resourceType).build();
        // END: Removed try-catch - ScimExceptionMapper handles exceptions globally
    }

    /**
     * Build User ResourceType definition.
     */
    private GenericScimResource buildUserResourceType() {
        ObjectNode resourceType = objectMapper.createObjectNode();

        // Add schemas
        ArrayNode schemas = objectMapper.createArrayNode();
        schemas.add(ScimSchemaUrns.RESOURCE_TYPE);
        resourceType.set("schemas", schemas);

        // Set resource type metadata
        resourceType.put("id", "User");
        resourceType.put("name", "User");
        resourceType.put("description", "User Account");
        resourceType.put("endpoint", "/Users");
        resourceType.put("schema", ScimSchemaUrns.CORE_USER_SCHEMA);

        // Add schema extensions (optional)
        ArrayNode schemaExtensions = objectMapper.createArrayNode();

        // Enterprise User extension
        ObjectNode enterpriseExt = objectMapper.createObjectNode();
        enterpriseExt.put("schema", ScimSchemaUrns.ENTERPRISE_USER_EXTENSION);
        enterpriseExt.put("required", false);
        schemaExtensions.add(enterpriseExt);

        // PingIDM User extension (for custom attributes)
        ObjectNode pingidmExt = objectMapper.createObjectNode();
        pingidmExt.put("schema", ScimSchemaUrns.PINGIDM_USER_EXTENSION);
        pingidmExt.put("required", false);
        schemaExtensions.add(pingidmExt);

        resourceType.set("schemaExtensions", schemaExtensions);

        // Add meta
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("resourceType", "ResourceType");
        String location = config.getScimServerBaseUrl() + "/ResourceTypes/User";
        meta.put("location", location);
        resourceType.set("meta", meta);

        return new GenericScimResource(resourceType);
    }

    /**
     * Build Group ResourceType definition.
     */
    private GenericScimResource buildGroupResourceType() {
        ObjectNode resourceType = objectMapper.createObjectNode();

        // Add schemas
        ArrayNode schemas = objectMapper.createArrayNode();
        schemas.add(ScimSchemaUrns.RESOURCE_TYPE);
        resourceType.set("schemas", schemas);

        // Set resource type metadata
        resourceType.put("id", "Group");
        resourceType.put("name", "Group");
        resourceType.put("description", "Group");
        resourceType.put("endpoint", "/Groups");
        resourceType.put("schema", ScimSchemaUrns.CORE_GROUP_SCHEMA);

        // Add schema extensions (optional)
        ArrayNode schemaExtensions = objectMapper.createArrayNode();

        // PingIDM Group extension (for custom attributes)
        ObjectNode pingidmExt = objectMapper.createObjectNode();
        pingidmExt.put("schema", ScimSchemaUrns.PINGIDM_GROUP_EXTENSION);
        pingidmExt.put("required", false);
        schemaExtensions.add(pingidmExt);

        resourceType.set("schemaExtensions", schemaExtensions);

        // Add meta
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("resourceType", "ResourceType");
        String location = config.getScimServerBaseUrl() + "/ResourceTypes/Group";
        meta.put("location", location);
        resourceType.set("meta", meta);

        return new GenericScimResource(resourceType);
    }

    // BEGIN: Removed buildErrorResponse and escapeJson methods - no longer needed
    // ScimExceptionMapper handles all error response formatting
    // END: Removed buildErrorResponse and escapeJson methods
}