package com.pingidentity.p1aic.scim.mapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.config.ScimServerConfig;
import com.pingidentity.p1aic.scim.schema.ScimSchemaUrns;
import com.unboundid.scim2.common.GenericScimResource;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * User-specific attribute mapper that converts between SCIM User resources
 * and PingIDM managed user objects.
 *
 * Handles standard SCIM User attributes and their mapping to PingIDM equivalents.
 */
public class UserAttributeMapper {

    private static final Logger LOGGER = Logger.getLogger(UserAttributeMapper.class.getName());

    private final DynamicAttributeMapper baseMapper;
    private final Map<String, String> scimToIdmMapping;
    private final Map<String, String> idmToScimMapping;
    private final ScimServerConfig config;

    /**
     * Constructor initializes attribute mappings.
     */
    public UserAttributeMapper() {
        this.baseMapper = new DynamicAttributeMapper();
        this.config = ScimServerConfig.getInstance();
        this.scimToIdmMapping = buildScimToIdmMapping();
        this.idmToScimMapping = buildIdmToScimMapping();
    }

    /**
     * Build SCIM to PingIDM attribute name mapping.
     */
    private Map<String, String> buildScimToIdmMapping() {
        Map<String, String> mapping = new HashMap<>();

        // Core User attributes
        mapping.put("userName", "userName");
        mapping.put("displayName", "displayName");
        mapping.put("active", "accountStatus"); // Special handling: boolean -> string
        mapping.put("password", "password");

        // Name attributes (SCIM uses complex "name" object)
        mapping.put("name.givenName", "givenName");
        mapping.put("name.familyName", "sn");
        mapping.put("name.formatted", "cn");
        mapping.put("name.middleName", "middleName");
        mapping.put("name.honorificPrefix", "honorificPrefix");
        mapping.put("name.honorificSuffix", "honorificSuffix");

        // Email (SCIM uses multi-valued emails array)
        mapping.put("emails[0].value", "mail");
        mapping.put("emails[primary=true].value", "mail");

        // Phone (SCIM uses multi-valued phoneNumbers array)
        mapping.put("phoneNumbers[0].value", "telephoneNumber");
        mapping.put("phoneNumbers[type eq \"work\"].value", "telephoneNumber");


        // Additional attributes
        mapping.put("title", "title");
        mapping.put("preferredLanguage", "preferredLanguage");
        mapping.put("locale", "locale");
        mapping.put("timezone", "timezone");
        mapping.put("profileUrl", "profileUrl");

        // Enterprise extension attributes
        mapping.put("employeeNumber", "employeeNumber");
        mapping.put("costCenter", "costCenter");
        mapping.put("organization", "organization");
        mapping.put("division", "division");
        mapping.put("department", "department");
        mapping.put("manager", "manager");

        return mapping;
    }

    /**
     * Build PingIDM to SCIM attribute name mapping (reverse of above).
     */
    private Map<String, String> buildIdmToScimMapping() {
        Map<String, String> mapping = new HashMap<>();

        // Core User attributes
        mapping.put("userName", "userName");
        mapping.put("displayName", "displayName");
        mapping.put("accountStatus", "active"); // Special handling: string -> boolean

        // Name attributes get nested under "name" in SCIM
        mapping.put("givenName", "name.givenName");
        mapping.put("sn", "name.familyName");
        mapping.put("cn", "name.formatted");
        mapping.put("middleName", "name.middleName");
        mapping.put("honorificPrefix", "name.honorificPrefix");
        mapping.put("honorificSuffix", "name.honorificSuffix");

        // Email becomes multi-valued array in SCIM
        mapping.put("mail", "emails");

        // Phone becomes multi-valued array in SCIM
        mapping.put("telephoneNumber", "phoneNumbers");

        // Additional attributes
        mapping.put("title", "title");
        mapping.put("preferredLanguage", "preferredLanguage");
        mapping.put("locale", "locale");
        mapping.put("timezone", "timezone");
        mapping.put("profileUrl", "profileUrl");

        // Enterprise extension attributes
        mapping.put("employeeNumber", "employeeNumber");
        mapping.put("costCenter", "costCenter");
        mapping.put("organization", "organization");
        mapping.put("division", "division");
        mapping.put("department", "department");
        mapping.put("manager", "manager");

        return mapping;
    }

