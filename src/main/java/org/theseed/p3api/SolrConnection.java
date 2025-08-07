/**
 *
 */
package org.theseed.p3api;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Consts;
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
	/** sort field for current query */
	private String sortField;
	/** name of the feature files */
	public static final String JSON_FILE_NAME = "genome_feature.json";
	/** map of table names to sort fields */
	private static final Map<String, String> KEY_MAP;
	static {
		Map<String, String> map = new java.util.HashMap<>();
		map.put("antibiotics", "antibiotic_name+asc");
		map.put("bioset", "bioset_id+asc");
		map.put("bioset_result", "id+asc");
		map.put("enzyme_class_ref", "ec_number+asc");
		map.put("epitope", "epitope_id+asc");
		map.put("epitope_assay", "assay_id+asc");
		map.put("experiment", "exp_id+asc");
		map.put("feature_sequence", "md5+asc");
		map.put("gene_ontology_ref", "go_id+asc");
		map.put("genome", "genome_id+asc");
		map.put("genome_amr", "id+asc");
		map.put("genome_feature", "patric_id+asc");
		map.put("genome_sequence", "sequence_id+asc");
		map.put("id_ref", "id+asc");
		map.put("misc_niaid_sgc", "target_id+asc");
		map.put("pathway", "id+asc");
		map.put("pathway_ref", "id+asc");
		map.put("ppi", "id+asc");
		map.put("protein_family_ref", "family_id+asc");
		map.put("protein_feature", "id+asc");
		map.put("protein_structure", "pdb_id+asc");
		map.put("sequence_feature", "id+asc");
		map.put("sequence_feature_vt", "id+asc");
		map.put("serology", "id+asc");
		map.put("sp_gene", "id+asc");
		map.put("sp_gene_evidence", "id+asc");
		map.put("sp_gene_ref", "id+asc");
		map.put("spike_lineage", "id+asc");
		map.put("spike_variant", "id+asc");
		map.put("strain", "id+asc");
		map.put("structured_assertion", "id+asc");
		map.put("subsystem", "id+asc");
		map.put("subsystem_ref", "subsystem_name+asc");
		map.put("surveillance", "id+asc");
		map.put("taxonomy", "taxon_id+asc");
		map.put("transcriptomics_experiment", "e_id+asc");
		map.put("transcriptomics_gene", "id+asc");
		map.put("transcriptomics_sample", "pid+asc");
		KEY_MAP = java.util.Collections.unmodifiableMap(map);
	}
	/** genome dump directory filter */
	public static final FileFilter GENOME_FILTER = new FileFilter() {

	    @Override
	    public boolean accept(File pathname) {
	        File gFile = new File(pathname, SolrConnection.JSON_FILE_NAME);
	        return gFile.canRead();
	    }

	};

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

	@Override
	protected Request createRequest(String tableName) {
        Request retVal = Request.Post(this.getUrl() + tableName);
		// Save the sort key for the query.
		this.sortField = KEY_MAP.get(tableName);
		if (this.sortField == null)
			throw new IllegalArgumentException("No sort key defined for table " + tableName + ".");
        // Denote we want a json response with SOLR codes.
        retVal.addHeader("Accept", "application/solr+json");
        // Return the request.
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
	    // Set up to loop through the chunks of response. We memorize the chunk position for log tracing,
		// and specify we don't have a cursor mark yet.
	    this.setChunkPosition(0);
		String cursor = "*";
	    boolean done = false;
	    while (! done) {
	        request.bodyString(String.format("%s&rows=%d&sort=%s", this.getBasicParms(), this.getChunkSize(), this.sortField),
	        		ContentType.APPLICATION_FORM_URLENCODED);
	        this.clearBuffer();
	        long start = System.currentTimeMillis();
	        HttpResponse resp = this.submitRequest(request);
	        // We have a good response. Look for a cursor mark.
	        Header cursorH = resp.getFirstHeader("nextCursorMark");
	        if (cursorH == null) {
	            // If there is no cursor mark, we are done.
	            done = true;
	        } else {
	            // Parse out the cursor mark value.
	            cursor = cursorH.getValue();
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
				// Update the chunk position for tracing.
				this.setChunkPosition(count + this.getChunkPosition());

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