package com.pingidentity.p1aic.scim.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unboundid.scim2.common.GenericScimResource;

import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Dynamic attribute mapper that converts between SCIM GenericScimResource
 * and PingIDM JSON format.
 *
 * This mapper handles both core attributes and custom attributes dynamically,
 * allowing customers to add custom attributes to User and Role managed objects
 * without code changes.
 */
public class DynamicAttributeMapper {

    private static final Logger LOGGER = Logger.getLogger(DynamicAttributeMapper.class.getName());

    private final ObjectMapper objectMapper;

    /**
     * Constructor initializes the Jackson ObjectMapper.
     */
    public DynamicAttributeMapper() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Convert SCIM GenericScimResource to PingIDM JSON format.
     *
     * @param scimResource the SCIM resource to convert
     * @param attributeMapping the attribute mapping configuration (SCIM -> PingIDM)
     * @return ObjectNode representing PingIDM JSON
     */
    public ObjectNode scimToPingIdm(GenericScimResource scimResource, Map<String, String> attributeMapping) {
        ObjectNode idmObject = objectMapper.createObjectNode();

        // Get the SCIM resource as ObjectNode
        ObjectNode scimNode = scimResource.asGenericScimResource().getObjectNode();

        // Iterate through all SCIM attributes
        Iterator<Map.Entry<String, JsonNode>> fields = scimNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String scimAttrName = field.getKey();
            JsonNode scimAttrValue = field.getValue();

            // Skip meta attributes (handled separately)
            if ("meta".equals(scimAttrName) || "schemas".equals(scimAttrName)) {
                continue;
            }

            // Map attribute name from SCIM to PingIDM
            String idmAttrName = mapAttributeName(scimAttrName, attributeMapping);

            // Convert attribute value based on type
            JsonNode idmAttrValue = convertScimValueToPingIdm(scimAttrValue);

            // Set the value in IDM object
            if (idmAttrValue != null && !idmAttrValue.isNull()) {
                idmObject.set(idmAttrName, idmAttrValue);
            }
        }

