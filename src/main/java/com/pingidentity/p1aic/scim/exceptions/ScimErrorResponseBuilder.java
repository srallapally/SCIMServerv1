package com.pingidentity.p1aic.scim.exceptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.schema.ScimSchemaUrns;

import java.util.logging.Logger;

/**
 * Builder for SCIM-compliant error responses.
 *
 * SCIM error responses follow the format defined in RFC 7644 Section 3.12:
 * {
 *   "schemas": ["urn:ietf:params:scim:api:messages:2.0:Error"],
 *   "status": "400",
 *   "scimType": "invalidValue",
 *   "detail": "The request body contains invalid data"
 * }
 */
public class ScimErrorResponseBuilder {

    private static final Logger LOGGER = Logger.getLogger(ScimErrorResponseBuilder.class.getName());

    private final ObjectMapper objectMapper;

    /**
     * Constructor initializes the ObjectMapper.
     */
    public ScimErrorResponseBuilder() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Build a SCIM error response with status code and detail message.
     *
     * @param statusCode the HTTP status code
     * @param detail the error detail message
     * @return JSON string representing the SCIM error response
     */
    public String buildErrorResponse(int statusCode, String detail) {
        return buildErrorResponse(statusCode, detail, null);
    }

    /**
     * Build a SCIM error response with status code, detail message, and SCIM type.
     *
     * @param statusCode the HTTP status code
     * @param detail the error detail message
     * @param scimType the SCIM error type (optional, can be null)
     * @return JSON string representing the SCIM error response
     */
    public String buildErrorResponse(int statusCode, String detail, String scimType) {
        try {
            ObjectNode errorNode = objectMapper.createObjectNode();

            // Add schemas array (required)
            ArrayNode schemas = objectMapper.createArrayNode();
            schemas.add(ScimSchemaUrns.ERROR);
            errorNode.set("schemas", schemas);

            // Add status (required) - as string per SCIM spec
            errorNode.put("status", String.valueOf(statusCode));

            // Add scimType (optional)
            if (scimType != null && !scimType.trim().isEmpty()) {
                errorNode.put("scimType", scimType);
            }

            // Add detail message (required)
            if (detail != null && !detail.trim().isEmpty()) {
                errorNode.put("detail", sanitizeDetail(detail));
            } else {
                errorNode.put("detail", getDefaultDetailForStatus(statusCode));
            }

            // Convert to JSON string
            return objectMapper.writeValueAsString(errorNode);

        } catch (Exception e) {
            LOGGER.severe("Failed to build SCIM error response: " + e.getMessage());
            // Return a fallback error response
            return buildFallbackErrorResponse(statusCode, detail);
        }
    }

    /**
     * Build an error response object node (for programmatic use).
     *
     * @param statusCode the HTTP status code
     * @param detail the error detail message
     * @param scimType the SCIM error type (optional)
     * @return ObjectNode representing the error response
     */
    public ObjectNode buildErrorResponseNode(int statusCode, String detail, String scimType) {
        ObjectNode errorNode = objectMapper.createObjectNode();

        // Add schemas array
        ArrayNode schemas = objectMapper.createArrayNode();
        schemas.add(ScimSchemaUrns.ERROR);
        errorNode.set("schemas", schemas);

        // Add status
        errorNode.put("status", String.valueOf(statusCode));

        // Add scimType if provided
        if (scimType != null && !scimType.trim().isEmpty()) {
            errorNode.put("scimType", scimType);
        }

        // Add detail
        if (detail != null && !detail.trim().isEmpty()) {
            errorNode.put("detail", sanitizeDetail(detail));
        } else {
            errorNode.put("detail", getDefaultDetailForStatus(statusCode));
        }

        return errorNode;
    }

    /**
     * Sanitize error detail message to prevent injection attacks.
     * Removes or escapes special characters that could break JSON or cause XSS.
     */
    private String sanitizeDetail(String detail) {
        if (detail == null) {
            return "";
        }

        // Remove control characters
        String sanitized = detail.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");

        // Limit length to prevent overly long error messages
        if (sanitized.length() > 500) {
            sanitized = sanitized.substring(0, 497) + "...";
        }

        return sanitized;
    }

