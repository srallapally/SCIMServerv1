package com.pingidentity.p1aic.scim.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a single custom attribute mapping between SCIM and PingIDM.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomAttributeMapping {

    public static final String CORE_USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
    public static final String ENTERPRISE_USER_SCHEMA = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

    @JsonProperty("scimPath")
    private String scimPath;

    @JsonProperty("scimSchema")
    private String scimSchema;

    @JsonProperty("pingIdmAttribute")
    private String pingIdmAttribute;

    @JsonProperty("type")
    private String type = "string";

    @JsonProperty("referenceTypes")
    private List<String> referenceTypes;

    @JsonProperty("description")
    private String description;

    @JsonProperty("multiValued")
    private boolean multiValued = false;

    @JsonProperty("required")
    private boolean required = false;

    @JsonProperty("caseExact")
    private boolean caseExact = false;

    @JsonProperty("mutability")
    private String mutability = "readWrite";

    @JsonProperty("returned")
    private String returned = "default";

    @JsonProperty("uniqueness")
    private String uniqueness = "none";

    @JsonProperty("canonicalValues")
    private List<String> canonicalValues;

    // New field to prevent double-mapping conflicts
    @JsonProperty("handledByCode")
    private boolean handledByCode = false;

    public CustomAttributeMapping() {
    }

    public CustomAttributeMapping(String scimPath, String scimSchema, String pingIdmAttribute) {
        this.scimPath = scimPath;
        this.scimSchema = scimSchema;
        this.pingIdmAttribute = pingIdmAttribute;
    }

    // === Derived Properties ===

    public boolean isEnterpriseExtension() {
        return scimSchema != null && scimSchema.contains("extension:enterprise:2.0:User");
    }

    public boolean isCoreUserAttribute() {
        return CORE_USER_SCHEMA.equals(scimSchema);
    }

    public boolean isNested() {
        return scimPath != null && scimPath.contains(".");
    }

    public String getParentPath() {
        if (!isNested()) {
            return null;
        }
        return scimPath.substring(0, scimPath.lastIndexOf('.'));
    }

    public String getLeafName() {
        if (!isNested()) {
            return scimPath;
        }
        return scimPath.substring(scimPath.lastIndexOf('.') + 1);
    }

    public String getFullScimPath() {
        if (isEnterpriseExtension()) {
            return scimSchema + ":" + scimPath;
        }
        return scimPath;
    }

    public boolean isValid() {
        return scimPath != null && !scimPath.isBlank()
                && scimSchema != null && !scimSchema.isBlank()
                && pingIdmAttribute != null && !pingIdmAttribute.isBlank();
    }

    // === Getters and Setters ===

    public String getScimPath() { return scimPath; }
    public void setScimPath(String scimPath) { this.scimPath = scimPath; }

    public String getScimSchema() { return scimSchema; }
    public void setScimSchema(String scimSchema) { this.scimSchema = scimSchema; }

    public String getPingIdmAttribute() { return pingIdmAttribute; }
    public void setPingIdmAttribute(String pingIdmAttribute) { this.pingIdmAttribute = pingIdmAttribute; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<String> getReferenceTypes() { return referenceTypes; }
    public void setReferenceTypes(List<String> referenceTypes) { this.referenceTypes = referenceTypes; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isMultiValued() { return multiValued; }
    public void setMultiValued(boolean multiValued) { this.multiValued = multiValued; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public boolean isCaseExact() { return caseExact; }
    public void setCaseExact(boolean caseExact) { this.caseExact = caseExact; }

    public String getMutability() { return mutability; }
    public void setMutability(String mutability) { this.mutability = mutability; }

    public String getReturned() { return returned; }
    public void setReturned(String returned) { this.returned = returned; }

    public String getUniqueness() { return uniqueness; }
    public void setUniqueness(String uniqueness) { this.uniqueness = uniqueness; }

    public List<String> getCanonicalValues() { return canonicalValues; }
    public void setCanonicalValues(List<String> canonicalValues) { this.canonicalValues = canonicalValues; }

    public boolean isHandledByCode() { return handledByCode; }
    public void setHandledByCode(boolean handledByCode) { this.handledByCode = handledByCode; }

    @Override
    public String toString() {
        return "CustomAttributeMapping{" +
                "scimPath='" + scimPath + '\'' +
                ", pingIdmAttribute='" + pingIdmAttribute + '\'' +
                ", handledByCode=" + handledByCode +
                '}';
    }
}