package com.pingidentity.p1aic.scim.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.config.CustomAttributeMappingConfig;
import com.pingidentity.p1aic.scim.config.PingIdmConfigService;
import com.pingidentity.p1aic.scim.config.ScimServerConfig;
import com.pingidentity.p1aic.scim.mapping.CustomAttributeMapperService;
import com.unboundid.scim2.common.GenericScimResource;
import com.unboundid.scim2.common.types.AttributeDefinition;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dynamic schema manager that builds and caches SCIM schemas based on PingIDM configuration.
 *
 * <p>This manager fetches PingIDM managed object configuration at startup and builds
 * SCIM schema definitions dynamically, including custom attributes that customers
 * have added to their managed objects.</p>
 *
 * <p>MODIFIED: Now integrates with {@link CustomAttributeMapperService} to enhance schemas
 * with custom-mapped attributes for SCIM 2.0 compliance (e.g., Enterprise User extension).</p>
 *
 * <p>The schemas are cached for performance and can be refreshed on demand.</p>
 */
@Singleton
public class DynamicSchemaManager {

    private static final Logger LOGGER = Logger.getLogger(DynamicSchemaManager.class.getName());

    @Inject
    private PingIdmConfigService configService;

    @Inject
    private ScimSchemaBuilder schemaBuilder;

    // BEGIN: Added CustomAttributeMapperService injection
    @Inject
    private CustomAttributeMapperService customAttributeMapper;
    // END: Added CustomAttributeMapperService injection

    private final ScimServerConfig config;
    private final ReadWriteLock lock;
    private final ObjectMapper objectMapper;

    // Cache for SCIM schemas
    private final Map<String, GenericScimResource> schemaCache;

    // Cache for attribute definitions
    private final Map<String, List<AttributeDefinition>> attributeCache;

    // Initialization flag
    private volatile boolean initialized = false;

