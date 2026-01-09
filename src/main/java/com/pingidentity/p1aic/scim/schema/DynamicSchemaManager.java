package com.pingidentity.p1aic.scim.schema;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.config.PingIdmConfigService;
import com.pingidentity.p1aic.scim.config.ScimServerConfig;
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
 * This manager fetches PingIDM managed object configuration at startup and builds
 * SCIM schema definitions dynamically, including custom attributes that customers
 * have added to their managed objects.
 *
 * The schemas are cached for performance and can be refreshed on demand.
 */
@Singleton
public class DynamicSchemaManager {

    private static final Logger LOGGER = Logger.getLogger(DynamicSchemaManager.class.getName());

    @Inject
    private PingIdmConfigService configService;

    @Inject
    private ScimSchemaBuilder schemaBuilder;

    private final ScimServerConfig config;
    private final ReadWriteLock lock;

    // Cache for SCIM schemas
    private final Map<String, GenericScimResource> schemaCache;

    // Cache for attribute definitions
    private final Map<String, List<AttributeDefinition>> attributeCache;

    // Initialization flag
    private volatile boolean initialized = false;

    /**
     * Constructor initializes caches and configuration.
     */
    public DynamicSchemaManager() {
        this.config = ScimServerConfig.getInstance();
        this.lock = new ReentrantReadWriteLock();
        this.schemaCache = new HashMap<>();
        this.attributeCache = new HashMap<>();
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

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize DynamicSchemaManager", e);
            // Don't throw exception - allow server to start but schemas won't be available
        }
    }

    /**
     * Build all SCIM schemas from PingIDM configuration.
     */
    private void buildSchemas() throws Exception {
        lock.writeLock().lock();
        try {
            String realm = config.getRealm();

            // Build User schema
            buildUserSchema(realm);

            // Build Group schema
            buildGroupSchema(realm);

            LOGGER.info("Successfully built SCIM schemas for realm: " + realm);

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
        ObjectNode userConfig = configService.getUserConfig(realm);

        if (userConfig == null) {
            LOGGER.warning("User configuration not found for realm: " + realm);
            return;
        }

        // Extract properties definition
        ObjectNode properties = configService.getPropertiesDefinition(userConfig);

        if (properties == null) {
            LOGGER.warning("Properties definition not found in user configuration");
            return;
        }

        // Build SCIM User schema
        GenericScimResource userSchema = schemaBuilder.buildUserSchema(properties);

        // Cache the schema
        schemaCache.put(ScimSchemaUrns.CORE_USER_SCHEMA, userSchema);

        // Extract and cache attribute definitions
        List<AttributeDefinition> attributes = schemaBuilder.extractAttributeDefinitions(userSchema);
        attributeCache.put("User", attributes);

        LOGGER.info("User schema built with " + attributes.size() + " attributes");
    }

    /**
     * Build Group schema from PingIDM role configuration.
     */
    private void buildGroupSchema(String realm) throws Exception {
        LOGGER.info("Building Group schema for realm: " + realm);

        // Fetch PingIDM role configuration
        ObjectNode roleConfig = configService.getRoleConfig(realm);

        if (roleConfig == null) {
            LOGGER.warning("Role configuration not found for realm: " + realm);
            return;
        }

        // Extract properties definition
        ObjectNode properties = configService.getPropertiesDefinition(roleConfig);

        if (properties == null) {
            LOGGER.warning("Properties definition not found in role configuration");
            return;
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