    /**
     * Convert SCIM User resource to PingIDM JSON format.
     */
    public ObjectNode scimToPingIdm(GenericScimResource scimUser) {
        ObjectNode scimNode = scimUser.asGenericScimResource().getObjectNode();
        ObjectNode idmUser = baseMapper.getObjectMapper().createObjectNode();

        // Handle userName
        if (scimNode.has("userName")) {
            idmUser.put("userName", scimNode.get("userName").asText());
        }

        // Handle displayName
        if (scimNode.has("displayName")) {
            idmUser.put("displayName", scimNode.get("displayName").asText());
        }

        // Handle password
        if (scimNode.has("password")) {
            idmUser.put("password", scimNode.get("password").asText());
        }

        // Handle active -> accountStatus (boolean to string conversion)
        if (scimNode.has("active")) {
            boolean active = scimNode.get("active").asBoolean();
            idmUser.put("accountStatus", active ? "active" : "inactive");
        }

        // Handle name object (complex attribute)
        if (scimNode.has("name") && scimNode.get("name").isObject()) {
            ObjectNode nameNode = (ObjectNode) scimNode.get("name");

            if (nameNode.has("givenName")) {
                idmUser.put("givenName", nameNode.get("givenName").asText());
            }
            if (nameNode.has("familyName")) {
                idmUser.put("sn", nameNode.get("familyName").asText());
            }
            if (nameNode.has("formatted")) {
                idmUser.put("cn", nameNode.get("formatted").asText());
            }
            if (nameNode.has("middleName")) {
                idmUser.put("middleName", nameNode.get("middleName").asText());
            }
        }

        // Handle emails (multi-valued -> single value, take primary or first)
        if (scimNode.has("emails") && scimNode.get("emails").isArray()) {
            ArrayNode emails = (ArrayNode) scimNode.get("emails");
            String primaryEmail = extractPrimaryOrFirst(emails, "value");
            if (primaryEmail != null) {
                idmUser.put("mail", primaryEmail);
            }
        }

        // Handle phoneNumbers (multi-valued -> single value, take primary or first)
        if (scimNode.has("phoneNumbers") && scimNode.get("phoneNumbers").isArray()) {
            ArrayNode phones = (ArrayNode) scimNode.get("phoneNumbers");
            String primaryPhone = extractPrimaryOrFirst(phones, "value");
            if (primaryPhone != null) {
                idmUser.put("telephoneNumber", primaryPhone);
            }
        }

        // BEGIN: Handle addresses - serialize as JSON string for PingIDM custom field storage
        if (scimNode.has("addresses") && scimNode.get("addresses").isArray()) {
            ArrayNode addresses = (ArrayNode) scimNode.get("addresses");
            if (addresses.size() > 0) {
                try {
                    String addressesJson = baseMapper.getObjectMapper().writeValueAsString(addresses);
                    idmUser.put("frUnindexedString6", addressesJson);
                    LOGGER.fine("Serialized addresses to frUnindexedString6: " + addressesJson);
                } catch (Exception e) {
                    LOGGER.warning("Failed to serialize addresses: " + e.getMessage());
                }
            }
        }
        // END: Handle addresses

        // BEGIN: Handle nickName - store in PingIDM custom field
        if (scimNode.has("nickName")) {
            idmUser.put("frUnindexedString7", scimNode.get("nickName").asText());
        }
        // END: Handle nickName

        // BEGIN: Handle userType - store in PingIDM custom field
        if (scimNode.has("userType")) {
            idmUser.put("frUnindexedString8", scimNode.get("userType").asText());
        }
        // END: Handle userType

        // BEGIN: Handle roles - serialize as JSON string for PingIDM custom field storage
        if (scimNode.has("roles") && scimNode.get("roles").isArray()) {
            ArrayNode roles = (ArrayNode) scimNode.get("roles");
            if (roles.size() > 0) {
                try {
                    String rolesJson = baseMapper.getObjectMapper().writeValueAsString(roles);
                    idmUser.put("frUnindexedString9", rolesJson);
                    LOGGER.fine("Serialized roles to frUnindexedString9: " + rolesJson);
                } catch (Exception e) {
                    LOGGER.warning("Failed to serialize roles: " + e.getMessage());
                }
            }
        }
        // END: Handle roles

        // Handle additional simple attributes
        copyIfPresent(scimNode, idmUser, "title", "title");
        copyIfPresent(scimNode, idmUser, "preferredLanguage", "preferredLanguage");
        copyIfPresent(scimNode, idmUser, "locale", "locale");
        copyIfPresent(scimNode, idmUser, "timezone", "timezone");
        copyIfPresent(scimNode, idmUser, "profileUrl", "profileUrl");

        // Handle custom extension attributes (enterprise extension)
        handleExtensionAttributes(scimNode, idmUser);

        return idmUser;
    }

