package com.pingidentity.p1aic.scim.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.config.CustomAttributeMapping;
import com.pingidentity.p1aic.scim.config.CustomAttributeMappingConfig;
import com.pingidentity.p1aic.scim.config.ScimServerConfig;
import com.pingidentity.p1aic.scim.schema.ScimSchemaUrns;
import com.unboundid.scim2.common.GenericScimResource;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * User-specific attribute mapper that converts between SCIM User resources
 * and PingIDM managed user objects.
 *
 * Handles standard SCIM User attributes and their mapping to PingIDM equivalents.
 * Now supports dynamic mapping via CustomAttributeMappingConfig.
 */
public class UserAttributeMapper {

    private static final Logger LOGGER = Logger.getLogger(UserAttributeMapper.class.getName());

    private final DynamicAttributeMapper baseMapper;
    private final ScimServerConfig config;
    private final CustomAttributeMappingConfig mappingConfig;

    /**
     * Default constructor for backward compatibility.
     * Note: Mappings for addresses, roles, etc., will not work without config.
     */
    public UserAttributeMapper() {
        this(null);
    }

    /**
     * Constructor with configuration injection.
     *
     * @param mappingConfig the custom attribute mapping configuration
     */
    @Inject
    public UserAttributeMapper(CustomAttributeMappingConfig mappingConfig) {
        this.baseMapper = new DynamicAttributeMapper();
        this.config = ScimServerConfig.getInstance();
        this.mappingConfig = mappingConfig;
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

        // Handle addresses - lookup mapping, serialize if needed
        handleSerializedAttribute(scimNode, idmUser, "addresses");

        // Handle nickName - lookup mapping
        handleMappedAttribute(scimNode, idmUser, "nickName");

        // Handle userType - lookup mapping
        handleMappedAttribute(scimNode, idmUser, "userType");

        // Handle roles - lookup mapping, serialize if needed
        handleSerializedAttribute(scimNode, idmUser, "roles");

        // Handle additional simple attributes (dynamically mapped or default pass-through)
        handleMappedAttribute(scimNode, idmUser, "title");
        handleMappedAttribute(scimNode, idmUser, "preferredLanguage");
        handleMappedAttribute(scimNode, idmUser, "locale");
        handleMappedAttribute(scimNode, idmUser, "timezone");
        handleMappedAttribute(scimNode, idmUser, "profileUrl");

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

        // Handle addresses - deserialize from JSON string in PingIDM custom field
        handleDeserializedAttribute(idmUser, scimNode, "addresses");

        // Handle nickName - retrieve from PingIDM custom field
        handleReverseMappedAttribute(idmUser, scimNode, "nickName");

        // Handle userType - retrieve from PingIDM custom field
        handleReverseMappedAttribute(idmUser, scimNode, "userType");

        // Handle roles - deserialize from JSON string in PingIDM custom field
        handleDeserializedAttribute(idmUser, scimNode, "roles");

        // Handle additional simple attributes
        handleReverseMappedAttribute(idmUser, scimNode, "title");
        handleReverseMappedAttribute(idmUser, scimNode, "preferredLanguage");
        handleReverseMappedAttribute(idmUser, scimNode, "locale");
        handleReverseMappedAttribute(idmUser, scimNode, "timezone");
        handleReverseMappedAttribute(idmUser, scimNode, "profileUrl");

        // Handle meta object
        ObjectNode metaNode = buildMetaNode(idmUser);
        scimNode.set("meta", metaNode);

        return new GenericScimResource(scimNode);
    }

    /**
     * Looks up the PingIDM attribute mapping for a SCIM attribute and copies the value.
     */
    private void handleMappedAttribute(ObjectNode scimSource, ObjectNode idmDest, String scimAttribute) {
        String idmAttribute = resolveIdmAttribute(scimAttribute);

        if (scimSource.has(scimAttribute)) {
            JsonNode value = scimSource.get(scimAttribute);
            if (value != null && !value.isNull()) {
                idmDest.set(idmAttribute, value);
            }
        }
    }

    /**
     * Looks up the PingIDM attribute mapping for a SCIM attribute and copies the value (reverse mapping).
     */
    private void handleReverseMappedAttribute(ObjectNode idmSource, ObjectNode scimDest, String scimAttribute) {
        String idmAttribute = resolveIdmAttribute(scimAttribute);

        if (idmSource.has(idmAttribute)) {
            JsonNode value = idmSource.get(idmAttribute);
            if (value != null && !value.isNull()) {
                scimDest.set(scimAttribute, value);
            }
        }
    }

    /**
     * Handles serialization of complex SCIM attributes (like addresses, roles) into a string IDM field.
     */
    private void handleSerializedAttribute(ObjectNode scimSource, ObjectNode idmDest, String scimAttribute) {
        if (scimSource.has(scimAttribute) && scimSource.get(scimAttribute).isArray()) {
            ArrayNode arrayNode = (ArrayNode) scimSource.get(scimAttribute);
            if (arrayNode.size() > 0) {
                String idmAttribute = resolveIdmAttribute(scimAttribute);
                try {
                    String serializedJson = baseMapper.getObjectMapper().writeValueAsString(arrayNode);
                    idmDest.put(idmAttribute, serializedJson);
                    LOGGER.fine("Serialized " + scimAttribute + " to " + idmAttribute + ": " + serializedJson);
                } catch (Exception e) {
                    LOGGER.warning("Failed to serialize " + scimAttribute + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Handles deserialization of JSON string from IDM field back to SCIM complex attribute.
     */
    private void handleDeserializedAttribute(ObjectNode idmSource, ObjectNode scimDest, String scimAttribute) {
        String idmAttribute = resolveIdmAttribute(scimAttribute);

        if (idmSource.has(idmAttribute)) {
            try {
                String jsonString = idmSource.get(idmAttribute).asText();
                if (jsonString != null && !jsonString.isEmpty() && !jsonString.equals("null")) {
                    JsonNode deserializedNode = baseMapper.getObjectMapper().readTree(jsonString);
                    if (deserializedNode.isArray()) {
                        scimDest.set(scimAttribute, deserializedNode);
                        LOGGER.fine("Deserialized " + scimAttribute + " from " + idmAttribute);
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to parse " + scimAttribute + " JSON: " + e.getMessage());
            }
        }
    }

    /**
     * Helper to resolve the PingIDM attribute name from config, or default to the SCIM name.
     */
    private String resolveIdmAttribute(String scimAttribute) {
        if (mappingConfig != null) {
            Optional<CustomAttributeMapping> mapping = mappingConfig.getByScimPath(scimAttribute);
            if (mapping.isPresent()) {
                return mapping.get().getPingIdmAttribute();
            }
        }
        return scimAttribute;
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

                copyIfPresent(extNode, idmUser, "employeeNumber", resolveIdmAttribute("employeeNumber"));
                copyIfPresent(extNode, idmUser, "costCenter", resolveIdmAttribute("costCenter"));
                copyIfPresent(extNode, idmUser, "organization", resolveIdmAttribute("organization"));
                copyIfPresent(extNode, idmUser, "division", resolveIdmAttribute("division"));
                copyIfPresent(extNode, idmUser, "department", resolveIdmAttribute("department"));
                copyIfPresent(extNode, idmUser, "manager", resolveIdmAttribute("manager"));
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