/**
 *
 */
package org.theseed.p3api;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

/**
 * This is a base class for connections that query the BV-BRC (formerly PATRIC) SOLR cores.
 * There is a subclass for the low-level core dumps and one for sophisticated database queries.
 *
 * @author Bruce Parrello
 *
 */
public abstract class SolrConnection extends Connection {

	// FIELDS
	/** logging facility */
	protected static Logger log = LoggerFactory.getLogger(SolrConnection.class);
	/** data API url */
	private String url;
	/** chunk size */
	private int chunkSize;
	/** name of the feature files */
	public static final String JSON_FILE_NAME = "genome_feature.json";
	/** genome dump directory filter */
	public static final FileFilter GENOME_FILTER = new FileFilter() {

	    @Override
	    public boolean accept(File pathname) {
	        File gFile = new File(pathname, SolrConnection.JSON_FILE_NAME);
	        return gFile.canRead();
	    }

	};
	/** pattern for extracting return ranges */
	private static final Pattern RANGE_INFO = Pattern.compile("items \\d+-(\\d+)/(\\d+)");

	/**
	 * Construct a new SolrConnection.
	 */
	public SolrConnection() {
		super();
	}

	/**
	 * Set up the API URL and the chunk size.
	 *
	 * @param url	URL to use
	 */
	protected void apiSetup(String url) {
		this.setUrl(url);
        // Insure that it ends in a slash.
        if (! StringUtils.endsWith(this.getUrl(), "/")) this.setUrl(this.getUrl() + "/");
        // Set the default chunk size and build the parm buffer.
        this.setChunkSize(25000);
	}

	/**
	 * Clean a string for use in a SOLR query.
	 *
	 * @param string		string to clear
	 *
	 * @return a version of the string with special characters removed
	 */
	public static String clean(String string) {
	    // Remove quotes and change parens to spaces.
	    String retVal = StringUtils.replaceChars(string, "()'\"", "  ");
	    // Trim spaces on the edge.
	    retVal = StringUtils.trimToEmpty(retVal);
	    // Collapse spaces in the middle.
	    retVal = retVal.replaceAll("\\s+", " ");
	    return retVal;
	}

	/**
	 * Extract the response for a request.
	 *
	 * @param request	request to send to PATRIC
	 *
	 * @return a JSON object containing the results of the request
	 */
	protected List<JsonObject> getResponse(Request request) {
	    List<JsonObject> retVal = new ArrayList<JsonObject>();
	    // Set up to loop through the chunks of response.
	    this.setChunkPosition(0);
	    boolean done = false;
	    while (! done) {
	        request.bodyString(String.format("%s&limit(%d,%d)", this.getBasicParms(), this.getChunkSize(),
	        		this.getChunkPosition()), ContentType.APPLICATION_FORM_URLENCODED);
	        this.clearBuffer();
	        long start = System.currentTimeMillis();
	        HttpResponse resp = this.submitRequest(request);
	        // We have a good response. Check the result range.
	        Header range = resp.getFirstHeader("content-range");
	        if (range == null) {
	            // If there is no range data, we are done.
	            done = true;
	        } else {
	            // Parse out the range of values returned.
	            String value = range.getValue();
	            Matcher rMatcher = RANGE_INFO.matcher(value);
	            if (rMatcher.matches()) {
	                this.setChunkPosition(Integer.valueOf(rMatcher.group(1)));
	                int total = Integer.valueOf(rMatcher.group(2));
	                done = (this.getChunkPosition() >= total);
	            } else {
	                log.debug("Range string did not match: \"{}\".", value);
	                done = true;
	            }
	        }
	        String jsonString;
	        try {
	            jsonString = EntityUtils.toString(resp.getEntity());
	        } catch (IOException e) {
	            throw new RuntimeException("HTTP conversion error: " + e.toString());
	        }
	        JsonArray data = Jsoner.deserialize(jsonString, (JsonArray) null);
	        if (data == null) {
	            throw new RuntimeException("Unexpected JSON response: " + StringUtils.substring(jsonString, 0, 50));
	        } else {
	            int count = 0;
	            for (Object record : data) {
	                retVal.add((JsonObject) record);
	                count++;
	            }
	            if (log.isDebugEnabled()) {
	                String flag = (done ? "" : " (partial result)");
	                log.debug(String.format("%d records returned after %2.3f seconds%s.", count,
	                        (System.currentTimeMillis() - start) / 1000.0, flag));
	            }
	        }
	    }
	    return retVal;
	}

	/**
	 * @return the data retrieval URL
	 */
	protected String getUrl() {
		return url;
	}

	/**
	 * Specify a new data retrieval URL.
	 *
	 * @param url 	the url to set
	 */
	protected void setUrl(String url) {
		this.url = url;
	}

	/**
	 * @return the chunk size
	 */
	protected int getChunkSize() {
		return chunkSize;
	}

	/**
	 * Specify a chunk size for pacing.
	 *
	 * @param chunkSize 	the proposed chunk size to set
	 */
	protected void setChunkSize(int chunkSize) {
		this.chunkSize = chunkSize;
	}

}