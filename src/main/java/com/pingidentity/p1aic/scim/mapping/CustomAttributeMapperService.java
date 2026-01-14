package com.pingidentity.p1aic.scim.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.config.CustomAttributeMapping;
import com.pingidentity.p1aic.scim.config.CustomAttributeMappingConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;

/**
 * Service for mapping custom attributes between SCIM and PingIDM formats.
 *
 * <p>This service handles three key integration points:</p>
 * <ul>
 *   <li><b>Schema Enhancement (4.a)</b> - Adds custom-mapped attributes to SCIM schema responses</li>
 *   <li><b>Outbound Mapping (4.b)</b> - Converts PingIDM responses to SCIM format</li>
 *   <li><b>Inbound Mapping (4.c)</b> - Converts SCIM requests to PingIDM format</li>
 * </ul>
 *
 * <p>The service uses configuration from {@link CustomAttributeMappingConfig} to determine
 * which PingIDM attributes map to which SCIM attributes.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * {@literal @}Inject
 * private CustomAttributeMapperService mapperService;
 *
 * // Enhance schema with custom attributes
 * ObjectNode enhancedSchema = mapperService.enhanceUserSchema(baseSchema);
 *
 * // Convert PingIDM user to SCIM format
 * ObjectNode scimUser = mapperService.applyOutboundMappings(scimUser, pingIdmUser);
 *
 * // Convert SCIM request to PingIDM format
 * ObjectNode pingIdmUser = mapperService.applyInboundMappings(pingIdmUser, scimRequest);
 * </pre>
 */
@Singleton
public class CustomAttributeMapperService {

    private static final Logger LOG = LoggerFactory.getLogger(CustomAttributeMapperService.class);

    private static final String ENTERPRISE_USER_SCHEMA = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";
    private static final String CORE_USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";

    private final CustomAttributeMappingConfig mappingConfig;
    private final ObjectMapper objectMapper;

    @Inject
    public CustomAttributeMapperService(CustomAttributeMappingConfig mappingConfig) {
        this.mappingConfig = mappingConfig;
        this.objectMapper = mappingConfig.getObjectMapper();
        LOG.info("CustomAttributeMapperService initialized with {} mappings",
                mappingConfig.getAllMappings().size());
    }

    /**
     * Constructor for testing without injection.
     */
    public CustomAttributeMapperService(CustomAttributeMappingConfig mappingConfig, ObjectMapper objectMapper) {
        this.mappingConfig = mappingConfig;
        this.objectMapper = objectMapper;
    }

    // ========================================================================
    // 4.a - SCHEMA ENHANCEMENT
    // ========================================================================

    /**
     * Enhance the User schema with custom-mapped attributes.
     *
     * <p>This method adds custom-mapped Core User attributes to the schema's
     * attribute list so that SCIM clients (like Microsoft Entra) see these
     * attributes as supported.</p>
     *
     * @param schemaNode the base User schema ObjectNode to enhance
     * @return the enhanced schema (same object, modified in place)
     */
    public ObjectNode enhanceUserSchema(ObjectNode schemaNode) {
        if (!mappingConfig.hasCustomMappings()) {
            LOG.debug("No custom mappings configured, schema unchanged");
            return schemaNode;
        }

        ArrayNode attributes = getOrCreateAttributesArray(schemaNode);
        List<CustomAttributeMapping> coreMappings = mappingConfig.getCoreUserMappings();

        LOG.debug("Enhancing User schema with {} core attribute mappings", coreMappings.size());

        for (CustomAttributeMapping mapping : coreMappings) {
            if (mapping.isNested()) {
                // For nested attributes like name.middleName, add to parent's subAttributes
                addNestedAttributeToSchema(attributes, mapping);
            } else {
                // For top-level attributes like title, add directly
                addTopLevelAttributeToSchema(attributes, mapping);
            }
        }

        return schemaNode;
    }

    /**
     * Enhance the Enterprise User extension schema with custom-mapped attributes.
     *
     * @param schemaNode the Enterprise User schema ObjectNode to enhance
     * @return the enhanced schema (same object, modified in place)
     */
    public ObjectNode enhanceEnterpriseUserSchema(ObjectNode schemaNode) {
        if (!mappingConfig.hasEnterpriseExtension()) {
            LOG.debug("No enterprise extension mappings configured, schema unchanged");
            return schemaNode;
        }

        ArrayNode attributes = getOrCreateAttributesArray(schemaNode);
        List<CustomAttributeMapping> enterpriseMappings = mappingConfig.getEnterpriseUserMappings();

        LOG.debug("Enhancing Enterprise User schema with {} attribute mappings", enterpriseMappings.size());

        for (CustomAttributeMapping mapping : enterpriseMappings) {
            addTopLevelAttributeToSchema(attributes, mapping);
        }

        return schemaNode;
    }

