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
 * OPTIMIZED: JAX-RS endpoint for SCIM 2.0 User resources.
 *
 * Enhancements:
 * - Proper handling of count=0 per RFC 7644 (returns only totalResults)
 * - Uses PingIDM's _countOnly parameter for better performance
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
     * OPTIMIZED: Properly handles count=0 per RFC 7644 Section 3.4.2.4
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
            @QueryParam("excludedAttributes") String excludedAttributes) {

        try {
            // Apply default startIndex
            int start = (startIndex != null && startIndex > 0) ? startIndex : DEFAULT_START_INDEX;

            // BEGIN: Properly handle count parameter including count=0
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
            // END: Properly handle count parameter

            LOGGER.info(String.format("Searching users: filter=%s, startIndex=%d, count=%d, attributes=%s, excludedAttributes=%s",
                    filter, start, pageSize, attributes, excludedAttributes));

            // TODO: Convert SCIM filter to PingIDM query filter
            String queryFilter = filter;

            // Convert SCIM attributes to PingIDM fields
            String idmFields = convertScimAttributesToIdmFields(attributes, excludedAttributes);

            // Call service to search users
            // Service layer will use _countOnly=true when pageSize=0
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

            return String.join(",", idmFields);
        }

        // If only excludedAttributes specified, return all fields
        // (PingIDM doesn't support exclude-only filtering via _fields)
        // Would need to fetch all and filter in response
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

    // ... rest of the methods (getUser, createUser, updateUser, etc.) remain unchanged ...

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