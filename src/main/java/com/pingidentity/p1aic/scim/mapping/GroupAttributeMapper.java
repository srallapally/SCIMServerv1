package com.pingidentity.p1aic.scim.mapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.config.ScimServerConfig;
import com.pingidentity.p1aic.scim.schema.ScimSchemaUrns;
import com.unboundid.scim2.common.GenericScimResource;
import java.util.logging.Logger;

/**
 * Group/Role-specific attribute mapper that converts between SCIM Group resources
 * and PingIDM managed role objects.
 *
 * Handles standard SCIM Group attributes and their mapping to PingIDM role equivalents.
 */
public class GroupAttributeMapper {

    private static final Logger LOGGER = Logger.getLogger(GroupAttributeMapper.class.getName());

    private final DynamicAttributeMapper baseMapper;
    private final ScimServerConfig config;

    /**
     * Constructor initializes the base mapper.
     */
    public GroupAttributeMapper() {
        this.baseMapper = new DynamicAttributeMapper();
        this.config = ScimServerConfig.getInstance();
    }

    /**
     * Convert SCIM Group resource to PingIDM role JSON format.
     */
    public ObjectNode scimToPingIdm(GenericScimResource scimGroup) {
        ObjectNode scimNode = scimGroup.asGenericScimResource().getObjectNode();
        ObjectNode idmRole = baseMapper.getObjectMapper().createObjectNode();

        // Handle displayName -> name
        if (scimNode.has("displayName")) {
            idmRole.put("name", scimNode.get("displayName").asText());
        }

        // Handle description
        if (scimNode.has("description")) {
            idmRole.put("description", scimNode.get("description").asText());
        }

        // Handle members array
        if (scimNode.has("members") && scimNode.get("members").isArray()) {
            ArrayNode scimMembers = (ArrayNode) scimNode.get("members");
            ArrayNode idmMembers = convertScimMembersToPingIdm(scimMembers);
            if (idmMembers.size() > 0) {
                idmRole.set("members", idmMembers);
            }
        }

        // Handle custom extension attributes
        handleExtensionAttributes(scimNode, idmRole);

        return idmRole;
    }

    /**
     * Convert PingIDM role JSON to SCIM Group resource.
     */
    public GenericScimResource pingIdmToScim(ObjectNode idmRole) {
        ObjectNode scimNode = baseMapper.getObjectMapper().createObjectNode();

        // Add schemas
        ArrayNode schemas = baseMapper.getObjectMapper().createArrayNode();
        schemas.add(ScimSchemaUrns.CORE_GROUP_SCHEMA);
        scimNode.set("schemas", schemas);

        // Handle id
        if (idmRole.has("_id")) {
            scimNode.put("id", idmRole.get("_id").asText());
        }

        // Handle name -> displayName
        if (idmRole.has("name")) {
            scimNode.put("displayName", idmRole.get("name").asText());
        }

        // Handle description
        if (idmRole.has("description")) {
            scimNode.put("description", idmRole.get("description").asText());
        }

        // Handle members array
        if (idmRole.has("members")) {
            JsonNode idmMembers = idmRole.get("members");
            if (idmMembers.isArray()) {
                ArrayNode scimMembers = convertPingIdmMembersToScim((ArrayNode) idmMembers);
                if (scimMembers.size() > 0) {
                    scimNode.set("members", scimMembers);
                }
            }
        }

        // Handle meta object
        ObjectNode metaNode = buildMetaNode(idmRole);
        scimNode.set("meta", metaNode);

        return new GenericScimResource(scimNode);
    }

    /**
     * Convert SCIM members array to PingIDM members format.
     *
     * SCIM members format:
     * [
     *   {"value": "user123", "$ref": "https://example.com/Users/user123", "type": "User"},
     *   {"value": "user456", "$ref": "https://example.com/Users/user456", "type": "User"}
     * ]
     *
     * PingIDM members format (relationship):
     * [
     *   {"_ref": "managed/alpha_user/user123"},
     *   {"_ref": "managed/alpha_user/user456"}
     * ]
     */
    private ArrayNode convertScimMembersToPingIdm(ArrayNode scimMembers) {
        ArrayNode idmMembers = baseMapper.getObjectMapper().createArrayNode();

        for (JsonNode memberNode : scimMembers) {
            if (memberNode.isObject()) {
                ObjectNode member = (ObjectNode) memberNode;

                // Extract member ID from value field
                if (member.has("value")) {
                    String memberId = member.get("value").asText();

                    // Determine member type (default to User)
                    String memberType = "user";
                    if (member.has("type")) {
                        String type = member.get("type").asText();
                        if ("Group".equalsIgnoreCase(type)) {
                            memberType = "role";
                        }
                    }

                    // Build PingIDM relationship reference
                    ObjectNode idmMember = baseMapper.getObjectMapper().createObjectNode();
                    String refPath = String.format("managed/%s_%s/%s",
                            config.getRealm(), memberType, memberId);
                    idmMember.put("_ref", refPath);

                    idmMembers.add(idmMember);
                }
            }
        }

        return idmMembers;
    }

