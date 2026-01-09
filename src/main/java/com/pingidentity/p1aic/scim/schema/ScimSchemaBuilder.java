package com.pingidentity.p1aic.scim.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unboundid.scim2.common.GenericScimResource;
import com.unboundid.scim2.common.types.AttributeDefinition;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Builder for SCIM schema definitions.
 *
 * Converts PingIDM managed object property definitions into SCIM 2.0 schema format.
 */
public class ScimSchemaBuilder {

    private static final Logger LOGGER = Logger.getLogger(ScimSchemaBuilder.class.getName());

    private final ObjectMapper objectMapper;

    /**
     * Constructor initializes the ObjectMapper.
     */
    public ScimSchemaBuilder() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Build SCIM User schema from PingIDM user properties.
     *
     * @param idmProperties the PingIDM user properties definition
     * @return GenericScimResource representing the User schema
     */
    public GenericScimResource buildUserSchema(ObjectNode idmProperties) {
        LOGGER.info("Building User schema");

        ObjectNode schemaNode = objectMapper.createObjectNode();

        // Set schema metadata
        schemaNode.put("id", ScimSchemaUrns.CORE_USER_SCHEMA);
        schemaNode.put("name", "User");
        schemaNode.put("description", "User Account");

        // Add schemas array
        ArrayNode schemas = objectMapper.createArrayNode();
        schemas.add(ScimSchemaUrns.SCHEMA);
        schemaNode.set("schemas", schemas);

        // Build attributes array
        ArrayNode attributes = objectMapper.createArrayNode();

        // Add core SCIM User attributes
        addCoreUserAttributes(attributes);

        // Add custom attributes from PingIDM configuration
        addCustomAttributes(attributes, idmProperties);

        schemaNode.set("attributes", attributes);

        return new GenericScimResource(schemaNode);
    }

    /**
     * Build SCIM Group schema from PingIDM role properties.
     *
     * @param idmProperties the PingIDM role properties definition
     * @return GenericScimResource representing the Group schema
     */
    public GenericScimResource buildGroupSchema(ObjectNode idmProperties) {
        LOGGER.info("Building Group schema");

        ObjectNode schemaNode = objectMapper.createObjectNode();

        // Set schema metadata
        schemaNode.put("id", ScimSchemaUrns.CORE_GROUP_SCHEMA);
        schemaNode.put("name", "Group");
        schemaNode.put("description", "Group");

        // Add schemas array
        ArrayNode schemas = objectMapper.createArrayNode();
        schemas.add(ScimSchemaUrns.SCHEMA);
        schemaNode.set("schemas", schemas);

        // Build attributes array
        ArrayNode attributes = objectMapper.createArrayNode();

        // Add core SCIM Group attributes
        addCoreGroupAttributes(attributes);

        // Add custom attributes from PingIDM configuration
        addCustomAttributes(attributes, idmProperties);

        schemaNode.set("attributes", attributes);

        return new GenericScimResource(schemaNode);
    }

