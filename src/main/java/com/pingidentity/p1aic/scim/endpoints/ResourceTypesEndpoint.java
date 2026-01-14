package com.pingidentity.p1aic.scim.endpoints;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.schema.ScimSchemaUrns;
import com.unboundid.scim2.common.GenericScimResource;
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException;
import com.unboundid.scim2.common.exceptions.ScimException;
// BEGIN: Added UriInfo import for dynamic URL resolution (consistent with ServiceProviderConfigEndpoint fix)
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
// END: Added UriInfo import for dynamic URL resolution
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
 *
 * SCIM 2.0 COMPLIANCE FIXES (RFC 7643 / RFC 7644):
 * - ListResponse built manually to avoid invalid root attributes (id, externalId, meta)
 *   per RFC 7644 Section 3.4.2
 * - ObjectMapper configured to exclude null values per RFC 7643 Section 2.5
 */
@Path("/ResourceTypes")
@Produces({"application/scim+json", "application/json"})
public class ResourceTypesEndpoint {

    private static final Logger LOGGER = Logger.getLogger(ResourceTypesEndpoint.class.getName());

    // BEGIN: SCIM 2.0 Compliance - ListResponse schema URN (RFC 7644 Section 3.4.2)
    private static final String LIST_RESPONSE_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:ListResponse";
    // END: SCIM 2.0 Compliance

    private final ObjectMapper objectMapper;

    /**
     * Constructor initializes ObjectMapper.
     *
     * Note: ScimServerConfig removed - using UriInfo for dynamic URL resolution
     * to properly handle proxies, load balancers, and Cloud Run deployments.
     */
    public ResourceTypesEndpoint() {
        this.objectMapper = new ObjectMapper();
        // BEGIN: SCIM 2.0 Compliance - Exclude null values (RFC 7643 Section 2.5)
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // END: SCIM 2.0 Compliance
    }

    /**
     * Get all resource types.
     *
     * GET /ResourceTypes
     *
     * Returns a SCIM 2.0 compliant ListResponse per RFC 7644 Section 3.4.2.
     * The ListResponse is a message wrapper and must only contain:
     * - schemas (required)
     * - totalResults (required)
     * - Resources (optional)
     * - startIndex (optional)
     * - itemsPerPage (optional)
     *
     * @param uriInfo injected by JAX-RS to get the actual request URI for meta.location
     * @param startIndex 1-based start index for pagination (optional)
     * @param count number of results to return (optional)
     * @return ListResponse containing all resource types
     */
    @GET
    // BEGIN: Added UriInfo parameter to get actual request URL for meta.location
    public Response getAllResourceTypes(
            @Context UriInfo uriInfo,
            @QueryParam("startIndex") Integer startIndex,
            @QueryParam("count") Integer count) throws ScimException {
        // END: Added UriInfo parameter to get actual request URL for meta.location

        // BEGIN: Removed try-catch - ScimExceptionMapper handles exceptions globally
        LOGGER.info("Getting all resource types");

        // BEGIN: Derive base URL from UriInfo (consistent with ServiceProviderConfigEndpoint fix)
        // uriInfo.getAbsolutePath() returns the full URI: https://host/scim/v2/ResourceTypes
        String baseUrl = uriInfo.getAbsolutePath().toString();
        // END: Derive base URL from UriInfo

        // Build all resource type definitions
        List<GenericScimResource> resourceTypes = new ArrayList<>();
        resourceTypes.add(buildUserResourceType(baseUrl));
        resourceTypes.add(buildGroupResourceType(baseUrl));

        // Apply pagination if requested
        int start = (startIndex != null && startIndex > 0) ? startIndex : 1;
        int pageSize = (count != null && count > 0) ? count : resourceTypes.size();

        // Calculate pagination bounds
        int fromIndex = Math.max(0, start - 1);
        int toIndex = Math.min(resourceTypes.size(), fromIndex + pageSize);

        // Get the page of results
        List<GenericScimResource> pageResults = resourceTypes.subList(fromIndex, toIndex);

        // BEGIN: SCIM 2.0 Compliant ListResponse (RFC 7644 Section 3.4.2)
        // Build response manually to avoid invalid root attributes (id, externalId, meta)
        // that the UnboundID SDK's ListResponse class may include from base class inheritance.
        // A ListResponse is a message wrapper, NOT a SCIM resource.
        ObjectNode responseNode = buildListResponse(resourceTypes.size(), start, pageSize, pageResults);
        // END: SCIM 2.0 Compliant ListResponse

        return Response.ok(responseNode).build();
        // END: Removed try-catch - ScimExceptionMapper handles exceptions globally
    }

