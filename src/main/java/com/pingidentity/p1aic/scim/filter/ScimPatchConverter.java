package com.pingidentity.p1aic.scim.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.exceptions.FilterTranslationException;
import com.pingidentity.p1aic.scim.mapping.AttributeMappingConfig;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Converter for SCIM PATCH operations to PingIDM PATCH format.
 *
 * SCIM PATCH uses RFC 7644 Section 3.5.2 format with operations like:
 * {
 *   "schemas": ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
 *   "Operations": [
 *     {"op": "add", "path": "emails", "value": {...}},
 *     {"op": "replace", "path": "active", "value": true},
 *     {"op": "remove", "path": "phoneNumbers[type eq \"fax\"]"}
 *   ]
 * }
 *
 * PingIDM PATCH uses RFC 6902 JSON Patch format:
 * [
 *   {"operation": "add", "field": "/mail", "value": "..."},
 *   {"operation": "replace", "field": "/accountStatus", "value": "active"},
 *   {"operation": "remove", "field": "/telephoneNumber"}
 * ]
 */
public class ScimPatchConverter {

    private static final Logger LOGGER = Logger.getLogger(ScimPatchConverter.class.getName());

    private final ObjectMapper objectMapper;
    private final Map<String, String> attributeMappings;
    private final String resourceType; // "User" or "Group"

    /**
     * Constructor for User resource PATCH conversion.
     *
     * @param resourceType the resource type ("User" or "Group")
     */
    public ScimPatchConverter(String resourceType) {
        this.objectMapper = new ObjectMapper();
        this.resourceType = resourceType;

        // Get appropriate attribute mappings based on resource type
        AttributeMappingConfig mappingConfig = AttributeMappingConfig.getInstance();
        if ("User".equalsIgnoreCase(resourceType)) {
            this.attributeMappings = mappingConfig.getUserScimToIdmMappings();
        } else if ("Group".equalsIgnoreCase(resourceType)) {
            this.attributeMappings = mappingConfig.getGroupScimToIdmMappings();
        } else {
            this.attributeMappings = Map.of();
        }
    }

    /**
     * Convert SCIM PATCH request to PingIDM PATCH format.
     *
     * @param scimPatchRequest the SCIM PATCH request as JSON string
     * @return PingIDM PATCH operations as JSON string
     * @throws FilterTranslationException if conversion fails
     */
    public String convert(String scimPatchRequest) throws FilterTranslationException {
        if (scimPatchRequest == null || scimPatchRequest.trim().isEmpty()) {
            throw new FilterTranslationException("SCIM PATCH request is null or empty");
        }

        try {
            LOGGER.info("Converting SCIM PATCH request for " + resourceType);

            // Parse SCIM PATCH request
            JsonNode scimPatch = objectMapper.readTree(scimPatchRequest);

            // Validate SCIM PATCH format
            if (!scimPatch.has("Operations") && !scimPatch.has("operations")) {
                throw new FilterTranslationException("SCIM PATCH request missing 'Operations' field");
            }

            // Get operations array (case-insensitive)
            JsonNode operationsNode = scimPatch.has("Operations")
                    ? scimPatch.get("Operations")
                    : scimPatch.get("operations");

            if (!operationsNode.isArray()) {
                throw new FilterTranslationException("'Operations' field must be an array");
            }

            // Convert operations
            ArrayNode idmOperations = objectMapper.createArrayNode();
            ArrayNode scimOperations = (ArrayNode) operationsNode;

            for (JsonNode operation : scimOperations) {
                ObjectNode idmOperation = convertOperation(operation);
                if (idmOperation != null) {
                    idmOperations.add(idmOperation);
                }
            }

            // Return as JSON array string
            String result = objectMapper.writeValueAsString(idmOperations);
            LOGGER.info("Converted SCIM PATCH to PingIDM format: " + result);
            return result;

        } catch (FilterTranslationException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.severe("Failed to convert SCIM PATCH: " + e.getMessage());
            throw new FilterTranslationException("Failed to convert SCIM PATCH: " + e.getMessage(), e);
        }
    }

    /**
     * Convert a single SCIM PATCH operation to PingIDM format.
     *
     * @param scimOp the SCIM operation
     * @return PingIDM operation, or null if operation should be skipped
     * @throws FilterTranslationException if conversion fails
     */
    private ObjectNode convertOperation(JsonNode scimOp) throws FilterTranslationException {
        if (!scimOp.has("op")) {
            throw new FilterTranslationException("SCIM operation missing 'op' field");
        }

        String op = scimOp.get("op").asText().toLowerCase();
        String path = scimOp.has("path") ? scimOp.get("path").asText() : null;
        JsonNode value = scimOp.has("value") ? scimOp.get("value") : null;

        ObjectNode idmOp = objectMapper.createObjectNode();

        switch (op) {
            case "add":
                return convertAddOperation(path, value, idmOp);
            case "replace":
                return convertReplaceOperation(path, value, idmOp);
            case "remove":
                return convertRemoveOperation(path, idmOp);
            default:
                throw new FilterTranslationException("Unsupported SCIM PATCH operation: " + op);
        }
    }

