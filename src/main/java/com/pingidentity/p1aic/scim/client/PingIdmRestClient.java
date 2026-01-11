package com.pingidentity.p1aic.scim.client;

import com.pingidentity.p1aic.scim.auth.OAuthTokenManager;
import com.pingidentity.p1aic.scim.config.ScimServerConfig;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Base REST client for making authenticated calls to PingIDM REST APIs.
 *
 * This client automatically adds required PingIDM headers and uses the OAuth
 * access token stored in ThreadLocal for the current request thread.
 *
 * The OAuth token is set by OAuthTokenFilter at the start of each request
 * and automatically cleaned up after the request completes.
 */
public class PingIdmRestClient {

    private static final Logger LOGGER = Logger.getLogger(PingIdmRestClient.class.getName());

    // PingIDM required headers
    private static final String HEADER_ACCEPT_API_VERSION = "Accept-API-Version";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_IF_MATCH = "If-Match";

    // PingIDM API version
    private static final String API_VERSION_RESOURCE = "resource=1.0";

    private final Client client;
    private final ScimServerConfig config;

    // ThreadLocal to store OAuth token for current request thread
    private static final ThreadLocal<String> currentOAuthToken = new ThreadLocal<>();

    private final OAuthTokenManager tokenManager;
    /**
     * Constructor initializes JAX-RS client.
     */
    public PingIdmRestClient() {
        this.client = ClientBuilder.newClient();
        this.config = ScimServerConfig.getInstance();
        this.tokenManager = new OAuthTokenManager();
    }

    /**
     * Set the OAuth token for the current request thread.
     * This should be called by OAuthTokenFilter at the start of each request.
     *
     * @param token the OAuth Bearer token
     */
    public static void setCurrentOAuthToken(String token) {
        currentOAuthToken.set(token);
        LOGGER.fine("OAuth token set for current thread");
    }

    /**
     * Clear the OAuth token after request completes.
     * This should be called by OAuthTokenFilter in the response filter.
     */
    public static void clearCurrentOAuthToken() {
        currentOAuthToken.remove();
        LOGGER.fine("OAuth token cleared for current thread");
    }

    /**
     * Get the OAuth token for the current request thread.
     *
     * @return the OAuth token, or null if not set
     */
    private String getCurrentOAuthToken() {
        return currentOAuthToken.get();
    }

    /**
     * Create a WebTarget for the given PingIDM endpoint URL.
     *
     * @param endpointUrl the full PingIDM endpoint URL
     * @return WebTarget configured for the endpoint
     */
    public WebTarget target(String endpointUrl) {
        return client.target(endpointUrl);
    }

    /**
     * Build a request with standard PingIDM headers and OAuth token.
     *
     * @param target the WebTarget to build the request from
     * @return Invocation.Builder with headers configured
     */
    public Invocation.Builder buildRequest(WebTarget target) {
        Invocation.Builder builder = target.request(MediaType.APPLICATION_JSON);

        // Add PingIDM API version header
        builder.header(HEADER_ACCEPT_API_VERSION, API_VERSION_RESOURCE);

        // Add OAuth Bearer token from ThreadLocal
        String token = getCurrentOAuthToken();
        if (token != null && !token.isEmpty()) {
            builder.header(HEADER_AUTHORIZATION, "Bearer " + token);
            LOGGER.fine("Added OAuth Bearer token to request");
        } else {
            LOGGER.warning("No OAuth access token available for PingIDM request");
        }

        return builder;
    }

    /**
     * Build a request for config/admin endpoints using server's OAuth credentials.
     * This is used during server startup when there's no incoming request token.
     *
     * @param target the WebTarget to build the request from
     * @return Invocation.Builder with headers configured
     * @throws Exception if token acquisition fails
     */
    private Invocation.Builder buildRequestWithServerToken(WebTarget target) throws Exception {
        Invocation.Builder builder = target.request(MediaType.APPLICATION_JSON);

        // Add PingIDM API version header
        builder.header(HEADER_ACCEPT_API_VERSION, API_VERSION_RESOURCE);

        // Get server OAuth token via client credentials
        String token = tokenManager.getAccessToken();
        builder.header(HEADER_AUTHORIZATION, "Bearer " + token);
        LOGGER.fine("Added server OAuth token to config request");

        return builder;
    }

