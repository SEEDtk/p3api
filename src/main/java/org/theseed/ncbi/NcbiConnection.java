/**
 *
 */
package org.theseed.ncbi;

import org.apache.http.client.fluent.Request;
import org.theseed.p3api.Connection;

/**
 * This is a connection object for NCBI Entrez database requests.  These come back in XML form, and each request
 * requires two stages:  storing the keys using the history feature, and then pulling the data records.  Each
 * query returns an XML document that may be parsed at leisure by the client.  The keys are internal to NCBI,
 * so all queries are based on field filters.  Each filter consists of a string to match and the name of the
 * field to match on.  The filters must all be true for the record to be returned.
 *
 * Note that the ID query is single-response.  We extract the WebEnv and query key and then chunk the results
 * from the fetch query.
 *
 * @author Bruce Parrello
 *
 */
public class NcbiConnection extends Connection {

    // FIELDS
    /** current webenv where our ID history is stored */
    private String webenv;
    /** ENTREZ URL for ID search */
    private static final String SEARCH_URL = "https://www.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi";
    /** ENTREZ URL for data fetch */
    private static final String FETCH_URL = "https://www.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi";




    @Override
    protected Request createRequest(String tableName) {
        // TODO code for createRequest
        return null;
    }
    // FIELDS
    // TODO data members for NcbiConnection

    // TODO constructors and methods for NcbiConnection
}
