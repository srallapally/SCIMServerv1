package com.pingidentity.p1aic.scim.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.config.PingIdmConfigService;
import com.unboundid.scim2.common.GenericScimResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DynamicSchemaManager.
 * 
 * Tests the dynamic schema generation from PingIDM configuration,
 * including schema caching, attribute discovery, and refresh functionality.
 */
@ExtendWith(MockitoExtension.class)
class DynamicSchemaManagerTest {

    @Mock
    private PingIdmConfigService configService;

    @Mock
    private ScimSchemaBuilder schemaBuilder;

    @InjectMocks
    private DynamicSchemaManager schemaManager;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should initialize schemas on startup")
    void testInitialize_Success() throws Exception {
        // Given: PingIDM configuration for user and role
        ObjectNode userConfig = createMockUserConfig();
        ObjectNode roleConfig = createMockRoleConfig();
        ObjectNode userProperties = objectMapper.createObjectNode();
        ObjectNode roleProperties = objectMapper.createObjectNode();

        when(configService.getUserConfig(anyString())).thenReturn(userConfig);
        when(configService.getRoleConfig(anyString())).thenReturn(roleConfig);
        when(configService.getPropertiesDefinition(userConfig)).thenReturn(userProperties);
        when(configService.getPropertiesDefinition(roleConfig)).thenReturn(roleProperties);

        // Mock schema builder responses
        GenericScimResource userSchema = createMockUserSchema();
        GenericScimResource roleSchema = createMockRoleSchema();
        when(schemaBuilder.buildUserSchema(userProperties)).thenReturn(userSchema);
        when(schemaBuilder.buildGroupSchema(roleProperties)).thenReturn(roleSchema);
        when(schemaBuilder.extractAttributeDefinitions(any())).thenReturn(List.of());

        // When: Initializing the schema manager
        schemaManager.initialize();

        // Then: Schemas should be built and cached
        assertThat(schemaManager.isInitialized()).isTrue();
        assertThat(schemaManager.getSchemaCount()).isEqualTo(2);
        
        verify(configService).getUserConfig(anyString());
        verify(configService).getRoleConfig(anyString());
        verify(schemaBuilder).buildUserSchema(userProperties);
        verify(schemaBuilder).buildGroupSchema(roleProperties);
    }

    @Test
    @DisplayName("Should retrieve User schema by URN")
    void testGetSchema_UserSchema() throws Exception {
        // Given: Initialized schema manager
        setupInitializedSchemaManager();

        // When: Getting User schema
        GenericScimResource schema = schemaManager.getSchema(ScimSchemaUrns.CORE_USER_SCHEMA);

        // Then: Should return User schema
        assertThat(schema).isNotNull();
        ObjectNode schemaNode = schema.asGenericScimResource().getObjectNode();
        assertThat(schemaNode.get("id").asText()).isEqualTo(ScimSchemaUrns.CORE_USER_SCHEMA);
    }

    @Test
    @DisplayName("Should retrieve Group schema by URN")
    void testGetSchema_GroupSchema() throws Exception {
        // Given: Initialized schema manager
        setupInitializedSchemaManager();

        // When: Getting Group schema
        GenericScimResource schema = schemaManager.getSchema(ScimSchemaUrns.CORE_GROUP_SCHEMA);

        // Then: Should return Group schema
        assertThat(schema).isNotNull();
        ObjectNode schemaNode = schema.asGenericScimResource().getObjectNode();
        assertThat(schemaNode.get("id").asText()).isEqualTo(ScimSchemaUrns.CORE_GROUP_SCHEMA);
    }

    @Test
    @DisplayName("Should return null for non-existent schema")
    void testGetSchema_NotFound() throws Exception {
        // Given: Initialized schema manager
        setupInitializedSchemaManager();

        // When: Getting non-existent schema
        GenericScimResource schema = schemaManager.getSchema("urn:unknown:schema");

        // Then: Should return null
        assertThat(schema).isNull();
    }

    @Test
    @DisplayName("Should retrieve all schemas")
    void testGetAllSchemas() throws Exception {
        // Given: Initialized schema manager
        setupInitializedSchemaManager();

        // When: Getting all schemas
        List<GenericScimResource> schemas = schemaManager.getAllSchemas();

        // Then: Should return all cached schemas
        assertThat(schemas).isNotNull();
        assertThat(schemas).hasSize(2);
    }

