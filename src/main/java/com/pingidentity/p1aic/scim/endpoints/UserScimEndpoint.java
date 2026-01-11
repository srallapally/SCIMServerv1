package com.pingidentity.p1aic.scim.endpoints;

import com.pingidentity.p1aic.scim.service.PingIdmUserService;
import com.unboundid.scim2.common.GenericScimResource;
import com.unboundid.scim2.common.exceptions.ScimException;
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
 */
@Path("/Users")
@Produces("application/scim+json")
@Consumes("application/scim+json")
public class UserScimEndpoint {

    private static final Logger LOGGER = Logger.getLogger(UserScimEndpoint.class.getName());

    // Default pagination values
    private static final int DEFAULT_START_INDEX = 1;
    private static final int DEFAULT_COUNT = 100;
    private static final int MAX_COUNT = 1000;

    @Inject
    private PingIdmUserService userService;

    /**
     * Search/List users.
     *
     * GET /Users?filter={filter}&startIndex={startIndex}&count={count}&attributes={attributes}
     *
     * @param filter SCIM filter expression (optional)
     * @param startIndex 1-based start index for pagination (default: 1)
     * @param count number of results to return (default: 100, max: 1000)
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
            @QueryParam("excludedAttributes") String excludedAttributes) {

        try {
            // Apply default values
            int start = (startIndex != null && startIndex > 0) ? startIndex : DEFAULT_START_INDEX;
            int pageSize = (count != null && count > 0) ? Math.min(count, MAX_COUNT) : DEFAULT_COUNT;

            LOGGER.info(String.format("Searching users: filter=%s, startIndex=%d, count=%d, attributes=%s, excludedAttributes=%s",
                    filter, start, pageSize, attributes, excludedAttributes));

            // TODO: Convert SCIM filter to PingIDM query filter
            String queryFilter = filter;

            // Convert SCIM attributes to PingIDM fields
            String idmFields = convertScimAttributesToIdmFields(attributes, excludedAttributes);

            // Call service to search users
            ListResponse<GenericScimResource> listResponse =
                    userService.searchUsers(queryFilter, start, pageSize, idmFields);

            return Response.ok(listResponse).build();

        } catch (ScimException e) {
            LOGGER.log(Level.SEVERE, "SCIM exception in searchUsers", e);
            return buildErrorResponse(e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected exception in searchUsers", e);
            return buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "Internal server error: " + e.getMessage());
        }
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
            @QueryParam("excludedAttributes") String excludedAttributes) {

        try {
            LOGGER.info("Getting user: " + id);

            // Convert SCIM attributes to PingIDM fields
            String idmFields = convertScimAttributesToIdmFields(attributes, excludedAttributes);

            // Call service to get user
            GenericScimResource user = userService.getUser(id, idmFields);

            // Return 200 OK with user resource
            return Response.ok(user).build();

        } catch (ScimException e) {
            LOGGER.log(Level.SEVERE, "SCIM exception in getUser", e);
            return buildErrorResponse(e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected exception in getUser", e);
            return buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "Internal server error: " + e.getMessage());
        }
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
    public Response createUser(GenericScimResource user) {

        try {
            LOGGER.info("Creating user");

            // Call service to create user
            GenericScimResource createdUser = userService.createUser(user);

            // Extract user ID for Location header
            String userId = extractUserId(createdUser);
            String location = "/Users/" + userId;

            // Return 201 Created with Location header
            return Response.status(Response.Status.CREATED)
                    .header("Location", location)
                    .entity(createdUser)
                    .build();

        } catch (ScimException e) {
            LOGGER.log(Level.SEVERE, "SCIM exception in createUser", e);
            return buildErrorResponse(e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected exception in createUser", e);
            return buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "Internal server error: " + e.getMessage());
        }
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
            @HeaderParam("If-Match") String ifMatch) {

        try {
            LOGGER.info("Updating user: " + id);

            // Extract revision from If-Match header (may be in quotes)
            String revision = extractRevision(ifMatch);

            // Call service to update user
            GenericScimResource updatedUser = userService.updateUser(id, user, revision);

            // Return 200 OK with updated user
            return Response.ok(updatedUser).build();

        } catch (ScimException e) {
            LOGGER.log(Level.SEVERE, "SCIM exception in updateUser", e);
            return buildErrorResponse(e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected exception in updateUser", e);
            return buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "Internal server error: " + e.getMessage());
        }
    }

    /**
     * Patch a user (partial update).
     *
     * PATCH /Users/{id}
     *
     * @param id the user ID
     * @param patchRequest the SCIM patch request (as String for now)
     * @param ifMatch the If-Match header for optimistic locking (optional)
     * @return the patched user resource
     */
    @PATCH
    @Path("/{id}")
    public Response patchUser(
            @PathParam("id") String id,
            String patchRequest,
            @HeaderParam("If-Match") String ifMatch) {

        try {
            LOGGER.info("Patching user: " + id);

            // Extract revision from If-Match header
            String revision = extractRevision(ifMatch);

            // TODO: Parse and convert SCIM patch operations to PingIDM format
            // For now, pass patch request as-is (will be enhanced in Phase 4)

            // Call service to patch user
            GenericScimResource patchedUser = userService.patchUser(id, patchRequest, revision);

            // Return 200 OK with patched user
            return Response.ok(patchedUser).build();

        } catch (ScimException e) {
            LOGGER.log(Level.SEVERE, "SCIM exception in patchUser", e);
            return buildErrorResponse(e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected exception in patchUser", e);
            return buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "Internal server error: " + e.getMessage());
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
            @HeaderParam("If-Match") String ifMatch) {

        try {
            LOGGER.info("Deleting user: " + id);

            // Extract revision from If-Match header
            String revision = extractRevision(ifMatch);

            // Call service to delete user
            userService.deleteUser(id, revision);

            // Return 204 No Content
            return Response.noContent().build();

        } catch (ScimException e) {
            LOGGER.log(Level.SEVERE, "SCIM exception in deleteUser", e);
            return buildErrorResponse(e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected exception in deleteUser", e);
            return buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "Internal server error: " + e.getMessage());
        }
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

                // Map SCIM attributes to PingIDM fields
                switch (trimmed) {
                    case "id":
                        // Already added
                        break;
                    case "userName":
                        idmFields.add("userName");
                        break;
                    case "name":
                        // Include all name sub-attributes
                        idmFields.add("givenName");
                        idmFields.add("sn");
                        idmFields.add("cn");
                        idmFields.add("middleName");
                        idmFields.add("honorificPrefix");
                        idmFields.add("honorificSuffix");
                        break;
                    case "name.givenName":
                        idmFields.add("givenName");
                        break;
                    case "name.familyName":
                        idmFields.add("sn");
                        break;
                    case "name.formatted":
                        idmFields.add("cn");
                        break;
                    case "name.middleName":
                        idmFields.add("middleName");
                        break;
                    case "name.honorificPrefix":
                        idmFields.add("honorificPrefix");
                        break;
                    case "name.honorificSuffix":
                        idmFields.add("honorificSuffix");
                        break;
                    case "displayName":
                        idmFields.add("displayName");
                        break;
                    case "emails":
                    case "emails.value":
                        idmFields.add("mail");
                        break;
                    case "phoneNumbers":
                    case "phoneNumbers.value":
                        idmFields.add("telephoneNumber");
                        break;
                    case "active":
                        idmFields.add("accountStatus");
                        break;
                    case "title":
                        idmFields.add("title");
                        break;
                    case "preferredLanguage":
                        idmFields.add("preferredLanguage");
                        break;
                    case "locale":
                        idmFields.add("locale");
                        break;
                    case "timezone":
                        idmFields.add("timezone");
                        break;
                    case "profileUrl":
                        idmFields.add("profileUrl");
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
        // For now, return all fields and let the application filter
        if (excludedAttributes != null && !excludedAttributes.trim().isEmpty()) {
            LOGGER.warning("excludedAttributes not fully supported - fetching all fields and will filter in application");
            return "*";
        }

        return "*";
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

        // Build SCIM error response
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