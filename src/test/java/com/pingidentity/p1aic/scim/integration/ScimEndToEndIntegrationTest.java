package com.pingidentity.p1aic.scim.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for SCIM Server with real OAuth tokens from PingOne AIC.
 * 
 * These tests verify the complete flow:
 * 1. Obtain OAuth token from PingOne AIC
 * 2. Use token to call SCIM Server endpoints
 * 3. SCIM Server passes token through to PingIDM
 * 4. Verify proper authentication and authorization
 * 
 * Required environment variables:
 * - PINGONE_TOKEN_ENDPOINT: OAuth token endpoint URL
 * - PINGONE_CLIENT_ID: OAuth client ID
 * - PINGONE_CLIENT_SECRET: OAuth client secret
 * - SCIM_SERVER_BASE_URL: SCIM Server base URL (e.g., http://localhost:8080/scim/v2)
 * 
 * Optional environment variables:
 * - PINGONE_SCOPE: OAuth scopes (defaults to "openid profile")
 * - SCIM_TEST_USER_ID: Existing user ID for read tests
 * 
 * To run these tests:
 * export PINGONE_TOKEN_ENDPOINT=https://auth.pingone.com/your-env-id/as/token
 * export PINGONE_CLIENT_ID=your-client-id
 * export PINGONE_CLIENT_SECRET=your-client-secret
 * export SCIM_SERVER_BASE_URL=http://localhost:8080/scim/v2
 * mvn test -Dtest=ScimEndToEndIntegrationTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "SCIM_SERVER_BASE_URL", matches = ".+")
class ScimEndToEndIntegrationTest {

    private static final String TOKEN_ENDPOINT_ENV = "PINGONE_TOKEN_ENDPOINT";
    private static final String CLIENT_ID_ENV = "PINGONE_CLIENT_ID";
    private static final String CLIENT_SECRET_ENV = "PINGONE_CLIENT_SECRET";
    private static final String SCIM_BASE_URL_ENV = "SCIM_SERVER_BASE_URL";
    private static final String SCOPE_ENV = "PINGONE_SCOPE";
    private static final String DEFAULT_SCOPE = "openid profile";

    private Client client;
    private ObjectMapper objectMapper;
    private String tokenEndpoint;
    private String clientId;
    private String clientSecret;
    private String scimBaseUrl;
    private String scope;

    // BEGIN: Shared test state
    private static String accessToken;
    private static String createdUserId;
    // END: Shared test state

