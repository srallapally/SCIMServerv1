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

            // Build endpoint URL
            String endpoint = restClient.getManagedRolesEndpoint() + "/" + roleId;

            // Add fields parameter if specified
            Response response;
            if (fields != null && !fields.equals("*")) {
                response = restClient.get(endpoint, "_fields", fields);
            } else {
                response = restClient.get(endpoint);
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

            // Build endpoint URL
            String endpoint = restClient.getManagedRolesEndpoint() + "/" + roleId;

            // Call PingIDM update API
            Response response;
            if (revision != null && !revision.isEmpty()) {
                response = restClient.put(endpoint, jsonBody, revision);
            } else {
                response = restClient.put(endpoint, jsonBody);
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

            // Build endpoint URL
            String endpoint = restClient.getManagedRolesEndpoint() + "/" + roleId;

            // Call PingIDM patch API
            Response response;
            if (revision != null && !revision.isEmpty()) {
                response = restClient.patch(endpoint, patchOperations, revision);
            } else {
                response = restClient.patch(endpoint, patchOperations);
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

            // Build endpoint URL
            String endpoint = restClient.getManagedRolesEndpoint() + "/" + roleId;

            // Call PingIDM delete API
            Response response;
            if (revision != null && !revision.isEmpty()) {
                response = restClient.delete(endpoint, revision);
            } else {
                response = restClient.delete(endpoint);
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
            return searchRolesCountOnly(queryFilter);
        }
        // END: count=0 optimization

        try {
            LOGGER.info("Searching roles with filter: " + queryFilter + ", fields: " + fields);

            // Build endpoint URL with query parameters
            String endpoint = restClient.getManagedRolesEndpoint();

            // Build query parameters
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

            // Add total paged results policy
            queryParams.add("_totalPagedResultsPolicy");
            queryParams.add("EXACT");

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

            // Extract total count from different possible fields
            int totalResults = 0;
            if (resultNode.has("totalPagedResults")) {
                totalResults = resultNode.get("totalPagedResults").asInt();
            } else if (resultNode.has("resultCount")) {
                totalResults = resultNode.get("resultCount").asInt();
            } else {
                totalResults = resources.size();
            }

            LOGGER.info("Found " + totalResults + " total roles, returning " + resources.size() + " in this page");

            // Build SCIM ListResponse
            return new ListResponse<>(totalResults, resources, startIndex, count);

        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error searching roles", e);
            throw new BadRequestException("Failed to search roles: " + e.getMessage());
        }
    }

    /**
     * Perform a count-only query to PingIDM for efficiency when count=0.
     * Uses PingIDM's _countOnly parameter to avoid fetching actual role data.
     *
     * @param queryFilter the PingIDM query filter (optional)
     * @return ListResponse with totalResults populated but empty Resources array
     * @throws ScimException if the count query fails
     */
    private ListResponse<GenericScimResource> searchRolesCountOnly(String queryFilter) throws ScimException {
        try {
            String endpoint = restClient.getManagedRolesEndpoint();

            // Build query parameters for count-only query
            List<String> queryParams = new ArrayList<>();

            // Add query filter
            queryParams.add("_queryFilter");
            queryParams.add(queryFilter != null && !queryFilter.isEmpty() ? queryFilter : "true");

            // Add _countOnly parameter for optimization
            queryParams.add("_countOnly");
            queryParams.add("true");

            // Add total paged results policy
            queryParams.add("_totalPagedResultsPolicy");
            queryParams.add("EXACT");

            // Log the count-only request
            StringBuilder urlBuilder = new StringBuilder(endpoint);
            urlBuilder.append("?");
            for (int i = 0; i < queryParams.size(); i += 2) {
                if (i > 0) urlBuilder.append("&");
                urlBuilder.append(queryParams.get(i)).append("=").append(queryParams.get(i + 1));
            }
            LOGGER.info("PingIDM count-only URL: " + urlBuilder.toString());

            // Call PingIDM count-only API
            Response response = restClient.get(endpoint, queryParams.toArray(new String[0]));

            // Read response
            String responseBody = response.readEntity(String.class);
            int statusCode = response.getStatus();

            // Check response status
            if (statusCode != Response.Status.OK.getStatusCode()) {
                LOGGER.severe("PingIDM count-only query failed with status: " + statusCode + ", body: " + responseBody);
                handleErrorResponse(statusCode, responseBody, "Failed to get role count");
            }

            // Parse response - PingIDM returns just {"totalPagedResults": N} for _countOnly
            ObjectNode resultNode = (ObjectNode) objectMapper.readTree(responseBody);

            int totalResults = extractTotalCount(resultNode, 0);

            LOGGER.info("Count-only query returned: " + totalResults + " total roles");

            // Return ListResponse with empty resources but correct total
            return new ListResponse<>(
                    totalResults,           // totalResults from PingIDM
                    new ArrayList<>(),      // Empty resources array
                    1,                      // startIndex = 1 (default)
                    0                       // itemsPerPage = 0 (count=0)
            );

        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in count-only role query", e);
            throw new BadRequestException("Failed to get role count: " + e.getMessage());
        }
    }

    /**
     * Extract total count from PingIDM response, handling different field names.
     *
     * @param resultNode the PingIDM response JSON
     * @param fallbackCount fallback value if no count field found
     * @return the total count
     */
    private int extractTotalCount(ObjectNode resultNode, int fallbackCount) {
        if (resultNode.has("totalPagedResults")) {
            return resultNode.get("totalPagedResults").asInt();
        } else if (resultNode.has("resultCount")) {
            return resultNode.get("resultCount").asInt();
        }
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