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
 * - ScimPatchConverter for patch operation translation
 */
public class AttributeMappingConfig {

    private static final Logger LOGGER = Logger.getLogger(AttributeMappingConfig.class.getName());

    private static final AttributeMappingConfig INSTANCE = new AttributeMappingConfig();

    private final Map<String, String> userScimToIdm;
    private final Map<String, String> userIdmToScim;
    private final Map<String, String> groupScimToIdm;
    private final Map<String, String> groupIdmToScim;

    private AttributeMappingConfig() {
        this.userScimToIdm = buildUserScimToIdmMappings();
        this.userIdmToScim = buildUserIdmToScimMappings();
        this.groupScimToIdm = buildGroupScimToIdmMappings();
        this.groupIdmToScim = buildGroupIdmToScimMappings();
    }

    public static AttributeMappingConfig getInstance() {
        return INSTANCE;
    }

    private Map<String, String> buildUserScimToIdmMappings() {
        Map<String, String> mappings = new HashMap<>();

        // Core User attributes
        mappings.put("userName", "userName");
        mappings.put("displayName", "displayName");
        mappings.put("active", "accountStatus");
        mappings.put("password", "password");

        // Name attributes
        mappings.put("name.givenName", "givenName");
        mappings.put("name.familyName", "sn");
        mappings.put("name.formatted", "cn");

        // Email (multi-valued -> single)
        mappings.put("emails", "mail");
        mappings.put("emails.value", "mail");
        mappings.put("emails[0].value", "mail");
        mappings.put("emails[primary eq true].value", "mail");
        mappings.put("emails[type eq \"work\"].value", "mail");

        // Phone (multi-valued -> single)
        mappings.put("telephoneNumber", "telephoneNumber"); // Flattened fallback

        // NOTE: addresses, roles, nickName, userType are NOT hardcoded here.
        // They are handled dynamically via CustomAttributeMappingConfig.

        return Collections.unmodifiableMap(mappings);
    }

    private Map<String, String> buildUserIdmToScimMappings() {
        Map<String, String> mappings = new HashMap<>();

        mappings.put("userName", "userName");
        mappings.put("displayName", "displayName");
        mappings.put("accountStatus", "active");

        mappings.put("givenName", "name.givenName");
        mappings.put("sn", "name.familyName");
        mappings.put("cn", "name.formatted");

        mappings.put("mail", "emails");
        mappings.put("telephoneNumber", "phoneNumbers");

        return Collections.unmodifiableMap(mappings);
    }

    private Map<String, String> buildGroupScimToIdmMappings() {
        Map<String, String> mappings = new HashMap<>();
        mappings.put("displayName", "name");
        mappings.put("description", "description");
        mappings.put("members", "members");
        mappings.put("members.value", "members");
        return Collections.unmodifiableMap(mappings);
    }

    private Map<String, String> buildGroupIdmToScimMappings() {
        Map<String, String> mappings = new HashMap<>();
        mappings.put("name", "displayName");
        mappings.put("description", "description");
        mappings.put("members", "members");
        return Collections.unmodifiableMap(mappings);
    }

    public Map<String, String> getUserScimToIdmMappings() { return userScimToIdm; }
    public Map<String, String> getUserIdmToScimMappings() { return userIdmToScim; }
    public Map<String, String> getGroupScimToIdmMappings() { return groupScimToIdm; }
    public Map<String, String> getGroupIdmToScimMappings() { return groupIdmToScim; }

    public String mapUserScimToIdm(String scimAttribute) {
        return userScimToIdm.getOrDefault(scimAttribute, scimAttribute);
    }
}