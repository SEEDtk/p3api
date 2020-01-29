/**
 *
 */
package org.theseed.p3api;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonKey;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;



/**
 * This object represents a connection to PATRIC and provides methods for retrieving genome and feature data.
 *
 * @author Bruce Parrello
 *
 */
public class Connection {

    /** logging facility */
    static Logger log = LoggerFactory.getLogger(Connection.class);

    /** maximum retries */
    private static final int MAX_TRIES = 5;

    /** maximum length of a key list (in characters) */
    private static final int MAX_LEN = 5000;

    /** pattern for extracting return ranges */
    private static final Pattern RANGE_INFO = Pattern.compile("items \\d+-(\\d+)/(\\d+)");

    /**
     * description of the major SOLR tables
     */
    public enum Table {

        GENOME("genome", "genome_id"),
        FEATURE("genome_feature", "patric_id"),
        TAXONOMY("taxonomy", "taxon_id"),
        CONTIG("genome_sequence", "sequence_id"),
        SEQUENCE("feature_sequence", "md5");

        // INTERNAL FIELDS

        /** name of key field */
        private String key;
        /** real name of table */
        private String realName;

        private Table(String name, String key) {
            this.key = key;
            this.realName = name;
        }

        /**
         * @return the name of this table's key field
         */
        public String getKey() {
            return this.key;
        }

        /**
         * @return the real name of this table's SOLR object
         */
        protected String getName() {
            return this.realName;
        }

    }

    /**
     * Internal class for creating Json keys on the fly.
     */
    public static class KeyBuffer implements JsonKey {

        String keyName;
        Object defaultValue;

        public KeyBuffer(String name, Object def) {
            this.keyName = name;
            this.defaultValue = def;
        }

        @Override
        public String getKey() {
            return this.keyName;
        }

        @Override
        public Object getValue() {
            return this.defaultValue;
        }

        /**
         * Use this key object with the specified name.
         *
         * @param newName	new name to assign to the key
         *
         * @return the key object for use as a parameter to a typed get
         */
        public KeyBuffer use(String newName) {
            this.keyName = newName;
            return this;
        }

    }

    // FIELDS

    /** data API url */
    private String url;
    /** security token */
    private String authToken;
    /** chunk size */
    private int chunkSize;
    /** parameter buffer */
    private StringBuilder buffer;
    /** table used in current request (for trace messages) */
    private String table;
    /** current position in response (for trace messages) */
    private int chunk;

    // JSON KEY BUFFERS
    private static final KeyBuffer STRING = new KeyBuffer("", "");
    private static final KeyBuffer INT = new KeyBuffer("", 0);
    private static final KeyBuffer COLLECTION = new KeyBuffer("", new JsonArray());
    private static final KeyBuffer DOUBLE = new KeyBuffer("", 0.0);

    /**
     * Extract a string from a record field.
     *
     * @param record	source record
     * @param keyName	name of the field containing a string
     *
     * @return the string value of the field
     */
    public static String getString(JsonObject record, String keyName) {
        String retVal = record.getStringOrDefault(STRING.use(keyName));
        return retVal;
    }

    /**
     * Extract an integer from a record field.
     *
     * @param record	source record
     * @param keyName	name of the field containing an integer number
     *
     * @return the integer value of the field
     */
    public static int getInt(JsonObject record, String keyName) {
        int retVal = record.getIntegerOrDefault(INT.use(keyName));
        return retVal;
    }

    /**
     * Extract a floating-point number from a record field.
     *
     * @param record	source record
     * @param keyName	name of the field containing a floating-point number
     *
     * @return the floating-point value of the field
     */
    public static double getDouble(JsonObject record, String keyName) {
        double retVal = record.getDoubleOrDefault(DOUBLE.use(keyName));
        return retVal;
    }

    /**
     * Extract a list of strings from a record field.
     *
     * @param record	source record
     * @param keyName	name of the field containing a floating-point number
     *
     * @return an array of the strings in the field
     */
    public static String[] getStringList(JsonObject record, String keyName) {
        JsonArray list = record.getCollectionOrDefault(COLLECTION.use(keyName));
        int length = list.size();
        String[] retVal = new String[length];
        for (int i = 0; i < length; i++) {
            retVal[i] = list.getString(i);
        }
        return retVal;
    }


