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
 */
public class UserAttributeMapper {

    private static final Logger LOGGER = Logger.getLogger(UserAttributeMapper.class.getName());

    private final DynamicAttributeMapper baseMapper;
    private final ScimServerConfig config;
    private final CustomAttributeMappingConfig mappingConfig;

    public UserAttributeMapper() {
        this(new CustomAttributeMappingConfig());
    }

    @Inject
    public UserAttributeMapper(CustomAttributeMappingConfig mappingConfig) {
        this.baseMapper = new DynamicAttributeMapper();
        this.config = ScimServerConfig.getInstance();
        this.mappingConfig = mappingConfig;
    }

    public ObjectNode scimToPingIdm(GenericScimResource scimUser) {
        ObjectNode scimNode = scimUser.asGenericScimResource().getObjectNode();
        ObjectNode idmUser = baseMapper.getObjectMapper().createObjectNode();

        // Standard attributes
        handleMappedAttribute(scimNode, idmUser, "userName");
        handleMappedAttribute(scimNode, idmUser, "displayName");

        if (scimNode.has("password")) {
            idmUser.put("password", scimNode.get("password").asText());
        }
        if (scimNode.has("active")) {
            idmUser.put("accountStatus", scimNode.get("active").asBoolean() ? "active" : "inactive");
        }

        // Name object
        if (scimNode.has("name") && scimNode.get("name").isObject()) {
            ObjectNode nameNode = (ObjectNode) scimNode.get("name");
            if (nameNode.has("givenName")) idmUser.put("givenName", nameNode.get("givenName").asText());
            if (nameNode.has("familyName")) idmUser.put("sn", nameNode.get("familyName").asText());
            if (nameNode.has("formatted")) idmUser.put("cn", nameNode.get("formatted").asText());

            handleMappedAttribute(nameNode, idmUser, "middleName", "name.middleName");
            handleMappedAttribute(nameNode, idmUser, "honorificPrefix", "name.honorificPrefix");
            handleMappedAttribute(nameNode, idmUser, "honorificSuffix", "name.honorificSuffix");
        }

        // Emails
        if (scimNode.has("emails") && scimNode.get("emails").isArray()) {
            ArrayNode emails = (ArrayNode) scimNode.get("emails");
            String primaryEmail = extractPrimaryOrFirst(emails, "value");
            if (primaryEmail != null) idmUser.put("mail", primaryEmail);
        }

        // Phone numbers (Flatten AND Serialize)
        if (scimNode.has("phoneNumbers") && scimNode.get("phoneNumbers").isArray()) {
            ArrayNode phones = (ArrayNode) scimNode.get("phoneNumbers");
            String primaryPhone = extractPrimaryOrFirst(phones, "value");
            if (primaryPhone != null) idmUser.put("telephoneNumber", primaryPhone);
        }
        handleSerializedAttribute(scimNode, idmUser, "phoneNumbers");

        // Complex serialized attributes
        handleSerializedAttribute(scimNode, idmUser, "addresses");
        handleSerializedAttribute(scimNode, idmUser, "roles");

        // Simple mapped attributes
        handleMappedAttribute(scimNode, idmUser, "nickName");
        handleMappedAttribute(scimNode, idmUser, "userType");
        handleMappedAttribute(scimNode, idmUser, "title");
        handleMappedAttribute(scimNode, idmUser, "preferredLanguage");
        handleMappedAttribute(scimNode, idmUser, "locale");
        handleMappedAttribute(scimNode, idmUser, "timezone");
        handleMappedAttribute(scimNode, idmUser, "profileUrl");

        // Extension attributes
        handleExtensionAttributes(scimNode, idmUser);

        return idmUser;
    }