    /**
     * Add core SCIM User attributes to the schema.
     */
    private void addCoreUserAttributes(ArrayNode attributes) {
        // id - unique identifier
        attributes.add(createAttribute("id", "string", false, true, true, false, "none",
                "Unique identifier for the User"));

        // userName - required
        attributes.add(createAttribute("userName", "string", false, true, true, true, "none",
                "Unique identifier for the User, typically used by the user to directly authenticate"));

        // name - complex attribute
        ObjectNode nameAttr = createAttribute("name", "complex", false, false, false, false, "none",
                "The components of the user's real name");
        ArrayNode nameSubAttrs = objectMapper.createArrayNode();
        nameSubAttrs.add(createAttribute("formatted", "string", false, false, false, false, "none",
                "The full name, including all middle names, titles, and suffixes as appropriate"));
        nameSubAttrs.add(createAttribute("familyName", "string", false, false, false, false, "none",
                "The family name of the User, or last name"));
        nameSubAttrs.add(createAttribute("givenName", "string", false, false, false, false, "none",
                "The given name of the User, or first name"));
        nameSubAttrs.add(createAttribute("middleName", "string", false, false, false, false, "none",
                "The middle name(s) of the User"));
        nameSubAttrs.add(createAttribute("honorificPrefix", "string", false, false, false, false, "none",
                "The honorific prefix(es) of the User, or title"));
        nameSubAttrs.add(createAttribute("honorificSuffix", "string", false, false, false, false, "none",
                "The honorific suffix(es) of the User"));
        nameAttr.set("subAttributes", nameSubAttrs);
        attributes.add(nameAttr);

        // displayName
        attributes.add(createAttribute("displayName", "string", false, false, false, false, "none",
                "The name of the User, suitable for display to end-users"));

        // emails - multi-valued
        ObjectNode emailsAttr = createAttribute("emails", "complex", true, false, false, false, "none",
                "Email addresses for the user");
        ArrayNode emailSubAttrs = objectMapper.createArrayNode();
        emailSubAttrs.add(createAttribute("value", "string", false, false, false, false, "none",
                "Email addresses for the user"));
        emailSubAttrs.add(createAttribute("display", "string", false, false, false, false, "none",
                "A human-readable name for the email address"));
        emailSubAttrs.add(createAttribute("type", "string", false, false, false, false, "none",
                "A label indicating the type of email address"));
        emailSubAttrs.add(createAttribute("primary", "boolean", false, false, false, false, "none",
                "Indicates if this is the primary email address"));
        emailsAttr.set("subAttributes", emailSubAttrs);
        attributes.add(emailsAttr);

        // phoneNumbers - multi-valued
        ObjectNode phonesAttr = createAttribute("phoneNumbers", "complex", true, false, false, false, "none",
                "Phone numbers for the User");
        ArrayNode phoneSubAttrs = objectMapper.createArrayNode();
        phoneSubAttrs.add(createAttribute("value", "string", false, false, false, false, "none",
                "Phone number of the User"));
        phoneSubAttrs.add(createAttribute("display", "string", false, false, false, false, "none",
                "A human-readable name for the phone number"));
        phoneSubAttrs.add(createAttribute("type", "string", false, false, false, false, "none",
                "A label indicating the type of phone number"));
        phoneSubAttrs.add(createAttribute("primary", "boolean", false, false, false, false, "none",
                "Indicates if this is the primary phone number"));
        phonesAttr.set("subAttributes", phoneSubAttrs);
        attributes.add(phonesAttr);

        // active
        attributes.add(createAttribute("active", "boolean", false, false, false, false, "none",
                "A Boolean value indicating the User's administrative status"));

        // password
        attributes.add(createAttribute("password", "string", false, false, false, false, "writeOnly",
                "The User's cleartext password"));

        // Additional standard attributes
        attributes.add(createAttribute("title", "string", false, false, false, false, "none",
                "The user's title"));
        attributes.add(createAttribute("preferredLanguage", "string", false, false, false, false, "none",
                "User's preferred written or spoken language"));
        attributes.add(createAttribute("locale", "string", false, false, false, false, "none",
                "Used to indicate the User's default location"));
        attributes.add(createAttribute("timezone", "string", false, false, false, false, "none",
                "The User's time zone"));
        attributes.add(createAttribute("profileUrl", "reference", false, false, false, false, "none",
                "A fully qualified URL pointing to a page representing the User's online profile"));

        // meta - complex attribute (read-only)
        ObjectNode metaAttr = createAttribute("meta", "complex", false, false, true, false, "none",
                "A complex attribute containing resource metadata");
        ArrayNode metaSubAttrs = objectMapper.createArrayNode();
        metaSubAttrs.add(createAttribute("resourceType", "string", false, false, true, false, "none",
                "The name of the resource type of the resource"));
        metaSubAttrs.add(createAttribute("created", "dateTime", false, false, true, false, "none",
                "The DateTime the Resource was added to the service provider"));
        metaSubAttrs.add(createAttribute("lastModified", "dateTime", false, false, true, false, "none",
                "The most recent DateTime the details of this Resource were updated"));
        metaSubAttrs.add(createAttribute("location", "string", false, false, true, false, "none",
                "The URI of the Resource being returned"));
        metaSubAttrs.add(createAttribute("version", "string", false, false, true, false, "none",
                "The version of the Resource being returned"));
        metaAttr.set("subAttributes", metaSubAttrs);
        attributes.add(metaAttr);
    }

    /**
     * Add core SCIM Group attributes to the schema.
     */
    private void addCoreGroupAttributes(ArrayNode attributes) {
        // id - unique identifier
        attributes.add(createAttribute("id", "string", false, true, true, false, "none",
                "Unique identifier for the Group"));

        // displayName - required
        attributes.add(createAttribute("displayName", "string", false, true, true, true, "none",
                "A human-readable name for the Group"));

        // members - multi-valued
        ObjectNode membersAttr = createAttribute("members", "complex", true, false, false, false, "none",
                "A list of members of the Group");
        ArrayNode memberSubAttrs = objectMapper.createArrayNode();
        memberSubAttrs.add(createAttribute("value", "string", false, false, false, false, "none",
                "Identifier of the member of this Group"));
        memberSubAttrs.add(createAttribute("$ref", "reference", false, false, false, false, "none",
                "The URI of the corresponding resource"));
        memberSubAttrs.add(createAttribute("type", "string", false, false, false, false, "none",
                "A label indicating the type of resource"));
        membersAttr.set("subAttributes", memberSubAttrs);
        attributes.add(membersAttr);

        // description
        attributes.add(createAttribute("description", "string", false, false, false, false, "none",
                "A human-readable description of the Group"));

        // meta - complex attribute (read-only)
        ObjectNode metaAttr = createAttribute("meta", "complex", false, false, true, false, "none",
                "A complex attribute containing resource metadata");
        ArrayNode metaSubAttrs = objectMapper.createArrayNode();
        metaSubAttrs.add(createAttribute("resourceType", "string", false, false, true, false, "none",
                "The name of the resource type of the resource"));
        metaSubAttrs.add(createAttribute("created", "dateTime", false, false, true, false, "none",
                "The DateTime the Resource was added to the service provider"));
        metaSubAttrs.add(createAttribute("lastModified", "dateTime", false, false, true, false, "none",
                "The most recent DateTime the details of this Resource were updated"));
        metaSubAttrs.add(createAttribute("location", "string", false, false, true, false, "none",
                "The URI of the Resource being returned"));
        metaSubAttrs.add(createAttribute("version", "string", false, false, true, false, "none",
                "The version of the Resource being returned"));
        metaAttr.set("subAttributes", metaSubAttrs);
        attributes.add(metaAttr);
    }

