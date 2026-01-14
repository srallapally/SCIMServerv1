package com.pingidentity.p1aic.scim.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.exceptions.FilterTranslationException;
import com.pingidentity.p1aic.scim.mapping.AttributeMappingConfig;
// BEGIN: Import CustomAttributeMappingConfig for custom attribute handling
import com.pingidentity.p1aic.scim.config.CustomAttributeMapping;
import com.pingidentity.p1aic.scim.config.CustomAttributeMappingConfig;
// END: Import CustomAttributeMappingConfig

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Converter for SCIM PATCH operations to PingIDM PATCH format.
 *
 * <p>SCIM PATCH uses RFC 7644 Section 3.5.2 format with operations like:</p>
 * <pre>
 * {
 *   "schemas": ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
 *   "Operations": [
 *     {"op": "add", "path": "emails", "value": {...}},
 *     {"op": "replace", "path": "active", "value": true},
 *     {"op": "remove", "path": "phoneNumbers[type eq \"fax\"]"}
 *   ]
 * }
 * </pre>
 *
 * <p>PingIDM PATCH uses RFC 6902 JSON Patch format:</p>
 * <pre>
 * [
 *   {"operation": "add", "field": "/mail", "value": "..."},
 *   {"operation": "replace", "field": "/accountStatus", "value": "active"},
 *   {"operation": "remove", "field": "/telephoneNumber"}
 * ]
 * </pre>
 *
 * <p>MODIFIED: Now integrates with {@link CustomAttributeMappingConfig} to handle
 * custom attribute mappings for attributes that PingIDM doesn't support OOTB
 * (e.g., Enterprise User extension attributes like employeeNumber, department).</p>
 *
 * <p>Special handling for Group members:</p>
 * <ul>
 *   <li>SCIM members use simple value format: [{"value": "user-123"}]</li>
 *   <li>PingIDM expects relationship _ref format: {"_ref": "managed/alpha_user/user-123"}</li>
 * </ul>
 */
public class ScimPatchConverter {

    private static final Logger LOGGER = Logger.getLogger(ScimPatchConverter.class.getName());

    // BEGIN: Enterprise User schema URN constant
    private static final String ENTERPRISE_USER_SCHEMA = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";
    // END: Enterprise User schema URN constant

    private final ObjectMapper objectMapper;
    private final Map<String, String> attributeMappings;
    private final String resourceType; // "User" or "Group"
    private final String managedUserObjectName;

    // BEGIN: Add CustomAttributeMappingConfig for custom attribute handling
    private final CustomAttributeMappingConfig customMappingConfig;
    // END: Add CustomAttributeMappingConfig

    /**
     * Constructor for User resource PATCH conversion.
     *
     * @param resourceType the resource type ("User" or "Group")
     */
    public ScimPatchConverter(String resourceType) {
        this(resourceType, System.getenv().getOrDefault("PINGIDM_MANAGED_USER_OBJECT", "alpha_user"), null);
    }

    /**
     * Constructor for PATCH conversion with explicit managed object configuration.
     *
     * @param resourceType the resource type ("User" or "Group")
     * @param managedUserObjectName the PingIDM managed user object name (e.g., "alpha_user")
     */
    public ScimPatchConverter(String resourceType, String managedUserObjectName) {
        this(resourceType, managedUserObjectName, null);
    }

