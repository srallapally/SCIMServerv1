package com.pingidentity.p1aic.scim.endpoints;

import com.pingidentity.p1aic.scim.service.PingIdmUserService;
// BEGIN: Add imports for filter and patch conversion
import com.pingidentity.p1aic.scim.filter.ScimFilterConverter;
import com.pingidentity.p1aic.scim.filter.ScimPatchConverter;
import com.pingidentity.p1aic.scim.exceptions.FilterTranslationException;
// END: Add imports for filter and patch conversion

// BEGIN: Added imports for SCIM 2.0 compliant ListResponse (RFC 7644 Section 3.4.2)
import com.fasterxml.jackson.annotation.JsonInclude;
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
 * JAX-RS endpoint for SCIM 2.0 User resources.
 *
 * Provides CRUD operations and search functionality for users.
 * Path: /scim/v2/Users
 *
 * Enhancements:
 * - Proper handling of count=0 per RFC 7644 (returns only totalResults)
 * - Uses PingIDM's _countOnly parameter for better performance
 * - Supports attribute projection via attributes/excludedAttributes parameters
 *
 * SCIM 2.0 COMPLIANCE FIXES (RFC 7643 / RFC 7644):
 * - ListResponse built manually to avoid invalid root attributes (id, externalId, meta)
 *   per RFC 7644 Section 3.4.2
 * - ObjectMapper configured to exclude null values per RFC 7643 Section 2.5
 */
@Path("/Users")
@Produces({"application/scim+json", "application/json"})
@Consumes({"application/scim+json", "application/json"})
public class UserScimEndpoint {

    private static final Logger LOGGER = Logger.getLogger(UserScimEndpoint.class.getName());

    // Default pagination values
    private static final int DEFAULT_START_INDEX = 1;
    private static final int DEFAULT_COUNT = 100;
    private static final int MAX_COUNT = 1000;

    // BEGIN: SCIM 2.0 Compliance - ListResponse schema URN (RFC 7644 Section 3.4.2)
    private static final String LIST_RESPONSE_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:ListResponse";
    // END: SCIM 2.0 Compliance

    @Inject
    private PingIdmUserService userService;

    // BEGIN: Add filter and patch converters
    private final ScimFilterConverter filterConverter;
    private final ScimPatchConverter patchConverter;
    // END: Add filter and patch converters

    // BEGIN: Added ObjectMapper for SCIM 2.0 compliant JSON serialization
    private final ObjectMapper objectMapper;
    // END: Added ObjectMapper

    // BEGIN: Add constructor to initialize converters and ObjectMapper
    public UserScimEndpoint() {
        this.filterConverter = new ScimFilterConverter();
        this.patchConverter = new ScimPatchConverter("User");
        this.objectMapper = new ObjectMapper();
        // BEGIN: SCIM 2.0 Compliance - Exclude null values (RFC 7643 Section 2.5)
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // END: SCIM 2.0 Compliance
    }
    // END: Add constructor to initialize converters and ObjectMapper

