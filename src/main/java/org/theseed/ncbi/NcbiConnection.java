/**
 *
 */
package org.theseed.ncbi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.p3api.Connection;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(NcbiConnection.class);
    /** current webenv where our ID history is stored */
    private String webenv;
    /** URL to use for current request */
    private String url;
    /** recommended chunk size */
    private int chunkSize;
    /** saved exception from XML conversion */
    private XmlException xmlError;
    /** default chunk size */
    private static int DEFAULT_CHUNK_SIZE = 200;
    /** ENTREZ URL for ID search */
    private static final String SEARCH_URL = "https://www.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi";
    /** ENTREZ URL for data fetch */
    private static final String FETCH_URL = "https://www.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi";
    /** ENTREZ URL for field list fetch */
    private static final String INFO_URL = "https://www.ncbi.nlm.nih.gov/entrez/eutils/einfo.fcgi";

    /**
     * This class describes a field.  It is returned by the getFieldNames method.
     */
    public static class Field {

        private String name;
        private String fullName;
        private String description;

        private Field(Element fieldElement) {
            this.name = XmlUtils.getXmlString(fieldElement, "Name");
            this.fullName = XmlUtils.getXmlString(fieldElement, "FullName");
            this.description = XmlUtils.getXmlString(fieldElement, "Description");
        }

        /**
         * @return the field's short name
         */
        public String getName() {
            return this.name;
        }

        /**
         * @return the field's full name
         */
        public String getFullName() {
            return this.fullName;
        }

        /**
         * @return the field description
         */
        public String getDescription() {
            return this.description;
        }

        /**
         * @return all the data in this descriptor
         */
        public String toLine() {
            return this.name + "\t" + this.fullName + "\t" + this.description;
        }

    }
    /**
     * Construct a new NCBI connection.
     */
    public NcbiConnection() {
        super();
        this.webenv = null;
        this.chunkSize = DEFAULT_CHUNK_SIZE;
    }

    /**
     * This method queries the list of fields for a database table.  Each field has a long name and a short name.
     * Only indexed fields are returned.  Records from the database always contain everything, and much of it is
     * not indexed.
     *
     * @param table		the table whose fields are desired
     *
     * @return a list of field descriptors
     *
     * @throws XmlException
     * @throws IOException
     */
    public List<Field> getFieldList(NcbiTable table) throws XmlException, IOException {
        this.url = INFO_URL;
        // In this case, the search parameters are simple, just the table name.
        String tableName = table.db();
        this.clearBuffer();
        this.bufferAppend("db=", tableName);
        Request request = this.requestBuilder(tableName);
        // There is also no chunking for this request.
        this.setChunkPosition(0);
        String resultString = this.getResponse(request, 400);
        Element result = XmlUtils.parseXmlString(resultString);
        // Get the field list from the result.
        List<Element> fieldList = XmlUtils.descendantsOf(result, "Field");
        List<Field> retVal = fieldList.stream().map(x -> new Field(x)).collect(Collectors.toList());
        return retVal;
    }

    /**
     * This method returns the valid field names for the specified table.  Both regular and full
     * names are allowed, and spaces are replaced with "+" in the full names.
     *
     * @param table		table of interest
     *
     * @return a set of the valid field names for the specified table
     *
     * @throws XmlException
     * @throws IOException
     *
     */
    public Set<String> getFieldNames(NcbiTable table) throws XmlException, IOException {
        List<Field> master = this.getFieldList(table);
        Set<String> retVal = new HashSet<String>(master.size() * 3 / 2);
        for (Field field : master) {
            retVal.add(field.getName());
            retVal.add(StringUtils.replaceChars(field.getFullName(), ' ', '+'));
        }
        return retVal;
    }

    /**
     * Execute a query against the specified table with the specified conditions.
     *
     * @param query		query specification containing the table and the conditions
     *
     * @return the XML elements for the query results
     *
     * @throws XmlException
     * @throws IOException
     */
    public List<Element> query(NcbiQuery query) throws XmlException, IOException {
        // First, we make the search request.
        this.url = SEARCH_URL;
        // Get the query limit.
        int limit = query.getLimit();
        // Now, we need to fill the buffer with the search parameters.
        this.clearBuffer();
        this.bufferAppend(query.toString(), "&useHistory=y");
        // If there is a web environment already available, use it.
        if (this.webenv != null)
            this.bufferAppend("&webenv=", this.webenv);
        // Create the request.
        final NcbiTable table = query.getTable();
        final String tableName = table.db();
        Request request = this.requestBuilder(tableName);
        // We do not need to do any chunking for the search.  The results are all stored in the
        // search-history web environment.  We will need to chunk the fetch output, however.
        this.setChunkPosition(0);
        String resultString = this.getResponse(request, 0);
        Element result = XmlUtils.parseXmlString(resultString);
        int count = XmlUtils.getXmlInt(result, "Count");
        if (count > limit) {
            log.info("Truncating query from {} to {} records.", count, limit);
            count = limit;
        }
        String queryKey = XmlUtils.getXmlString(result, "QueryKey");
        this.webenv = XmlUtils.getXmlString(result, "WebEnv");
        List<Element> retVal = new ArrayList<Element>(count);
        log.info("Expecting {} records from {}.", count, tableName);
        // Now we use a fetch request to get the individual records.  These come back in chunks.
        this.url = FETCH_URL;
        this.clearBuffer();
        this.bufferAppend("db=", tableName, "&webenv=", this.webenv, "&query_key=", queryKey, "&", table.returnType());
        this.setChunkPosition(0);
        request = this.requestBuilder(tableName);
        // Our results will go in here.
        List<String> resultQueue = new ArrayList<String>(count / this.chunkSize + 1);
        // Loop until we've gotten them all.
        while (! this.atChunkPosition(count)) {
            // Get the next chunk.  Note we do a little dancing to avoid asking for more
            // than we need.
            int n = this.chunkSize;
            if (this.getChunkPosition() + n > count)
                n = count - retVal.size();
            resultString = this.getResponse(request, n);
            resultQueue.add(resultString);
            // Advance the position.
            this.moveChunkPosition(this.chunkSize);
        }
        // Now we convert the XML strings into elements in parallel.
        this.xmlError = null;
        List<Element> xmlQueue = resultQueue.parallelStream().map(x -> this.parseXmlString(x))
                .collect(Collectors.toList());
        if (xmlError != null)
            throw xmlError;
        for (Element element : xmlQueue) {
            // Copy all its nodes into the result.
            NodeList nodes = element.getElementsByTagName(table.tagName());
            for (int i = 0; i < nodes.getLength(); i++)
                retVal.add((Element) nodes.item(i));
        }
        return retVal;
    }

    /**
     * Parse an XML string into an element.  This is a special non-throwing method that saves
     * the exception for later.
     *
     * @param xmlString		incoming XML string
     *
     * @return the document element, or NULL if an error occurred
     */
    private Element parseXmlString(String xmlString) {
        Element retVal = null;
        try {
            retVal = XmlUtils.parseXmlString(xmlString);
        } catch (Exception e) {
            this.xmlError = new XmlException("Error parsing response: " + e.toString());
        }
        return retVal;
    }

    /**
     * Get an XML document for a chunk request.
     *
     * @param request	built request to send
     * @param size		number of records to return
     *
     * @return the XML document from the server as a string
     *
     * @throws XmlException
     */
    private String getResponse(Request request, int size) throws IOException {
        // Add the parameters to the request.
        request.bodyString(String.format("%s&retstart=%d&retmax=%d", this.getBasicParms(),
                this.getChunkPosition(), size), ContentType.APPLICATION_FORM_URLENCODED);
        this.paceNcbiQuery();
        HttpResponse resp = this.submitRequest(request);
        // "submitRequest" will not return unless we got a good response.  Return the response text.
        return EntityUtils.toString(resp.getEntity());
    }

    @Override
    protected Request createRequest(String tableName) {
        Request retVal = Request.Post(this.url);
        // Denote we want an XML response.
        retVal.addHeader("Accept", "text/xml");
        // Set up the API key.
        if (this.getApiKey() != null)
            this.bufferAppend("&api_key=", this.getApiKey());
        return retVal;
    }

    /**
     * Set the chunk size for retrieval batches.
     *
     * @param chunkSize 	the chunk size to set
     */
    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

}
