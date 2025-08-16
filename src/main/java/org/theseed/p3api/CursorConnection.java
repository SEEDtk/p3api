package org.theseed.p3api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
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
 * This class is not even remotely thread-safe, so code accordingly.
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
    /** maximum number of records to retrieve */
    public static final int MAX_LIMIT = 1000000000;

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

    /**
     * This object describes a derived-field action. The information in here is used to
     * build the target-table query for the derived field and to perform the field value
     * mapping. To build the query, we need the user-friendly names for the target table's
     * key field, the relevant data field, and the set of target table key values to use.
     * To perform the mapping, we need the internal name of the source record field and
     * the user-friendly name for the target record field, and a map from the target key
     * values to the target field values. We will build the set of key values as a null-target
     * map and fill in the values after the query is executed.
     */
    protected class DerivedFieldAction {

        /* field list for the derivation query (comma-delimited) */
        private String fieldList;
        /** user-friendly name of the target table's data field */
        private String targetField;
        /** internal name of the source table's linking field */
        private String linkField;
        /** user-friendly name of the source table's linking field */
        private String linkFieldName;
        /** user-friendly name of the target table */
        private String targetTableName;
        /** user-friendly name of the target table's key */
        private String targetKeyField;

        /**
         * Construct a derived field action from the component data items.
         * @param dataMap       BV-BRC data map for queries
         * @param descriptor    derived field descriptor
         * 
         * @throws IOException 
         */
        public DerivedFieldAction(BvbrcDataMap dataMap, BvbrcDataMap.DerivedField descriptor) throws IOException {
            this.targetTableName = descriptor.getTargetTable();
            // Get the table descriptor from the data map.
            BvbrcDataMap.Table table = dataMap.getTable(this.targetTableName);
            // The target key field is the user-friendly key name.
            this.targetKeyField = table.getKeyField();
            // We need the field containing the derived value here. Again, it is user-friendly.
            this.targetField = descriptor.getTargetField();
            // The link field is the source field name.
            this.linkField = descriptor.getInternalName();
            // Build the field list for the target table query.
            this.fieldList = this.targetKeyField + "," + this.targetField;
            // Find the user-friendly link field name (if any).
            this.linkFieldName = table.getUserFieldName(this.linkField);
        }

        /**
         * Process this derived field for a query and update the output records. This must be done BEFORE
         * doing the 
         * 
         * @param sourceName    user-friendly name of this field
         * @param records       list of output records to update
         * 
         * @throws IOException 
         */
        public void updateDerivedField(String sourceName, List<JsonObject> records) throws IOException {
            // We will begin by building a map of all the required key values. These are the values found in the
            // linking field.
            Map<String, String> keyValues = new HashMap<>(records.size() * 4 / 3 + 1);
            // We'll track the maximum key length here.
            int maxKeyLength = 0;
            for (JsonObject record : records) {
                String key = KeyBuffer.getString(record, this.linkFieldName);
                if (! key.isEmpty()) {
                    keyValues.put(key, null);
                    if (key.length() > maxKeyLength)
                        maxKeyLength = key.length();
                }
            }
            // Now we execute the linking query. We compute the batch size from the maximum key length.
            int batchSize = 20000 / (maxKeyLength + 6);
            this.updateKeyValues(keyValues, records.size(), batchSize);
            // Now use the key-value map to update the records.
            for (JsonObject record : records) {
                String key = KeyBuffer.getString(record, this.linkFieldName);
                if (! key.isEmpty()) {
                    String value = keyValues.get(key);
                    if (value != null)
                        record.put(sourceName, value);
                }
            }
        }

        /**
         * Execute the linking query and update the key-value map.
         * 
         * @param keyValues     key-value map to update
         * @param size          number of records expected
         * @param batchSize     optimized batch size to use
         * 
         * @throws IOException 
         */
        private void updateKeyValues(Map<String, String> keyValues, int size, int batchSize) throws IOException {
            // Execute the linking query.
            List<JsonObject> linkRecords = CursorConnection.this.getRecords(this.targetTableName, size, batchSize, this.targetKeyField, keyValues.keySet(),
                this.fieldList);
            // Run through the records, filling in values.
            for (JsonObject linkRecord : linkRecords) {
                String key = KeyBuffer.getString(linkRecord, this.targetKeyField);
                String value = KeyBuffer.getString(linkRecord, this.targetField);
                keyValues.put(key, value);
            }
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
     * Get a bunch of records from a table using a key field. This is essentially a relationship
     * crossing. We take as input a list of key values and the name of a key field, then run the
     * query for each key value. A batch size is specified that limits the number of key values in
     * each query. The batch size is responsible for preventing parameter buffer overrun and
     * ensuring that the queries can be processed efficiently. Thus, the larger the key size, and
     * the larger the number of expected records per key, the smaller the batch size should be.
     * 
     * @param table         user-friendly table name
     * @param limit         maximum number of rows to return
     * @param batchSize     maximum number of key values to include in each query
     * @param keyField      user-friendly name of the key field
     * @param keyValues     list of key values
     * @param fields        comma-delimited list of fields to return
     * @param criteria      collection of filters to apply
     * 
     * @return the desired list of records
     * 
     * @throws IOException
     */
    public List<JsonObject> getRecords(String table, int limit, int batchSize, String keyField, Collection<String> keyValues,
            String fields, Collection<SolrFilter> criteria) throws IOException {
        // Copy the filter list. This list is usually small, and copying it allows us to modify it when processing
        // a query batch. Our modification will be to add the IN clause and then remove it.
        List<SolrFilter> filterList = new ArrayList<SolrFilter>(criteria.size() + 1);
        filterList.addAll(criteria);
        // We store the resulting records in here. We initialize it to the smaller of the limit and twice the batch
        // size, which is a compromise between memory usage and performance.
        int arraySize = Math.min(batchSize * 2, limit);
        List<JsonObject> retVal = new ArrayList<JsonObject>(arraySize);
        // Each batch query will return a number of records less than or equal to the limit. The remaining
        // limit is tracked in here, and is used to limit the next batch query.
        int remaining = limit;
        // The current batch of key values is built in here. If we have fewer keys, we will shorten the array,
        // but that will only happen during the last loop iteration.
        String[] keys = new String[batchSize];
        int numKeys = 0;
        // Now we are ready.
        Iterator<String> iter = keyValues.iterator();
        while (iter.hasNext() && remaining > 0) {
            String key = iter.next();
            // Insure there is room for this key.
            if (numKeys >= batchSize) {
                // We have filled the batch, so we can process it.
                this.processBatch(retVal, table, remaining, keyField, keys, fields, filterList);
                remaining = limit - retVal.size();
                // Reset for the next batch.
                numKeys = 0;
            }
            // We have room, so add the key.
            keys[numKeys] = key;
            numKeys++;
        }
        // If there are any remaining keys, process them as well.
        if (numKeys > 0 && remaining > 0) {
            keys = Arrays.copyOf(keys, numKeys);
            this.processBatch(retVal, table, remaining, keyField, keys, fields, filterList);
        }
        // Return the records found.
        return retVal;
    }

    /**
     * Process a batch of queries.
     * 
     * @param resultList    accumulating list of query results
     * @param table         user-friendly name of the target table for the query
     * @param limit         maximum number of records to return
     * @param keyField      user-friendly name of the key field
     * @param keys          array of desired key values
     * @param fields        comma-delimited list of fields to return
     * @param criteria      list of filters to apply; this is modified and restored by the method
     * 
     * @throws IOException 
     */
    private void processBatch(List<JsonObject> resultList, String table, int remaining, String keyField, String[] keys,
            String fields, List<SolrFilter> criteria) throws IOException {
        // We need to add an "IN" clause to the query to specify the keys.
        criteria.add(SolrFilter.IN(keyField, keys));
        // Now we execute the query.
        List<JsonObject> batchResults = this.getRecords(table, remaining, fields, criteria);
        // Add the results to the result list.
        resultList.addAll(batchResults);
        // Restore the criteria list.
        criteria.removeLast();
    }

    /**
     * Get a bunch of records from a table using a key field. This is essentially a relationship
     * crossing. We take as input a list of key values and the name of a key field, then run the
     * query for each key value. A batch size is specified that limits the number of key values in
     * each query. The batch size is responsible for preventing parameter buffer overrun and
     * ensuring that the queries can be processed efficiently. Thus, the larger the key size, and
     * the larger the number of expected records per key, the smaller the batch size should be.
     * 
     * @param table         user-friendly table name
     * @param limit         maximum number of rows to return
     * @param batchSize     maximum number of key values to include in each query
     * @param keyField      name of the key field
     * @param keyValues     list of key values
     * @param fields        comma-delimited list of fields to return
     * @param criteria      array of filters to apply
     * 
     * @return the desired list of records
     * 
     * @throws IOException
     */
    public List<JsonObject> getRecords(String table, int limit, int batchSize, String keyField, Collection<String> keyValues, 
            String fields, SolrFilter... criteria) throws IOException {
        return this.getRecords(table, limit, batchSize, keyField, keyValues, fields, Arrays.asList(criteria));
    }

    /**
     * Get a map of records from a table using a key field. This is essentially a relationship
     * crossing. We take as input a list of key values and the name of a key field, then run the
     * query for each key value. A batch size is specified that limits the number of key values in
     * each query. The batch size is responsible for preventing parameter buffer overrun and
     * ensuring that the queries can be processed efficiently. Thus, the larger the key size, and
     * the larger the number of expected records per key, the smaller the batch size should be.
     * 
     * @param table         user-friendly table name
     * @param limit         maximum number of rows to return
     * @param batchSize     maximum number of key values to include in each query
     * @param keyField      name of the key field
     * @param keyValues     list of key values
     * @param fields        comma-delimited list of fields to return
     * @param criteria      array of filters to apply
     * 
     * @return a map from the record keys to the records themselves
     * 
     * @throws IOException
     */
    public Map<String, JsonObject> getRecordMap(String table, int limit, int batchSize, Collection<String> keyValues, 
            String fields, SolrFilter... criteria) throws IOException {
        String keyField = this.dataMap.getTable(table).getKeyField();
        List<JsonObject> records = this.getRecords(table, limit, batchSize, keyField, keyValues, fields, criteria);
        Map<String, JsonObject> retVal = new HashMap<String, JsonObject>(records.size() * 4 / 3 + 1);
        for (JsonObject record : records) {
            String key = KeyBuffer.getString(record, keyField);
            retVal.put(key, record);
        }
        return retVal;
    }

    /**
     * Get a list of records from a table using the BVBRC data map. The client specifies the user-friendly
     * table name, a list of fields (also using user-friendly names), a limit, and one or more filters
     * (again, using user-friendly names). An error will occur if the filters overflow the parameter buffer.
     * Note that this is a convenience method that just converts the criterion array to a collection and calls
     * the main method.
     * 
     * @param table     user-friendly table name
     * @param limit     maximum number of rows to return
     * @param fields    comma-delimiter list of user-friendly field names
     * @param criteria  collection of user-friendly criteria
     * 
     * @return a list of the matching records
     * 
     * @throws IOException
     */
    public List<JsonObject> getRecords(String table, int limit, String fields, SolrFilter... criteria) throws IOException {
        return this.getRecords(table, limit, fields, Arrays.asList(criteria));
    }

    /**
     * Get a single record from a table using the primary key. The client specifies the user-friendly
     * table name, the key value, and a list of fields (also using user-friendly names).
     * 
     * @param table     user-friendly table name
     * @param keyValue  primary key value
     * @param fields    comma-delimiter list of user-friendly field names
     * 
     * @return the matching record, or NULL if it was not found
     * 
     * @throws IOException
     */
    public JsonObject getRecord(String table, String keyValue, String fields) throws IOException {
        JsonObject retVal;
        BvbrcDataMap.Table tbl = this.dataMap.getTable(table);
        // Get the user-friendly key field name.
        String keyName = tbl.getKeyField();
        List<JsonObject> records = this.getRecords(table, 1, fields, SolrFilter.EQ(keyName, keyValue));
        if (records.isEmpty())
            retVal = null;
        else
            retVal = records.get(0);
        return retVal;
    }

    /**
     * Get a list of records from a table using the BVBRC data map. The client specifies the user-friendly
     * table name, a list of fields (also using user-friendly names), a limit, and one or more filters
     * (again, using user-friendly names). An error will occur if the filters overflow the parameter buffer.
     * 
     * @param table     user-friendly table name
     * @param limit     maximum number of rows to return
     * @param fields    comma-delimiter list of user-friendly field names
     * @param criteria  collection of user-friendly criteria
     * 
     * @return a list of the matching records
     * 
     * @throws IOException
     */
    public List<JsonObject> getRecords(String table, int limit, String fields, Collection<SolrFilter> criteria) throws IOException {
        // Save the row limit.
        this.rowLimit = limit;
        // Convert the user-friendly table name to its SOLR name and build the request.
        BvbrcDataMap.Table solrTable = this.dataMap.getTable(table);
        // Create the field list. We also translate to internal names here, and we require that the main key be included.
        // Here is where we also set up the reverse map for undoing field-name translations, and the derived-value action
        // tables.
        Map<String, String> reverseMap = new TreeMap<String, String>();
        Map<String, DerivedFieldAction> derivedMap = new HashMap<String, DerivedFieldAction>();
        Set<String> fieldSet = new TreeSet<String>();
        fieldSet.add(solrTable.getInternalKeyField());
        for (String field : StringUtils.split(fields, ',')) {
            BvbrcDataMap.IField fieldInfo = solrTable.getInternalFieldData(field);
            if (fieldInfo == null)
                fieldSet.add(field);
            else {
                // Here we have a mapped field of some sort. Store the necessary internal field
                // name in the output field list.
                String internalName = fieldInfo.getInternalName();
                fieldSet.add(internalName);
                // If the field is an ordinary mapped field, save the reversing mapping.
                if (fieldInfo instanceof BvbrcDataMap.MappedField)
                    reverseMap.put(internalName, field);
                else if (fieldInfo instanceof BvbrcDataMap.DerivedField) {
                    // Here we have a derived field, so we need to set up a derived field action.
                    DerivedFieldAction action = new DerivedFieldAction(this.dataMap, (BvbrcDataMap.DerivedField) fieldInfo);
                    derivedMap.put(field, action);
                } else
                    throw new IllegalStateException("Unknown field type for field " + field + " in table " + table + ".");
            }
        }
        String allFields = StringUtils.join(fieldSet, ',');
        // Set up the constant parameters. These are used on every query, even after we find the cursor mark.
        this.constantParms = "fl=" + allFields + "&sort=" + solrTable.getInternalSortField() + "+asc";
        // Now we need to set up the filters. The filters all go in the "q" parameter, which
        // is what we store in the parameter buffer.
        String[] filterStrings = SolrFilter.toStrings(this.dataMap, table, criteria);
        if (filterStrings.length == 0)
            filterStrings = DEFAULT_FILTER;
        this.bufferAppend("q=", StringUtils.join(filterStrings, " AND "));
        // Form the full request using the filter and other one-time parameters.
        Request request = this.requestBuilder(solrTable.getInternalName());
        // Get the results.
        List<JsonObject> retVal = this.getResponse(request);
        // Next we need to update the derived fields.
        for (var actionEntry : derivedMap.entrySet()) {
            String sourceName = actionEntry.getKey();
            DerivedFieldAction action = actionEntry.getValue();
            action.updateDerivedField(sourceName, retVal);
        }
        // Now we fix the field names in the returned records. Note that if a field is not in the reverse
        // map, it will remain under its internal name. We only do this if the reverse map is nonempty.
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
            // Here we check to see if we are done. We are done if there is no cursor, if the number of
            // documents is equal to (or greater than) the number of rows left, or the number of
            // documents returned is equal to or greater than the total number of documents to find.
            int numReturned = this.getChunkPosition() + docs.size();
            // If the number of documents is more than the rows left or there is no cursor, we are done.
            if (docs.size() >= rowsLeft || numReturned >= numFound || cursorMark == null)
                done = true;
            // Update the chunk position.
            this.setChunkPosition(numReturned);
            // Save the actual records.
            for (Object doc : docs)
                retVal.add((JsonObject) doc);
        }
        log.info("Fetched {} records in {}ms.", retVal.size(), System.currentTimeMillis() - start);
        return retVal;
    }

}