    // BEGIN: Add new constructor that accepts CustomAttributeMappingConfig
    /**
     * Constructor for PATCH conversion with custom attribute mapping support.
     *
     * @param resourceType the resource type ("User" or "Group")
     * @param managedUserObjectName the PingIDM managed user object name (e.g., "alpha_user")
     * @param customMappingConfig the custom attribute mapping configuration (may be null)
     */
    public ScimPatchConverter(String resourceType, String managedUserObjectName,
                              CustomAttributeMappingConfig customMappingConfig) {
        this.objectMapper = new ObjectMapper();
        this.resourceType = resourceType;
        this.managedUserObjectName = managedUserObjectName != null ? managedUserObjectName : "alpha_user";
        this.customMappingConfig = customMappingConfig;

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
                " with managedUserObjectName=" + this.managedUserObjectName +
                ", customMappings=" + (customMappingConfig != null && customMappingConfig.hasCustomMappings()));
    }
    // END: Add new constructor that accepts CustomAttributeMappingConfig

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
                List<ObjectNode> convertedOps = convertOperation(operation);
                for (ObjectNode idmOp : convertedOps) {
                    if (idmOp != null) {
                        idmOperations.add(idmOp);
                    }
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
     * May return multiple operations for array-based attributes like members.
     *
     * @param scimOp the SCIM operation
     * @return List of PingIDM operations (may be empty if operation should be skipped)
     * @throws FilterTranslationException if conversion fails
     */
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

    /**
     * Convert SCIM "add" operation to PingIDM format.
     * For members, creates separate operations for each member with _ref format.
     */
    private List<ObjectNode> convertAddOperation(String path, JsonNode value)
            throws FilterTranslationException {
        List<ObjectNode> operations = new ArrayList<>();

        if (value == null) {
            throw new FilterTranslationException("'add' operation requires a value");
        }

        // If path is null, value is an object with attribute names as keys
        if (path == null || path.isEmpty()) {
            if (value.isObject()) {
                // Iterate through each attribute in the value
                value.fields().forEachRemaining(entry -> {
                    String attrPath = entry.getKey();
                    JsonNode attrValue = entry.getValue();

                    // Check if this is members (Group)
                    if ("members".equalsIgnoreCase(attrPath) && "Group".equalsIgnoreCase(resourceType)) {
                        try {
                            operations.addAll(convertMembersAddOperation(attrValue));
                        } catch (FilterTranslationException e) {
                            LOGGER.severe("Failed to convert members add: " + e.getMessage());
                        }
                    } else {
                        ObjectNode idmOp = createIdmOperation("add", attrPath, attrValue);
                        if (idmOp != null) {
                            operations.add(idmOp);
                        }
                    }
                });
            }
        } else {
            // Path is specified - handle members specially for Groups
            if ("members".equalsIgnoreCase(path) && "Group".equalsIgnoreCase(resourceType)) {
                operations.addAll(convertMembersAddOperation(value));
            } else {
                // Standard attribute add
                ObjectNode idmOp = createIdmOperation("add", path, value);
                if (idmOp != null) {
                    operations.add(idmOp);
                }
            }
        }

        return operations;
    }

    /**
     * Convert SCIM "replace" operation to PingIDM format.
     */
    private List<ObjectNode> convertReplaceOperation(String path, JsonNode value)
            throws FilterTranslationException {
        List<ObjectNode> operations = new ArrayList<>();

        // If path is null, value is an object with attribute names as keys
        if (path == null || path.isEmpty()) {
            if (value == null || !value.isObject()) {
                throw new FilterTranslationException("'replace' operation without path requires object value");
            }

            // Iterate through each attribute in the value
            value.fields().forEachRemaining(entry -> {
                String attrPath = entry.getKey();
                JsonNode attrValue = entry.getValue();

                // Check if this is members (Group)
                if ("members".equalsIgnoreCase(attrPath) && "Group".equalsIgnoreCase(resourceType)) {
                    try {
                        operations.addAll(convertMembersReplaceOperation(attrValue));
                    } catch (FilterTranslationException e) {
                        LOGGER.severe("Failed to convert members replace: " + e.getMessage());
                    }
                } else {
                    ObjectNode idmOp = createIdmOperation("replace", attrPath, attrValue);
                    if (idmOp != null) {
                        operations.add(idmOp);
                    }
                }
            });
        } else {
            // Path is specified
            if ("members".equalsIgnoreCase(path) && "Group".equalsIgnoreCase(resourceType)) {
                operations.addAll(convertMembersReplaceOperation(value));
            } else {
                ObjectNode idmOp = createIdmOperation("replace", path, value);
                if (idmOp != null) {
                    operations.add(idmOp);
                }
            }
        }

        return operations;
    }

    /**
     * Convert SCIM "remove" operation to PingIDM format.
     */
    private List<ObjectNode> convertRemoveOperation(String path, JsonNode value)
            throws FilterTranslationException {
        List<ObjectNode> operations = new ArrayList<>();

        if (path == null || path.isEmpty()) {
            throw new FilterTranslationException("'remove' operation requires a path");
        }

        // Check if this is a members remove operation for Groups
        if (path.toLowerCase().startsWith("members") && "Group".equalsIgnoreCase(resourceType)) {
            operations.addAll(convertMembersRemoveOperation(path, value));
        } else {
            // Standard attribute remove
            ObjectNode idmOp = createIdmOperation("remove", path, null);
            if (idmOp != null) {
                operations.add(idmOp);
            }
        }

        return operations;
    }

    /**
     * Create a single PingIDM operation.
     *
     * @param operation the operation type (add, replace, remove)
     * @param scimPath the SCIM path
     * @param value the value (may be null for remove)
     * @return PingIDM operation node
     */
    private ObjectNode createIdmOperation(String operation, String scimPath, JsonNode value) {
        String idmField = mapScimPathToIdmField(scimPath);

        if (idmField == null || idmField.isEmpty()) {
            LOGGER.warning("Could not map SCIM path to PingIDM field: " + scimPath);
            return null;
        }

        ObjectNode idmOp = objectMapper.createObjectNode();
        idmOp.put("operation", operation);
        idmOp.put("field", "/" + idmField);

        if (value != null && !"remove".equalsIgnoreCase(operation)) {
            // Convert value if needed
            JsonNode convertedValue = convertValue(scimPath, value);
            idmOp.set("value", convertedValue);
        }

        LOGGER.fine("Created PingIDM operation: " + operation + " " + idmField);
        return idmOp;
    }

    /**
     * Convert SCIM members add operation to PingIDM format.
     *
     * @param membersValue the SCIM members value
     * @return list of PingIDM add operations
     */
    private List<ObjectNode> convertMembersAddOperation(JsonNode membersValue) throws FilterTranslationException {
        List<ObjectNode> operations = new ArrayList<>();

        if (membersValue == null) {
            return operations;
        }

        // Handle array of members
        if (membersValue.isArray()) {
            for (JsonNode member : membersValue) {
                String userId = extractMemberValue(member);
                if (userId != null && !userId.isEmpty()) {
                    ObjectNode idmOp = createMemberAddOperation(userId);
                    operations.add(idmOp);
                }
            }
        } else if (membersValue.isObject()) {
            // Single member object
            String userId = extractMemberValue(membersValue);
            if (userId != null && !userId.isEmpty()) {
                ObjectNode idmOp = createMemberAddOperation(userId);
                operations.add(idmOp);
            }
        }

        LOGGER.info("Converted " + operations.size() + " member add operations to PingIDM format");
        return operations;
    }

    /**
     * Create a single PingIDM add operation for a member.
     *
     * @param userId the user ID to add
     * @return PingIDM add operation
     */
    private ObjectNode createMemberAddOperation(String userId) {
        ObjectNode idmOp = objectMapper.createObjectNode();
        idmOp.put("operation", "add");
        idmOp.put("field", "/members/-"); // Append to array

        // Create _ref value
        ObjectNode refValue = objectMapper.createObjectNode();
        refValue.put("_ref", buildMemberRef(userId));
        idmOp.set("value", refValue);

        return idmOp;
    }

    /**
     * Convert SCIM members replace operation to PingIDM format.
     *
     * @param membersValue the new members array
     * @return list of PingIDM operations (single replace with all members)
     */
    private List<ObjectNode> convertMembersReplaceOperation(JsonNode membersValue) throws FilterTranslationException {
        List<ObjectNode> operations = new ArrayList<>();

        ObjectNode idmOp = objectMapper.createObjectNode();
        idmOp.put("operation", "replace");
        idmOp.put("field", "/members");

        // Convert all members to _ref format
        ArrayNode idmMembers = objectMapper.createArrayNode();

        if (membersValue != null && membersValue.isArray()) {
            for (JsonNode member : membersValue) {
                String userId = extractMemberValue(member);
                if (userId != null && !userId.isEmpty()) {
                    ObjectNode refValue = objectMapper.createObjectNode();
                    refValue.put("_ref", buildMemberRef(userId));
                    idmMembers.add(refValue);
                }
            }
        }

        idmOp.set("value", idmMembers);
        operations.add(idmOp);

        LOGGER.info("Converted members replace operation with " + idmMembers.size() + " members");
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

    /**
     * Map SCIM path to PingIDM field name.
     * Handles both simple paths (e.g., "active") and complex paths (e.g., "name.givenName").
     * Also handles Enterprise User extension paths (e.g., "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:department").
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

        // BEGIN: Handle Enterprise User extension paths
        // E.g., "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:employeeNumber"
        if (path.startsWith(ENTERPRISE_USER_SCHEMA + ":")) {
            String attrName = path.substring(ENTERPRISE_USER_SCHEMA.length() + 1);
            String customMapping = lookupCustomMapping(attrName, true);
            if (customMapping != null) {
                LOGGER.info("Mapped Enterprise extension path '" + path + "' to PingIDM field '" + customMapping + "'");
                return customMapping;
            }
            // Fall through to standard mapping if no custom mapping found
            path = attrName;
        }
        // END: Handle Enterprise User extension paths

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

        // BEGIN: Check custom mappings first for User resource
        if ("User".equalsIgnoreCase(resourceType)) {
            String customMapping = lookupCustomMapping(path, false);
            if (customMapping != null) {
                LOGGER.info("Mapped custom attribute '" + path + "' to PingIDM field '" + customMapping + "'");
                return customMapping;
            }

            // Handle nested paths like name.middleName
            if (path.contains(".")) {
                customMapping = lookupCustomMapping(path, false);
                if (customMapping != null) {
                    LOGGER.info("Mapped nested custom attribute '" + path + "' to PingIDM field '" + customMapping + "'");
                    return customMapping;
                }
            }
        }
        // END: Check custom mappings

        // Use standard attribute mapping, or pass through if not found
        return attributeMappings.getOrDefault(path, path);
    }

    // BEGIN: Add helper method for custom mapping lookup
    /**
     * Look up a custom mapping for a SCIM attribute path.
     *
     * @param scimPath the SCIM attribute path (e.g., "title", "name.middleName", "employeeNumber")
     * @param isEnterpriseAttr true if this is an enterprise extension attribute
     * @return the PingIDM attribute name, or null if no custom mapping found
     */
    private String lookupCustomMapping(String scimPath, boolean isEnterpriseAttr) {
        if (customMappingConfig == null || !customMappingConfig.hasCustomMappings()) {
            return null;
        }

        Optional<CustomAttributeMapping> mapping = customMappingConfig.getByScimPath(scimPath);

        if (mapping.isPresent()) {
            CustomAttributeMapping m = mapping.get();
            // Verify it's the right type (enterprise vs core)
            if (isEnterpriseAttr && m.isEnterpriseExtension()) {
                return m.getPingIdmAttribute();
            } else if (!isEnterpriseAttr && !m.isEnterpriseExtension()) {
                return m.getPingIdmAttribute();
            }
        }

        return null;
    }
    // END: Add helper method for custom mapping lookup

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