package com.pingidentity.p1aic.scim.config;

/**
 * Singleton configuration class for SCIM Server.
 * Holds PingIDM connection details, SCIM server URLs, and endpoint configurations.
 */
public class ScimServerConfig {

    private static ScimServerConfig instance;

    // PingIDM Configuration
    private String pingIdmBaseUrl;
    private String pingIdmUsername;
    private String pingIdmPassword;

    // SCIM Server Configuration
    private String scimServerBaseUrl;
    private String realm;

    // Managed Object Names (configurable)
    private String managedUserObjectName = "alpha_user";
    private String managedRoleObjectName = "alpha_role";

    private ScimServerConfig() {
        // Private constructor for singleton
        // Set default realm
        this.realm = "scim";
    }

    /**
     * Get singleton instance.
     */
    public static synchronized ScimServerConfig getInstance() {
        if (instance == null) {
            instance = new ScimServerConfig();
        }
        return instance;
    }

    // PingIDM Configuration

    public String getPingIdmBaseUrl() {
        return pingIdmBaseUrl;
    }

    public void setPingIdmBaseUrl(String pingIdmBaseUrl) {
        this.pingIdmBaseUrl = pingIdmBaseUrl;
        if (pingIdmBaseUrl != null && pingIdmBaseUrl.endsWith("/")) {
            this.pingIdmBaseUrl = pingIdmBaseUrl.substring(0, pingIdmBaseUrl.length() - 1);
        }
    }

    public String getPingIdmUsername() {
        return pingIdmUsername;
    }

    public void setPingIdmUsername(String pingIdmUsername) {
        this.pingIdmUsername = pingIdmUsername;
    }

    public String getPingIdmPassword() {
        return pingIdmPassword;
    }

    public void setPingIdmPassword(String pingIdmPassword) {
        this.pingIdmPassword = pingIdmPassword;
    }

    // SCIM Server Configuration

    public String getScimServerBaseUrl() {
        return scimServerBaseUrl;
    }

    public void setScimServerBaseUrl(String scimServerBaseUrl) {
        this.scimServerBaseUrl = scimServerBaseUrl;
        if (scimServerBaseUrl != null && scimServerBaseUrl.endsWith("/")) {
            this.scimServerBaseUrl = scimServerBaseUrl.substring(0, scimServerBaseUrl.length() - 1);
        }
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    // Managed Object Configuration

    public String getManagedUserObjectName() {
        return managedUserObjectName;
    }

    public void setManagedUserObjectName(String managedUserObjectName) {
        this.managedUserObjectName = managedUserObjectName;
    }

    public String getManagedRoleObjectName() {
        return managedRoleObjectName;
    }

    public void setManagedRoleObjectName(String managedRoleObjectName) {
        this.managedRoleObjectName = managedRoleObjectName;
    }

    // PingIDM Endpoint URLs

    /**
     * Get the managed users endpoint URL.
     */
    public String getManagedUsersEndpoint() {
        return pingIdmBaseUrl + "/openidm/managed/" + managedUserObjectName;
    }

    /**
     * Get the managed roles endpoint URL.
     */
    public String getManagedRolesEndpoint() {
        return pingIdmBaseUrl + "/openidm/managed/" + managedRoleObjectName;
    }

    /**
     * Get the config managed endpoint URL.
     */
    public String getConfigManagedEndpoint() {
        return pingIdmBaseUrl + "/openidm/config/managed";
    }
}