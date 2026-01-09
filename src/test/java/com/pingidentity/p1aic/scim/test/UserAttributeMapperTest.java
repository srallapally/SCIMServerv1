package com.pingidentity.p1aic.scim.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pingidentity.p1aic.scim.mapping.UserAttributeMapper;
import com.unboundid.scim2.common.GenericScimResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for UserAttributeMapper.
 *
 * Tests the conversion between SCIM User resources and PingIDM user objects,
 * including handling of core attributes, complex attributes, and custom extensions.
 */
class UserAttributeMapperTest {

    private UserAttributeMapper mapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mapper = new UserAttributeMapper();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should convert SCIM User to PingIDM format with basic attributes")
    void testScimToPingIdm_BasicAttributes() {
        // Given: A SCIM user with basic attributes
        ObjectNode scimNode = objectMapper.createObjectNode();
        scimNode.put("userName", "john.doe");
        scimNode.put("displayName", "John Doe");
        scimNode.put("active", true);

        GenericScimResource scimUser = new GenericScimResource(scimNode);

        // When: Converting to PingIDM format
        ObjectNode idmUser = mapper.scimToPingIdm(scimUser);

        // Then: PingIDM object should have correct attributes
        assertThat(idmUser.has("userName")).isTrue();
        assertThat(idmUser.get("userName").asText()).isEqualTo("john.doe");
        assertThat(idmUser.get("displayName").asText()).isEqualTo("John Doe");
        assertThat(idmUser.get("accountStatus").asText()).isEqualTo("active");
    }

    @Test
    @DisplayName("Should convert active=false to accountStatus=inactive")
    void testScimToPingIdm_InactiveUser() {
        // Given: A SCIM user with active=false
        ObjectNode scimNode = objectMapper.createObjectNode();
        scimNode.put("userName", "john.doe");
        scimNode.put("active", false);

        GenericScimResource scimUser = new GenericScimResource(scimNode);

        // When: Converting to PingIDM format
        ObjectNode idmUser = mapper.scimToPingIdm(scimUser);

        // Then: accountStatus should be "inactive"
        assertThat(idmUser.get("accountStatus").asText()).isEqualTo("inactive");
    }

    @Test
    @DisplayName("Should convert SCIM name object to PingIDM flat attributes")
    void testScimToPingIdm_NameObject() {
        // Given: A SCIM user with name object
        ObjectNode scimNode = objectMapper.createObjectNode();
        scimNode.put("userName", "john.doe");

        ObjectNode nameNode = objectMapper.createObjectNode();
        nameNode.put("givenName", "John");
        nameNode.put("familyName", "Doe");
        nameNode.put("formatted", "John Doe");
        nameNode.put("middleName", "Michael");
        scimNode.set("name", nameNode);

        GenericScimResource scimUser = new GenericScimResource(scimNode);

        // When: Converting to PingIDM format
        ObjectNode idmUser = mapper.scimToPingIdm(scimUser);

        // Then: Name attributes should be flattened
        assertThat(idmUser.get("givenName").asText()).isEqualTo("John");
        assertThat(idmUser.get("sn").asText()).isEqualTo("Doe");
        assertThat(idmUser.get("cn").asText()).isEqualTo("John Doe");
        assertThat(idmUser.get("middleName").asText()).isEqualTo("Michael");
    }

    @Test
    @DisplayName("Should convert SCIM emails array to single PingIDM mail attribute")
    void testScimToPingIdm_EmailsArray() {
        // Given: A SCIM user with emails array
        ObjectNode scimNode = objectMapper.createObjectNode();
        scimNode.put("userName", "john.doe");

        ArrayNode emailsArray = objectMapper.createArrayNode();
        ObjectNode primaryEmail = objectMapper.createObjectNode();
        primaryEmail.put("value", "john.doe@example.com");
        primaryEmail.put("primary", true);
        primaryEmail.put("type", "work");
        emailsArray.add(primaryEmail);

        ObjectNode secondaryEmail = objectMapper.createObjectNode();
        secondaryEmail.put("value", "jdoe@personal.com");
        secondaryEmail.put("primary", false);
        secondaryEmail.put("type", "home");
        emailsArray.add(secondaryEmail);

        scimNode.set("emails", emailsArray);

        GenericScimResource scimUser = new GenericScimResource(scimNode);

        // When: Converting to PingIDM format
        ObjectNode idmUser = mapper.scimToPingIdm(scimUser);

        // Then: Primary email should be extracted
        assertThat(idmUser.has("mail")).isTrue();
        assertThat(idmUser.get("mail").asText()).isEqualTo("john.doe@example.com");
    }

