package com.pingidentity.p1aic.scim.client;

import com.pingidentity.p1aic.scim.auth.OAuthContext;
import com.pingidentity.p1aic.scim.auth.OAuthTokenManager;
import com.pingidentity.p1aic.scim.config.ScimServerConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Base REST client for making authenticated calls to PingIDM REST APIs.
 *
 * This client automatically adds required PingIDM headers and uses the OAuth
 * access token stored in request-scoped OAuthContext for the current request.
 *
 * REFACTORED: Now uses @RequestScoped OAuthContext injection instead of ThreadLocal
 * for better compatibility with async/reactive processing models.
 *
 * The OAuth token is set by OAuthTokenFilter at the start of each request
 * and automatically cleaned up when the request scope ends.
 */
public class PingIdmRestClient {

    private static final Logger LOGGER = Logger.getLogger(PingIdmRestClient.class.getName());
    private static final int CONNECT_TIMEOUT_MS = 10000;  // 10 seconds to establish connection
    private static final int READ_TIMEOUT_MS = 30000;      // 30 seconds to read response
    // PingIDM required headers
    private static final String HEADER_ACCEPT_API_VERSION = "Accept-API-Version";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_IF_MATCH = "If-Match";

    // PingIDM API version
    private static final String API_VERSION_RESOURCE = "resource=1.0";

    private final Client client;
    private final ScimServerConfig config;

    // BEGIN: Inject request-scoped OAuthContext instead of using ThreadLocal
    @Inject
    private OAuthContext oauthContext;
    // END: Inject request-scoped OAuthContext

    private final OAuthTokenManager tokenManager;

    /**
     * Constructor initializes JAX-RS client.
     */
    public PingIdmRestClient() {
        // BEGIN: Configure HTTP client with timeouts and connection pooling
        ClientConfig clientConfig = new ClientConfig();

        // Set connect timeout - how long to wait to establish TCP connection
        clientConfig.property(ClientProperties.CONNECT_TIMEOUT, CONNECT_TIMEOUT_MS);

        // Set read timeout - how long to wait for response after connection established
        clientConfig.property(ClientProperties.READ_TIMEOUT, READ_TIMEOUT_MS);

        // Build client with configured properties
        this.client = ClientBuilder.newClient(clientConfig);

        LOGGER.info("PingIDM REST client initialized with connect timeout: " + CONNECT_TIMEOUT_MS +
                "ms, read timeout: " + READ_TIMEOUT_MS + "ms");
        // END: Configure HTTP client with timeouts and connection pooling

        this.config = ScimServerConfig.getInstance();
        this.tokenManager = new OAuthTokenManager();
    }

