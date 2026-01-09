package com.pingidentity.p1aic.scim.test;

import com.pingidentity.p1aic.scim.exceptions.FilterTranslationException;
import com.pingidentity.p1aic.scim.filter.ScimFilterConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ScimFilterConverter.
 *
 * Tests the conversion of SCIM filter expressions to PingIDM query filter syntax,
 * including handling of various operators, logical expressions, and attribute mappings.
 */
class ScimFilterConverterTest {

    private ScimFilterConverter converter;

    @BeforeEach
    void setUp() {
        converter = new ScimFilterConverter();
    }

    @Test
    @DisplayName("Should convert simple equality filter")
    void testConvert_SimpleEquality() throws FilterTranslationException {
        // Given: A simple SCIM equality filter
        String scimFilter = "userName eq \"john.doe\"";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should produce correct PingIDM filter
        assertThat(result).isEqualTo("userName eq \"john.doe\"");
    }

    @Test
    @DisplayName("Should convert not equal filter")
    void testConvert_NotEqual() throws FilterTranslationException {
        // Given: A SCIM not equal filter
        String scimFilter = "userName ne \"john.doe\"";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should produce correct PingIDM filter
        assertThat(result).isEqualTo("userName ne \"john.doe\"");
    }

    @Test
    @DisplayName("Should convert contains filter")
    void testConvert_Contains() throws FilterTranslationException {
        // Given: A SCIM contains filter
        String scimFilter = "displayName co \"John\"";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should produce correct PingIDM filter
        assertThat(result).isEqualTo("displayName co \"John\"");
    }

    @Test
    @DisplayName("Should convert starts with filter")
    void testConvert_StartsWith() throws FilterTranslationException {
        // Given: A SCIM starts with filter
        String scimFilter = "userName sw \"john\"";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should produce correct PingIDM filter
        assertThat(result).isEqualTo("userName sw \"john\"");
    }

    @Test
    @DisplayName("Should convert ends with filter")
    void testConvert_EndsWith() throws FilterTranslationException {
        // Given: A SCIM ends with filter
        String scimFilter = "userName ew \"doe\"";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should produce correct PingIDM filter
        assertThat(result).isEqualTo("userName ew \"doe\"");
    }

    @Test
    @DisplayName("Should convert greater than filter")
    void testConvert_GreaterThan() throws FilterTranslationException {
        // Given: A SCIM greater than filter
        String scimFilter = "age gt 30";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should produce correct PingIDM filter
        assertThat(result).isEqualTo("age gt 30");
    }

    @Test
    @DisplayName("Should convert greater than or equal filter")
    void testConvert_GreaterThanOrEqual() throws FilterTranslationException {
        // Given: A SCIM greater than or equal filter
        String scimFilter = "age ge 30";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should produce correct PingIDM filter
        assertThat(result).isEqualTo("age ge 30");
    }

    @Test
    @DisplayName("Should convert less than filter")
    void testConvert_LessThan() throws FilterTranslationException {
        // Given: A SCIM less than filter
        String scimFilter = "age lt 50";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should produce correct PingIDM filter
        assertThat(result).isEqualTo("age lt 50");
    }

    @Test
    @DisplayName("Should convert less than or equal filter")
    void testConvert_LessThanOrEqual() throws FilterTranslationException {
        // Given: A SCIM less than or equal filter
        String scimFilter = "age le 50";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should produce correct PingIDM filter
        assertThat(result).isEqualTo("age le 50");
    }

    @Test
    @DisplayName("Should convert present filter")
    void testConvert_Present() throws FilterTranslationException {
        // Given: A SCIM present filter
        String scimFilter = "userName pr";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should produce correct PingIDM filter
        assertThat(result).isEqualTo("userName pr");
    }

    @Test
    @DisplayName("Should convert AND logical operator")
    void testConvert_AndOperator() throws FilterTranslationException {
        // Given: A SCIM filter with AND operator
        String scimFilter = "userName eq \"john.doe\" and active eq true";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should produce correct PingIDM filter with AND
        assertThat(result).contains("and");
        assertThat(result).contains("userName eq \"john.doe\"");
        assertThat(result).contains("active eq true");
    }

    @Test
    @DisplayName("Should convert OR logical operator")
    void testConvert_OrOperator() throws FilterTranslationException {
        // Given: A SCIM filter with OR operator
        String scimFilter = "userName eq \"john.doe\" or userName eq \"jane.doe\"";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should produce correct PingIDM filter with OR
        assertThat(result).contains("or");
        assertThat(result).contains("userName eq \"john.doe\"");
        assertThat(result).contains("userName eq \"jane.doe\"");
    }

    @Test
    @DisplayName("Should convert NOT logical operator")
    void testConvert_NotOperator() throws FilterTranslationException {
        // Given: A SCIM filter with NOT operator
        String scimFilter = "not (userName eq \"john.doe\")";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should produce correct PingIDM filter with NOT
        assertThat(result).startsWith("!");
    }

    @Test
    @DisplayName("Should handle parentheses for precedence")
    void testConvert_Parentheses() throws FilterTranslationException {
        // Given: A SCIM filter with parentheses
        String scimFilter = "(userName eq \"john.doe\" or userName eq \"jane.doe\") and active eq true";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should preserve logical structure
        assertThat(result).contains("and");
        assertThat(result).contains("or");
    }

