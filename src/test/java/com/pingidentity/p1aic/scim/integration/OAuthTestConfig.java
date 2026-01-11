package com.pingidentity.p1aic.scim.integration;

/**
 * Configuration helper for OAuth integration tests.
 * 
 * This class provides utility methods for loading and validating
 * test configuration from environment variables.
 */
public class OAuthTestConfig {

    // Environment variable names
    public static final String PINGONE_TOKEN_ENDPOINT = "PINGONE_TOKEN_ENDPOINT";
    public static final String PINGONE_CLIENT_ID = "PINGONE_CLIENT_ID";
    public static final String PINGONE_CLIENT_SECRET = "PINGONE_CLIENT_SECRET";
    public static final String PINGONE_SCOPE = "PINGONE_SCOPE";
    public static final String SCIM_SERVER_BASE_URL = "SCIM_SERVER_BASE_URL";
    public static final String SCIM_TEST_USER_ID = "SCIM_TEST_USER_ID";

    // Default values
    public static final String DEFAULT_SCOPE = "openid profile";

    /**
     * Check if OAuth integration tests are configured.
     * 
     * @return true if all required environment variables are set
     */
    public static boolean isConfigured() {
        return getTokenEndpoint() != null &&
               getClientId() != null &&
               getClientSecret() != null;
    }

    /**
     * Check if SCIM end-to-end tests are configured.
     * 
     * @return true if OAuth and SCIM server URL are configured
     */
    public static boolean isEndToEndConfigured() {
        return isConfigured() && getScimServerBaseUrl() != null;
    }

    /**
     * Get PingOne token endpoint URL.
     * 
     * @return token endpoint URL or null if not set
     */
    public static String getTokenEndpoint() {
        return System.getenv(PINGONE_TOKEN_ENDPOINT);
    }

    /**
     * Get OAuth client ID.
     * 
     * @return client ID or null if not set
     */
    public static String getClientId() {
        return System.getenv(PINGONE_CLIENT_ID);
    }

    /**
     * Get OAuth client secret.
     * 
     * @return client secret or null if not set
     */
    public static String getClientSecret() {
        return System.getenv(PINGONE_CLIENT_SECRET);
    }

    /**
     * Get OAuth scope.
     * 
     * @return configured scope or default scope
     */
    public static String getScope() {
        String scope = System.getenv(PINGONE_SCOPE);
        return scope != null ? scope : DEFAULT_SCOPE;
    }

    /**
     * Get SCIM server base URL.
     * 
     * @return SCIM server base URL or null if not set
     */
    public static String getScimServerBaseUrl() {
        return System.getenv(SCIM_SERVER_BASE_URL);
    }

    /**
     * Get test user ID for read operations.
     * 
     * @return test user ID or null if not set
     */
    public static String getTestUserId() {
        return System.getenv(SCIM_TEST_USER_ID);
    }

    /**
     * Print configuration status for debugging.
     */
    public static void printConfigStatus() {
        System.out.println("=== OAuth Integration Test Configuration ===");
        System.out.println("Token Endpoint: " + (getTokenEndpoint() != null ? "SET" : "NOT SET"));
        System.out.println("Client ID: " + (getClientId() != null ? "SET" : "NOT SET"));
        System.out.println("Client Secret: " + (getClientSecret() != null ? "SET" : "NOT SET"));
        System.out.println("Scope: " + getScope());
        System.out.println("SCIM Server URL: " + (getScimServerBaseUrl() != null ? getScimServerBaseUrl() : "NOT SET"));
        System.out.println("Test User ID: " + (getTestUserId() != null ? "SET" : "NOT SET"));
        System.out.println("OAuth Tests Configured: " + isConfigured());
        System.out.println("End-to-End Tests Configured: " + isEndToEndConfigured());
        System.out.println("==========================================");
    }

    /**
     * Validate configuration and throw exception if incomplete.
     * 
     * @throws IllegalStateException if configuration is incomplete
     */
    public static void validateConfiguration() {
        if (!isConfigured()) {
            StringBuilder missing = new StringBuilder("Missing required environment variables: ");
            
            if (getTokenEndpoint() == null) {
                missing.append(PINGONE_TOKEN_ENDPOINT).append(" ");
            }
            if (getClientId() == null) {
                missing.append(PINGONE_CLIENT_ID).append(" ");
            }
            if (getClientSecret() == null) {
                missing.append(PINGONE_CLIENT_SECRET).append(" ");
            }
            
            throw new IllegalStateException(missing.toString());
        }
    }

    /**
     * Validate end-to-end configuration and throw exception if incomplete.
     * 
     * @throws IllegalStateException if configuration is incomplete
     */
    public static void validateEndToEndConfiguration() {
        validateConfiguration();
        
        if (getScimServerBaseUrl() == null) {
            throw new IllegalStateException("Missing required environment variable: " + SCIM_SERVER_BASE_URL);
        }
    }
}
