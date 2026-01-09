package com.pingidentity.p1aic.scim.service;
import com.pingidentity.p1aic.scim.client.PingIdmRestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

            // Check response status
            if (response.getStatus() != Response.Status.CREATED.getStatusCode() &&
                    response.getStatus() != Response.Status.OK.getStatusCode()) {
                handleErrorResponse(response, "Failed to create role");
            }

            // Parse response body
            String responseBody = response.readEntity(String.class);
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
        try {
            LOGGER.info("Getting role: " + roleId);

            // Build endpoint URL
            String endpoint = restClient.getManagedRolesEndpoint() + "/" + roleId;

            // Call PingIDM get API
            Response response = restClient.get(endpoint);

            // Check if role exists
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                throw new ResourceNotFoundException("Role not found: " + roleId);
            }

            // Check response status
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                handleErrorResponse(response, "Failed to get role");
            }

            // Parse response body
            String responseBody = response.readEntity(String.class);
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

            // Check if role exists
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                throw new ResourceNotFoundException("Role not found: " + roleId);
            }

            // Check response status
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                handleErrorResponse(response, "Failed to update role");
            }

            // Parse response body
            String responseBody = response.readEntity(String.class);
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

            // Check if role exists
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                throw new ResourceNotFoundException("Role not found: " + roleId);
            }

            // Check response status
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                handleErrorResponse(response, "Failed to patch role");
            }

            // Parse response body
            String responseBody = response.readEntity(String.class);
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

            // Check if role exists
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                throw new ResourceNotFoundException("Role not found: " + roleId);
            }

            // Check response status
            if (response.getStatus() != Response.Status.OK.getStatusCode() &&
                    response.getStatus() != Response.Status.NO_CONTENT.getStatusCode()) {
                handleErrorResponse(response, "Failed to delete role");
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
        try {
            LOGGER.info("Searching roles with filter: " + queryFilter);

            // Build endpoint URL with query parameters
            String endpoint = restClient.getManagedRolesEndpoint();

            // Build query parameters
            List<String> queryParams = new ArrayList<>();

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

            // Call PingIDM query API
            Response response = restClient.get(endpoint, queryParams.toArray(new String[0]));

            // Check response status
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                handleErrorResponse(response, "Failed to search roles");
            }

            // Parse response body
            String responseBody = response.readEntity(String.class);
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

            // Extract total count
            int totalResults = resultNode.has("resultCount") ?
                    resultNode.get("resultCount").asInt() : resources.size();

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
     * Handle error responses from PingIDM.
     */
    private void handleErrorResponse(Response response, String defaultMessage) throws ScimException {
        String errorMessage = defaultMessage;

        try {
            String responseBody = response.readEntity(String.class);
            JsonNode errorNode = objectMapper.readTree(responseBody);

            // Try to extract error message from PingIDM response
            if (errorNode.has("message")) {
                errorMessage = errorNode.get("message").asText();
            } else if (errorNode.has("detail")) {
                errorMessage = errorNode.get("detail").asText();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse error response", e);
        }

        // Map HTTP status to appropriate SCIM exception
        int status = response.getStatus();
        if (status == Response.Status.NOT_FOUND.getStatusCode()) {
            throw new ResourceNotFoundException(errorMessage);
        } else if (status == Response.Status.CONFLICT.getStatusCode()) {
            throw new BadRequestException(errorMessage);
        } else if (status == Response.Status.BAD_REQUEST.getStatusCode()) {
            throw new BadRequestException(errorMessage);
        } else {
            throw new BadRequestException(errorMessage + " (HTTP " + status + ")");
        }
    }
}
