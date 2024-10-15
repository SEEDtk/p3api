/**
 *
 */
package org.theseed.p3api;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the base class for web API connections.  It handles the grubby stuff about submitting requests
 * and formatting parameter buffers.  It also contains a mechanism for tracking NCBI requests, so that
 * we can be sure they are properly paced.  (It turns out multiple connection types require NCBI.)
 *
 * Please be aware that multiple connections running in parallel will screw up the NCBI pacing.  So
 * far, this is not a problem.  Most applications use a single connection subclass.
 *
 * @author Bruce Parrello
 *
 */
public abstract class Connection {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(Connection.class);
    /** parameter buffer */
    private StringBuilder buffer;
    /** table used in current request (for trace messages) */
    private String table;
    /** last request sent; used in chunking and in error messages */
    private String basicParms;
    /** current position in response (for trace messages) */
    private int chunk;
    /** timeout interval */
    private int timeout;
    /** minimum number of milliseconds between NCBI queries */
    private long ncbiSpeed;
    /** time of last NCBI query */
    private long lastNcbiQuery = System.currentTimeMillis();
    /** NCBI API key */
    private String apiKey;
    /** executor for cookie policy */
    private Executor executor;
    /** maximum retries */
    public static final int MAX_TRIES = 5;
    /** maximum length of a key list (in characters) */
    protected static final int MAX_LEN = 50000;
    /** default timeout interval in milliseconds */
    protected static final int DEFAULT_TIMEOUT = 3 * 60 * 1000;

