/**
 *
 */
package org.theseed.ncbi;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.theseed.p3api.Connection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

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
    /** URL to use for current request */
    private String url;
    /** recommended chunk size */
    private int chunkSize;
    /** XML document builder */
    private DocumentBuilder docFactory;
    /** default chunk size */
    private static int DEFAULT_CHUNK_SIZE = 100;
    /** ENTREZ URL for ID search */
    private static final String SEARCH_URL = "https://www.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi";
    /** ENTREZ URL for data fetch */
    private static final String FETCH_URL = "https://www.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi";

    /**
     * Construct a new NCBI connection.
     */
    public NcbiConnection() {
        super();
        this.webenv = null;
        this.chunkSize = DEFAULT_CHUNK_SIZE;
        try {
            this.docFactory = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Cannot create XML builder: " + e.getMessage());
        }
    }

    /**
     * Execute a query against the specified table with the specified conditions.
     *
     * @param query		query specification containing the table and the conditions
     *
     * @return the XML elements for the query results
     *
     * @throws XmlException
     */
    public List<Element> query(NcbiQuery query) throws XmlException {
        // First, we make the search request.
        this.url = SEARCH_URL;
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
        Element result = this.getResponse(request, 0);
        int count = XmlUtils.getXmlInt(result, "Count");
        String queryKey = XmlUtils.getXmlString(result, "QueryKey");
        this.webenv = XmlUtils.getXmlString(result, "WebEnv");
        List<Element> retVal = new ArrayList<Element>(count);
        // Now we use a fetch request to get the individual records.  These come back in chunks.
        this.url = FETCH_URL;
        this.clearBuffer();
        this.bufferAppend("db=", tableName, "&webenv=", this.webenv, "&query_key=", queryKey, "&", table.returnType());
        this.setChunkPosition(0);
        request = this.requestBuilder(tableName);
        // Loop until we've gotten them all.
        while (! this.atChunkPosition(count)) {
            // Get the next chunk.
            result = this.getResponse(request, this.chunkSize);
            // Copy all its nodes into the result.
            NodeList nodes = result.getElementsByTagName(table.tagName());
            for (int i = 0; i < nodes.getLength(); i++)
                retVal.add((Element) nodes.item(i));
            // Advance the position.
            this.moveChunkPosition(this.chunkSize);
        }
        return retVal;
    }

    /**
     * Get an XML document for a chunk request.
     *
     * @param request	built request to send
     * @param size		number of records to return
     *
     * @return the XML document from the server
     *
     * @throws XmlException
     */
    private Element getResponse(Request request, int size) throws XmlException {
        // Add the parameters to the request.
        request.bodyString(String.format("%s&retstart=%d&retmax=%d", this.getBasicParms(),
                this.getChunkPosition(), size), ContentType.APPLICATION_FORM_URLENCODED);
        long start = System.currentTimeMillis();
        this.paceNcbiQuery();
        HttpResponse resp = this.submitRequest(request);
        // "submitRequest" will not return unless we got a good response.  Get the response text
        // and convert it to XML.
        Element retVal;
        try {
            String xmlString = EntityUtils.toString(resp.getEntity());
            InputSource xmlSource = new InputSource(new StringReader(xmlString));
            Document doc = this.docFactory.parse(xmlSource);
            retVal = doc.getDocumentElement();
            Element error = XmlUtils.findFirstByTagName(retVal, "ERROR");
            if (error != null)
                throw new XmlException("NCBI ERROR: " + error.getTextContent());
        } catch (UnsupportedOperationException | IOException | SAXException e) {
            throw new XmlException("Error accessing XML response: " + e.getMessage());
        }
        if (log.isDebugEnabled()) {
            String duration = String.format("%2.3f", (System.currentTimeMillis() - start) / 1000.0);
            log.debug("XML document returned after {} seconds.", duration);
        }
        return retVal;
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
