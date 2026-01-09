package com.pingidentity.p1aic.scim.filter;

import com.pingidentity.p1aic.scim.exceptions.FilterTranslationException;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converter for SCIM filter expressions to PingIDM query filter syntax.
 *
 * SCIM filters use a simplified query language defined in RFC 7644 Section 3.4.2.2.
 * PingIDM uses its own query filter syntax for the _queryFilter parameter.
 *
 * This converter translates between the two formats, including attribute name mapping.
 */
public class ScimFilterConverter {

    private static final Logger LOGGER = Logger.getLogger(ScimFilterConverter.class.getName());

    // Attribute name mappings (SCIM -> PingIDM)
    private final Map<String, String> attributeMappings;

    // SCIM filter operators and their PingIDM equivalents
    private static final Map<String, String> OPERATOR_MAPPINGS = new HashMap<>();

    static {
        OPERATOR_MAPPINGS.put("eq", "eq");
        OPERATOR_MAPPINGS.put("ne", "ne");
        OPERATOR_MAPPINGS.put("co", "co");
        OPERATOR_MAPPINGS.put("sw", "sw");
        OPERATOR_MAPPINGS.put("ew", "ew");
        OPERATOR_MAPPINGS.put("gt", "gt");
        OPERATOR_MAPPINGS.put("ge", "ge");
        OPERATOR_MAPPINGS.put("lt", "lt");
        OPERATOR_MAPPINGS.put("le", "le");
        OPERATOR_MAPPINGS.put("pr", "pr");
    }

    // Logical operators
    private static final Map<String, String> LOGICAL_OPERATORS = new HashMap<>();

    static {
        LOGICAL_OPERATORS.put("and", "and");
        LOGICAL_OPERATORS.put("or", "or");
        LOGICAL_OPERATORS.put("not", "!");
    }

    // Regex patterns for parsing SCIM filters
    private static final Pattern SIMPLE_FILTER_PATTERN =
            Pattern.compile("(\\w+(?:\\.\\w+)*)\\s+(eq|ne|co|sw|ew|gt|ge|lt|le|pr)\\s*(?:\"([^\"]*)\"|([^\\s]+))?",
                    Pattern.CASE_INSENSITIVE);

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
     * Convert SCIM filter to PingIDM query filter.
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

            String converted = convertExpression(scimFilter.trim());

            LOGGER.info("Converted to PingIDM filter: " + converted);

