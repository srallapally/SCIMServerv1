package com.pingidentity.p1aic.scim.schema;

// --- BEGIN: AttributeTypeInfo.java ---
import com.fasterxml.jackson.databind.JsonNode;

public class AttributeTypeInfo {

    private final String type;
    private final boolean multiValued;
    private final JsonNode itemsSchema;  // For array types

    public AttributeTypeInfo(String type, boolean multiValued, JsonNode itemsSchema) {
        this.type = type;
        this.multiValued = multiValued;
        this.itemsSchema = itemsSchema;
    }

    public String getType() { return type; }
    public boolean isMultiValued() { return multiValued; }
    public JsonNode getItemsSchema() { return itemsSchema; }

    public boolean isString() { return "string".equalsIgnoreCase(type); }
    public boolean isBoolean() { return "boolean".equalsIgnoreCase(type); }
    public boolean isNumber() { return "number".equalsIgnoreCase(type) || "integer".equalsIgnoreCase(type); }
    public boolean isObject() { return "object".equalsIgnoreCase(type); }
    public boolean isRelationship() { return "relationship".equalsIgnoreCase(type); }
}
// --- END: AttributeTypeInfo.java ---