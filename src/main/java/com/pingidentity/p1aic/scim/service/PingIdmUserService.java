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
 * OPTIMIZED: Service class for PingIDM managed user operations.
 *
 * Enhancements:
 * - Uses _countOnly=true for count=0 requests (RFC 7644 compliance)
 * - Supports efficient total count retrieval without fetching resources
 */
public class PingIdmUserService {

    private static final Logger LOGGER = Logger.getLogger(PingIdmUserService.class.getName());

    @Inject
    private PingIdmRestClient restClient;

    private final UserAttributeMapper attributeMapper;
    private final ObjectMapper objectMapper;

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
     * Search/list users from PingIDM with field selection.
     * OPTIMIZED: Uses _countOnly=true when count=0 for better performance.
     *
     * @param queryFilter the PingIDM query filter (optional)
     * @param startIndex the 1-based start index for pagination
     * @param count the number of results to return (0 = count only)
     * @param fields the PingIDM fields to return (e.g., "*" for all)
     * @return ListResponse containing the users
     * @throws ScimException if search fails
     */
    public ListResponse<GenericScimResource> searchUsers(String queryFilter, int startIndex, int count, String fields)
            throws ScimException {
        try {
            // BEGIN: Optimization for count=0
            if (count == 0) {
                // Use efficient count-only query
                return searchUsersCountOnly(queryFilter);
            }
            // END: Optimization for count=0

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

            // Extract total count
            int totalResults = extractTotalCount(resultNode, resources.size());

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
     * BEGIN: NEW METHOD - Efficient count-only query
     *
     * Perform a count-only query using PingIDM's _countOnly parameter.
     * This is much more efficient than fetching all resources when only the count is needed.
     *
     * Used when SCIM client requests count=0 per RFC 7644.
     *
     * @param queryFilter the PingIDM query filter (optional)
     * @return ListResponse with totalResults populated, empty Resources array
     * @throws ScimException if count query fails
     */
    private ListResponse<GenericScimResource> searchUsersCountOnly(String queryFilter) throws ScimException {
        try {
            LOGGER.info("Performing count-only query with filter: " + queryFilter);

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

            // Add count-only flag (PingIDM specific)
            queryParams.add("_countOnly");
            queryParams.add("true");

            // Add total paged results policy for accuracy
            queryParams.add("_totalPagedResultsPolicy");
            queryParams.add("EXACT");

            // Log the URL
            StringBuilder urlBuilder = new StringBuilder(endpoint);
            urlBuilder.append("?");
            for (int i = 0; i < queryParams.size(); i += 2) {
                if (i > 0) urlBuilder.append("&");
                urlBuilder.append(queryParams.get(i)).append("=").append(queryParams.get(i + 1));
            }
            LOGGER.info("PingIDM count-only URL: " + urlBuilder.toString());

            // Call PingIDM count API
            Response response = restClient.get(endpoint, queryParams.toArray(new String[0]));

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
            int totalResults = 0;
            if (resultNode.has("totalPagedResults")) {
                totalResults = resultNode.get("totalPagedResults").asInt();
            } else if (resultNode.has("resultCount")) {
                totalResults = resultNode.get("resultCount").asInt();
            }

            LOGGER.info("Count-only query returned: " + totalResults + " total users");

            // Return ListResponse with count but no resources (per SCIM spec for count=0)
            return new ListResponse<>(
                    totalResults,           // totalResults
                    new ArrayList<>(),      // Empty resources array
                    1,                      // startIndex (doesn't matter for count=0)
                    0                       // itemsPerPage = 0
            );

        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in count-only query", e);
            throw new BadRequestException("Failed to count users: " + e.getMessage());
        }
    }
    // END: NEW METHOD

    /**
     * BEGIN: HELPER METHOD - Extract total count from PingIDM response
     *
     * @param resultNode the PingIDM response JSON
     * @param fallbackCount fallback count if not found in response
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
    // END: HELPER METHOD

    /**
     * Search/list users from PingIDM (backward compatible - returns all fields).
     */
    public ListResponse<GenericScimResource> searchUsers(String queryFilter, int startIndex, int count)
            throws ScimException {
        return searchUsers(queryFilter, startIndex, count, "*");
    }

    // ... rest of the methods (getUser, createUser, updateUser, etc.) remain unchanged ...

    /**
     * Handle error responses from PingIDM.
     */
    private void handleErrorResponse(int statusCode, String responseBody, String defaultMessage) throws ScimException {
        String errorMessage = defaultMessage;

        try {
            JsonNode errorNode = objectMapper.readTree(responseBody);
            if (errorNode.has("message")) {
                errorMessage = errorNode.get("message").asText();
            } else if (errorNode.has("detail")) {
                errorMessage = errorNode.get("detail").asText();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse error response", e);
        }

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