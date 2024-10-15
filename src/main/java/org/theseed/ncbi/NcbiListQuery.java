/**
 *
 */
package org.theseed.ncbi;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

/**
 * This is an alternate form of NCBI query that accepts as input a list of
 * IDs rather than a set of field/value pairs.  When a new ID is added to the
 * internal ID list, the number of IDs is returned.  When the query is run,
 * the ID list is cleared.  This is because the query is expected to be used
 * in batch operations.
 *
 * @author Bruce Parrello
 *
 */
public class NcbiListQuery extends NcbiQuery {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(NcbiListQuery.class);

    /** name of ID field to use */
    private String idName;
    /** list of field values for current query */
    private List<String> values;

    /**
     * Construct an NCBI list query.
     *
     * @param table			target table
     * @param fieldName		name of ID field to use
     */
    public NcbiListQuery(NcbiTable table, String fieldName) {
        super(table);
        this.idName = fieldName;
        this.values = new ArrayList<String>();
    }

    /**
     * Add an ID to the list and return the count of IDs in place.
     *
     * @param idValue		new ID to add
     *
     * @return the number of IDs currently stored
     */
    public int addId(String idValue) {
        this.values.add(idValue);
        return this.values.size();
    }

    /**
     * Add a collection of IDs to the list.
     */
    public void addIds(Collection<String> idValues) {
        this.values.addAll(idValues);
    }

    /**
     * @return TRUE if there are no IDs in the ID list
     */
    public boolean isEmpty() {
        return this.values.isEmpty();
    }

    @Override
    protected void storeParameters(StringBuilder buffer) {
        String delimiter = "&term=";
        for (String idValue : this.values) {
            buffer.append(delimiter);
            this.storeFilter(buffer, this.idName, idValue);
            delimiter = "+OR+";
        }
    }

    @Override
    protected int getBufferSize() {
        return (this.values.size() + 1) * 30;
    }

    /**
     * This is an override of the query runner that clears the ID list after
     * use.  Its purpose is to facilitate batch operations.
     *
     * @param ncbi	the NCBI connection to use for running the query
     *
     * @return a list of XML elements for the records returned
     *
     * @throws XmlException
     * @throws IOException
     */
    @Override
    public List<Element> run(NcbiConnection ncbi) throws XmlException, IOException {
        List<Element> retVal = super.run(ncbi);
        // Clear the ID list so the query can be reused.
        this.values.clear();
        return retVal;
    }

    /**
     * @return the number of values in this query
     */
    public int size() {
        return this.values.size();
    }

}
