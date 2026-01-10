package com.pingidentity.p1aic.scim.config;

public class ScimServerConfig {

    private static ScimServerConfig instance;

    // PingIDM Configuration
    private String pingIdmBaseUrl;

    // BEGIN: Remove unused username/password, add OAuth config
    // OAuth Configuration
    private String oauthTokenUrl;
    private String oauthClientId;
    private String oauthClientSecret;
    private String oauthScope;
    // END: Add OAuth config

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

    // BEGIN: Add OAuth getters/setters
    public String getOauthTokenUrl() {
        return oauthTokenUrl;
    }

    public void setOauthTokenUrl(String oauthTokenUrl) {
        this.oauthTokenUrl = oauthTokenUrl;
    }

    public String getOauthClientId() {
        return oauthClientId;
    }

    public void setOauthClientId(String oauthClientId) {
        this.oauthClientId = oauthClientId;
    }

    public String getOauthClientSecret() {
        return oauthClientSecret;
    }

    public void setOauthClientSecret(String oauthClientSecret) {
        this.oauthClientSecret = oauthClientSecret;
    }

    public String getOauthScope() {
        return oauthScope;
    }

    public void setOauthScope(String oauthScope) {
        this.oauthScope = oauthScope;
    }
    // END: Add OAuth getters/setters

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