    public GenericScimResource pingIdmToScim(ObjectNode idmUser) {
        ObjectNode scimNode = baseMapper.getObjectMapper().createObjectNode();
        ArrayNode schemas = baseMapper.getObjectMapper().createArrayNode();
        schemas.add(ScimSchemaUrns.CORE_USER_SCHEMA);
        scimNode.set("schemas", schemas);

        if (idmUser.has("_id")) scimNode.put("id", idmUser.get("_id").asText());

        handleReverseMappedAttribute(idmUser, scimNode, "userName");
        handleReverseMappedAttribute(idmUser, scimNode, "displayName");

        if (idmUser.has("accountStatus")) {
            scimNode.put("active", "active".equalsIgnoreCase(idmUser.get("accountStatus").asText()));
        }

        ObjectNode nameNode = baseMapper.getObjectMapper().createObjectNode();
        boolean hasName = false;
        if (idmUser.has("givenName")) { nameNode.put("givenName", idmUser.get("givenName").asText()); hasName = true; }
        if (idmUser.has("sn")) { nameNode.put("familyName", idmUser.get("sn").asText()); hasName = true; }
        if (idmUser.has("cn")) { nameNode.put("formatted", idmUser.get("cn").asText()); hasName = true; }

        hasName |= handleReverseMappedAttribute(idmUser, nameNode, "middleName", "name.middleName");
        hasName |= handleReverseMappedAttribute(idmUser, nameNode, "honorificPrefix", "name.honorificPrefix");
        hasName |= handleReverseMappedAttribute(idmUser, nameNode, "honorificSuffix", "name.honorificSuffix");

        if (hasName) scimNode.set("name", nameNode);

        if (idmUser.has("mail")) {
            ArrayNode emails = baseMapper.getObjectMapper().createArrayNode();
            ObjectNode emailObj = baseMapper.getObjectMapper().createObjectNode();
            emailObj.put("value", idmUser.get("mail").asText());
            emailObj.put("primary", true);
            emailObj.put("type", "work");
            emails.add(emailObj);
            scimNode.set("emails", emails);
        }

        handleDeserializedAttribute(idmUser, scimNode, "addresses");
        handleDeserializedAttribute(idmUser, scimNode, "roles");

        if (!handleDeserializedAttribute(idmUser, scimNode, "phoneNumbers") && idmUser.has("telephoneNumber")) {
            ArrayNode phones = baseMapper.getObjectMapper().createArrayNode();
            ObjectNode phoneObj = baseMapper.getObjectMapper().createObjectNode();
            phoneObj.put("value", idmUser.get("telephoneNumber").asText());
            phoneObj.put("primary", true);
            phoneObj.put("type", "work");
            phones.add(phoneObj);
            scimNode.set("phoneNumbers", phones);
        }

        handleReverseMappedAttribute(idmUser, scimNode, "nickName");
        handleReverseMappedAttribute(idmUser, scimNode, "userType");
        handleReverseMappedAttribute(idmUser, scimNode, "title");
        handleReverseMappedAttribute(idmUser, scimNode, "preferredLanguage");
        handleReverseMappedAttribute(idmUser, scimNode, "locale");
        handleReverseMappedAttribute(idmUser, scimNode, "timezone");
        handleReverseMappedAttribute(idmUser, scimNode, "profileUrl");

        handleExtensionAttributesReverse(idmUser, scimNode);
        scimNode.set("meta", buildMetaNode(idmUser));

        return new GenericScimResource(scimNode);
    }

    private void handleMappedAttribute(ObjectNode source, ObjectNode dest, String attributeName) {
        handleMappedAttribute(source, dest, attributeName, attributeName);
    }

    private void handleMappedAttribute(ObjectNode source, ObjectNode dest, String jsonKey, String configKey) {
        String idmAttr = resolveIdmAttribute(configKey);
        if (source.has(jsonKey)) {
            JsonNode value = source.get(jsonKey);
            if (value != null && !value.isNull()) {
                if (value.isTextual() && value.asText().isEmpty()) return;
                dest.set(idmAttr, value);
            }
        }
    }

    private boolean handleReverseMappedAttribute(ObjectNode source, ObjectNode dest, String attributeName) {
        return handleReverseMappedAttribute(source, dest, attributeName, attributeName);
    }

    private boolean handleReverseMappedAttribute(ObjectNode source, ObjectNode dest, String jsonKey, String configKey) {
        String idmAttr = resolveIdmAttribute(configKey);
        if (source.has(idmAttr)) {
            JsonNode value = source.get(idmAttr);
            if (value != null && !value.isNull()) {
                dest.set(jsonKey, value);
                return true;
            }
        }
        return false;
    }