    /**
     * Convert PingIDM members array to SCIM members format.
     *
     * PingIDM members format:
     * [
     *   {"_ref": "managed/alpha_user/user123", "_refResourceCollection": "managed/alpha_user", "_refResourceId": "user123"},
     *   {"_ref": "managed/alpha_user/user456", "_refResourceCollection": "managed/alpha_user", "_refResourceId": "user456"}
     * ]
     *
     * SCIM members format:
     * [
     *   {"value": "user123", "$ref": "https://example.com/Users/user123", "type": "User"},
     *   {"value": "user456", "$ref": "https://example.com/Users/user456", "type": "User"}
     * ]
     */
    private ArrayNode convertPingIdmMembersToScim(ArrayNode idmMembers) {
        ArrayNode scimMembers = baseMapper.getObjectMapper().createArrayNode();

        for (JsonNode memberNode : idmMembers) {
            if (memberNode.isObject()) {
                ObjectNode member = (ObjectNode) memberNode;

                String memberId = null;
                String memberType = "User"; // Default type

                // Extract member ID from _refResourceId or parse from _ref
                if (member.has("_refResourceId")) {
                    memberId = member.get("_refResourceId").asText();
                } else if (member.has("_ref")) {
                    // Parse _ref: "managed/alpha_user/user123" -> "user123"
                    String ref = member.get("_ref").asText();
                    String[] parts = ref.split("/");
                    if (parts.length >= 3) {
                        memberId = parts[parts.length - 1];

                        // Determine type from collection name
                        String collection = parts[parts.length - 2];
                        if (collection.endsWith("_role")) {
                            memberType = "Group";
                        }
                    }
                }

                if (memberId != null) {
                    // Build SCIM member object
                    ObjectNode scimMember = baseMapper.getObjectMapper().createObjectNode();
                    scimMember.put("value", memberId);
                    scimMember.put("type", memberType);

                    // Build $ref URL
                    String resourceType = "User".equals(memberType) ? "Users" : "Groups";
                    String refUrl = String.format("%s/%s/%s",
                            config.getScimServerBaseUrl(), resourceType, memberId);
                    scimMember.put("$ref", refUrl);

                    scimMembers.add(scimMember);
                }
            }
        }

        return scimMembers;
    }

    /**
     * Handle SCIM extension attributes for Groups.
     */
    private void handleExtensionAttributes(ObjectNode scimNode, ObjectNode idmRole) {
        // Check for custom PingIDM extension
        if (scimNode.has(ScimSchemaUrns.PINGIDM_GROUP_EXTENSION)) {
            JsonNode extension = scimNode.get(ScimSchemaUrns.PINGIDM_GROUP_EXTENSION);
            if (extension.isObject()) {
                ObjectNode extNode = (ObjectNode) extension;

                // Copy all custom attributes to IDM role
                extNode.fields().forEachRemaining(entry -> {
                    idmRole.set(entry.getKey(), entry.getValue());
                });
            }
        }
    }

    /**
     * Build SCIM meta object from PingIDM metadata.
     */
    private ObjectNode buildMetaNode(ObjectNode idmRole) {
        ObjectNode metaNode = baseMapper.getObjectMapper().createObjectNode();

        metaNode.put("resourceType", "Group");

        // Build location URL
        if (idmRole.has("_id")) {
            String id = idmRole.get("_id").asText();
            String location = String.format("%s/Groups/%s", config.getScimServerBaseUrl(), id);
            metaNode.put("location", location);
        }

        // Set version (use _rev as ETag)
        if (idmRole.has("_rev")) {
            metaNode.put("version", idmRole.get("_rev").asText());
        }

        // Extract timestamps from _meta if available
        if (idmRole.has("_meta") && idmRole.get("_meta").isObject()) {
            ObjectNode idmMeta = (ObjectNode) idmRole.get("_meta");

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