package com.pingidentity.p1aic.scim.endpoints;

import com.pingidentity.p1aic.scim.service.PingIdmRoleService;
// BEGIN: Add imports for filter and patch conversion
import com.pingidentity.p1aic.scim.filter.ScimFilterConverter;
import com.pingidentity.p1aic.scim.filter.ScimPatchConverter;
import com.pingidentity.p1aic.scim.exceptions.FilterTranslationException;
// END: Add imports for filter and patch conversion

// BEGIN: Added imports for SCIM 2.0 compliant ListResponse (RFC 7644 Section 3.4.2)
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
// END: Added imports for SCIM 2.0 compliant ListResponse

import com.unboundid.scim2.common.GenericScimResource;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.messages.ListResponse;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JAX-RS endpoint for SCIM 2.0 Group resources.
 *
 * Provides CRUD operations and search functionality for groups/roles.
 * Path: /scim/v2/Groups
 *
 * REFACTORED: Removed try-catch blocks - ScimExceptionMapper handles all exceptions globally
 *
 * SCIM 2.0 COMPLIANCE FIXES (RFC 7643 / RFC 7644):
 * - ListResponse built manually to avoid invalid root attributes (id, externalId, meta)
 *   per RFC 7644 Section 3.4.2
 * - ObjectMapper configured to exclude null values per RFC 7643 Section 2.5
 */
@Path("/Groups")
@Produces({"application/scim+json", "application/json"})
@Consumes({"application/scim+json", "application/json"})
public class GroupScimEndpoint {

    private static final Logger LOGGER = Logger.getLogger(GroupScimEndpoint.class.getName());

    // Default pagination values
    private static final int DEFAULT_START_INDEX = 1;
    private static final int DEFAULT_COUNT = 100;
    private static final int MAX_COUNT = 1000;

    // BEGIN: SCIM 2.0 Compliance - ListResponse schema URN (RFC 7644 Section 3.4.2)
    private static final String LIST_RESPONSE_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:ListResponse";
    // END: SCIM 2.0 Compliance

    @Inject
    private PingIdmRoleService roleService;

    // BEGIN: Add filter and patch converters
    private final ScimFilterConverter filterConverter;
    private final ScimPatchConverter patchConverter;
    // END: Add filter and patch converters

    // BEGIN: Added ObjectMapper for SCIM 2.0 compliant JSON serialization
    // Also used for manual JSON parsing to bypass SDK validation on custom attributes
    private final ObjectMapper objectMapper;
    // END: Added ObjectMapper

    // BEGIN: Add constructor to initialize converters and ObjectMapper
    public GroupScimEndpoint() {
        this.filterConverter = new ScimFilterConverter();
        this.patchConverter = new ScimPatchConverter("Group");
        this.objectMapper = new ObjectMapper();
        // BEGIN: SCIM 2.0 Compliance - Exclude null values (RFC 7643 Section 2.5)
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // END: SCIM 2.0 Compliance
    }
    // END: Add constructor to initialize converters and ObjectMapper

