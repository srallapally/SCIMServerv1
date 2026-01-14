package com.pingidentity.p1aic.scim.mapping;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Configuration class for attribute name mappings between SCIM and PingIDM.
 *
 * This class provides centralized mapping definitions that are used by:
 * - UserAttributeMapper and GroupAttributeMapper for data conversion
 * - ScimFilterConverter for filter translation
 * - Other components that need to map attribute names
 *
 * Mappings are bidirectional (SCIM -> PingIDM and PingIDM -> SCIM).
 */
public class AttributeMappingConfig {

    private static final Logger LOGGER = Logger.getLogger(AttributeMappingConfig.class.getName());

    // Singleton instance
    private static final AttributeMappingConfig INSTANCE = new AttributeMappingConfig();

    // User attribute mappings
    private final Map<String, String> userScimToIdm;
    private final Map<String, String> userIdmToScim;

    // Group attribute mappings
    private final Map<String, String> groupScimToIdm;
    private final Map<String, String> groupIdmToScim;

    /**
     * Private constructor initializes all mappings.
     */
    private AttributeMappingConfig() {
        this.userScimToIdm = buildUserScimToIdmMappings();
        this.userIdmToScim = buildUserIdmToScimMappings();
        this.groupScimToIdm = buildGroupScimToIdmMappings();
        this.groupIdmToScim = buildGroupIdmToScimMappings();

        LOGGER.info("AttributeMappingConfig initialized with " +
                userScimToIdm.size() + " user mappings and " +
                groupScimToIdm.size() + " group mappings");
    }

    /**
     * Get singleton instance.
     */
    public static AttributeMappingConfig getInstance() {
        return INSTANCE;
    }

    /**
     * Build User SCIM to PingIDM attribute mappings.
     */
    private Map<String, String> buildUserScimToIdmMappings() {
        Map<String, String> mappings = new HashMap<>();

        // Core User attributes
        mappings.put("userName", "userName");
        mappings.put("displayName", "displayName");
        mappings.put("active", "accountStatus"); // Special: boolean -> string
        mappings.put("password", "password");

        // Name attributes (SCIM complex -> PingIDM flat)
        mappings.put("name.givenName", "givenName");
        mappings.put("name.familyName", "sn");
        mappings.put("name.formatted", "cn");
        mappings.put("name.middleName", "middleName");
        mappings.put("name.honorificPrefix", "honorificPrefix");
        mappings.put("name.honorificSuffix", "honorificSuffix");

        // Email (multi-valued -> single)
        mappings.put("emails", "mail");
        mappings.put("emails.value", "mail");
        mappings.put("emails[0].value", "mail");
        mappings.put("emails[primary eq true].value", "mail");
        mappings.put("emails[type eq \"work\"].value", "mail");

        // Phone (multi-valued -> single)
        mappings.put("phoneNumbers", "telephoneNumber");
        mappings.put("phoneNumbers.value", "telephoneNumber");
        mappings.put("phoneNumbers[0].value", "telephoneNumber");
        mappings.put("phoneNumbers[primary eq true].value", "telephoneNumber");
        mappings.put("phoneNumbers[type eq \"work\"].value", "telephoneNumber");
        // BEGIN: Add mappings for missing SCIM Core User attributes
        // Addresses (multi-valued complex)
        mappings.put("addresses", "addresses");
        mappings.put("addresses.formatted", "addresses");
        mappings.put("addresses.streetAddress", "addresses");
        mappings.put("addresses.locality", "addresses");
        mappings.put("addresses.region", "addresses");
        mappings.put("addresses.postalCode", "addresses");
        mappings.put("addresses.country", "addresses");
        mappings.put("addresses.type", "addresses");

        // nickName
        mappings.put("nickName", "nickName");

        // userType
        mappings.put("userType", "userType");

        // Roles (multi-valued complex)
        mappings.put("roles", "roles");
        mappings.put("roles.value", "roles");
        mappings.put("roles.display", "roles");
        mappings.put("roles.type", "roles");
// END: Add mappings for missing SCIM Core User attributes
        // Additional standard attributes
        mappings.put("title", "title");
        mappings.put("preferredLanguage", "preferredLanguage");
        mappings.put("locale", "locale");
        mappings.put("timezone", "timezone");
        mappings.put("profileUrl", "profileUrl");

        // Enterprise extension attributes
        mappings.put("employeeNumber", "employeeNumber");
        mappings.put("costCenter", "costCenter");
        mappings.put("organization", "organization");
        mappings.put("division", "division");
        mappings.put("department", "department");
        mappings.put("manager", "manager");
        mappings.put("manager.value", "manager");

        return Collections.unmodifiableMap(mappings);
    }