    /**
     * Add a top-level attribute definition to the schema's attributes array.
     */
    private void addTopLevelAttributeToSchema(ArrayNode attributes, CustomAttributeMapping mapping) {
        // Check if attribute already exists
        if (attributeExistsInSchema(attributes, mapping.getScimPath())) {
            LOG.debug("Attribute '{}' already exists in schema, skipping", mapping.getScimPath());
            return;
        }

        ObjectNode attrNode = createAttributeDefinition(mapping);
        attributes.add(attrNode);
        LOG.debug("Added top-level attribute '{}' to schema", mapping.getScimPath());
    }

    /**
     * Add a nested attribute (like name.middleName) to its parent in the schema.
     */
    private void addNestedAttributeToSchema(ArrayNode attributes, CustomAttributeMapping mapping) {
        String parentPath = mapping.getParentPath();
        String leafName = mapping.getLeafName();

        // Find the parent attribute
        ObjectNode parentAttr = findAttributeByName(attributes, parentPath);
        if (parentAttr == null) {
            LOG.warn("Parent attribute '{}' not found in schema for nested attribute '{}'",
                    parentPath, mapping.getScimPath());
            return;
        }

        // Get or create subAttributes array
        ArrayNode subAttributes = (ArrayNode) parentAttr.get("subAttributes");
        if (subAttributes == null) {
            subAttributes = objectMapper.createArrayNode();
            parentAttr.set("subAttributes", subAttributes);
        }

        // Check if sub-attribute already exists
        if (attributeExistsInSchema(subAttributes, leafName)) {
            LOG.debug("Sub-attribute '{}' already exists in '{}', skipping", leafName, parentPath);
            return;
        }

        // Create and add the sub-attribute definition
        ObjectNode subAttrNode = createAttributeDefinition(mapping);
        subAttrNode.put("name", leafName); // Use leaf name, not full path
        subAttributes.add(subAttrNode);
        LOG.debug("Added nested attribute '{}.{}' to schema", parentPath, leafName);
    }

    /**
     * Create a SCIM attribute definition from a mapping.
     */
    private ObjectNode createAttributeDefinition(CustomAttributeMapping mapping) {
        ObjectNode attrNode = objectMapper.createObjectNode();

        attrNode.put("name", mapping.isNested() ? mapping.getLeafName() : mapping.getScimPath());
        attrNode.put("type", mapping.getType());
        attrNode.put("multiValued", mapping.isMultiValued());
        attrNode.put("required", mapping.isRequired());
        attrNode.put("caseExact", mapping.isCaseExact());
        attrNode.put("mutability", mapping.getMutability());
        attrNode.put("returned", mapping.getReturned());
        attrNode.put("uniqueness", mapping.getUniqueness());

        if (mapping.getDescription() != null && !mapping.getDescription().isBlank()) {
            attrNode.put("description", mapping.getDescription());
        } else {
            attrNode.put("description", "Custom mapped attribute from PingIDM: " + mapping.getPingIdmAttribute());
        }

        // Add referenceTypes for reference type attributes
        if ("reference".equals(mapping.getType()) && mapping.getReferenceTypes() != null) {
            ArrayNode refTypes = objectMapper.createArrayNode();
            mapping.getReferenceTypes().forEach(refTypes::add);
            attrNode.set("referenceTypes", refTypes);
        }

        // Add canonicalValues if present
        if (mapping.getCanonicalValues() != null && !mapping.getCanonicalValues().isEmpty()) {
            ArrayNode canonicalValues = objectMapper.createArrayNode();
            mapping.getCanonicalValues().forEach(canonicalValues::add);
            attrNode.set("canonicalValues", canonicalValues);
        }

        return attrNode;
    }

    // ========================================================================
    // 4.b - OUTBOUND MAPPING (PingIDM -> SCIM)
    // ========================================================================