    /**
     * Execute GET request to PingIDM config endpoints using server OAuth credentials.
     * Used during server startup for administrative operations.
     *
     * @param endpointUrl the PingIDM endpoint URL
     * @return Response from PingIDM
     * @throws Exception if request fails
    */
    public Response getConfig(String endpointUrl) throws Exception {
        LOGGER.info("PingIDM GET (Config): " + endpointUrl);

        WebTarget target = target(endpointUrl);
        return buildRequestWithServerToken(target).get();
    }
    /**
     * Execute GET request to PingIDM.
     *
     * @param endpointUrl the PingIDM endpoint URL
     * @return Response from PingIDM
     */
    public Response get(String endpointUrl) {
        // Log the complete URL
        LOGGER.info("PingIDM GET: " + endpointUrl);

        WebTarget target = target(endpointUrl);
        return buildRequest(target).get();
    }

    /**
     * Execute GET request with query parameters.
     *
     * @param endpointUrl the PingIDM endpoint URL
     * @param queryParams query parameters as key-value pairs
     * @return Response from PingIDM
     */
    public Response get(String endpointUrl, String... queryParams) {
        WebTarget target = target(endpointUrl);

        // Add query parameters (expects pairs: key1, value1, key2, value2, ...)
        for (int i = 0; i < queryParams.length; i += 2) {
            if (i + 1 < queryParams.length) {
                target = target.queryParam(queryParams[i], queryParams[i + 1]);
            }
        }

        // Build the complete URL for logging
        String completeUrl = buildCompleteUrl(endpointUrl, queryParams);
        LOGGER.info("PingIDM GET: " + completeUrl);

        return buildRequest(target).get();
    }

    /**
     * Execute POST request to PingIDM.
     *
     * @param endpointUrl the PingIDM endpoint URL
     * @param jsonBody the JSON body as String
     * @return Response from PingIDM
     */
    public Response post(String endpointUrl, String jsonBody) {
        LOGGER.info("PingIDM POST: " + endpointUrl);
        LOGGER.fine("Request body: " + jsonBody);

        WebTarget target = target(endpointUrl);
        return buildRequest(target)
                .post(Entity.entity(jsonBody, MediaType.APPLICATION_JSON));
    }

    /**
     * Execute POST request with action parameter (for PingIDM _action operations).
     *
     * @param endpointUrl the PingIDM endpoint URL
     * @param action the action to perform (e.g., "create")
     * @param jsonBody the JSON body as String
     * @return Response from PingIDM
     */
    public Response postWithAction(String endpointUrl, String action, String jsonBody) {
        String completeUrl = endpointUrl + "?_action=" + action;
        LOGGER.info("PingIDM POST: " + completeUrl);
        LOGGER.fine("Request body: " + jsonBody);

        WebTarget target = target(endpointUrl).queryParam("_action", action);
        return buildRequest(target)
                .post(Entity.entity(jsonBody, MediaType.APPLICATION_JSON));
    }

    /**
     * Execute PUT request to PingIDM (update/replace).
     *
     * @param endpointUrl the PingIDM endpoint URL (including resource ID)
     * @param jsonBody the JSON body as String
     * @return Response from PingIDM
     */
    public Response put(String endpointUrl, String jsonBody) {
        LOGGER.info("PingIDM PUT: " + endpointUrl);
        LOGGER.fine("Request body: " + jsonBody);

        WebTarget target = target(endpointUrl);
        return buildRequest(target)
                .put(Entity.entity(jsonBody, MediaType.APPLICATION_JSON));
    }

    /**
     * Execute PUT request with If-Match header for optimistic concurrency control.
     *
     * @param endpointUrl the PingIDM endpoint URL (including resource ID)
     * @param jsonBody the JSON body as String
     * @param revision the revision/etag value for If-Match header
     * @return Response from PingIDM
     */
    public Response put(String endpointUrl, String jsonBody, String revision) {
        LOGGER.info("PingIDM PUT: " + endpointUrl + " (If-Match: " + revision + ")");
        LOGGER.fine("Request body: " + jsonBody);

        WebTarget target = target(endpointUrl);
        Invocation.Builder builder = buildRequest(target);

        // Add If-Match header for optimistic locking
        if (revision != null && !revision.isEmpty()) {
            builder.header(HEADER_IF_MATCH, revision);
        }

        return builder.put(Entity.entity(jsonBody, MediaType.APPLICATION_JSON));
    }

