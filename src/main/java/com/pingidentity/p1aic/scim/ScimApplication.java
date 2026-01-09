package com.pingidentity.p1aic.scim;

import com.pingidentity.p1aic.scim.auth.OAuthTokenFilter;
import com.pingidentity.p1aic.scim.endpoints.*;
import com.pingidentity.p1aic.scim.exceptions.PingIdmExceptionMapper;
import com.pingidentity.p1aic.scim.exceptions.ScimExceptionMapper;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * JAX-RS Application class that bootstraps the SCIM 2.0 server.
 *
 * This class registers all JAX-RS resources (endpoints), filters,
 * and exception mappers required for the SCIM server.
 *
 * The application is available at: /scim/v2
 */
@ApplicationPath("/scim/v2")
public class ScimApplication extends Application {

    private static final Logger LOGGER = Logger.getLogger(ScimApplication.class.getName());

    /**
     * Register all JAX-RS components (endpoints, filters, exception mappers).
     */
    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<>();

        // Security Filter
        classes.add(OAuthTokenFilter.class);

        // SCIM Resource Endpoints
        classes.add(UserScimEndpoint.class);
        classes.add(GroupScimEndpoint.class);

        // SCIM Discovery Endpoints
        classes.add(ServiceProviderConfigEndpoint.class);
        classes.add(ResourceTypesEndpoint.class);
        classes.add(SchemasEndpoint.class);

        // Exception Mappers
        classes.add(ScimExceptionMapper.class);
        classes.add(PingIdmExceptionMapper.class);

        LOGGER.info("SCIM Application initialized with " + classes.size() + " components");

        return classes;
    }

    /**
     * Lifecycle hook called when application starts.
     */
    @Override
    public Set<Object> getSingletons() {
        Set<Object> singletons = new HashSet<>();

        // Add any singleton instances here if needed
        // For now, we'll use class-based registration via getClasses()

        return singletons;
    }
}