    /**
     * Apply custom attribute mappings when converting PingIDM response to SCIM format.
     *
     * <p>This method reads custom attributes from the PingIDM user object and adds
     * them to the appropriate locations in the SCIM user object.</p>
     *
     * @param scimUser the SCIM user ObjectNode to populate
     * @param pingIdmUser the source PingIDM user ObjectNode
     * @return the enhanced SCIM user (same object, modified in place)
     */
    public ObjectNode applyOutboundMappings(ObjectNode scimUser, ObjectNode pingIdmUser) {
        if (!mappingConfig.hasCustomMappings()) {
            return scimUser;
        }

        LOG.debug("Applying outbound custom attribute mappings");

        for (CustomAttributeMapping mapping : mappingConfig.getAllMappings()) {
            JsonNode pingIdmValue = pingIdmUser.get(mapping.getPingIdmAttribute());

            if (pingIdmValue == null || pingIdmValue.isNull() || pingIdmValue.isMissingNode()) {
                LOG.trace("PingIDM attribute '{}' is null/missing, skipping", mapping.getPingIdmAttribute());
                continue;
            }

            if (mapping.isEnterpriseExtension()) {
                // Add to Enterprise User extension object
                addToEnterpriseExtension(scimUser, mapping.getScimPath(), pingIdmValue);
            } else if (mapping.isNested()) {
                // Add to nested object (e.g., name.middleName)
                addToNestedObject(scimUser, mapping.getParentPath(), mapping.getLeafName(), pingIdmValue);
            } else {
                // Add as top-level attribute
                scimUser.set(mapping.getScimPath(), pingIdmValue);
                LOG.trace("Set SCIM attribute '{}' from PingIDM '{}'",
                        mapping.getScimPath(), mapping.getPingIdmAttribute());
            }
        }

        return scimUser;
    }

    /**
     * Add a value to the Enterprise User extension in the SCIM user.
     * Creates the extension object and updates schemas array if needed.
     */
    private void addToEnterpriseExtension(ObjectNode scimUser, String scimPath, JsonNode value) {
        // Get or create the enterprise extension object
        ObjectNode enterprise = (ObjectNode) scimUser.get(ENTERPRISE_USER_SCHEMA);
        if (enterprise == null) {
            enterprise = objectMapper.createObjectNode();
            scimUser.set(ENTERPRISE_USER_SCHEMA, enterprise);

            // Add to schemas array if not already present
            ensureSchemaInArray(scimUser, ENTERPRISE_USER_SCHEMA);
        }

        enterprise.set(scimPath, value);
        LOG.trace("Set Enterprise extension attribute '{}' from PingIDM", scimPath);
    }

    /**
     * Add a value to a nested object in the SCIM user.
     * Creates the parent object if it doesn't exist.
     */
    private void addToNestedObject(ObjectNode scimUser, String parentPath, String leafName, JsonNode value) {
        ObjectNode parent = (ObjectNode) scimUser.get(parentPath);
        if (parent == null) {
            parent = objectMapper.createObjectNode();
            scimUser.set(parentPath, parent);
        }
        parent.set(leafName, value);
        LOG.trace("Set nested attribute '{}.{}' from PingIDM", parentPath, leafName);
    }

    /**
     * Ensure a schema URN is in the SCIM user's schemas array.
     */
    private void ensureSchemaInArray(ObjectNode scimUser, String schemaUrn) {
        ArrayNode schemas = (ArrayNode) scimUser.get("schemas");
        if (schemas == null) {
            schemas = objectMapper.createArrayNode();
            scimUser.set("schemas", schemas);
        }

        // Check if already present
        for (JsonNode schema : schemas) {
            if (schemaUrn.equals(schema.asText())) {
                return;
            }
        }

        schemas.add(schemaUrn);
    }

    // ========================================================================
    // 4.c - INBOUND MAPPING (SCIM -> PingIDM)
    // ========================================================================

    /**
     * Apply custom attribute mappings when converting SCIM request to PingIDM format.
     *
     * <p>This method reads custom-mapped attributes from the SCIM request and adds
     * them to the PingIDM user object using the configured PingIDM attribute names.</p>
     *
     * @param pingIdmUser the PingIDM user ObjectNode to populate
     * @param scimUser the source SCIM user ObjectNode (from client request)
     * @return the enhanced PingIDM user (same object, modified in place)
     */
    public ObjectNode applyInboundMappings(ObjectNode pingIdmUser, ObjectNode scimUser) {
        if (!mappingConfig.hasCustomMappings()) {
            return pingIdmUser;
        }

        LOG.debug("Applying inbound custom attribute mappings");

        for (CustomAttributeMapping mapping : mappingConfig.getAllMappings()) {
            JsonNode scimValue = extractScimValue(scimUser, mapping);

            if (scimValue == null || scimValue.isNull() || scimValue.isMissingNode()) {
                LOG.trace("SCIM attribute '{}' is null/missing in request, skipping", mapping.getScimPath());
                continue;
            }

            pingIdmUser.set(mapping.getPingIdmAttribute(), scimValue);
            LOG.trace("Set PingIDM attribute '{}' from SCIM '{}'",
                    mapping.getPingIdmAttribute(), mapping.getScimPath());
        }

        return pingIdmUser;
    }

