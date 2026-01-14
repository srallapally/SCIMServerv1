package com.pingidentity.p1aic.scim.endpoints;

import com.pingidentity.p1aic.scim.service.PingIdmUserService;
// BEGIN: Add imports for filter and patch conversion
import com.pingidentity.p1aic.scim.filter.ScimFilterConverter;
import com.pingidentity.p1aic.scim.filter.ScimPatchConverter;
import com.pingidentity.p1aic.scim.exceptions.FilterTranslationException;
// END: Add imports for filter and patch conversion
// BEGIN: Import CustomAttributeMappingConfig for PATCH operations
import com.pingidentity.p1aic.scim.config.CustomAttributeMappingConfig;
import com.pingidentity.p1aic.scim.config.ScimServerConfig;
// END: Import CustomAttributeMappingConfig
// BEGIN: Add Jackson imports for bypassing SDK validation on custom attributes
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
// END: Add Jackson imports for bypassing SDK validation
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
 * <p>Provides CRUD operations and search functionality for users.</p>
 * <p>Path: /scim/v2/Users</p>
 *
 * <p>MODIFIED: Now integrates with {@link CustomAttributeMappingConfig} to handle
 * custom attribute mappings in PATCH operations for attributes that PingIDM
 * doesn't support OOTB (e.g., Enterprise User extension attributes).</p>
 *
 * <p>Enhancements:</p>
 * <ul>
 *   <li>Proper handling of count=0 per RFC 7644 (returns only totalResults)</li>
 *   <li>Uses PingIDM's _countOnly parameter for better performance</li>
 *   <li>Supports attribute projection via attributes/excludedAttributes parameters</li>
 *   <li>Custom attribute mapping support for PATCH operations</li>
 * </ul>
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

    @Inject
    private PingIdmUserService userService;

    // BEGIN: Inject CustomAttributeMappingConfig for PATCH operations
    @Inject
    private CustomAttributeMappingConfig customMappingConfig;
    // END: Inject CustomAttributeMappingConfig

    // BEGIN: Add ObjectMapper for manual JSON parsing to bypass SDK validation
    private final ObjectMapper objectMapper = new ObjectMapper();
    // END: Add ObjectMapper for manual JSON parsing

    private final ScimFilterConverter filterConverter;
    // BEGIN: Change patchConverter to be lazily initialized with custom mappings
    private ScimPatchConverter patchConverter;
    // END: Change patchConverter initialization

    public UserScimEndpoint() {
        this.filterConverter = new ScimFilterConverter();
        // BEGIN: Defer patchConverter initialization to allow injection
        // patchConverter will be initialized lazily when first needed
        this.patchConverter = null;
        // END: Defer patchConverter initialization
    }

    // BEGIN: Add lazy initialization method for patchConverter
    /**
     * Get the PATCH converter, initializing it with custom mappings if available.
     * This allows the converter to use injected CustomAttributeMappingConfig.
     *
     * @return the initialized ScimPatchConverter
     */
    private ScimPatchConverter getPatchConverter() {
        if (patchConverter == null) {
            String managedUserObject = ScimServerConfig.getInstance().getManagedUserObjectName();
            if (managedUserObject == null || managedUserObject.isEmpty()) {
                managedUserObject = "alpha_user";
            }
            // Initialize with custom mapping config if available
            patchConverter = new ScimPatchConverter("User", managedUserObject, customMappingConfig);
            LOGGER.info("Initialized ScimPatchConverter with custom mapping support");
        }
        return patchConverter;
    }
    // END: Add lazy initialization method for patchConverter

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
            @QueryParam("excludedAttributes") String excludedAttributes) throws ScimException {

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

        // Convert SCIM filter to PingIDM query filter
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

        // Convert SCIM attributes to PingIDM fields
        String idmFields = convertScimAttributesToIdmFields(attributes, excludedAttributes);

        // Call service to search users
        ListResponse<GenericScimResource> listResponse =
                userService.searchUsers(queryFilter, start, pageSize, idmFields);

        return Response.ok(listResponse).build();
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
            @QueryParam("excludedAttributes") String excludedAttributes) throws ScimException {

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
     * @param userJson the user resource JSON as String
     * @return the created user resource
     */
    @POST
    public Response createUser(String userJson) throws ScimException {

        LOGGER.info("Creating user");

        try {
            // BEGIN: Parse JSON directly without SDK validation to support custom attributes
            ObjectNode userNode = (ObjectNode) objectMapper.readTree(userJson);
            LOGGER.info("Parsed user JSON successfully");
            GenericScimResource user = new GenericScimResource(userNode);
            // END: Parse JSON directly without SDK validation

            // Call service to create user
            GenericScimResource createdUser = userService.createUser(user);

            // Extract user ID for Location header
            String userId = extractUserId(createdUser);
            String location = jakarta.ws.rs.core.UriBuilder.fromPath("/Users").path(userId).build().toString();

            // Return 201 Created with Location header
            return Response.status(Response.Status.CREATED)
                    .header("Location", location)
                    .entity(createdUser)
                    .build();
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "Invalid JSON in request", e);
            throw new BadRequestException("Invalid JSON format: " + e.getMessage());
        }
    }

    /**
     * Update a user (full replace).
     *
     * PUT /Users/{id}
     *
     * @param id the user ID
     * @param userJson the updated user resource JSON as String
     * @param ifMatch the If-Match header for optimistic locking (optional)
     * @return the updated user resource
     */
    @PUT
    @Path("/{id}")
    public Response updateUser(
            @PathParam("id") String id,
            String userJson,
            @HeaderParam("If-Match") String ifMatch) throws ScimException {

        LOGGER.info("Updating user: " + id);

        try {
            // BEGIN: Parse JSON directly without SDK validation to support custom attributes
            ObjectNode userNode = (ObjectNode) objectMapper.readTree(userJson);
            GenericScimResource user = new GenericScimResource(userNode);
            // END: Parse JSON directly without SDK validation

            // Extract revision from If-Match header (may be in quotes)
            String revision = extractRevision(ifMatch);

            // Call service to update user
            GenericScimResource updatedUser = userService.updateUser(id, user, revision);

            // Return 200 OK with updated user
            return Response.ok(updatedUser).build();
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "Invalid JSON in request", e);
            throw new BadRequestException("Invalid JSON format: " + e.getMessage());
        }
    }

    /**
     * Patch a user (partial update).
     *
     * PATCH /Users/{id}
     *
     * @param id the user ID
     * @param patchJson the SCIM PATCH request JSON as String
     * @param ifMatch the If-Match header for optimistic locking (optional)
     * @return the patched user resource
     */
    @PATCH
    @Path("/{id}")
    public Response patchUser(
            @PathParam("id") String id,
            String patchJson,
            @HeaderParam("If-Match") String ifMatch) throws ScimException {

        LOGGER.info("Patching user: " + id);

        try {
            // BEGIN: Parse JSON directly without SDK validation to support custom attributes
            ObjectNode patchNode = (ObjectNode) objectMapper.readTree(patchJson);
            GenericScimResource patchRequest = new GenericScimResource(patchNode);
            // END: Parse JSON directly without SDK validation

            // Extract revision from If-Match header
            String revision = extractRevision(ifMatch);

            // BEGIN: Use lazy-initialized patchConverter with custom mappings
            String idmPatchOperations;
            try {
                idmPatchOperations = getPatchConverter().convert(patchJson);
                LOGGER.info("Converted SCIM PATCH to PingIDM format for user: " + id);
            } catch (FilterTranslationException e) {
                LOGGER.severe("Failed to convert SCIM PATCH operations: " + e.getMessage());
                throw new BadRequestException("Invalid PATCH request: " + e.getMessage());
            }
            // END: Use lazy-initialized patchConverter

            // Call service to patch user
            GenericScimResource patchedUser = userService.patchUser(id, idmPatchOperations, revision);

            // Return 200 OK with patched user
            return Response.ok(patchedUser).build();
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "Invalid JSON in request", e);
            throw new BadRequestException("Invalid JSON format: " + e.getMessage());
        }
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
            // Always include userName - required attribute per RFC 7643 Section 4.1
            if (!idmFields.contains("userName")) {
                idmFields.add("userName");
            }

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

    /**
     * Build error response from ScimException.
     */
    private Response buildErrorResponse(ScimException e) {
        int statusCode = e.getScimError() != null ?
                e.getScimError().getStatus() : Response.Status.BAD_REQUEST.getStatusCode();

        String errorResponse = String.format(
                "{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:Error\"]," +
                        "\"status\":\"%d\"," +
                        "\"detail\":\"%s\"}",
                statusCode,
                escapeJson(e.getMessage())
        );

        return Response.status(statusCode)
                .entity(errorResponse)
                .type("application/scim+json")
                .build();
    }

    /**
     * Build error response with custom status and message.
     */
    private Response buildErrorResponse(Response.Status status, String message) {
        String errorResponse = String.format(
                "{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:Error\"]," +
                        "\"status\":\"%d\"," +
                        "\"detail\":\"%s\"}",
                status.getStatusCode(),
                escapeJson(message)
        );

        return Response.status(status)
                .entity(errorResponse)
                .type("application/scim+json")
                .build();
    }

    /**
     * Escape special characters in JSON strings.
     */
    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}