package com.pingidentity.p1aic.scim.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.client.PingIdmRestClient;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service class for fetching PingIDM configuration.
 *
 * This service retrieves managed object configuration from PingIDM to discover
 * the schema including custom attributes that customers have added.
 */
public class PingIdmConfigService {

    private static final Logger LOGGER = Logger.getLogger(PingIdmConfigService.class.getName());

    @Inject
    private PingIdmRestClient restClient;

    private final ObjectMapper objectMapper;

    /**
     * Constructor initializes the ObjectMapper.
     */
    public PingIdmConfigService() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get the full managed object configuration from PingIDM.
     *
     * This returns the entire /openidm/config/managed configuration which includes
     * definitions for all managed objects (users, roles, etc.) and their properties.
     *
     * @return ObjectNode containing the managed object configuration
     * @throws Exception if configuration retrieval fails
     */
    public ObjectNode getManagedConfig() throws Exception {
        try {
            LOGGER.info("Fetching PingIDM managed object configuration");

            // Get config endpoint URL
            String endpoint = restClient.getConfigManagedEndpoint();

            // BEGIN: Use getConfig for OAuth client credentials authentication
            // Call PingIDM config API (uses server OAuth token)
            Response response = restClient.getConfig(endpoint);
            // END: Use getConfig

            // Check response status
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                String errorMsg = String.format("Failed to fetch managed config. Status: %d",
                        response.getStatus());
                LOGGER.severe(errorMsg);
                throw new Exception(errorMsg);
            }

            // Parse response body
            String responseBody = response.readEntity(String.class);
            ObjectNode configNode = (ObjectNode) objectMapper.readTree(responseBody);

            LOGGER.info("Successfully fetched PingIDM managed object configuration");

            return configNode;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching managed config", e);
            throw e;
        }
    }

    /**
     * Get the configuration for a specific managed object type (e.g., user, role).
     *
     * @param objectType the managed object type (e.g., "alpha_user", "alpha_role")
     * @return ObjectNode containing the specific object's configuration, or null if not found
     * @throws Exception if configuration retrieval fails
     */
    public ObjectNode getManagedObjectConfig(String objectType) throws Exception {
        try {
            LOGGER.info("Fetching config for managed object: " + objectType);

            // Get full managed config
            ObjectNode managedConfig = getManagedConfig();

            // Extract objects array
            if (!managedConfig.has("objects") || !managedConfig.get("objects").isArray()) {
                LOGGER.warning("Managed config does not contain 'objects' array");
                return null;
            }

            // Search for the specific object type
            JsonNode objectsArray = managedConfig.get("objects");
            for (JsonNode objectNode : objectsArray) {
                if (objectNode.has("name")) {
                    String name = objectNode.get("name").asText();
                    if (objectType.equals(name)) {
                        LOGGER.info("Found configuration for: " + objectType);
                        return (ObjectNode) objectNode;
                    }
                }
            }

            LOGGER.warning("Configuration not found for managed object: " + objectType);
            return null;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching managed object config for: " + objectType, e);
            throw e;
        }
    }

    /**
     * Get the user object configuration.
     *
     * @param realm the realm name (e.g., "alpha")
     * @return ObjectNode containing the user object configuration
     * @throws Exception if configuration retrieval fails
     */
    public ObjectNode getUserConfig(String realm) throws Exception {
        String objectType = realm + "_user";
        return getManagedObjectConfig(objectType);
    }

    /**
     * Get the role object configuration.
     *
     * @param realm the realm name (e.g., "alpha")
     * @return ObjectNode containing the role object configuration
     * @throws Exception if configuration retrieval fails
     */
    public ObjectNode getRoleConfig(String realm) throws Exception {
        String objectType = realm + "_role";
        return getManagedObjectConfig(objectType);
    }

    /**
     * Extract properties definition from a managed object configuration.
     *
     * @param objectConfig the managed object configuration
     * @return ObjectNode containing the properties definition, or null if not found
     */
    public ObjectNode getPropertiesDefinition(ObjectNode objectConfig) {
        if (objectConfig == null) {
            return null;
        }

        // Properties can be at different locations depending on PingIDM version
        // Check schema.properties first (newer versions)
        if (objectConfig.has("schema") && objectConfig.get("schema").isObject()) {
            ObjectNode schema = (ObjectNode) objectConfig.get("schema");
            if (schema.has("properties") && schema.get("properties").isObject()) {
                return (ObjectNode) schema.get("properties");
            }
        }

        // Check properties directly (older versions)
        if (objectConfig.has("properties") && objectConfig.get("properties").isObject()) {
            return (ObjectNode) objectConfig.get("properties");
        }

        return null;
    }

    /**
     * Check if a managed object has a specific property defined.
     *
     * @param objectConfig the managed object configuration
     * @param propertyName the property name to check
     * @return true if the property is defined, false otherwise
     */
    public boolean hasProperty(ObjectNode objectConfig, String propertyName) {
        ObjectNode properties = getPropertiesDefinition(objectConfig);
        if (properties == null) {
            return false;
        }

        return properties.has(propertyName);
    }

    /**
     * Get the definition for a specific property.
     *
     * @param objectConfig the managed object configuration
     * @param propertyName the property name
     * @return ObjectNode containing the property definition, or null if not found
     */
    public ObjectNode getPropertyDefinition(ObjectNode objectConfig, String propertyName) {
        ObjectNode properties = getPropertiesDefinition(objectConfig);
        if (properties == null || !properties.has(propertyName)) {
            return null;
        }

        JsonNode propDef = properties.get(propertyName);
        if (propDef.isObject()) {
            return (ObjectNode) propDef;
        }

        return null;
    }
}