    /**
     * Get a specific resource type by name.
     *
     * GET /ResourceTypes/{name}
     *
     * @param uriInfo injected by JAX-RS to get the actual request URI for meta.location
     * @param name the resource type name (e.g., "User", "Group")
     * @return the resource type definition
     */
    @GET
    @Path("/{name}")
    // BEGIN: Added UriInfo parameter to get actual request URL for meta.location
    public Response getResourceType(
            @Context UriInfo uriInfo,
            @PathParam("name") String name) throws ScimException {
        // END: Added UriInfo parameter to get actual request URL for meta.location

        // BEGIN: Removed try-catch - ScimExceptionMapper handles exceptions globally
        LOGGER.info("Getting resource type: " + name);

        // BEGIN: Derive base URL from UriInfo
        // For /ResourceTypes/User, we need the base /ResourceTypes URL
        // uriInfo.getAbsolutePath() = https://host/scim/v2/ResourceTypes/User
        // We use this directly since it already points to the specific resource
        String resourceUrl = uriInfo.getAbsolutePath().toString();
        // For building the resource, we need the parent path: /ResourceTypes
        String baseUrl = resourceUrl.substring(0, resourceUrl.lastIndexOf('/'));
        // END: Derive base URL from UriInfo

        GenericScimResource resourceType = null;

        // Match resource type by name (case-insensitive)
        if ("User".equalsIgnoreCase(name)) {
            resourceType = buildUserResourceType(baseUrl);
        } else if ("Group".equalsIgnoreCase(name)) {
            resourceType = buildGroupResourceType(baseUrl);
        }

        if (resourceType == null) {
            LOGGER.warning("Resource type not found: " + name);
            throw new ResourceNotFoundException("Resource type not found: " + name);
        }

        return Response.ok(resourceType).build();
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
     * Build User ResourceType definition.
     *
     * @param baseUrl the base URL for ResourceTypes (e.g., https://host/scim/v2/ResourceTypes)
     *                derived from UriInfo to ensure correct hostname and path prefix
     */
    private GenericScimResource buildUserResourceType(String baseUrl) {
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
        // BEGIN: Use baseUrl from UriInfo for correct meta.location
        // This ensures the location includes the full path (e.g., /scim/v2/ResourceTypes/User)
        // and correct hostname even behind proxies, load balancers, or Cloud Run
        String location = baseUrl + "/User";
        meta.put("location", location);
        // END: Use baseUrl from UriInfo for correct meta.location
        resourceType.set("meta", meta);

        return new GenericScimResource(resourceType);
    }

    /**
     * Build Group ResourceType definition.
     *
     * @param baseUrl the base URL for ResourceTypes (e.g., https://host/scim/v2/ResourceTypes)
     *                derived from UriInfo to ensure correct hostname and path prefix
     */
    private GenericScimResource buildGroupResourceType(String baseUrl) {
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
        // BEGIN: Use baseUrl from UriInfo for correct meta.location
        // This ensures the location includes the full path (e.g., /scim/v2/ResourceTypes/Group)
        // and correct hostname even behind proxies, load balancers, or Cloud Run
        String location = baseUrl + "/Group";
        meta.put("location", location);
        // END: Use baseUrl from UriInfo for correct meta.location
        resourceType.set("meta", meta);

        return new GenericScimResource(resourceType);
    }
}