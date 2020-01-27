/**
 *
 */
package org.theseed.p3api;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

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
        // First we have to delete parentheses.  It's a SOLR thing.
        String retVal = StringUtils.replaceChars(value, "()", "  ");
        try {
            // URL-encode everything else.
            retVal = URLEncoder.encode(retVal, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 not supported for parameter \"" + value + "\".");
        }
        return retVal;

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
     * @param values	field values
     */
    public static String IN(String field, String... values) {
        String prefix = "in(" + field + ",(";
        String retVal = Arrays.stream(values).map(x -> fix(x)).collect(Collectors.joining(",", prefix, "))"));
        return retVal;
    }

}