    /**
     * Initialize a connection.
     *
     */
    public Connection(String token) {
        this.setup(token);
    }

    /**
     * Initialize a connection with a given authorization token.
     *
     * @param token		an authorization token, or NULL if the connection is to be unauthorized.
     */
    protected void setup(String token) {
        this.url = "https://p3.theseed.org/services/data_api/";
        this.chunkSize = 25000;
        this.buffer = new StringBuilder(MAX_LEN);
        // If the user is not logged in, this will be null.
        this.authToken = token;
        // Default the trace stuff.
        this.table = "<none>";
        this.chunk = 0;

    }

    /**
     * Initialize a connection for the currently-logged-in user.
     */
    public Connection() {
        String token = null;
        File tokenFile = new File(System.getProperty("user.home"), ".patric_token");
        if (tokenFile.canRead()) {
            // If any IO fails, we just operate without a token.
            try (Scanner tokenScanner = new Scanner(tokenFile)) {
                token = tokenScanner.nextLine();
            } catch (IOException e) { }
        }
        this.setup(token);
    }

    /**
     * Request the specified fields in the specified records of the specified table.
     *
     * @param table		the table containing the records
     * @param fields	a comma-delimited list of the fields desired
     * @param criteria	(repeating) the criteria to use for filtering the records
     *
     * @return a JsonArray of the desired results
     */
    public List<JsonObject> query(Table table, String fields, String... criteria) {
        Request request = this.requestBuilder(table.getName());
        this.buffer.append("select(" + fields + ")");
        this.addParameters(criteria);
        List<JsonObject> retVal = this.getResponse(request);
        return retVal;
    }

    /**
     * Request the specified fields from a single record.
     *
     * @param table		the table containing the record
     * @param key		key value for the record
     * @param fields	a comma-delimited list of the fields desired
     *
     * @return a JsonObject of the desired fields, or NULL if the record does not exist
     */
    public JsonObject getRecord(Table table, String key, String fields) {
        JsonObject retVal = null;
        List<JsonObject> recordList = this.query(table, fields, String.format("eq(%s,%s)", table.getKey(), key));
        if (recordList.size() > 0) {
            retVal = (JsonObject) recordList.get(0);
        }
        return retVal;
    }

    /**
     * Request the specified fields from a set of records.
     *
     * @param table		the table containing the record
     * @param keys		a collection of the relevant keys
     * @param fields	a comma-delimited list of the fields desired
     *
     * @return a collection of JsonObjects of the desired records
     */
    public Map<String, JsonObject> getRecords(Table table, Collection<String> keys, String fields) {
        Map<String, JsonObject> retVal = new HashMap<String, JsonObject>();
        // Insure we have the key field in the field list.
        String realFields = fields;
        int kLoc = realFields.indexOf(table.getKey());
        int kEnd = kLoc + table.getKey().length();
        if (kLoc < 0 || (kLoc != 0 && realFields.charAt(kLoc - 1) != ',') ||
                (kEnd == fields.length() && realFields.charAt(kEnd) == ','))
            realFields += "," + table.getKey();
        // Only proceed if the user wants at least one record.
        if (keys.size() > 0) {
            // Build the key list in the main string buffer.
            this.buffer.setLength(0);
            // Loop through the parameters, sending requests.
            for (String key : keys) {
                if (this.buffer.length() + key.length() >= MAX_LEN) {
                    this.processBatch(table, retVal, realFields);
                }
                if (this.buffer.length() == 0)
                    this.buffer.append("in(" + table.getKey() + ",(");
                else
                    this.buffer.append(",");
                this.buffer.append(key);
            }
            this.processBatch(table, retVal, realFields);
        }
        // Return the resulting map.
        return retVal;
    }