    /**
     * Build User PingIDM to SCIM attribute mappings.
     */
    private Map<String, String> buildUserIdmToScimMappings() {
        Map<String, String> mappings = new HashMap<>();

        // Core User attributes
        mappings.put("userName", "userName");
        mappings.put("displayName", "displayName");
        mappings.put("accountStatus", "active"); // Special: string -> boolean

        // Name attributes (PingIDM flat -> SCIM complex)
        mappings.put("givenName", "name.givenName");
        mappings.put("sn", "name.familyName");
        mappings.put("cn", "name.formatted");
        mappings.put("middleName", "name.middleName");
        mappings.put("honorificPrefix", "name.honorificPrefix");
        mappings.put("honorificSuffix", "name.honorificSuffix");

        // Email (single -> multi-valued)
        mappings.put("mail", "emails");

        // Phone (single -> multi-valued)
        mappings.put("telephoneNumber", "phoneNumbers");

        // Additional standard attributes
        mappings.put("title", "title");
        mappings.put("preferredLanguage", "preferredLanguage");
        mappings.put("locale", "locale");
        mappings.put("timezone", "timezone");
        mappings.put("profileUrl", "profileUrl");

        // Enterprise extension attributes
        mappings.put("employeeNumber", "employeeNumber");
        mappings.put("costCenter", "costCenter");
        mappings.put("organization", "organization");
        mappings.put("division", "division");
        mappings.put("department", "department");
        mappings.put("manager", "manager");

        return Collections.unmodifiableMap(mappings);
    }

    /**
     * Build Group SCIM to PingIDM attribute mappings.
     */
    private Map<String, String> buildGroupScimToIdmMappings() {
        Map<String, String> mappings = new HashMap<>();

        // Core Group attributes
        mappings.put("displayName", "name");
        mappings.put("description", "description");
        mappings.put("members", "members");
        mappings.put("members.value", "members");

        return Collections.unmodifiableMap(mappings);
    }

    /**
     * Build Group PingIDM to SCIM attribute mappings.
     */
    private Map<String, String> buildGroupIdmToScimMappings() {
        Map<String, String> mappings = new HashMap<>();

        // Core Group attributes
        mappings.put("name", "displayName");
        mappings.put("description", "description");
        mappings.put("members", "members");

        return Collections.unmodifiableMap(mappings);
    }

    /**
     * Get User SCIM to PingIDM attribute mappings.
     *
     * @return unmodifiable map of SCIM attribute names to PingIDM attribute names
     */
    public Map<String, String> getUserScimToIdmMappings() {
        return userScimToIdm;
    }

    /**
     * Get User PingIDM to SCIM attribute mappings.
     *
     * @return unmodifiable map of PingIDM attribute names to SCIM attribute names
     */
    public Map<String, String> getUserIdmToScimMappings() {
        return userIdmToScim;
    }

    /**
     * Get Group SCIM to PingIDM attribute mappings.
     *
     * @return unmodifiable map of SCIM attribute names to PingIDM attribute names
     */
    public Map<String, String> getGroupScimToIdmMappings() {
        return groupScimToIdm;
    }

    /**
     * Get Group PingIDM to SCIM attribute mappings.
     *
     * @return unmodifiable map of PingIDM attribute names to SCIM attribute names
     */
    public Map<String, String> getGroupIdmToScimMappings() {
        return groupIdmToScim;
    }

    /**
     * Map a User SCIM attribute name to PingIDM attribute name.
     *
     * @param scimAttribute the SCIM attribute name
     * @return the PingIDM attribute name, or the original name if no mapping exists
     */
    public String mapUserScimToIdm(String scimAttribute) {
        return userScimToIdm.getOrDefault(scimAttribute, scimAttribute);
    }

    /**
     * Map a User PingIDM attribute name to SCIM attribute name.
     *
     * @param idmAttribute the PingIDM attribute name
     * @return the SCIM attribute name, or the original name if no mapping exists
     */
    public String mapUserIdmToScim(String idmAttribute) {
        return userIdmToScim.getOrDefault(idmAttribute, idmAttribute);
    }

    /**
     * Map a Group SCIM attribute name to PingIDM attribute name.
     *
     * @param scimAttribute the SCIM attribute name
     * @return the PingIDM attribute name, or the original name if no mapping exists
     */
    public String mapGroupScimToIdm(String scimAttribute) {
        return groupScimToIdm.getOrDefault(scimAttribute, scimAttribute);
    }

    /**
     * Map a Group PingIDM attribute name to SCIM attribute name.
     *
     * @param idmAttribute the PingIDM attribute name
     * @return the SCIM attribute name, or the original name if no mapping exists
     */
    public String mapGroupIdmToScim(String idmAttribute) {
        return groupIdmToScim.getOrDefault(idmAttribute, idmAttribute);
    }

    /**
     * Check if a User SCIM attribute has a mapping to PingIDM.
     *
     * @param scimAttribute the SCIM attribute name
     * @return true if a mapping exists, false otherwise
     */
    public boolean hasUserScimToIdmMapping(String scimAttribute) {
        return userScimToIdm.containsKey(scimAttribute);
    }

    /**
     * Check if a User PingIDM attribute has a mapping to SCIM.
     *
     * @param idmAttribute the PingIDM attribute name
     * @return true if a mapping exists, false otherwise
     */
    public boolean hasUserIdmToScimMapping(String idmAttribute) {
        return userIdmToScim.containsKey(idmAttribute);
    }

    /**
     * Check if a Group SCIM attribute has a mapping to PingIDM.
     *
     * @param scimAttribute the SCIM attribute name
     * @return true if a mapping exists, false otherwise
     */
    public boolean hasGroupScimToIdmMapping(String scimAttribute) {
        return groupScimToIdm.containsKey(scimAttribute);
    }

    /**
     * Check if a Group PingIDM attribute has a mapping to PingIDM.
     *
     * @param idmAttribute the PingIDM attribute name
     * @return true if a mapping exists, false otherwise
     */
    public boolean hasGroupIdmToScimMapping(String idmAttribute) {
        return groupIdmToScim.containsKey(idmAttribute);
    }
}