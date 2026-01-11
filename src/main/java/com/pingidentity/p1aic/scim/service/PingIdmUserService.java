package com.pingidentity.p1aic.scim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.client.PingIdmRestClient;
import com.pingidentity.p1aic.scim.mapping.UserAttributeMapper;
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
 * Service class for PingIDM managed user operations.
 *
 * This service uses PingIdmRestClient to interact with PingIDM REST APIs
 * and UserAttributeMapper to convert between SCIM and PingIDM formats.
 */
public class PingIdmUserService {

    private static final Logger LOGGER = Logger.getLogger(PingIdmUserService.class.getName());

    @Inject
    private PingIdmRestClient restClient;

    private final UserAttributeMapper attributeMapper;
    private final ObjectMapper objectMapper;

    /**
     * Constructor initializes the attribute mapper.
     */
    public PingIdmUserService() {
        this.attributeMapper = new UserAttributeMapper();
        this.objectMapper = new ObjectMapper();
    }

    @Inject
    public PingIdmUserService(PingIdmRestClient restClient) {
        this.restClient = restClient;
        this.attributeMapper = new UserAttributeMapper();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Create a new user in PingIDM.
     *
     * @param scimUser the SCIM user resource to create
     * @return the created user as SCIM resource
     * @throws ScimException if creation fails
     */
    public GenericScimResource createUser(GenericScimResource scimUser) throws ScimException {
        try {
            // Convert SCIM user to PingIDM format
            ObjectNode idmUser = attributeMapper.scimToPingIdm(scimUser);

            // Convert to JSON string
            String jsonBody = objectMapper.writeValueAsString(idmUser);

            LOGGER.info("Creating user in PingIDM");

            // Call PingIDM create API
            String endpoint = restClient.getManagedUsersEndpoint();
            Response response = restClient.postWithAction(endpoint, "create", jsonBody);

            // Read response immediately
            int statusCode = response.getStatus();
            String responseBody = response.readEntity(String.class);

            // Check response status
            if (statusCode != Response.Status.CREATED.getStatusCode() &&
                    statusCode != Response.Status.OK.getStatusCode()) {
                handleErrorResponse(statusCode, responseBody, "Failed to create user");
            }

            // Parse response body
            ObjectNode createdUser = (ObjectNode) objectMapper.readTree(responseBody);

            // Convert back to SCIM format
            return attributeMapper.pingIdmToScim(createdUser);

        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating user", e);
            throw new BadRequestException("Failed to create user: " + e.getMessage());
        }
    }

    /**
     * Get a user by ID from PingIDM.
     *
     * @param userId the user ID
     * @return the user as SCIM resource
     * @throws ScimException if user not found or retrieval fails
     */
    public GenericScimResource getUser(String userId) throws ScimException {
        return getUser(userId, "*");
    }

    /**
     * Get a user by ID from PingIDM with field selection.
     *
     * @param userId the user ID
     * @param fields the PingIDM fields to return (e.g., "*" for all, or "userName,mail,givenName")
     * @return the user as SCIM resource
     * @throws ScimException if user not found or retrieval fails
     */
    public GenericScimResource getUser(String userId, String fields) throws ScimException {
        try {
            LOGGER.info("Getting user: " + userId + ", fields: " + fields);

            // Build endpoint URL
            String endpoint = restClient.getManagedUsersEndpoint() + "/" + userId;

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

            // Check if user exists
            if (statusCode == Response.Status.NOT_FOUND.getStatusCode()) {
                throw new ResourceNotFoundException("User not found: " + userId);
            }

            // Check response status
            if (statusCode != Response.Status.OK.getStatusCode()) {
                handleErrorResponse(statusCode, responseBody, "Failed to get user");
            }

            // Parse response body
            ObjectNode idmUser = (ObjectNode) objectMapper.readTree(responseBody);

            // Convert to SCIM format
            return attributeMapper.pingIdmToScim(idmUser);

        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting user", e);
            throw new BadRequestException("Failed to get user: " + e.getMessage());
        }
    }

    /**
     * Update a user in PingIDM (full replace).
     *
     * @param userId the user ID to update
     * @param scimUser the updated SCIM user resource
     * @param revision the revision/etag for optimistic locking (optional)
     * @return the updated user as SCIM resource
     * @throws ScimException if update fails
     */
    public GenericScimResource updateUser(String userId, GenericScimResource scimUser, String revision)
            throws ScimException {
        try {
            // Convert SCIM user to PingIDM format
            ObjectNode idmUser = attributeMapper.scimToPingIdm(scimUser);

            // Convert to JSON string
            String jsonBody = objectMapper.writeValueAsString(idmUser);

            LOGGER.info("Updating user: " + userId);

            // Build endpoint URL
            String endpoint = restClient.getManagedUsersEndpoint() + "/" + userId;

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

            // Check if user exists
            if (statusCode == Response.Status.NOT_FOUND.getStatusCode()) {
                throw new ResourceNotFoundException("User not found: " + userId);
            }

            // Check response status
            if (statusCode != Response.Status.OK.getStatusCode()) {
                handleErrorResponse(statusCode, responseBody, "Failed to update user");
            }

            // Parse response body
            ObjectNode updatedUser = (ObjectNode) objectMapper.readTree(responseBody);

            // Convert back to SCIM format
            return attributeMapper.pingIdmToScim(updatedUser);

        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating user", e);
            throw new BadRequestException("Failed to update user: " + e.getMessage());
        }
    }

    /**
     * Patch a user in PingIDM (partial update).
     *
     * @param userId the user ID to patch
     * @param patchOperations the SCIM patch operations (as JSON string)
     * @param revision the revision/etag for optimistic locking (optional)
     * @return the patched user as SCIM resource
     * @throws ScimException if patch fails
     */
    public GenericScimResource patchUser(String userId, String patchOperations, String revision)
            throws ScimException {
        try {
            LOGGER.info("Patching user: " + userId);

            // Build endpoint URL
            String endpoint = restClient.getManagedUsersEndpoint() + "/" + userId;

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

            // Check if user exists
            if (statusCode == Response.Status.NOT_FOUND.getStatusCode()) {
                throw new ResourceNotFoundException("User not found: " + userId);
            }

            // Check response status
            if (statusCode != Response.Status.OK.getStatusCode()) {
                handleErrorResponse(statusCode, responseBody, "Failed to patch user");
            }

            // Parse response body
            ObjectNode patchedUser = (ObjectNode) objectMapper.readTree(responseBody);

            // Convert back to SCIM format
            return attributeMapper.pingIdmToScim(patchedUser);

        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error patching user", e);
            throw new BadRequestException("Failed to patch user: " + e.getMessage());
        }
    }

    /**
     * Delete a user from PingIDM.
     *
     * @param userId the user ID to delete
     * @param revision the revision/etag for optimistic locking (optional)
     * @throws ScimException if deletion fails
     */
    public void deleteUser(String userId, String revision) throws ScimException {
        try {
            LOGGER.info("Deleting user: " + userId);

            // Build endpoint URL
            String endpoint = restClient.getManagedUsersEndpoint() + "/" + userId;

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

            // Check if user exists
            if (statusCode == Response.Status.NOT_FOUND.getStatusCode()) {
                throw new ResourceNotFoundException("User not found: " + userId);
            }

            // Check response status
            if (statusCode != Response.Status.OK.getStatusCode() &&
                    statusCode != Response.Status.NO_CONTENT.getStatusCode()) {
                handleErrorResponse(statusCode, responseBody, "Failed to delete user");
            }

        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error deleting user", e);
            throw new BadRequestException("Failed to delete user: " + e.getMessage());
        }
    }

    /**
     * Search/list users from PingIDM (backward compatible - returns all fields).
     *
     * @param queryFilter the PingIDM query filter (optional)
     * @param startIndex the 1-based start index for pagination
     * @param count the number of results to return
     * @return ListResponse containing the users
     * @throws ScimException if search fails
     */
    public ListResponse<GenericScimResource> searchUsers(String queryFilter, int startIndex, int count)
            throws ScimException {
        return searchUsers(queryFilter, startIndex, count, "*");
    }

    /**
     * Search/list users from PingIDM with field selection.
     *
     * @param queryFilter the PingIDM query filter (optional)
     * @param startIndex the 1-based start index for pagination
     * @param count the number of results to return
     * @param fields the PingIDM fields to return (e.g., "*" for all, or "userName,mail,givenName")
     * @return ListResponse containing the users
     * @throws ScimException if search fails
     */
    public ListResponse<GenericScimResource> searchUsers(String queryFilter, int startIndex, int count, String fields)
            throws ScimException {
        try {
            LOGGER.info("Searching users with filter: " + queryFilter + ", fields: " + fields);

            // Build endpoint URL with query parameters
            String endpoint = restClient.getManagedUsersEndpoint();

            // Build query parameters
            List<String> queryParams = new ArrayList<>();

            // Add query filter parameter
            if (queryFilter != null && !queryFilter.isEmpty()) {
                queryParams.add("_queryFilter");
                queryParams.add(queryFilter);
            } else {
                // Use _queryFilter=true to return all users
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
                handleErrorResponse(statusCode, responseBody, "Failed to search users");
            }

            // Parse response body
            ObjectNode resultNode = (ObjectNode) objectMapper.readTree(responseBody);

            // Extract results array
            List<GenericScimResource> resources = new ArrayList<>();
            if (resultNode.has("result") && resultNode.get("result").isArray()) {
                ArrayNode results = (ArrayNode) resultNode.get("result");
                for (JsonNode userNode : results) {
                    ObjectNode idmUser = (ObjectNode) userNode;
                    GenericScimResource scimUser = attributeMapper.pingIdmToScim(idmUser);
                    resources.add(scimUser);
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

            LOGGER.info("Found " + totalResults + " total users, returning " + resources.size() + " in this page");

            // Build SCIM ListResponse
            return new ListResponse<>(totalResults, resources, startIndex, count);

        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error searching users", e);
            throw new BadRequestException("Failed to search users: " + e.getMessage());
        }
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