    /**
     * Default constructor initializes caches and configuration.
     */
    public DynamicSchemaManager() {
        this.config = ScimServerConfig.getInstance();
        this.lock = new ReentrantReadWriteLock();
        this.schemaCache = new HashMap<>();
        this.attributeCache = new HashMap<>();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Constructor with explicit dependencies (for testing or manual instantiation).
     */
    @Inject
    public DynamicSchemaManager(PingIdmConfigService configService,
                                ScimSchemaBuilder schemaBuilder,
                                CustomAttributeMapperService customAttributeMapper) {
        this.configService = configService;
        this.schemaBuilder = schemaBuilder;
        this.customAttributeMapper = customAttributeMapper;

        // Initialize standard fields
        this.config = ScimServerConfig.getInstance();
        this.lock = new ReentrantReadWriteLock();
        this.schemaCache = new HashMap<>();
        this.attributeCache = new HashMap<>();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Initialize schemas on startup.
     * This method is called automatically after dependency injection.
     */
    @PostConstruct
    public void initialize() {
        try {
            LOGGER.info("Initializing DynamicSchemaManager...");

            // Build schemas from PingIDM configuration
            buildSchemas();

            initialized = true;
            LOGGER.info("DynamicSchemaManager initialized successfully");
            LOGGER.info("Schema count: " + schemaCache.size());
            LOGGER.info("Cached schemas: " + String.join(", ", schemaCache.keySet()));

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize DynamicSchemaManager", e);
            throw new RuntimeException("Failed to initialize DynamicSchemaManager", e);
        }
    }

    /**
     * Build all SCIM schemas from PingIDM configuration.
     */
    private void buildSchemas() throws Exception {
        lock.writeLock().lock();
        try {
            String realm = config.getRealm();

            LOGGER.info("Building schemas. Realm: " + realm);

            // Build User schema
            LOGGER.info("About to build user schema...");
            buildUserSchema(realm);
            LOGGER.info("User schema build completed");

            // BEGIN: Build Enterprise User extension schema if custom mappings exist
            if (customAttributeMapper != null && customAttributeMapper.hasEnterpriseExtension()) {
                LOGGER.info("About to build Enterprise User extension schema...");
                buildEnterpriseUserSchema();
                LOGGER.info("Enterprise User extension schema build completed");
            }
            // END: Build Enterprise User extension schema

            // Build Group schema
            LOGGER.info("About to build group schema...");
            buildGroupSchema(realm);
            LOGGER.info("Group schema build completed");

            LOGGER.info("Successfully built SCIM schemas for realm: " + realm);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in buildSchemas()", e);
            throw e;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Build User schema from PingIDM user configuration.
     */
    private void buildUserSchema(String realm) throws Exception {
        LOGGER.info("Building User schema for realm: " + realm);

        // Fetch PingIDM user configuration
        String userObjectName = config.getManagedUserObjectName();
        LOGGER.info("User object name: " + userObjectName);
        if (configService == null) {
            throw new IllegalStateException("ConfigService is null!");
        }
        ObjectNode userConfig = configService.getManagedObjectConfig(userObjectName);

        if (userConfig == null) {
            LOGGER.warning("User configuration not found for realm: " + realm);
            throw new Exception("User configuration not found for object: " + userObjectName);
        }

        // Extract properties definition
        ObjectNode properties = configService.getPropertiesDefinition(userConfig);

        if (properties == null) {
            LOGGER.warning("Properties definition not found in user configuration");
            properties = objectMapper.createObjectNode();
            LOGGER.info("Using empty properties for user schema");
        }
        LOGGER.info("Building SCIM User schema with " + properties.size() + " custom properties");

        // Build SCIM User schema
        GenericScimResource userSchema = schemaBuilder.buildUserSchema(properties);

        // BEGIN: Enhance schema with custom attribute mappings
        if (customAttributeMapper != null && customAttributeMapper.hasCustomMappings()) {
            LOGGER.info("Enhancing User schema with custom attribute mappings...");
            ObjectNode schemaNode = (ObjectNode) userSchema.getObjectNode();
            customAttributeMapper.enhanceUserSchema(schemaNode);
            LOGGER.info("User schema enhanced with custom mappings");
        }
        // END: Enhance schema with custom attribute mappings

        // Cache the schema
        schemaCache.put(ScimSchemaUrns.CORE_USER_SCHEMA, userSchema);

        // Extract and cache attribute definitions
        List<AttributeDefinition> attributes = schemaBuilder.extractAttributeDefinitions(userSchema);
        attributeCache.put("User", attributes);

        LOGGER.info("User schema built with " + attributes.size() + " attributes");
    }

    // BEGIN: New method to build Enterprise User extension schema
    /**
     * Build Enterprise User extension schema from custom attribute mappings.
     *
     * <p>This schema is only built if enterprise extension attributes are configured
     * in the custom attribute mappings.</p>
     */
    private void buildEnterpriseUserSchema() {
        LOGGER.info("Building Enterprise User extension schema from custom mappings");

        ObjectNode schemaNode = objectMapper.createObjectNode();

        // Set schema metadata
        schemaNode.put("id", ScimSchemaUrns.ENTERPRISE_USER_SCHEMA);
        schemaNode.put("name", "EnterpriseUser");
        schemaNode.put("description", "Enterprise User Extension");

        // Add schemas array
        ArrayNode schemas = objectMapper.createArrayNode();
        schemas.add(ScimSchemaUrns.SCHEMA);
        schemaNode.set("schemas", schemas);

        // Create empty attributes array - will be populated by enhanceEnterpriseUserSchema
        ArrayNode attributes = objectMapper.createArrayNode();
        schemaNode.set("attributes", attributes);

        // Enhance with custom enterprise mappings
        if (customAttributeMapper != null) {
            customAttributeMapper.enhanceEnterpriseUserSchema(schemaNode);
        }

        // Only cache if we have attributes
        ArrayNode enhancedAttrs = (ArrayNode) schemaNode.get("attributes");
        if (enhancedAttrs != null && enhancedAttrs.size() > 0) {
            GenericScimResource enterpriseSchema = new GenericScimResource(schemaNode);
            schemaCache.put(ScimSchemaUrns.ENTERPRISE_USER_SCHEMA, enterpriseSchema);

            // Extract and cache attribute definitions
            List<AttributeDefinition> attrDefs = schemaBuilder.extractAttributeDefinitions(enterpriseSchema);
            attributeCache.put("EnterpriseUser", attrDefs);

            LOGGER.info("Enterprise User schema built with " + enhancedAttrs.size() + " attributes");
        } else {
            LOGGER.info("No enterprise extension attributes configured, skipping schema creation");
        }
    }
    // END: New method to build Enterprise User extension schema

    /**
     * Build Group schema from PingIDM role configuration.
     */
    private void buildGroupSchema(String realm) throws Exception {
        LOGGER.info("Building Group schema for realm: " + realm);

        // Fetch PingIDM role configuration
        String roleObjectName = config.getManagedRoleObjectName();
        LOGGER.info("Role object name: " + roleObjectName);
        ObjectNode roleConfig = configService.getManagedObjectConfig(roleObjectName);

        if (roleConfig == null) {
            LOGGER.warning("Role configuration not found for realm: " + realm);
            throw new Exception("Role configuration not found for object: " + roleObjectName);
        }

        // Extract properties definition
        ObjectNode properties = configService.getPropertiesDefinition(roleConfig);

        if (properties == null) {
            LOGGER.warning("Properties definition not found in role configuration");
            properties = objectMapper.createObjectNode();
            LOGGER.info("Using empty properties for group schema");
        }

        // Build SCIM Group schema
        GenericScimResource groupSchema = schemaBuilder.buildGroupSchema(properties);

        // Cache the schema
        schemaCache.put(ScimSchemaUrns.CORE_GROUP_SCHEMA, groupSchema);

        // Extract and cache attribute definitions
        List<AttributeDefinition> attributes = schemaBuilder.extractAttributeDefinitions(groupSchema);
        attributeCache.put("Group", attributes);

        LOGGER.info("Group schema built with " + attributes.size() + " attributes");
    }

    /**
     * Get a SCIM schema by URN.
     *
     * @param schemaUrn the schema URN (e.g., "urn:ietf:params:scim:schemas:core:2.0:User")
     * @return the schema resource, or null if not found
     */
    public GenericScimResource getSchema(String schemaUrn) {
        lock.readLock().lock();
        try {
            return schemaCache.get(schemaUrn);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all SCIM schemas.
     *
     * @return list of all schema resources
     */
    public List<GenericScimResource> getAllSchemas() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(schemaCache.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get attribute definitions for a resource type.
     *
     * @param resourceType the resource type (e.g., "User", "Group")
     * @return list of attribute definitions, or empty list if not found
     */
    public List<AttributeDefinition> getAttributeDefinitions(String resourceType) {
        lock.readLock().lock();
        try {
            List<AttributeDefinition> attributes = attributeCache.get(resourceType);
            return attributes != null ? new ArrayList<>(attributes) : new ArrayList<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the User schema.
     *
     * @return the User schema resource, or null if not available
     */
    public GenericScimResource getUserSchema() {
        return getSchema(ScimSchemaUrns.CORE_USER_SCHEMA);
    }

    /**
     * Get the Group schema.
     *
     * @return the Group schema resource, or null if not available
     */
    public GenericScimResource getGroupSchema() {
        return getSchema(ScimSchemaUrns.CORE_GROUP_SCHEMA);
    }

    // BEGIN: New method to get Enterprise User schema
    /**
     * Get the Enterprise User extension schema.
     *
     * @return the Enterprise User schema resource, or null if not available
     */
    public GenericScimResource getEnterpriseUserSchema() {
        return getSchema(ScimSchemaUrns.ENTERPRISE_USER_SCHEMA);
    }
    // END: New method to get Enterprise User schema

    /**
     * Check if a schema exists.
     *
     * @param schemaUrn the schema URN
     * @return true if the schema exists, false otherwise
     */
    public boolean hasSchema(String schemaUrn) {
        lock.readLock().lock();
        try {
            return schemaCache.containsKey(schemaUrn);
        } finally {
            lock.readLock().unlock();
        }
    }

    // BEGIN: New method to check for enterprise extension
    /**
     * Check if the Enterprise User extension schema is available.
     *
     * @return true if the Enterprise User schema exists
     */
    public boolean hasEnterpriseUserSchema() {
        return hasSchema(ScimSchemaUrns.ENTERPRISE_USER_SCHEMA);
    }
    // END: New method to check for enterprise extension

    /**
     * Refresh schemas by re-fetching from PingIDM configuration.
     * This can be called to pick up configuration changes without restarting the server.
     *
     * @throws Exception if refresh fails
     */
    public void refreshSchemas() throws Exception {
        LOGGER.info("Refreshing SCIM schemas from PingIDM configuration");

        lock.writeLock().lock();
        try {
            // Clear existing caches
            schemaCache.clear();
            attributeCache.clear();

            // Rebuild schemas
            buildSchemas();

            LOGGER.info("SCIM schemas refreshed successfully");

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Check if the schema manager has been initialized.
     *
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        LOGGER.fine("isInitialized() called. Instance hashcode: " + System.identityHashCode(this) +
                " initialized: " + initialized);
        return initialized;
    }

    /**
     * Get the number of cached schemas.
     *
     * @return the number of schemas in the cache
     */
    public int getSchemaCount() {
        lock.readLock().lock();
        try {
            return schemaCache.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all schema URNs that are cached.
     *
     * @return list of schema URNs
     */
    public List<String> getCachedSchemaUrns() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(schemaCache.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }
}