    /**
     * Search/List users.
     *
     * GET /Users?filter={filter}&startIndex={startIndex}&count={count}&attributes={attributes}
     *
     * @param filter SCIM filter expression (optional)
     * @param startIndex 1-based start index for pagination (default: 1)
     * @param count number of results to return (default: 100, max: 1000, 0 = count only)
     * @param attributes comma-separated list of attributes to return (optional)
     * @param excludedAttributes comma-separated list of attributes to exclude (optional)
     * @return ListResponse containing users
     */
    @GET
    public Response searchUsers(
            @QueryParam("filter") String filter,
            @QueryParam("startIndex") Integer startIndex,
            @QueryParam("count") Integer count,
            @QueryParam("attributes") String attributes,
            @QueryParam("excludedAttributes") String excludedAttributes) throws ScimException{

        // Apply default startIndex
        int start = (startIndex != null && startIndex > 0) ? startIndex : DEFAULT_START_INDEX;

        // Handle count parameter including count=0
        int pageSize;
        if (count != null && count == 0) {
            // RFC 7644: count=0 means return only totalResults, no Resources
            pageSize = 0;
            LOGGER.info("Count=0 requested: will return only totalResults");
        } else if (count != null && count > 0) {
            // Enforce maximum to prevent abuse
            pageSize = Math.min(count, MAX_COUNT);
        } else {
            // Not specified, use default
            pageSize = DEFAULT_COUNT;
        }

        LOGGER.info(String.format("Searching users: filter=%s, startIndex=%d, count=%d, attributes=%s, excludedAttributes=%s",
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

        // Call service to search users
        ListResponse<GenericScimResource> listResponse =
                userService.searchUsers(queryFilter, start, pageSize, idmFields);

        // BEGIN: SCIM 2.0 Compliant ListResponse (RFC 7644 Section 3.4.2)
        // Convert service ListResponse to compliant ObjectNode to avoid invalid root attributes
        // (id, externalId, meta) that the UnboundID SDK's ListResponse may include.
        ObjectNode responseNode = buildCompliantListResponse(listResponse);
        // END: SCIM 2.0 Compliant ListResponse

        return Response.ok(responseNode).build();
    }

    /**
     * Get a user by ID.
     *
     * GET /Users/{id}?attributes={attributes}
     *
     * @param id the user ID
     * @param attributes comma-separated list of attributes to return (optional)
     * @param excludedAttributes comma-separated list of attributes to exclude (optional)
     * @return the user resource
     */
    @GET
    @Path("/{id}")
    public Response getUser(
            @PathParam("id") String id,
            @QueryParam("attributes") String attributes,
            @QueryParam("excludedAttributes") String excludedAttributes) throws ScimException{

        LOGGER.info("Getting user: " + id);

        // Convert SCIM attributes to PingIDM fields
        String idmFields = convertScimAttributesToIdmFields(attributes, excludedAttributes);

        // Call service to get user
        GenericScimResource user = userService.getUser(id, idmFields);

        // Return 200 OK with user resource
        return Response.ok(user).build();
    }

    /**
     * Create a new user.
     *
     * POST /Users
     *
     * @param user the user resource to create
     * @return the created user resource
     */
    @POST
    public Response createUser(GenericScimResource user) throws ScimException{

        LOGGER.info("Creating user");

        // Call service to create user
        GenericScimResource createdUser = userService.createUser(user);

        // Extract user ID for Location header
        String userId = extractUserId(createdUser);
        // BEGIN: Use UriBuilder instead of string concatenation for Location header
        String location = jakarta.ws.rs.core.UriBuilder.fromPath("/Users").path(userId).build().toString();
        // END: Use UriBuilder instead of string concatenation for Location header

        // Return 201 Created with Location header
        return Response.status(Response.Status.CREATED)
                .header("Location", location)
                .entity(createdUser)
                .build();
    }

    /**
     * Update a user (full replace).
     *
     * PUT /Users/{id}
     *
     * @param id the user ID
     * @param user the updated user resource
     * @param ifMatch the If-Match header for optimistic locking (optional)
     * @return the updated user resource
     */
    @PUT
    @Path("/{id}")
    public Response updateUser(
            @PathParam("id") String id,
            GenericScimResource user,
            @HeaderParam("If-Match") String ifMatch) throws ScimException{
        LOGGER.info("Updating user: " + id);
        // Extract revision from If-Match header (may be in quotes)
        String revision = extractRevision(ifMatch);
        // Call service to update user
        GenericScimResource updatedUser = userService.updateUser(id, user, revision);
        // Return 200 OK with updated user
        return Response.ok(updatedUser).build();
    }

    /**
     * Patch a user (partial update).
     *
     * PATCH /Users/{id}
     *
     * @param id the user ID
     * @param patchRequest the SCIM patch request
     * @param ifMatch the If-Match header for optimistic locking (optional)
     * @return the patched user resource
     */
    @PATCH
    @Path("/{id}")
    public Response patchUser(
            @PathParam("id") String id,
            String patchRequest,
            @HeaderParam("If-Match") String ifMatch) throws ScimException {

        LOGGER.info("Patching user: " + id);

        // Extract revision from If-Match header
        String revision = extractRevision(ifMatch);

        // BEGIN: Parse and convert SCIM patch operations to PingIDM format
        String idmPatchOperations;
        try {
            idmPatchOperations = patchConverter.convert(patchRequest);
            LOGGER.info("Converted SCIM PATCH to PingIDM format for user: " + id);
        } catch (FilterTranslationException e) {
            LOGGER.severe("Failed to convert SCIM PATCH operations: " + e.getMessage());
            throw new BadRequestException("Invalid PATCH request: " + e.getMessage());
        }
        // END: Parse and convert SCIM patch operations to PingIDM format

        // Call service to patch user
        GenericScimResource patchedUser = userService.patchUser(id, idmPatchOperations, revision);

        // Return 200 OK with patched user
        return Response.ok(patchedUser).build();
    }

    /**
     * Delete a user.
     *
     * DELETE /Users/{id}
     *
     * @param id the user ID
     * @param ifMatch the If-Match header for optimistic locking (optional)
     * @return 204 No Content on success
     */
    @DELETE
    @Path("/{id}")
    public Response deleteUser(
            @PathParam("id") String id,
            @HeaderParam("If-Match") String ifMatch) throws ScimException {
        LOGGER.info("Deleting user: " + id);

        // Extract revision from If-Match header
        String revision = extractRevision(ifMatch);
        // Call service to delete user
        userService.deleteUser(id, revision);
        // Return 204 No Content
        return Response.noContent().build();
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
     * Convert SCIM attributes/excludedAttributes to PingIDM _fields parameter.
     *
     * @param attributes comma-separated list of attributes to include
     * @param excludedAttributes comma-separated list of attributes to exclude
     * @return PingIDM _fields value
     */
    private String convertScimAttributesToIdmFields(String attributes, String excludedAttributes) {
        // If no attribute filtering requested, return all fields
        if ((attributes == null || attributes.trim().isEmpty()) &&
                (excludedAttributes == null || excludedAttributes.trim().isEmpty())) {
            return "*";
        }

        // If attributes specified, use those (excludedAttributes ignored when attributes present)
        if (attributes != null && !attributes.trim().isEmpty()) {
            // Map SCIM attributes to PingIDM field names
            List<String> idmFields = new ArrayList<>();
            String[] scimAttrs = attributes.split(",");

            for (String scimAttr : scimAttrs) {
                scimAttr = scimAttr.trim();
                String idmField = mapScimAttributeToIdm(scimAttr);
                if (idmField != null && !idmField.isEmpty()) {
                    idmFields.add(idmField);
                }
            }

            // Always include _id and _rev for metadata
            if (!idmFields.contains("_id")) {
                idmFields.add("_id");
            }
            if (!idmFields.contains("_rev")) {
                idmFields.add("_rev");
            }
            // BEGIN: Always include userName - required attribute per RFC 7643 Section 4.1
            if (!idmFields.contains("userName")) {
                idmFields.add("userName");
            }
            // END: Always include userName - required attribute per RFC 7643 Section 4.1

            return String.join(",", idmFields);
        }

        // If only excludedAttributes specified, return all fields
        // (PingIDM doesn't support exclude-only filtering via _fields)
        return "*";
    }

    /**
     * Map SCIM attribute name to PingIDM field name.
     */
    private String mapScimAttributeToIdm(String scimAttribute) {
        if (scimAttribute == null || scimAttribute.isEmpty()) {
            return null;
        }

        // Handle complex attributes (e.g., "name.givenName")
        if (scimAttribute.contains(".")) {
            String[] parts = scimAttribute.split("\\.", 2);
            String parent = parts[0];
            String child = parts[1];

            if ("name".equals(parent)) {
                return switch (child) {
                    case "givenName" -> "givenName";
                    case "familyName" -> "sn";
                    case "formatted" -> "cn";
                    case "middleName" -> "middleName";
                    default -> null;
                };
            }
            return null;
        }

        // Simple attribute mapping
        return switch (scimAttribute) {
            case "id" -> "_id";
            case "userName" -> "userName";
            case "displayName" -> "displayName";
            case "active" -> "accountStatus";
            case "emails" -> "mail";
            case "phoneNumbers" -> "telephoneNumber";
            case "title" -> "title";
            case "preferredLanguage" -> "preferredLanguage";
            case "locale" -> "locale";
            case "timezone" -> "timezone";
            case "meta" -> "_rev,_meta";
            default -> scimAttribute; // Pass through if not mapped
        };
    }

    /**
     * Extract user ID from GenericScimResource.
     */
    private String extractUserId(GenericScimResource user) {
        try {
            return user.getId();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to extract user ID", e);
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