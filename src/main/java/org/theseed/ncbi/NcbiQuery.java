/**
 *
 */
package org.theseed.ncbi;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Element;

/**
 * This is the base class for the various types of NCBI queries.
 *
 * @author Bruce Parrello
 *
 */
public abstract class NcbiQuery {

    // FIELDS
    /** name of database */
    private NcbiTable db;
    /** maximum number of records to return */
    private int limit;

    /**
     * Create an NCBI query object.
     *
     * @param table		target table
     */
    public NcbiQuery(NcbiTable table) {
        this.db = table;
        this.limit = Integer.MAX_VALUE;
    }

    /**
     * Run this query against the specified NCBI connection.
     *
     * @param ncbi		NCBI connection for servicing NCBI requests
     *
     * @return a list of the elements found by the query
     *
     * @throws XmlException
     * @throws IOException
     */
    public List<Element> run(NcbiConnection ncbi) throws XmlException, IOException {
        List<Element> retVal = ncbi.query(this);
        return retVal;
    }

    /**
     * @return the table for this query
     */
    public NcbiTable getTable() {
        return this.db;
    }

    /**
     * @return the name of the table for this query
     */
    public String getTableName() {
        return this.db.db();
    }

    /**
     * This method converts the query to the appropriate parameter string for the request.  This
     * includes URLencoding the values.
     *
     * @return the string representation of this request
     */
    @Override
    public final String toString() {
        // Create a buffer to hold the parameters.
        String table = this.getTableName();
        StringBuilder buffer = new StringBuilder(this.getBufferSize());
        buffer.append("db=" + table);
        this.storeParameters(buffer);
        // Return the parameter data as a string
        return buffer.toString();
    }



    /**
     * Store the parameters for the query in the specified string buffer.
     *
     * @param buffer	output string buffer
     */
    protected abstract void storeParameters(StringBuilder buffer);

    /**
     * @return the size to allocate for this query's string builder
     */
    protected abstract int getBufferSize();

    /**
     * Store a filter item in a query string buffer.  Note that because of a weird anomaly
     * in NCBI, when the field value is a number, we can't quote the string.
     *
     * @param buffer		output query string buffer
     * @param fieldName		name of the field for the filter
     * @param fieldValue	value for the filtering of the field
     */
    protected void storeFilter(StringBuilder buffer, String fieldName, String fieldValue) {
        // URLencode the value.
        String encodedValue;
        if (StringUtils.isNumeric(fieldValue))
            encodedValue = fieldValue;
        else try {
            encodedValue = "\"" + URLEncoder.encode(fieldValue, StandardCharsets.UTF_8.toString())
                    + "\"";
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 encoding is not supported.");
        }
        // Form the parameter string.
        buffer.append(encodedValue);
        buffer.append("[");
        buffer.append(fieldName);
        buffer.append(']');
    }

    /**
     * @return the record limit
     */
    public int getLimit() {
        return this.limit;
    }

    /**
     * Specify a new record limit.
     *
     * @param limit 	the limit to set
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }


}
