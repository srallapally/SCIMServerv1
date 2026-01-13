package com.pingidentity.p1aic.scim.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.exceptions.FilterTranslationException;
import com.pingidentity.p1aic.scim.mapping.AttributeMappingConfig;

import java.util.ArrayList;
import java.util.List;
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
 *
 * Special handling for Group members:
 * - SCIM members use simple value format: [{"value": "user-123"}]
 * - PingIDM expects relationship _ref format: {"_ref": "managed/alpha_user/user-123"}
 */
public class ScimPatchConverter {

    private static final Logger LOGGER = Logger.getLogger(ScimPatchConverter.class.getName());

    private final ObjectMapper objectMapper;
    private final Map<String, String> attributeMappings;
    private final String resourceType; // "User" or "Group"
    // BEGIN: Add managed user object name for building _ref paths
    private final String managedUserObjectName;
    // END: Add managed user object name for building _ref paths

    /**
     * Constructor for User resource PATCH conversion.
     *
     * @param resourceType the resource type ("User" or "Group")
     */
    public ScimPatchConverter(String resourceType) {
        // BEGIN: Call new constructor with default managed user object name from environment
        this(resourceType, System.getenv().getOrDefault("PINGIDM_MANAGED_USER_OBJECT", "alpha_user"));
        // END: Call new constructor with default managed user object name from environment
    }

    // BEGIN: Add new constructor that accepts managed user object name
    /**
     * Constructor for PATCH conversion with explicit managed object configuration.
     *
     * @param resourceType the resource type ("User" or "Group")
     * @param managedUserObjectName the PingIDM managed user object name (e.g., "alpha_user")
     */
    public ScimPatchConverter(String resourceType, String managedUserObjectName) {
        this.objectMapper = new ObjectMapper();
        this.resourceType = resourceType;
        this.managedUserObjectName = managedUserObjectName != null ? managedUserObjectName : "alpha_user";

        // Get appropriate attribute mappings based on resource type
        AttributeMappingConfig mappingConfig = AttributeMappingConfig.getInstance();
        if ("User".equalsIgnoreCase(resourceType)) {
            this.attributeMappings = mappingConfig.getUserScimToIdmMappings();
        } else if ("Group".equalsIgnoreCase(resourceType)) {
            this.attributeMappings = mappingConfig.getGroupScimToIdmMappings();
        } else {
            this.attributeMappings = Map.of();
        }

        LOGGER.info("ScimPatchConverter initialized for " + resourceType +
                " with managedUserObjectName=" + this.managedUserObjectName);
    }
    // END: Add new constructor that accepts managed user object name

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
                // BEGIN: Modified to handle multiple operations returned for members
                List<ObjectNode> convertedOps = convertOperation(operation);
                for (ObjectNode idmOp : convertedOps) {
                    if (idmOp != null) {
                        idmOperations.add(idmOp);
                    }
                }
                // END: Modified to handle multiple operations returned for members
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
     * May return multiple operations for array-based attributes like members.
     *
     * @param scimOp the SCIM operation
     * @return List of PingIDM operations (may be empty if operation should be skipped)
     * @throws FilterTranslationException if conversion fails
     */
    // BEGIN: Changed return type from ObjectNode to List<ObjectNode> to support members expansion
    private List<ObjectNode> convertOperation(JsonNode scimOp) throws FilterTranslationException {
        if (!scimOp.has("op")) {
            throw new FilterTranslationException("SCIM operation missing 'op' field");
        }

        String op = scimOp.get("op").asText().toLowerCase();
        String path = scimOp.has("path") ? scimOp.get("path").asText() : null;
        JsonNode value = scimOp.has("value") ? scimOp.get("value") : null;

        List<ObjectNode> results = new ArrayList<>();

        switch (op) {
            case "add":
                results.addAll(convertAddOperation(path, value));
                break;
            case "replace":
                results.addAll(convertReplaceOperation(path, value));
                break;
            case "remove":
                results.addAll(convertRemoveOperation(path, value));
                break;
            default:
                throw new FilterTranslationException("Unsupported SCIM PATCH operation: " + op);
        }

        return results;
    }
    // END: Changed return type from ObjectNode to List<ObjectNode> to support members expansion

