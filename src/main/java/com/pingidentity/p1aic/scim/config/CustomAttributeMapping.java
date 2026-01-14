package com.pingidentity.p1aic.scim.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a single custom attribute mapping between SCIM and PingIDM.
 *
 * <p>This class maps a standard SCIM attribute (that PingIDM doesn't support OOTB)
 * to a custom attribute in the PingIDM managed object schema.</p>
 *
 * <p>Example mapping:</p>
 * <pre>
 * {
 *   "scimPath": "name.middleName",
 *   "scimSchema": "urn:ietf:params:scim:schemas:core:2.0:User",
 *   "pingIdmAttribute": "frIndexedString1",
 *   "type": "string",
 *   "description": "The middle name(s) of the User"
 * }
 * </pre>
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

    // === Constructors ===

    public CustomAttributeMapping() {
    }

    public CustomAttributeMapping(String scimPath, String scimSchema, String pingIdmAttribute) {
        this.scimPath = scimPath;
        this.scimSchema = scimSchema;
        this.pingIdmAttribute = pingIdmAttribute;
    }

    // === Derived Properties ===

    /**
     * Check if this is an Enterprise User extension attribute.
     *
     * @return true if the scimSchema is the Enterprise User extension URN
     */
    public boolean isEnterpriseExtension() {
        return scimSchema != null && scimSchema.contains("extension:enterprise:2.0:User");
    }

    /**
     * Check if this is a Core User schema attribute.
     *
     * @return true if the scimSchema is the Core User URN
     */
    public boolean isCoreUserAttribute() {
        return CORE_USER_SCHEMA.equals(scimSchema);
    }

    /**
     * Check if this is a nested attribute (e.g., name.middleName).
     *
     * @return true if the scimPath contains a dot
     */
    public boolean isNested() {
        return scimPath != null && scimPath.contains(".");
    }

    /**
     * Get the parent path for nested attributes.
     * For "name.middleName", returns "name".
     *
     * @return the parent path, or null if not nested
     */
    public String getParentPath() {
        if (!isNested()) {
            return null;
        }
        return scimPath.substring(0, scimPath.lastIndexOf('.'));
    }

    /**
     * Get the leaf attribute name.
     * For "name.middleName", returns "middleName".
     * For "title", returns "title".
     *
     * @return the leaf attribute name
     */
    public String getLeafName() {
        if (!isNested()) {
            return scimPath;
        }
        return scimPath.substring(scimPath.lastIndexOf('.') + 1);
    }

    /**
     * Build the full SCIM path including schema for enterprise extensions.
     * For core attributes: returns scimPath (e.g., "title")
     * For enterprise: returns "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:employeeNumber"
     *
     * @return the fully qualified SCIM path
     */
    public String getFullScimPath() {
        if (isEnterpriseExtension()) {
            return scimSchema + ":" + scimPath;
        }
        return scimPath;
    }

    /**
     * Check if this mapping entry is valid (has required fields).
     *
     * @return true if scimPath, scimSchema, and pingIdmAttribute are all non-null
     */
    public boolean isValid() {
        return scimPath != null && !scimPath.isBlank()
                && scimSchema != null && !scimSchema.isBlank()
                && pingIdmAttribute != null && !pingIdmAttribute.isBlank();
    }

    // === Getters and Setters ===

    public String getScimPath() {
        return scimPath;
    }

    public void setScimPath(String scimPath) {
        this.scimPath = scimPath;
    }

    public String getScimSchema() {
        return scimSchema;
    }

    public void setScimSchema(String scimSchema) {
        this.scimSchema = scimSchema;
    }

    public String getPingIdmAttribute() {
        return pingIdmAttribute;
    }

    public void setPingIdmAttribute(String pingIdmAttribute) {
        this.pingIdmAttribute = pingIdmAttribute;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getReferenceTypes() {
        return referenceTypes;
    }

    public void setReferenceTypes(List<String> referenceTypes) {
        this.referenceTypes = referenceTypes;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isMultiValued() {
        return multiValued;
    }

    public void setMultiValued(boolean multiValued) {
        this.multiValued = multiValued;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isCaseExact() {
        return caseExact;
    }

    public void setCaseExact(boolean caseExact) {
        this.caseExact = caseExact;
    }

    public String getMutability() {
        return mutability;
    }

    public void setMutability(String mutability) {
        this.mutability = mutability;
    }

    public String getReturned() {
        return returned;
    }

    public void setReturned(String returned) {
        this.returned = returned;
    }

    public String getUniqueness() {
        return uniqueness;
    }

    public void setUniqueness(String uniqueness) {
        this.uniqueness = uniqueness;
    }

    public List<String> getCanonicalValues() {
        return canonicalValues;
    }

    public void setCanonicalValues(List<String> canonicalValues) {
        this.canonicalValues = canonicalValues;
    }

    @Override
    public String toString() {
        return "CustomAttributeMapping{" +
                "scimPath='" + scimPath + '\'' +
                ", scimSchema='" + scimSchema + '\'' +
                ", pingIdmAttribute='" + pingIdmAttribute + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}