    /**
     * Get the OAuth token for the current request.
     * @return the OAuth token, or null if not set
     */
    private String getCurrentOAuthToken() {
        // BEGIN: Use injected OAuthContext instead of ThreadLocal
        if (oauthContext != null && oauthContext.hasAccessToken()) {
            return oauthContext.getAccessToken();
        }
        return null;
        // END: Use injected OAuthContext
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

        // BEGIN: Add OAuth Bearer token from request-scoped OAuthContext
        String token = getCurrentOAuthToken();
        if (token != null && !token.isEmpty()) {
            builder.header(HEADER_AUTHORIZATION, "Bearer " + token);
            LOGGER.fine("Added OAuth Bearer token from OAuthContext to request");
        } else {
            LOGGER.warning("No OAuth access token available in OAuthContext for PingIDM request");
        }
        // END: Add OAuth Bearer token from request-scoped OAuthContext

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

    // BEGIN: Add WebTarget-based helper methods for safe URL construction
    /**
     * Execute GET request to a specific resource using WebTarget.path() for safe URL construction.
     *
     * @param baseEndpoint the base endpoint URL (e.g., /openidm/managed/alpha_user)
     * @param resourceId the resource ID to append to the path
     * @return Response from PingIDM
     */
    public Response getResource(String baseEndpoint, String resourceId) {
        WebTarget target = target(baseEndpoint).path(resourceId);
        LOGGER.info("PingIDM GET: " + target.getUri());
        return buildRequest(target).get();
    }

    /**
     * Execute GET request to a specific resource with query parameters.
     *
     * @param baseEndpoint the base endpoint URL
     * @param resourceId the resource ID to append to the path
     * @param queryParams query parameters as key-value pairs
     * @return Response from PingIDM
     */
    public Response getResource(String baseEndpoint, String resourceId, String... queryParams) {
        WebTarget target = target(baseEndpoint).path(resourceId);

        // Add query parameters
        for (int i = 0; i < queryParams.length; i += 2) {
            if (i + 1 < queryParams.length) {
                target = target.queryParam(queryParams[i], queryParams[i + 1]);
            }
        }

        LOGGER.info("PingIDM GET: " + target.getUri());
        return buildRequest(target).get();
    }

    /**
     * Execute PUT request to a specific resource using WebTarget.path() for safe URL construction.
     *
     * @param baseEndpoint the base endpoint URL
     * @param resourceId the resource ID to append to the path
     * @param jsonBody the JSON body as String
     * @return Response from PingIDM
     */
    public Response putResource(String baseEndpoint, String resourceId, String jsonBody) {
        WebTarget target = target(baseEndpoint).path(resourceId);
        LOGGER.info("PingIDM PUT: " + target.getUri());
        LOGGER.fine("Request body: " + jsonBody);

        return buildRequest(target)
                .put(Entity.entity(jsonBody, MediaType.APPLICATION_JSON));
    }

    /**
     * Execute PUT request to a specific resource with If-Match header.
     *
     * @param baseEndpoint the base endpoint URL
     * @param resourceId the resource ID to append to the path
     * @param jsonBody the JSON body as String
     * @param revision the revision/etag value for If-Match header
     * @return Response from PingIDM
     */
    public Response putResource(String baseEndpoint, String resourceId, String jsonBody, String revision) {
        WebTarget target = target(baseEndpoint).path(resourceId);
        LOGGER.info("PingIDM PUT: " + target.getUri() + " (If-Match: " + revision + ")");
        LOGGER.fine("Request body: " + jsonBody);

        Invocation.Builder builder = buildRequest(target);

        // Add If-Match header for optimistic locking
        if (revision != null && !revision.isEmpty()) {
            builder.header(HEADER_IF_MATCH, revision);
        }

        return builder.put(Entity.entity(jsonBody, MediaType.APPLICATION_JSON));
    }

    /**
     * Execute PATCH request to a specific resource using WebTarget.path() for safe URL construction.
     *
     * @param baseEndpoint the base endpoint URL
     * @param resourceId the resource ID to append to the path
     * @param jsonPatch the JSON patch body as String
     * @return Response from PingIDM
     */
    public Response patchResource(String baseEndpoint, String resourceId, String jsonPatch) {
        WebTarget target = target(baseEndpoint).path(resourceId);
        LOGGER.info("PingIDM PATCH: " + target.getUri());
        LOGGER.fine("Request body: " + jsonPatch);

        return buildRequest(target)
                .method("PATCH", Entity.entity(jsonPatch, MediaType.APPLICATION_JSON));
    }

    /**
     * Execute PATCH request to a specific resource with If-Match header.
     *
     * @param baseEndpoint the base endpoint URL
     * @param resourceId the resource ID to append to the path
     * @param jsonPatch the JSON patch body as String
     * @param revision the revision/etag value for If-Match header
     * @return Response from PingIDM
     */
    public Response patchResource(String baseEndpoint, String resourceId, String jsonPatch, String revision) {
        WebTarget target = target(baseEndpoint).path(resourceId);
        LOGGER.info("PingIDM PATCH: " + target.getUri() + " (If-Match: " + revision + ")");
        LOGGER.fine("Request body: " + jsonPatch);

        Invocation.Builder builder = buildRequest(target);

        // Add If-Match header for optimistic locking
        if (revision != null && !revision.isEmpty()) {
            builder.header(HEADER_IF_MATCH, revision);
        }

        return builder.method("PATCH", Entity.entity(jsonPatch, MediaType.APPLICATION_JSON));
    }

    /**
     * Execute DELETE request to a specific resource using WebTarget.path() for safe URL construction.
     *
     * @param baseEndpoint the base endpoint URL
     * @param resourceId the resource ID to append to the path
     * @return Response from PingIDM
     */
    public Response deleteResource(String baseEndpoint, String resourceId) {
        WebTarget target = target(baseEndpoint).path(resourceId);
        LOGGER.info("PingIDM DELETE: " + target.getUri());

        return buildRequest(target).delete();
    }

    /**
     * Execute DELETE request to a specific resource with If-Match header.
     *
     * @param baseEndpoint the base endpoint URL
     * @param resourceId the resource ID to append to the path
     * @param revision the revision/etag value for If-Match header
     * @return Response from PingIDM
     */
    public Response deleteResource(String baseEndpoint, String resourceId, String revision) {
        WebTarget target = target(baseEndpoint).path(resourceId);
        LOGGER.info("PingIDM DELETE: " + target.getUri() + " (If-Match: " + revision + ")");

        Invocation.Builder builder = buildRequest(target);

        // Add If-Match header for optimistic locking
        if (revision != null && !revision.isEmpty()) {
            builder.header(HEADER_IF_MATCH, revision);
        }

        return builder.delete();
    }
    // END: Add WebTarget-based helper methods for safe URL construction

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
        if (tokenManager != null) {
            tokenManager.close();
        }
    }
}