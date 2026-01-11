package com.pingidentity.p1aic.scim.auth;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OAuthTokenFilter.
 * 
 * Tests the OAuth Bearer token extraction and validation logic,
 * including handling of various Authorization header formats.
 */
@ExtendWith(MockitoExtension.class)
class OAuthTokenFilterTest {

    @Mock
    private OAuthContext oauthContext;

    @Mock
    private ContainerRequestContext requestContext;

    @Mock
    private UriInfo uriInfo;

    @InjectMocks
    private OAuthTokenFilter filter;

    @BeforeEach
    void setUp() {
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
    }

    @Test
    @DisplayName("Should extract valid Bearer token from Authorization header")
    void testFilter_ValidBearerToken() throws Exception {
        // Given: A valid Authorization header with Bearer token
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";
        when(uriInfo.getPath()).thenReturn("/Users");
        when(requestContext.getHeaderString("Authorization")).thenReturn("Bearer " + token);

        // When: Filter processes the request
        filter.filter(requestContext);

        // Then: Token should be stored in OAuth context
        verify(oauthContext).setAccessToken(token);
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    @DisplayName("Should abort request when Authorization header is missing")
    void testFilter_MissingAuthorizationHeader() throws Exception {
        // Given: No Authorization header
        when(uriInfo.getPath()).thenReturn("/Users");
        when(requestContext.getHeaderString("Authorization")).thenReturn(null);

        // When: Filter processes the request
        filter.filter(requestContext);

        // Then: Request should be aborted with 401
        verify(requestContext).abortWith(argThat(response -> 
            response.getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()
        ));
        verify(oauthContext, never()).setAccessToken(any());
    }

    @Test
    @DisplayName("Should abort request when Authorization header is empty")
    void testFilter_EmptyAuthorizationHeader() throws Exception {
        // Given: Empty Authorization header
        when(uriInfo.getPath()).thenReturn("/Users");
        when(requestContext.getHeaderString("Authorization")).thenReturn("");

        // When: Filter processes the request
        filter.filter(requestContext);

        // Then: Request should be aborted with 401
        verify(requestContext).abortWith(argThat(response -> 
            response.getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()
        ));
    }

    @Test
    @DisplayName("Should abort request when Authorization header has invalid format")
    void testFilter_InvalidAuthorizationFormat() throws Exception {
        // Given: Authorization header without "Bearer " prefix
        when(uriInfo.getPath()).thenReturn("/Users");
        when(requestContext.getHeaderString("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        // When: Filter processes the request
        filter.filter(requestContext);

        // Then: Request should be aborted with 401
        verify(requestContext).abortWith(argThat(response -> 
            response.getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()
        ));
    }

    @Test
    @DisplayName("Should abort request when Bearer token is empty")
    void testFilter_EmptyBearerToken() throws Exception {
        // Given: Authorization header with "Bearer " but no token
        when(uriInfo.getPath()).thenReturn("/Users");
        when(requestContext.getHeaderString("Authorization")).thenReturn("Bearer ");

        // When: Filter processes the request
        filter.filter(requestContext);

        // Then: Request should be aborted with 401
        verify(requestContext).abortWith(argThat(response -> 
            response.getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()
        ));
    }

    @Test
    @DisplayName("Should abort request when Bearer token is only whitespace")
    void testFilter_WhitespaceBearerToken() throws Exception {
        // Given: Authorization header with "Bearer " and only whitespace
        when(uriInfo.getPath()).thenReturn("/Users");
        when(requestContext.getHeaderString("Authorization")).thenReturn("Bearer    ");

        // When: Filter processes the request
        filter.filter(requestContext);

        // Then: Request should be aborted with 401
        verify(requestContext).abortWith(argThat(response -> 
            response.getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()
        ));
    }

    @Test
    @DisplayName("Should trim whitespace from Bearer token")
    void testFilter_BearerTokenWithWhitespace() throws Exception {
        // Given: Authorization header with extra whitespace
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";
        when(uriInfo.getPath()).thenReturn("/Users");
        when(requestContext.getHeaderString("Authorization")).thenReturn("Bearer   " + token + "   ");

        // When: Filter processes the request
        filter.filter(requestContext);

        // Then: Token should be trimmed and stored
        verify(oauthContext).setAccessToken(token);
    }

    @Test
    @DisplayName("Should skip authentication for ServiceProviderConfig endpoint")
    void testFilter_ServiceProviderConfigEndpoint() throws Exception {
        // Given: Request to ServiceProviderConfig endpoint
        when(uriInfo.getPath()).thenReturn("/ServiceProviderConfig");

        // When: Filter processes the request
        filter.filter(requestContext);

        // Then: Filter should skip authentication
        verify(requestContext, never()).abortWith(any());
        verify(oauthContext, never()).setAccessToken(any());
    }

    @Test
    @DisplayName("Should skip authentication for ResourceTypes endpoint")
    void testFilter_ResourceTypesEndpoint() throws Exception {
        // Given: Request to ResourceTypes endpoint
        when(uriInfo.getPath()).thenReturn("/ResourceTypes");

        // When: Filter processes the request
        filter.filter(requestContext);

        // Then: Filter should skip authentication
        verify(requestContext, never()).abortWith(any());
        verify(oauthContext, never()).setAccessToken(any());
    }

    @Test
    @DisplayName("Should skip authentication for Schemas endpoint")
    void testFilter_SchemasEndpoint() throws Exception {
        // Given: Request to Schemas endpoint
        when(uriInfo.getPath()).thenReturn("/Schemas");

        // When: Filter processes the request
        filter.filter(requestContext);

        // Then: Filter should skip authentication
        verify(requestContext, never()).abortWith(any());
        verify(oauthContext, never()).setAccessToken(any());
    }

    @Test
    @DisplayName("Should require authentication for Users endpoint")
    void testFilter_UsersEndpointRequiresAuth() throws Exception {
        // Given: Request to Users endpoint without Authorization header
        when(uriInfo.getPath()).thenReturn("/Users");
        when(requestContext.getHeaderString("Authorization")).thenReturn(null);

        // When: Filter processes the request
        filter.filter(requestContext);

        // Then: Request should be aborted with 401
        verify(requestContext).abortWith(argThat(response -> 
            response.getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()
        ));
    }

    @Test
    @DisplayName("Should require authentication for Groups endpoint")
    void testFilter_GroupsEndpointRequiresAuth() throws Exception {
        // Given: Request to Groups endpoint without Authorization header
        when(uriInfo.getPath()).thenReturn("/Groups");
        when(requestContext.getHeaderString("Authorization")).thenReturn(null);

        // When: Filter processes the request
        filter.filter(requestContext);

        // Then: Request should be aborted with 401
        verify(requestContext).abortWith(argThat(response -> 
            response.getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()
        ));
    }

    @Test
    @DisplayName("Should handle case-insensitive Bearer prefix")
    void testFilter_CaseInsensitiveBearerPrefix() throws Exception {
        // Given: Authorization header with lowercase "bearer"
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";
        when(uriInfo.getPath()).thenReturn("/Users");
        when(requestContext.getHeaderString("Authorization")).thenReturn("bearer " + token);

        // When: Filter processes the request
        filter.filter(requestContext);

        // Then: Request should be aborted (case-sensitive check)
        verify(requestContext).abortWith(argThat(response -> 
            response.getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()
        ));
    }

    @Test
    @DisplayName("Should include error detail in 401 response")
    void testFilter_ErrorResponseContainsDetail() throws Exception {
        // Given: Request without Authorization header
        when(uriInfo.getPath()).thenReturn("/Users");
        when(requestContext.getHeaderString("Authorization")).thenReturn(null);

        // When: Filter processes the request
        filter.filter(requestContext);

        // Then: Response should contain error detail
        verify(requestContext).abortWith(argThat(response -> {
            String entity = (String) response.getEntity();
            return entity != null && 
                   entity.contains("Missing Authorization header") &&
                   entity.contains("urn:ietf:params:scim:api:messages:2.0:Error");
        }));
    }

    @Test
    @DisplayName("Should set correct content type for error response")
    void testFilter_ErrorResponseContentType() throws Exception {
        // Given: Request without Authorization header
        when(uriInfo.getPath()).thenReturn("/Users");
        when(requestContext.getHeaderString("Authorization")).thenReturn(null);

        // When: Filter processes the request
        filter.filter(requestContext);

        // Then: Response should have application/scim+json content type
        verify(requestContext).abortWith(argThat(response -> 
            "application/scim+json".equals(response.getMediaType().toString())
        ));
    }

    @Test
    @DisplayName("Should handle long Bearer tokens")
    void testFilter_LongBearerToken() throws Exception {
        // Given: A very long Bearer token
        String longToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImtleTEifQ." +
                          "eyJzdWIiOiJ1c2VyMTIzIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJleHAiOjE1MTYyNDI2MjIsImF1ZCI6ImF1ZGllbmNlIiwiaXNzIjoiaXNzdWVyIiwic2NvcGUiOiJyZWFkIHdyaXRlIn0." +
                          "WflKEbtxJdTw7jJv0YaJBtR2iJ8SvWqnYBwW8pJvD3kBZtJqLwMxKvFNKQvxz6bPbqY7FMxNqBpZ9LmPvKqW5tR";
        when(uriInfo.getPath()).thenReturn("/Users");
        when(requestContext.getHeaderString("Authorization")).thenReturn("Bearer " + longToken);

        // When: Filter processes the request
        filter.filter(requestContext);

        // Then: Token should be extracted successfully
        verify(oauthContext).setAccessToken(longToken);
        verify(requestContext, never()).abortWith(any());
    }
}
