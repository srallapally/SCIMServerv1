package com.pingidentity.p1aic.scim.schema;

// --- BEGIN: SchemaDefinitions.java ---
import com.unboundid.scim2.common.types.SchemaResource;

public class SchemaDefinitions {

    private String userObjectName;        // e.g., "alpha_user"
    private String roleObjectName;        // e.g., "alpha_role"
    private SchemaResource userSchema;
    private SchemaResource userExtensionSchema;
    private SchemaResource roleSchema;
    private SchemaResource roleExtensionSchema;

    private static final String EXTENSION_PREFIX = "urn:pingidentity:scim:schemas:extension:";

    // Getters and setters
    public String getUserObjectName() { return userObjectName; }
    public void setUserObjectName(String userObjectName) { this.userObjectName = userObjectName; }

    public String getRoleObjectName() { return roleObjectName; }
    public void setRoleObjectName(String roleObjectName) { this.roleObjectName = roleObjectName; }

    public SchemaResource getUserSchema() { return userSchema; }
    public void setUserSchema(SchemaResource userSchema) { this.userSchema = userSchema; }

    public SchemaResource getUserExtensionSchema() { return userExtensionSchema; }
    public void setUserExtensionSchema(SchemaResource schema) { this.userExtensionSchema = schema; }

    public SchemaResource getRoleSchema() { return roleSchema; }
    public void setRoleSchema(SchemaResource roleSchema) { this.roleSchema = roleSchema; }

    public SchemaResource getRoleExtensionSchema() { return roleExtensionSchema; }
    public void setRoleExtensionSchema(SchemaResource schema) { this.roleExtensionSchema = schema; }

    public String getUserExtensionSchemaUrn() {
        return EXTENSION_PREFIX + "user:1.0";
    }

    public String getRoleExtensionSchemaUrn() {
        return EXTENSION_PREFIX + "role:1.0";
    }
}
// --- END: SchemaDefinitions.java ---
