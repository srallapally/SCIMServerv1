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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JAX-RS filter for OAuth Bearer token authentication.
 *
 * This filter:
 * 1. Extracts the Bearer token from the Authorization header
 * 2. Validates that the token is present and properly formatted
 * 3. Stores the token in request-scoped OAuthContext for PingIdmRestClient to use
 * 4. OAuthContext is automatically cleaned up when request scope ends
 *
 * Certain endpoints (ServiceProviderConfig, Schemas, ResourceTypes) are
 * exempt from authentication as per SCIM 2.0 specification.
 *
 */
@Provider
@PreMatching
public class OAuthTokenFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger logger = LoggerFactory.getLogger(OAuthTokenFilter.class);

    // BEGIN: Inject request-scoped OAuthContext instead of using ThreadLocal
    //@Inject
    //private OAuthContext oauthContext;

    @Inject
    private jakarta.inject.Provider<OAuthContext> oauthContextProvider;
    // END: Inject request-scoped OAuthContext

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

        // BEGIN: Store token in request-scoped OAuthContext instead of ThreadLocal
        //oauthContext.setAccessToken(token);
        OAuthContext oauthContext = oauthContextProvider.get();
        oauthContext.setAccessToken(token);
        logger.debug("OAuth token extracted and stored in OAuthContext for request to: {}", path);
        // END: Store token in request-scoped OAuthContext
    }

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) {
        // BEGIN: Request-scoped bean automatically cleaned up - no manual cleanup needed
        // The CDI container will destroy the OAuthContext bean when request scope ends
        logger.debug("Request completed - OAuthContext will be automatically cleaned up by CDI container");
        // END: Request-scoped bean automatically cleaned up
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