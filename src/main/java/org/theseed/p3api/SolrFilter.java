package org.theseed.p3api;

import java.io.IOException;
import java.util.Collection;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * This class represents a SOLR filter. Methods are provided to translate the field name according
 * to the data map, and to 
 */
public abstract class SolrFilter {

    // FIELDS
    /** field name */
    private String fieldName;
    /** field value */
    private String fieldValue;
    /** pattern for matching numeric values */
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    /** set of characters to escape in a SOLR query */
    private static final String SOLR_ESCAPE_CHARS = "+\\-&|!(){}\\[\\]\\^\"~?:\\\\";
    /** replacement pattern for escaping SOLR special characters */
    private static final String SOLR_ESCAPE_REPLACEMENT = "\\\\$0";

    // CONSTRUCTORS AND METHODS

    /**
     * Construct a SOLR filter for a specific field.
     *
     * @param field     the field to filter on
     */
    protected SolrFilter(String field) {
        this.fieldName = field;
        // The default value is a wildcard.
        this.fieldValue = "*";
    }

    /**
     * Set the value for this filter.
     *
     * @param value     the value to filter for
     */
    protected void setValue(String value) {
        this.fieldValue = smartQuote(value);
    }

    /**
     * Quote a string if it is non-numeric.
     * 
     * @param value     string to possibly quote
     * 
     * @return the quoted string
     */
    protected static String smartQuote(String value) {
        String retVal;
        // If the value is numeric, we do not quote it.
        if (NUMERIC_PATTERN.matcher(value).matches())
            retVal = value;
        else
            retVal = quote(value);
        return retVal;
    }

    /**
     * @return the value string for this filter, quoted if necessary
     */
    protected String getValue() {
        return this.fieldValue;
    }

    /**
     * Compute the internal field name.
     * 
     * @param dataMap       BV-BRC data map for converting field names
     * @param table         table containing this filter's field
     *
     * @return the internal name of this filter's field
     * 
     * @throws IOException
     */
    protected String getInternalFieldName(BvbrcDataMap dataMap, String table) throws IOException {
        BvbrcDataMap.Table tbl = dataMap.getTable(table);
        return tbl.getInternalFieldName(this.fieldName);
    }

    /**
     * @return the query string for this filter
     * 
     * @param dataMap       BV-BRC data map for converting field names
     * @param table         table containing this filter's field
     */
    public abstract String toString(BvbrcDataMap dataMap, String table) throws IOException;

    /**
     * Convert a filter list to an array of query strings.
     * 
     * @param dataMap   the data map to use for fixing up the field names
     * @param table     the name of the table containing the fields
     * @param criteria  the collection of criteria to convert
     * 
     * @throws IOException 
     */
    public static String[] toStrings(BvbrcDataMap dataMap, String table, Collection<SolrFilter> criteria) throws IOException {
        String[] retVal = new String[criteria.size()];
        int i = 0;
        for (SolrFilter filter : criteria) {
            retVal[i] = filter.toString(dataMap, table);
            i++;
        }
        return retVal;
    }

    /**
     * Quote a string for use in a SOLR query.
     *
     * @param value     the string to quote
     * 
     * @return the quoted string, with invalid characters escaped
     */
    protected static String quote(String value) {
        String retVal;
        // If there are no special characters, we are fine.
        if (! StringUtils.containsAny(value, SOLR_ESCAPE_CHARS))
            if (! StringUtils.contains(value, ' '))
                retVal = value;
            else
                retVal = "\"" + value + "\"";
        else {
            // Here we have to escape all the special characters. The replacement pattern prefixes
            // a backslash to each. We also put on the quotes.
            retVal = "\"" + value.replaceAll("([" + SOLR_ESCAPE_CHARS + "])", SOLR_ESCAPE_REPLACEMENT) + "\"";
        }
        return retVal;
    }

    // CRITERION SUBCLASSES

    /**
     * This is an equality filter.
     */
    protected static class Eq extends SolrFilter {

        public Eq(String field, String value) {
            super(field);
            this.setValue(value);
        }

        @Override
        public String toString(BvbrcDataMap dataMap, String table) throws IOException {            
            return this.getInternalFieldName(dataMap, table) + ":" + this.getValue();
        }

    }

    /**
     * This is a one-of-a-set filter.
     */
    protected static class In extends SolrFilter {

        /** array of values for the filter */
        private String[] values;

        public In(String field, String[] values) {
            super(field);
            this.values = values;
        }

        @Override
        public String toString(BvbrcDataMap dataMap, String table) throws IOException {
            StringBuilder retVal = new StringBuilder();
            retVal.append(this.getInternalFieldName(dataMap, table)).append(":(");
            for (int i = 0; i < values.length; i++) {
                if (i > 0) retVal.append(" OR ");
                retVal.append(quote(values[i]));
            }
            retVal.append(")");
            return retVal.toString();
        }

    }

    /**
     * This is an inequality filter.
     */
    protected static class Ne extends SolrFilter {

        public Ne(String field, String value) {
            super(field);
            this.setValue(value);
        }

        @Override
        public String toString(BvbrcDataMap dataMap, String table) throws IOException {
            return "-" + this.getInternalFieldName(dataMap, table) + ":" + this.getValue();
        }

    }

    /**
     * This is a relational-operator filter. It is always created by one of the static methods.
     */
    protected static class Rel extends SolrFilter {

        /** low-limit bracket */
        private String lowBracket;
        /** high-limit bracket */
        private String highBracket;
        /** low value for filtering */
        private String lowValue;
        /** high value for filtering */
        private String highValue;

