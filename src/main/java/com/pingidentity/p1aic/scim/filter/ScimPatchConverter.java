package com.pingidentity.p1aic.scim.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.exceptions.FilterTranslationException;
import com.pingidentity.p1aic.scim.mapping.AttributeMappingConfig;
import com.pingidentity.p1aic.scim.config.CustomAttributeMapping;
import com.pingidentity.p1aic.scim.config.CustomAttributeMappingConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public class ScimPatchConverter {

    private static final Logger LOGGER = Logger.getLogger(ScimPatchConverter.class.getName());
    private static final String ENTERPRISE_USER_SCHEMA = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

    private final ObjectMapper objectMapper;
    private final Map<String, String> attributeMappings;
    private final String resourceType;
    private final String managedUserObjectName;
    private final CustomAttributeMappingConfig customMappingConfig;

    public ScimPatchConverter(String resourceType) {
        this(resourceType, System.getenv().getOrDefault("PINGIDM_MANAGED_USER_OBJECT", "alpha_user"), null);
    }

    public ScimPatchConverter(String resourceType, String managedUserObjectName) {
        this(resourceType, managedUserObjectName, null);
    }

    public ScimPatchConverter(String resourceType, String managedUserObjectName,
                              CustomAttributeMappingConfig customMappingConfig) {
        this.objectMapper = new ObjectMapper();
        this.resourceType = resourceType;
        this.managedUserObjectName = managedUserObjectName != null ? managedUserObjectName : "alpha_user";
        this.customMappingConfig = customMappingConfig;

        AttributeMappingConfig mappingConfig = AttributeMappingConfig.getInstance();
        if ("User".equalsIgnoreCase(resourceType)) {
            this.attributeMappings = mappingConfig.getUserScimToIdmMappings();
        } else if ("Group".equalsIgnoreCase(resourceType)) {
            this.attributeMappings = mappingConfig.getGroupScimToIdmMappings();
        } else {
            this.attributeMappings = Map.of();
        }
    }

    public String convert(String scimPatchRequest) throws FilterTranslationException {
        if (scimPatchRequest == null || scimPatchRequest.trim().isEmpty()) {
            throw new FilterTranslationException("SCIM PATCH request is null or empty");
        }
        try {
            JsonNode scimPatch = objectMapper.readTree(scimPatchRequest);
            JsonNode operationsNode = scimPatch.has("Operations") ? scimPatch.get("Operations") : scimPatch.get("operations");
            if (operationsNode == null || !operationsNode.isArray()) {
                throw new FilterTranslationException("SCIM PATCH request missing 'Operations' array");
            }

            ArrayNode idmOperations = objectMapper.createArrayNode();
            for (JsonNode operation : operationsNode) {
                List<ObjectNode> convertedOps = convertOperation(operation);
                for (ObjectNode idmOp : convertedOps) {
                    if (idmOp != null) idmOperations.add(idmOp);
                }
            }
            return objectMapper.writeValueAsString(idmOperations);
        } catch (Exception e) {
            throw new FilterTranslationException("Failed to convert SCIM PATCH: " + e.getMessage(), e);
        }
    }

    private List<ObjectNode> convertOperation(JsonNode scimOp) throws FilterTranslationException {
        String op = scimOp.get("op").asText().toLowerCase();
        String path = scimOp.has("path") ? scimOp.get("path").asText() : null;
        JsonNode value = scimOp.has("value") ? scimOp.get("value") : null;

        if ("add".equals(op) && isManagerAttribute(path)) {
            op = "replace";
        }

        List<ObjectNode> results = new ArrayList<>();
        switch (op) {
            case "add": results.addAll(convertStandardOperation("add", path, value)); break;
            case "replace": results.addAll(convertStandardOperation("replace", path, value)); break;
            case "remove":
                ObjectNode idmOp = createIdmOperation("remove", path, null);
                if (idmOp != null) results.add(idmOp);
                break;
            default: throw new FilterTranslationException("Unsupported SCIM PATCH operation: " + op);
        }
        return results;
    }

    private List<ObjectNode> convertStandardOperation(String op, String path, JsonNode value) {
        List<ObjectNode> operations = new ArrayList<>();
        if (path == null || path.isEmpty()) {
            if (value != null && value.isObject()) {
                value.fields().forEachRemaining(entry -> {
                    ObjectNode idmOp = createIdmOperation(op, entry.getKey(), entry.getValue());
                    if (idmOp != null) operations.add(idmOp);
                });
            }
        } else {
            ObjectNode idmOp = createIdmOperation(op, path, value);
            if (idmOp != null) operations.add(idmOp);
        }
        return operations;
    }

    private ObjectNode createIdmOperation(String operation, String scimPath, JsonNode value) {
        String idmField = mapScimPathToIdmField(scimPath);

        if (idmField == null || (idmField.equals(scimPath) && scimPath.contains("["))) {
            LOGGER.warning("Skipping PATCH op for unmapped filter path: " + scimPath);
            return null;
        }

        // BEGIN FIX: Skip metadata updates on flattened simple attributes
        // If attempting to PATCH sub-attributes (e.g. primary, type) of a complex attribute
        // that maps to a simple IDM string (e.g. emails -> mail), skip the operation
        // to avoid validation errors (e.g. trying to set boolean 'true' on string 'mail').
        if (shouldSkipFlattenedAttributeUpdate(scimPath, idmField)) {
            LOGGER.info("Skipping PATCH operation on metadata for flattened attribute. Path: " + scimPath + ", Mapped To: " + idmField);
            return null;
        }
        // END FIX

        ObjectNode idmOp = objectMapper.createObjectNode();
        idmOp.put("operation", operation);
        idmOp.put("field", "/" + idmField);

        if (value != null && !"remove".equalsIgnoreCase(operation)) {
            if (isManagerAttribute(scimPath)) {
                idmOp.set("value", convertManagerValue(value));
            } else if (isSerializedAttribute(scimPath)) {
                // If it's a multivalued field, wrap the string in an array for 'replace'
                // or ensure correct format. For simplicity here, we stringify.
                // NOTE: 'add' to array in IDM usually requires array value for field or single for field/-
                // This logic is simplified; production might need more complex array handling.
                String serialized = value.toString();
                if (idmField.startsWith("frIndexedMultivalued")) {
                    ArrayNode arr = objectMapper.createArrayNode();
                    arr.add(serialized);
                    idmOp.set("value", arr);
                } else {
                    idmOp.put("value", serialized);
                }
            } else {
                idmOp.set("value", convertValue(scimPath, value));
            }
        }
        return idmOp;
    }

    // BEGIN FIX: Helper method to detect flattened metadata updates
    private boolean shouldSkipFlattenedAttributeUpdate(String scimPath, String idmField) {
        if (scimPath == null) return false;

        // Check if the target IDM attribute is a simple string that comes from a complex SCIM array
        boolean isFlattenedSimple = "mail".equals(idmField) || "telephoneNumber".equals(idmField);

        if (!isFlattenedSimple) return false;

        String lower = scimPath.toLowerCase();

        // Check if the path targets a sub-attribute (has dot or filter bracket)
        boolean isSubAttribute = lower.contains(".") || lower.contains("[");

        // Check if it is targeting the actual value
        boolean isValue = lower.endsWith(".value");

        // If it's a sub-attribute but NOT the value (e.g., .primary, .type), we should skip it
        return isSubAttribute && !isValue;
    }
    // END FIX

    private boolean isManagerAttribute(String path) {
        return path != null && (path.equalsIgnoreCase("manager") ||
                path.equalsIgnoreCase(ENTERPRISE_USER_SCHEMA + ":manager"));
    }

    private boolean isSerializedAttribute(String path) {
        if (customMappingConfig == null) return false;
        Optional<CustomAttributeMapping> m = customMappingConfig.getByScimPath(path);
        return m.isPresent() && m.get().isHandledByCode() && "complex".equals(m.get().getType());
    }

    private String getBaseAttribute(String path) {
        int bracketIndex = path.indexOf('[');
        return (bracketIndex > 0) ? path.substring(0, bracketIndex) : path;
    }

    private JsonNode convertManagerValue(JsonNode value) {
        String managerId = null;
        if (value.isTextual()) managerId = value.asText();
        else if (value.isObject() && value.has("value")) managerId = value.get("value").asText();

        if (managerId != null) {
            ObjectNode ref = objectMapper.createObjectNode();
            ref.put("_ref", "managed/" + managedUserObjectName + "/" + managerId);
            return ref;
        }
        return value;
    }

    private String mapScimPathToIdmField(String scimPath) {
        if (scimPath == null || scimPath.isEmpty()) return scimPath;
        String path = scimPath.startsWith("/") ? scimPath.substring(1) : scimPath;

        if (path.startsWith(ENTERPRISE_USER_SCHEMA + ":")) {
            String attrName = path.substring(ENTERPRISE_USER_SCHEMA.length() + 1);
            String customMapping = lookupCustomMapping(attrName, true);
            if (customMapping != null) return customMapping;
            path = attrName;
        }

        if (path.contains("[")) {
            String baseAttr = getBaseAttribute(path);
            if ("User".equalsIgnoreCase(resourceType)) {
                String customMapping = lookupCustomMapping(baseAttr, false);
                if (customMapping != null) return customMapping;
            }
            return attributeMappings.getOrDefault(baseAttr, baseAttr);
        }

        if ("User".equalsIgnoreCase(resourceType)) {
            String customMapping = lookupCustomMapping(path, false);
            if (customMapping != null) return customMapping;
        }

        return attributeMappings.getOrDefault(path, path);
    }

    private String lookupCustomMapping(String scimPath, boolean isEnterpriseAttr) {
        if (customMappingConfig == null) return null;
        Optional<CustomAttributeMapping> mapping = customMappingConfig.getByScimPath(scimPath);
        if (mapping.isPresent()) return mapping.get().getPingIdmAttribute();
        return null;
    }

    private JsonNode convertValue(String path, JsonNode value) {
        if (value == null || value.isNull()) return value;
        if (path != null && path.contains("active") && value.isBoolean()) {
            return objectMapper.getNodeFactory().textNode(value.asBoolean() ? "active" : "inactive");
        }
        return value;
    }
}