    /**
     * Convert SCIM "add" operation to PingIDM format.
     */
    private ObjectNode convertAddOperation(String path, JsonNode value, ObjectNode idmOp)
            throws FilterTranslationException {
        if (value == null) {
            throw new FilterTranslationException("'add' operation requires a value");
        }

        idmOp.put("operation", "add");

        // If path is null, we're adding fields from the value object
        if (path == null || path.isEmpty()) {
            // For no-path add, the value should be an object with fields to add
            if (!value.isObject()) {
                throw new FilterTranslationException("'add' without path requires value to be an object");
            }
            // For simplicity, we'll add each field separately
            // This creates multiple operations, but we'll return just the first
            // A more complete implementation would return multiple operations
            LOGGER.warning("'add' without path not fully supported - use explicit paths");
            return null;
        }

        // Map SCIM path to PingIDM field
        String idmField = mapScimPathToIdmField(path);
        idmOp.put("field", "/" + idmField);

        // Convert value based on attribute type
        JsonNode idmValue = convertValue(path, value);
        idmOp.set("value", idmValue);

        return idmOp;
    }

    /**
     * Convert SCIM "replace" operation to PingIDM format.
     */
    private ObjectNode convertReplaceOperation(String path, JsonNode value, ObjectNode idmOp)
            throws FilterTranslationException {
        if (path == null || path.isEmpty()) {
            throw new FilterTranslationException("'replace' operation requires a path");
        }
        if (value == null) {
            throw new FilterTranslationException("'replace' operation requires a value");
        }

        idmOp.put("operation", "replace");

        // Map SCIM path to PingIDM field
        String idmField = mapScimPathToIdmField(path);
        idmOp.put("field", "/" + idmField);

        // Convert value based on attribute type
        JsonNode idmValue = convertValue(path, value);
        idmOp.set("value", idmValue);

        return idmOp;
    }

    /**
     * Convert SCIM "remove" operation to PingIDM format.
     */
    private ObjectNode convertRemoveOperation(String path, ObjectNode idmOp)
            throws FilterTranslationException {
        if (path == null || path.isEmpty()) {
            throw new FilterTranslationException("'remove' operation requires a path");
        }

        idmOp.put("operation", "remove");

        // Map SCIM path to PingIDM field
        String idmField = mapScimPathToIdmField(path);
        idmOp.put("field", "/" + idmField);

        return idmOp;
    }

    /**
     * Map SCIM path to PingIDM field name.
     * Handles both simple paths (e.g., "active") and complex paths (e.g., "name.givenName").
     *
     * @param scimPath the SCIM path
     * @return the PingIDM field name
     */
    private String mapScimPathToIdmField(String scimPath) {
        if (scimPath == null || scimPath.isEmpty()) {
            return scimPath;
        }

        // Remove leading slash if present (SCIM paths may or may not have it)
        String path = scimPath.startsWith("/") ? scimPath.substring(1) : scimPath;

        // Handle filter expressions in path (e.g., "emails[type eq \"work\"].value")
        // For now, we'll strip the filter and just use the base attribute
        if (path.contains("[")) {
            int bracketIndex = path.indexOf('[');
            String baseAttr = path.substring(0, bracketIndex);
            // Map the base attribute
            String mapped = attributeMappings.getOrDefault(baseAttr, baseAttr);
            LOGGER.info("Simplified filtered path '" + path + "' to base attribute '" + mapped + "'");
            return mapped;
        }

        // Use attribute mapping, or pass through if not found
        return attributeMappings.getOrDefault(path, path);
    }

    /**
     * Convert SCIM value to PingIDM format based on attribute type.
     * Handles special conversions like active (boolean) -> accountStatus (string).
     *
     * @param path the SCIM path (to determine conversion rules)
     * @param value the SCIM value
     * @return the PingIDM value
     */
    private JsonNode convertValue(String path, JsonNode value) {
        if (value == null || value.isNull()) {
            return value;
        }

        // Handle special case: active attribute (boolean -> string conversion)
        if (path != null && path.contains("active") && value.isBoolean()) {
            boolean active = value.asBoolean();
            return objectMapper.getNodeFactory().textNode(active ? "active" : "inactive");
        }

        // For other attributes, pass through as-is
        return value;
    }
}