    /**
     * Search/List groups.
     *
     * GET /Groups?filter={filter}&startIndex={startIndex}&count={count}&attributes={attributes}
     *
     * @param filter SCIM filter expression (optional)
     * @param startIndex 1-based start index for pagination (default: 1)
     * @param count number of results to return (default: 100, max: 1000)
     * @param attributes comma-separated list of attributes to return (optional)
     * @param excludedAttributes comma-separated list of attributes to exclude (optional)
     * @return ListResponse containing groups
     */
    @GET
    public Response searchGroups(
            @QueryParam("filter") String filter,
            @QueryParam("startIndex") Integer startIndex,
            @QueryParam("count") Integer count,
            @QueryParam("attributes") String attributes,
            @QueryParam("excludedAttributes") String excludedAttributes) throws ScimException {

        // BEGIN: Removed try-catch - ScimExceptionMapper handles exceptions globally
        // Apply default values
        int start = (startIndex != null && startIndex > 0) ? startIndex : DEFAULT_START_INDEX;

        // Handle count parameter including count=0
        int pageSize;
        if (count != null && count == 0) {
            pageSize = 0;  // RFC 7644: return only totalResults
            LOGGER.info("Count=0 requested: will return only totalResults");
        } else if (count != null && count > 0) {
            pageSize = Math.min(count, MAX_COUNT);
        } else {
            pageSize = DEFAULT_COUNT;
        }

        LOGGER.info(String.format("Searching groups: filter=%s, startIndex=%d, count=%d, attributes=%s, excludedAttributes=%s",
                filter, start, pageSize, attributes, excludedAttributes));

        // BEGIN: Convert SCIM filter to PingIDM query filter
        String queryFilter = null;
        if (filter != null && !filter.trim().isEmpty()) {
            try {
                queryFilter = filterConverter.convert(filter);
                LOGGER.info("Converted SCIM filter '" + filter + "' to PingIDM query: '" + queryFilter + "'");
            } catch (FilterTranslationException e) {
                LOGGER.severe("Failed to convert SCIM filter: " + e.getMessage());
                throw new BadRequestException("Invalid filter expression: " + e.getMessage());
            }
        }
        // END: Convert SCIM filter to PingIDM query filter

        // Convert SCIM attributes to PingIDM fields
        String idmFields = convertScimAttributesToIdmFields(attributes, excludedAttributes);

        // Call service to search roles
        ListResponse<GenericScimResource> listResponse =
                roleService.searchRoles(queryFilter, start, pageSize, idmFields);

        // BEGIN: SCIM 2.0 Compliant ListResponse (RFC 7644 Section 3.4.2)
        // Convert service ListResponse to compliant ObjectNode to avoid invalid root attributes
        // (id, externalId, meta) that the UnboundID SDK's ListResponse may include.
        ObjectNode responseNode = buildCompliantListResponse(listResponse);
        // END: SCIM 2.0 Compliant ListResponse

        return Response.ok(responseNode).build();
        // END: Removed try-catch - ScimExceptionMapper handles exceptions globally
    }

    /**
     * Get a group by ID.
     *
     * GET /Groups/{id}?attributes={attributes}
     *
     * @param id the group ID
     * @param attributes comma-separated list of attributes to return (optional)
     * @param excludedAttributes comma-separated list of attributes to exclude (optional)
     * @return the group resource
     */
    @GET
    @Path("/{id}")
    public Response getGroup(
            @PathParam("id") String id,
            @QueryParam("attributes") String attributes,
            @QueryParam("excludedAttributes") String excludedAttributes) throws ScimException {

        // BEGIN: Removed try-catch - ScimExceptionMapper handles exceptions globally
        LOGGER.info("Getting group: " + id);

        // Convert SCIM attributes to PingIDM fields
        String idmFields = convertScimAttributesToIdmFields(attributes, excludedAttributes);

        // Call service to get role
        GenericScimResource group = roleService.getRole(id, idmFields);

        // Return 200 OK with group resource
        return Response.ok(group).build();
        // END: Removed try-catch - ScimExceptionMapper handles exceptions globally
    }