        protected Rel(String field, boolean inclusive, String low, String high) {
            super(field);
            // There is no quoting, because one string will be numeric and the other will 
            // be a wildcard. We are always exclusive on the wildcard side.
            this.lowValue = low;
            this.highValue = high;
            if (this.lowValue.contentEquals("*")) {
                this.lowBracket = "{";
                this.highBracket = (inclusive ? "]" : "}");
            } else if (this.highValue.contentEquals("*")) {
                this.lowBracket = (inclusive ? "[" : "{");
                this.highBracket = "}";
            } else
                throw new IllegalArgumentException("Relational filter has two numeric bounds: " + low + " and " + high);
        }

        @Override
        public String toString(BvbrcDataMap dataMap, String table) throws IOException {
            return this.getInternalFieldName(dataMap, table) + ":" + this.lowBracket + this.lowValue + " TO " + this.highValue + this.highBracket;
        }

    }

    // STATIC BUILDERS

    /**
     * Filter on a field having a particular value.
     * 
     * @param field     user-friendly field name
     * @param value     the value to filter on
     * 
     * @return a new equality filter
     */
    public static SolrFilter EQ(String field, String value) {
        return new Eq(field, value);
    }

    /**
     * Filter on a field not having a particular value.
     * 
     * @param field     user-friendly field name
     * @param value     the value to filter on
     * 
     * @return a new non-equality filter
     */
    public static SolrFilter NE(String field, String value) {
        return new Ne(field, value);
    }

    /**
     * Filter on a field having one of a particular set of values.
     * 
     * @param field     user-friendly field name
     * @param values    the array of values to filter on
     * 
     * @return a new one-of-a-set filter
     */
    public static SolrFilter IN(String field, String... values) {
        return new In(field, values);
    }

    /**
     * Filter on a numeric field having a particular value.
     * 
     * @param field     user-friendly field name
     * @param value     the value to filter on
     * 
     * @return a new equality filter
     */
    public static SolrFilter EQ(String field, double value) {
        return new Eq(field, String.valueOf(value));
    }

    /**
     * Filter on a numeric field not having a particular value.
     * 
     * @param field     user-friendly field name
     * @param value     the value to filter on
     * 
     * @return a new non-equality filter
     */
    public static SolrFilter NE(String field, double value) {
        return new Ne(field, String.valueOf(value));
    }

    /**
     * Filter on an integer field having a particular value.
     * 
     * @param field     user-friendly field name
     * @param value     the value to filter on
     * 
     * @return a new equality filter
     */
    public static SolrFilter EQ(String field, int value) {
        return new Eq(field, String.valueOf(value));
    }

    /**
     * Filter on an integer field not having a particular value.
     * 
     * @param field     user-friendly field name
     * @param value     the value to filter on
     * 
     * @return a new non-equality filter
     */
    public static SolrFilter NE(String field, int value) {
        return new Ne(field, String.valueOf(value));
    }

    /**
     * Filter on a numeric field having a value greater than a particular value.
     * 
     * @param field     user-friendly field name
     * @param value     the value to filter on
     * 
     * @return a new greater-than filter
     */
    public static SolrFilter GT(String field, double value) {
        return new Rel(field, false, String.valueOf(value), "*");
    }

    /**
     * Filter on a numeric field having a value greater than or equal to a particular value.
     * 
     * @param field     user-friendly field name
     * @param value     the value to filter on
     * 
     * @return a new greater-than-or-equal-to filter
     */
    public static SolrFilter GE(String field, double value) {
        return new Rel(field, true, String.valueOf(value), "*");
    }

    /**
     * Filter on a numeric field having a value less than a particular value.
     * 
     * @param field     user-friendly field name
     * @param value     the value to filter on
     * 
     * @return a new less-than filter
     */
    public static SolrFilter LT(String field, double value) {
        return new Rel(field, false, "*", String.valueOf(value));
    }

    /**
     * Filter on a numeric field having a value less than or equal to a particular value.
     * 
     * @param field     user-friendly field name
     * @param value     the value to filter on
     * 
     * @return a new less-than-or-equal-to filter
     */
    public static SolrFilter LE(String field, double value) {
        return new Rel(field, true, "*", String.valueOf(value));
    }


    /**
     * Filter on an integer field having a value greater than a particular value.
     * 
     * @param field     user-friendly field name
     * @param value     the value to filter on
     * 
     * @return a new greater-than filter
     */
    public static SolrFilter GT(String field, int value) {
        return new Rel(field, false, String.valueOf(value), "*");
    }

    /**
     * Filter on an integer field having a value greater than or equal to a particular value.
     * 
     * @param field     user-friendly field name
     * @param value     the value to filter on
     * 
     * @return a new greater-than-or-equal-to filter
     */
    public static SolrFilter GE(String field, int value) {
        return new Rel(field, true, String.valueOf(value), "*");
    }

    /**
     * Filter on an integer field having a value less than a particular value.
     * 
     * @param field     user-friendly field name
     * @param value     the value to filter on
     * 
     * @return a new less-than filter
     */
    public static SolrFilter LT(String field, int value) {
        return new Rel(field, false, "*", String.valueOf(value));
    }

    /**
     * Filter on an integer field having a value less than or equal to a particular value.
     * 
     * @param field     user-friendly field name
     * @param value     the value to filter on
     * 
     * @return a new less-than-or-equal-to filter
     */
    public static SolrFilter LE(String field, int value) {
        return new Rel(field, true, "*", String.valueOf(value));
    }

}