    /**
     * Add custom attributes from PingIDM properties to the schema.
     */
    private void addCustomAttributes(ArrayNode attributes, ObjectNode idmProperties) {
        if (idmProperties == null) {
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = idmProperties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String attrName = field.getKey();
            JsonNode attrDef = field.getValue();

            // Skip internal attributes and already-defined core attributes
            if (isInternalAttribute(attrName) || isCoreAttribute(attrName)) {
                continue;
            }

            // Only process if attribute definition is an object
            if (!attrDef.isObject()) {
                continue;
            }

            ObjectNode attrDefNode = (ObjectNode) attrDef;

            // Create SCIM attribute definition
            ObjectNode scimAttr = createAttributeFromIdmDefinition(attrName, attrDefNode);
            if (scimAttr != null) {
                attributes.add(scimAttr);
            }
        }
    }

    /**
     * Create a SCIM attribute definition from PingIDM property definition.
     */
    private ObjectNode createAttributeFromIdmDefinition(String attrName, ObjectNode idmDef) {
        // Extract type
        String idmType = idmDef.has("type") ? idmDef.get("type").asText() : "string";
        String scimType = mapIdmTypeToScimType(idmType);

        // Check if multi-valued (array type in PingIDM)
        boolean multiValued = "array".equalsIgnoreCase(idmType);

        // Extract description
        String description = null;
        if (idmDef.has("description") && !idmDef.get("description").isNull()) {
            description = idmDef.get("description").asText();
        }

        // Check if required
        boolean required = idmDef.has("required") && idmDef.get("required").asBoolean();

        // Create attribute
        return createAttribute(attrName, scimType, multiValued, false, false, required, "none", description);
    }

    /**
     * Create a SCIM attribute definition.
     */
    private ObjectNode createAttribute(
            String name,
            String type,
            boolean multiValued,
            boolean caseExact,
            boolean readOnly,
            boolean required,
            String returned,
            String description) {

        ObjectNode attr = objectMapper.createObjectNode();

        attr.put("name", name);
        attr.put("type", type);
        attr.put("multiValued", multiValued);
        attr.put("caseExact", caseExact);
        attr.put("required", required);
        attr.put("returned", returned);
        attr.put("uniqueness", "none");
        attr.put("mutability", readOnly ? "readOnly" : "readWrite");

        if (description != null) {
            attr.put("description", description);
        }

        return attr;
    }

    /**
     * Map PingIDM data type to SCIM attribute type.
     */
    private String mapIdmTypeToScimType(String idmType) {
        if (idmType == null) {
            return "string";
        }

        return switch (idmType.toLowerCase()) {
            case "string" -> "string";
            case "boolean" -> "boolean";
            case "integer" -> "integer";
            case "number" -> "decimal";
            case "array" -> "string"; // Will set multiValued=true separately
            case "object" -> "complex";
            case "relationship" -> "reference";
            default -> "string";
        };
    }

    /**
     * Check if an attribute is a core SCIM attribute that's already defined.
     */
    private boolean isCoreAttribute(String attrName) {
        return Set.of(
                "id", "userName", "name", "displayName", "emails", "phoneNumbers",
                "active", "password", "title", "preferredLanguage", "locale",
                "timezone", "profileUrl", "meta", "schemas",
                "givenName", "sn", "cn", "mail", "telephoneNumber",
                "members", "description"
        ).contains(attrName);
    }

    /**
     * Check if an attribute is an internal PingIDM attribute.
     */
    private boolean isInternalAttribute(String attrName) {
        return attrName.startsWith("_") ||
                Set.of(
                        "effectiveRoles", "effectiveAssignments",
                        "authzRoles", "kbaInfo", "preferences"
                ).contains(attrName);
    }

    /**
     * Extract attribute definitions from a SCIM schema resource.
     * This is useful for caching and validation.
     *
     * @param schemaResource the SCIM schema resource
     * @return list of attribute definitions
     */
    public List<AttributeDefinition> extractAttributeDefinitions(GenericScimResource schemaResource) {
        List<AttributeDefinition> definitions = new ArrayList<>();

        // Note: This is a placeholder implementation
        // The actual extraction would require parsing the schema's attributes array
        // and converting to AttributeDefinition objects
        // For now, return empty list

        return definitions;
    }
}