    /**
     *
     */
    public Connection() {
        // Default the trace stuff.
        this.setTable("<none>");
        this.setChunkPosition(0);
        // Default the timeout.
        this.setTimeout(DEFAULT_TIMEOUT);
        // Read the API key.
        File apiFile = new File(System.getProperty("user.home"), ".ncbi_token");
        this.apiKey = readToken(apiFile);
        // If we have an API key, we can do 5 NCBI queries a second instead of 2.
        this.ncbiSpeed = 500;
        if (this.getApiKey() != null) {
            this.ncbiSpeed = 200;
        }
        // Create the parameter buffer.
        this.buffer = new StringBuilder(MAX_LEN);
        // Set up a client configuration to prevent the NCBI cookie message.
        HttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setCookieSpec(CookieSpecs.STANDARD).build())
                .disableRedirectHandling()
                .build();
        this.executor = Executor.newInstance(httpClient);
    }

    /**
     * Add the API key to an NCBI URL.
     *
     * @param url 	the URL to modify
     *
     * @return the modified URL
     */
    public String fixNcbiUrl(String url) {
        String retVal = url;
        if (this.apiKey != null)
            retVal += "&api_key=" + this.getApiKey();
        return retVal;
    }

    /**
     * Read a security token from a token file.
     *
     * @param tokenFile		file containing the token
     *
     * @return the token string, or NULL if none was found
     */
    protected static String readToken(File tokenFile) {
        String retVal = null;
        if (tokenFile.canRead()) {
            // If any IO fails, we just operate without a token.
            try (Scanner tokenScanner = new Scanner(tokenFile)) {
                retVal = tokenScanner.nextLine();
            } catch (IOException e) { }
        }
        return retVal;
    }

    /**
     * Submit a request to the server.
     *
     * @param request	request to submit
     *
     * @return the response object from the request
     */
    protected HttpResponse submitRequest(Request request) {
        HttpResponse retVal = null;
        // We will retry after certain errors.  These variables manage the retrying.
        int tries = 0;
        boolean done = false;
        while (! done) {
            // Query the server for the data.
            long start = System.currentTimeMillis();
            Response resp = null;
            try {
                // Submit the request.
                resp = this.executor.execute(request);
                // Check the response.
                retVal = resp.returnResponse();
                if (log.isDebugEnabled()) {
                    log.debug(String.format("%2.3f seconds for HTTP request %s (position %d, try %d).",
                            (System.currentTimeMillis() - start) / 1000.0, this.getTable(), this.getChunkPosition(), tries));
                }
                int code = retVal.getStatusLine().getStatusCode();
                if (code < 400) {
                    // Here we succeeded.  Stop the loop.
                    done = true;
                } else if (tries >= MAX_TRIES || code == 403) {
                    // Here we have tried too many times or it is an unrecoverable error.  Build a display string for the URL.
                    throwHttpError(retVal.getStatusLine().getReasonPhrase());
                } else if (code == 429) {
                    // Here we are being throttled. Sleep and try again.
                    tries++;
                    log.debug("Pausing for retry after error code 429.");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        log.warn("Interrupting during throttle wait.");
                    }
                } else {
                    // We have a server error, try again.
                    tries++;
                    log.debug("Retrying after error code {}.", code);
                }
            } catch (IOException e) {
                // This is almost certainly a timeout error.  We either retry or percolate.
                if (tries >= MAX_TRIES) {
                    // Time to give up.  Throw an error.
                    throwHttpError(e.toString());
                } else {
                    tries++;
                    log.debug("Retrying after {}.", e.toString());
                }
            }
        }
        return retVal;
    }

    /**
     * Record an unrecoverable HTTP error.
     *
     * @param errorType		description of the error
     */
    protected void throwHttpError(String errorType) {
        log.error("Failing request was {}", StringUtils.abbreviate(this.getBasicParms(), " ...", 100));
        throw new RuntimeException("HTTP error: " + errorType);
    }

    /**
     * Create a request for the specified data object.  The buffer should
     * contain the current parameters.  We allow, however, that the parameters
     * can be modified before asking for a response, due to chunking.
     *
     * @param table		name of the target table
     *
     * @return a request builder with any required authorization and the proper URL
     */
    protected Request requestBuilder(String table) {
        // Save the name of the table for debugging purposes.
        this.setTable(table);
        // Create the request.
        Request retVal = this.createRequest(table);
        // Save the parms for the request.
        this.saveBasicParms();
        // Set the timeout.
        retVal.connectTimeout(this.getTimeout());
        return retVal;
    }


    /**
     * @return a web request for data from the specified table
     *
     * @param tableName		name of the table in question
     */
    protected abstract Request createRequest(String tableName);

    /**
     * Erase the current parameter buffer.
     */
    protected void clearBuffer() {
        this.buffer.setLength(0);
    }

    /**
     * Append strings to the parameter buffer.
     *
     * @param string	strings to append
     */
    protected void bufferAppend(String... string) {
        for (String string0 : string)
            this.buffer.append(string0);
    }

    /**
     * Set the query timeout value.
     *
     * @param timeout 	the timeout to set, in seconds
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout * 1000;
    }

    /**
     * @return the parameter buffer length
     */
    protected int getBufferLength() {
        return this.buffer.length();
    }

    /**
     * Set the basic parameters to be reused for each query chunk
     * from the parameter buffer.
     *
     * @param basicParms the basic parameter string
     */
    protected void saveBasicParms() {
        this.basicParms = this.buffer.toString();
    }

    /**
     * @return the position of the next chunk of data
     */
    protected int getChunkPosition() {
        return chunk;
    }

    /**
     * @return TRUE if the chunk position is at or past the specified point
     *
     * @param pos	position to check against
     */
    protected boolean atChunkPosition(int pos) {
        return this.chunk >= pos;
    }


    /**
     * Specify the position of the next data chunk.
     */
    protected void setChunkPosition(int chunk) {
        this.chunk = chunk;
    }

    /**
     * Increment the chunk position.
     *
     * @param size	amount to add
     */
    public void moveChunkPosition(int size) {
        this.chunk += size;
    }

    /**
     * @return the basic parameter string (this is reused for chunked queries)
     */
    protected String getBasicParms() {
        return basicParms;
    }

    /**
     * @return the table
     */
    protected String getTable() {
        return table;
    }

    /**
     * @param table the table to set
     */
    protected void setTable(String table) {
        this.table = table;
    }

    /**
     * @return the timeout
     */
    protected int getTimeout() {
        return timeout;
    }

    /**
     * @return the apiKey
     */
    protected String getApiKey() {
        return apiKey;
    }

    /**
     * Pause until it is safe to issue a new NCBI query.
     */
    protected void paceNcbiQuery() {
        long diff = System.currentTimeMillis() - this.lastNcbiQuery;
        if (diff < this.ncbiSpeed)
            try {
                Thread.sleep(this.ncbiSpeed - diff);
                log.debug("NCBI throttle (diff = {}).", diff);
            } catch (InterruptedException e1) { }
        this.lastNcbiQuery = System.currentTimeMillis();
    }

}
