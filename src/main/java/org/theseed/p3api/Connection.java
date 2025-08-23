/**
 *
 */
package org.theseed.p3api;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
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
    private static final Logger log = LoggerFactory.getLogger(Connection.class);
    /** parameter buffer */
    private final StringBuilder buffer;
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
    /** time of last status message */
    private long lastStatusMessage;
    /** number of bytes downloaded */
    private long byteCount;
    /** number of requests processed */
    private int requestCount;
    /** number of retries */
    private int retryCount;
    /** number of milliseconds between status messages */
    private long messageGap;
    /** NCBI API key */
    private final String apiKey;
    /** executor for cookie policy */
    private final Executor executor;
    /** maximum retries */
    public static final int MAX_TRIES = 5;
    /** maximum length of a key list (in characters) */
    protected static final int MAX_LEN = 50000;
    /** default timeout interval in milliseconds */
    protected static final int DEFAULT_TIMEOUT = 3 * 60 * 1000;
    /** warning threshold for response length */
    protected static final int WARN_CONTENT_LENGTH = 10000000;
    /** gap between status messages, in ms */
    private static final long DEFAULT_MESSAGE_GAP = 5000;

    /**
     * Construct a connection object. This initializes all our tracking stuff and
     * the NCBI throttling timers, but the subclass must handle the actual connecting.
     */
    public Connection() {
        // Default the trace stuff.
        this.setTable("<none>");
        this.setChunkPosition(0);
        this.messageGap = DEFAULT_MESSAGE_GAP;
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
        // Set the counters.
        this.lastStatusMessage = System.currentTimeMillis();
        this.requestCount = 0;
        this.retryCount = 0;
        this.byteCount = 0;
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
     * Set the log-message gap to the specified number of seconds.
     * 
     * @param seconds   number of seconds for the message gap, or 0 to turn off trace messages
     */
    public void setMessageGap(int seconds) {
        if (seconds <= 0)
            this.messageGap = Long.MAX_VALUE;
        else
            this.messageGap = seconds * 1000;
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
        // Set the last-message time if this is the first request.
        if (this.requestCount == 0)
            this.lastStatusMessage = System.currentTimeMillis();
        // We will retry after certain errors.  These variables manage the retrying.
        int tries = 0;
        boolean done = false;
        while (! done) {
            // Query the server for the data.
            long start = System.currentTimeMillis();
            Response resp;
            try {
                // Submit the request.
                log.debug("Submitting request {}.", request.toString());
                resp = this.executor.execute(request);
                this.requestCount++;
                long now = System.currentTimeMillis();
                if (log.isInfoEnabled() && now - this.lastStatusMessage >= this.messageGap) {
                    log.info("Processed {} requests ({} retries) so far, {} bytes downloaded.", this.requestCount, this.retryCount, 
                        FileUtils.byteCountToDisplaySize(this.byteCount));
                    this.lastStatusMessage = now;
                }
                // Check the response.
                retVal = this.extractResponse(resp);
                if (log.isDebugEnabled()) {
                    log.debug(String.format("%2.3f seconds for HTTP request %s (position %d, try %d).",
                            (System.currentTimeMillis() - start) / 1000.0, this.getTable(), this.getChunkPosition(), tries));
                }
                int code = retVal.getStatusLine().getStatusCode();
                if (code < 400) {
                    // Here we succeeded.  Stop the loop.
                    done = true;
                    this.byteCount += retVal.getEntity().getContentLength();
                } else if (tries >= MAX_TRIES || code == 403) {
                    // Here we have tried too many times or it is an unrecoverable error.  Build a display string for the URL.
                    throwHttpError(retVal.getStatusLine().getReasonPhrase());
                } else if (code == 429) {
                    // Here we are being throttled. Sleep with exponential backoff and try again.
                    tries++;
                    this.retryCount++;
                    this.pauseForRetry(tries);
                } else {
                    // We have a server error, try again.
                    tries++;
                    this.retryCount++;
                    if (log.isDebugEnabled()) {
                        String message = EntityUtils.toString(retVal.getEntity());
                        log.debug("Retrying after error code {}: {}.", code, message);
                    }
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
     * Pause for a retry. The initial pause is one second. Each subsequent retry increases the amount
     * to a max of 16 seconds.
     * 
     * @param tries     number of tries so far on this request
     */
    private void pauseForRetry(int tries) {
        long backoff = (long) Math.min(1000 * Math.pow(2, tries - 1), 16000); // max 16 seconds
        log.debug("Pausing for {} ms before retry after error code 429.", backoff);
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException e) {
            log.warn("Interrupting during throttle wait.");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Process the response. Along the way, we issue a warning if it is very long, as that
     * will cause the program to appear to hang while we unspool it.
     * 
     * @param resp      the response object to check
     * 
     * @return the downloaded response from the request
     * 
     * @throws IOException 
     */
    private HttpResponse extractResponse(Response resp) throws IOException {        
        HttpResponse retVal = resp.returnResponse();
        if (retVal.getEntity() != null) {
            long len = retVal.getEntity().getContentLength();
            long now = System.currentTimeMillis();
            if (len > WARN_CONTENT_LENGTH && now - this.lastStatusMessage > this.messageGap) {
                log.info("Response length was {} bytes, which caused a delay.", len);
                this.lastStatusMessage = now;
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
    public final void setTimeout(int timeout) {
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
    protected final void setChunkPosition(int chunk) {
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
    protected final void setTable(String table) {
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
    protected final String getApiKey() {
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
