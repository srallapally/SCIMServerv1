package com.pingidentity.p1aic.scim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.client.PingIdmRestClient;
import com.pingidentity.p1aic.scim.mapping.GroupAttributeMapper;
import com.unboundid.scim2.common.GenericScimResource;
import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.messages.ListResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service class for PingIDM managed role operations.
 *
 * This service uses PingIdmRestClient to interact with PingIDM REST APIs
 * and GroupAttributeMapper to convert between SCIM Group and PingIDM role formats.
 */
public class PingIdmRoleService {

    private static final Logger LOGGER = Logger.getLogger(PingIdmRoleService.class.getName());

    @Inject
    private PingIdmRestClient restClient;

    private final GroupAttributeMapper attributeMapper;
    private final ObjectMapper objectMapper;

    /**
     * Constructor initializes the attribute mapper.
     */
    public PingIdmRoleService() {
        this.attributeMapper = new GroupAttributeMapper();
        this.objectMapper = new ObjectMapper();
    }

    @Inject
    public PingIdmRoleService(PingIdmRestClient restClient) {
        this.restClient = restClient;
        this.attributeMapper = new GroupAttributeMapper();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Create a new role in PingIDM.
     *
     * @param scimGroup the SCIM group resource to create
     * @return the created role as SCIM group resource
     * @throws ScimException if creation fails
     */
    public GenericScimResource createRole(GenericScimResource scimGroup) throws ScimException {
        try {
            // Convert SCIM group to PingIDM role format
            ObjectNode idmRole = attributeMapper.scimToPingIdm(scimGroup);

            // Convert to JSON string
            String jsonBody = objectMapper.writeValueAsString(idmRole);

            LOGGER.info("Creating role in PingIDM");

            // Call PingIDM create API
            String endpoint = restClient.getManagedRolesEndpoint();
            Response response = restClient.postWithAction(endpoint, "create", jsonBody);

            // Read response immediately
            int statusCode = response.getStatus();
            String responseBody = response.readEntity(String.class);

            // Check response status
            if (statusCode != Response.Status.CREATED.getStatusCode() &&
                    statusCode != Response.Status.OK.getStatusCode()) {
                handleErrorResponse(statusCode, responseBody, "Failed to create role");
            }

            // Parse response body
            ObjectNode createdRole = (ObjectNode) objectMapper.readTree(responseBody);

            // Convert back to SCIM format
            return attributeMapper.pingIdmToScim(createdRole);

        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating role", e);
            throw new BadRequestException("Failed to create role: " + e.getMessage());
        }
    }

    /**
     * Get a role by ID from PingIDM.
     *
     * @param roleId the role ID
     * @return the role as SCIM group resource
     * @throws ScimException if role not found or retrieval fails
     */
    public GenericScimResource getRole(String roleId) throws ScimException {
        return getRole(roleId, "*");
    }

    /**
     * Get a role by ID from PingIDM with field selection.
     *
     * @param roleId the role ID
     * @param fields the PingIDM fields to return (e.g., "*" for all, or "name,description,members")
     * @return the role as SCIM group resource
     * @throws ScimException if role not found or retrieval fails
     */
    public GenericScimResource getRole(String roleId, String fields) throws ScimException {
        try {
            LOGGER.info("Getting role: " + roleId + ", fields: " + fields);

            // BEGIN: Use WebTarget-based getResource instead of string concatenation
            // Build endpoint URL using WebTarget.path() for safe URL construction
            String baseEndpoint = restClient.getManagedRolesEndpoint();
            // END: Use WebTarget-based getResource instead of string concatenation

            // Add fields parameter if specified
            Response response;
            if (fields != null && !fields.equals("*")) {
                // BEGIN: Use WebTarget-based getResource with query parameters
                response = restClient.getResource(baseEndpoint, roleId, "_fields", fields);
                // END: Use WebTarget-based getResource with query parameters
            } else {
                // BEGIN: Use WebTarget-based getResource without query parameters
                response = restClient.getResource(baseEndpoint, roleId);
                // END: Use WebTarget-based getResource without query parameters
            }

            // Read response immediately
            int statusCode = response.getStatus();
            String responseBody = response.readEntity(String.class);

            // Check if role exists
            if (statusCode == Response.Status.NOT_FOUND.getStatusCode()) {
                throw new ResourceNotFoundException("Role not found: " + roleId);
            }

            // Check response status
            if (statusCode != Response.Status.OK.getStatusCode()) {
                handleErrorResponse(statusCode, responseBody, "Failed to get role");
            }

            // Parse response body
            ObjectNode idmRole = (ObjectNode) objectMapper.readTree(responseBody);

            // Convert to SCIM format
            return attributeMapper.pingIdmToScim(idmRole);

        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting role", e);
            throw new BadRequestException("Failed to get role: " + e.getMessage());
        }
    }

    /**
     * Update a role in PingIDM (full replace).
     *
     * @param roleId the role ID to update
     * @param scimGroup the updated SCIM group resource
     * @param revision the revision/etag for optimistic locking (optional)
     * @return the updated role as SCIM group resource
     * @throws ScimException if update fails
     */
    public GenericScimResource updateRole(String roleId, GenericScimResource scimGroup, String revision)
            throws ScimException {
        try {
            // Convert SCIM group to PingIDM role format
            ObjectNode idmRole = attributeMapper.scimToPingIdm(scimGroup);

            // Convert to JSON string
            String jsonBody = objectMapper.writeValueAsString(idmRole);

            LOGGER.info("Updating role: " + roleId);

            // BEGIN: Use WebTarget-based putResource instead of string concatenation
            // Build endpoint URL using WebTarget.path() for safe URL construction
            String baseEndpoint = restClient.getManagedRolesEndpoint();
            // END: Use WebTarget-based putResource instead of string concatenation

            // Call PingIDM update API
            Response response;
            if (revision != null && !revision.isEmpty()) {
                // BEGIN: Use WebTarget-based putResource with revision
                response = restClient.putResource(baseEndpoint, roleId, jsonBody, revision);
                // END: Use WebTarget-based putResource with revision
            } else {
                // BEGIN: Use WebTarget-based putResource without revision
                response = restClient.putResource(baseEndpoint, roleId, jsonBody);
                // END: Use WebTarget-based putResource without revision
            }

            // Read response immediately
            int statusCode = response.getStatus();
            String responseBody = response.readEntity(String.class);

            // Check if role exists
            if (statusCode == Response.Status.NOT_FOUND.getStatusCode()) {
                throw new ResourceNotFoundException("Role not found: " + roleId);
            }

            // Check response status
            if (statusCode != Response.Status.OK.getStatusCode()) {
                handleErrorResponse(statusCode, responseBody, "Failed to update role");
            }

            // Parse response body
            ObjectNode updatedRole = (ObjectNode) objectMapper.readTree(responseBody);

            // Convert back to SCIM format
            return attributeMapper.pingIdmToScim(updatedRole);

        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating role", e);
            throw new BadRequestException("Failed to update role: " + e.getMessage());
        }
    }

    /**
     * Patch a role in PingIDM (partial update).
     *
     * @param roleId the role ID to patch
     * @param patchOperations the SCIM patch operations (as JSON string)
     * @param revision the revision/etag for optimistic locking (optional)
     * @return the patched role as SCIM group resource
     * @throws ScimException if patch fails
     */
    public GenericScimResource patchRole(String roleId, String patchOperations, String revision)
            throws ScimException {
        try {
            LOGGER.info("Patching role: " + roleId);

            // BEGIN: Use WebTarget-based patchResource instead of string concatenation
            // Build endpoint URL using WebTarget.path() for safe URL construction
            String baseEndpoint = restClient.getManagedRolesEndpoint();
            // END: Use WebTarget-based patchResource instead of string concatenation

            // Call PingIDM patch API
            Response response;
            if (revision != null && !revision.isEmpty()) {
                // BEGIN: Use WebTarget-based patchResource with revision
                response = restClient.patchResource(baseEndpoint, roleId, patchOperations, revision);
                // END: Use WebTarget-based patchResource with revision
            } else {
                // BEGIN: Use WebTarget-based patchResource without revision
                response = restClient.patchResource(baseEndpoint, roleId, patchOperations);
                // END: Use WebTarget-based patchResource without revision
            }

            // Read response immediately
            int statusCode = response.getStatus();
            String responseBody = response.readEntity(String.class);

            // Check if role exists
            if (statusCode == Response.Status.NOT_FOUND.getStatusCode()) {
                throw new ResourceNotFoundException("Role not found: " + roleId);
            }

            // Check response status
            if (statusCode != Response.Status.OK.getStatusCode()) {
                handleErrorResponse(statusCode, responseBody, "Failed to patch role");
            }

            // Parse response body
            ObjectNode patchedRole = (ObjectNode) objectMapper.readTree(responseBody);

            // Convert back to SCIM format
            return attributeMapper.pingIdmToScim(patchedRole);

        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error patching role", e);
            throw new BadRequestException("Failed to patch role: " + e.getMessage());
        }
    }

    /**
     * Delete a role from PingIDM.
     *
     * @param roleId the role ID to delete
     * @param revision the revision/etag for optimistic locking (optional)
     * @throws ScimException if deletion fails
     */
    public void deleteRole(String roleId, String revision) throws ScimException {
        try {
            LOGGER.info("Deleting role: " + roleId);

            // BEGIN: Use WebTarget-based deleteResource instead of string concatenation
            // Build endpoint URL using WebTarget.path() for safe URL construction
            String baseEndpoint = restClient.getManagedRolesEndpoint();
            // END: Use WebTarget-based deleteResource instead of string concatenation

            // Call PingIDM delete API
            Response response;
            if (revision != null && !revision.isEmpty()) {
                // BEGIN: Use WebTarget-based deleteResource with revision
                response = restClient.deleteResource(baseEndpoint, roleId, revision);
                // END: Use WebTarget-based deleteResource with revision
            } else {
                // BEGIN: Use WebTarget-based deleteResource without revision
                response = restClient.deleteResource(baseEndpoint, roleId);
                // END: Use WebTarget-based deleteResource without revision
            }

            // Read response immediately
            int statusCode = response.getStatus();
            String responseBody = response.readEntity(String.class);

            // Check if role exists
            if (statusCode == Response.Status.NOT_FOUND.getStatusCode()) {
                throw new ResourceNotFoundException("Role not found: " + roleId);
            }

            // Check response status
            if (statusCode != Response.Status.OK.getStatusCode() &&
                    statusCode != Response.Status.NO_CONTENT.getStatusCode()) {
                handleErrorResponse(statusCode, responseBody, "Failed to delete role");
            }

        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error deleting role", e);
            throw new BadRequestException("Failed to delete role: " + e.getMessage());
        }
    }

    /**
     * Search/list roles from PingIDM.
     *
     * @param queryFilter the PingIDM query filter (optional)
     * @param startIndex the 1-based start index for pagination
     * @param count the number of results to return
     * @return ListResponse containing the roles as SCIM groups
     * @throws ScimException if search fails
     */
    public ListResponse<GenericScimResource> searchRoles(String queryFilter, int startIndex, int count)
            throws ScimException {
        return searchRoles(queryFilter, startIndex, count, "*");
    }

    /**
     * Search/list roles from PingIDM with field selection.
     *
     * <p>This method performs two calls to PingIDM:</p>
     * <ol>
     *   <li>A count-only query to get accurate totalResults (required for SCIM compliance)</li>
     *   <li>A paginated query to get the actual resources</li>
     * </ol>
     *
     * <p>PingIDM requires _countOnly=true with _totalPagedResultsPolicy=EXACT to return
     * accurate total counts. Without this, PingIDM returns -1 for totalPagedResults.</p>
     *
     * @param queryFilter the PingIDM query filter (optional)
     * @param startIndex the 1-based start index for pagination
     * @param count the number of results to return (0 for count-only query)
     * @param fields the PingIDM fields to return (e.g., "*" for all, or "name,description,members")
     * @return ListResponse containing the roles as SCIM groups
     * @throws ScimException if search fails
     */
    public ListResponse<GenericScimResource> searchRoles(String queryFilter, int startIndex, int count, String fields)
            throws ScimException {

        // BEGIN: count=0 optimization - use PingIDM _countOnly for performance
        if (count == 0) {
            LOGGER.info("Performing count-only query with filter: " + queryFilter);
            int totalResults = getTotalCount(queryFilter);
            return new ListResponse<>(totalResults, new ArrayList<>(), 1, 0);
        }
        // END: count=0 optimization

        try {
            LOGGER.info("Searching roles with filter: " + queryFilter + ", fields: " + fields);

            // BEGIN: First get accurate total count via count-only query
            int totalResults = getTotalCount(queryFilter);
            LOGGER.info("Count-only query returned totalResults: " + totalResults);
            // END: Get accurate total count

            // Build endpoint URL with query parameters
            String endpoint = restClient.getManagedRolesEndpoint();

            // Build query parameters for resource fetch (no _totalPagedResultsPolicy needed)
            List<String> queryParams = new ArrayList<>();

            // Add query filter parameter
            if (queryFilter != null && !queryFilter.isEmpty()) {
                queryParams.add("_queryFilter");
                queryParams.add(queryFilter);
            } else {
                // Use _queryFilter=true to return all roles
                queryParams.add("_queryFilter");
                queryParams.add("true");
            }

            // Add pagination parameters (convert SCIM 1-based to PingIDM 0-based)
            int pageOffset = Math.max(0, startIndex - 1);
            queryParams.add("_pageSize");
            queryParams.add(String.valueOf(count));
            queryParams.add("_pagedResultsOffset");
            queryParams.add(String.valueOf(pageOffset));

            // Add fields parameter
            queryParams.add("_fields");
            queryParams.add(fields != null ? fields : "*");

            // Log the full URL for debugging
            StringBuilder urlBuilder = new StringBuilder(endpoint);
            urlBuilder.append("?");
            for (int i = 0; i < queryParams.size(); i += 2) {
                if (i > 0) urlBuilder.append("&");
                urlBuilder.append(queryParams.get(i)).append("=").append(queryParams.get(i + 1));
            }
            LOGGER.info("PingIDM search URL: " + urlBuilder.toString());

            // Call PingIDM query API
            Response response = restClient.get(endpoint, queryParams.toArray(new String[0]));

            // Read response body before checking status
            int statusCode = response.getStatus();
            String responseBody = response.readEntity(String.class);

            if (statusCode != Response.Status.OK.getStatusCode()) {
                LOGGER.severe("PingIDM search failed with status: " + statusCode + ", body: " + responseBody);
                handleErrorResponse(statusCode, responseBody, "Failed to search roles");
            }

            // Parse response body
            ObjectNode resultNode = (ObjectNode) objectMapper.readTree(responseBody);

            // Extract results array
            List<GenericScimResource> resources = new ArrayList<>();
            if (resultNode.has("result") && resultNode.get("result").isArray()) {
                ArrayNode results = (ArrayNode) resultNode.get("result");
                for (JsonNode roleNode : results) {
                    ObjectNode idmRole = (ObjectNode) roleNode;
                    GenericScimResource scimGroup = attributeMapper.pingIdmToScim(idmRole);
                    resources.add(scimGroup);
                }
            }

            LOGGER.info("Found " + totalResults + " total roles, returning " + resources.size() + " in this page");

            // Build SCIM ListResponse with accurate totalResults from count-only query
            return new ListResponse<>(totalResults, resources, startIndex, count);

        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error searching roles", e);
            throw new BadRequestException("Failed to search roles: " + e.getMessage());
        }
    }

    /**
     * Get the total count of roles matching the filter using PingIDM's _countOnly parameter.
     *
     * <p>This is the only reliable way to get accurate total counts from PingIDM.
     * Without _countOnly=true and _totalPagedResultsPolicy=EXACT, PingIDM returns -1.</p>
     *
     * @param queryFilter the PingIDM query filter (optional, null or empty means all roles)
     * @return the total count of matching roles
     * @throws ScimException if count query fails
     */
    private int getTotalCount(String queryFilter) throws ScimException {
        try {
            LOGGER.fine("Getting total role count with filter: " + queryFilter);

            String endpoint = restClient.getManagedRolesEndpoint();

            // Build query parameters for count-only request
            List<String> queryParams = new ArrayList<>();

            // Add query filter
            if (queryFilter != null && !queryFilter.isEmpty()) {
                queryParams.add("_queryFilter");
                queryParams.add(queryFilter);
            } else {
                queryParams.add("_queryFilter");
                queryParams.add("true");
            }

            // Add count-only flag (PingIDM specific) - THIS IS REQUIRED for accurate counts
            queryParams.add("_countOnly");
            queryParams.add("true");

            // Add total paged results policy for accuracy - THIS IS REQUIRED
            queryParams.add("_totalPagedResultsPolicy");
            queryParams.add("EXACT");

            // Log the URL
            StringBuilder urlBuilder = new StringBuilder(endpoint);
            urlBuilder.append("?");
            for (int i = 0; i < queryParams.size(); i += 2) {
                if (i > 0) urlBuilder.append("&");
                urlBuilder.append(queryParams.get(i)).append("=").append(queryParams.get(i + 1));
            }
            LOGGER.fine("PingIDM role count-only URL: " + urlBuilder.toString());

            // BEGIN: Use getWithProtocolVersion for count-only queries
            // PingIDM requires Accept-API-Version: protocol=2.2,resource=1.0 for _countOnly=true
            Response response = restClient.getWithProtocolVersion(endpoint, queryParams.toArray(new String[0]));
            // END: Use getWithProtocolVersion for count-only queries

            int statusCode = response.getStatus();
            String responseBody = response.readEntity(String.class);

            if (statusCode != Response.Status.OK.getStatusCode()) {
                LOGGER.severe("PingIDM role count query failed with status: " + statusCode + ", body: " + responseBody);
                handleErrorResponse(statusCode, responseBody, "Failed to count roles");
            }

            // Parse response
            ObjectNode resultNode = (ObjectNode) objectMapper.readTree(responseBody);

            // Extract count from PingIDM response
            // With _countOnly=true, PingIDM returns: {"totalPagedResults": X}
            int totalResults = extractTotalCount(resultNode, 0);

            LOGGER.fine("Role count-only query returned: " + totalResults);
            return totalResults;

        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in role count-only query", e);
            throw new BadRequestException("Failed to count roles: " + e.getMessage());
        }
    }

    /**
     * Extract total count from PingIDM response, handling different field names.
     *
     * <p>PingIDM may return -1 for totalPagedResults when the total count is unknown
     * (e.g., for large datasets or when _totalPagedResultsPolicy is not EXACT with _countOnly).
     * Per RFC 7644 Section 3.4.2, totalResults MUST be a non-negative integer,
     * so we handle the -1 case by using the fallback count.</p>
     *
     * @param resultNode the PingIDM response JSON
     * @param fallbackCount fallback value if no count field found or value is -1
     * @return the total count (always non-negative per SCIM spec)
     */
    private int extractTotalCount(ObjectNode resultNode, int fallbackCount) {
        // BEGIN: Handle PingIDM's -1 value for unknown total count
        if (resultNode.has("totalPagedResults")) {
            int total = resultNode.get("totalPagedResults").asInt();
            // PingIDM returns -1 when total is unknown; use fallback per RFC 7644 compliance
            if (total >= 0) {
                return total;
            }
            LOGGER.fine("PingIDM returned totalPagedResults=-1 (unknown), using fallback count");
        }
        if (resultNode.has("resultCount")) {
            int total = resultNode.get("resultCount").asInt();
            if (total >= 0) {
                return total;
            }
        }
        // END: Handle PingIDM's -1 value
        return fallbackCount;
    }

    /**
     * Handle error responses from PingIDM.
     * Updated to accept status code and response body separately.
     */
    private void handleErrorResponse(int statusCode, String responseBody, String defaultMessage) throws ScimException {
        String errorMessage = defaultMessage;

        try {
            if (responseBody != null && !responseBody.isEmpty()) {
                JsonNode errorNode = objectMapper.readTree(responseBody);

                // Try to extract error message from PingIDM response
                if (errorNode.has("message")) {
                    errorMessage = errorNode.get("message").asText();
                } else if (errorNode.has("detail")) {
                    errorMessage = errorNode.get("detail").asText();
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse error response", e);
        }

        // Map HTTP status to appropriate SCIM exception
        if (statusCode == Response.Status.NOT_FOUND.getStatusCode()) {
            throw new ResourceNotFoundException(errorMessage);
        } else if (statusCode == Response.Status.CONFLICT.getStatusCode()) {
            throw new BadRequestException(errorMessage);
        } else if (statusCode == Response.Status.BAD_REQUEST.getStatusCode()) {
            throw new BadRequestException(errorMessage);
        } else {
            throw new BadRequestException(errorMessage + " (HTTP " + statusCode + ")");
        }
    }
}