    @Test
    @DisplayName("Should map SCIM name.givenName to PingIDM givenName")
    void testConvert_AttributeMapping_GivenName() throws FilterTranslationException {
        // Given: A SCIM filter with name.givenName
        String scimFilter = "name.givenName eq \"John\"";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should map to givenName
        assertThat(result).contains("givenName eq \"John\"");
    }

    @Test
    @DisplayName("Should map SCIM name.familyName to PingIDM sn")
    void testConvert_AttributeMapping_FamilyName() throws FilterTranslationException {
        // Given: A SCIM filter with name.familyName
        String scimFilter = "name.familyName eq \"Doe\"";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should map to sn
        assertThat(result).contains("sn eq \"Doe\"");
    }

    @Test
    @DisplayName("Should map SCIM active to PingIDM accountStatus")
    void testConvert_AttributeMapping_Active() throws FilterTranslationException {
        // Given: A SCIM filter with active attribute
        String scimFilter = "active eq true";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should map to accountStatus with "active" value
        assertThat(result).contains("accountStatus eq \"active\"");
    }

    @Test
    @DisplayName("Should convert active eq false to accountStatus eq inactive")
    void testConvert_AttributeMapping_Inactive() throws FilterTranslationException {
        // Given: A SCIM filter with active eq false
        String scimFilter = "active eq false";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should map to accountStatus with "inactive" value
        assertThat(result).contains("accountStatus eq \"inactive\"");
    }

    @Test
    @DisplayName("Should handle null or empty filter")
    void testConvert_EmptyFilter() throws FilterTranslationException {
        // Given: An empty filter
        String scimFilter = "";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should return "true" (no filter)
        assertThat(result).isEqualTo("true");
    }

    @Test
    @DisplayName("Should handle null filter")
    void testConvert_NullFilter() throws FilterTranslationException {
        // Given: A null filter
        String scimFilter = null;

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should return "true" (no filter)
        assertThat(result).isEqualTo("true");
    }

    @Test
    @DisplayName("Should throw exception for invalid filter syntax")
    void testConvert_InvalidSyntax() {
        // Given: An invalid SCIM filter
        String scimFilter = "userName invalid syntax";

        // When/Then: Should throw FilterTranslationException
        assertThatThrownBy(() -> converter.convert(scimFilter))
                .isInstanceOf(FilterTranslationException.class);
    }

    @Test
    @DisplayName("Should handle filter with boolean value")
    void testConvert_BooleanValue() throws FilterTranslationException {
        // Given: A SCIM filter with boolean value
        String scimFilter = "active eq true";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should handle boolean correctly
        assertThat(result).contains("accountStatus eq \"active\"");
    }

    @Test
    @DisplayName("Should handle filter with numeric value")
    void testConvert_NumericValue() throws FilterTranslationException {
        // Given: A SCIM filter with numeric value
        String scimFilter = "employeeNumber eq 12345";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should preserve numeric value without quotes
        assertThat(result).contains("employeeNumber eq 12345");
    }

    @Test
    @DisplayName("Should handle filter with quoted string value")
    void testConvert_QuotedString() throws FilterTranslationException {
        // Given: A SCIM filter with quoted string
        String scimFilter = "userName eq \"john.doe\"";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should preserve quotes
        assertThat(result).contains("userName eq \"john.doe\"");
    }

    @Test
    @DisplayName("Should handle complex nested filter")
    void testConvert_ComplexNestedFilter() throws FilterTranslationException {
        // Given: A complex nested SCIM filter
        String scimFilter = "(userName eq \"john.doe\" and active eq true) or (userName eq \"jane.doe\" and active eq false)";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should preserve logical structure
        assertThat(result).isNotNull();
        assertThat(result).contains("or");
        assertThat(result).contains("and");
    }

    @Test
    @DisplayName("Should handle filter with multiple AND operations")
    void testConvert_MultipleAndOperations() throws FilterTranslationException {
        // Given: A SCIM filter with multiple AND operations
        String scimFilter = "userName eq \"john.doe\" and active eq true and title eq \"Engineer\"";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should handle multiple ANDs correctly
        assertThat(result).contains("and");
        assertThat(result).contains("userName eq \"john.doe\"");
        assertThat(result).contains("accountStatus eq \"active\"");
        assertThat(result).contains("title eq \"Engineer\"");
    }

    @Test
    @DisplayName("Should handle case-insensitive operators")
    void testConvert_CaseInsensitiveOperators() throws FilterTranslationException {
        // Given: A SCIM filter with uppercase operators
        String scimFilter = "userName EQ \"john.doe\" AND active EQ true";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should handle case-insensitive operators
        assertThat(result).isNotNull();
        assertThat(result).containsIgnoringCase("and");
    }

    @Test
    @DisplayName("Should handle filter with dot notation in attribute names")
    void testConvert_DotNotation() throws FilterTranslationException {
        // Given: A SCIM filter with dot notation
        String scimFilter = "name.formatted eq \"John Doe\"";

        // When: Converting to PingIDM format
        String result = converter.convert(scimFilter);

        // Then: Should map to PingIDM attribute
        assertThat(result).contains("cn eq \"John Doe\"");
    }
}
