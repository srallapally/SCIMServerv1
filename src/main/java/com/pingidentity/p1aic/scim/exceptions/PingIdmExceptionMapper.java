package com.pingidentity.p1aic.scim.exceptions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Exception mapper that converts PingIDM error responses to SCIM error format.
 *
 * This class helps translate error responses from PingIDM REST APIs into
 * SCIM-compliant error responses that clients can understand.
 */
public class PingIdmExceptionMapper {

    private static final Logger LOGGER = Logger.getLogger(PingIdmExceptionMapper.class.getName());

    private final ObjectMapper objectMapper;
    private final ScimErrorResponseBuilder errorBuilder;

    /**
     * Constructor initializes ObjectMapper and error builder.
     */
    public PingIdmExceptionMapper() {
        this.objectMapper = new ObjectMapper();
        this.errorBuilder = new ScimErrorResponseBuilder();
    }

    /**
     * Map a PingIDM error response to a SCIM error response.
     *
     * @param response the HTTP response from PingIDM
     * @return SCIM-compliant error response string
     */
    public String mapPingIdmError(Response response) {
        int statusCode = response.getStatus();
        String detail = "An error occurred while processing the request";
        String scimType = null;

        try {
            // Try to extract error details from PingIDM response
            String responseBody = response.readEntity(String.class);

            if (responseBody != null && !responseBody.isEmpty()) {
                JsonNode errorNode = objectMapper.readTree(responseBody);

                // PingIDM error format typically has "message", "reason", "detail" fields
                if (errorNode.has("message")) {
                    detail = errorNode.get("message").asText();
                } else if (errorNode.has("detail")) {
                    detail = errorNode.get("detail").asText();
                } else if (errorNode.has("reason")) {
                    detail = errorNode.get("reason").asText();
                }

                // Extract additional error information if available
                if (errorNode.has("code")) {
                    String code = errorNode.get("code").asText();
                    scimType = mapPingIdmCodeToScimType(code);
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse PingIDM error response", e);
        }

        // If scimType not determined from error code, infer from status code
        if (scimType == null) {
            scimType = inferScimTypeFromStatus(statusCode, detail);
        }

        // Build SCIM error response
        return errorBuilder.buildErrorResponse(statusCode, detail, scimType);
    }

    /**
     * Map a PingIDM error response to a SCIM error HTTP Response.
     *
     * @param pingIdmResponse the HTTP response from PingIDM
     * @return HTTP Response with SCIM error format
     */
    public Response mapToScimResponse(Response pingIdmResponse) {
        int statusCode = pingIdmResponse.getStatus();
        String errorResponse = mapPingIdmError(pingIdmResponse);

        return Response.status(statusCode)
                .entity(errorResponse)
                .type("application/scim+json")
                .build();
    }

    /**
     * Map PingIDM error code to SCIM error type.
     *
     * PingIDM error codes may include:
     * - 400: Bad Request
     * - 401: Unauthorized
     * - 403: Forbidden
     * - 404: Not Found
     * - 409: Conflict (duplicate)
     * - 412: Precondition Failed (version mismatch)
     * - 500: Internal Server Error
     */
    private String mapPingIdmCodeToScimType(String code) {
        if (code == null) {
            return null;
        }

        return switch (code.toLowerCase()) {
            case "duplicate", "uniqueness", "already_exists" -> "uniqueness";
            case "not_found", "no_such_object" -> "noTarget";
            case "invalid_value", "constraint_violation" -> "invalidValue";
            case "bad_request", "invalid_request" -> "invalidSyntax";
            case "unauthorized", "insufficient_access" -> null; // No specific SCIM type for auth errors
            case "version_mismatch", "precondition_failed" -> "mutability";
            default -> null;
        };
    }

    /**
     * Infer SCIM error type from HTTP status code and error message.
     */
    private String inferScimTypeFromStatus(int statusCode, String detail) {
        String lowerDetail = detail != null ? detail.toLowerCase() : "";

        // Check message content first
        if (lowerDetail.contains("unique") || lowerDetail.contains("duplicate") ||
                lowerDetail.contains("already exists")) {
            return "uniqueness";
        } else if (lowerDetail.contains("not found")) {
            return "noTarget";
        } else if (lowerDetail.contains("invalid") && lowerDetail.contains("value")) {
            return "invalidValue";
        } else if (lowerDetail.contains("invalid") && lowerDetail.contains("filter")) {
            return "invalidFilter";
        } else if (lowerDetail.contains("read-only") || lowerDetail.contains("immutable")) {
            return "mutability";
        }

        // Infer from status code
        return switch (statusCode) {
            case 404 -> "noTarget";
            case 409 -> "uniqueness";
            case 412 -> "mutability";
            default -> null;
        };
    }

    /**
     * Check if a response is an error response from PingIDM.
     *
     * @param response the HTTP response from PingIDM
     * @return true if the response indicates an error (4xx or 5xx status)
     */
    public boolean isErrorResponse(Response response) {
        int statusCode = response.getStatus();
        return statusCode >= 400;
    }

    /**
     * Extract error message from PingIDM response.
     *
     * @param response the HTTP response from PingIDM
     * @return the error message, or a default message if extraction fails
     */
    public String extractErrorMessage(Response response) {
        try {
            String responseBody = response.readEntity(String.class);

            if (responseBody != null && !responseBody.isEmpty()) {
                JsonNode errorNode = objectMapper.readTree(responseBody);

                if (errorNode.has("message")) {
                    return errorNode.get("message").asText();
                } else if (errorNode.has("detail")) {
                    return errorNode.get("detail").asText();
                } else if (errorNode.has("reason")) {
                    return errorNode.get("reason").asText();
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to extract error message from PingIDM response", e);
        }

        return "An error occurred while processing the request";
    }

    /**
     * Map HTTP status code to appropriate SCIM HTTP status code.
     * Most status codes map directly, but some may need adjustment.
     *
     * @param pingIdmStatus the HTTP status code from PingIDM
     * @return the appropriate HTTP status code for SCIM response
     */
    public int mapStatusCode(int pingIdmStatus) {
        // Most status codes map directly
        // Special cases can be handled here if needed
        return pingIdmStatus;
    }
}