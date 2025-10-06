/**
 *
 */
package org.theseed.p3api;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * This class is used to build criteria for calls to the PATRIC API.  It handles the URL encoding of unsafe values.
 *
 */
public class Criterion {

    /**
     * @return the criterion string for a single-valued comparison.
     *
     * @param op		comparison operator
     * @param field		field name
     * @param value		field value
     */
    private static String build(String op, String field, String value) {
        return op + "(" + field + "," + fix(value) + ")";
    }

    /**
     * @return a safe version of a string for inclusion as a field value
     *
     * @param value		value to make safe
     */
    public static String fix(String value) {
        // Delete special characters.  It's a SOLR thing.
        StringBuilder retVal = new StringBuilder(value.length());
        char last = 'X';
        for (int i = 0; i < value.length(); i++) {
            char curr = value.charAt(i);
            if (curr >= 'a' && curr <= 'z' || curr >= 'A' && curr <= 'Z' ||
                    curr >= '0' && curr <= '9' || curr == '-' ||
                    curr == '.' || curr == '_') {
                retVal.append(curr);
                last = curr;
            } else if (curr == '|') {
                retVal.append("%7C");
            } else if (last != ' ') {
                retVal.append('+');
                last = ' ';
            }
        }
        return retVal.toString();
    }

    /**
     * @return the criterion string for an equality comparison
     *
     * @param field		field name
     * @param value		field value
     */
    public static String EQ(String field, String value) {
        return build("eq", field, value);
    }

    /**
     * @return the criterion string for an inequality comparison
     *
     * @param field		field name
     * @param value		field value
     */
    public static String NE(String field, String value) {
        return build("ne", field, value);
    }

    /**
     * @return the criterion string for an inclusion request
     *
     * @param field		field name
     * @param values	field values (as an array)
     */
    public static String IN(String field, String... values) {
        String prefix = "in(" + field + ",(";
        String retVal = Arrays.stream(values).map(x -> fix(x)).collect(Collectors.joining(",", prefix, "))"));
        return retVal;
    }

    /**
     * @return the criterion string for an inclusion request
     *
     * @param field		field name
     * @param values	field values (as a collection)
     */
    public static String IN(String field, Collection<String> values) {
        String prefix = "in(" + field + ",(";
        String retVal = values.stream().map(x -> fix(x)).collect(Collectors.joining(",", prefix, "))"));
        return retVal;
    }

    /**
     * @return the criterion string for a greater-or-equal comparison to an integer
     *
     * @param field		field name
     * @param value		field value
     */
    public static String GE(String field, int value) {
        String retVal = String.format("ge(%s,%d)", field, value);
        return retVal;
    }

    /**
     * @return the criterion string for a less-or-equal comparison to an integer
     *
     * @param field		field name
     * @param value		field value
     */
    public static String LE(String field, int value) {
        String retVal = String.format("le(%s,%d)", field, value);
        return retVal;
    }

}
