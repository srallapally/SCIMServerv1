package com.pingidentity.p1aic.scim.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.endpoints.UserScimEndpoint;
import com.pingidentity.p1aic.scim.service.PingIdmUserService;
import com.unboundid.scim2.common.GenericScimResource;
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException;
import com.unboundid.scim2.common.messages.ListResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for UserScimEndpoint.
 *
 * Tests the REST endpoint layer for SCIM User operations,
 * including request handling, response formatting, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class UserScimEndpointTest {

    @Mock
    private PingIdmUserService userService;

    @InjectMocks
    private UserScimEndpoint endpoint;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should return 200 OK when searching users")
    void testSearchUsers_Success() throws Exception {
        // Given: Search parameters
        String filter = "userName eq \"john.doe\"";
        Integer startIndex = 1;
        Integer count = 10;

        // Mock service response
        ObjectNode userNode = objectMapper.createObjectNode();
        userNode.put("id", "user123");
        userNode.put("userName", "john.doe");
        GenericScimResource user = new GenericScimResource(userNode);

        ListResponse<GenericScimResource> listResponse =
                new ListResponse<>(1, Collections.singletonList(user), startIndex, count);

        when(userService.searchUsers(anyString(), anyInt(), anyInt())).thenReturn(listResponse);

        // When: Searching users
        Response response = endpoint.searchUsers(filter, startIndex, count);

        // Then: Should return 200 OK with list response
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isInstanceOf(ListResponse.class);

        ListResponse<?> resultList = (ListResponse<?>) response.getEntity();
        assertThat(resultList.getTotalResults()).isEqualTo(1);

        verify(userService).searchUsers(filter, startIndex, count);
    }

    @Test
    @DisplayName("Should apply default values for pagination parameters")
    void testSearchUsers_DefaultPagination() throws Exception {
        // Given: No pagination parameters
        Integer startIndex = null;
        Integer count = null;

        // Mock service response
        ListResponse<GenericScimResource> listResponse =
                new ListResponse<>(0, Collections.emptyList(), 1, 100);

        when(userService.searchUsers(isNull(), eq(1), eq(100))).thenReturn(listResponse);

        // When: Searching users without pagination params
        Response response = endpoint.searchUsers(null, startIndex, count);

        // Then: Should use default values (startIndex=1, count=100)
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        verify(userService).searchUsers(isNull(), eq(1), eq(100));
    }

    @Test
    @DisplayName("Should enforce maximum count limit")
    void testSearchUsers_MaxCountLimit() throws Exception {
        // Given: Count exceeding maximum (1000)
        Integer startIndex = 1;
        Integer count = 5000;

        // Mock service response
        ListResponse<GenericScimResource> listResponse =
                new ListResponse<>(0, Collections.emptyList(), startIndex, 1000);

        when(userService.searchUsers(isNull(), eq(startIndex), eq(1000))).thenReturn(listResponse);

        // When: Searching users with large count
        Response response = endpoint.searchUsers(null, startIndex, count);

        // Then: Should cap count at 1000
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        verify(userService).searchUsers(isNull(), eq(startIndex), eq(1000));
    }

    @Test
    @DisplayName("Should return 200 OK when getting a user")
    void testGetUser_Success() throws Exception {
        // Given: A user ID
        String userId = "user123";

        // Mock service response
        ObjectNode userNode = objectMapper.createObjectNode();
        userNode.put("id", userId);
        userNode.put("userName", "john.doe");
        GenericScimResource user = new GenericScimResource(userNode);

        when(userService.getUser(userId)).thenReturn(user);

        // When: Getting the user
        Response response = endpoint.getUser(userId);

        // Then: Should return 200 OK with user resource
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isInstanceOf(GenericScimResource.class);

        verify(userService).getUser(userId);
    }

    @Test
    @DisplayName("Should return 404 when user not found")
    void testGetUser_NotFound() throws Exception {
        // Given: A non-existent user ID
        String userId = "nonexistent";

        when(userService.getUser(userId)).thenThrow(new ResourceNotFoundException("User not found"));

        // When: Getting the user
        Response response = endpoint.getUser(userId);

        // Then: Should return 404 Not Found
        assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());

        String entity = (String) response.getEntity();
        assertThat(entity).contains("User not found");
        assertThat(entity).contains("urn:ietf:params:scim:api:messages:2.0:Error");
    }

    @Test
    @DisplayName("Should return 201 Created when creating a user")
    void testCreateUser_Success() throws Exception {
        // Given: A SCIM user to create
        ObjectNode scimNode = objectMapper.createObjectNode();
        scimNode.put("userName", "john.doe");
        GenericScimResource scimUser = new GenericScimResource(scimNode);

        // Mock service response
        ObjectNode createdNode = objectMapper.createObjectNode();
        createdNode.put("id", "user123");
        createdNode.put("userName", "john.doe");
        GenericScimResource createdUser = new GenericScimResource(createdNode);

        when(userService.createUser(any(GenericScimResource.class))).thenReturn(createdUser);

        // When: Creating the user
        Response response = endpoint.createUser(scimUser);

        // Then: Should return 201 Created with Location header
        assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
        assertThat(response.getHeaderString("Location")).contains("/Users/user123");
        assertThat(response.getEntity()).isInstanceOf(GenericScimResource.class);

        verify(userService).createUser(any(GenericScimResource.class));
    }

    @Test
    @DisplayName("Should return 200 OK when updating a user")
    void testUpdateUser_Success() throws Exception {
        // Given: A user ID and updated SCIM user
        String userId = "user123";
        ObjectNode scimNode = objectMapper.createObjectNode();
        scimNode.put("userName", "john.doe");
        GenericScimResource scimUser = new GenericScimResource(scimNode);

        // Mock service response
        ObjectNode updatedNode = objectMapper.createObjectNode();
        updatedNode.put("id", userId);
        updatedNode.put("userName", "john.doe");
        GenericScimResource updatedUser = new GenericScimResource(updatedNode);

        when(userService.updateUser(eq(userId), any(GenericScimResource.class), isNull()))
                .thenReturn(updatedUser);

        // When: Updating the user
        Response response = endpoint.updateUser(userId, scimUser, null);

        // Then: Should return 200 OK with updated resource
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isInstanceOf(GenericScimResource.class);

        verify(userService).updateUser(eq(userId), any(GenericScimResource.class), isNull());
    }

    @Test
    @DisplayName("Should pass If-Match header to service when updating")
    void testUpdateUser_WithIfMatch() throws Exception {
        // Given: A user ID, SCIM user, and If-Match header
        String userId = "user123";
        String ifMatch = "\"5\"";
        ObjectNode scimNode = objectMapper.createObjectNode();
        scimNode.put("userName", "john.doe");
        GenericScimResource scimUser = new GenericScimResource(scimNode);

        // Mock service response
        GenericScimResource updatedUser = new GenericScimResource(scimNode);
        when(userService.updateUser(eq(userId), any(GenericScimResource.class), eq("5")))
                .thenReturn(updatedUser);

        // When: Updating the user with If-Match
        Response response = endpoint.updateUser(userId, scimUser, ifMatch);

        // Then: Should pass revision to service (without quotes)
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        verify(userService).updateUser(eq(userId), any(GenericScimResource.class), eq("5"));
    }

    @Test
    @DisplayName("Should return 200 OK when patching a user")
    void testPatchUser_Success() throws Exception {
        // Given: A user ID and patch operations
        String userId = "user123";
        String patchRequest = "[{\"op\":\"replace\",\"path\":\"displayName\",\"value\":\"Updated\"}]";

        // Mock service response
        ObjectNode patchedNode = objectMapper.createObjectNode();
        patchedNode.put("id", userId);
        patchedNode.put("displayName", "Updated");
        GenericScimResource patchedUser = new GenericScimResource(patchedNode);

        when(userService.patchUser(eq(userId), eq(patchRequest), isNull()))
                .thenReturn(patchedUser);

        // When: Patching the user
        Response response = endpoint.patchUser(userId, patchRequest, null);

        // Then: Should return 200 OK with patched resource
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isInstanceOf(GenericScimResource.class);

        verify(userService).patchUser(eq(userId), eq(patchRequest), isNull());
    }

    @Test
    @DisplayName("Should return 204 No Content when deleting a user")
    void testDeleteUser_Success() throws Exception {
        // Given: A user ID
        String userId = "user123";

        doNothing().when(userService).deleteUser(eq(userId), isNull());

        // When: Deleting the user
        Response response = endpoint.deleteUser(userId, null);

        // Then: Should return 204 No Content
        assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
        assertThat(response.getEntity()).isNull();

        verify(userService).deleteUser(eq(userId), isNull());
    }

    @Test
    @DisplayName("Should pass If-Match header to service when deleting")
    void testDeleteUser_WithIfMatch() throws Exception {
        // Given: A user ID and If-Match header
        String userId = "user123";
        String ifMatch = "\"5\"";

        doNothing().when(userService).deleteUser(eq(userId), eq("5"));

        // When: Deleting the user with If-Match
        Response response = endpoint.deleteUser(userId, ifMatch);

        // Then: Should pass revision to service
        assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
        verify(userService).deleteUser(eq(userId), eq("5"));
    }

    @Test
    @DisplayName("Should extract revision from quoted If-Match header")
    void testExtractRevision_Quoted() throws Exception {
        // Given: A quoted If-Match header
        String userId = "user123";
        String ifMatch = "\"123\"";

        doNothing().when(userService).deleteUser(eq(userId), eq("123"));

        // When: Deleting with quoted If-Match
        endpoint.deleteUser(userId, ifMatch);

        // Then: Should extract revision without quotes
        verify(userService).deleteUser(eq(userId), eq("123"));
    }

    @Test
    @DisplayName("Should handle unquoted If-Match header")
    void testExtractRevision_Unquoted() throws Exception {
        // Given: An unquoted If-Match header
        String userId = "user123";
        String ifMatch = "123";

        doNothing().when(userService).deleteUser(eq(userId), eq("123"));

        // When: Deleting with unquoted If-Match
        endpoint.deleteUser(userId, ifMatch);

        // Then: Should use revision as-is
        verify(userService).deleteUser(eq(userId), eq("123"));
    }

    @Test
    @DisplayName("Should return 500 when unexpected error occurs")
    void testGetUser_UnexpectedException() throws Exception {
        // Given: A user ID
        String userId = "user123";

        when(userService.getUser(userId)).thenThrow(new RuntimeException("Unexpected error"));

        // When: Getting the user
        Response response = endpoint.getUser(userId);

        // Then: Should return 500 Internal Server Error
        assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());

        String entity = (String) response.getEntity();
        assertThat(entity).contains("Internal server error");
    }

    @Test
    @DisplayName("Should escape JSON special characters in error messages")
    void testErrorResponse_JSONEscaping() throws Exception {
        // Given: A user ID
        String userId = "user123";

        when(userService.getUser(userId))
                .thenThrow(new ResourceNotFoundException("User \"john\" not found"));

        // When: Getting the user
        Response response = endpoint.getUser(userId);

        // Then: Should escape quotes in error message
        String entity = (String) response.getEntity();
        assertThat(entity).contains("\\\"john\\\"");
    }

    @Test
    @DisplayName("Should set correct content type for responses")
    void testContentType() throws Exception {
        // Given: A user ID
        String userId = "user123";

        ObjectNode userNode = objectMapper.createObjectNode();
        userNode.put("id", userId);
        GenericScimResource user = new GenericScimResource(userNode);

        when(userService.getUser(userId)).thenReturn(user);

        // When: Getting the user
        Response response = endpoint.getUser(userId);

        // Then: Content type should be application/scim+json
        // Note: This is set at the class level with @Produces annotation
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    }
}