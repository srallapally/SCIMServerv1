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

import java.util.List;

@Singleton
public class CustomAttributeMapperService {

    private static final Logger LOG = LoggerFactory.getLogger(CustomAttributeMapperService.class);
    private static final String ENTERPRISE_USER_SCHEMA = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

    private final CustomAttributeMappingConfig mappingConfig;
    private final ObjectMapper objectMapper;

    @Inject
    public CustomAttributeMapperService(CustomAttributeMappingConfig mappingConfig) {
        this.mappingConfig = mappingConfig;
        this.objectMapper = mappingConfig.getObjectMapper();
    }

    // 4.a - SCHEMA ENHANCEMENT (Keep as is, schema should still advertise these attributes)
    public ObjectNode enhanceUserSchema(ObjectNode schemaNode) {
        if (!mappingConfig.hasCustomMappings()) return schemaNode;
        ArrayNode attributes = getOrCreateAttributesArray(schemaNode);
        for (CustomAttributeMapping mapping : mappingConfig.getCoreUserMappings()) {
            // Note: We DO want to advertise handledByCode attributes in the schema
            if (mapping.isNested()) addNestedAttributeToSchema(attributes, mapping);
            else addTopLevelAttributeToSchema(attributes, mapping);
        }
        return schemaNode;
    }

    public ObjectNode enhanceEnterpriseUserSchema(ObjectNode schemaNode) {
        if (!mappingConfig.hasEnterpriseExtension()) return schemaNode;
        ArrayNode attributes = getOrCreateAttributesArray(schemaNode);
        for (CustomAttributeMapping mapping : mappingConfig.getEnterpriseUserMappings()) {
            addTopLevelAttributeToSchema(attributes, mapping);
        }
        return schemaNode;
    }

    // 4.b - OUTBOUND MAPPING (Skip attributes handled explicitly by code)
    public ObjectNode applyOutboundMappings(ObjectNode scimUser, ObjectNode pingIdmUser) {
        if (!mappingConfig.hasCustomMappings()) return scimUser;

        for (CustomAttributeMapping mapping : mappingConfig.getAllMappings()) {
            // Skip if handled by specific mapper code (e.g. addresses, roles) to avoid overwriting with raw data
            if (mapping.isHandledByCode()) {
                continue;
            }

            JsonNode pingIdmValue = pingIdmUser.get(mapping.getPingIdmAttribute());
            if (pingIdmValue == null || pingIdmValue.isNull() || pingIdmValue.isMissingNode()) {
                continue;
            }

            if (mapping.isEnterpriseExtension()) {
                addToEnterpriseExtension(scimUser, mapping.getScimPath(), pingIdmValue);
            } else if (mapping.isNested()) {
                addToNestedObject(scimUser, mapping.getParentPath(), mapping.getLeafName(), pingIdmValue);
            } else {
                scimUser.set(mapping.getScimPath(), pingIdmValue);
            }
        }
        return scimUser;
    }

    // 4.c - INBOUND MAPPING (Skip attributes handled explicitly by code)
    public ObjectNode applyInboundMappings(ObjectNode pingIdmUser, ObjectNode scimUser) {
        if (!mappingConfig.hasCustomMappings()) return pingIdmUser;

        for (CustomAttributeMapping mapping : mappingConfig.getAllMappings()) {
            // Skip if handled by specific mapper code to avoid overwriting serialized strings with raw arrays
            if (mapping.isHandledByCode()) {
                continue;
            }

            JsonNode scimValue = extractScimValue(scimUser, mapping);
            if (scimValue == null || scimValue.isNull() || scimValue.isMissingNode()) {
                continue;
            }

            pingIdmUser.set(mapping.getPingIdmAttribute(), scimValue);
        }
        return pingIdmUser;
    }

    // Helpers
    private void addTopLevelAttributeToSchema(ArrayNode attributes, CustomAttributeMapping mapping) {
        if (attributeExistsInSchema(attributes, mapping.getScimPath())) return;
        attributes.add(createAttributeDefinition(mapping));
    }