            return converted;

        } catch (Exception e) {
            LOGGER.severe("Failed to convert SCIM filter: " + scimFilter);
            throw new FilterTranslationException("Failed to convert SCIM filter: " + e.getMessage(), e);
        }
    }

    /**
     * Convert a SCIM filter expression (may contain logical operators).
     */
    private String convertExpression(String expression) throws FilterTranslationException {
        // Handle parentheses
        if (expression.contains("(")) {
            return convertExpressionWithParentheses(expression);
        }

        // Handle logical operators (and, or)
        for (String logicalOp : new String[]{"and", "or"}) {
            int opIndex = findLogicalOperator(expression, logicalOp);
            if (opIndex > 0) {
                String left = expression.substring(0, opIndex).trim();
                String right = expression.substring(opIndex + logicalOp.length()).trim();

                String convertedLeft = convertExpression(left);
                String convertedRight = convertExpression(right);
                String idmOperator = LOGICAL_OPERATORS.get(logicalOp.toLowerCase());

                return String.format("(%s %s %s)", convertedLeft, idmOperator, convertedRight);
            }
        }

        // Handle NOT operator
        if (expression.toLowerCase().startsWith("not ")) {
            String inner = expression.substring(4).trim();
            String convertedInner = convertExpression(inner);
            return "!" + convertedInner;
        }

        // Handle simple comparison
        return convertSimpleComparison(expression);
    }

    /**
     * Convert expression with parentheses.
     */
    private String convertExpressionWithParentheses(String expression) throws FilterTranslationException {
        // Find matching parentheses and recursively convert
        int depth = 0;
        int start = -1;

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);

            if (c == '(') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0 && start >= 0) {
                    // Found matching closing parenthesis
                    String inner = expression.substring(start + 1, i);
                    String converted = convertExpression(inner);

                    // Replace the parenthetical expression and continue
                    String before = expression.substring(0, start);
                    String after = expression.substring(i + 1);

                    return convertExpression(before + "(" + converted + ")" + after);
                }
            }
        }

        // No parentheses found, treat as simple expression
        return convertExpression(expression);
    }

    /**
     * Find the position of a logical operator (and, or) at the top level (not in parentheses).
     */
    private int findLogicalOperator(String expression, String operator) {
        int depth = 0;
        String lowerExpr = expression.toLowerCase();
        String lowerOp = operator.toLowerCase();

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);

            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0) {
                // Check if we're at the operator
                if (i + lowerOp.length() <= lowerExpr.length()) {
                    String substring = lowerExpr.substring(i, i + lowerOp.length());
                    if (substring.equals(lowerOp)) {
                        // Check that it's a word boundary
                        boolean validBefore = (i == 0 || Character.isWhitespace(expression.charAt(i - 1)));
                        boolean validAfter = (i + lowerOp.length() >= expression.length() ||
                                Character.isWhitespace(expression.charAt(i + lowerOp.length())));

                        if (validBefore && validAfter) {
                            return i;
                        }
                    }
                }
            }
        }

        return -1;
    }

    /**
     * Convert a simple SCIM comparison to PingIDM format.
     * Examples:
     * - userName eq "john" -> userName eq "john"
     * - name.familyName co "Smith" -> sn co "Smith"
     * - active eq true -> accountStatus eq "active"
     */
    private String convertSimpleComparison(String comparison) throws FilterTranslationException {
        Matcher matcher = SIMPLE_FILTER_PATTERN.matcher(comparison);

        if (!matcher.matches()) {
            throw new FilterTranslationException("Invalid SCIM filter syntax: " + comparison);
        }

        String scimAttribute = matcher.group(1);
        String operator = matcher.group(2).toLowerCase();
        String value = matcher.group(3) != null ? matcher.group(3) : matcher.group(4);

        // Map attribute name from SCIM to PingIDM
        String idmAttribute = mapAttributeName(scimAttribute);

        // Map operator
        String idmOperator = OPERATOR_MAPPINGS.get(operator);
        if (idmOperator == null) {
            throw new FilterTranslationException("Unsupported operator: " + operator);
        }

        // Handle 'pr' (present) operator - no value needed
        if ("pr".equals(operator)) {
            return idmAttribute + " pr";
        }

        // Special handling for active attribute (boolean -> string conversion)
        if ("active".equals(scimAttribute)) {
            value = convertActiveValue(value);
        }

        // Build PingIDM filter
        if (value == null || value.isEmpty()) {
            throw new FilterTranslationException("Missing value for operator: " + operator);
        }

        // Quote the value if it's not already quoted and not a boolean/number
        String quotedValue = quoteValue(value);

        return String.format("%s %s %s", idmAttribute, idmOperator, quotedValue);
    }

    /**
     * Map SCIM attribute name to PingIDM attribute name.
     */
    private String mapAttributeName(String scimAttribute) {
        return attributeMappings.getOrDefault(scimAttribute, scimAttribute);
    }

    /**
     * Convert active boolean value to PingIDM accountStatus string.
     */
    private String convertActiveValue(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return "active";
        } else if ("false".equalsIgnoreCase(value)) {
            return "inactive";
        }
        return value;
    }

    /**
     * Quote a value if needed.
     */
    private String quoteValue(String value) {
        if (value == null) {
            return "null";
        }

        // Already quoted
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value;
        }

        // Boolean or number - don't quote
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) ||
                "null".equalsIgnoreCase(value) || isNumeric(value)) {
            return value;
        }

        // Quote string values
        return "\"" + value + "\"";
    }

    /**
     * Check if a string is numeric.
     */
    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

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
