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
        GenericScimResource inputUser = mock(GenericScimResource.class);
        GenericScimResource createdUser = mock(GenericScimResource.class);
        when(createdUser.getId()).thenReturn("user123");
        when(userService.createUser(any(GenericScimResource.class))).thenReturn(createdUser);

        // Act
        Response response = endpoint.createUser(inputUser);

        // Assert
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getHeaderString("Location")).contains("/Users/user123");
        verify(userService).createUser(inputUser);
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