package com.pingidentity.p1aic.scim.endpoints;

import com.pingidentity.p1aic.scim.service.PingIdmUserService;
import com.unboundid.scim2.common.GenericScimResource;
import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException;
import com.unboundid.scim2.common.messages.ListResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserScimEndpoint.
 *
 * Tests SCIM User endpoint operations including search, get, create, update, patch, and delete.
 */
@ExtendWith(MockitoExtension.class)
class UserScimEndpointTest {

    @Mock
    private PingIdmUserService userService;

    @InjectMocks
    private UserScimEndpoint endpoint;

    @BeforeEach
    void setUp() {
        // MockitoExtension handles initialization
    }

    @Test
    @DisplayName("Should successfully search users with filter")
    void testSearchUsers_Success() throws Exception {
        // Arrange
        List<GenericScimResource> users = new ArrayList<>();
        ListResponse<GenericScimResource> mockResponse = new ListResponse<>(10, users, 1, 100);

        // Mock the 4-parameter searchUsers method
        when(userService.searchUsers(anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(mockResponse);

        // Act
        Response response = endpoint.searchUsers(
                "userName eq \"john.doe\"",
                1,
                100,
                null,  // attributes
                null   // excludedAttributes
        );

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);

        // Verify searchUsers was called with 4 parameters (including "*" for fields)
        ArgumentCaptor<String> queryFilterCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> startIndexCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> countCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> fieldsCaptor = ArgumentCaptor.forClass(String.class);

        verify(userService).searchUsers(
                queryFilterCaptor.capture(),
                startIndexCaptor.capture(),
                countCaptor.capture(),
                fieldsCaptor.capture()
        );

        assertThat(queryFilterCaptor.getValue()).isEqualTo("userName eq \"john.doe\"");
        assertThat(startIndexCaptor.getValue()).isEqualTo(1);
        assertThat(countCaptor.getValue()).isEqualTo(100);
        assertThat(fieldsCaptor.getValue()).isEqualTo("*"); // Default when no attributes specified
    }

    @Test
    @DisplayName("Should convert SCIM attributes to PingIDM fields when attributes parameter provided")
    void testSearchUsers_WithAttributes() throws Exception {
        // Arrange
        List<GenericScimResource> users = new ArrayList<>();
        ListResponse<GenericScimResource> mockResponse = new ListResponse<>(10, users, 1, 100);

        when(userService.searchUsers(anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(mockResponse);

        // Act
        Response response = endpoint.searchUsers(
                null,
                1,
                100,
                "userName,emails,name.givenName",  // attributes parameter
                null   // excludedAttributes
        );

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);

        // Verify the converted fields were passed
        ArgumentCaptor<String> fieldsCaptor = ArgumentCaptor.forClass(String.class);
        verify(userService).searchUsers(
                anyString(),
                anyInt(),
                anyInt(),
                fieldsCaptor.capture()
        );

        // Should include mapped fields plus _id and _rev
        String capturedFields = fieldsCaptor.getValue();
        assertThat(capturedFields).contains("_id");
        assertThat(capturedFields).contains("_rev");
        assertThat(capturedFields).contains("userName");
        assertThat(capturedFields).contains("mail"); // emails -> mail
        assertThat(capturedFields).contains("givenName"); // name.givenName -> givenName
    }

    @Test
    @DisplayName("Should use default pagination values when not specified")
    void testSearchUsers_DefaultPagination() throws Exception {
        // Arrange
        List<GenericScimResource> users = new ArrayList<>();
        ListResponse<GenericScimResource> mockResponse = new ListResponse<>(10, users, 1, 100);

        when(userService.searchUsers(anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(mockResponse);

        // Act - no pagination parameters
        Response response = endpoint.searchUsers(null, null, null, null, null);

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);

        // Verify default values were used
        ArgumentCaptor<Integer> startIndexCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> countCaptor = ArgumentCaptor.forClass(Integer.class);

        verify(userService).searchUsers(
                anyString(),
                startIndexCaptor.capture(),
                countCaptor.capture(),
                anyString()
        );

        assertThat(startIndexCaptor.getValue()).isEqualTo(1);   // Default startIndex
        assertThat(countCaptor.getValue()).isEqualTo(100);      // Default count
    }

    @Test
    @DisplayName("Should cap count at maximum limit (1000)")
    void testSearchUsers_MaxCountLimit() throws Exception {
        // Arrange
        List<GenericScimResource> users = new ArrayList<>();
        ListResponse<GenericScimResource> mockResponse = new ListResponse<>(10, users, 1, 1000);

        when(userService.searchUsers(anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(mockResponse);

        // Act - request 2000 items (exceeds max)
        Response response = endpoint.searchUsers(null, 1, 2000, null, null);

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);

        // Verify count was capped at MAX_COUNT (1000)
        ArgumentCaptor<Integer> countCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(userService).searchUsers(
                anyString(),
                anyInt(),
                countCaptor.capture(),
                anyString()
        );

        assertThat(countCaptor.getValue()).isEqualTo(1000); // Should be capped at max
    }

    @Test
    @DisplayName("Should handle count=0 for count-only queries (RFC 7644)")
    void testSearchUsers_CountOnly() throws Exception {
        // Arrange
        List<GenericScimResource> emptyUsers = new ArrayList<>();
        ListResponse<GenericScimResource> mockResponse = new ListResponse<>(50, emptyUsers, 1, 0);

        when(userService.searchUsers(anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(mockResponse);

        // Act - count=0 should return only totalResults
        Response response = endpoint.searchUsers(null, 1, 0, null, null);

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);

        // Verify count=0 was passed to service (which will use _countOnly optimization)
        ArgumentCaptor<Integer> countCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(userService).searchUsers(
                anyString(),
                anyInt(),
                countCaptor.capture(),
                anyString()
        );

        assertThat(countCaptor.getValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should successfully create user")
    void testCreateUser_Success() throws Exception {
        // Arrange
        String userJson = """
                {
                    "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
                    "userName": "john.doe@example.com",
                    "name": {
                        "givenName": "John",
                        "familyName": "Doe"
                    },
                    "active": true
                }
                """;

        GenericScimResource createdUser = mock(GenericScimResource.class);
        when(createdUser.getId()).thenReturn("user123");
        when(userService.createUser(any(GenericScimResource.class))).thenReturn(createdUser);

        // Act
        Response response = endpoint.createUser(userJson);

        // Assert
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getHeaderString("Location")).contains("/Users/user123");
        verify(userService).createUser(any(GenericScimResource.class));
    }

    @Test
    @DisplayName("Should reject invalid JSON in create user request")
    void testCreateUser_InvalidJson() throws Exception {
        // Arrange
        String invalidJson = "{ invalid json here }";

        // Act
        Response response = endpoint.createUser(invalidJson);

        // Assert
        assertThat(response.getStatus()).isEqualTo(400);
        String responseBody = (String) response.getEntity();
        assertThat(responseBody).contains("Invalid JSON format");
        assertThat(responseBody).contains("urn:ietf:params:scim:api:messages:2.0:Error");
        verify(userService, never()).createUser(any());
    }

    @Test
    @DisplayName("Should successfully create user with custom attributes")
    void testCreateUser_WithCustomAttributes() throws Exception {
        // Arrange
        String userJson = """
                {
                    "schemas": [
                        "urn:ietf:params:scim:schemas:core:2.0:User",
                        "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"
                    ],
                    "userName": "jane.doe@example.com",
                    "title": "Senior Engineer",
                    "timezone": "America/Los_Angeles",
                    "locale": "en-US",
                    "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User": {
                        "employeeNumber": "EMP12345",
                        "costCenter": "Engineering",
                        "department": "Software Development"
                    }
                }
                """;

        GenericScimResource createdUser = mock(GenericScimResource.class);
        when(createdUser.getId()).thenReturn("user456");
        when(userService.createUser(any(GenericScimResource.class))).thenReturn(createdUser);

        // Act
        Response response = endpoint.createUser(userJson);

        // Assert
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getHeaderString("Location")).contains("/Users/user456");
        verify(userService).createUser(any(GenericScimResource.class));
    }

    @Test
    @DisplayName("Should successfully update user")
    void testUpdateUser_Success() throws Exception {
        // Arrange
        String userJson = """
                {
                    "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
                    "userName": "john.doe@example.com",
                    "name": {
                        "givenName": "John",
                        "familyName": "Doe"
                    },
                    "active": true
                }
                """;

        GenericScimResource updatedUser = mock(GenericScimResource.class);
        when(userService.updateUser(eq("user123"), any(GenericScimResource.class), isNull()))
                .thenReturn(updatedUser);

        // Act
        Response response = endpoint.updateUser("user123", userJson, null);

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);
        verify(userService).updateUser(eq("user123"), any(GenericScimResource.class), isNull());
    }

    @Test
    @DisplayName("Should reject invalid JSON in update user request")
    void testUpdateUser_InvalidJson() throws Exception {
        // Arrange
        String invalidJson = "{ not valid json }";

        // Act
        Response response = endpoint.updateUser("user123", invalidJson, null);

        // Assert
        assertThat(response.getStatus()).isEqualTo(400);
        String responseBody = (String) response.getEntity();
        assertThat(responseBody).contains("Invalid JSON format");
        verify(userService, never()).updateUser(anyString(), any(), any());
    }

    @Test
    @DisplayName("Should successfully update user with If-Match header")
    void testUpdateUser_WithIfMatch() throws Exception {
        // Arrange
        String userJson = """
                {
                    "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
                    "userName": "john.doe@example.com",
                    "active": false
                }
                """;

        GenericScimResource updatedUser = mock(GenericScimResource.class);
        when(userService.updateUser(eq("user123"), any(GenericScimResource.class), eq("5")))
                .thenReturn(updatedUser);

        // Act
        Response response = endpoint.updateUser("user123", userJson, "\"5\"");

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);
        verify(userService).updateUser(eq("user123"), any(GenericScimResource.class), eq("5"));
    }

    @Test
    @DisplayName("Should successfully patch user")
    void testPatchUser_Success() throws Exception {
        // Arrange
        String patchJson = """
                {
                    "schemas": ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                    "Operations": [
                        {
                            "op": "replace",
                            "path": "active",
                            "value": false
                        }
                    ]
                }
                """;

        GenericScimResource patchedUser = mock(GenericScimResource.class);
        when(userService.patchUser(eq("user123"), anyString(), isNull()))
                .thenReturn(patchedUser);

        // Act
        Response response = endpoint.patchUser("user123", patchJson, null);

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);
        verify(userService).patchUser(eq("user123"), anyString(), isNull());
    }

    @Test
    @DisplayName("Should reject invalid JSON in patch user request")
    void testPatchUser_InvalidJson() throws Exception {
        // Arrange
        String invalidJson = "{ invalid }";

        // Act
        Response response = endpoint.patchUser("user123", invalidJson, null);

        // Assert
        assertThat(response.getStatus()).isEqualTo(400);
        String responseBody = (String) response.getEntity();
        assertThat(responseBody).contains("Invalid JSON format");
        verify(userService, never()).patchUser(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should successfully get user by ID")
    void testGetUser_Success() throws Exception {
        // Arrange
        GenericScimResource user = mock(GenericScimResource.class);
        when(user.getId()).thenReturn("user123");
        when(userService.getUser(eq("user123"), eq("*"))).thenReturn(user);

        // Act
        Response response = endpoint.getUser("user123", null, null);

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(user);
        verify(userService).getUser(eq("user123"), eq("*"));
    }

    @Test
    @DisplayName("Should get user with specific attributes")
    void testGetUser_WithAttributes() throws Exception {
        // Arrange
        GenericScimResource user = mock(GenericScimResource.class);
        when(userService.getUser(eq("user123"), anyString())).thenReturn(user);

        // Act
        Response response = endpoint.getUser("user123", "userName,emails", null);

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);
        ArgumentCaptor<String> fieldsCaptor = ArgumentCaptor.forClass(String.class);
        verify(userService).getUser(eq("user123"), fieldsCaptor.capture());

        // Verify fields were mapped correctly
        String capturedFields = fieldsCaptor.getValue();
        assertThat(capturedFields).contains("userName");
        assertThat(capturedFields).contains("mail"); // emails -> mail
    }

    @Test
    @DisplayName("Should successfully delete user")
    void testDeleteUser_Success() throws Exception {
        // Arrange
        doNothing().when(userService).deleteUser(eq("user123"), isNull());

        // Act
        Response response = endpoint.deleteUser("user123", null);

        // Assert
        assertThat(response.getStatus()).isEqualTo(204);
        verify(userService).deleteUser(eq("user123"), isNull());
    }

    @Test
    @DisplayName("Should return 404 when deleting non-existent user")
    void testDeleteUser_NotFound() throws Exception {
        // Arrange
        doThrow(new ResourceNotFoundException("User not found"))
                .when(userService).deleteUser(eq("user123"), isNull());

        // Act
        Response response = endpoint.deleteUser("user123", null);

        // Assert
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("Should handle service exceptions and return appropriate error response")
    void testSearchUsers_ServiceException() throws Exception {
        // Arrange
        when(userService.searchUsers(anyString(), anyInt(), anyInt(), anyString()))
                .thenThrow(new BadRequestException("Invalid filter"));

        // Act
        Response response = endpoint.searchUsers(
                "invalid filter",
                1,
                100,
                null,
                null
        );

        // Assert
        assertThat(response.getStatus()).isEqualTo(400);
        String responseBody = (String) response.getEntity();
        assertThat(responseBody).contains("Invalid filter");
        assertThat(responseBody).contains("urn:ietf:params:scim:api:messages:2.0:Error");
    }

    @Test
    @DisplayName("Should return all fields (*) when excludedAttributes specified without attributes")
    void testSearchUsers_WithExcludedAttributesOnly() throws Exception {
        // Arrange
        List<GenericScimResource> users = new ArrayList<>();
        ListResponse<GenericScimResource> mockResponse = new ListResponse<>(10, users, 1, 100);

        when(userService.searchUsers(anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(mockResponse);

        // Act - only excludedAttributes, no attributes
        // Per implementation: PingIDM doesn't support exclude-only, so returns "*"
        Response response = endpoint.searchUsers(
                null,
                1,
                100,
                null,              // attributes
                "password,groups"  // excludedAttributes
        );

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);

        ArgumentCaptor<String> fieldsCaptor = ArgumentCaptor.forClass(String.class);
        verify(userService).searchUsers(
                anyString(),
                anyInt(),
                anyInt(),
                fieldsCaptor.capture()
        );

        // Should return "*" when only excludedAttributes specified
        assertThat(fieldsCaptor.getValue()).isEqualTo("*");
    }

    @Test
    @DisplayName("Should handle both attributes and excludedAttributes by using attributes (SCIM spec)")
    void testSearchUsers_BothAttributesAndExcluded() throws Exception {
        // Arrange
        List<GenericScimResource> users = new ArrayList<>();
        ListResponse<GenericScimResource> mockResponse = new ListResponse<>(10, users, 1, 100);

        when(userService.searchUsers(anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(mockResponse);

        // Act - both parameters provided
        // Per SCIM spec: attributes takes precedence, excludedAttributes is ignored
        Response response = endpoint.searchUsers(
                null,
                1,
                100,
                "userName,emails",    // attributes - should be used
                "password"            // excludedAttributes - should be ignored
        );

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);

        ArgumentCaptor<String> fieldsCaptor = ArgumentCaptor.forClass(String.class);
        verify(userService).searchUsers(
                anyString(),
                anyInt(),
                anyInt(),
                fieldsCaptor.capture()
        );

        // Should process attributes parameter, ignore excludedAttributes
        String capturedFields = fieldsCaptor.getValue();
        assertThat(capturedFields).contains("userName");
        assertThat(capturedFields).contains("mail"); // emails -> mail
    }

    @Test
    @DisplayName("Should handle complex attribute notation (name.givenName)")
    void testSearchUsers_ComplexAttributes() throws Exception {
        // Arrange
        List<GenericScimResource> users = new ArrayList<>();
        ListResponse<GenericScimResource> mockResponse = new ListResponse<>(10, users, 1, 100);

        when(userService.searchUsers(anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(mockResponse);

        // Act
        Response response = endpoint.searchUsers(
                null,
                1,
                100,
                "name.givenName,name.familyName,emails",
                null
        );

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);

        ArgumentCaptor<String> fieldsCaptor = ArgumentCaptor.forClass(String.class);
        verify(userService).searchUsers(
                anyString(),
                anyInt(),
                anyInt(),
                fieldsCaptor.capture()
        );

        String capturedFields = fieldsCaptor.getValue();
        assertThat(capturedFields).contains("givenName");   // name.givenName -> givenName
        assertThat(capturedFields).contains("sn");          // name.familyName -> sn
        assertThat(capturedFields).contains("mail");        // emails -> mail
    }
}