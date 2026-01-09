package com.pingidentity.p1aic.scim.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.client.PingIdmRestClient;
import com.pingidentity.p1aic.scim.mapping.UserAttributeMapper;
import com.pingidentity.p1aic.scim.service.PingIdmUserService;
import com.unboundid.scim2.common.GenericScimResource;
import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.messages.ListResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PingIdmUserService.
 *
 * Tests the service layer that handles CRUD operations for users,
 * including interactions with PingIDM REST API and attribute mapping.
 */
@ExtendWith(MockitoExtension.class)
class PingIdmUserServiceTest {

    @Mock
    private PingIdmRestClient restClient;

    @InjectMocks
    private PingIdmUserService userService;

    private ObjectMapper objectMapper;
    private UserAttributeMapper attributeMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        attributeMapper = new UserAttributeMapper();

        // Configure mock rest client with default endpoint
        when(restClient.getManagedUsersEndpoint()).thenReturn("https://idm.example.com/openidm/managed/alpha_user");
    }

    @Test
    @DisplayName("Should successfully create a user")
    void testCreateUser_Success() throws Exception {
        // Given: A SCIM user to create
        ObjectNode scimNode = objectMapper.createObjectNode();
        scimNode.put("userName", "john.doe");
        scimNode.put("displayName", "John Doe");
        scimNode.put("active", true);
        GenericScimResource scimUser = new GenericScimResource(scimNode);

        // Mock PingIDM response
        ObjectNode idmResponse = objectMapper.createObjectNode();
        idmResponse.put("_id", "user123");
        idmResponse.put("_rev", "1");
        idmResponse.put("userName", "john.doe");
        idmResponse.put("displayName", "John Doe");
        idmResponse.put("accountStatus", "active");

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(Response.Status.CREATED.getStatusCode());
        when(mockResponse.readEntity(String.class)).thenReturn(objectMapper.writeValueAsString(idmResponse));

        when(restClient.postWithAction(anyString(), eq("create"), anyString())).thenReturn(mockResponse);

        // When: Creating the user
        GenericScimResource result = userService.createUser(scimUser);

        // Then: User should be created successfully
        assertThat(result).isNotNull();
        ObjectNode resultNode = result.asGenericScimResource().getObjectNode();
        assertThat(resultNode.get("id").asText()).isEqualTo("user123");
        assertThat(resultNode.get("userName").asText()).isEqualTo("john.doe");

        verify(restClient).postWithAction(anyString(), eq("create"), anyString());
    }

    @Test
    @DisplayName("Should throw BadRequestException when create fails")
    void testCreateUser_Failure() throws Exception {
        // Given: A SCIM user to create
        ObjectNode scimNode = objectMapper.createObjectNode();
        scimNode.put("userName", "john.doe");
        GenericScimResource scimUser = new GenericScimResource(scimNode);

        // Mock PingIDM error response
        ObjectNode errorResponse = objectMapper.createObjectNode();
        errorResponse.put("message", "User already exists");

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(Response.Status.CONFLICT.getStatusCode());
        when(mockResponse.readEntity(String.class)).thenReturn(objectMapper.writeValueAsString(errorResponse));

        when(restClient.postWithAction(anyString(), eq("create"), anyString())).thenReturn(mockResponse);

        // When/Then: Creating the user should throw exception
        assertThatThrownBy(() -> userService.createUser(scimUser))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("User already exists");
    }

    @Test
    @DisplayName("Should successfully get a user by ID")
    void testGetUser_Success() throws Exception {
        // Given: A user ID
        String userId = "user123";

        // Mock PingIDM response
        ObjectNode idmResponse = objectMapper.createObjectNode();
        idmResponse.put("_id", userId);
        idmResponse.put("_rev", "1");
        idmResponse.put("userName", "john.doe");
        idmResponse.put("displayName", "John Doe");
        idmResponse.put("accountStatus", "active");

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
        when(mockResponse.readEntity(String.class)).thenReturn(objectMapper.writeValueAsString(idmResponse));

        when(restClient.get(contains(userId))).thenReturn(mockResponse);

        // When: Getting the user
        GenericScimResource result = userService.getUser(userId);

        // Then: User should be retrieved successfully
        assertThat(result).isNotNull();
        ObjectNode resultNode = result.asGenericScimResource().getObjectNode();
        assertThat(resultNode.get("id").asText()).isEqualTo(userId);
        assertThat(resultNode.get("userName").asText()).isEqualTo("john.doe");

        verify(restClient).get(contains(userId));
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user not found")
    void testGetUser_NotFound() throws Exception {
        // Given: A non-existent user ID
        String userId = "nonexistent";

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(Response.Status.NOT_FOUND.getStatusCode());

        when(restClient.get(contains(userId))).thenReturn(mockResponse);

        // When/Then: Getting the user should throw exception
        assertThatThrownBy(() -> userService.getUser(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("Should successfully update a user")
    void testUpdateUser_Success() throws Exception {
        // Given: A user ID and updated SCIM user
        String userId = "user123";
        ObjectNode scimNode = objectMapper.createObjectNode();
        scimNode.put("userName", "john.doe");
        scimNode.put("displayName", "John Doe Updated");
        GenericScimResource scimUser = new GenericScimResource(scimNode);

        // Mock PingIDM response
        ObjectNode idmResponse = objectMapper.createObjectNode();
        idmResponse.put("_id", userId);
        idmResponse.put("_rev", "2");
        idmResponse.put("userName", "john.doe");
        idmResponse.put("displayName", "John Doe Updated");
        idmResponse.put("accountStatus", "active");

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
        when(mockResponse.readEntity(String.class)).thenReturn(objectMapper.writeValueAsString(idmResponse));

        when(restClient.put(contains(userId), anyString())).thenReturn(mockResponse);

        // When: Updating the user
        GenericScimResource result = userService.updateUser(userId, scimUser, null);

        // Then: User should be updated successfully
        assertThat(result).isNotNull();
        ObjectNode resultNode = result.asGenericScimResource().getObjectNode();
        assertThat(resultNode.get("displayName").asText()).isEqualTo("John Doe Updated");

        verify(restClient).put(contains(userId), anyString());
    }

    @Test
    @DisplayName("Should successfully update a user with revision")
    void testUpdateUser_WithRevision() throws Exception {
        // Given: A user ID, updated SCIM user, and revision
        String userId = "user123";
        String revision = "1";
        ObjectNode scimNode = objectMapper.createObjectNode();
        scimNode.put("userName", "john.doe");
        GenericScimResource scimUser = new GenericScimResource(scimNode);

        // Mock PingIDM response
        ObjectNode idmResponse = objectMapper.createObjectNode();
        idmResponse.put("_id", userId);
        idmResponse.put("_rev", "2");
        idmResponse.put("userName", "john.doe");

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
        when(mockResponse.readEntity(String.class)).thenReturn(objectMapper.writeValueAsString(idmResponse));

        when(restClient.put(contains(userId), anyString(), eq(revision))).thenReturn(mockResponse);

        // When: Updating the user with revision
        userService.updateUser(userId, scimUser, revision);

        // Then: PUT should be called with revision
        verify(restClient).put(contains(userId), anyString(), eq(revision));
    }

    @Test
    @DisplayName("Should successfully patch a user")
    void testPatchUser_Success() throws Exception {
        // Given: A user ID and patch operations
        String userId = "user123";
        String patchOps = "[{\"op\":\"replace\",\"path\":\"displayName\",\"value\":\"Updated Name\"}]";

        // Mock PingIDM response
        ObjectNode idmResponse = objectMapper.createObjectNode();
        idmResponse.put("_id", userId);
        idmResponse.put("_rev", "2");
        idmResponse.put("userName", "john.doe");
        idmResponse.put("displayName", "Updated Name");

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
        when(mockResponse.readEntity(String.class)).thenReturn(objectMapper.writeValueAsString(idmResponse));

        when(restClient.patch(contains(userId), eq(patchOps))).thenReturn(mockResponse);

        // When: Patching the user
        GenericScimResource result = userService.patchUser(userId, patchOps, null);

        // Then: User should be patched successfully
        assertThat(result).isNotNull();
        verify(restClient).patch(contains(userId), eq(patchOps));
    }

    @Test
    @DisplayName("Should successfully delete a user")
    void testDeleteUser_Success() throws Exception {
        // Given: A user ID
        String userId = "user123";

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(Response.Status.NO_CONTENT.getStatusCode());

        when(restClient.delete(contains(userId))).thenReturn(mockResponse);

        // When: Deleting the user
        userService.deleteUser(userId, null);

        // Then: User should be deleted successfully
        verify(restClient).delete(contains(userId));
    }

    @Test
    @DisplayName("Should successfully delete a user with revision")
    void testDeleteUser_WithRevision() throws Exception {
        // Given: A user ID and revision
        String userId = "user123";
        String revision = "1";

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(Response.Status.NO_CONTENT.getStatusCode());

        when(restClient.delete(contains(userId), eq(revision))).thenReturn(mockResponse);

        // When: Deleting the user with revision
        userService.deleteUser(userId, revision);

        // Then: DELETE should be called with revision
        verify(restClient).delete(contains(userId), eq(revision));
    }

    @Test
    @DisplayName("Should successfully search users without filter")
    void testSearchUsers_NoFilter() throws Exception {
        // Given: No filter, startIndex=1, count=10
        int startIndex = 1;
        int count = 10;

        // Mock PingIDM response
        ObjectNode queryResponse = objectMapper.createObjectNode();
        queryResponse.put("resultCount", 2);

        ArrayNode results = objectMapper.createArrayNode();

        ObjectNode user1 = objectMapper.createObjectNode();
        user1.put("_id", "user1");
        user1.put("userName", "john.doe");
        results.add(user1);

        ObjectNode user2 = objectMapper.createObjectNode();
        user2.put("_id", "user2");
        user2.put("userName", "jane.doe");
        results.add(user2);

        queryResponse.set("result", results);

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
        when(mockResponse.readEntity(String.class)).thenReturn(objectMapper.writeValueAsString(queryResponse));

        when(restClient.get(anyString(), any(String[].class))).thenReturn(mockResponse);

        // When: Searching users
        ListResponse<GenericScimResource> result = userService.searchUsers(null, startIndex, count);

        // Then: Results should be returned
        assertThat(result).isNotNull();
        assertThat(result.getTotalResults()).isEqualTo(2);
        assertThat(result.getResources()).hasSize(2);
        assertThat(result.getStartIndex()).isEqualTo(startIndex);

        verify(restClient).get(anyString(), any(String[].class));
    }

    @Test
    @DisplayName("Should successfully search users with filter")
    void testSearchUsers_WithFilter() throws Exception {
        // Given: A query filter
        String queryFilter = "userName eq \"john.doe\"";
        int startIndex = 1;
        int count = 10;

        // Mock PingIDM response
        ObjectNode queryResponse = objectMapper.createObjectNode();
        queryResponse.put("resultCount", 1);

        ArrayNode results = objectMapper.createArrayNode();
        ObjectNode user1 = objectMapper.createObjectNode();
        user1.put("_id", "user1");
        user1.put("userName", "john.doe");
        results.add(user1);

        queryResponse.set("result", results);

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
        when(mockResponse.readEntity(String.class)).thenReturn(objectMapper.writeValueAsString(queryResponse));

        when(restClient.get(anyString(), any(String[].class))).thenReturn(mockResponse);

        // When: Searching users with filter
        ListResponse<GenericScimResource> result = userService.searchUsers(queryFilter, startIndex, count);

        // Then: Filtered results should be returned
        assertThat(result).isNotNull();
        assertThat(result.getTotalResults()).isEqualTo(1);
        assertThat(result.getResources()).hasSize(1);
    }

    @Test
    @DisplayName("Should convert SCIM pagination to PingIDM pagination")
    void testSearchUsers_PaginationConversion() throws Exception {
        // Given: SCIM pagination (1-based startIndex)
        int scimStartIndex = 11; // SCIM uses 1-based indexing
        int count = 10;

        // Mock PingIDM response
        ObjectNode queryResponse = objectMapper.createObjectNode();
        queryResponse.put("resultCount", 0);
        queryResponse.set("result", objectMapper.createArrayNode());

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
        when(mockResponse.readEntity(String.class)).thenReturn(objectMapper.writeValueAsString(queryResponse));

        when(restClient.get(anyString(), any(String[].class))).thenReturn(mockResponse);

        // When: Searching users
        userService.searchUsers(null, scimStartIndex, count);

        // Then: Should convert to 0-based PingIDM offset (10)
        verify(restClient).get(anyString(), argThat(params -> {
            for (int i = 0; i < params.length - 1; i++) {
                if ("_pagedResultsOffset".equals(params[i])) {
                    return "10".equals(params[i + 1]); // startIndex 11 -> offset 10
                }
            }
            return false;
        }));
    }

    @Test
    @DisplayName("Should handle empty search results")
    void testSearchUsers_EmptyResults() throws Exception {
        // Given: Search parameters
        int startIndex = 1;
        int count = 10;

        // Mock PingIDM response with no results
        ObjectNode queryResponse = objectMapper.createObjectNode();
        queryResponse.put("resultCount", 0);
        queryResponse.set("result", objectMapper.createArrayNode());

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
        when(mockResponse.readEntity(String.class)).thenReturn(objectMapper.writeValueAsString(queryResponse));

        when(restClient.get(anyString(), any(String[].class))).thenReturn(mockResponse);

        // When: Searching users
        ListResponse<GenericScimResource> result = userService.searchUsers(null, startIndex, count);

        // Then: Empty results should be returned
        assertThat(result).isNotNull();
        assertThat(result.getTotalResults()).isEqualTo(0);
        assertThat(result.getResources()).isEmpty();
    }

    @Test
    @DisplayName("Should throw BadRequestException when search fails")
    void testSearchUsers_Failure() throws Exception {
        // Given: Search parameters
        int startIndex = 1;
        int count = 10;

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(Response.Status.BAD_REQUEST.getStatusCode());
        when(mockResponse.readEntity(String.class)).thenReturn("{\"message\":\"Invalid query filter\"}");

        when(restClient.get(anyString(), any(String[].class))).thenReturn(mockResponse);

        // When/Then: Searching should throw exception
        assertThatThrownBy(() -> userService.searchUsers("invalid filter", startIndex, count))
                .isInstanceOf(BadRequestException.class);
    }
}
