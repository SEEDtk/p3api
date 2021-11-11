/**
 *
 */
package org.theseed.ncbi;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.theseed.utils.ParseFailureException;
import org.w3c.dom.Element;

/**
 * An NCBI query is essentially a map from field names to values.  These are
 * then assembled into a string for the parameters.
 *
 * A fluent interface is used to add the field/value pairs.
 *
 * @author Bruce Parrello
 *
 */
public class NcbiQuery {

    // FIELDS
    /** name of database */
    private NcbiTable db;
    /** date type for date filtering */
    private String dateType;
    /** minimum date for date filtering */
    private LocalDate minDate;
    /** parameter map */
    private Map<String, String> parmMap;
    /** constant for publication date type */
    public static final String PUB_DATE = "pdat";
    /** constant for completion date type */
    public static final String COMPLETE_DATE = "cdat";
    /** constant for creation date type */
    public static final String CREATE_DATE = "crdt";
    /** constant for entrez date type */
    public static final String ENTREZ_DATE = "edat";
    /** constant for modification date type */
    public static final String MOD_DATE = "mdat";
    /** set of valid date types */
    private static final Set<String> VALID_DATE_TYPES = new TreeSet<String>(Arrays.asList(MOD_DATE,
            ENTREZ_DATE, CREATE_DATE, COMPLETE_DATE, PUB_DATE));

    /**
     * Create a blank, empty query.
     *
     * @param table		target database table
     */
    public NcbiQuery(NcbiTable table) {
        this.db = table;
        this.minDate = null;
        this.dateType = null;
        // We use a tree map because the number of parameters is expected to be small.
        this.parmMap = new TreeMap<String, String>();
    }

    /**
     * Add a parameter.
     *
     * @param field		name of field
     * @param value		desired value
     *
     * @return this object, for chaining
     */
    public NcbiQuery EQ(String field, String value) {
        this.parmMap.put(field, value);
        return this;
    }

    /**
     * This method converts the query to the appropriate parameter string for the request.  This
     * includes URLencoding the values.
     *
     * @return the string representation of this request
     */
    @Override
    public String toString() {
        // Create a buffer to hold the parameters.  We will not do URL encoding here, since that
        // happens when we build the parameter string.
        String table = this.db.db();
        StringBuilder buffer = new StringBuilder(30*this.parmMap.size() + table.length() + 10);
        buffer.append("db=" + table);
        if (! this.parmMap.isEmpty()) {
            // Here we have parameters to add.  The first one is prefixed by the "term=" parm name.
            // The rest are separated by " AND ".
            String delimiter = "&term=";
            // Loop through the parameters.
            for (Map.Entry<String, String> parmEntry : this.parmMap.entrySet()) {
                // URLencode the value.
                String encodedValue;
                try {
                    encodedValue = URLEncoder.encode(parmEntry.getValue(), StandardCharsets.UTF_8.toString());
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException("UTF-8 encoding is not supported.");
                }
                // Form the parameter string.
                buffer.append(delimiter);
                buffer.append('"');
                buffer.append(encodedValue);
                buffer.append("\"[");
                buffer.append(parmEntry.getKey());
                buffer.append(']');
                delimiter = "+AND+";
            }
        }
        // Check for a date filter.
        if (this.minDate != null) {
            // Compute the current time.  This will be the maxdate.
            LocalDate now = LocalDate.now();
            String max = formatted(now);
            // Format the mindate.
            String min = formatted(this.minDate);
            // Create the date filter.
            buffer.append("&datetype=");
            buffer.append(this.dateType);
            buffer.append("&mindate=");
            buffer.append(min);
            buffer.append("&maxdate=");
            buffer.append(max);
        }
        // Return the parameter data as a string
        return buffer.toString();
    }

    /**
     * Specify a minimum date for records returned.
     *
     * @param type	date type
     * @param min	minimum date
     */
    public NcbiQuery since(String type, LocalDate min) {
        this.minDate = min;
        this.dateType = type;
        return this;
    }

    /**
     * @return the table for this query
     */
    public NcbiTable getTable() {
        return this.db;
    }

    /**
     * Convert a local date to Entrez format.
     *
     * @param date	date to convert
     *
     * @return the date as an Entrez string
     */
    private static String formatted(LocalDate date) {
        String retVal = String.format("%04d/%02d/%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
        return retVal;
    }

    /**
     * Run this query against the specified NCBI connection.
     *
     * @param ncbi		NCBI connection for servicing NCBI requests
     *
     * @return a list of the elements found by the query
     *
     * @throws XmlException
     */
    public List<Element> run(NcbiConnection ncbi) throws XmlException {
        List<Element> retVal = ncbi.query(this);
        return retVal;
    }

    /**
     * Insure that the specified date type is valid.
     *
     * @param dateType		date type to check
     *
     * @throws ParseFailureException
     */
    public static void validateDateType(String dateType) throws ParseFailureException {
        if (! VALID_DATE_TYPES.contains(dateType))
            throw new ParseFailureException("Invalid date type \"" + dateType + "\".");
    }

}