    /**
     * Get default detail message for a given status code.
     */
    private String getDefaultDetailForStatus(int statusCode) {
        return switch (statusCode) {
            case 400 -> "Bad Request: The request is malformed or contains invalid data";
            case 401 -> "Unauthorized: Authentication is required";
            case 403 -> "Forbidden: The authenticated user does not have permission to perform this operation";
            case 404 -> "Not Found: The specified resource does not exist";
            case 409 -> "Conflict: The request could not be completed due to a conflict with the current state of the resource";
            case 412 -> "Precondition Failed: The version specified in the If-Match header does not match the current version";
            case 429 -> "Too Many Requests: Rate limit exceeded";
            case 500 -> "Internal Server Error: An unexpected error occurred on the server";
            case 501 -> "Not Implemented: The requested operation is not supported";
            case 503 -> "Service Unavailable: The service is temporarily unavailable";
            default -> "An error occurred while processing the request";
        };
    }

    /**
     * Build a simple fallback error response when JSON serialization fails.
     * This ensures we always return a valid response even if there's an error building the error.
     */
    private String buildFallbackErrorResponse(int statusCode, String detail) {
        String safeDetail = detail != null ? escapeJson(detail) : "An error occurred";

        return String.format(
                "{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:Error\"]," +
                        "\"status\":\"%d\"," +
                        "\"detail\":\"%s\"}",
                statusCode,
                safeDetail
        );
    }

    /**
     * Escape special characters in JSON strings.
     */
    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f");
    }

    /**
     * Build a validation error response with multiple validation errors.
     *
     * @param statusCode the HTTP status code (typically 400)
     * @param validationErrors array of validation error messages
     * @return JSON string representing the SCIM error response
     */
    public String buildValidationErrorResponse(int statusCode, String[] validationErrors) {
        StringBuilder detail = new StringBuilder("Validation failed: ");

        for (int i = 0; i < validationErrors.length; i++) {
            detail.append(validationErrors[i]);
            if (i < validationErrors.length - 1) {
                detail.append("; ");
            }
        }

        return buildErrorResponse(statusCode, detail.toString(), "invalidValue");
    }

    /**
     * Build an unauthorized error response.
     *
     * @param detail the error detail message
     * @return JSON string representing the SCIM error response
     */
    public String buildUnauthorizedError(String detail) {
        return buildErrorResponse(401, detail, null);
    }

    /**
     * Build a forbidden error response.
     *
     * @param detail the error detail message
     * @return JSON string representing the SCIM error response
     */
    public String buildForbiddenError(String detail) {
        return buildErrorResponse(403, detail, null);
    }

    /**
     * Build a not found error response.
     *
     * @param resourceType the type of resource that was not found
     * @param resourceId the ID of the resource that was not found
     * @return JSON string representing the SCIM error response
     */
    public String buildNotFoundError(String resourceType, String resourceId) {
        String detail = String.format("%s with ID '%s' not found", resourceType, resourceId);
        return buildErrorResponse(404, detail, "noTarget");
    }

    /**
     * Build a conflict error response (typically for uniqueness violations).
     *
     * @param detail the error detail message
     * @return JSON string representing the SCIM error response
     */
    public String buildConflictError(String detail) {
        return buildErrorResponse(409, detail, "uniqueness");
    }

    /**
     * Build an invalid filter error response.
     *
     * @param detail the error detail message
     * @return JSON string representing the SCIM error response
     */
    public String buildInvalidFilterError(String detail) {
        return buildErrorResponse(400, detail, "invalidFilter");
    }

    /**
     * Build a mutability error response (for read-only attributes).
     *
     * @param attributeName the name of the read-only attribute
     * @return JSON string representing the SCIM error response
     */
    public String buildMutabilityError(String attributeName) {
        String detail = String.format("Attribute '%s' is read-only and cannot be modified", attributeName);
        return buildErrorResponse(400, detail, "mutability");
    }
}