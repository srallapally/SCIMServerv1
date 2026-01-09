package com.pingidentity.p1aic.scim.auth;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * JAX-RS filter that extracts OAuth Bearer token from Authorization header
 * and stores it in the request-scoped OAuthContext for downstream use.
 *
 * This filter runs early in the request processing chain to ensure the token
 * is available for all subsequent filters and endpoints.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class OAuthTokenFilter implements ContainerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Inject
    private OAuthContext oauthContext;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // Skip token extraction for discovery endpoints (they may not require auth)
        String path = requestContext.getUriInfo().getPath();
        if (isDiscoveryEndpoint(path)) {
            return;
        }

        // Extract Authorization header
        String authHeader = requestContext.getHeaderString(AUTHORIZATION_HEADER);

        if (authHeader == null || authHeader.trim().isEmpty()) {
            // No Authorization header present
            abortWithUnauthorized(requestContext, "Missing Authorization header");
            return;
        }

        if (!authHeader.startsWith(BEARER_PREFIX)) {
            // Authorization header doesn't start with "Bearer "
            abortWithUnauthorized(requestContext, "Invalid Authorization header format. Expected 'Bearer <token>'");
            return;
        }

        // Extract token (remove "Bearer " prefix)
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        if (token.isEmpty()) {
            // Empty token after "Bearer " prefix
            abortWithUnauthorized(requestContext, "Empty bearer token");
            return;
        }

        // Store token in request-scoped context
        oauthContext.setAccessToken(token);
    }

    /**
     * Check if the request path is for a discovery endpoint that may not require authentication.
     * Discovery endpoints: /ServiceProviderConfig, /ResourceTypes, /Schemas
     */
    private boolean isDiscoveryEndpoint(String path) {
        return path.endsWith("/ServiceProviderConfig") ||
                path.endsWith("/ResourceTypes") ||
                path.endsWith("/Schemas");
    }

    /**
     * Abort the request with 401 Unauthorized and SCIM error response.
     */
    private void abortWithUnauthorized(ContainerRequestContext requestContext, String detail) {
        // Build SCIM-compliant error response
        String errorResponse = String.format(
                "{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:Error\"]," +
                        "\"status\":\"401\"," +
                        "\"detail\":\"%s\"}",
                detail
        );

        requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                        .entity(errorResponse)
                        .type("application/scim+json")
                        .build()
        );
    }
}