    /**
     * Convert PingIDM user JSON to SCIM User resource.
     */
    public GenericScimResource pingIdmToScim(ObjectNode idmUser) {
        ObjectNode scimNode = baseMapper.getObjectMapper().createObjectNode();

        // Add schemas
        ArrayNode schemas = baseMapper.getObjectMapper().createArrayNode();
        schemas.add(ScimSchemaUrns.CORE_USER_SCHEMA);
        scimNode.set("schemas", schemas);

        // Handle id
        if (idmUser.has("_id")) {
            scimNode.put("id", idmUser.get("_id").asText());
        }

        // Handle userName
        if (idmUser.has("userName")) {
            scimNode.put("userName", idmUser.get("userName").asText());
        }

        // Handle displayName
        if (idmUser.has("displayName")) {
            scimNode.put("displayName", idmUser.get("displayName").asText());
        }

        // Handle accountStatus -> active (string to boolean conversion)
        if (idmUser.has("accountStatus")) {
            String status = idmUser.get("accountStatus").asText();
            scimNode.put("active", "active".equalsIgnoreCase(status));
        }

        // Handle name object (build complex attribute)
        ObjectNode nameNode = baseMapper.getObjectMapper().createObjectNode();
        boolean hasNameData = false;

        if (idmUser.has("givenName")) {
            nameNode.put("givenName", idmUser.get("givenName").asText());
            hasNameData = true;
        }
        if (idmUser.has("sn")) {
            nameNode.put("familyName", idmUser.get("sn").asText());
            hasNameData = true;
        }
        if (idmUser.has("cn")) {
            nameNode.put("formatted", idmUser.get("cn").asText());
            hasNameData = true;
        }
        if (idmUser.has("middleName")) {
            nameNode.put("middleName", idmUser.get("middleName").asText());
            hasNameData = true;
        }

        if (hasNameData) {
            scimNode.set("name", nameNode);
        }

        // Handle mail -> emails (single value to multi-valued array)
        if (idmUser.has("mail")) {
            ArrayNode emails = baseMapper.getObjectMapper().createArrayNode();
            ObjectNode emailObj = baseMapper.getObjectMapper().createObjectNode();
            emailObj.put("value", idmUser.get("mail").asText());
            emailObj.put("primary", true);
            emailObj.put("type", "work");
            emails.add(emailObj);
            scimNode.set("emails", emails);
        }

        // Handle telephoneNumber -> phoneNumbers (single value to multi-valued array)
        if (idmUser.has("telephoneNumber")) {
            ArrayNode phones = baseMapper.getObjectMapper().createArrayNode();
            ObjectNode phoneObj = baseMapper.getObjectMapper().createObjectNode();
            phoneObj.put("value", idmUser.get("telephoneNumber").asText());
            phoneObj.put("primary", true);
            phoneObj.put("type", "work");
            phones.add(phoneObj);
            scimNode.set("phoneNumbers", phones);
        }
        // BEGIN: Handle addresses - deserialize from JSON string in PingIDM custom field
        if (idmUser.has("frUnindexedString6")) {
            try {
                String addressesJson = idmUser.get("frUnindexedString6").asText();
                if (addressesJson != null && !addressesJson.isEmpty() && !addressesJson.equals("null")) {
                    JsonNode addressesNode = baseMapper.getObjectMapper().readTree(addressesJson);
                    if (addressesNode.isArray()) {
                        scimNode.set("addresses", addressesNode);
                        LOGGER.fine("Deserialized addresses from frUnindexedString6");
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to parse addresses JSON: " + e.getMessage());
            }
        }
        // END: Handle addresses

        // BEGIN: Handle nickName - retrieve from PingIDM custom field
        if (idmUser.has("frUnindexedString7")) {
            String nickName = idmUser.get("frUnindexedString7").asText();
            if (nickName != null && !nickName.isEmpty()) {
                scimNode.put("nickName", nickName);
            }
        }
        // END: Handle nickName

        // BEGIN: Handle userType - retrieve from PingIDM custom field
        if (idmUser.has("frUnindexedString8")) {
            String userType = idmUser.get("frUnindexedString8").asText();
            if (userType != null && !userType.isEmpty()) {
                scimNode.put("userType", userType);
            }
        }
        // END: Handle userType

        // BEGIN: Handle roles - deserialize from JSON string in PingIDM custom field
        if (idmUser.has("frUnindexedString9")) {
            try {
                String rolesJson = idmUser.get("frUnindexedString9").asText();
                if (rolesJson != null && !rolesJson.isEmpty() && !rolesJson.equals("null")) {
                    JsonNode rolesNode = baseMapper.getObjectMapper().readTree(rolesJson);
                    if (rolesNode.isArray()) {
                        scimNode.set("roles", rolesNode);
                        LOGGER.fine("Deserialized roles from frUnindexedString9");
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to parse roles JSON: " + e.getMessage());
            }
        }
        // END: Handle roles
        // Handle additional simple attributes
        copyIfPresent(idmUser, scimNode, "title", "title");
        copyIfPresent(idmUser, scimNode, "preferredLanguage", "preferredLanguage");
        copyIfPresent(idmUser, scimNode, "locale", "locale");
        copyIfPresent(idmUser, scimNode, "timezone", "timezone");
        copyIfPresent(idmUser, scimNode, "profileUrl", "profileUrl");

        // Handle meta object
        ObjectNode metaNode = buildMetaNode(idmUser);
        scimNode.set("meta", metaNode);

        return new GenericScimResource(scimNode);
    }

    /**
     * Extract primary value from multi-valued array, or return first element.
     */
    private String extractPrimaryOrFirst(ArrayNode array, String valueField) {
        String firstValue = null;

        for (JsonNode element : array) {
            if (element.isObject()) {
                ObjectNode obj = (ObjectNode) element;

                // Check if this is the primary element
                if (obj.has("primary") && obj.get("primary").asBoolean()) {
                    if (obj.has(valueField)) {
                        return obj.get(valueField).asText();
                    }
                }

                // Store first value as fallback
                if (firstValue == null && obj.has(valueField)) {
                    firstValue = obj.get(valueField).asText();
                }
            }
        }

        return firstValue;
    }

    /**
     * Copy attribute if present in source to destination.
     */
    private void copyIfPresent(ObjectNode source, ObjectNode dest, String sourceKey, String destKey) {
        if (source.has(sourceKey)) {
            JsonNode value = source.get(sourceKey);
            if (value != null && !value.isNull()) {
                dest.set(destKey, value);
            }
        }
    }

    /**
     * Handle SCIM extension attributes (e.g., enterprise extension).
     */
    private void handleExtensionAttributes(ObjectNode scimNode, ObjectNode idmUser) {
        // Check for enterprise extension
        if (scimNode.has(ScimSchemaUrns.ENTERPRISE_USER_EXTENSION)) {
            JsonNode extension = scimNode.get(ScimSchemaUrns.ENTERPRISE_USER_EXTENSION);
            if (extension.isObject()) {
                ObjectNode extNode = (ObjectNode) extension;

                copyIfPresent(extNode, idmUser, "employeeNumber", "employeeNumber");
                copyIfPresent(extNode, idmUser, "costCenter", "costCenter");
                copyIfPresent(extNode, idmUser, "organization", "organization");
                copyIfPresent(extNode, idmUser, "division", "division");
                copyIfPresent(extNode, idmUser, "department", "department");
                copyIfPresent(extNode, idmUser, "manager", "manager");
            }
        }

        // Check for custom PingIDM extension
        if (scimNode.has(ScimSchemaUrns.PINGIDM_USER_EXTENSION)) {
            JsonNode extension = scimNode.get(ScimSchemaUrns.PINGIDM_USER_EXTENSION);
            if (extension.isObject()) {
                ObjectNode extNode = (ObjectNode) extension;

                // Copy all custom attributes to IDM user
                extNode.fields().forEachRemaining(entry -> {
                    idmUser.set(entry.getKey(), entry.getValue());
                });
            }
        }
    }

    /**
     * Build SCIM meta object from PingIDM metadata.
     */
    private ObjectNode buildMetaNode(ObjectNode idmUser) {
        ObjectNode metaNode = baseMapper.getObjectMapper().createObjectNode();

        metaNode.put("resourceType", "User");

        // Build location URL
        if (idmUser.has("_id")) {
            String id = idmUser.get("_id").asText();
            String location = String.format("%s/Users/%s", config.getScimServerBaseUrl(), id);
            metaNode.put("location", location);
        }

        // Set version (use _rev as ETag)
        if (idmUser.has("_rev")) {
            metaNode.put("version", idmUser.get("_rev").asText());
        }

        // Extract timestamps from _meta if available
        if (idmUser.has("_meta") && idmUser.get("_meta").isObject()) {
            ObjectNode idmMeta = (ObjectNode) idmUser.get("_meta");

            if (idmMeta.has("created")) {
                metaNode.put("created", idmMeta.get("created").asText());
            }

            if (idmMeta.has("lastModified")) {
                metaNode.put("lastModified", idmMeta.get("lastModified").asText());
            }
        }

        return metaNode;
    }
}