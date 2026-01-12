package com.pingidentity.p1aic.scim.auth;

import com.pingidentity.p1aic.scim.config.ScimServerConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.glassfish.hk2.api.ServiceLocator; // Import ServiceLocator
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JAX-RS filter for OAuth Bearer token authentication.
 * * REFACTORED: Uses ServiceLocator to resolve OAuthContext dynamically.
 * This prevents "Not inside a request scope" errors during server startup.
 */
@Provider
@PreMatching
public class OAuthTokenFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger logger = LoggerFactory.getLogger(OAuthTokenFilter.class);

    // FIXED: Inject ServiceLocator instead of Provider<OAuthContext>
    // This allows us to look up the request-scoped bean only when needed (at runtime)
    @Inject
    private ServiceLocator serviceLocator;

    // SCIM endpoints that don't require authentication
    private static final String[] PUBLIC_PATHS = {
            "/ServiceProviderConfig",
            "/Schemas",
            "/ResourceTypes"
    };

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        // Skip authentication for public endpoints
        for (String publicPath : PUBLIC_PATHS) {
            if (normalizedPath.endsWith(publicPath)) {
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

        // FIXED: Retrieve OAuthContext dynamically using ServiceLocator
        // This ensures the lookup happens inside the active request scope
        try {
            OAuthContext oauthContext = serviceLocator.getService(OAuthContext.class);
            if (oauthContext != null) {
                oauthContext.setAccessToken(token);
                logger.debug("OAuth token extracted and stored in OAuthContext for request to: {}", path);
            } else {
                logger.error("Failed to resolve OAuthContext from ServiceLocator");
                abortWithUnauthorized(requestContext, "Internal Server Error: Auth Context unavailable");
            }
        } catch (Exception e) {
            logger.error("Error setting access token in OAuthContext", e);
            abortWithUnauthorized(requestContext, "Internal Server Error: Auth Context error");
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) {
        // Request-scoped bean automatically cleaned up - no manual cleanup needed
        logger.debug("Request completed - OAuthContext will be automatically cleaned up by CDI container");
    }

    private void abortWithUnauthorized(ContainerRequestContext requestContext, String message) {
        ScimServerConfig config = ScimServerConfig.getInstance();
        String realm = config.getRealm();

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