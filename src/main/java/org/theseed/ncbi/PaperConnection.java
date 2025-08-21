/**
 *
 */
package org.theseed.ncbi;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.p3api.Connection;

/**
 * This class manages a web connection that downloads papers from the web.  The primary method takes
 * a URL as input and returns the text of the paper.  This is an unusual connection type because it is not
 * table-driven.  Rather, the table is the URL of the paper.
 *
 * @author Bruce Parrello
 *
 */
public class PaperConnection extends Connection {

    // FIELDS
    /** logging facility */
    private static final Logger log = LoggerFactory.getLogger(PaperConnection.class);
    /** saved request URL */
    private String paperUrl;
    /** maximum number of redirects */
    private static final int MAX_REDIRECTS = 20;

    /**
     * Construct a standard page connection.
     */
    public PaperConnection() {
        super();
    }

    @Override
    protected Request createRequest(String url) {
        Request retVal = Request.Get(url);
        // Denote we want an HTML response.
        retVal.addHeader("Accept", "text/html");
        return retVal;
    }

    /**
     * This method will return the HTML for a paper at a given DOI URL.
     *
     * @param url	DOI URL of the paper
     *
     * @return the web page for the DOI link as a text string, or NULL if the link is invalid
     *
     * @throws IOException
     * @throws ParseException
     */
    public String getPaper(String url) throws ParseException, IOException {
        // Insure we have no accumulated parameters.
        this.clearBuffer();
        // Create the request from the URL.
        Request request = this.requestBuilder(url);
        HttpResponse resp = this.submitRequest(request);
        int pathLen = 0;
        // "submitRequest" will not return unless we got a good response, but that response may be a redirect.
        while (resp.getStatusLine().getStatusCode() >= 300) {
            // Insure we don't go too far.
            pathLen++;
            if (pathLen > MAX_REDIRECTS)
                this.throwHttpError("Too many redirects.");
            String newUrl = resp.getLastHeader("Location").getValue();
            if (! newUrl.startsWith("http")) {
            	try {
	                URI parsedUrl = new URI(this.paperUrl);
	                this.paperUrl = parsedUrl.getScheme() + "://" + parsedUrl.getHost() + newUrl;
            	} catch (URISyntaxException e) {
            		throw new ParseException(e.toString());
            	}
            } else
                this.paperUrl = newUrl;
            Request newReq = this.requestBuilder(this.paperUrl);
            log.debug("Following redirect to {}.", this.paperUrl);
            resp = this.submitRequest(newReq);
        }
        String retVal = EntityUtils.toString(resp.getEntity());
        return retVal;
    }

    /**
     * Record an unrecoverable HTTP error.  We override the normal one because we have different
     * needs for the log message.
     *
     * @param errorType		description of the error
     */
    @Override
    protected void throwHttpError(String errorType) {
        log.error("Failing request was {}", this.paperUrl);
        throw new DownloadException(this.paperUrl, errorType);
    }

    /**
     * @return the URL of the last paper we attempted to download
     */
    public String getPaperUrl() {
        return this.paperUrl;
    }

}