    /**
     * Apply custom attribute mappings for a PATCH operation.
     *
     * <p>This is similar to inbound mapping but only processes attributes
     * that are present in the patch request (doesn't set missing attrs to null).</p>
     *
     * @param pingIdmPatch the PingIDM patch ObjectNode to populate
     * @param scimPatchValue the SCIM attribute value from the patch operation
     * @param scimPath the SCIM attribute path being patched
     * @return true if the path was handled by custom mapping, false otherwise
     */
    public boolean applyInboundPatchMapping(ObjectNode pingIdmPatch, JsonNode scimPatchValue, String scimPath) {
        // Try to find a mapping for this SCIM path
        CustomAttributeMapping mapping = mappingConfig.getByScimPath(scimPath).orElse(null);

        // Also try with full path for enterprise attributes
        if (mapping == null) {
            mapping = mappingConfig.getByFullScimPath(scimPath).orElse(null);
        }

        // Try parsing complex paths like "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:department"
        if (mapping == null && scimPath.startsWith(ENTERPRISE_USER_SCHEMA + ":")) {
            String attrName = scimPath.substring(ENTERPRISE_USER_SCHEMA.length() + 1);
            mapping = mappingConfig.getByScimPath(attrName).orElse(null);
            if (mapping != null && !mapping.isEnterpriseExtension()) {
                mapping = null; // Must be an enterprise attribute
            }
        }

        if (mapping == null) {
            return false; // Not a custom-mapped attribute
        }

        pingIdmPatch.set(mapping.getPingIdmAttribute(), scimPatchValue);
        LOG.debug("Applied PATCH mapping: {} -> {}", scimPath, mapping.getPingIdmAttribute());
        return true;
    }

    /**
     * Extract a SCIM attribute value based on the mapping configuration.
     */
    private JsonNode extractScimValue(ObjectNode scimUser, CustomAttributeMapping mapping) {
        if (mapping.isEnterpriseExtension()) {
            // Look in the enterprise extension object
            JsonNode extension = scimUser.get(ENTERPRISE_USER_SCHEMA);
            if (extension != null && extension.isObject()) {
                return extension.get(mapping.getScimPath());
            }
            return null;
        } else if (mapping.isNested()) {
            // Look in the nested object
            JsonNode parent = scimUser.get(mapping.getParentPath());
            if (parent != null && parent.isObject()) {
                return parent.get(mapping.getLeafName());
            }
            return null;
        } else {
            // Top-level attribute
            return scimUser.get(mapping.getScimPath());
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Get or create the attributes array in a schema node.
     */
    private ArrayNode getOrCreateAttributesArray(ObjectNode schemaNode) {
        ArrayNode attributes = (ArrayNode) schemaNode.get("attributes");
        if (attributes == null) {
            attributes = objectMapper.createArrayNode();
            schemaNode.set("attributes", attributes);
        }
        return attributes;
    }

    /**
     * Check if an attribute with the given name exists in the attributes array.
     */
    private boolean attributeExistsInSchema(ArrayNode attributes, String name) {
        for (JsonNode attr : attributes) {
            if (attr.isObject()) {
                JsonNode nameNode = attr.get("name");
                if (nameNode != null && name.equals(nameNode.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Find an attribute by name in the attributes array.
     */
    private ObjectNode findAttributeByName(ArrayNode attributes, String name) {
        for (JsonNode attr : attributes) {
            if (attr.isObject()) {
                JsonNode nameNode = attr.get("name");
                if (nameNode != null && name.equals(nameNode.asText())) {
                    return (ObjectNode) attr;
                }
            }
        }
        return null;
    }

    /**
     * Get the set of PingIDM attribute names that should be requested from PingIDM.
     *
     * <p>This is useful for optimizing PingIDM queries by only requesting
     * the custom attributes that are mapped.</p>
     *
     * @return comma-separated string of PingIDM attribute names, or null if no mappings
     */
    public String getPingIdmFieldsParameter() {
        if (!mappingConfig.hasCustomMappings()) {
            return null;
        }
        return String.join(",", mappingConfig.getMappedPingIdmAttributes());
    }

    /**
     * Check if the mapping configuration has enterprise extension mappings.
     *
     * @return true if enterprise extension attributes are mapped
     */
    public boolean hasEnterpriseExtension() {
        return mappingConfig.hasEnterpriseExtension();
    }

    /**
     * Check if any custom mappings are configured.
     *
     * @return true if custom mappings exist
     */
    public boolean hasCustomMappings() {
        return mappingConfig.hasCustomMappings();
    }

    /**
     * Get the underlying mapping configuration.
     *
     * @return the CustomAttributeMappingConfig instance
     */
    public CustomAttributeMappingConfig getMappingConfig() {
        return mappingConfig;
    }
}