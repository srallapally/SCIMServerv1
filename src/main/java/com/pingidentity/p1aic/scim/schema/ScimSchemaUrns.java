package com.pingidentity.p1aic.scim.schema;

/**
 * Constants for SCIM 2.0 schema URNs used throughout the application.
 *
 * <p>These URNs are defined in RFC 7643 (SCIM Core Schema) and RFC 7644 (SCIM Protocol).</p>
 */
public final class ScimSchemaUrns {

    // ========================================================================
    // Core SCIM Resource Schemas (RFC 7643)
    // ========================================================================

    /** Core User schema URN */
    public static final String CORE_USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";

    /** Core Group schema URN */
    public static final String CORE_GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";

    // BEGIN: Added Enterprise User Schema constant
    /** Enterprise User Extension schema URN (RFC 7643 Section 4.3) */
    public static final String ENTERPRISE_USER_SCHEMA = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

    /** Alias for ENTERPRISE_USER_SCHEMA (for backward compatibility) */
    public static final String ENTERPRISE_USER_EXTENSION = ENTERPRISE_USER_SCHEMA;
    // END: Added Enterprise User Schema constant

    // ========================================================================
    // SCIM Discovery Schemas
    // ========================================================================

    /** Schema definition schema URN (for /Schemas endpoint) */
    public static final String SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Schema";

    /** Service Provider Configuration schema URN */
    public static final String SERVICE_PROVIDER_CONFIG = "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig";

    /** Resource Type schema URN */
    public static final String RESOURCE_TYPE = "urn:ietf:params:scim:schemas:core:2.0:ResourceType";

    // ========================================================================
    // SCIM Protocol Message Schemas (RFC 7644)
    // ========================================================================

    /** Error response schema URN */
    public static final String ERROR = "urn:ietf:params:scim:api:messages:2.0:Error";

    /** List response schema URN */
    public static final String LIST_RESPONSE = "urn:ietf:params:scim:api:messages:2.0:ListResponse";

    /** Patch operation schema URN */
    public static final String PATCH_OP = "urn:ietf:params:scim:api:messages:2.0:PatchOp";

    /** Bulk request schema URN */
    public static final String BULK_REQUEST = "urn:ietf:params:scim:api:messages:2.0:BulkRequest";

    /** Bulk response schema URN */
    public static final String BULK_RESPONSE = "urn:ietf:params:scim:api:messages:2.0:BulkResponse";

    /** Search request schema URN */
    public static final String SEARCH_REQUEST = "urn:ietf:params:scim:api:messages:2.0:SearchRequest";

    // ========================================================================
    // Custom PingIDM Extension Schemas (Optional)
    // ========================================================================

    /** PingIDM User extension schema URN (for custom PingIDM attributes) */
    public static final String PINGIDM_USER_EXTENSION = "urn:pingidentity:scim:schemas:extension:pingidm:2.0:User";

    /** PingIDM Group extension schema URN (for custom PingIDM attributes) */
    public static final String PINGIDM_GROUP_EXTENSION = "urn:pingidentity:scim:schemas:extension:pingidm:2.0:Group";

    // Private constructor to prevent instantiation
    private ScimSchemaUrns() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}