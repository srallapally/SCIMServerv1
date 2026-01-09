package com.pingidentity.p1aic.scim.schema;
/**
 * Constants for SCIM 2.0 schema URNs used throughout the application.
 */
public final class ScimSchemaUrns {

    // SCIM 2.0 Core Schemas
    public static final String CORE_USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
    public static final String CORE_GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";

    // Enterprise User Extension
    public static final String ENTERPRISE_USER_EXTENSION = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

    // Custom PingIDM Extension Schema URN
    public static final String PINGIDM_USER_EXTENSION = "urn:pingidentity:scim:schemas:extension:pingidm:2.0:User";
    public static final String PINGIDM_GROUP_EXTENSION = "urn:pingidentity:scim:schemas:extension:pingidm:2.0:Group";

    // SCIM Service Provider Configuration
    public static final String SERVICE_PROVIDER_CONFIG = "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig";

    // SCIM Resource Type
    public static final String RESOURCE_TYPE = "urn:ietf:params:scim:schemas:core:2.0:ResourceType";

    // SCIM Schema
    public static final String SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Schema";

    // SCIM Error Response
    public static final String ERROR = "urn:ietf:params:scim:api:messages:2.0:Error";

    // SCIM List Response
    public static final String LIST_RESPONSE = "urn:ietf:params:scim:api:messages:2.0:ListResponse";

    // SCIM Patch Operations
    public static final String PATCH_OP = "urn:ietf:params:scim:api:messages:2.0:PatchOp";

    // SCIM Bulk Operations
    public static final String BULK_REQUEST = "urn:ietf:params:scim:api:messages:2.0:BulkRequest";
    public static final String BULK_RESPONSE = "urn:ietf:params:scim:api:messages:2.0:BulkResponse";

    // Private constructor to prevent instantiation
    private ScimSchemaUrns() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}