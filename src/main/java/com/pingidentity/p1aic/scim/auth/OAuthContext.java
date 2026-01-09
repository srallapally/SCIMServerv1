package com.pingidentity.p1aic.scim.auth;
import jakarta.enterprise.context.RequestScoped;

/**
 * Request-scoped context that holds the OAuth access token for the current HTTP request.
 *
 * The OAuthTokenFilter extracts the Bearer token from the Authorization header and stores
 * it in this context. Service classes can then inject this context to retrieve the token
 * for making authenticated calls to PingIDM.
 *
 * This bean is request-scoped, meaning a new instance is created for each HTTP request
 * and is automatically destroyed when the request completes.
 */
@RequestScoped
public class OAuthContext {

    private String accessToken;

    /**
     * Get the OAuth access token for the current request.
     *
     * @return the access token, or null if not set
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Set the OAuth access token for the current request.
     * This is typically called by OAuthTokenFilter.
     *
     * @param accessToken the access token to store
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    /**
     * Check if an access token is present.
     *
     * @return true if access token is set and not empty, false otherwise
     */
    public boolean hasAccessToken() {
        return accessToken != null && !accessToken.trim().isEmpty();
    }

    /**
     * Clear the access token from this context.
     * This is typically not needed as the context is request-scoped,
     * but can be useful for testing.
     */
    public void clear() {
        this.accessToken = null;
    }

    @Override
    public String toString() {
        // Don't expose the actual token value in toString for security
        return "OAuthContext{" +
                "hasToken=" + hasAccessToken() +
                '}';
    }
}