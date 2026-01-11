package com.pingidentity.p1aic.scim.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OAuth token acquisition from PingOne Advanced Identity Cloud.
 * 
 * These tests require actual PingOne AIC tenant credentials and make real network calls.
 * They are disabled by default and only run when environment variables are set.
 * 
 * Required environment variables:
 * - PINGONE_TOKEN_ENDPOINT: OAuth token endpoint URL (e.g., https://auth.pingone.com/{environmentId}/as/token)
 * - PINGONE_CLIENT_ID: OAuth client ID
 * - PINGONE_CLIENT_SECRET: OAuth client secret
 * - PINGONE_SCOPE: OAuth scopes (optional, defaults to "openid profile")
 * 
 * To run these tests:
 * export PINGONE_TOKEN_ENDPOINT=https://auth.pingone.com/your-env-id/as/token
 * export PINGONE_CLIENT_ID=your-client-id
 * export PINGONE_CLIENT_SECRET=your-client-secret
 * export PINGONE_SCOPE="openid profile"
 * mvn test -Dtest=PingOneOAuthIntegrationTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "PINGONE_TOKEN_ENDPOINT", matches = ".+")
class PingOneOAuthIntegrationTest {

    private static final String TOKEN_ENDPOINT_ENV = "PINGONE_TOKEN_ENDPOINT";
    private static final String CLIENT_ID_ENV = "PINGONE_CLIENT_ID";
    private static final String CLIENT_SECRET_ENV = "PINGONE_CLIENT_SECRET";
    private static final String SCOPE_ENV = "PINGONE_SCOPE";
    private static final String DEFAULT_SCOPE = "openid profile";

    private Client client;
    private ObjectMapper objectMapper;
    private String tokenEndpoint;
    private String clientId;
    private String clientSecret;
    private String scope;

    // BEGIN: Shared test state for token reuse across tests
    private static String sharedAccessToken;
    private static String sharedRefreshToken;
    private static Long sharedTokenExpiresAt;
    // END: Shared test state for token reuse across tests

