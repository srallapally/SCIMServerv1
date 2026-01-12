package com.pingidentity.p1aic.scim.exceptions;

import com.unboundid.scim2.common.exceptions.ScimException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JAX-RS exception mapper for SCIM exceptions.
 *
 * This mapper intercepts ScimException instances thrown by endpoints or services
 * and converts them into proper HTTP responses with SCIM-compliant error format.
 */
@Provider
public class ScimExceptionMapper implements ExceptionMapper<ScimException> {

    private static final Logger LOGGER = Logger.getLogger(ScimExceptionMapper.class.getName());

    private final ScimErrorResponseBuilder errorBuilder;

    /**
     * Constructor initializes the error response builder.
     */
    public ScimExceptionMapper() {
        this.errorBuilder = new ScimErrorResponseBuilder();
    }

    /**
     * Convert a ScimException to an HTTP Response.
     *
     * @param exception the SCIM exception
     * @return HTTP response with SCIM error format
     */
    @Override
    public Response toResponse(ScimException exception) {

        // Log the exception
        LOGGER.log(Level.WARNING, "SCIM exception caught: " + exception.getMessage(), exception);

        // Extract status code from exception
        int statusCode = getStatusCode(exception);

        // Extract error details
        String detail = exception.getMessage() != null ? exception.getMessage() : "An error occurred";
        String scimType = getScimType(exception);

        // Build SCIM error response
        String errorResponse = errorBuilder.buildErrorResponse(statusCode, detail, scimType);

        // Return HTTP response
        return Response.status(statusCode)
                .entity(errorResponse)
                .type("application/scim+json")
                .build();
    }

    /**
     * Extract HTTP status code from ScimException.
     */
    private int getStatusCode(ScimException exception) {
        try {
            // Try to get status from ScimError if available
            if (exception.getScimError() != null) {
                return exception.getScimError().getStatus();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to extract status from ScimError", e);
        }

        // Map exception type to status code
        String exceptionClass = exception.getClass().getSimpleName();

        return switch (exceptionClass) {
            case "BadRequestException" -> Response.Status.BAD_REQUEST.getStatusCode();
            case "ResourceNotFoundException" -> Response.Status.NOT_FOUND.getStatusCode();
            case "UnauthorizedException" -> Response.Status.UNAUTHORIZED.getStatusCode();
            case "ForbiddenException" -> Response.Status.FORBIDDEN.getStatusCode();
            case "ResourceConflictException" -> Response.Status.CONFLICT.getStatusCode();
            case "PreconditionFailedException" -> Response.Status.PRECONDITION_FAILED.getStatusCode();
            case "TooManyRequestsException" -> 429; // Too Many Requests
            case "InternalServerErrorException" -> Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
            case "NotImplementedException" -> Response.Status.NOT_IMPLEMENTED.getStatusCode();
            default -> Response.Status.BAD_REQUEST.getStatusCode();
        };
    }

    /**
     * Extract SCIM error type from exception.
     *
     * SCIM error types include:
     * - invalidFilter
     * - tooMany
     * - uniqueness
     * - mutability
     * - invalidSyntax
     * - invalidPath
     * - noTarget
     * - invalidValue
     * - invalidVers
     * - sensitive
     */
    private String getScimType(ScimException exception) {
        try {
            // Try to get scimType from ScimError if available
            if (exception.getScimError() != null && exception.getScimError().getScimType() != null) {
                return exception.getScimError().getScimType();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to extract scimType from ScimError", e);
        }

        // Map exception message to scimType
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";

        if (message.contains("filter")) {
            return "invalidFilter";
        } else if (message.contains("unique")) {
            return "uniqueness";
        } else if (message.contains("syntax")) {
            return "invalidSyntax";
        } else if (message.contains("path")) {
            return "invalidPath";
        } else if (message.contains("value")) {
            return "invalidValue";
        } else if (message.contains("mutability") || message.contains("read-only")) {
            return "mutability";
        } else if (message.contains("not found")) {
            return "noTarget";
        }

        // Return null if no specific type can be determined
        return null;
    }
}