    private void handleSerializedAttribute(ObjectNode source, ObjectNode dest, String scimAttr) {
        if (source.has(scimAttr) && source.get(scimAttr).isArray()) {
            String idmAttr = resolveIdmAttribute(scimAttr);
            try {
                ArrayNode arr = (ArrayNode) source.get(scimAttr);
                // Fix boolean fields (e.g. primary)
                for (JsonNode item : arr) {
                    if (item.isObject()) {
                        ObjectNode obj = (ObjectNode) item;
                        if (obj.has("primary") && obj.get("primary").isTextual()) {
                            boolean val = Boolean.parseBoolean(obj.get("primary").asText());
                            obj.put("primary", val);
                        }
                    }
                }

                // Check if target is Multivalued (Array) or String
                if (idmAttr.startsWith("frIndexedMultivalued")) {
                    ArrayNode idmArray = baseMapper.getObjectMapper().createArrayNode();
                    for (JsonNode item : arr) {
                        String itemJson = baseMapper.getObjectMapper().writeValueAsString(item);
                        idmArray.add(itemJson);
                    }
                    dest.set(idmAttr, idmArray);
                } else {
                    String json = baseMapper.getObjectMapper().writeValueAsString(arr);
                    dest.put(idmAttr, json);
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to serialize " + scimAttr + ": " + e.getMessage());
            }
        }
    }

    private boolean handleDeserializedAttribute(ObjectNode source, ObjectNode dest, String scimAttr) {
        String idmAttr = resolveIdmAttribute(scimAttr);
        if (source.has(idmAttr)) {
            try {
                JsonNode idmValue = source.get(idmAttr);

                // Case 1: Array of JSON Strings (frIndexedMultivalued)
                if (idmValue.isArray()) {
                    ArrayNode scimArray = baseMapper.getObjectMapper().createArrayNode();
                    for (JsonNode item : idmValue) {
                        if (item.isTextual()) {
                            JsonNode node = baseMapper.getObjectMapper().readTree(item.asText());
                            scimArray.add(node);
                        }
                    }
                    if (scimArray.size() > 0) {
                        dest.set(scimAttr, scimArray);
                        return true;
                    }
                }
                // Case 2: Single JSON String blob (frIndexedString)
                else if (idmValue.isTextual()) {
                    String json = idmValue.asText();
                    if (json != null && !json.equals("null") && !json.isEmpty()) {
                        JsonNode node = baseMapper.getObjectMapper().readTree(json);
                        if (node.isArray()) {
                            dest.set(scimAttr, node);
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to deserialize " + scimAttr + ": " + e.getMessage());
            }
        }
        return false;
    }

    private void handleExtensionAttributes(ObjectNode scimNode, ObjectNode idmUser) {
        if (scimNode.has(ScimSchemaUrns.ENTERPRISE_USER_EXTENSION)) {
            JsonNode ext = scimNode.get(ScimSchemaUrns.ENTERPRISE_USER_EXTENSION);
            if (ext.isObject()) {
                ObjectNode extObj = (ObjectNode) ext;
                copyIfPresent(extObj, idmUser, "employeeNumber", resolveIdmAttribute("employeeNumber"));
                copyIfPresent(extObj, idmUser, "costCenter", resolveIdmAttribute("costCenter"));
                copyIfPresent(extObj, idmUser, "organization", resolveIdmAttribute("organization"));
                copyIfPresent(extObj, idmUser, "division", resolveIdmAttribute("division"));
                copyIfPresent(extObj, idmUser, "department", resolveIdmAttribute("department"));

                if (extObj.has("manager")) {
                    JsonNode managerNode = extObj.get("manager");
                    if (managerNode.has("value")) {
                        String managerId = managerNode.get("value").asText();
                        if (managerId != null && !managerId.isEmpty()) {
                            ObjectNode ref = baseMapper.getObjectMapper().createObjectNode();
                            ref.put("_ref", "managed/" + config.getManagedUserObjectName() + "/" + managerId);
                            idmUser.set(resolveIdmAttribute("manager"), ref);
                        } else {
                            idmUser.set(resolveIdmAttribute("manager"), null);
                        }
                    }
                }
            }
        }
    }

    private void handleExtensionAttributesReverse(ObjectNode idmUser, ObjectNode scimNode) {
        ObjectNode enterpriseExt = baseMapper.getObjectMapper().createObjectNode();
        boolean hasExt = false;
        hasExt |= handleReverseMappedAttribute(idmUser, enterpriseExt, "employeeNumber");
        hasExt |= handleReverseMappedAttribute(idmUser, enterpriseExt, "costCenter");
        hasExt |= handleReverseMappedAttribute(idmUser, enterpriseExt, "organization");
        hasExt |= handleReverseMappedAttribute(idmUser, enterpriseExt, "division");
        hasExt |= handleReverseMappedAttribute(idmUser, enterpriseExt, "department");

        String managerAttr = resolveIdmAttribute("manager");
        if (idmUser.has(managerAttr)) {
            JsonNode managerRef = idmUser.get(managerAttr);
            if (managerRef.isObject() && managerRef.has("_ref")) {
                String ref = managerRef.get("_ref").asText();
                String[] parts = ref.split("/");
                if (parts.length > 0) {
                    String managerId = parts[parts.length - 1];
                    ObjectNode managerObj = baseMapper.getObjectMapper().createObjectNode();
                    managerObj.put("value", managerId);
                    managerObj.put("$ref", config.getScimServerBaseUrl() + "/Users/" + managerId);
                    enterpriseExt.set("manager", managerObj);
                    hasExt = true;
                }
            }
        }

        if (hasExt) {
            scimNode.set(ScimSchemaUrns.ENTERPRISE_USER_EXTENSION, enterpriseExt);
            ArrayNode schemas = (ArrayNode) scimNode.get("schemas");
            boolean present = false;
            for(JsonNode schema : schemas) {
                if(ScimSchemaUrns.ENTERPRISE_USER_EXTENSION.equals(schema.asText())) present = true;
            }
            if(!present) schemas.add(ScimSchemaUrns.ENTERPRISE_USER_EXTENSION);
        }
    }

    private String resolveIdmAttribute(String scimAttr) {
        if (mappingConfig != null) {
            return mappingConfig.getByScimPath(scimAttr)
                    .map(CustomAttributeMapping::getPingIdmAttribute)
                    .orElse(scimAttr);
        }
        return scimAttr;
    }

    private String extractPrimaryOrFirst(ArrayNode array, String field) {
        String first = null;
        for (JsonNode n : array) {
            if (n.has("primary") && n.get("primary").asBoolean() && n.has(field)) return n.get(field).asText();
            if (first == null && n.has(field)) first = n.get(field).asText();
        }
        return first;
    }

    private void copyIfPresent(ObjectNode src, ObjectNode dest, String srcKey, String destKey) {
        if (src.has(srcKey)) {
            JsonNode val = src.get(srcKey);
            if (val != null && !val.isNull()) {
                if (val.isTextual() && val.asText().isEmpty()) return;
                dest.set(destKey, val);
            }
        }
    }

    private ObjectNode buildMetaNode(ObjectNode idmUser) {
        ObjectNode meta = baseMapper.getObjectMapper().createObjectNode();
        meta.put("resourceType", "User");
        if (idmUser.has("_id")) meta.put("location", config.getScimServerBaseUrl() + "/Users/" + idmUser.get("_id").asText());
        if (idmUser.has("_rev")) meta.put("version", idmUser.get("_rev").asText());
        if (idmUser.has("_meta") && idmUser.get("_meta").isObject()) {
            ObjectNode idmMeta = (ObjectNode) idmUser.get("_meta");
            if (idmMeta.has("created")) meta.put("created", idmMeta.get("created").asText());
            if (idmMeta.has("lastModified")) meta.put("lastModified", idmMeta.get("lastModified").asText());
        }
        return meta;
    }
}