package com.pingidentity.p1aic.scim;
import com.pingidentity.p1aic.scim.auth.OAuthContext;
import com.pingidentity.p1aic.scim.config.CustomAttributeMappingConfig;
import com.pingidentity.p1aic.scim.config.JacksonConfig;
import com.pingidentity.p1aic.scim.config.PingIdmConfigService;
import com.pingidentity.p1aic.scim.mapping.CustomAttributeMapperService;
import com.pingidentity.p1aic.scim.schema.ScimSchemaBuilder;
import com.pingidentity.p1aic.scim.client.PingIdmRestClient;
import com.pingidentity.p1aic.scim.config.ScimServerConfig;
import com.pingidentity.p1aic.scim.lifecycle.SchemaManagerInitializer;
import com.pingidentity.p1aic.scim.mapping.UserAttributeMapper;
import com.pingidentity.p1aic.scim.schema.DynamicSchemaManager;
import com.pingidentity.p1aic.scim.service.PingIdmUserService;
import com.pingidentity.p1aic.scim.service.PingIdmRoleService;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.process.internal.RequestScoped;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main class for SCIM Server with embedded Jetty.
 *
 * REFACTORED: Uses proper lifecycle management with SchemaManagerInitializer
 * instead of manual instantiation anti-pattern.
 *
 * The SchemaManagerInitializer (ApplicationEventListener) ensures the DynamicSchemaManager
 * is initialized after DI setup but before the server accepts requests.
 */
public class ScimServerMain {

    private static final Logger logger = LoggerFactory.getLogger(ScimServerMain.class);

