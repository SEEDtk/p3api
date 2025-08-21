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
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicNameValuePair;
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
    protected static final Logger log = LoggerFactory.getLogger(CursorConnection.class);
    /** authorization token */
    protected String authToken;
    /** bv-brc data map */
    private final BvbrcDataMap dataMap;
    /** remaining row limit */
    private long rowLimit;
    /** constant parameters */
    private String constantParms;
    /** map from internal names to user-friendly names */
    private final Map<String, String> reverseMap;
    /** map from user-friendly names to derived field actions */
    private final Map<String, DerivedFieldAction> derivedMap;
    /** default filter string */
    private static final String[] DEFAULT_FILTER = { "*:*" };
    /** maximum number of records to retrieve */
    public static final long MAX_LIMIT = 4000000000L;
    /** batch size for derived queries */
    public static final int DERIVED_BATCH_SIZE = 500;

    /**
     * This enum defines the keys used and their default values.
     */
    public static enum SpecialKeys implements JsonKey {
        RESPONSE("response", new JsonObject().putChain("numFound", 0).putChain("docs", new JsonArray())),
        NUMFOUND("numFound", 0),
        NEXT_CURSOR_MARK("nextCursorMark", null),
        DOCS("docs", new JsonArray()),
        SCHEMA("schema", new JsonObject()),
        FIELDS("fields", new JsonArray());

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
        private final String fieldList;
        /** internal name of the source table's linking field */
        private final String linkField;
        /** internal name of the target table */
        private final String targetTableName;
        /** internal name of the target table's key */
        private final String targetKeyField;
        /** internal name of the target table's data field */
        private final String targetField;

        /**
         * Construct a derived field action from the component data items.
         * @param dataMap       BV-BRC data map for queries
         * @param descriptor    derived field descriptor
         * 
         * @throws IOException 
         */
        public DerivedFieldAction(BvbrcDataMap dataMap, BvbrcDataMap.DerivedField descriptor) throws IOException {
            String targetTableUserName = descriptor.getTargetTable();
            // Get the table descriptor from the data map.
            BvbrcDataMap.Table table = dataMap.getTable(targetTableUserName);
            // Get the internal name of the target table.
            this.targetTableName = table.getInternalName();
            // The target key field is the internal key name.
            this.targetKeyField = table.getKeyField();
            // We need the field containing the derived value here. Again, it is an internal name.
            this.targetField = descriptor.getTargetField();
            // The link field is the source field internal name.
            this.linkField = descriptor.getInternalName();
            // Build the field list for the target table query. The target table query is unmapped,
            // so we are using all internal names.
            this.fieldList = this.targetKeyField + "," + this.targetField;
        }

        /**
         * Process this derived field for a query and update the output records. This must be done BEFORE
         * doing the field mappings, since everything is done unmapped here!
         * 
         * @param sourceName    user-friendly name of this field
         * @param records       list of output records to update
         * 
         * @throws IOException 
         */
        public void updateDerivedField(String sourceName, List<JsonObject> records) throws IOException {
            // We will begin by building a map of all the required key values. These are the values found in the
            // linking field. Each value is mapped to the list of records containing it.
            Map<String, List<JsonObject>> keyRecords = new HashMap<>(records.size() * 4 / 3 + 1);
            for (JsonObject record : records) {
                String key = KeyBuffer.getString(record, this.linkField);
                if (! key.isEmpty()) {
                    List<JsonObject> recordList = keyRecords.computeIfAbsent(key, k -> new ArrayList<>(2));
                    recordList.add(record);
                }
            }
            // Now we execute the linking query.
            this.updateKeyValues(keyRecords, sourceName);
        }

        /**
         * Execute the linking query and update the records for each key found.
         * 
         * @param keyRecords     key-value map to update
         * @param sourceName     user-friendly name of this field
         * 
         * @throws IOException 
         */
        private void updateKeyValues(Map<String, List<JsonObject>> keyRecords, String sourceName) throws IOException {
            // We have to process the keys in batches to keep from overrunning the query buffer. This list
            // will hold each set of keys.
            List<String> keyBatch = new ArrayList<>(CursorConnection.DERIVED_BATCH_SIZE);
            for (String key : keyRecords.keySet()) {
                // Insure there is room in the batch.
                if (keyBatch.size() >= CursorConnection.DERIVED_BATCH_SIZE) {
                    // We have filled the batch, so we can process it.
                    this.processKeyBatch(keyRecords, keyBatch, sourceName);
                    // Clear the batch for the next round.
                    keyBatch.clear();
                }
                // Add the key to the batch.
                keyBatch.add(key);
            }
            // Process the residual batch.
            if (! keyBatch.isEmpty())
                this.processKeyBatch(keyRecords, keyBatch, sourceName);
        }

        /**
         * Process a batch of keys using the linking query. When the corrected value is found, we store it
         * in all the records containing the key.
         * 
         * @param keyRecords    map of key values to records containing the value
         * @param keyBatch      list of key values to process
         * @param sourceName    user-friendly name of this field
         */
        private void processKeyBatch(Map<String, List<JsonObject>> keyRecords, List<String> keyBatch, String sourceName) throws IOException {
            // Execute the linking query. The linking query is unmapped and operates in a single batch, so it is very different
            // from our normal query process.
            Request request = CursorConnection.this.createRequest(this.targetTableName);
            String keyString = keyBatch.stream().map(x -> SolrFilter.quote(x))
                    .collect(Collectors.joining(" OR ", this.targetKeyField + ":(", ")"));
            NameValuePair keyPair = new BasicNameValuePair("q", keyString);
            NameValuePair fldPair = new BasicNameValuePair("fl", this.fieldList);
            NameValuePair rowPair = new BasicNameValuePair("rows", String.valueOf(CursorConnection.DERIVED_BATCH_SIZE));
            request.bodyForm(keyPair, fldPair, rowPair);
            // Now we execute the query.
            JsonObject results = CursorConnection.this.getResults(request);
            // Here we have a response. We need the number found and the actual records.
            JsonObject response = (JsonObject) results.get("response");
            JsonArray docs = response.getCollectionOrDefault(SpecialKeys.DOCS);
            log.debug("Found {} records during derived-field query for {} of {} keys.", docs.size(), keyBatch.size(), keyRecords.size());
            // Run through the records, filling in values.
            for (Object doc : docs) {
                JsonObject linkRecord = (JsonObject) doc;
                String key = KeyBuffer.getString(linkRecord, this.targetKeyField);
                String value = KeyBuffer.getString(linkRecord, this.targetField);
                for (JsonObject record : keyRecords.get(key))
                    record.put(sourceName, value);
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
        this.reverseMap = new TreeMap<>();
        this.derivedMap = new HashMap<>();
        this.rowLimit = MAX_LIMIT;
    }

    /**
     * Initialize a connection with a given authorization token and URL.
     *
     * @param token		an authorization token, or NULL if the connection is to be unauthorized.
     */
    final protected void setup(String token, String url) {
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
    public List<JsonObject> getRecords(String table, long limit, int batchSize, String keyField, Collection<String> keyValues,
            String fields, Collection<SolrFilter> criteria) throws IOException {
        List<JsonObject> retVal = new ArrayList<>();
        this.getRecords(table, limit, batchSize, keyField, keyValues, fields, criteria, retVal::add);
        return retVal;
    }

    /**
     * Process a bunch of records from a table using a key field. This is essentially a relationship
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
     * @param consumer      method to apply to each returned record
     * 
     * @throws IOException
     */
    public void getRecords(String table, long limit, int batchSize, String keyField, Collection<String> keyValues,
            String fields, Collection<SolrFilter> criteria, Consumer<JsonObject> consumer) throws IOException {
        // Copy the filter list. This list is usually small, and copying it allows us to modify it when processing
        // a query batch. Our modification will be to add the IN clause and then remove it.
        List<SolrFilter> filterList = new ArrayList<>(criteria.size() + 1);
        filterList.addAll(criteria);
        // Each batch query will return a number of records less than or equal to the limit. The remaining
        // limit is tracked in here, and is used to limit the next batch query.
        long remaining = limit;
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
                // We have filled the batch, so we can process it. We reduce the remaining-records count by
                // the number of records processed.
                remaining -= this.processBatch(consumer, table, remaining, keyField, keys, fields, filterList);
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
            this.processBatch(consumer, table, remaining, keyField, keys, fields, filterList);
        }
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
     * @param consumer      method to apply to each returned record
     * 
     * @throws IOException 
     */
    private long processBatch(Consumer<JsonObject> consumer, String table, long remaining, String keyField, String[] keys,
            String fields, List<SolrFilter> criteria) throws IOException {
        // We need to add an "IN" clause to the query to specify the keys.
        criteria.add(SolrFilter.IN(keyField, keys));
        // Now we execute the query.
        long retVal = this.getRecords(table, remaining, fields, criteria, consumer);
        // Restore the criteria list.
        criteria.removeLast();
        return retVal;
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
    public List<JsonObject> getRecords(String table, long limit, int batchSize, String keyField, Collection<String> keyValues, 
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
    public Map<String, JsonObject> getRecordMap(String table, long limit, int batchSize, Collection<String> keyValues, 
            String fields, SolrFilter... criteria) throws IOException {
        String keyField = this.dataMap.getTable(table).getKeyField();
        List<JsonObject> records = this.getRecords(table, limit, batchSize, keyField, keyValues, fields, criteria);
        Map<String, JsonObject> retVal = new HashMap<>(records.size() * 4 / 3 + 1);
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
    public List<JsonObject> getRecords(String table, long limit, String fields, SolrFilter... criteria) throws IOException {
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
     * Get a list of allowable internal field names for a specified table.
     * 
     * @param table     table whose field list is desired
     * 
     * @return a JSON array containing the schema's field list
     * 
     * @throws IOException
     */
    public JsonArray getFieldList(String table) throws IOException {
        // Get the table descriptor and the internal table name
        BvbrcDataMap.Table tbl = this.dataMap.getTable(table);
        String schemaTable = tbl.getInternalName() + "/schema";
        // Create a request for the schema.
        Request request = Request.Get(this.getUrl() + schemaTable + "?http_content-type=application/solrquery+x-www-form-urlencoded&http_accept=application/solr+json");
        // Get the field list from the schema response.
        JsonObject response = this.getResults(request);
        JsonObject schema = response.getMapOrDefault(SpecialKeys.SCHEMA);
        JsonArray retVal = schema.getCollectionOrDefault(SpecialKeys.FIELDS);
        if (retVal.isEmpty())
            throw new IOException("No fields found for table " + table + ".");
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
    public List<JsonObject> getRecords(String table, long limit, String fields, Collection<SolrFilter> criteria) throws IOException {
        List<JsonObject> retVal = new ArrayList<>();
        this.getRecords(table, limit, fields, criteria, retVal::add);
        return retVal;
    }

    /**
     * Process records from a table using the BVBRC data map. The client specifies the user-friendly
     * table name, a list of fields (also using user-friendly names), a limit, and one or more filters
     * (again, using user-friendly names). Each record is processed individually by a user-provided callback
     * (consumer).
     * 
     * @param table     user-friendly table name
     * @param limit     maximum number of rows to return
     * @param fields    comma-delimiter list of user-friendly field names
     * @param criteria  collection of user-friendly criteria
     * @param consumer  method to apply to each returned record
     * 
     * @throws IOException
     */
    public long getRecords(String table, long limit, String fields, Collection<SolrFilter> criteria, Consumer<JsonObject> consumer) throws IOException {
        // Save the row limit.
        this.rowLimit = limit;
        // Get the SOLR table information and set up the field mappings.
        BvbrcDataMap.Table solrTable = this.dataMap.getTable(table);
        String allFields = this.getAllFields(table, fields, solrTable);
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
        // Process the results.
        return this.getResponse(request, consumer);
    }

    /**
     * Analyze the requested fields for a SOLR query and set up all the mappings.
     * 
     * @param table         user-friendly name of the target table
     * @param fields        comma-delimited list of user-friendly field names
     * @param solrTable     SOLR table information
     * 
     * @return              a string containing all the fields to retrieve
     * 
     * @throws IOException
     */
    private String getAllFields(String table, String fields, BvbrcDataMap.Table solrTable) throws IOException {
        // Initialize the translation maps.
        this.reverseMap.clear();
        this.derivedMap.clear();
        // Set up the list of fields we want to retrieve. We always need the internal key field.
        Set<String> fieldSet = new TreeSet<>();
        fieldSet.add(solrTable.getInternalKeyField());
        // Now process all the requested fields. We also set up the mapping process here.
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
                else if (fieldInfo instanceof BvbrcDataMap.DerivedField derivedFieldInfo) {
                    // Here we have a derived field, so we need to set up a derived field action.
                    DerivedFieldAction action = new DerivedFieldAction(this.dataMap, derivedFieldInfo);
                    derivedMap.put(field, action);
                } else
                    throw new IllegalStateException("Unknown field type for field " + field + " in table " + table + ".");
            }
        }
        String allFields = StringUtils.join(fieldSet, ',');
        return allFields;
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

    /**
     * Process the response from a SOLR request. We get the records from the response a chunk at a time,
     * fix the fields, and pass the results to a consumer.
     * 
     * @param request       request to process
     * @param consumer      method to apply to each returned record
     * 
     * @return the numnber of records processed
     * 
     * @throws IOException
     */
	protected long getResponse(Request request, Consumer<JsonObject> consumer) throws IOException {
        // We'll count the number of records processed in here.
        long retVal = 0;
	    // Set up to loop through the chunks of response.
	    this.setChunkPosition(0);
        // Set up for progress messages if we go long.
        long lastMsg = System.currentTimeMillis();
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
            long rowMax = this.getChunkSize();
            long rowsLeft = this.rowLimit - this.getChunkPosition();
            if (rowMax > rowsLeft)
                rowMax = rowsLeft;
            // Build the full request.
            String body = parmString + "&cursorMark=" + cursorMark + "&rows=" + rowMax;
            request.bodyString(body, ContentType.APPLICATION_FORM_URLENCODED);
            JsonObject results = this.getResults(request);
            // Here we have a response. We need the number found and the actual records.
            JsonObject response = (JsonObject) results.get("response");
            long numFound = (long) response.getLong(SpecialKeys.NUMFOUND);
            JsonArray docs = response.getCollectionOrDefault(SpecialKeys.DOCS);
            log.debug("Chunk at position {} returned {} of {} records.", this.getChunkPosition(), docs.size(), numFound);
            long now = System.currentTimeMillis();
            if (now - lastMsg > 5000) {
                // If we are taking too long, log a message.
                log.info("Found {} records so far in {} query.", retVal, this.getTable());
                lastMsg = now;
            }
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
            List<JsonObject> records = new ArrayList<>(docs.size());
            for (Object doc : docs)
                records.add((JsonObject) doc);
            // Next we need to update the derived fields.
            for (var actionEntry : derivedMap.entrySet()) {
                String sourceName = actionEntry.getKey();
                DerivedFieldAction action = actionEntry.getValue();
                action.updateDerivedField(sourceName, records);
            }
            // Now we fix the field names in the returned records and pass them to the consumer.
            // Note that if a field is not in the reverse, it will remain under its internal
            // name.
            for (JsonObject record : records) {
                for (var reverseMapEntry : reverseMap.entrySet()) {
                    String internalName = reverseMapEntry.getKey();
                    String userFriendlyName = reverseMapEntry.getValue();
                    if (record.containsKey(internalName))
                        record.put(userFriendlyName, record.remove(internalName));
                }
                consumer.accept(record);
                retVal++;
            }
        }
        log.debug("Processed {} records in {}ms.", retVal, System.currentTimeMillis() - start);
        return retVal;
    }

    /**
     * Get the results from a proposed request. The results include the response and various headers.
     * 
     * @param request   request to send to the server
     * 
     * @return a result object for the request
     */
    private JsonObject getResults(Request request) {
        HttpResponse resp = this.submitRequest(request);
        // Get the result JSON.
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
        return results;
    }

}