        return idmObject;
    }

    /**
     * Convert PingIDM JSON to SCIM GenericScimResource.
     *
     * @param idmObject the PingIDM JSON object
     * @param schemaUrn the SCIM schema URN for the resource
     * @param attributeMapping the attribute mapping configuration (PingIDM -> SCIM)
     * @param scimBaseUrl the base URL for generating resource location
     * @param resourceType the resource type (e.g., "Users", "Groups")
     * @return GenericScimResource
     */
    public GenericScimResource pingIdmToScim(
            ObjectNode idmObject,
            String schemaUrn,
            Map<String, String> attributeMapping,
            String scimBaseUrl,
            String resourceType) {

        ObjectNode scimNode = objectMapper.createObjectNode();

        // Add schema URN
        ArrayNode schemas = objectMapper.createArrayNode();
        schemas.add(schemaUrn);
        scimNode.set("schemas", schemas);

        // Extract ID and revision from PingIDM
        String id = idmObject.has("_id") ? idmObject.get("_id").asText() : null;
        String revision = idmObject.has("_rev") ? idmObject.get("_rev").asText() : null;

        if (id != null) {
            scimNode.put("id", id);
        }

        // Build meta object
        ObjectNode metaNode = buildMetaNode(idmObject, scimBaseUrl, resourceType, id, revision);
        scimNode.set("meta", metaNode);

        // Iterate through all PingIDM attributes
        Iterator<Map.Entry<String, JsonNode>> fields = idmObject.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String idmAttrName = field.getKey();
            JsonNode idmAttrValue = field.getValue();

            // Skip internal PingIDM attributes
            if (isInternalIdmAttribute(idmAttrName)) {
                continue;
            }

            // Map attribute name from PingIDM to SCIM
            String scimAttrName = mapAttributeName(idmAttrName, attributeMapping);

            // Convert attribute value based on type
            JsonNode scimAttrValue = convertPingIdmValueToScim(idmAttrValue);

            // Set the value in SCIM object
            if (scimAttrValue != null && !scimAttrValue.isNull()) {
                scimNode.set(scimAttrName, scimAttrValue);
            }
        }

        // Create GenericScimResource from ObjectNode
        return new GenericScimResource(scimNode);
    }

    /**
     * Map attribute name using the provided mapping, or return as-is if no mapping exists.
     */
    private String mapAttributeName(String attrName, Map<String, String> attributeMapping) {
        return attributeMapping.getOrDefault(attrName, attrName);
    }

    /**
     * Convert SCIM attribute value to PingIDM format.
     * Handles complex objects, arrays, and primitives.
     */
    private JsonNode convertScimValueToPingIdm(JsonNode scimValue) {
        if (scimValue == null || scimValue.isNull()) {
            return null;
        }

        // Handle complex objects (e.g., name: {givenName, familyName})
        if (scimValue.isObject()) {
            return scimValue; // Pass through as-is for now
        }

        // Handle arrays
        if (scimValue.isArray()) {
            ArrayNode resultArray = objectMapper.createArrayNode();
            for (JsonNode element : scimValue) {
                JsonNode converted = convertScimValueToPingIdm(element);
                if (converted != null && !converted.isNull()) {
                    resultArray.add(converted);
                }
            }
            return resultArray;
        }

        // Handle primitives (string, number, boolean)
        return scimValue;
    }

    /**
     * Convert PingIDM attribute value to SCIM format.
     * Handles complex objects, arrays, and primitives.
     */
    private JsonNode convertPingIdmValueToScim(JsonNode idmValue) {
        if (idmValue == null || idmValue.isNull()) {
            return null;
        }

        // Handle complex objects
        if (idmValue.isObject()) {
            return idmValue; // Pass through as-is for now
        }

        // Handle arrays
        if (idmValue.isArray()) {
            ArrayNode resultArray = objectMapper.createArrayNode();
            for (JsonNode element : idmValue) {
                JsonNode converted = convertPingIdmValueToScim(element);
                if (converted != null && !converted.isNull()) {
                    resultArray.add(converted);
                }
            }
            return resultArray;
        }

        // Handle primitives (string, number, boolean)
        return idmValue;
    }

    /**
     * Build SCIM meta object from PingIDM metadata.
     */
    private ObjectNode buildMetaNode(
            ObjectNode idmObject,
            String scimBaseUrl,
            String resourceType,
            String id,
            String revision) {

        ObjectNode metaNode = objectMapper.createObjectNode();

        // Set resourceType
        metaNode.put("resourceType", resourceType.replaceAll("s$", "")); // "Users" -> "User"

        // Set location
        if (scimBaseUrl != null && id != null) {
            String location = String.format("%s/%s/%s", scimBaseUrl, resourceType, id);
            metaNode.put("location", location);
        }

        // Set version (use _rev as ETag)
        if (revision != null) {
            metaNode.put("version", revision);
        }

        // Extract created and lastModified timestamps from _meta if available
        if (idmObject.has("_meta")) {
            JsonNode metaData = idmObject.get("_meta");

            if (metaData.has("created")) {
                String created = metaData.get("created").asText();
                metaNode.put("created", created);
            }

            if (metaData.has("lastModified")) {
                String lastModified = metaData.get("lastModified").asText();
                metaNode.put("lastModified", lastModified);
            }
        }

        return metaNode;
    }

    /**
     * Check if an attribute is an internal PingIDM attribute that should not be mapped to SCIM.
     */
    private boolean isInternalIdmAttribute(String attrName) {
        return attrName.startsWith("_") ||
                "effectiveRoles".equals(attrName) ||
                "effectiveAssignments".equals(attrName) ||
                "authzRoles".equals(attrName) ||
                "kbaInfo".equals(attrName) ||
                "preferences".equals(attrName);
    }

    /**
     * Get the ObjectMapper instance.
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