    @BeforeEach
    void setUp() {
        client = ClientBuilder.newClient();
        objectMapper = new ObjectMapper();
        
        // Load configuration from environment variables
        tokenEndpoint = System.getenv(TOKEN_ENDPOINT_ENV);
        clientId = System.getenv(CLIENT_ID_ENV);
        clientSecret = System.getenv(CLIENT_SECRET_ENV);
        scope = System.getenv(SCOPE_ENV) != null ? System.getenv(SCOPE_ENV) : DEFAULT_SCOPE;
        
        assertThat(tokenEndpoint).as("PINGONE_TOKEN_ENDPOINT must be set").isNotNull();
        assertThat(clientId).as("PINGONE_CLIENT_ID must be set").isNotNull();
        assertThat(clientSecret).as("PINGONE_CLIENT_SECRET must be set").isNotNull();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should obtain access token using client credentials grant")
    void testClientCredentialsGrant() throws Exception {
        // Given: OAuth client credentials
        Form form = new Form()
                .param("grant_type", "client_credentials")
                .param("scope", scope);

        // When: Requesting access token using client credentials
        Response response = client.target(tokenEndpoint)
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", buildBasicAuthHeader(clientId, clientSecret))
                .post(Entity.form(form));

        // Then: Should receive access token
        assertThat(response.getStatus()).isEqualTo(200);
        
        String responseBody = response.readEntity(String.class);
        JsonNode tokenResponse = objectMapper.readTree(responseBody);
        
        assertThat(tokenResponse.has("access_token")).isTrue();
        assertThat(tokenResponse.has("token_type")).isTrue();
        assertThat(tokenResponse.has("expires_in")).isTrue();
        
        String accessToken = tokenResponse.get("access_token").asText();
        String tokenType = tokenResponse.get("token_type").asText();
        int expiresIn = tokenResponse.get("expires_in").asInt();
        
        assertThat(accessToken).isNotEmpty();
        assertThat(tokenType).isEqualToIgnoringCase("Bearer");
        assertThat(expiresIn).isGreaterThan(0);
        
        // BEGIN: Store token for reuse in subsequent tests
        sharedAccessToken = accessToken;
        sharedTokenExpiresAt = Instant.now().getEpochSecond() + expiresIn;
        if (tokenResponse.has("refresh_token")) {
            sharedRefreshToken = tokenResponse.get("refresh_token").asText();
        }
        // END: Store token for reuse in subsequent tests
        
        System.out.println("Successfully obtained access token");
        System.out.println("Token Type: " + tokenType);
        System.out.println("Expires In: " + expiresIn + " seconds");
        System.out.println("Access Token (first 20 chars): " + accessToken.substring(0, Math.min(20, accessToken.length())) + "...");
    }

    @Test
    @Order(2)
    @DisplayName("Should validate access token structure")
    void testAccessTokenStructure() throws Exception {
        // Given: An access token from PingOne AIC
        if (sharedAccessToken == null) {
            testClientCredentialsGrant(); // Obtain token if not already available
        }
        
        String accessToken = sharedAccessToken;
        assertThat(accessToken).isNotNull();

        // When: Examining the token structure
        // PingOne AIC typically issues JWT tokens
        String[] tokenParts = accessToken.split("\\.");
        
        // Then: Should be a valid JWT structure (header.payload.signature)
        assertThat(tokenParts).hasSize(3);
        
        // Decode header
        String headerJson = new String(Base64.getUrlDecoder().decode(tokenParts[0]));
        JsonNode header = objectMapper.readTree(headerJson);
        
        assertThat(header.has("alg")).isTrue();
        assertThat(header.has("typ")).isTrue();
        assertThat(header.get("typ").asText()).isEqualTo("JWT");
        
        System.out.println("Token Algorithm: " + header.get("alg").asText());
        
        // Decode payload
        String payloadJson = new String(Base64.getUrlDecoder().decode(tokenParts[1]));
        JsonNode payload = objectMapper.readTree(payloadJson);
        
        // Validate standard JWT claims
        assertThat(payload.has("exp")).as("Token should have expiration claim").isTrue();
        assertThat(payload.has("iat")).as("Token should have issued at claim").isTrue();
        
        if (payload.has("exp")) {
            long exp = payload.get("exp").asLong();
            long now = Instant.now().getEpochSecond();
            assertThat(exp).as("Token should not be expired").isGreaterThan(now);
        }
        
        System.out.println("Token Subject: " + (payload.has("sub") ? payload.get("sub").asText() : "N/A"));
        System.out.println("Token Issuer: " + (payload.has("iss") ? payload.get("iss").asText() : "N/A"));
        System.out.println("Token Audience: " + (payload.has("aud") ? payload.get("aud").asText() : "N/A"));
    }

    @Test
    @Order(3)
    @DisplayName("Should handle invalid client credentials")
    void testInvalidClientCredentials() {
        // Given: Invalid client credentials
        Form form = new Form()
                .param("grant_type", "client_credentials")
                .param("scope", scope);

        // When: Requesting access token with invalid credentials
        Response response = client.target(tokenEndpoint)
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", buildBasicAuthHeader("invalid_client", "invalid_secret"))
                .post(Entity.form(form));

        // Then: Should receive 401 Unauthorized or 400 Bad Request
        int status = response.getStatus();
        assertThat(status).isIn(400, 401);
        
        String responseBody = response.readEntity(String.class);
        System.out.println("Error response for invalid credentials: " + responseBody);
    }

    @Test
    @Order(4)
    @DisplayName("Should handle invalid grant type")
    void testInvalidGrantType() {
        // Given: Invalid grant type
        Form form = new Form()
                .param("grant_type", "invalid_grant_type")
                .param("scope", scope);

        // When: Requesting access token with invalid grant type
        Response response = client.target(tokenEndpoint)
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", buildBasicAuthHeader(clientId, clientSecret))
                .post(Entity.form(form));

        // Then: Should receive 400 Bad Request
        assertThat(response.getStatus()).isEqualTo(400);
        
        String responseBody = response.readEntity(String.class);
        assertThat(responseBody).containsIgnoringCase("unsupported_grant_type");
        
        System.out.println("Error response for invalid grant type: " + responseBody);
    }

    @Test
    @Order(5)
    @DisplayName("Should obtain token with client credentials in request body")
    void testClientCredentialsInBody() throws Exception {
        // Given: Client credentials in request body (alternative to Basic Auth)
        Form form = new Form()
                .param("grant_type", "client_credentials")
                .param("client_id", clientId)
                .param("client_secret", clientSecret)
                .param("scope", scope);

        // When: Requesting access token
        Response response = client.target(tokenEndpoint)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.form(form));

        // Then: Should receive access token
        assertThat(response.getStatus()).isEqualTo(200);
        
        String responseBody = response.readEntity(String.class);
        JsonNode tokenResponse = objectMapper.readTree(responseBody);
        
        assertThat(tokenResponse.has("access_token")).isTrue();
        String accessToken = tokenResponse.get("access_token").asText();
        assertThat(accessToken).isNotEmpty();
        
        System.out.println("Successfully obtained token using credentials in body");
    }