    @Test
    @DisplayName("Should check if schema exists")
    void testHasSchema() throws Exception {
        // Given: Initialized schema manager
        setupInitializedSchemaManager();

        // When/Then: Checking schema existence
        assertThat(schemaManager.hasSchema(ScimSchemaUrns.CORE_USER_SCHEMA)).isTrue();
        assertThat(schemaManager.hasSchema(ScimSchemaUrns.CORE_GROUP_SCHEMA)).isTrue();
        assertThat(schemaManager.hasSchema("urn:unknown:schema")).isFalse();
    }

    @Test
    @DisplayName("Should retrieve User schema directly")
    void testGetUserSchema() throws Exception {
        // Given: Initialized schema manager
        setupInitializedSchemaManager();

        // When: Getting User schema
        GenericScimResource schema = schemaManager.getUserSchema();

        // Then: Should return User schema
        assertThat(schema).isNotNull();
        ObjectNode schemaNode = schema.asGenericScimResource().getObjectNode();
        assertThat(schemaNode.get("id").asText()).isEqualTo(ScimSchemaUrns.CORE_USER_SCHEMA);
    }

    @Test
    @DisplayName("Should retrieve Group schema directly")
    void testGetGroupSchema() throws Exception {
        // Given: Initialized schema manager
        setupInitializedSchemaManager();

        // When: Getting Group schema
        GenericScimResource schema = schemaManager.getGroupSchema();

        // Then: Should return Group schema
        assertThat(schema).isNotNull();
        ObjectNode schemaNode = schema.asGenericScimResource().getObjectNode();
        assertThat(schemaNode.get("id").asText()).isEqualTo(ScimSchemaUrns.CORE_GROUP_SCHEMA);
    }