    private void addNestedAttributeToSchema(ArrayNode attributes, CustomAttributeMapping mapping) {
        String parentPath = mapping.getParentPath();
        String leafName = mapping.getLeafName();
        ObjectNode parentAttr = findAttributeByName(attributes, parentPath);
        if (parentAttr == null) return;

        ArrayNode subAttributes = (ArrayNode) parentAttr.get("subAttributes");
        if (subAttributes == null) {
            subAttributes = objectMapper.createArrayNode();
            parentAttr.set("subAttributes", subAttributes);
        }
        if (attributeExistsInSchema(subAttributes, leafName)) return;

        ObjectNode subAttrNode = createAttributeDefinition(mapping);
        subAttrNode.put("name", leafName);
        subAttributes.add(subAttrNode);
    }

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
        if (mapping.getDescription() != null) attrNode.put("description", mapping.getDescription());
        if ("reference".equals(mapping.getType()) && mapping.getReferenceTypes() != null) {
            ArrayNode refTypes = objectMapper.createArrayNode();
            mapping.getReferenceTypes().forEach(refTypes::add);
            attrNode.set("referenceTypes", refTypes);
        }
        return attrNode;
    }

    private void addToEnterpriseExtension(ObjectNode scimUser, String scimPath, JsonNode value) {
        ObjectNode enterprise = (ObjectNode) scimUser.get(ENTERPRISE_USER_SCHEMA);
        if (enterprise == null) {
            enterprise = objectMapper.createObjectNode();
            scimUser.set(ENTERPRISE_USER_SCHEMA, enterprise);
            ensureSchemaInArray(scimUser, ENTERPRISE_USER_SCHEMA);
        }
        enterprise.set(scimPath, value);
    }

    private void addToNestedObject(ObjectNode scimUser, String parentPath, String leafName, JsonNode value) {
        ObjectNode parent = (ObjectNode) scimUser.get(parentPath);
        if (parent == null) {
            parent = objectMapper.createObjectNode();
            scimUser.set(parentPath, parent);
        }
        parent.set(leafName, value);
    }

    private void ensureSchemaInArray(ObjectNode scimUser, String schemaUrn) {
        ArrayNode schemas = (ArrayNode) scimUser.get("schemas");
        if (schemas == null) {
            schemas = objectMapper.createArrayNode();
            scimUser.set("schemas", schemas);
        }
        for (JsonNode schema : schemas) {
            if (schemaUrn.equals(schema.asText())) return;
        }
        schemas.add(schemaUrn);
    }

    private JsonNode extractScimValue(ObjectNode scimUser, CustomAttributeMapping mapping) {
        if (mapping.isEnterpriseExtension()) {
            JsonNode extension = scimUser.get(ENTERPRISE_USER_SCHEMA);
            return (extension != null) ? extension.get(mapping.getScimPath()) : null;
        } else if (mapping.isNested()) {
            JsonNode parent = scimUser.get(mapping.getParentPath());
            return (parent != null) ? parent.get(mapping.getLeafName()) : null;
        } else {
            return scimUser.get(mapping.getScimPath());
        }
    }

    private ArrayNode getOrCreateAttributesArray(ObjectNode schemaNode) {
        ArrayNode attributes = (ArrayNode) schemaNode.get("attributes");
        if (attributes == null) {
            attributes = objectMapper.createArrayNode();
            schemaNode.set("attributes", attributes);
        }
        return attributes;
    }

    private boolean attributeExistsInSchema(ArrayNode attributes, String name) {
        for (JsonNode attr : attributes) {
            if (attr.has("name") && name.equals(attr.get("name").asText())) return true;
        }
        return false;
    }

    private ObjectNode findAttributeByName(ArrayNode attributes, String name) {
        for (JsonNode attr : attributes) {
            if (attr.has("name") && name.equals(attr.get("name").asText())) return (ObjectNode) attr;
        }
        return null;
    }

    public String getPingIdmFieldsParameter() {
        if (!mappingConfig.hasCustomMappings()) return null;
        return String.join(",", mappingConfig.getMappedPingIdmAttributes());
    }

    public boolean hasEnterpriseExtension() {
        return mappingConfig.hasEnterpriseExtension();
    }

    public boolean hasCustomMappings() {
        return mappingConfig.hasCustomMappings();
    }

    public CustomAttributeMappingConfig getMappingConfig() {
        return mappingConfig;
    }
}