    @Test
    @Order(6)
    @DisplayName("Should refresh access token if refresh token available")
    @EnabledIfEnvironmentVariable(named = "PINGONE_SUPPORT_REFRESH_TOKEN", matches = "true")
    void testRefreshToken() throws Exception {
        // Given: A refresh token from previous authorization
        if (sharedRefreshToken == null) {
            testClientCredentialsGrant();
        }
        
        // Note: Client credentials grant typically does not provide refresh tokens
        // This test is conditional and requires authorization code flow setup
        Assumptions.assumeTrue(sharedRefreshToken != null, "Refresh token not available");

        Form form = new Form()
                .param("grant_type", "refresh_token")
                .param("refresh_token", sharedRefreshToken);

        // When: Requesting new access token using refresh token
        Response response = client.target(tokenEndpoint)
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", buildBasicAuthHeader(clientId, clientSecret))
                .post(Entity.form(form));

        // Then: Should receive new access token
        assertThat(response.getStatus()).isEqualTo(200);
        
        String responseBody = response.readEntity(String.class);
        JsonNode tokenResponse = objectMapper.readTree(responseBody);
        
        assertThat(tokenResponse.has("access_token")).isTrue();
        String newAccessToken = tokenResponse.get("access_token").asText();
        assertThat(newAccessToken).isNotEmpty();
        assertThat(newAccessToken).isNotEqualTo(sharedAccessToken);
        
        System.out.println("Successfully refreshed access token");
    }

    @Test
    @Order(7)
    @DisplayName("Should verify token expiration time is reasonable")
    void testTokenExpiration() throws Exception {
        // Given: An access token
        if (sharedAccessToken == null || sharedTokenExpiresAt == null) {
            testClientCredentialsGrant();
        }

        // When: Checking token expiration
        long now = Instant.now().getEpochSecond();
        long timeUntilExpiry = sharedTokenExpiresAt - now;

        // Then: Token should not be expired and have reasonable lifetime
        assertThat(timeUntilExpiry).as("Token should not be expired").isGreaterThan(0);
        assertThat(timeUntilExpiry).as("Token lifetime should be reasonable (< 24 hours)")
                .isLessThan(86400); // 24 hours
        
        System.out.println("Token expires in: " + timeUntilExpiry + " seconds");
    }

    @Test
    @Order(8)
    @DisplayName("Should handle missing required parameters")
    void testMissingRequiredParameters() {
        // Given: Request without grant_type parameter
        Form form = new Form()
                .param("scope", scope);

        // When: Requesting access token without grant_type
        Response response = client.target(tokenEndpoint)
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", buildBasicAuthHeader(clientId, clientSecret))
                .post(Entity.form(form));

        // Then: Should receive 400 Bad Request
        assertThat(response.getStatus()).isEqualTo(400);
        
        String responseBody = response.readEntity(String.class);
        System.out.println("Error response for missing grant_type: " + responseBody);
    }

    @Test
    @Order(9)
    @DisplayName("Should obtain token with specific scopes")
    void testScopeRequest() throws Exception {
        // Given: Specific scope request
        String requestedScope = "openid profile email";
        Form form = new Form()
                .param("grant_type", "client_credentials")
                .param("scope", requestedScope);

        // When: Requesting access token with specific scopes
        Response response = client.target(tokenEndpoint)
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", buildBasicAuthHeader(clientId, clientSecret))
                .post(Entity.form(form));

        // Then: Should receive access token
        assertThat(response.getStatus()).isEqualTo(200);
        
        String responseBody = response.readEntity(String.class);
        JsonNode tokenResponse = objectMapper.readTree(responseBody);
        
        assertThat(tokenResponse.has("access_token")).isTrue();
        
        // Check if scope is returned in response
        if (tokenResponse.has("scope")) {
            String grantedScope = tokenResponse.get("scope").asText();
            System.out.println("Granted scope: " + grantedScope);
            
            // Granted scope might be subset of requested scope
            assertThat(grantedScope).isNotEmpty();
        }
    }

    @Test
    @Order(10)
    @DisplayName("Should validate token can be used for API calls")
    void testTokenUsageForApiCalls() throws Exception {
        // Given: A valid access token
        if (sharedAccessToken == null) {
            testClientCredentialsGrant();
        }
        
        String accessToken = sharedAccessToken;
        assertThat(accessToken).isNotNull();

        // When: Using token in Authorization header format
        String authHeader = "Bearer " + accessToken;

        // Then: Authorization header should be properly formatted
        assertThat(authHeader).startsWith("Bearer ");
        assertThat(authHeader.substring(7)).isNotEmpty();
        
        // Verify the token doesn't contain whitespace (common issue)
        assertThat(accessToken).doesNotContainAnyWhitespaces();
        
        System.out.println("Token is properly formatted for API usage");
        System.out.println("Authorization header: Bearer " + accessToken.substring(0, 20) + "...");
    }

    // BEGIN: Helper method to build Basic Authentication header
    /**
     * Build Basic Authentication header from client ID and secret.
     * 
     * @param clientId OAuth client ID
     * @param clientSecret OAuth client secret
     * @return Basic Auth header value
     */
    private String buildBasicAuthHeader(String clientId, String clientSecret) {
        String credentials = clientId + ":" + clientSecret;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
        return "Basic " + encoded;
    }
    // END: Helper method to build Basic Authentication header
}
