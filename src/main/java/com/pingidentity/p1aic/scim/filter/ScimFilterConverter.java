package com.pingidentity.p1aic.scim.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.pingidentity.p1aic.scim.exceptions.FilterTranslationException;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.filters.*;
import com.unboundid.scim2.common.Path;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Converter for SCIM filter expressions to PingIDM query filter syntax.
 *
 * REFACTORED: Now uses UnboundID SDK's Filter parser and visitor pattern
 * instead of fragile regex parsing. This provides proper support for:
 * - Nested logical operators: (a eq "x" or b eq "y") and c eq "z"
 * - Complex filter expressions with proper precedence
 * - Type-safe filter traversal
 *
 * SCIM filters use a simplified query language defined in RFC 7644 Section 3.4.2.2.
 * PingIDM uses its own query filter syntax for the _queryFilter parameter.
 */
public class ScimFilterConverter {

    private static final Logger LOGGER = Logger.getLogger(ScimFilterConverter.class.getName());

    // Attribute name mappings (SCIM -> PingIDM)
    private final Map<String, String> attributeMappings;

    /**
     * Constructor initializes attribute mappings.
     */
    public ScimFilterConverter() {
        this.attributeMappings = buildDefaultAttributeMappings();
    }

    /**
     * Constructor with custom attribute mappings.
     *
     * @param attributeMappings custom attribute name mappings (SCIM -> PingIDM)
     */
    public ScimFilterConverter(Map<String, String> attributeMappings) {
        this.attributeMappings = new HashMap<>(attributeMappings);
    }

    /**
     * Convert SCIM filter to PingIDM query filter using UnboundID SDK parser.
     *
     * @param scimFilter the SCIM filter expression (e.g., 'userName eq "john"')
     * @return the PingIDM query filter expression
     * @throws FilterTranslationException if conversion fails
     */
    public String convert(String scimFilter) throws FilterTranslationException {
        if (scimFilter == null || scimFilter.trim().isEmpty()) {
            return "true"; // No filter means return all
        }

        try {
            LOGGER.info("Converting SCIM filter: " + scimFilter);

            // BEGIN: Use UnboundID SDK to parse SCIM filter into object graph
            Filter parsedFilter = Filter.fromString(scimFilter);

            // Use visitor pattern to convert to PingIDM syntax
            PingIdmFilterVisitor visitor = new PingIdmFilterVisitor(attributeMappings);
            String converted = parsedFilter.visit(visitor, null);
            // END: Use UnboundID SDK to parse SCIM filter

            LOGGER.info("Converted to PingIDM filter: " + converted);
            return converted;

        } catch (ScimException e) {
            LOGGER.severe("Failed to convert SCIM filter: " + scimFilter);
            throw new FilterTranslationException("Failed to convert SCIM filter: " + e.getMessage(), e);
        }
    }

    // BEGIN: Add PingIDM Filter Visitor implementation
    /**
     * Visitor that converts UnboundID Filter objects to PingIDM query syntax.
     *
     * This visitor traverses the parsed SCIM filter tree and builds the
     * equivalent PingIDM query filter string.
     *
     * FilterVisitor<R, P> where:
     * - R is the return type (String - the PingIDM filter expression)
     * - P is the parameter type (Void - we don't need to pass context)
     */
    private static class PingIdmFilterVisitor implements FilterVisitor<String, Void> {

        private final Map<String, String> attributeMappings;

        public PingIdmFilterVisitor(Map<String, String> attributeMappings) {
            this.attributeMappings = attributeMappings;
        }

        @Override
        public String visit(EqualFilter equalFilter, Void unused) throws ScimException {
            return buildComparisonFilter(equalFilter.getAttributePath(), "eq", equalFilter.getComparisonValue());
        }

        @Override
        public String visit(NotEqualFilter notEqualFilter, Void unused) throws ScimException {
            return buildComparisonFilter(notEqualFilter.getAttributePath(), "ne", notEqualFilter.getComparisonValue());
        }

        @Override
        public String visit(ContainsFilter containsFilter, Void unused) throws ScimException {
            return buildComparisonFilter(containsFilter.getAttributePath(), "co", containsFilter.getComparisonValue());
        }

        @Override
        public String visit(StartsWithFilter startsWithFilter, Void unused) throws ScimException {
            return buildComparisonFilter(startsWithFilter.getAttributePath(), "sw", startsWithFilter.getComparisonValue());
        }

        @Override
        public String visit(EndsWithFilter endsWithFilter, Void unused) throws ScimException {
            return buildComparisonFilter(endsWithFilter.getAttributePath(), "ew", endsWithFilter.getComparisonValue());
        }

        @Override
        public String visit(PresentFilter presentFilter, Void unused) throws ScimException {
            String idmAttribute = mapAttributeName(presentFilter.getAttributePath());
            return idmAttribute + " pr";
        }

        @Override
        public String visit(GreaterThanFilter greaterThanFilter, Void unused) throws ScimException {
            return buildComparisonFilter(greaterThanFilter.getAttributePath(), "gt", greaterThanFilter.getComparisonValue());
        }

        @Override
        public String visit(GreaterThanOrEqualFilter greaterThanOrEqualFilter, Void unused) throws ScimException {
            return buildComparisonFilter(greaterThanOrEqualFilter.getAttributePath(), "ge", greaterThanOrEqualFilter.getComparisonValue());
        }

