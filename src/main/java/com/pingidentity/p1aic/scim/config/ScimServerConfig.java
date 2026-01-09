package com.pingidentity.p1aic.scim.config;

import java.util.Optional;

/**
 * Configuration class for SCIM Server settings.
 * Reads configuration from environment variables or system properties.
 */
public class ScimServerConfig {

    private static final String PINGIDM_BASE_URL_KEY = "PINGIDM_BASE_URL";
    private static final String PINGIDM_REALM_KEY = "PINGIDM_REALM";
    private static final String SCIM_SERVER_BASE_URL_KEY = "SCIM_SERVER_BASE_URL";

    // Default values
    private static final String DEFAULT_PINGIDM_BASE_URL = "https://localhost:8443";
    private static final String DEFAULT_REALM = "alpha";
    private static final String DEFAULT_SCIM_SERVER_BASE_URL = "http://localhost:8080/scim/v2";

    private final String pingIdmBaseUrl;
    private final String realm;
    private final String scimServerBaseUrl;

    // Singleton instance
    private static final ScimServerConfig INSTANCE = new ScimServerConfig();

    /**
     * Private constructor - loads configuration from environment variables or system properties.
     */
    private ScimServerConfig() {
        this.pingIdmBaseUrl = getConfigValue(PINGIDM_BASE_URL_KEY, DEFAULT_PINGIDM_BASE_URL);
        this.realm = getConfigValue(PINGIDM_REALM_KEY, DEFAULT_REALM);
        this.scimServerBaseUrl = getConfigValue(SCIM_SERVER_BASE_URL_KEY, DEFAULT_SCIM_SERVER_BASE_URL);
    }

    /**
     * Get singleton instance of configuration.
     */
    public static ScimServerConfig getInstance() {
        return INSTANCE;
    }

    /**
     * Get PingIDM base URL (e.g., https://idm.example.com:8443).
     */
    public String getPingIdmBaseUrl() {
        return pingIdmBaseUrl;
    }

    /**
     * Get PingIDM realm name (e.g., "alpha").
     */
    public String getRealm() {
        return realm;
    }

    /**
     * Get SCIM server base URL (used for generating resource locations).
     */
    public String getScimServerBaseUrl() {
        return scimServerBaseUrl;
    }

    /**
     * Get full managed users endpoint URL.
     * Format: {baseUrl}/openidm/managed/{realm}_user
     */
    public String getManagedUsersEndpoint() {
        return String.format("%s/openidm/managed/%s_user", pingIdmBaseUrl, realm);
    }

    /**
     * Get full managed roles endpoint URL.
     * Format: {baseUrl}/openidm/managed/{realm}_role
     */
    public String getManagedRolesEndpoint() {
        return String.format("%s/openidm/managed/%s_role", pingIdmBaseUrl, realm);
    }

    /**
     * Get PingIDM config endpoint URL.
     * Format: {baseUrl}/openidm/config/managed
     */
    public String getConfigManagedEndpoint() {
        return String.format("%s/openidm/config/managed", pingIdmBaseUrl);
    }

    /**
     * Helper method to get configuration value from environment variable or system property.
     * Environment variables take precedence over system properties.
     */
    private String getConfigValue(String key, String defaultValue) {
        return Optional.ofNullable(System.getenv(key))
                .or(() -> Optional.ofNullable(System.getProperty(key)))
                .orElse(defaultValue);
    }

    @Override
    public String toString() {
        return "ScimServerConfig{" +
                "pingIdmBaseUrl='" + pingIdmBaseUrl + '\'' +
                ", realm='" + realm + '\'' +
                ", scimServerBaseUrl='" + scimServerBaseUrl + '\'' +
                '}';
    }
}