    /**
     * Process a single request for the records with the key being built in the parameter buffer.
     *
     * @param records	map of keys to records
     * @param fields	comma-delimited list of desired fields
     */
    private void processBatch(Table table, Map<String, JsonObject> records, String fields) {
        // Get the table's key name.
        String key = table.getKey();
        // Build the HTTP request.
        Request request = this.requestBuilder(table.getName());
        // Close off the in-list.
        this.buffer.append("))");
        // Add the select list to the parameters being built.
        this.buffer.append("&select(" + fields + ")");
        // Get the desired records.
        List<JsonObject> recordList = this.getResponse(request);
        // Put them in the output map.
        for (JsonObject record : recordList) {
            String rKey = Connection.getString(record, key);
            records.put(rKey, record);
        }
    }

    /**
     * Get fields from a single object.
     *
     * @param table		the SOLR table containing the object
     */
    /**
     * Extract the response for a request.
     *
     * @param request	request to send to PATRIC
     *
     * @return a JSON object containing the results of the request
     */
    private List<JsonObject> getResponse(Request request) {
        List<JsonObject> retVal = new ArrayList<JsonObject>();
        // Move the parameters into the request.  Note we clear the buffer for next time.
        String basicParms = this.buffer.toString();
        this.buffer.setLength(0);
        // Set up to loop through the chunks of response.
        this.chunk = 0;
        boolean done = false;
        while (! done) {
            request.bodyString(String.format("%s&limit(%d,%d)", basicParms, this.chunkSize, this.chunk),
                    ContentType.APPLICATION_FORM_URLENCODED);
            this.buffer.setLength(0);
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
                    this.chunk = Integer.valueOf(rMatcher.group(1));
                    int total = Integer.valueOf(rMatcher.group(2));
                    done = (this.chunk >= total);
                } else {
                    log.debug("Range string did not match: \"{}\".", value);
                    done = true;
                }
            }
            String jsonString;
            try {
                jsonString = EntityUtils.toString(resp.getEntity());
            } catch (IOException e) {
                throw new RuntimeException("HTTP conversion error: " + e.getMessage());
            }
            JsonArray data = Jsoner.deserialize(jsonString, (JsonArray) null);
            if (data == null) {
                throw new RuntimeException("Unexpected JSON response: " + StringUtils.substring(jsonString, 0, 50));
            } else {
                for (Object record : data) {
                    retVal.add((JsonObject) record);
                }
            }
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
    private HttpResponse submitRequest(Request request) {
        HttpResponse retVal = null;
        try {
            // We will retry after certain errors.  These variables manage the retrying.
            int tries = 0;
            boolean done = false;
            while (! done) {
                // Query the server for the data.
                long start = System.currentTimeMillis();
                Response resp = request.execute();
                if (log.isTraceEnabled()) {
                    log.trace(String.format("%2.3f seconds for HTTP request %s (position %d, try %d).",
                            (System.currentTimeMillis() - start) / 1000.0, this.table, this.chunk, tries));
                }
                // Check the response.
                retVal = resp.returnResponse();
                int code = retVal.getStatusLine().getStatusCode();
                if (code < 400) {
                    // Here we succeeded.  Stop the loop.
                    done = true;
                } else if (tries >= MAX_TRIES) {
                    // Here we have tried too many times.
                    throw new RuntimeException("HTTP error: " + retVal.getStatusLine().getReasonPhrase());
                } else {
                    // We have a server error, try again.
                    tries++;
                    log.debug("Retrying after error code {}.", code);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("HTTP error: " + e.getMessage());
        }
        return retVal;
    }

    /**
     * Add parameter strings to the parameter buffer.
     *
     * @param parameters	parameter strings to store
     */
    private void addParameters(String... parameters) {
        for (String parm : parameters) {
            buffer.append('&');
            buffer.append(parm);
        }
    }

    /**
     * Create a request builder for the specified data object.
     *
     * @param table	name of the target SOLR table
     *
     * @return a request builder with any required authorization and the proper URL
     */
    private Request requestBuilder(String table) {
        Request retVal = Request.Post(this.url + table);
        // Save the name of the table for debugging purposes.
        this.table = table;
        // Denote we want a json response.
        retVal.addHeader("Accept", "application/json");
        // Attach authorization if we have a token.
        if (this.authToken != null) {
            retVal.addHeader("Authorization", this.authToken);
        }
        return retVal;
    }
}