    @Test
    @DisplayName("Should use first email when no primary is specified")
    void testScimToPingIdm_EmailsArray_NoPrimary() {
        // Given: A SCIM user with emails array but no primary flag
        ObjectNode scimNode = objectMapper.createObjectNode();
        scimNode.put("userName", "john.doe");

        ArrayNode emailsArray = objectMapper.createArrayNode();
        ObjectNode email = objectMapper.createObjectNode();
        email.put("value", "john.doe@example.com");
        email.put("type", "work");
        emailsArray.add(email);

        scimNode.set("emails", emailsArray);

        GenericScimResource scimUser = new GenericScimResource(scimNode);

        // When: Converting to PingIDM format
        ObjectNode idmUser = mapper.scimToPingIdm(scimUser);

        // Then: First email should be used
        assertThat(idmUser.get("mail").asText()).isEqualTo("john.doe@example.com");
    }

    @Test
    @DisplayName("Should convert PingIDM user to SCIM format with basic attributes")
    void testPingIdmToScim_BasicAttributes() {
        // Given: A PingIDM user with basic attributes
        ObjectNode idmUser = objectMapper.createObjectNode();
        idmUser.put("_id", "user123");
        idmUser.put("_rev", "1");
        idmUser.put("userName", "john.doe");
        idmUser.put("displayName", "John Doe");
        idmUser.put("accountStatus", "active");

        // When: Converting to SCIM format
        GenericScimResource scimUser = mapper.pingIdmToScim(idmUser);
        ObjectNode scimNode = scimUser.asGenericScimResource().getObjectNode();

        // Then: SCIM object should have correct attributes
        assertThat(scimNode.get("id").asText()).isEqualTo("user123");
        assertThat(scimNode.get("userName").asText()).isEqualTo("john.doe");
        assertThat(scimNode.get("displayName").asText()).isEqualTo("John Doe");
        assertThat(scimNode.get("active").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Should convert accountStatus=inactive to active=false")
    void testPingIdmToScim_InactiveUser() {
        // Given: A PingIDM user with accountStatus=inactive
        ObjectNode idmUser = objectMapper.createObjectNode();
        idmUser.put("_id", "user123");
        idmUser.put("userName", "john.doe");
        idmUser.put("accountStatus", "inactive");

        // When: Converting to SCIM format
        GenericScimResource scimUser = mapper.pingIdmToScim(idmUser);
        ObjectNode scimNode = scimUser.asGenericScimResource().getObjectNode();

        // Then: active should be false
        assertThat(scimNode.get("active").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Should convert PingIDM flat name attributes to SCIM name object")
    void testPingIdmToScim_NameObject() {
        // Given: A PingIDM user with name attributes
        ObjectNode idmUser = objectMapper.createObjectNode();
        idmUser.put("_id", "user123");
        idmUser.put("userName", "john.doe");
        idmUser.put("givenName", "John");
        idmUser.put("sn", "Doe");
        idmUser.put("cn", "John Doe");
        idmUser.put("middleName", "Michael");

        // When: Converting to SCIM format
        GenericScimResource scimUser = mapper.pingIdmToScim(idmUser);
        ObjectNode scimNode = scimUser.asGenericScimResource().getObjectNode();

        // Then: Name object should be created
        assertThat(scimNode.has("name")).isTrue();
        ObjectNode nameNode = (ObjectNode) scimNode.get("name");
        assertThat(nameNode.get("givenName").asText()).isEqualTo("John");
        assertThat(nameNode.get("familyName").asText()).isEqualTo("Doe");
        assertThat(nameNode.get("formatted").asText()).isEqualTo("John Doe");
        assertThat(nameNode.get("middleName").asText()).isEqualTo("Michael");
    }

    @Test
    @DisplayName("Should convert PingIDM mail to SCIM emails array")
    void testPingIdmToScim_EmailsArray() {
        // Given: A PingIDM user with mail attribute
        ObjectNode idmUser = objectMapper.createObjectNode();
        idmUser.put("_id", "user123");
        idmUser.put("userName", "john.doe");
        idmUser.put("mail", "john.doe@example.com");

        // When: Converting to SCIM format
        GenericScimResource scimUser = mapper.pingIdmToScim(idmUser);
        ObjectNode scimNode = scimUser.asGenericScimResource().getObjectNode();

        // Then: Emails array should be created
        assertThat(scimNode.has("emails")).isTrue();
        ArrayNode emailsArray = (ArrayNode) scimNode.get("emails");
        assertThat(emailsArray.size()).isEqualTo(1);

        ObjectNode emailObj = (ObjectNode) emailsArray.get(0);
        assertThat(emailObj.get("value").asText()).isEqualTo("john.doe@example.com");
        assertThat(emailObj.get("primary").asBoolean()).isTrue();
        assertThat(emailObj.get("type").asText()).isEqualTo("work");
    }

    @Test
    @DisplayName("Should convert PingIDM telephoneNumber to SCIM phoneNumbers array")
    void testPingIdmToScim_PhoneNumbersArray() {
        // Given: A PingIDM user with telephoneNumber attribute
        ObjectNode idmUser = objectMapper.createObjectNode();
        idmUser.put("_id", "user123");
        idmUser.put("userName", "john.doe");
        idmUser.put("telephoneNumber", "+1-555-1234");

        // When: Converting to SCIM format
        GenericScimResource scimUser = mapper.pingIdmToScim(idmUser);
        ObjectNode scimNode = scimUser.asGenericScimResource().getObjectNode();

        // Then: PhoneNumbers array should be created
        assertThat(scimNode.has("phoneNumbers")).isTrue();
        ArrayNode phonesArray = (ArrayNode) scimNode.get("phoneNumbers");
        assertThat(phonesArray.size()).isEqualTo(1);

        ObjectNode phoneObj = (ObjectNode) phonesArray.get(0);
        assertThat(phoneObj.get("value").asText()).isEqualTo("+1-555-1234");
        assertThat(phoneObj.get("primary").asBoolean()).isTrue();
        assertThat(phoneObj.get("type").asText()).isEqualTo("work");
    }

    @Test
    @DisplayName("Should include meta object when converting PingIDM to SCIM")
    void testPingIdmToScim_MetaObject() {
        // Given: A PingIDM user with metadata
        ObjectNode idmUser = objectMapper.createObjectNode();
        idmUser.put("_id", "user123");
        idmUser.put("_rev", "5");
        idmUser.put("userName", "john.doe");

        // When: Converting to SCIM format
        GenericScimResource scimUser = mapper.pingIdmToScim(idmUser);
        ObjectNode scimNode = scimUser.asGenericScimResource().getObjectNode();

        // Then: Meta object should be included
        assertThat(scimNode.has("meta")).isTrue();
        ObjectNode metaNode = (ObjectNode) scimNode.get("meta");
        assertThat(metaNode.get("resourceType").asText()).isEqualTo("User");
        assertThat(metaNode.get("version").asText()).isEqualTo("5");
        assertThat(metaNode.has("location")).isTrue();
    }

    @Test
    @DisplayName("Should handle password attribute in SCIM to PingIDM conversion")
    void testScimToPingIdm_Password() {
        // Given: A SCIM user with password
        ObjectNode scimNode = objectMapper.createObjectNode();
        scimNode.put("userName", "john.doe");
        scimNode.put("password", "SecureP@ssw0rd");

        GenericScimResource scimUser = new GenericScimResource(scimNode);

        // When: Converting to PingIDM format
        ObjectNode idmUser = mapper.scimToPingIdm(scimUser);

        // Then: Password should be included
        assertThat(idmUser.has("password")).isTrue();
        assertThat(idmUser.get("password").asText()).isEqualTo("SecureP@ssw0rd");
    }

    @Test
    @DisplayName("Should handle additional standard attributes")
    void testScimToPingIdm_AdditionalAttributes() {
        // Given: A SCIM user with additional attributes
        ObjectNode scimNode = objectMapper.createObjectNode();
        scimNode.put("userName", "john.doe");
        scimNode.put("title", "Senior Engineer");
        scimNode.put("preferredLanguage", "en-US");
        scimNode.put("locale", "en_US");
        scimNode.put("timezone", "America/New_York");
        scimNode.put("profileUrl", "https://example.com/users/john.doe");

        GenericScimResource scimUser = new GenericScimResource(scimNode);

        // When: Converting to PingIDM format
        ObjectNode idmUser = mapper.scimToPingIdm(scimUser);

        // Then: All attributes should be preserved
        assertThat(idmUser.get("title").asText()).isEqualTo("Senior Engineer");
        assertThat(idmUser.get("preferredLanguage").asText()).isEqualTo("en-US");
        assertThat(idmUser.get("locale").asText()).isEqualTo("en_US");
        assertThat(idmUser.get("timezone").asText()).isEqualTo("America/New_York");
        assertThat(idmUser.get("profileUrl").asText()).isEqualTo("https://example.com/users/john.doe");
    }

    @Test
    @DisplayName("Should handle empty name object")
    void testScimToPingIdm_EmptyNameObject() {
        // Given: A SCIM user with empty name object
        ObjectNode scimNode = objectMapper.createObjectNode();
        scimNode.put("userName", "john.doe");
        scimNode.set("name", objectMapper.createObjectNode());

        GenericScimResource scimUser = new GenericScimResource(scimNode);

        // When: Converting to PingIDM format
        ObjectNode idmUser = mapper.scimToPingIdm(scimUser);

        // Then: No name attributes should be present
        assertThat(idmUser.has("givenName")).isFalse();
        assertThat(idmUser.has("sn")).isFalse();
    }

    @Test
    @DisplayName("Should handle empty emails array")
    void testScimToPingIdm_EmptyEmailsArray() {
        // Given: A SCIM user with empty emails array
        ObjectNode scimNode = objectMapper.createObjectNode();
        scimNode.put("userName", "john.doe");
        scimNode.set("emails", objectMapper.createArrayNode());

        GenericScimResource scimUser = new GenericScimResource(scimNode);

        // When: Converting to PingIDM format
        ObjectNode idmUser = mapper.scimToPingIdm(scimUser);

        // Then: No mail attribute should be present
        assertThat(idmUser.has("mail")).isFalse();
    }
}