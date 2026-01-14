package com.pingidentity.p1aic.scim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.client.PingIdmRestClient;
import com.pingidentity.p1aic.scim.mapping.UserAttributeMapper;
// BEGIN: Import CustomAttributeMapperService for custom attribute handling
import com.pingidentity.p1aic.scim.mapping.CustomAttributeMapperService;
// END: Import CustomAttributeMapperService
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
 * <p>This service uses PingIdmRestClient to interact with PingIDM REST APIs
 * and UserAttributeMapper to convert between SCIM User and PingIDM user formats.</p>
 *
 * <p>MODIFIED: Now integrates with {@link CustomAttributeMapperService} to handle
 * custom attribute mappings for attributes that PingIDM doesn't support OOTB
 * (e.g., Enterprise User extension attributes like employeeNumber, department).</p>
 *
 * <p>Enhancements:</p>
 * <ul>
 *   <li>Uses _countOnly=true for count=0 requests (RFC 7644 compliance)</li>
 *   <li>Supports efficient total count retrieval without fetching resources</li>
 *   <li>Supports field selection for optimized data retrieval</li>
 *   <li>Applies custom attribute mappings for inbound and outbound conversions</li>
 * </ul>
 */
public class PingIdmUserService {

    private static final Logger LOGGER = Logger.getLogger(PingIdmUserService.class.getName());

    @Inject
    private PingIdmRestClient restClient;

    // BEGIN: Inject CustomAttributeMapperService for custom attribute handling
    @Inject
    private CustomAttributeMapperService customAttributeMapper;
    // END: Inject CustomAttributeMapperService

    private final UserAttributeMapper attributeMapper;
    private final ObjectMapper objectMapper;