    /**
     * Create a new group.
     *
     * POST /Groups
     *
     * @param groupJson the group resource JSON as String
     * @return the created group resource
     */
    @POST
    public Response createGroup(String groupJson) throws ScimException {

        // BEGIN: Removed try-catch - ScimExceptionMapper handles exceptions globally
        LOGGER.info("Creating group");

        try {
            // BEGIN: Parse JSON directly without SDK validation to support custom attributes
            ObjectNode groupNode = (ObjectNode) objectMapper.readTree(groupJson);
            GenericScimResource group = new GenericScimResource(groupNode);
            // END: Parse JSON directly without SDK validation

            // Call service to create role
            GenericScimResource createdGroup = roleService.createRole(group);

            // Extract group ID for Location header
            String groupId = extractGroupId(createdGroup);
            // BEGIN: Use UriBuilder instead of string concatenation for Location header
            String location = jakarta.ws.rs.core.UriBuilder.fromPath("/Groups").path(groupId).build().toString();
            // END: Use UriBuilder instead of string concatenation for Location header

            // Return 201 Created with Location header
            return Response.status(Response.Status.CREATED)
                    .header("Location", location)
                    .entity(createdGroup)
                    .build();
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "Invalid JSON in request", e);
            throw new BadRequestException("Invalid JSON format: " + e.getMessage());
        }
        // END: Removed try-catch - ScimExceptionMapper handles exceptions globally
    }

    /**
     * Update a group (full replace).
     *
     * PUT /Groups/{id}
     *
     * @param id the group ID
     * @param groupJson the updated group resource JSON as String
     * @param ifMatch the If-Match header for optimistic locking (optional)
     * @return the updated group resource
     */
    @PUT
    @Path("/{id}")
    public Response updateGroup(
            @PathParam("id") String id,
            String groupJson,
            @HeaderParam("If-Match") String ifMatch) throws ScimException {

        // BEGIN: Removed try-catch - ScimExceptionMapper handles exceptions globally
        LOGGER.info("Updating group: " + id);

        try {
            // BEGIN: Parse JSON directly without SDK validation to support custom attributes
            ObjectNode groupNode = (ObjectNode) objectMapper.readTree(groupJson);
            GenericScimResource group = new GenericScimResource(groupNode);
            // END: Parse JSON directly without SDK validation

            // Extract revision from If-Match header (may be in quotes)
            String revision = extractRevision(ifMatch);

            // Call service to update role
            GenericScimResource updatedGroup = roleService.updateRole(id, group, revision);

            // Return 200 OK with updated group
            return Response.ok(updatedGroup).build();
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "Invalid JSON in request", e);
            throw new BadRequestException("Invalid JSON format: " + e.getMessage());
        }
        // END: Removed try-catch - ScimExceptionMapper handles exceptions globally
    }

    /**
     * Patch a group (partial update).
     *
     * PATCH /Groups/{id}
     *
     * @param id the group ID
     * @param patchJson the SCIM PATCH request JSON as String
     * @param ifMatch the If-Match header for optimistic locking (optional)
     * @return the patched group resource
     */
    @PATCH
    @Path("/{id}")
    public Response patchGroup(
            @PathParam("id") String id,
            String patchJson,
            @HeaderParam("If-Match") String ifMatch) throws ScimException {

        // BEGIN: Removed try-catch - ScimExceptionMapper handles exceptions globally
        LOGGER.info("Patching group: " + id);

        try {
            // BEGIN: Parse JSON directly without SDK validation to support custom attributes
            ObjectNode patchNode = (ObjectNode) objectMapper.readTree(patchJson);
            GenericScimResource patchRequest = new GenericScimResource(patchNode);
            // END: Parse JSON directly without SDK validation

            // Extract revision from If-Match header
            String revision = extractRevision(ifMatch);

            // BEGIN: Parse and convert SCIM patch operations to PingIDM format
            String idmPatchOperations;
            try {
                idmPatchOperations = patchConverter.convert(patchJson);
                LOGGER.info("Converted SCIM PATCH to PingIDM format for group: " + id);
            } catch (FilterTranslationException e) {
                LOGGER.severe("Failed to convert SCIM PATCH operations: " + e.getMessage());
                throw new BadRequestException("Invalid PATCH request: " + e.getMessage());
            }
            // END: Parse and convert SCIM patch operations to PingIDM format

            // Call service to patch role
            GenericScimResource patchedGroup = roleService.patchRole(id, idmPatchOperations, revision);

            // Return 200 OK with patched group
            return Response.ok(patchedGroup).build();
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "Invalid JSON in request", e);
            throw new BadRequestException("Invalid JSON format: " + e.getMessage());
        }
        // END: Removed try-catch - ScimExceptionMapper handles exceptions globally
    }

    /**
     * Delete a group.
     *
     * DELETE /Groups/{id}
     *
     * @param id the group ID
     * @param ifMatch the If-Match header for optimistic locking (optional)
     * @return 204 No Content on success
     */
    @DELETE
    @Path("/{id}")
    public Response deleteGroup(
            @PathParam("id") String id,
            @HeaderParam("If-Match") String ifMatch) throws ScimException {

        // BEGIN: Removed try-catch - ScimExceptionMapper handles exceptions globally
        LOGGER.info("Deleting group: " + id);

        // Extract revision from If-Match header
        String revision = extractRevision(ifMatch);

        // Call service to delete role
        roleService.deleteRole(id, revision);

        // Return 204 No Content
        return Response.noContent().build();
        // END: Removed try-catch - ScimExceptionMapper handles exceptions globally
    }

    /**
     * Build a SCIM 2.0 compliant ListResponse from service response.
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
     * @param serviceResponse the ListResponse from the service layer
     * @return ObjectNode representing the compliant ListResponse
     */
    private ObjectNode buildCompliantListResponse(ListResponse<GenericScimResource> serviceResponse) {
        ObjectNode responseNode = objectMapper.createObjectNode();

        // schemas (required) - must be ListResponse schema
        ArrayNode schemasArray = objectMapper.createArrayNode();
        schemasArray.add(LIST_RESPONSE_SCHEMA);
        responseNode.set("schemas", schemasArray);

        // totalResults (required)
        responseNode.put("totalResults", serviceResponse.getTotalResults());

        // startIndex (optional but included for pagination)
        if (serviceResponse.getStartIndex() != null) {
            responseNode.put("startIndex", serviceResponse.getStartIndex());
        }

        // itemsPerPage (optional but included for pagination)
        if (serviceResponse.getItemsPerPage() != null) {
            responseNode.put("itemsPerPage", serviceResponse.getItemsPerPage());
        }

        // Resources (optional) - array of returned resources
        List<GenericScimResource> resources = serviceResponse.getResources();
        if (resources != null && !resources.isEmpty()) {
            ArrayNode resourcesArray = objectMapper.createArrayNode();
            for (GenericScimResource resource : resources) {
                resourcesArray.add(resource.getObjectNode());
            }
            responseNode.set("Resources", resourcesArray);
        }

        return responseNode;
    }

    /**
     * Convert SCIM attributes parameter to PingIDM _fields parameter.
     *
     * @param attributes comma-separated SCIM attributes to include
     * @param excludedAttributes comma-separated SCIM attributes to exclude
     * @return PingIDM _fields parameter value, or "*" for all fields
     */
    private String convertScimAttributesToIdmFields(String attributes, String excludedAttributes) {
        // If both are null/empty, return all fields
        if ((attributes == null || attributes.trim().isEmpty()) &&
                (excludedAttributes == null || excludedAttributes.trim().isEmpty())) {
            return "*";
        }

        // Cannot specify both attributes and excludedAttributes
        if (attributes != null && !attributes.trim().isEmpty() &&
                excludedAttributes != null && !excludedAttributes.trim().isEmpty()) {
            throw new IllegalArgumentException("Cannot specify both 'attributes' and 'excludedAttributes'");
        }

        // Handle attributes (include list)
        if (attributes != null && !attributes.trim().isEmpty()) {
            String[] scimAttrs = attributes.split(",");
            List<String> idmFields = new ArrayList<>();

            // Always include id and meta fields
            idmFields.add("_id");
            idmFields.add("_rev");

            for (String scimAttr : scimAttrs) {
                String trimmed = scimAttr.trim();

                // Map SCIM Group attributes to PingIDM role fields
                switch (trimmed) {
                    case "id":
                        // Already added
                        break;
                    case "displayName":
                        idmFields.add("name");
                        break;
                    case "description":
                        idmFields.add("description");
                        break;
                    case "members":
                    case "members.value":
                    case "members.$ref":
                    case "members.type":
                        idmFields.add("members");
                        break;
                    case "meta":
                        // Already have _rev, timestamps are in _meta if available
                        break;
                    default:
                        // Pass through custom attributes as-is
                        idmFields.add(trimmed);
                        break;
                }
            }

            return String.join(",", idmFields);
        }

        // Handle excludedAttributes - not directly supported by PingIDM _fields
        // Would need to fetch all fields and filter in application layer
        if (excludedAttributes != null && !excludedAttributes.trim().isEmpty()) {
            LOGGER.warning("excludedAttributes not fully supported - fetching all fields and will filter in application");
            return "*";
        }

        return "*";
    }

    /**
     * Extract group ID from GenericScimResource.
     */
    private String extractGroupId(GenericScimResource group) {
        try {
            return group.getId();
        } catch (Exception e) {
            LOGGER.warning("Failed to extract group ID: " + e.getMessage());
            return "unknown";
        }
    }

    /**
     * Extract revision from If-Match header.
     * Handles both quoted and unquoted values: "123" or 123
     */
    private String extractRevision(String ifMatch) {
        if (ifMatch == null || ifMatch.trim().isEmpty()) {
            return null;
        }

        // Remove quotes if present
        String revision = ifMatch.trim();
        if (revision.startsWith("\"") && revision.endsWith("\"")) {
            revision = revision.substring(1, revision.length() - 1);
        }

        return revision;
    }

    // BEGIN: Removed buildErrorResponse and escapeJson methods - no longer needed
    // ScimExceptionMapper handles all error response formatting
    // END: Removed buildErrorResponse and escapeJson methods
}