    @Test
    @DisplayName("Should return correct schema count")
    void testGetSchemaCount() throws Exception {
        // Given: Initialized schema manager
        setupInitializedSchemaManager();

        // When: Getting schema count
        int count = schemaManager.getSchemaCount();

        // Then: Should return 2 (User + Group)
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("Should return cached schema URNs")
    void testGetCachedSchemaUrns() throws Exception {
        // Given: Initialized schema manager
        setupInitializedSchemaManager();

        // When: Getting cached schema URNs
        List<String> urns = schemaManager.getCachedSchemaUrns();

        // Then: Should return URNs for cached schemas
        assertThat(urns).isNotNull();
        assertThat(urns).hasSize(2);
        assertThat(urns).contains(ScimSchemaUrns.CORE_USER_SCHEMA);
        assertThat(urns).contains(ScimSchemaUrns.CORE_GROUP_SCHEMA);
    }

    @Test
    @DisplayName("Should refresh schemas on demand")
    void testRefreshSchemas() throws Exception {
        // Given: Initialized schema manager
        setupInitializedSchemaManager();

        // Reset mocks to verify refresh calls
        clearInvocations(configService, schemaBuilder);

        ObjectNode userConfig = createMockUserConfig();
        ObjectNode roleConfig = createMockRoleConfig();
        ObjectNode userProperties = objectMapper.createObjectNode();
        ObjectNode roleProperties = objectMapper.createObjectNode();

        when(configService.getUserConfig(anyString())).thenReturn(userConfig);
        when(configService.getRoleConfig(anyString())).thenReturn(roleConfig);
        when(configService.getPropertiesDefinition(userConfig)).thenReturn(userProperties);
        when(configService.getPropertiesDefinition(roleConfig)).thenReturn(roleProperties);

        GenericScimResource userSchema = createMockUserSchema();
        GenericScimResource roleSchema = createMockRoleSchema();
        when(schemaBuilder.buildUserSchema(userProperties)).thenReturn(userSchema);
        when(schemaBuilder.buildGroupSchema(roleProperties)).thenReturn(roleSchema);
        when(schemaBuilder.extractAttributeDefinitions(any())).thenReturn(List.of());

        // When: Refreshing schemas
        schemaManager.refreshSchemas();

        // Then: Schemas should be rebuilt
        verify(configService, times(1)).getUserConfig(anyString());
        verify(configService, times(1)).getRoleConfig(anyString());
        verify(schemaBuilder, times(1)).buildUserSchema(any());
        verify(schemaBuilder, times(1)).buildGroupSchema(any());
    }

    @Test
    @DisplayName("Should handle missing user configuration gracefully")
    void testInitialize_MissingUserConfig() throws Exception {
        // Given: No user configuration available
        when(configService.getUserConfig(anyString())).thenReturn(null);
        when(configService.getRoleConfig(anyString())).thenReturn(createMockRoleConfig());

        // When: Initializing the schema manager
        schemaManager.initialize();

        // Then: Should not throw exception, but may not have user schema
        assertThat(schemaManager.isInitialized()).isTrue();
    }

    @Test
    @DisplayName("Should handle missing role configuration gracefully")
    void testInitialize_MissingRoleConfig() throws Exception {
        // Given: No role configuration available
        when(configService.getUserConfig(anyString())).thenReturn(createMockUserConfig());
        when(configService.getRoleConfig(anyString())).thenReturn(null);

        // When: Initializing the schema manager
        schemaManager.initialize();

        // Then: Should not throw exception, but may not have group schema
        assertThat(schemaManager.isInitialized()).isTrue();
    }

    @Test
    @DisplayName("Should handle missing properties definition gracefully")
    void testInitialize_MissingProperties() throws Exception {
        // Given: Configuration without properties definition
        ObjectNode userConfig = createMockUserConfig();
        when(configService.getUserConfig(anyString())).thenReturn(userConfig);
        when(configService.getPropertiesDefinition(userConfig)).thenReturn(null);

        // When: Initializing the schema manager
        schemaManager.initialize();

        // Then: Should not throw exception
        assertThat(schemaManager.isInitialized()).isTrue();
    }

    @Test
    @DisplayName("Should not throw exception on initialization failure")
    void testInitialize_Failure() throws Exception {
        // Given: Config service throws exception
        when(configService.getUserConfig(anyString())).thenThrow(new RuntimeException("Connection failed"));

        // When: Initializing the schema manager
        schemaManager.initialize();

        // Then: Should handle exception gracefully (not initialized but no exception thrown)
        // This allows the server to start even if PingIDM is temporarily unavailable
        assertThat(schemaManager.isInitialized()).isFalse();
    }

    @Test
    @DisplayName("Should retrieve attribute definitions for User resource type")
    void testGetAttributeDefinitions_User() throws Exception {
        // Given: Initialized schema manager
        setupInitializedSchemaManager();

        // When: Getting attribute definitions for User
        List<?> attributes = schemaManager.getAttributeDefinitions("User");

        // Then: Should return attribute definitions
        assertThat(attributes).isNotNull();
    }

    @Test
    @DisplayName("Should retrieve attribute definitions for Group resource type")
    void testGetAttributeDefinitions_Group() throws Exception {
        // Given: Initialized schema manager
        setupInitializedSchemaManager();

        // When: Getting attribute definitions for Group
        List<?> attributes = schemaManager.getAttributeDefinitions("Group");

        // Then: Should return attribute definitions
        assertThat(attributes).isNotNull();
    }

    @Test
    @DisplayName("Should return empty list for unknown resource type")
    void testGetAttributeDefinitions_Unknown() throws Exception {
        // Given: Initialized schema manager
        setupInitializedSchemaManager();

        // When: Getting attribute definitions for unknown type
        List<?> attributes = schemaManager.getAttributeDefinitions("Unknown");

        // Then: Should return empty list
        assertThat(attributes).isEmpty();
    }

    // Helper methods

    private void setupInitializedSchemaManager() throws Exception {
        ObjectNode userConfig = createMockUserConfig();
        ObjectNode roleConfig = createMockRoleConfig();
        ObjectNode userProperties = objectMapper.createObjectNode();
        ObjectNode roleProperties = objectMapper.createObjectNode();

        when(configService.getUserConfig(anyString())).thenReturn(userConfig);
        when(configService.getRoleConfig(anyString())).thenReturn(roleConfig);
        when(configService.getPropertiesDefinition(userConfig)).thenReturn(userProperties);
        when(configService.getPropertiesDefinition(roleConfig)).thenReturn(roleProperties);

        GenericScimResource userSchema = createMockUserSchema();
        GenericScimResource roleSchema = createMockRoleSchema();
        when(schemaBuilder.buildUserSchema(userProperties)).thenReturn(userSchema);
        when(schemaBuilder.buildGroupSchema(roleProperties)).thenReturn(roleSchema);
        when(schemaBuilder.extractAttributeDefinitions(any())).thenReturn(List.of());

        schemaManager.initialize();
    }

    private ObjectNode createMockUserConfig() {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("name", "alpha_user");
        return config;
    }

    private ObjectNode createMockRoleConfig() {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("name", "alpha_role");
        return config;
    }

    private GenericScimResource createMockUserSchema() {
        ObjectNode schemaNode = objectMapper.createObjectNode();
        schemaNode.put("id", ScimSchemaUrns.CORE_USER_SCHEMA);
        schemaNode.put("name", "User");
        return new GenericScimResource(schemaNode);
    }

    private GenericScimResource createMockRoleSchema() {
        ObjectNode schemaNode = objectMapper.createObjectNode();
        schemaNode.put("id", ScimSchemaUrns.CORE_GROUP_SCHEMA);
        schemaNode.put("name", "Group");
        return new GenericScimResource(schemaNode);
    }
}
