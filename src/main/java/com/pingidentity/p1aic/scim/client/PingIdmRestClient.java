package com.pingidentity.p1aic.scim.client;

import com.pingidentity.p1aic.scim.auth.OAuthContext;
import com.pingidentity.p1aic.scim.config.ScimServerConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.logging.Logger;

/**
 * Base REST client for making authenticated calls to PingIDM REST APIs.
 *
 * This client automatically adds required PingIDM headers and passes through
 * the OAuth access token from the current request context.
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

    @Inject
    private OAuthContext oauthContext;

    /**
     * Constructor initializes JAX-RS client.
     */
    public PingIdmRestClient() {
        this.client = ClientBuilder.newClient();
        this.config = ScimServerConfig.getInstance();
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

        // Add OAuth Bearer token from request context
        if (oauthContext != null && oauthContext.hasAccessToken()) {
            builder.header(HEADER_AUTHORIZATION, "Bearer " + oauthContext.getAccessToken());
        } else {
            LOGGER.warning("No OAuth access token available in context");
        }

        return builder;
    }

    /**
     * Execute GET request to PingIDM.
     *
     * @param endpointUrl the PingIDM endpoint URL
     * @return Response from PingIDM
     */
    public Response get(String endpointUrl) {
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
        WebTarget target = target(endpointUrl);
        Invocation.Builder builder = buildRequest(target);

        // Add If-Match header for optimistic locking
        if (revision != null && !revision.isEmpty()) {
            builder.header(HEADER_IF_MATCH, revision);
        }

        return builder.delete();
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

    /**
     * Close the underlying JAX-RS client.
     * Should be called when the application shuts down.
     */
    public void close() {
        if (client != null) {
            client.close();
        }
    }
}