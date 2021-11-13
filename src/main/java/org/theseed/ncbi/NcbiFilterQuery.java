/**
 *
 */
package org.theseed.ncbi;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.theseed.utils.ParseFailureException;

/**
 * An NCBI query is essentially a map from field names to values.  These are
 * then assembled into a string for the parameters.
 *
 * A fluent interface is used to add the field/value pairs.
 *
 * @author Bruce Parrello
 *
 */
public class NcbiFilterQuery extends NcbiQuery {

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
    public NcbiFilterQuery(NcbiTable table) {
        super(table);
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
    public NcbiFilterQuery EQ(String field, String value) {
        this.parmMap.put(field, value);
        return this;
    }

    /**
     * Specify a minimum date for records returned.
     *
     * @param type	date type
     * @param min	minimum date
     */
    public NcbiFilterQuery since(String type, LocalDate min) {
        this.minDate = min;
        this.dateType = type;
        return this;
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

    @Override
    protected int getBufferSize() {
        return 30*this.parmMap.size() + 30;
    }

    @Override
    protected void storeParameters(StringBuilder buffer) {
        if (! this.parmMap.isEmpty()) {
            // Here we have parameters to add.  The first one is prefixed by the "term=" parm name.
            // The rest are separated by " AND ".
            String delimiter = "&term=";
            // Loop through the parameters.
            for (Map.Entry<String, String> parmEntry : this.parmMap.entrySet()) {
                buffer.append(delimiter);
                String fieldName = parmEntry.getKey();
                String fieldValue = parmEntry.getValue();
                this.storeFilter(buffer, fieldName, fieldValue);
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
    }

}
