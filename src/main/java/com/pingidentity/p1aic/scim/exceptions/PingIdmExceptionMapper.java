package com.pingidentity.p1aic.scim.exceptions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PingIdmExceptionMapper {

    private static final Logger LOGGER = Logger.getLogger(PingIdmExceptionMapper.class.getName());

    private final ObjectMapper objectMapper;
    private final ScimErrorResponseBuilder errorBuilder;

    public PingIdmExceptionMapper() {
        this.objectMapper = new ObjectMapper();
        this.errorBuilder = new ScimErrorResponseBuilder();
    }

    public String mapPingIdmError(Response response) {
        int statusCode = response.getStatus();
        String detail = "An error occurred while processing the request";
        String scimType = null;

        try {
            String responseBody = response.readEntity(String.class);

            if (responseBody != null && !responseBody.isEmpty()) {
                JsonNode errorNode = objectMapper.readTree(responseBody);

                if (errorNode.has("message")) {
                    detail = errorNode.get("message").asText();
                } else if (errorNode.has("detail")) {
                    detail = errorNode.get("detail").asText();
                } else if (errorNode.has("reason")) {
                    detail = errorNode.get("reason").asText();
                }

                if (errorNode.has("code")) {
                    String code = errorNode.get("code").asText();
                    scimType = mapPingIdmCodeToScimType(code);
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse PingIDM error response", e);
        }

        if (scimType == null) {
            scimType = inferScimTypeFromStatus(statusCode, detail);
        }

        // Map 403 Policy Validation (likely uniqueness) to 409 for SCIM compliance
        if (statusCode == 403 && (scimType == null || "invalidValue".equals(scimType))) {
            if (detail != null && detail.contains("Policy")) {
                statusCode = 409;
                scimType = "uniqueness";
            }
        }

        return errorBuilder.buildErrorResponse(statusCode, detail, scimType);
    }

    public Response mapToScimResponse(Response pingIdmResponse) {
        int statusCode = pingIdmResponse.getStatus();
        String errorResponse = mapPingIdmError(pingIdmResponse);

        // Adjust status code if mapped (e.g. 403 -> 409)
        if (statusCode == 403 && errorResponse.contains("\"status\":\"409\"")) {
            statusCode = 409;
        }

        return Response.status(statusCode)
                .entity(errorResponse)
                .type("application/scim+json")
                .build();
    }

    private String mapPingIdmCodeToScimType(String code) {
        if (code == null) return null;
        return switch (code.toLowerCase()) {
            case "duplicate", "uniqueness", "already_exists" -> "uniqueness";
            case "not_found", "no_such_object" -> "noTarget";
            case "invalid_value", "constraint_violation" -> "invalidValue";
            case "bad_request", "invalid_request" -> "invalidSyntax";
            case "version_mismatch", "precondition_failed" -> "mutability";
            default -> null;
        };
    }

    private String inferScimTypeFromStatus(int statusCode, String detail) {
        String lowerDetail = detail != null ? detail.toLowerCase() : "";
        if (lowerDetail.contains("unique") || lowerDetail.contains("duplicate") || lowerDetail.contains("already exists")) {
            return "uniqueness";
        } else if (lowerDetail.contains("not found")) {
            return "noTarget";
        }
        return switch (statusCode) {
            case 404 -> "noTarget";
            case 409 -> "uniqueness";
            case 412 -> "mutability";
            default -> null;
        };
    }
}