    public static void main(String[] args) {
        try {
            // ------------------------------------------------------------
            // 1. Load Environment Configuration
            // ------------------------------------------------------------
            int port = getPort();
            String scimServerBaseUrl = getScimServerBaseUrl(port);
            String realm = getEnvOrDefault("SCIM_REALM", "scim");
            String pingIdmBaseUrl = getRequiredEnv("PINGIDM_BASE_URL");
            String oauthTokenUrl = getRequiredEnv("OAUTH_TOKEN_URL");
            String oauthClientId = getRequiredEnv("OAUTH_CLIENT_ID");
            String oauthClientSecret = getRequiredEnv("OAUTH_CLIENT_SECRET");
            String oauthScope = getEnvOrDefault("OAUTH_SCOPE", "");
            String managedUserObject = getEnvOrDefault("PINGIDM_MANAGED_USER_OBJECT", "alpha_user");
            String managedRoleObject = getEnvOrDefault("PINGIDM_MANAGED_ROLE_OBJECT", "alpha_role");

            logger.info("Starting SCIM Server...");
            logger.info("Port: {}", port);
            logger.info("SCIM Server Base URL: {}", scimServerBaseUrl);
            logger.info("PingIDM URL: {}", pingIdmBaseUrl);
            logger.info("OAuth Token URL: {}", oauthTokenUrl);

            // Initialize Singleton Config
            ScimServerConfig config = ScimServerConfig.getInstance();
            config.setScimServerBaseUrl(scimServerBaseUrl);
            config.setRealm(realm);
            config.setPingIdmBaseUrl(pingIdmBaseUrl);
            config.setOauthTokenUrl(oauthTokenUrl);
            config.setOauthClientId(oauthClientId);
            config.setOauthClientSecret(oauthClientSecret);
            config.setOauthScope(oauthScope);
            config.setManagedUserObjectName(managedUserObject);
            config.setManagedRoleObjectName(managedRoleObject);

            // ------------------------------------------------------------
            // 2. Services are managed by HK2 DI container
            //    Initialization is handled by SchemaManagerInitializer lifecycle listener
            // ------------------------------------------------------------
            logger.info("Services will be instantiated by HK2 dependency injection");
            logger.info("Schema initialization will be triggered by lifecycle listener");

            // ------------------------------------------------------------
            // 3. Configure Jetty Server
            // ------------------------------------------------------------
            Server server = new Server();
            ServerConnector connector = new ServerConnector(server);
            connector.setPort(port);
            server.addConnector(connector);

            ServletContextHandler scimContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
            scimContext.setContextPath("/scim/v2");

            // ------------------------------------------------------------
            // 4. Configure Jersey with HK2 Dependency Injection
            // ------------------------------------------------------------
            ResourceConfig resourceConfig = new ResourceConfig();

            // Register Endpoints
            resourceConfig.register(com.pingidentity.p1aic.scim.config.JacksonConfig.class);
            resourceConfig.register(com.pingidentity.p1aic.scim.auth.OAuthTokenFilter.class);
            resourceConfig.register(com.pingidentity.p1aic.scim.endpoints.ServiceProviderConfigEndpoint.class);
            resourceConfig.register(com.pingidentity.p1aic.scim.endpoints.SchemasEndpoint.class);
            resourceConfig.register(com.pingidentity.p1aic.scim.endpoints.ResourceTypesEndpoint.class);
            resourceConfig.register(com.pingidentity.p1aic.scim.endpoints.UserScimEndpoint.class);
            resourceConfig.register(com.pingidentity.p1aic.scim.endpoints.GroupScimEndpoint.class);

            // Register Providers
            resourceConfig.register(com.pingidentity.p1aic.scim.exceptions.ScimExceptionMapper.class);

            // BEGIN: Register lifecycle listener for schema initialization
            resourceConfig.register(SchemaManagerInitializer.class);
            logger.info("Registered SchemaManagerInitializer lifecycle listener");
            // END: Register lifecycle listener

            // BEGIN: Bind services to HK2 DI container (let HK2 instantiate)
            resourceConfig.register(new AbstractBinder() {
                @Override
                protected void configure() {
                    // Bind classes - HK2 will instantiate them
                    bind(PingIdmRestClient.class).to(PingIdmRestClient.class).in(jakarta.inject.Singleton.class);
                    bind(PingIdmUserService.class).to(PingIdmUserService.class).in(jakarta.inject.Singleton.class);
                    bind(PingIdmRoleService.class).to(PingIdmRoleService.class).in(jakarta.inject.Singleton.class);
                    bind(UserAttributeMapper.class).to(UserAttributeMapper.class).in(jakarta.inject.Singleton.class);
                    bind(PingIdmConfigService.class).to(PingIdmConfigService.class).in(jakarta.inject.Singleton.class);
                    bind(CustomAttributeMappingConfig.class).to(CustomAttributeMappingConfig.class).in(jakarta.inject.Singleton.class);
                    bind(CustomAttributeMapperService.class).to(CustomAttributeMapperService.class).in(jakarta.inject.Singleton.class);
                    bind(ScimSchemaBuilder.class).to(ScimSchemaBuilder.class).in(jakarta.inject.Singleton.class);
                    bind(DynamicSchemaManager.class).to(DynamicSchemaManager.class).in(jakarta.inject.Singleton.class);

                    // Bind OAuthContext as request-scoped (per-request instance)
                    bindFactory(OAuthContextFactory.class)
                            .to(OAuthContext.class)
                            .in(RequestScoped.class);
                }
            });
            // END: Bind services to HK2 DI container

            ServletHolder jerseyServlet = new ServletHolder(new ServletContainer(resourceConfig));
            jerseyServlet.setInitOrder(0);
            scimContext.addServlet(jerseyServlet, "/*");

            // Health Check Context
            ServletContextHandler healthContext = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
            healthContext.setContextPath("/");
            healthContext.addServlet(new ServletHolder(new HealthCheckServlet()), "/health");

            // Combine Contexts
            ContextHandlerCollection handlers = new ContextHandlerCollection();
            handlers.addHandler(healthContext);
            handlers.addHandler(scimContext);
            server.setHandler(handlers);

            // Graceful Shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down SCIM Server...");
                try {
                    server.stop();
                    logger.info("SCIM Server stopped");
                } catch (Exception e) {
                    logger.error("Error stopping server", e);
                }
            }));

            // Start
            server.start();
            logger.info("SCIM Server started successfully");
            logger.info("Health check: {}/health", scimServerBaseUrl);
            logger.info("SCIM Endpoints: {}/scim/v2", scimServerBaseUrl);

            server.join();

        } catch (Exception e) {
            logger.error("Failed to start SCIM Server", e);
            System.exit(1);
        }
    }

    // --- Helper Methods ---

    private static int getPort() {
        String portStr = System.getenv("PORT");
        if (portStr != null && !portStr.isEmpty()) {
            try {
                return Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                logger.warn("Invalid PORT value: {}, using default 8080", portStr);
            }
        }
        return 8080;
    }

    private static String getScimServerBaseUrl(int port) {
        String baseUrl = System.getenv("SCIM_SERVER_BASE_URL");
        if (baseUrl != null && !baseUrl.isEmpty()) {
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            logger.info("Using SCIM_SERVER_BASE_URL from environment: {}", baseUrl);
            return baseUrl;
        }
        String defaultUrl = "http://localhost:" + port;
        logger.info("SCIM_SERVER_BASE_URL not set, using default: {}", defaultUrl);
        return defaultUrl;
    }

    private static String getEnvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    private static String getRequiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Required environment variable not set: " + name);
        }
        return value;
    }

    static class HealthCheckServlet extends jakarta.servlet.http.HttpServlet {
        @Override
        protected void doGet(jakarta.servlet.http.HttpServletRequest req,
                             jakarta.servlet.http.HttpServletResponse resp) throws java.io.IOException {
            resp.setContentType("application/json");
            resp.setStatus(200);
            resp.getWriter().write("{\"status\":\"healthy\",\"service\":\"scim-server\"}");
        }
    }

    /**
     * Factory for creating request-scoped OAuthContext instances.
     * HK2 uses factories to create per-request instances.
     */
    public static class OAuthContextFactory implements org.glassfish.hk2.api.Factory<OAuthContext> {
        @Override
        public OAuthContext provide() {
            return new OAuthContext();
        }

        @Override
        public void dispose(OAuthContext instance) {
            if (instance != null) {
                instance.clear();
            }
        }
    }
}