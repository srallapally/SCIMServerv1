package com.pingidentity.p1aic.scim.auth;

import com.pingidentity.p1aic.scim.client.PingIdmRestClient;
import com.pingidentity.p1aic.scim.config.ScimServerConfig;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JAX-RS filter for OAuth Bearer token authentication.
 *
 * This filter:
 * 1. Extracts the Bearer token from the Authorization header
 * 2. Validates that the token is present and properly formatted
 * 3. Stores the token in ThreadLocal for PingIdmRestClient to use
 * 4. Cleans up the ThreadLocal after the request completes
 *
 * Certain endpoints (ServiceProviderConfig, Schemas, ResourceTypes) are
 * exempt from authentication as per SCIM 2.0 specification.
 */
@Provider
@PreMatching
public class OAuthTokenFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger logger = LoggerFactory.getLogger(OAuthTokenFilter.class);

    // SCIM endpoints that don't require authentication
    private static final String[] PUBLIC_PATHS = {
            "/ServiceProviderConfig",
            "/Schemas",
            "/ResourceTypes"
    };

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();

        // Skip authentication for public endpoints
        for (String publicPath : PUBLIC_PATHS) {
            if (path.endsWith(publicPath)) {
                logger.debug("Skipping authentication for public endpoint: {}", path);
                return;
            }
        }

        // Extract Authorization header
        String authHeader = requestContext.getHeaderString("Authorization");

        if (authHeader == null || authHeader.isEmpty()) {
            logger.warn("Missing Authorization header for path: {}", path);
            abortWithUnauthorized(requestContext, "Missing Authorization header");
            return;
        }

        // Validate Bearer token format
        if (!authHeader.toLowerCase().startsWith("bearer ")) {
            logger.warn("Invalid Authorization header format for path: {}", path);
            abortWithUnauthorized(requestContext, "Invalid Authorization header format");
            return;
        }

        // Extract token (remove "Bearer " prefix)
        String token = authHeader.substring(7).trim();

        if (token.isEmpty()) {
            logger.warn("Empty Bearer token for path: {}", path);
            abortWithUnauthorized(requestContext, "Empty Bearer token");
            return;
        }

        // Store token in ThreadLocal for PingIdmRestClient to use
        PingIdmRestClient.setCurrentOAuthToken(token);
        logger.debug("OAuth token extracted and stored for request to: {}", path);
    }

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) {
        // Clean up ThreadLocal after request completes (success or failure)
        PingIdmRestClient.clearCurrentOAuthToken();
        logger.debug("OAuth token cleared after request completion");
    }

    /**
     * Abort the request with 401 Unauthorized response.
     *
     * @param requestContext the request context
     * @param message the error message
     */
    private void abortWithUnauthorized(ContainerRequestContext requestContext, String message) {
        ScimServerConfig config = ScimServerConfig.getInstance();
        String realm = config.getRealm();

        // Build SCIM-compliant error response
        String errorResponse = String.format(
                "{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:Error\"]," +
                        "\"status\":\"401\"," +
                        "\"detail\":\"%s\"}",
                message
        );

        requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                        .header("WWW-Authenticate", "Bearer realm=\"" + realm + "\"")
                        .entity(errorResponse)
                        .type("application/scim+json")
                        .build()
        );
    }
}