    /**
     * Execute PATCH request to PingIDM (partial update).
     *
     * @param endpointUrl the PingIDM endpoint URL (including resource ID)
     * @param jsonPatch the JSON patch body as String
     * @return Response from PingIDM
     */
    public Response patch(String endpointUrl, String jsonPatch) {
        LOGGER.info("PingIDM PATCH: " + endpointUrl);
        LOGGER.fine("Request body: " + jsonPatch);

        WebTarget target = target(endpointUrl);
        return buildRequest(target)
                .method("PATCH", Entity.entity(jsonPatch, MediaType.APPLICATION_JSON));
    }

    /**
     * Execute PATCH request with If-Match header.
     *
     * @param endpointUrl the PingIDM endpoint URL (including resource ID)
     * @param jsonPatch the JSON patch body as String
     * @param revision the revision/etag value for If-Match header
     * @return Response from PingIDM
     */
    public Response patch(String endpointUrl, String jsonPatch, String revision) {
        LOGGER.info("PingIDM PATCH: " + endpointUrl + " (If-Match: " + revision + ")");
        LOGGER.fine("Request body: " + jsonPatch);

        WebTarget target = target(endpointUrl);
        Invocation.Builder builder = buildRequest(target);

        // Add If-Match header for optimistic locking
        if (revision != null && !revision.isEmpty()) {
            builder.header(HEADER_IF_MATCH, revision);
        }

        return builder.method("PATCH", Entity.entity(jsonPatch, MediaType.APPLICATION_JSON));
    }

    /**
     * Execute DELETE request to PingIDM.
     *
     * @param endpointUrl the PingIDM endpoint URL (including resource ID)
     * @return Response from PingIDM
     */
    public Response delete(String endpointUrl) {
        LOGGER.info("PingIDM DELETE: " + endpointUrl);

        WebTarget target = target(endpointUrl);
        return buildRequest(target).delete();
    }

    /**
     * Execute DELETE request with If-Match header.
     *
     * @param endpointUrl the PingIDM endpoint URL (including resource ID)
     * @param revision the revision/etag value for If-Match header
     * @return Response from PingIDM
     */
    public Response delete(String endpointUrl, String revision) {
        LOGGER.info("PingIDM DELETE: " + endpointUrl + " (If-Match: " + revision + ")");

        WebTarget target = target(endpointUrl);
        Invocation.Builder builder = buildRequest(target);

        // Add If-Match header for optimistic locking
        if (revision != null && !revision.isEmpty()) {
            builder.header(HEADER_IF_MATCH, revision);
        }

        return builder.delete();
    }

    /**
     * Build complete URL string with query parameters for logging.
     *
     * @param baseUrl the base URL
     * @param queryParams the query parameters as key-value pairs
     * @return the complete URL string
     */
    private String buildCompleteUrl(String baseUrl, String... queryParams) {
        if (queryParams == null || queryParams.length == 0) {
            return baseUrl;
        }

        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        urlBuilder.append("?");

        for (int i = 0; i < queryParams.length; i += 2) {
            if (i > 0) {
                urlBuilder.append("&");
            }
            if (i + 1 < queryParams.length) {
                urlBuilder.append(queryParams[i])
                        .append("=")
                        .append(urlEncode(queryParams[i + 1]));
            }
        }

        return urlBuilder.toString();
    }

    /**
     * URL encode a string value.
     *
     * @param value the value to encode
     * @return the URL-encoded value
     */
    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            LOGGER.warning("Failed to URL encode value: " + value);
            return value;
        }
    }

    /**
     * Get the PingIDM base URL from configuration.
     */
    public String getPingIdmBaseUrl() {
        return config.getPingIdmBaseUrl();
    }

    /**
     * Get the managed users endpoint URL from configuration.
     */
    public String getManagedUsersEndpoint() {
        return config.getManagedUsersEndpoint();
    }

    /**
     * Get the managed roles endpoint URL from configuration.
     */
    public String getManagedRolesEndpoint() {
        return config.getManagedRolesEndpoint();
    }

    /**
     * Get the config managed endpoint URL from configuration.
     */
    public String getConfigManagedEndpoint() {
        return config.getConfigManagedEndpoint();
    }

    public void close() {
        if (client != null) {
            client.close();
            LOGGER.info("PingIDM REST client closed");
        }
        // BEGIN: Close token manager
        if (tokenManager != null) {
            tokenManager.close();
        }
        // END: Close token manager
    }
}