    /**
     * Convert SCIM "add" operation to PingIDM format.
     * For members, creates separate operations for each member with _ref format.
     */
    // BEGIN: Changed return type and signature to support multiple operations for members
    private List<ObjectNode> convertAddOperation(String path, JsonNode value)
            throws FilterTranslationException {
        List<ObjectNode> operations = new ArrayList<>();

        if (value == null) {
            throw new FilterTranslationException("'add' operation requires a value");
        }

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
            return operations;
        }

        // BEGIN: Special handling for members attribute on Group resources
        if (isMembersPath(path)) {
            operations.addAll(convertMembersAddOperation(value));
            return operations;
        }
        // END: Special handling for members attribute on Group resources

        // Standard attribute handling
        ObjectNode idmOp = objectMapper.createObjectNode();
        idmOp.put("operation", "add");

        // Map SCIM path to PingIDM field
        String idmField = mapScimPathToIdmField(path);
        idmOp.put("field", "/" + idmField);

        // Convert value based on attribute type
        JsonNode idmValue = convertValue(path, value);
        idmOp.set("value", idmValue);

        operations.add(idmOp);
        return operations;
    }
    // END: Changed return type and signature to support multiple operations for members

    /**
     * Convert SCIM "replace" operation to PingIDM format.
     */
    // BEGIN: Changed return type and signature to support multiple operations for members
    private List<ObjectNode> convertReplaceOperation(String path, JsonNode value)
            throws FilterTranslationException {
        List<ObjectNode> operations = new ArrayList<>();

        if (path == null || path.isEmpty()) {
            throw new FilterTranslationException("'replace' operation requires a path");
        }
        if (value == null) {
            throw new FilterTranslationException("'replace' operation requires a value");
        }

        // BEGIN: Special handling for members attribute on Group resources
        if (isMembersPath(path)) {
            operations.addAll(convertMembersReplaceOperation(value));
            return operations;
        }
        // END: Special handling for members attribute on Group resources

        ObjectNode idmOp = objectMapper.createObjectNode();
        idmOp.put("operation", "replace");

        // Map SCIM path to PingIDM field
        String idmField = mapScimPathToIdmField(path);
        idmOp.put("field", "/" + idmField);

        // Convert value based on attribute type
        JsonNode idmValue = convertValue(path, value);
        idmOp.set("value", idmValue);

        operations.add(idmOp);
        return operations;
    }
    // END: Changed return type and signature to support multiple operations for members

    /**
     * Convert SCIM "remove" operation to PingIDM format.
     */
    // BEGIN: Changed return type and signature to support multiple operations for members
    private List<ObjectNode> convertRemoveOperation(String path, JsonNode value)
            throws FilterTranslationException {
        List<ObjectNode> operations = new ArrayList<>();

        if (path == null || path.isEmpty()) {
            throw new FilterTranslationException("'remove' operation requires a path");
        }

        // BEGIN: Special handling for members attribute on Group resources
        if (isMembersPath(path)) {
            operations.addAll(convertMembersRemoveOperation(path, value));
            return operations;
        }
        // END: Special handling for members attribute on Group resources

        ObjectNode idmOp = objectMapper.createObjectNode();
        idmOp.put("operation", "remove");

        // Map SCIM path to PingIDM field
        String idmField = mapScimPathToIdmField(path);
        idmOp.put("field", "/" + idmField);

        operations.add(idmOp);
        return operations;
    }
    // END: Changed return type and signature to support multiple operations for members

    // BEGIN: Add members-specific conversion methods
    /**
     * Check if the path refers to the members attribute.
     *
     * @param path the SCIM path
     * @return true if this is a members path
     */
    private boolean isMembersPath(String path) {
        if (path == null) {
            return false;
        }
        String normalizedPath = path.toLowerCase().trim();
        return "Group".equalsIgnoreCase(resourceType) &&
                (normalizedPath.equals("members") ||
                        normalizedPath.startsWith("members.") ||
                        normalizedPath.startsWith("members["));
    }

    /**
     * Convert SCIM members add operation to PingIDM format.
     * Creates individual operations for each member with _ref format.
     *
     * SCIM input: {"op": "add", "path": "members", "value": [{"value": "user-123"}]}
     * PingIDM output: [{"operation": "add", "field": "/members/-", "value": {"_ref": "managed/alpha_user/user-123"}}]
     *
     * @param value the SCIM members value (array of member objects)
     * @return list of PingIDM operations
     */
    private List<ObjectNode> convertMembersAddOperation(JsonNode value) throws FilterTranslationException {
        List<ObjectNode> operations = new ArrayList<>();

        if (value.isArray()) {
            // Multiple members: create one operation per member
            for (JsonNode member : value) {
                ObjectNode idmOp = createMemberAddOperation(member);
                if (idmOp != null) {
                    operations.add(idmOp);
                }
            }
        } else if (value.isObject()) {
            // Single member object
            ObjectNode idmOp = createMemberAddOperation(value);
            if (idmOp != null) {
                operations.add(idmOp);
            }
        } else {
            throw new FilterTranslationException("members value must be an array or object");
        }

        LOGGER.info("Converted " + operations.size() + " member add operations to PingIDM _ref format");
        return operations;
    }

    /**
     * Create a single PingIDM add operation for a member.
     *
     * @param member the SCIM member object (e.g., {"value": "user-123"})
     * @return PingIDM operation with _ref format
     */
    private ObjectNode createMemberAddOperation(JsonNode member) throws FilterTranslationException {
        String userId = extractMemberValue(member);
        if (userId == null || userId.isEmpty()) {
            LOGGER.warning("Skipping member with empty value");
            return null;
        }

        ObjectNode idmOp = objectMapper.createObjectNode();
        idmOp.put("operation", "add");
        // Use "/-" suffix for JSON Pointer array append
        idmOp.put("field", "/members/-");

        // Create _ref value object
        ObjectNode refValue = objectMapper.createObjectNode();
        refValue.put("_ref", buildMemberRef(userId));
        idmOp.set("value", refValue);

        LOGGER.fine("Created member add operation: " + idmOp.toString());
        return idmOp;
    }

    /**
     * Convert SCIM members replace operation to PingIDM format.
     * Replaces entire members array with new _ref formatted values.
     *
     * SCIM input: {"op": "replace", "path": "members", "value": [{"value": "user-123"}, {"value": "user-456"}]}
     * PingIDM output: [{"operation": "replace", "field": "/members", "value": [{"_ref": "managed/alpha_user/user-123"}, ...]}]
     *
     * @param value the SCIM members value
     * @return list containing single replace operation
     */
    private List<ObjectNode> convertMembersReplaceOperation(JsonNode value) throws FilterTranslationException {
        List<ObjectNode> operations = new ArrayList<>();

        ObjectNode idmOp = objectMapper.createObjectNode();
        idmOp.put("operation", "replace");
        idmOp.put("field", "/members");

        // Build array of _ref objects
        ArrayNode refArray = objectMapper.createArrayNode();

        if (value.isArray()) {
            for (JsonNode member : value) {
                String userId = extractMemberValue(member);
                if (userId != null && !userId.isEmpty()) {
                    ObjectNode refValue = objectMapper.createObjectNode();
                    refValue.put("_ref", buildMemberRef(userId));
                    refArray.add(refValue);
                }
            }
        } else if (value.isObject()) {
            String userId = extractMemberValue(value);
            if (userId != null && !userId.isEmpty()) {
                ObjectNode refValue = objectMapper.createObjectNode();
                refValue.put("_ref", buildMemberRef(userId));
                refArray.add(refValue);
            }
        } else {
            throw new FilterTranslationException("members value must be an array or object");
        }

        idmOp.set("value", refArray);
        operations.add(idmOp);

        LOGGER.info("Converted members replace operation with " + refArray.size() + " members to PingIDM _ref format");
        return operations;
    }

    /**
     * Convert SCIM members remove operation to PingIDM format.
     * Handles both:
     * - Remove specific members: {"op": "remove", "path": "members", "value": [{"value": "user-123"}]}
     * - Remove by filter: {"op": "remove", "path": "members[value eq \"user-123\"]"}
     *
     * @param path the SCIM path (may contain filter)
     * @param value the SCIM members value (optional)
     * @return list of PingIDM remove operations
     */
    private List<ObjectNode> convertMembersRemoveOperation(String path, JsonNode value) throws FilterTranslationException {
        List<ObjectNode> operations = new ArrayList<>();

        // Check if path contains a filter expression like members[value eq "user-123"]
        if (path.contains("[") && path.contains("]")) {
            // Extract user ID from filter expression
            String userId = extractUserIdFromFilter(path);
            if (userId != null && !userId.isEmpty()) {
                ObjectNode idmOp = createMemberRemoveOperation(userId);
                operations.add(idmOp);
            }
        } else if (value != null) {
            // Remove specific members provided in value
            if (value.isArray()) {
                for (JsonNode member : value) {
                    String userId = extractMemberValue(member);
                    if (userId != null && !userId.isEmpty()) {
                        ObjectNode idmOp = createMemberRemoveOperation(userId);
                        operations.add(idmOp);
                    }
                }
            } else if (value.isObject()) {
                String userId = extractMemberValue(value);
                if (userId != null && !userId.isEmpty()) {
                    ObjectNode idmOp = createMemberRemoveOperation(userId);
                    operations.add(idmOp);
                }
            }
        } else {
            // Remove all members (no filter, no value)
            ObjectNode idmOp = objectMapper.createObjectNode();
            idmOp.put("operation", "replace");
            idmOp.put("field", "/members");
            idmOp.set("value", objectMapper.createArrayNode()); // Empty array
            operations.add(idmOp);
            LOGGER.info("Converting remove all members to replace with empty array");
        }

        LOGGER.info("Converted " + operations.size() + " member remove operations to PingIDM format");
        return operations;
    }

    /**
     * Create a single PingIDM remove operation for a member.
     *
     * @param userId the user ID to remove
     * @return PingIDM remove operation
     */
    private ObjectNode createMemberRemoveOperation(String userId) {
        ObjectNode idmOp = objectMapper.createObjectNode();
        idmOp.put("operation", "remove");
        idmOp.put("field", "/members");

        // Create _ref value for the member to remove
        ObjectNode refValue = objectMapper.createObjectNode();
        refValue.put("_ref", buildMemberRef(userId));
        idmOp.set("value", refValue);

        return idmOp;
    }

    /**
     * Extract user ID from SCIM member object.
     * Handles formats: {"value": "user-123"} or {"value": "user-123", "display": "..."}
     *
     * @param member the SCIM member object
     * @return the user ID, or null if not found
     */
    private String extractMemberValue(JsonNode member) {
        if (member == null) {
            return null;
        }

        // Check for "value" field (standard SCIM format)
        if (member.has("value")) {
            return member.get("value").asText();
        }

        // If it's a text node directly, use it as-is
        if (member.isTextual()) {
            return member.asText();
        }

        LOGGER.warning("Could not extract member value from: " + member.toString());
        return null;
    }

    /**
     * Extract user ID from a SCIM filter expression.
     * E.g., members[value eq "user-123"] -> "user-123"
     *
     * @param path the SCIM path with filter
     * @return the extracted user ID, or null if not found
     */
    private String extractUserIdFromFilter(String path) {
        // Pattern: members[value eq "user-123"] or members[value eq 'user-123']
        int eqIndex = path.toLowerCase().indexOf(" eq ");
        if (eqIndex == -1) {
            LOGGER.warning("Could not find 'eq' in filter: " + path);
            return null;
        }

        String afterEq = path.substring(eqIndex + 4).trim();
        int closeBracket = afterEq.indexOf(']');
        if (closeBracket != -1) {
            afterEq = afterEq.substring(0, closeBracket);
        }

        // Remove quotes
        afterEq = afterEq.trim();
        if ((afterEq.startsWith("\"") && afterEq.endsWith("\"")) ||
                (afterEq.startsWith("'") && afterEq.endsWith("'"))) {
            afterEq = afterEq.substring(1, afterEq.length() - 1);
        }

        LOGGER.fine("Extracted user ID from filter: " + afterEq);
        return afterEq;
    }

    /**
     * Build PingIDM _ref path for a member.
     * Format: "managed/{managedUserObjectName}/{userId}"
     *
     * @param userId the user ID
     * @return the full _ref path
     */
    private String buildMemberRef(String userId) {
        return "managed/" + managedUserObjectName + "/" + userId;
    }
    // END: Add members-specific conversion methods

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