        @Override
        public String visit(LessThanFilter lessThanFilter, Void unused) throws ScimException {
            return buildComparisonFilter(lessThanFilter.getAttributePath(), "lt", lessThanFilter.getComparisonValue());
        }

        @Override
        public String visit(LessThanOrEqualFilter lessThanOrEqualFilter, Void unused) throws ScimException {
            return buildComparisonFilter(lessThanOrEqualFilter.getAttributePath(), "le", lessThanOrEqualFilter.getComparisonValue());
        }

        @Override
        public String visit(AndFilter andFilter, Void unused) throws ScimException {
            // Convert: (filter1 AND filter2) -> (converted1 and converted2)
            StringBuilder result = new StringBuilder("(");
            boolean first = true;

            for (Filter component : andFilter.getCombinedFilters()) {
                if (!first) {
                    result.append(" and ");
                }
                result.append(component.visit(this, unused));
                first = false;
            }

            result.append(")");
            return result.toString();
        }

        @Override
        public String visit(OrFilter orFilter, Void unused) throws ScimException {
            // Convert: (filter1 OR filter2) -> (converted1 or converted2)
            StringBuilder result = new StringBuilder("(");
            boolean first = true;

            for (Filter component : orFilter.getCombinedFilters()) {
                if (!first) {
                    result.append(" or ");
                }
                result.append(component.visit(this, unused));
                first = false;
            }

            result.append(")");
            return result.toString();
        }

        @Override
        public String visit(NotFilter notFilter, Void unused) throws ScimException {
            // Convert: NOT filter -> !converted
            return "!" + notFilter.getInvertedFilter().visit(this, unused);
        }

        @Override
        public String visit(ComplexValueFilter complexValueFilter, Void unused) throws ScimException {
            // Handle complex attribute filters like: emails[type eq "work"].value
            // Get the base attribute path (e.g., "emails")
            String idmAttribute = mapAttributeName(complexValueFilter.getAttributePath());

            // Get the value filter (e.g., "type eq 'work'")
            String valueFilter = complexValueFilter.getValueFilter().visit(this, unused);

            // This is a simplified approach - may need enhancement based on PingIDM capabilities
            return "(" + idmAttribute + " " + valueFilter + ")";
        }

        /**
         * Build a comparison filter expression in PingIDM syntax.
         *
         * CORRECTED: comparisonValue is JsonNode, not FilterValue
         */
        private String buildComparisonFilter(Path attributePath, String operator, JsonNode comparisonValue) {

            String idmAttribute = mapAttributeName(attributePath);
            String idmValue = formatValue(attributePath, comparisonValue);

            return String.format("%s %s %s", idmAttribute, operator, idmValue);
        }

        /**
         * Map SCIM attribute path to PingIDM attribute name.
         */
        private String mapAttributeName(Path attributePath) {
            String scimAttribute = attributePath.toString();

            // Use mapping, or pass through if not found
            String idmAttribute = attributeMappings.getOrDefault(scimAttribute, scimAttribute);

            return idmAttribute;
        }

        /**
         * Format a JsonNode value for PingIDM query syntax.
         *
         * CORRECTED: Works directly with JsonNode from getComparisonValue()
         */
        private String formatValue(Path attributePath, JsonNode jsonValue) {

            if (jsonValue == null || jsonValue.isNull()) {
                return "null";
            }

            // Handle special case: active attribute (boolean -> string conversion)
            String scimAttribute = attributePath.toString();
            if ("active".equals(scimAttribute) && jsonValue.isBoolean()) {
                boolean boolValue = jsonValue.asBoolean();
                return "\"" + (boolValue ? "active" : "inactive") + "\"";
            }

            // Handle different JsonNode types
            if (jsonValue.isTextual()) {
                return "\"" + escapeString(jsonValue.asText()) + "\"";
            } else if (jsonValue.isBoolean()) {
                return jsonValue.asBoolean() ? "true" : "false";
            } else if (jsonValue.isInt() || jsonValue.isLong()) {
                return String.valueOf(jsonValue.asLong());
            } else if (jsonValue.isDouble() || jsonValue.isFloat()) {
                return String.valueOf(jsonValue.asDouble());
            } else if (jsonValue.isNumber()) {
                return jsonValue.asText();
            }

            // For other types (arrays, objects), convert to string representation
            return "\"" + escapeString(jsonValue.asText()) + "\"";
        }

        /**
         * Escape special characters in string values.
         */
        private String escapeString(String input) {
            if (input == null) {
                return "";
            }
            return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"");
        }
    }
    // END: Add PingIDM Filter Visitor implementation

    /**
     * Build default attribute mappings for User resources.
     */
    private Map<String, String> buildDefaultAttributeMappings() {
        Map<String, String> mappings = new HashMap<>();

        // Core User attributes
        mappings.put("userName", "userName");
        mappings.put("displayName", "displayName");
        mappings.put("active", "accountStatus");

        // Name attributes
        mappings.put("name.givenName", "givenName");
        mappings.put("name.familyName", "sn");
        mappings.put("name.formatted", "cn");
        mappings.put("name.middleName", "middleName");

        // Email
        mappings.put("emails.value", "mail");
        mappings.put("emails[type eq \"work\"].value", "mail");

        // Phone
        mappings.put("phoneNumbers.value", "telephoneNumber");
        mappings.put("phoneNumbers[type eq \"work\"].value", "telephoneNumber");

        // Additional attributes
        mappings.put("title", "title");
        mappings.put("preferredLanguage", "preferredLanguage");
        mappings.put("locale", "locale");

        return mappings;
    }
}