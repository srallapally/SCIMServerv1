package com.pingidentity.p1aic.scim.lifecycle;

import com.pingidentity.p1aic.scim.schema.DynamicSchemaManager;
import jakarta.inject.Inject;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jersey ApplicationEventListener that ensures DynamicSchemaManager is initialized
 * before the server starts accepting requests.
 *
 * This listener is invoked during the Jersey application lifecycle and triggers
 * schema initialization at the INITIALIZATION_FINISHED event, which occurs after
 * dependency injection setup but before the server starts serving requests.
 *
 * This replaces the anti-pattern of manual instantiation in main() method.
 */
public class SchemaManagerInitializer implements ApplicationEventListener {

    private static final Logger logger = LoggerFactory.getLogger(SchemaManagerInitializer.class);

    @Inject
    private DynamicSchemaManager schemaManager;

    /**
     * Handle application lifecycle events.
     * Initializes the schema manager when the application initialization is finished.
     */
    @Override
    public void onEvent(ApplicationEvent event) {
        switch (event.getType()) {
            case INITIALIZATION_FINISHED:
                logger.info("Jersey application initialized - triggering schema manager initialization");
                initializeSchemaManager();
                break;
            default:
                // Ignore other events
                break;
        }
    }

    /**
     * Initialize the DynamicSchemaManager.
     * This is called after DI container setup but before accepting requests.
     */
    private void initializeSchemaManager() {
        try {
            logger.info("Initializing DynamicSchemaManager via lifecycle listener...");
            schemaManager.initialize();

            if (!schemaManager.isInitialized()) {
                logger.error("FATAL: Schema manager reports not initialized after initialize() call");
                throw new IllegalStateException("Schema manager initialization failed");
            }

            logger.info("Schema manager initialization successful. Schema count: {}", schemaManager.getSchemaCount());
            logger.info("Cached schemas: {}", String.join(", ", schemaManager.getCachedSchemaUrns()));

        } catch (Exception e) {
            logger.error("FATAL: Could not initialize SCIM Schema. Server cannot start.", e);
            // Re-throw to prevent server startup
            throw new IllegalStateException("Schema manager initialization failed", e);
        }
    }

    /**
     * No request-level event listener needed.
     */
    @Override
    public RequestEventListener onRequest(RequestEvent requestEvent) {
        return null;
    }
}