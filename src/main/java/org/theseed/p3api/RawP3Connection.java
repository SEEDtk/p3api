/**
 *
 */
package org.theseed.p3api;

import java.util.List;

import org.apache.http.client.fluent.Request;

import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * This is a connection for PATRIC requests to dump the SOLR cores. It is much less sophisticated
 * than the full P3Connection, since it does not allow field selection and does not defined any
 * logical tables.  You just specify the name of a SOLR core and the criteria. There is also no
 * bothering with authentication.
 *
 * @author Bruce Parrello
 *
 */
public class RawP3Connection extends SolrConnection {

	// FIELDS
	/** URL for the SOLR API */
	private static final String SOLR_API_URL = "https://www.bv-brc.org/api/";

	/**
	 * Initialize this connection.
	 */
	public RawP3Connection() {
		super();
		this.apiSetup(SOLR_API_URL);
	}

	/**
	 * Get a list of records from a SOLR core. At least one criterion must be specified. (This
	 * is a requirement of our API.) Use the Criterion class to create criteria.
	 *
	 * @param tableName		name of the SOLR core
	 * @param criteria		array of criteria to use
	 */
	public List<JsonObject> getRecords(String tableName, String... criteria) {
		// Use the criteria to set up the parameters.
		this.clearBuffer();
		if (criteria.length < 1)
			throw new IllegalArgumentException("At least one criterion is required on " + tableName + " query.");
		this.bufferAppend(criteria[0]);
		for (int i = 1; i < criteria.length; i++)
			this.bufferAppend("&", criteria[i]);
        // Build the HTTP request.
        Request request = this.requestBuilder(tableName);
        // Get the desired records.
		List<JsonObject> retVal = this.getResponse(request);
		return retVal;
	}

	@Override
	protected Request createRequest(String tableName) {
        Request retVal = Request.Post(this.getUrl() + tableName);
        // Denote we want a json response.
        retVal.addHeader("Accept", "application/json");
        // Return the request.
        return retVal;
	}

}
