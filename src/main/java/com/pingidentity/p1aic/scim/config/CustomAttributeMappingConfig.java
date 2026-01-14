package com.pingidentity.p1aic.scim.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads and manages custom attribute mappings from configuration.
 *
 * <p>Configuration can be provided via environment variables:</p>
 * <ul>
 *   <li>{@code SCIM_CUSTOM_ATTRIBUTE_MAPPINGS} - JSON string containing the mappings</li>
 *   <li>{@code SCIM_CUSTOM_ATTRIBUTE_MAPPINGS_FILE} - Path to a JSON file containing mappings</li>
 * </ul>
 *
 * <p>The JSON string option takes precedence if both are provided.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * {@literal @}Inject
 * private CustomAttributeMappingConfig mappingConfig;
 *
 * // Get all mappings for enterprise extension
 * List&lt;CustomAttributeMapping&gt; enterpriseMappings = mappingConfig.getEnterpriseUserMappings();
 *
 * // Lookup by SCIM path
 * Optional&lt;CustomAttributeMapping&gt; titleMapping = mappingConfig.getByScimPath("title");
 *
 * // Lookup by PingIDM attribute
 * Optional&lt;CustomAttributeMapping&gt; mapping = mappingConfig.getByPingIdmAttribute("frIndexedString1");
 * </pre>
 */
@Singleton
public class CustomAttributeMappingConfig {

    private static final Logger LOG = LoggerFactory.getLogger(CustomAttributeMappingConfig.class);

    /** Environment variable for inline JSON configuration */
    public static final String ENV_MAPPINGS_JSON = "SCIM_CUSTOM_ATTRIBUTE_MAPPINGS";

    /** Environment variable for configuration file path */
    public static final String ENV_MAPPINGS_FILE = "SCIM_CUSTOM_ATTRIBUTE_MAPPINGS_FILE";

    private final List<CustomAttributeMapping> mappings;

    /** Lookup map by SCIM path (e.g., "title" or "name.middleName") */
    private final Map<String, CustomAttributeMapping> byScimPath;

    /** Lookup map by full SCIM path including schema for enterprise attrs */
    private final Map<String, CustomAttributeMapping> byFullScimPath;

    /** Lookup map by PingIDM attribute name */
    private final Map<String, CustomAttributeMapping> byPingIdmAttribute;

    private final ObjectMapper objectMapper;

    /**
     * Creates a new configuration instance, loading mappings from environment.
     */
    public CustomAttributeMappingConfig() {
        this.objectMapper = new ObjectMapper();
        this.mappings = loadMappings();
        this.byScimPath = buildScimPathIndex();
        this.byFullScimPath = buildFullScimPathIndex();
        this.byPingIdmAttribute = buildPingIdmIndex();

        LOG.info("Loaded {} custom attribute mappings", mappings.size());
        if (LOG.isDebugEnabled()) {
            mappings.forEach(m -> LOG.debug("  {} ({}) <-> {}",
                    m.getScimPath(), m.getScimSchema(), m.getPingIdmAttribute()));
        }
    }

    /**
     * Creates a configuration instance with provided mappings (for testing).
     *
     * @param mappings the list of mappings to use
     */
    public CustomAttributeMappingConfig(List<CustomAttributeMapping> mappings) {
        this.objectMapper = new ObjectMapper();
        this.mappings = mappings != null ? new ArrayList<>(mappings) : new ArrayList<>();
        this.byScimPath = buildScimPathIndex();
        this.byFullScimPath = buildFullScimPathIndex();
        this.byPingIdmAttribute = buildPingIdmIndex();
    }

    // === Configuration Loading ===

    private List<CustomAttributeMapping> loadMappings() {
        // Try JSON string first (takes precedence)
        String jsonConfig = System.getenv(ENV_MAPPINGS_JSON);
        if (jsonConfig != null && !jsonConfig.isBlank()) {
            LOG.info("Loading custom attribute mappings from {} environment variable", ENV_MAPPINGS_JSON);
            try {
                return parseMappingsJson(jsonConfig);
            } catch (IOException e) {
                LOG.error("Failed to parse {} environment variable: {}", ENV_MAPPINGS_JSON, e.getMessage());
                throw new RuntimeException("Invalid custom attribute mapping configuration in " + ENV_MAPPINGS_JSON, e);
            }
        }

        // Try file path
        String filePath = System.getenv(ENV_MAPPINGS_FILE);
        if (filePath != null && !filePath.isBlank()) {
            LOG.info("Loading custom attribute mappings from file: {}", filePath);
            try {
                File file = new File(filePath);
                if (!file.exists()) {
                    LOG.warn("Custom attribute mappings file not found: {}. Continuing with no custom mappings.", filePath);
                    return Collections.emptyList();
                }
                if (!file.canRead()) {
                    LOG.error("Custom attribute mappings file is not readable: {}", filePath);
                    throw new RuntimeException("Cannot read custom attribute mapping file: " + filePath);
                }
                String content = Files.readString(file.toPath());
                return parseMappingsJson(content);
            } catch (IOException e) {
                LOG.error("Failed to read custom attribute mappings file {}: {}", filePath, e.getMessage());
                throw new RuntimeException("Cannot read custom attribute mapping file: " + filePath, e);
            }
        }

        LOG.info("No custom attribute mappings configured (neither {} nor {} environment variables set)",
                ENV_MAPPINGS_JSON, ENV_MAPPINGS_FILE);
        return Collections.emptyList();
    }

