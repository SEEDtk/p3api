package org.theseed.p3api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonKey;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

/**
 * This is a subclass of SolrConnection that uses cursor-based paging, which is more efficient
 * for large result sets. The drawback is that the user cannot control the sort order, and
 * the query does not use the standard query format, but rather the raw SOLR format.
 * 
 * @author Bruce Parrello
 */
public class CursorConnection extends SolrConnection {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(CursorConnection.class);
    /** authorization token */
    protected String authToken;
    /** bv-brc data map */
    private BvbrcDataMap dataMap;
    /** remaining row limit */
    private int rowLimit;
    /** constant parameters */
    private String constantParms;
    /** default filter string */
    private static final String[] DEFAULT_FILTER = { "*:*" };

    /**
     * This enum defines the keys used and their default values.
     */
    public static enum SpecialKeys implements JsonKey {
        RESPONSE("response", new JsonObject().putChain("numFound", 0).putChain("docs", new JsonArray())),
        NUMFOUND("numFound", 0),
        NEXT_CURSOR_MARK("nextCursorMark", null),
        DOCS("docs", new JsonArray());

        private final String m_key;
        private final Object m_value;

        SpecialKeys(final String key,final Object value) {
            this.m_key = key;
            this.m_value = value;
        }

        /**
         * This is the string used as a key in the incoming JsonObject map.
         */
        @Override
        public String getKey() {
            return this.m_key;
        }

        /**
         * This is the default value used when the key is not found.
         */
        @Override
        public Object getValue() {
            return this.m_value;
        }

    }


    // CONSTRUCTORS AND METHODS

    /**
     * Construct a cursor-based connection to the BV-BRC data API.
     */
    public CursorConnection(BvbrcDataMap dataMap) {
        super();
        String token = P3Connection.getLoginToken();
        String apiUrl = P3Connection.getApiUrl();
        this.setup(token, apiUrl);
        this.dataMap = dataMap;
    }

    /**
     * Initialize a connection with a given authorization token and URL.
     *
     * @param token		an authorization token, or NULL if the connection is to be unauthorized.
     */
    protected void setup(String token, String url) {
    	// Set up the URL and the chunk size.
        this.apiSetup(url);
        // If the user is not logged in, this will be null and we won't be able to access
        // private genomes.
        this.authToken = token;
    }

    /**
     * Get a list of records from a table using the BVBRC data map. The client specifies the user-friendly
     * table name, a list of fields (also using user-friendly names), a limit, and one or more filters
     * (again, using user-friendly names). An error will occur if the filters overflow the parameter buffer.
     * 
     * @param table     user-friendly table name
     * @param limit     maximum number of rows to return
     * @param fields    comma-delimiter list of user-friendly field names
     * @param criteria  array of user-friendly criteria
     * 
     * @throws IOException
     */
    public List<JsonObject> getRecords(String table, int limit, String fields, SolrFilter... criteria) throws IOException {
        // Save the row limit.
        this.rowLimit = limit;
        // Convert the user-friendly table name to its SOLR name and build the request.
        BvbrcDataMap.Table solrTable = this.dataMap.getTable(table);
        // Create the field list. We also translate to internal names here, and we require that the main key be included.
        // Finally, we also create a map for reversing the translations.
        Map<String, String> reverseMap = new TreeMap<String, String>();
        Set<String> fieldSet = new TreeSet<String>();
        fieldSet.add(solrTable.getInternalKeyField());
        for (String field : StringUtils.split(fields, ',')) {
            String internalName = solrTable.getInternalFieldName(field);
            fieldSet.add(internalName);
            if (! field.equals(internalName))
                reverseMap.put(internalName, field);
        }
        String allFields = StringUtils.join(fieldSet, ',');
        // Set up the constant parameters. These are used on every query, even after we find the cursor mark.
        this.constantParms = "fl=" + allFields + "&sort=" + solrTable.getInternalSortField() + "+asc";
        // Now we need to set up the filters.
        String[] filterStrings = SolrFilter.toStrings(this.dataMap, table, criteria);
        if (filterStrings.length == 0)
            filterStrings = DEFAULT_FILTER;
        this.bufferAppend("q=", StringUtils.join(filterStrings, " AND "));
        // Form the full request using the filter and other one-time parameters.
        Request request = this.requestBuilder(solrTable.getInternalName());
        // Get the results.
        List<JsonObject> retVal = this.getResponse(request);
        // Now we fix the field names in the returned records. Note that if a field is not in the reverse
        // map, it will remain under its internal name. This will only happen for the key field. If a
        // user-friendly name equals the key field name, then the key field will be overwritten.
        // We only need to do this if the reverse map is nonempty.
        if (! reverseMap.isEmpty()) {
            for (JsonObject record : retVal) {
                for (var reverseMapEntry : reverseMap.entrySet()) {
                    String internalName = reverseMapEntry.getKey();
                    String userFriendlyName = reverseMapEntry.getValue();
                    if (record.containsKey(internalName))
                        record.put(userFriendlyName, record.remove(internalName));
                }
            }
        }
        return retVal;
    }


