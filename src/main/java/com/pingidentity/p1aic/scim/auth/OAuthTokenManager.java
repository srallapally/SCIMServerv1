package com.pingidentity.p1aic.scim.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pingidentity.p1aic.scim.config.ScimServerConfig;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages OAuth access tokens for server-to-server authentication with PingIDM.
 * Uses OAuth 2.0 Client Credentials flow to obtain tokens for administrative operations.
 */
public class OAuthTokenManager {

    private static final Logger logger = LoggerFactory.getLogger(OAuthTokenManager.class);

    private final ScimServerConfig config;
    private final Client client;
    private final ObjectMapper objectMapper;
    private final ReentrantLock lock;

    // Token cache
    private String cachedAccessToken;
    private long tokenExpiresAt;

    // Buffer time before token expiry (in milliseconds)
    private static final long EXPIRY_BUFFER_MS = 60000; // 1 minute

    public OAuthTokenManager() {
        this.config = ScimServerConfig.getInstance();
        this.client = ClientBuilder.newClient();
        this.objectMapper = new ObjectMapper();
        this.lock = new ReentrantLock();
    }

    /**
     * Get a valid access token, obtaining a new one if necessary.
     *
     * @return valid OAuth access token
     * @throws Exception if token acquisition fails
     */
    public String getAccessToken() throws Exception {
        lock.lock();
        try {
            // Check if cached token is still valid
            if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpiresAt) {
                logger.debug("Using cached OAuth token");
                return cachedAccessToken;
            }

            // Token expired or doesn't exist, get a new one
            logger.info("Obtaining new OAuth access token via client credentials flow");
            return obtainNewToken();

        } finally {
            lock.unlock();
        }
    }

    /**
     * Obtain a new OAuth access token using client credentials flow.
     *
     * @return new access token
     * @throws Exception if token acquisition fails
     */
    private String obtainNewToken() throws Exception {
        String tokenUrl = config.getOauthTokenUrl();
        String clientId = config.getOauthClientId();
        String clientSecret = config.getOauthClientSecret();
        String scope = config.getOauthScope();

        if (tokenUrl == null || tokenUrl.isEmpty()) {
            throw new IllegalStateException("OAUTH_TOKEN_URL not configured");
        }
        if (clientId == null || clientId.isEmpty()) {
            throw new IllegalStateException("OAUTH_CLIENT_ID not configured");
        }
        if (clientSecret == null || clientSecret.isEmpty()) {
            throw new IllegalStateException("OAUTH_CLIENT_SECRET not configured");
        }
        logger.debug("Obtaining OAuth access token via client credentials flow");
        try {
            // Build form data for token request
            Form form = new Form();
            form.param("grant_type", "client_credentials");
            if (scope != null && !scope.isEmpty()) {
                form.param("scope", scope);
            }

            // Build Basic Auth header for client credentials
            String credentials = clientId + ":" + clientSecret;
            String encodedCredentials = Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            // Make token request
            Response response = client.target(tokenUrl)
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Basic " + encodedCredentials)
                    .post(Entity.form(form));

            if (response.getStatus() != 200) {
                String errorBody = response.readEntity(String.class);
                logger.error("OAuth token request failed with status {}: {}",
                        response.getStatus(), errorBody);
                throw new Exception("Failed to obtain OAuth token. Status: " + response.getStatus());
            }

            // Parse token response
            String responseBody = response.readEntity(String.class);
            JsonNode tokenResponse = objectMapper.readTree(responseBody);

            if (!tokenResponse.has("access_token")) {
                throw new Exception("OAuth response missing access_token field");
            }

            cachedAccessToken = tokenResponse.get("access_token").asText();
            logger.info("Cached OAuth access token: {}", cachedAccessToken);
            // Calculate token expiration time
            long expiresIn = tokenResponse.has("expires_in")
                    ? tokenResponse.get("expires_in").asLong()
                    : 3600; // Default 1 hour
            tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000) - EXPIRY_BUFFER_MS;

            logger.info("Successfully obtained OAuth access token (expires in {} seconds)", expiresIn);

            return cachedAccessToken;

        } catch (Exception e) {
            logger.error("Error obtaining OAuth token", e);
            throw new Exception("Failed to obtain OAuth token: " + e.getMessage(), e);
        }
    }

    /**
     * Clear the cached token (useful for testing or forcing token refresh).
     */
    public void clearCache() {
        lock.lock();
        try {
            cachedAccessToken = null;
            tokenExpiresAt = 0;
            logger.debug("OAuth token cache cleared");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Close the underlying HTTP client.
     */
    public void close() {
        if (client != null) {
            client.close();
        }
    }
}