    /**
     * Parse the JSON configuration string into a list of mappings.
     * Supports both wrapped format (with "customAttributeMappings" key) and array format.
     */
    private List<CustomAttributeMapping> parseMappingsJson(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);

        // Handle wrapped format: { "customAttributeMappings": [...] }
        JsonNode mappingsNode = root.has("customAttributeMappings")
                ? root.get("customAttributeMappings")
                : root;

        if (!mappingsNode.isArray()) {
            throw new IOException("Expected JSON array of mappings, got: " + mappingsNode.getNodeType());
        }

        List<CustomAttributeMapping> result = objectMapper.convertValue(mappingsNode,
                new TypeReference<List<CustomAttributeMapping>>() {});

        // Filter out invalid entries (e.g., comment-only entries)
        return result.stream()
                .filter(CustomAttributeMapping::isValid)
                .collect(Collectors.toList());
    }

    // === Index Building ===

    private Map<String, CustomAttributeMapping> buildScimPathIndex() {
        Map<String, CustomAttributeMapping> index = new HashMap<>();
        for (CustomAttributeMapping m : mappings) {
            // Index by simple SCIM path (e.g., "title", "name.middleName", "employeeNumber")
            index.put(m.getScimPath(), m);
        }
        return Collections.unmodifiableMap(index);
    }

    private Map<String, CustomAttributeMapping> buildFullScimPathIndex() {
        Map<String, CustomAttributeMapping> index = new HashMap<>();
        for (CustomAttributeMapping m : mappings) {
            // Index by full path (includes schema URN for enterprise attrs)
            index.put(m.getFullScimPath(), m);
        }
        return Collections.unmodifiableMap(index);
    }

    private Map<String, CustomAttributeMapping> buildPingIdmIndex() {
        Map<String, CustomAttributeMapping> index = new HashMap<>();
        for (CustomAttributeMapping m : mappings) {
            index.put(m.getPingIdmAttribute(), m);
        }
        return Collections.unmodifiableMap(index);
    }

    // === Public API ===

    /**
     * Get all configured mappings.
     *
     * @return unmodifiable list of all mappings
     */
    public List<CustomAttributeMapping> getAllMappings() {
        return Collections.unmodifiableList(mappings);
    }

    /**
     * Get mappings for Core User schema attributes only.
     *
     * @return list of mappings where scimSchema is the Core User URN
     */
    public List<CustomAttributeMapping> getCoreUserMappings() {
        return mappings.stream()
                .filter(CustomAttributeMapping::isCoreUserAttribute)
                .collect(Collectors.toList());
    }

    /**
     * Get mappings for Enterprise User extension attributes only.
     *
     * @return list of mappings where scimSchema is the Enterprise User URN
     */
    public List<CustomAttributeMapping> getEnterpriseUserMappings() {
        return mappings.stream()
                .filter(CustomAttributeMapping::isEnterpriseExtension)
                .collect(Collectors.toList());
    }

    /**
     * Get mappings for nested attributes (e.g., name.middleName).
     *
     * @return list of mappings with nested SCIM paths
     */
    public List<CustomAttributeMapping> getNestedMappings() {
        return mappings.stream()
                .filter(CustomAttributeMapping::isNested)
                .collect(Collectors.toList());
    }

    /**
     * Get mappings for top-level (non-nested) attributes.
     *
     * @return list of mappings with simple SCIM paths
     */
    public List<CustomAttributeMapping> getTopLevelMappings() {
        return mappings.stream()
                .filter(m -> !m.isNested())
                .collect(Collectors.toList());
    }

    /**
     * Lookup a mapping by its SCIM path.
     *
     * @param scimPath the SCIM attribute path (e.g., "title", "name.middleName")
     * @return optional containing the mapping if found
     */
    public Optional<CustomAttributeMapping> getByScimPath(String scimPath) {
        return Optional.ofNullable(byScimPath.get(scimPath));
    }

    /**
     * Lookup a mapping by its full SCIM path (includes schema URN for enterprise attrs).
     *
     * @param fullScimPath the full SCIM path (e.g., "title" or
     *                     "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:employeeNumber")
     * @return optional containing the mapping if found
     */
    public Optional<CustomAttributeMapping> getByFullScimPath(String fullScimPath) {
        return Optional.ofNullable(byFullScimPath.get(fullScimPath));
    }

    /**
     * Lookup a mapping by the PingIDM attribute name.
     *
     * @param pingIdmAttribute the PingIDM attribute name (e.g., "frIndexedString1")
     * @return optional containing the mapping if found
     */
    public Optional<CustomAttributeMapping> getByPingIdmAttribute(String pingIdmAttribute) {
        return Optional.ofNullable(byPingIdmAttribute.get(pingIdmAttribute));
    }

    /**
     * Check if any custom mappings are configured.
     *
     * @return true if at least one mapping is configured
     */
    public boolean hasCustomMappings() {
        return !mappings.isEmpty();
    }

    /**
     * Check if enterprise extension mappings are configured.
     *
     * @return true if at least one enterprise extension mapping exists
     */
    public boolean hasEnterpriseExtension() {
        return mappings.stream().anyMatch(CustomAttributeMapping::isEnterpriseExtension);
    }

    /**
     * Get the set of all PingIDM attribute names that have mappings.
     * Useful for requesting specific fields from PingIDM.
     *
     * @return set of PingIDM attribute names
     */
    public Set<String> getMappedPingIdmAttributes() {
        return byPingIdmAttribute.keySet();
    }

    /**
     * Get the ObjectMapper used for JSON processing.
     *
     * @return the configured ObjectMapper
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}