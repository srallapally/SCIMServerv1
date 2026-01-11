package com.pingidentity.p1aic.scim.endpoints;

import com.pingidentity.p1aic.scim.service.PingIdmRoleService;
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
 * JAX-RS endpoint for SCIM 2.0 Group resources.
 *
 * Provides CRUD operations and search functionality for groups/roles.
 * Path: /scim/v2/Groups
 */
@Path("/Groups")
@Produces("application/scim+json")
@Consumes("application/scim+json")
public class GroupScimEndpoint {

    private static final Logger LOGGER = Logger.getLogger(GroupScimEndpoint.class.getName());

    // Default pagination values
    private static final int DEFAULT_START_INDEX = 1;
    private static final int DEFAULT_COUNT = 100;
    private static final int MAX_COUNT = 1000;

    @Inject
    private PingIdmRoleService roleService;

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
            @QueryParam("excludedAttributes") String excludedAttributes) {

        try {
            // Apply default values
            int start = (startIndex != null && startIndex > 0) ? startIndex : DEFAULT_START_INDEX;
            int pageSize = (count != null && count > 0) ? Math.min(count, MAX_COUNT) : DEFAULT_COUNT;

            LOGGER.info(String.format("Searching groups: filter=%s, startIndex=%d, count=%d, attributes=%s, excludedAttributes=%s",
                    filter, start, pageSize, attributes, excludedAttributes));

            // TODO: Convert SCIM filter to PingIDM query filter
            String queryFilter = filter;

            // Convert SCIM attributes to PingIDM fields
            String idmFields = convertScimAttributesToIdmFields(attributes, excludedAttributes);

            // Call service to search roles
            ListResponse<GenericScimResource> listResponse =
                    roleService.searchRoles(queryFilter, start, pageSize, idmFields);

            return Response.ok(listResponse).build();

        } catch (ScimException e) {
            LOGGER.log(Level.SEVERE, "SCIM exception in searchGroups", e);
            return buildErrorResponse(e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected exception in searchGroups", e);
            return buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "Internal server error: " + e.getMessage());
        }
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
            @QueryParam("excludedAttributes") String excludedAttributes) {

        try {
            LOGGER.info("Getting group: " + id);

            // Convert SCIM attributes to PingIDM fields
            String idmFields = convertScimAttributesToIdmFields(attributes, excludedAttributes);

            // Call service to get role
            GenericScimResource group = roleService.getRole(id, idmFields);

            // Return 200 OK with group resource
            return Response.ok(group).build();

        } catch (ScimException e) {
            LOGGER.log(Level.SEVERE, "SCIM exception in getGroup", e);
            return buildErrorResponse(e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected exception in getGroup", e);
            return buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "Internal server error: " + e.getMessage());
        }
    }

    /**
     * Create a new group.
     *
     * POST /Groups
     *
     * @param group the group resource to create
     * @return the created group resource
     */
    @POST
    public Response createGroup(GenericScimResource group) {

        try {
            LOGGER.info("Creating group");

            // Call service to create role
            GenericScimResource createdGroup = roleService.createRole(group);

            // Extract group ID for Location header
            String groupId = extractGroupId(createdGroup);
            String location = "/Groups/" + groupId;

            // Return 201 Created with Location header
            return Response.status(Response.Status.CREATED)
                    .header("Location", location)
                    .entity(createdGroup)
                    .build();

        } catch (ScimException e) {
            LOGGER.log(Level.SEVERE, "SCIM exception in createGroup", e);
            return buildErrorResponse(e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected exception in createGroup", e);
            return buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "Internal server error: " + e.getMessage());
        }
    }

    /**
     * Update a group (full replace).
     *
     * PUT /Groups/{id}
     *
     * @param id the group ID
     * @param group the updated group resource
     * @param ifMatch the If-Match header for optimistic locking (optional)
     * @return the updated group resource
     */
    @PUT
    @Path("/{id}")
    public Response updateGroup(
            @PathParam("id") String id,
            GenericScimResource group,
            @HeaderParam("If-Match") String ifMatch) {

        try {
            LOGGER.info("Updating group: " + id);

            // Extract revision from If-Match header (may be in quotes)
            String revision = extractRevision(ifMatch);

            // Call service to update role
            GenericScimResource updatedGroup = roleService.updateRole(id, group, revision);

            // Return 200 OK with updated group
            return Response.ok(updatedGroup).build();

        } catch (ScimException e) {
            LOGGER.log(Level.SEVERE, "SCIM exception in updateGroup", e);
            return buildErrorResponse(e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected exception in updateGroup", e);
            return buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "Internal server error: " + e.getMessage());
        }
    }

    /**
     * Patch a group (partial update).
     *
     * PATCH /Groups/{id}
     *
     * @param id the group ID
     * @param patchRequest the SCIM patch request (as String for now)
     * @param ifMatch the If-Match header for optimistic locking (optional)
     * @return the patched group resource
     */
    @PATCH
    @Path("/{id}")
    public Response patchGroup(
            @PathParam("id") String id,
            String patchRequest,
            @HeaderParam("If-Match") String ifMatch) {

        try {
            LOGGER.info("Patching group: " + id);

            // Extract revision from If-Match header
            String revision = extractRevision(ifMatch);

            // TODO: Parse and convert SCIM patch operations to PingIDM format
            // For now, pass patch request as-is (will be enhanced in Phase 4)

            // Call service to patch role
            GenericScimResource patchedGroup = roleService.patchRole(id, patchRequest, revision);

            // Return 200 OK with patched group
            return Response.ok(patchedGroup).build();

        } catch (ScimException e) {
            LOGGER.log(Level.SEVERE, "SCIM exception in patchGroup", e);
            return buildErrorResponse(e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected exception in patchGroup", e);
            return buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "Internal server error: " + e.getMessage());
        }
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
            @HeaderParam("If-Match") String ifMatch) {

        try {
            LOGGER.info("Deleting group: " + id);

            // Extract revision from If-Match header
            String revision = extractRevision(ifMatch);

            // Call service to delete role
            roleService.deleteRole(id, revision);

            // Return 204 No Content
            return Response.noContent().build();

        } catch (ScimException e) {
            LOGGER.log(Level.SEVERE, "SCIM exception in deleteGroup", e);
            return buildErrorResponse(e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected exception in deleteGroup", e);
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
            LOGGER.log(Level.WARNING, "Failed to extract group ID", e);
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