    /**
     * Default constructor.
     */
    public PingIdmUserService() {
        this.attributeMapper = new UserAttributeMapper();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Constructor with dependency injection.
     *
     * @param restClient the PingIDM REST client
     */
    @Inject
    public PingIdmUserService(PingIdmRestClient restClient) {
        this.restClient = restClient;
        this.attributeMapper = new UserAttributeMapper();
        this.objectMapper = new ObjectMapper();
    }

    // BEGIN: Constructor with all dependencies for testing
    /**
     * Constructor with all dependencies (for testing).
     *
     * @param restClient the PingIDM REST client
     * @param customAttributeMapper the custom attribute mapper service
     */
    public PingIdmUserService(PingIdmRestClient restClient, CustomAttributeMapperService customAttributeMapper) {
        this.restClient = restClient;
        this.customAttributeMapper = customAttributeMapper;
        this.attributeMapper = new UserAttributeMapper();
        this.objectMapper = new ObjectMapper();
    }
    // END: Constructor with all dependencies for testing

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

            // BEGIN: Apply custom inbound mappings (SCIM -> PingIDM)
            if (customAttributeMapper != null && customAttributeMapper.hasCustomMappings()) {
                ObjectNode scimUserNode = (ObjectNode) scimUser.getObjectNode();
                customAttributeMapper.applyInboundMappings(idmUser, scimUserNode);
                LOGGER.info("Applied custom inbound mappings for user creation");
            }
            // END: Apply custom inbound mappings

            // Convert to JSON string
            String jsonBody = objectMapper.writeValueAsString(idmUser);

            LOGGER.info("Creating user in PingIDM");
            LOGGER.fine("User JSON: " + jsonBody);

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
            ObjectNode createdIdmUser = (ObjectNode) objectMapper.readTree(responseBody);

            // Convert back to SCIM format
            GenericScimResource scimResult = attributeMapper.pingIdmToScim(createdIdmUser);

            // BEGIN: Apply custom outbound mappings (PingIDM -> SCIM)
            if (customAttributeMapper != null && customAttributeMapper.hasCustomMappings()) {
                ObjectNode scimResultNode = (ObjectNode) scimResult.getObjectNode();
                customAttributeMapper.applyOutboundMappings(scimResultNode, createdIdmUser);
                LOGGER.fine("Applied custom outbound mappings for created user");
            }
            // END: Apply custom outbound mappings

            return scimResult;

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
     * @param fields the PingIDM fields to return (e.g., "*" for all, or "userName,mail,_id,_rev")
     * @return the user as SCIM resource
     * @throws ScimException if user not found or retrieval fails
     */
    public GenericScimResource getUser(String userId, String fields) throws ScimException {
        try {
            LOGGER.info("Getting user: " + userId + ", fields: " + fields);

            // BEGIN: Append custom mapped PingIDM fields to ensure they are retrieved
            String enhancedFields = enhanceFieldsWithCustomMappings(fields);
            // END: Append custom mapped fields

            // Build endpoint URL using WebTarget.path() for safe URL construction
            String baseEndpoint = restClient.getManagedUsersEndpoint();

            // Add fields parameter if specified
            Response response;
            if (enhancedFields != null && !enhancedFields.equals("*")) {
                response = restClient.getResource(baseEndpoint, userId, "_fields", enhancedFields);
            } else {
                response = restClient.getResource(baseEndpoint, userId);
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
            GenericScimResource scimUser = attributeMapper.pingIdmToScim(idmUser);

            // BEGIN: Apply custom outbound mappings (PingIDM -> SCIM)
            if (customAttributeMapper != null && customAttributeMapper.hasCustomMappings()) {
                ObjectNode scimUserNode = (ObjectNode) scimUser.getObjectNode();
                customAttributeMapper.applyOutboundMappings(scimUserNode, idmUser);
                LOGGER.fine("Applied custom outbound mappings for user: " + userId);
            }
            // END: Apply custom outbound mappings

            return scimUser;

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

            // BEGIN: Apply custom inbound mappings (SCIM -> PingIDM)
            if (customAttributeMapper != null && customAttributeMapper.hasCustomMappings()) {
                ObjectNode scimUserNode = (ObjectNode) scimUser.getObjectNode();
                customAttributeMapper.applyInboundMappings(idmUser, scimUserNode);
                LOGGER.fine("Applied custom inbound mappings for user update: " + userId);
            }
            // END: Apply custom inbound mappings

            // Convert to JSON string
            String jsonBody = objectMapper.writeValueAsString(idmUser);

            LOGGER.info("Updating user: " + userId);

            // Build endpoint URL using WebTarget.path() for safe URL construction
            String baseEndpoint = restClient.getManagedUsersEndpoint();

            // Call PingIDM update API
            Response response = restClient.putResource(baseEndpoint, userId, jsonBody, revision);

            // Read response immediately
            int statusCode = response.getStatus();
            String responseBody = response.readEntity(String.class);

            // Check response status
            if (statusCode != Response.Status.OK.getStatusCode()) {
                handleErrorResponse(statusCode, responseBody, "Failed to update user");
            }

            // Parse response body
            ObjectNode updatedIdmUser = (ObjectNode) objectMapper.readTree(responseBody);

            // Convert back to SCIM format
            GenericScimResource scimResult = attributeMapper.pingIdmToScim(updatedIdmUser);

            // BEGIN: Apply custom outbound mappings (PingIDM -> SCIM)
            if (customAttributeMapper != null && customAttributeMapper.hasCustomMappings()) {
                ObjectNode scimResultNode = (ObjectNode) scimResult.getObjectNode();
                customAttributeMapper.applyOutboundMappings(scimResultNode, updatedIdmUser);
                LOGGER.fine("Applied custom outbound mappings for updated user: " + userId);
            }
            // END: Apply custom outbound mappings

            return scimResult;

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
     * @param patchOperations the PingIDM patch operations JSON string
     * @param revision the revision/etag for optimistic locking (optional)
     * @return the patched user as SCIM resource
     * @throws ScimException if patch fails
     */
    public GenericScimResource patchUser(String userId, String patchOperations, String revision)
            throws ScimException {
        try {
            LOGGER.info("Patching user: " + userId);

            // Build endpoint URL
            String baseEndpoint = restClient.getManagedUsersEndpoint();

            // Call PingIDM patch API
            Response response = restClient.patchResource(baseEndpoint, userId, patchOperations, revision);

            // Read response immediately
            int statusCode = response.getStatus();
            String responseBody = response.readEntity(String.class);

            // Check response status
            if (statusCode != Response.Status.OK.getStatusCode()) {
                handleErrorResponse(statusCode, responseBody, "Failed to patch user");
            }

            // Parse response body
            ObjectNode patchedIdmUser = (ObjectNode) objectMapper.readTree(responseBody);

            // Convert back to SCIM format
            GenericScimResource scimResult = attributeMapper.pingIdmToScim(patchedIdmUser);

            // BEGIN: Apply custom outbound mappings (PingIDM -> SCIM)
            if (customAttributeMapper != null && customAttributeMapper.hasCustomMappings()) {
                ObjectNode scimResultNode = (ObjectNode) scimResult.getObjectNode();
                customAttributeMapper.applyOutboundMappings(scimResultNode, patchedIdmUser);
                LOGGER.fine("Applied custom outbound mappings for patched user: " + userId);
            }
            // END: Apply custom outbound mappings

            return scimResult;

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
            String baseEndpoint = restClient.getManagedUsersEndpoint();

            // Call PingIDM delete API
            Response response = restClient.deleteResource(baseEndpoint, userId, revision);

            // Read response immediately
            int statusCode = response.getStatus();
            String responseBody = response.readEntity(String.class);

            // Check response status (204 No Content or 200 OK are both acceptable)
            if (statusCode != Response.Status.NO_CONTENT.getStatusCode() &&
                    statusCode != Response.Status.OK.getStatusCode()) {
                handleErrorResponse(statusCode, responseBody, "Failed to delete user");
            }

            LOGGER.info("User deleted successfully: " + userId);

        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error deleting user", e);
            throw new BadRequestException("Failed to delete user: " + e.getMessage());
        }
    }

    /**
     * Search/list users from PingIDM with field selection.
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
     * @param count the number of results to return (0 for count-only)
     * @param fields the PingIDM fields to return
     * @return ListResponse containing the users
     * @throws ScimException if search fails
     */
    public ListResponse<GenericScimResource> searchUsers(String queryFilter, int startIndex, int count, String fields)
            throws ScimException {

        // Handle count=0 (count-only query per RFC 7644)
        if (count == 0) {
            int totalResults = getTotalCount(queryFilter);
            return new ListResponse<>(totalResults, new ArrayList<>(), 1, 0);
        }

        try {
            LOGGER.info(String.format("Searching users: filter=%s, startIndex=%d, count=%d, fields=%s",
                    queryFilter, startIndex, count, fields));

            // BEGIN: First get accurate total count via count-only query
            int totalResults = getTotalCount(queryFilter);
            LOGGER.info("Count-only query returned totalResults: " + totalResults);
            // END: Get accurate total count

            // BEGIN: Append custom mapped PingIDM fields to ensure they are retrieved
            String enhancedFields = enhanceFieldsWithCustomMappings(fields);
            // END: Append custom mapped fields

            String endpoint = restClient.getManagedUsersEndpoint();

            // Build query parameters for resource fetch
            List<String> queryParams = new ArrayList<>();

            // Add query filter
            if (queryFilter != null && !queryFilter.isEmpty()) {
                queryParams.add("_queryFilter");
                queryParams.add(queryFilter);
            } else {
                queryParams.add("_queryFilter");
                queryParams.add("true");
            }

            // Add pagination
            int pageOffset = startIndex - 1; // Convert 1-based to 0-based
            queryParams.add("_pagedResultsOffset");
            queryParams.add(String.valueOf(pageOffset));
            queryParams.add("_pageSize");
            queryParams.add(String.valueOf(count));

            // Add field selection
            if (enhancedFields != null && !enhancedFields.equals("*")) {
                queryParams.add("_fields");
                queryParams.add(enhancedFields);
            }

            // Log the URL for debugging
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

                    // BEGIN: Apply custom outbound mappings for each user in search results
                    if (customAttributeMapper != null && customAttributeMapper.hasCustomMappings()) {
                        ObjectNode scimUserNode = (ObjectNode) scimUser.getObjectNode();
                        customAttributeMapper.applyOutboundMappings(scimUserNode, idmUser);
                    }
                    // END: Apply custom outbound mappings

                    resources.add(scimUser);
                }
            }

            LOGGER.info("Found " + totalResults + " total users, returning " + resources.size() + " in this page");

            // Build SCIM ListResponse with accurate totalResults from count-only query
            return new ListResponse<>(totalResults, resources, startIndex, count);

        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error searching users", e);
            throw new BadRequestException("Failed to search users: " + e.getMessage());
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
     * Get the total count of users matching the filter using PingIDM's _countOnly parameter.
     *
     * <p>This is the only reliable way to get accurate total counts from PingIDM.
     * Without _countOnly=true and _totalPagedResultsPolicy=EXACT, PingIDM returns -1.</p>
     *
     * @param queryFilter the PingIDM query filter (optional, null or empty means all users)
     * @return the total count of matching users
     * @throws ScimException if count query fails
     */
    private int getTotalCount(String queryFilter) throws ScimException {
        try {
            LOGGER.fine("Getting total count with filter: " + queryFilter);

            String endpoint = restClient.getManagedUsersEndpoint();

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
            LOGGER.fine("PingIDM count-only URL: " + urlBuilder.toString());

            // BEGIN: Use getWithProtocolVersion for count-only queries
            // PingIDM requires Accept-API-Version: protocol=2.2,resource=1.0 for _countOnly=true
            Response response = restClient.getWithProtocolVersion(endpoint, queryParams.toArray(new String[0]));
            // END: Use getWithProtocolVersion for count-only queries

            int statusCode = response.getStatus();
            String responseBody = response.readEntity(String.class);

            if (statusCode != Response.Status.OK.getStatusCode()) {
                LOGGER.severe("PingIDM count query failed with status: " + statusCode + ", body: " + responseBody);
                handleErrorResponse(statusCode, responseBody, "Failed to count users");
            }

            // Parse response
            ObjectNode resultNode = (ObjectNode) objectMapper.readTree(responseBody);

            // Extract count from PingIDM response
            // With _countOnly=true, PingIDM returns: {"totalPagedResults": X}
            int totalResults = extractTotalCount(resultNode, 0);

            LOGGER.fine("Count-only query returned: " + totalResults);
            return totalResults;

        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in count-only query", e);
            throw new BadRequestException("Failed to count users: " + e.getMessage());
        }
    }

    // BEGIN: Helper method to enhance fields with custom mappings
    /**
     * Enhance the fields parameter to include custom-mapped PingIDM attributes.
     *
     * <p>This ensures that when specific fields are requested, we also retrieve
     * the custom-mapped PingIDM attributes so they can be included in the SCIM response.</p>
     *
     * @param fields the original fields parameter
     * @return enhanced fields including custom-mapped attributes
     */
    private String enhanceFieldsWithCustomMappings(String fields) {
        if (customAttributeMapper == null || !customAttributeMapper.hasCustomMappings()) {
            return fields;
        }

        // If requesting all fields, no need to enhance
        if (fields == null || fields.equals("*")) {
            return fields;
        }

        // Get the custom PingIDM fields
        String customFields = customAttributeMapper.getPingIdmFieldsParameter();
        if (customFields == null || customFields.isEmpty()) {
            return fields;
        }

        // Append custom fields
        return fields + "," + customFields;
    }
    // END: Helper method to enhance fields with custom mappings

    /**
     * Extract total count from PingIDM response.
     *
     * <p>PingIDM may return -1 for totalPagedResults when the total count is unknown
     * (e.g., for large datasets or when _totalPagedResultsPolicy is not EXACT).
     * Per RFC 7644 Section 3.4.2, totalResults MUST be a non-negative integer,
     * so we handle the -1 case by using the fallback count.</p>
     *
     * @param resultNode the PingIDM response JSON
     * @param fallbackCount fallback count if not found or unknown (-1) in response
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
     *
     * @param statusCode the HTTP status code
     * @param responseBody the response body as string
     * @param defaultMessage the default error message
     * @throws ScimException mapped from PingIDM error
     */
    private void handleErrorResponse(int statusCode, String responseBody, String defaultMessage) throws ScimException {
        String errorMessage = defaultMessage;

        try {
            if (responseBody != null && !responseBody.isEmpty()) {
                JsonNode errorNode = objectMapper.readTree(responseBody);
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