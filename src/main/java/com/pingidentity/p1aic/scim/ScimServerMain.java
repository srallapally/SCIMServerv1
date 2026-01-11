package com.pingidentity.p1aic.scim;

import com.pingidentity.p1aic.scim.config.PingIdmConfigService;
import com.pingidentity.p1aic.scim.schema.ScimSchemaBuilder;
import com.pingidentity.p1aic.scim.client.PingIdmRestClient;
import com.pingidentity.p1aic.scim.config.ScimServerConfig;
import com.pingidentity.p1aic.scim.mapping.UserAttributeMapper;
import com.pingidentity.p1aic.scim.schema.DynamicSchemaManager;
import com.pingidentity.p1aic.scim.service.PingIdmUserService;
import com.pingidentity.p1aic.scim.service.PingIdmRoleService; // Import added
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main class for SCIM Server with embedded Jetty.
 * Uses Eager Initialization to ensure Schema Manager is ready before traffic starts.
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
            // 2. Instantiate and Initialize Services (Eager Loading)
            // ------------------------------------------------------------
            // We manually "new" these objects to ensure they are ready before the server opens.
            // NOTE: Ensure your classes have the matching constructors as defined in Step 1-3.

            // Core Config & Schema Services
            PingIdmRestClient restClient = new PingIdmRestClient();

            PingIdmConfigService configService = new PingIdmConfigService(restClient);
            ScimSchemaBuilder schemaBuilder = new ScimSchemaBuilder();

            // Schema Manager: Needs config + builder.
            DynamicSchemaManager schemaManager = new DynamicSchemaManager(configService, schemaBuilder);

            logger.info("Initializing Dynamic Schema Manager (Fetching schema from PingIDM)...");
            try {
                // This is the FIX for the 503 Error. We force initialization now.
                // Since we created it manually, @PostConstruct is not auto-called, so we call it here.
                schemaManager.initialize();
            } catch (Exception e) {
                logger.error("FATAL: Could not initialize SCIM Schema. Server cannot start.", e);
                // Fail fast: If schema is broken, there is no point in starting the server.
                System.exit(1);
            }
            // BEGIN: Verify initialization
            if (!schemaManager.isInitialized()) {
                logger.error("FATAL: Schema manager reports not initialized after initialize() call");
                System.exit(1);
            }
            logger.info("Schema manager initialization verified. Schema count: {}", schemaManager.getSchemaCount());
            // END: Verify initialization

            // Client & User Services
            PingIdmUserService userService = new PingIdmUserService(restClient);
            // Assuming you might need RoleService as well since you uploaded it
            PingIdmRoleService roleService = new PingIdmRoleService(restClient);

            UserAttributeMapper userMapper = new UserAttributeMapper();

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
            // 4. Configure Jersey (Bind the Eager Instances)
            // ------------------------------------------------------------
            ResourceConfig resourceConfig = new ResourceConfig();

            // Register Endpoints (Keep as classes, Jersey handles these)
            resourceConfig.register(com.pingidentity.p1aic.scim.endpoints.ServiceProviderConfigEndpoint.class);
            resourceConfig.register(com.pingidentity.p1aic.scim.endpoints.SchemasEndpoint.class);
            resourceConfig.register(com.pingidentity.p1aic.scim.endpoints.ResourceTypesEndpoint.class);
            resourceConfig.register(com.pingidentity.p1aic.scim.endpoints.UserScimEndpoint.class);
            resourceConfig.register(com.pingidentity.p1aic.scim.endpoints.GroupScimEndpoint.class);

            // Register Providers
            resourceConfig.register(com.pingidentity.p1aic.scim.exceptions.ScimExceptionMapper.class);

            // Bind the PRE-INITIALIZED instances to the container
            resourceConfig.register(new AbstractBinder() {
                @Override
                protected void configure() {
                    // Bind the specific instances we created above
                    bind(restClient).to(PingIdmRestClient.class);
                    bind(userService).to(PingIdmUserService.class);
                    // bind(roleService).to(PingIdmRoleService.class); // Uncomment if used in endpoints
                    bind(userMapper).to(UserAttributeMapper.class);
                    bind(configService).to(PingIdmConfigService.class);
                    bind(schemaBuilder).to(ScimSchemaBuilder.class);

                    // Bind the initialized schema manager
                    bind(schemaManager).to(DynamicSchemaManager.class);
                }
            });

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
}