    @Override
    protected Request createRequest(String table) {
        Request retVal = Request.Post(this.getUrl() + table);
        // Denote we want a json response.
        retVal.addHeader("Accept", "application/solr+json");
        retVal.addHeader("Content-Type", "application/solrquery+x-www-form-urlencoded");
        // Attach authorization if we have a token.
        if (this.authToken != null) {
            retVal.addHeader("Authorization", this.authToken);
        }
        return retVal;
    }

    @Override
	protected List<JsonObject> getResponse(Request request) {
	    List<JsonObject> retVal = new ArrayList<JsonObject>();
	    // Set up to loop through the chunks of response.
	    this.setChunkPosition(0);
        // Note that we assume the parmString is nonempty, because a default filter is passed in if it is empty.
        String parmString = this.getBasicParms() + "&" + this.constantParms;
        // Start with an unknown cursor mark.
        String cursorMark = "*";
        // Initialize for the processing loop.
        this.clearBuffer();
        long start = System.currentTimeMillis();
        boolean done = false;
        while (! done) {
            // Compute the row count for the parameters. Note we also figure out the total number of requested rows left.
            int rowMax = this.getChunkSize();
            int rowsLeft = this.rowLimit - this.getChunkPosition();
            if (rowMax > rowsLeft)
                rowMax = rowsLeft;
            // Build the full request.
            String body = parmString + "&cursorMark=" + cursorMark + "&rows=" + rowMax;
            request.bodyString(body, ContentType.APPLICATION_FORM_URLENCODED);
            HttpResponse resp = this.submitRequest(request);
            // Get the response JSON.
            JsonObject results;
            try {
                String jsonString = EntityUtils.toString(resp.getEntity());
                results = (JsonObject)Jsoner.deserialize(jsonString);
                if (results == null)
                    throw new RuntimeException("No results from BV-BRC.");
            } catch (IOException e) {
                throw new RuntimeException("HTTP conversion error: " + e.toString());
            } catch (JsonException e) {
                throw new RuntimeException("JSON parsing error: " + e.toString());
            }
            // Here we have a response. We need the number found and the actual records.
            JsonObject response = (JsonObject) results.get("response");
            long numFound = (long) response.getLong(SpecialKeys.NUMFOUND);
            JsonArray docs = response.getCollectionOrDefault(SpecialKeys.DOCS);
            log.debug("Chunk at position {} returned {} of {} records.", this.getChunkPosition(), docs.size(), numFound);
            // Update the cursor mark for next time.
            cursorMark = (String) results.getStringOrDefault(SpecialKeys.NEXT_CURSOR_MARK);
            // If the number of documents is more than the rows left or there is no cursor, we are done.
            if (docs.size() >= rowsLeft || cursorMark == null)
                done = true;
            // Update the chunk position.
            this.setChunkPosition(this.getChunkPosition() + docs.size());
            // Save the actual records.
            for (Object doc : docs)
                retVal.add((JsonObject) doc);
        }
        log.info("Fetched {} records in {}ms.", retVal.size(), System.currentTimeMillis() - start);
        return retVal;
    }

}