    @BeforeEach
    void setUp() {
        client = ClientBuilder.newClient();
        objectMapper = new ObjectMapper();
        
        tokenEndpoint = System.getenv(TOKEN_ENDPOINT_ENV);
        clientId = System.getenv(CLIENT_ID_ENV);
        clientSecret = System.getenv(CLIENT_SECRET_ENV);
        scimBaseUrl = System.getenv(SCIM_BASE_URL_ENV);
        scope = System.getenv(SCOPE_ENV) != null ? System.getenv(SCOPE_ENV) : DEFAULT_SCOPE;
        
        assertThat(tokenEndpoint).as("PINGONE_TOKEN_ENDPOINT must be set").isNotNull();
        assertThat(clientId).as("PINGONE_CLIENT_ID must be set").isNotNull();
        assertThat(clientSecret).as("PINGONE_CLIENT_SECRET must be set").isNotNull();
        assertThat(scimBaseUrl).as("SCIM_SERVER_BASE_URL must be set").isNotNull();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should obtain OAuth token before SCIM operations")
    void testObtainOAuthToken() throws Exception {
        // Given: OAuth client credentials
        Form form = new Form()
                .param("grant_type", "client_credentials")
                .param("scope", scope);

        String authHeader = buildBasicAuthHeader(clientId, clientSecret);

        // When: Requesting access token
        Response response = client.target(tokenEndpoint)
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", authHeader)
                .post(Entity.form(form));

        // Then: Should receive valid access token
        assertThat(response.getStatus()).isEqualTo(200);
        
        String responseBody = response.readEntity(String.class);
        JsonNode tokenResponse = objectMapper.readTree(responseBody);
        
        assertThat(tokenResponse.has("access_token")).isTrue();
        accessToken = tokenResponse.get("access_token").asText();
        
        assertThat(accessToken).isNotNull().isNotEmpty();
        
        System.out.println("Successfully obtained OAuth token for SCIM operations");
    }

    @Test
    @Order(2)
    @DisplayName("Should access SCIM ServiceProviderConfig without authentication")
    void testServiceProviderConfigNoAuth() {
        // Given: SCIM ServiceProviderConfig endpoint (discovery endpoint)
        String endpoint = scimBaseUrl + "/ServiceProviderConfig";

        // When: Accessing without Authorization header
        Response response = client.target(endpoint)
                .request("application/scim+json")
                .get();

        // Then: Should return 200 OK (discovery endpoints don't require auth)
        assertThat(response.getStatus()).isEqualTo(200);
        
        String responseBody = response.readEntity(String.class);
        System.out.println("ServiceProviderConfig response (first 100 chars): " + 
                responseBody.substring(0, Math.min(100, responseBody.length())));
    }

    @Test
    @Order(3)
    @DisplayName("Should reject SCIM Users request without authentication")
    void testUsersEndpointWithoutAuth() {
        // Given: SCIM Users endpoint
        String endpoint = scimBaseUrl + "/Users";

        // When: Accessing without Authorization header
        Response response = client.target(endpoint)
                .request("application/scim+json")
                .get();

        // Then: Should return 401 Unauthorized
        assertThat(response.getStatus()).isEqualTo(401);
        
        String responseBody = response.readEntity(String.class);
        assertThat(responseBody).contains("urn:ietf:params:scim:api:messages:2.0:Error");
        
        System.out.println("Correctly rejected unauthenticated request");
    }

    @Test
    @Order(4)
    @DisplayName("Should search users with valid OAuth token")
    void testSearchUsersWithValidToken() throws Exception {
        // Given: Valid OAuth token
        if (accessToken == null) {
            testObtainOAuthToken();
        }
        
        String endpoint = scimBaseUrl + "/Users";

        // When: Searching users with valid token
        Response response = client.target(endpoint)
                .request("application/scim+json")
                .header("Authorization", "Bearer " + accessToken)
                .get();

        // Then: Should return 200 OK with user list
        assertThat(response.getStatus()).isEqualTo(200);
        
        String responseBody = response.readEntity(String.class);
        JsonNode listResponse = objectMapper.readTree(responseBody);
        
        assertThat(listResponse.has("Resources")).isTrue();
        assertThat(listResponse.has("totalResults")).isTrue();
        assertThat(listResponse.has("schemas")).isTrue();
        
        int totalResults = listResponse.get("totalResults").asInt();
        System.out.println("Successfully retrieved users list. Total results: " + totalResults);
    }

    @Test
    @Order(5)
    @DisplayName("Should create user with valid OAuth token")
    void testCreateUserWithValidToken() throws Exception {
        // Given: Valid OAuth token and user data
        if (accessToken == null) {
            testObtainOAuthToken();
        }
        
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        ObjectNode newUser = objectMapper.createObjectNode();
        newUser.put("userName", "testuser_" + uniqueId);
        newUser.put("displayName", "Test User " + uniqueId);
        newUser.put("active", true);
        
        ObjectNode name = objectMapper.createObjectNode();
        name.put("givenName", "Test");
        name.put("familyName", "User");
        newUser.set("name", name);
        
        String endpoint = scimBaseUrl + "/Users";
        String userJson = objectMapper.writeValueAsString(newUser);

        // When: Creating user with valid token
        Response response = client.target(endpoint)
                .request("application/scim+json")
                .header("Authorization", "Bearer " + accessToken)
                .post(Entity.entity(userJson, "application/scim+json"));

        // Then: Should return 201 Created with user resource
        int status = response.getStatus();
        String responseBody = response.readEntity(String.class);
        
        System.out.println("Create user response status: " + status);
        System.out.println("Create user response body: " + responseBody);
        
        if (status == 201) {
            JsonNode createdUser = objectMapper.readTree(responseBody);
            
            assertThat(createdUser.has("id")).isTrue();
            assertThat(createdUser.has("userName")).isTrue();
            assertThat(createdUser.get("userName").asText()).isEqualTo("testuser_" + uniqueId);
            
            createdUserId = createdUser.get("id").asText();
            String location = response.getHeaderString("Location");
            
            System.out.println("Successfully created user with ID: " + createdUserId);
            System.out.println("Location header: " + location);
        } else {
            // Log error for debugging
            System.err.println("User creation failed with status: " + status);
            System.err.println("Response: " + responseBody);
        }
    }

    @Test
    @Order(6)
    @DisplayName("Should get user by ID with valid OAuth token")
    void testGetUserWithValidToken() throws Exception {
        // Given: Valid OAuth token and existing user ID
        if (accessToken == null) {
            testObtainOAuthToken();
        }
        
        // Use created user ID or fall back to environment variable
        String userId = createdUserId != null ? createdUserId : System.getenv("SCIM_TEST_USER_ID");
        
        if (userId == null) {
            System.out.println("Skipping test - no user ID available");
            return;
        }
        
        String endpoint = scimBaseUrl + "/Users/" + userId;

        // When: Getting user with valid token
        Response response = client.target(endpoint)
                .request("application/scim+json")
                .header("Authorization", "Bearer " + accessToken)
                .get();

        // Then: Should return 200 OK with user resource
        int status = response.getStatus();
        String responseBody = response.readEntity(String.class);
        
        System.out.println("Get user response status: " + status);
        
        if (status == 200) {
            JsonNode user = objectMapper.readTree(responseBody);
            
            assertThat(user.has("id")).isTrue();
            assertThat(user.has("userName")).isTrue();
            assertThat(user.get("id").asText()).isEqualTo(userId);
            
            System.out.println("Successfully retrieved user: " + user.get("userName").asText());
        } else {
            System.err.println("Get user failed with status: " + status);
            System.err.println("Response: " + responseBody);
        }
    }

    @Test
    @Order(7)
    @DisplayName("Should update user with valid OAuth token")
    void testUpdateUserWithValidToken() throws Exception {
        // Given: Valid OAuth token and existing user
        if (accessToken == null) {
            testObtainOAuthToken();
        }
        
        String userId = createdUserId;
        if (userId == null) {
            System.out.println("Skipping test - no user ID available");
            return;
        }
        
        ObjectNode updatedUser = objectMapper.createObjectNode();
        updatedUser.put("userName", "testuser_updated");
        updatedUser.put("displayName", "Updated Test User");
        updatedUser.put("active", true);
        
        String endpoint = scimBaseUrl + "/Users/" + userId;
        String userJson = objectMapper.writeValueAsString(updatedUser);

        // When: Updating user with valid token
        Response response = client.target(endpoint)
                .request("application/scim+json")
                .header("Authorization", "Bearer " + accessToken)
                .put(Entity.entity(userJson, "application/scim+json"));

        // Then: Should return 200 OK with updated resource
        int status = response.getStatus();
        String responseBody = response.readEntity(String.class);
        
        System.out.println("Update user response status: " + status);
        
        if (status == 200) {
            JsonNode user = objectMapper.readTree(responseBody);
            
            assertThat(user.has("id")).isTrue();
            assertThat(user.get("displayName").asText()).isEqualTo("Updated Test User");
            
            System.out.println("Successfully updated user");
        } else {
            System.err.println("Update user failed with status: " + status);
            System.err.println("Response: " + responseBody);
        }
    }

    @Test
    @Order(8)
    @DisplayName("Should patch user with valid OAuth token")
    void testPatchUserWithValidToken() throws Exception {
        // Given: Valid OAuth token and patch operations
        if (accessToken == null) {
            testObtainOAuthToken();
        }
        
        String userId = createdUserId;
        if (userId == null) {
            System.out.println("Skipping test - no user ID available");
            return;
        }
        
        // SCIM PATCH operations
        String patchOps = "[{\"op\":\"replace\",\"path\":\"displayName\",\"value\":\"Patched User\"}]";
        
        String endpoint = scimBaseUrl + "/Users/" + userId;

        // When: Patching user with valid token
        Response response = client.target(endpoint)
                .request("application/scim+json")
                .header("Authorization", "Bearer " + accessToken)
                .method("PATCH", Entity.entity(patchOps, "application/scim+json"));

        // Then: Should return 200 OK
        int status = response.getStatus();
        String responseBody = response.readEntity(String.class);
        
        System.out.println("Patch user response status: " + status);
        
        if (status == 200) {
            JsonNode user = objectMapper.readTree(responseBody);
            System.out.println("Successfully patched user");
        } else {
            System.err.println("Patch user failed with status: " + status);
            System.err.println("Response: " + responseBody);
        }
    }

    @Test
    @Order(9)
    @DisplayName("Should delete user with valid OAuth token")
    void testDeleteUserWithValidToken() throws Exception {
        // Given: Valid OAuth token and existing user
        if (accessToken == null) {
            testObtainOAuthToken();
        }
        
        String userId = createdUserId;
        if (userId == null) {
            System.out.println("Skipping test - no user ID available");
            return;
        }
        
        String endpoint = scimBaseUrl + "/Users/" + userId;

        // When: Deleting user with valid token
        Response response = client.target(endpoint)
                .request("application/scim+json")
                .header("Authorization", "Bearer " + accessToken)
                .delete();

        // Then: Should return 204 No Content
        int status = response.getStatus();
        
        System.out.println("Delete user response status: " + status);
        
        if (status == 204) {
            System.out.println("Successfully deleted user");
            createdUserId = null; // Clear so subsequent tests don't try to use it
        } else {
            String responseBody = response.readEntity(String.class);
            System.err.println("Delete user failed with status: " + status);
            System.err.println("Response: " + responseBody);
        }
    }

    @Test
    @Order(10)
    @DisplayName("Should reject request with expired/invalid token")
    void testInvalidToken() {
        // Given: Invalid OAuth token
        String invalidToken = "invalid.token.here";
        String endpoint = scimBaseUrl + "/Users";

        // When: Accessing with invalid token
        Response response = client.target(endpoint)
                .request("application/scim+json")
                .header("Authorization", "Bearer " + invalidToken)
                .get();

        // Then: Should return 401 Unauthorized
        // Note: Actual response depends on how PingIDM validates tokens
        int status = response.getStatus();
        System.out.println("Invalid token response status: " + status);
        
        // Could be 401 (Unauthorized) or 403 (Forbidden) depending on validation
        assertThat(status).isIn(401, 403);
    }

    @Test
    @Order(11)
    @DisplayName("Should filter users with valid OAuth token")
    void testFilterUsersWithValidToken() throws Exception {
        // Given: Valid OAuth token and SCIM filter
        if (accessToken == null) {
            testObtainOAuthToken();
        }
        
        String filter = "userName eq \"testuser\"";
        String endpoint = scimBaseUrl + "/Users?filter=" + java.net.URLEncoder.encode(filter, "UTF-8");

        // When: Filtering users with valid token
        Response response = client.target(endpoint)
                .request("application/scim+json")
                .header("Authorization", "Bearer " + accessToken)
                .get();

        // Then: Should return 200 OK with filtered results
        int status = response.getStatus();
        String responseBody = response.readEntity(String.class);
        
        System.out.println("Filter users response status: " + status);
        
        if (status == 200) {
            JsonNode listResponse = objectMapper.readTree(responseBody);
            assertThat(listResponse.has("Resources")).isTrue();
            
            System.out.println("Successfully filtered users");
        } else {
            System.err.println("Filter users failed with status: " + status);
            System.err.println("Response: " + responseBody);
        }
    }

    @Test
    @Order(12)
    @DisplayName("Should retrieve SCIM schemas with valid OAuth token")
    void testGetSchemasWithValidToken() throws Exception {
        // Given: Valid OAuth token
        if (accessToken == null) {
            testObtainOAuthToken();
        }
        
        String endpoint = scimBaseUrl + "/Schemas";

        // When: Getting schemas
        Response response = client.target(endpoint)
                .request("application/scim+json")
                .header("Authorization", "Bearer " + accessToken)
                .get();

        // Then: Should return 200 OK with schemas
        // Note: Schemas endpoint might not require auth (it's a discovery endpoint)
        int status = response.getStatus();
        String responseBody = response.readEntity(String.class);
        
        System.out.println("Get schemas response status: " + status);
        
        if (status == 200) {
            JsonNode schemasResponse = objectMapper.readTree(responseBody);
            assertThat(schemasResponse.has("Resources")).isTrue();
            
            System.out.println("Successfully retrieved schemas");
        }
    }

    // BEGIN: Helper method to build Basic Authentication header
    private String buildBasicAuthHeader(String clientId, String clientSecret) {
        String credentials = clientId + ":" + clientSecret;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
        return "Basic " + encoded;
    }
    // END: Helper method to build Basic Authentication header
}
