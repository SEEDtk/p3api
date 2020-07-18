/**
 *
 */
package org.theseed.p3api;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Genome;
import org.theseed.genome.TaxItem;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonKey;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;



/**
 * This object represents a connection to PATRIC and provides methods for retrieving genome and feature data.
 * The default API is at https://patric.theseed.org/services/data_api, but this can be overridden using the
 * P3API_URL environment variable.
 *
 * @author Bruce Parrello
 *
 */
public class Connection {

    /** default URL */
    private static final String DATA_API_URL = "https://p3.theseed.org/services/data_api/";

    /** taxonomy URL format */
    private static final String NCBI_TAX_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=taxonomy&id=%d";

    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(Connection.class);

    /** maximum retries */
    private static final int MAX_TRIES = 5;

    /** maximum length of a key list (in characters) */
    private static final int MAX_LEN = 50000;

    /** pattern for extracting return ranges */
    private static final Pattern RANGE_INFO = Pattern.compile("items \\d+-(\\d+)/(\\d+)");

    /** default timeout interval in milliseconds */
    private static final int DEFAULT_TIMEOUT = 3 * 60 * 1000;

    /**
     * description of the major SOLR tables
     */
    public enum Table {

        GENOME("genome", "genome_id"),
        FEATURE("genome_feature", "patric_id"),
        TAXONOMY("taxonomy", "taxon_id"),
        CONTIG("genome_sequence", "sequence_id"),
        SEQUENCE("feature_sequence", "md5"),
        SUBSYSTEM_ITEM("subsystem", "id"),
        FAMILY("protein_family_ref", "family_id");

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
    protected String url;
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
    /** last request sent */
    private String basicParms;
    /** timeout interval */
    private int timeout;
    /** time of last NCBI query */
    private long lastNcbiQuery;

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
     * @param keyName	name of the field containing the strings
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
     * Extract a list of integers from a record field.
     *
     * @param record	record containing the field
     * @param keyName		name of the field containing the numbers
     *
     * @return an array of the integers in the field
     */
    public static int[] getIntegerList(JsonObject record, String keyName) {
        JsonArray list = record.getCollectionOrDefault(COLLECTION.use(keyName));
        int length = list.size();
        int[] retVal = new int[length];
        for (int i = 0; i < length; i++) {
            retVal[i] = list.getInteger(i);
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
        // Try to get the URL from the environment.
        this.url = System.getenv("P3API_URL");
        if (this.url == null) this.url = DATA_API_URL;
        // Insure that it ends in a slash.
        if (! StringUtils.endsWith(this.url, "/")) this.url += "/";
        // Set the default chunk size and build the parm buffer.
        this.chunkSize = 25000;
        this.buffer = new StringBuilder(MAX_LEN);
        // If the user is not logged in, this will be null and we won't be able to access
        // private genomes.
        this.authToken = token;
        // Default the trace stuff.
        this.table = "<none>";
        this.chunk = 0;
        // Default the timeout.
        this.timeout = DEFAULT_TIMEOUT;
        // Denote no NCBI queries yet.
        this.lastNcbiQuery = 0;
        // Turn off the stupid cookie message.
        java.util.logging.Logger.getLogger("org.apache.http.client.protocol.ResponseProcessCookies").setLevel(Level.OFF);
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
        List<JsonObject> recordList = this.query(table, fields, Criterion.EQ(table.getKey(), key));
        if (recordList.size() > 0) {
            retVal = (JsonObject) recordList.get(0);
        }
        return retVal;
    }

    /**
     * Request the specified fields from a set of records.
     *
     * @param table		the table containing the records
     * @param keys		a collection of the relevant key values
     * @param fields	a comma-delimited list of the fields desired
     *
     * @return a collection of JsonObjects of the desired records
     */
    public Map<String, JsonObject> getRecords(Table table, Collection<String> keys, String fields) {
        String keyName = table.getKey();
        List<JsonObject> records = getRecords(table, keyName, keys, fields);
        Map<String, JsonObject> retVal = new HashMap<String, JsonObject>(records.size());
        for (JsonObject record : records) {
            retVal.put(Connection.getString(record, keyName), record);
        }
        return retVal;
    }

    /**
     * Request the specified fields from a set of records using an alternate key and optional filtering
     * criteria.
     *
     * @param table		the table containing the records
     * @param keyName	the name of the key field to use
     * @param keys		a collection of the relevant key values
     * @param fields	a comma-delimited list of the fields desired
     * @param criteria	zero or more additional criteria
     *
     * @return a collection of JsonObjects of the desired records
     */
    public List<JsonObject> getRecords(Table table, String keyName, Collection<String> keys, String fields,
            String... criteria) {
        List<JsonObject> retVal = new ArrayList<JsonObject>(keys.size());
        // Insure we have the key field in the field list.
        String realFields = fields;
        int kLoc = realFields.indexOf(keyName);
        int kEnd = kLoc + keyName.length();
        if (kLoc < 0 || (kLoc != 0 && realFields.charAt(kLoc - 1) != ',') ||
                (kEnd != fields.length() && realFields.charAt(kEnd) != ','))
            realFields += "," + keyName;
        // Only proceed if the user wants at least one record.
        if (keys.size() > 0) {
            // Build the key list in the main string buffer.
            this.buffer.setLength(0);
            // Loop through the parameters, sending requests.
            for (String key : keys) {
                if (this.buffer.length() + key.length() >= MAX_LEN) {
                    this.processBatch(table, retVal, realFields);
                }
                if (this.buffer.length() == 0) {
                    // Here we are starting a new buffer.  Put in the criteria and then
                    // start the IN clause.
                    this.buffer.append(StringUtils.join(criteria, ','));
                    if (criteria.length > 0) this.buffer.append(',');
                    this.buffer.append("in(" + keyName + ",(");
                } else
                    this.buffer.append(",");
                this.buffer.append(Criterion.fix(key));
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
    private void processBatch(Table table, List<JsonObject> records, String fields) {
        // Build the HTTP request.
        Request request = this.requestBuilder(table.getName());
        // Close off the in-list.
        this.buffer.append("))");
        // Add the select list to the parameters being built.
        this.buffer.append("&select(" + fields + ")");
        // Get the desired records.
        List<JsonObject> recordList = this.getResponse(request);
        // Put them in the output list.
        records.addAll(recordList);
    }

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
        this.basicParms = this.buffer.toString();
        this.buffer.setLength(0);
        // Set up to loop through the chunks of response.
        this.chunk = 0;
        boolean done = false;
        while (! done) {
            request.bodyString(String.format("%s&limit(%d,%d)", this.basicParms, this.chunkSize, this.chunk),
                    ContentType.APPLICATION_FORM_URLENCODED);
            this.buffer.setLength(0);
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
     * Submit a request to the server.
     *
     * @param request	request to submit
     *
     * @return the response object from the request
     */
    private HttpResponse submitRequest(Request request) {
        HttpResponse retVal = null;
        // We will retry after certain errors.  These variables manage the retrying.
        int tries = 0;
        boolean done = false;
        while (! done) {
            // Query the server for the data.
            long start = System.currentTimeMillis();
            Response resp = null;
            try {
                resp = request.execute();
                if (log.isDebugEnabled()) {
                    log.debug(String.format("%2.3f seconds for HTTP request %s (position %d, try %d).",
                            (System.currentTimeMillis() - start) / 1000.0, this.table, this.chunk, tries));
                }
            // Check the response.
                retVal = resp.returnResponse();
                int code = retVal.getStatusLine().getStatusCode();
                if (code < 400) {
                    // Here we succeeded.  Stop the loop.
                    done = true;
                } else if (tries >= MAX_TRIES) {
                    // Here we have tried too many times.  Build a display string for the URL.
                    throwHttpError(retVal.getStatusLine().getReasonPhrase());
                } else {
                    // We have a server error, try again.
                    tries++;
                    log.debug("Retrying after error code {}.", code);
                }
            } catch (IOException e) {
                // This is almost certainly a timeout error.  We either retry or percolate.
                if (tries >= MAX_TRIES) {
                    // Time to give up.  Throw an error.
                    throwHttpError(e.getMessage());
                } else {
                    tries++;
                    log.debug("Retrying after {}.", e.getMessage());
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
        log.error("Failing request was {}", StringUtils.abbreviate(this.basicParms, " ...", 100));
        throw new RuntimeException("HTTP error: " + errorType);
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
        Request retVal = createRequest(table);
        // Save the name of the table for debugging purposes.
        this.table = table;
        // Denote we want a json response.
        retVal.addHeader("Accept", "application/json");
        // Attach authorization if we have a token.
        if (this.authToken != null) {
            retVal.addHeader("Authorization", this.authToken);
        }
        // Set the timeout.
        retVal.connectTimeout(this.timeout);
        return retVal;
    }

    /**
     * Create a request to get data from the specified table.
     *
     * @param table		name of the target table
     * @return
     */
    protected Request createRequest(String table) {
        return Request.Post(this.url + table);
    }

    /**
     * @param timeout 	the timeout to set, in seconds
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout * 1000;
    }

    /**
     * Compute the taxonomic information for a grouping and store it in a genome.
     *
     * @param genome	target genome
     * @param taxId		relevant taxonomy ID
     *
     * @return TRUE if successful, else FALSE
     */
    public boolean computeLineage(Genome genome, int taxId) {
        boolean retVal = false;
        // Get the data for this grouping.
        Request ncbiRequest = Request.Get(String.format(NCBI_TAX_URL, taxId));
        ncbiRequest.addHeader("Accept", "text/xml");
        ncbiRequest.connectTimeout(this.timeout);
        // We will now try to send the request.
        int tries = 0;
        Document taxonDoc = null;
        while (tries < MAX_TRIES && taxonDoc == null) {
            // Verify we are not asking NCBI too often.
            long diff = System.currentTimeMillis() - this.lastNcbiQuery;
            if (diff < 334)
                try {
                    Thread.sleep(334 - diff);
                    log.debug("NCBI throttle (diff = {}).", diff);
                } catch (InterruptedException e1) { }
            this.lastNcbiQuery = System.currentTimeMillis();
            // Now make the request.
            try {
                tries++;
                Response resp = ncbiRequest.execute();
                // Check the response.
                HttpResponse rawResponse = resp.returnResponse();
                int statusCode = rawResponse.getStatusLine().getStatusCode();
                if (statusCode < 400) {
                    String xmlString = EntityUtils.toString(rawResponse.getEntity());
                    InputSource xmlSource = new InputSource(new StringReader(xmlString));
                    taxonDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                            .parse(xmlSource);
                } else if (tries < MAX_TRIES) {
                    log.debug("Retrying taxonomy request after error code {}.", statusCode);
                } else {
                    throw new RuntimeException("Taxonomy request failed with error " + statusCode + " " +
                            rawResponse.getStatusLine().getReasonPhrase());
                }
            } catch (IOException e) {
                // This is usually a timeout error.
                if (tries >= MAX_TRIES)
                    throw new RuntimeException("HTTP error in genome ID request: " + e.getMessage());
                else {
                    tries++;
                    log.debug("Retrying genome ID request after " + e.getMessage());
                }
            } catch (Exception e) {
                throw new RuntimeException("Error parsing XML for taxonomy query: " + e.getMessage());
            }
        }
        // Now we have the taxonomy information.
        if (taxonDoc == null) {
            log.warn("No taxonomy information found for {}.", taxId);
        } else {
            // Get the genetic code.
            Node gcNode = taxonDoc.getElementsByTagName("GCId").item(0);
            int gc = Integer.valueOf(gcNode.getTextContent());
            // Everything is under the "Taxon" node.  We need to save all the
            // children of LineageEx, plus the ScientificName and Rank
            // at the top.
            String leafName = String.format("Unknown %s", genome.getDomain());
            String leafRank = "no rank";
            Node rootTaxon = taxonDoc.getElementsByTagName("Taxon").item(0);
            NodeList children = rootTaxon.getChildNodes();
            ArrayList<TaxItem> taxItems = new ArrayList<TaxItem>(30);
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                String type = child.getNodeName();
                switch (type) {
                case "ScientificName" :
                    leafName = child.getTextContent();
                    break;
                case "Rank" :
                    leafRank = child.getTextContent();
                    break;
                case "LineageEx" :
                    // Here we have the full lineage, and we need to convert it to tax items.
                    NodeList lineageChildren = child.getChildNodes();
                    for (int j = 0; j < lineageChildren.getLength(); j++) {
                        Node lineageChild = lineageChildren.item(j);
                        if (lineageChild.getNodeName().contentEquals("Taxon")) {
                            NodeList lineageItems = lineageChild.getChildNodes();
                            // The lineage node has 3 children-- TaxId, ScientificName, Rank.
                            int taxNum = 0;
                            String taxName = "";
                            String taxRank = "no rank";
                            for (int c = 0; c < lineageItems.getLength(); c++) {
                                Node grandChild = lineageItems.item(c);
                                String subType = grandChild.getNodeName();
                                switch (subType) {
                                case "TaxId" :
                                    taxNum = Integer.valueOf(grandChild.getTextContent());
                                    break;
                                case "ScientificName" :
                                    taxName = grandChild.getTextContent();
                                    break;
                                case "Rank" :
                                    taxRank = grandChild.getTextContent();
                                    break;
                                }
                            }
                            TaxItem item = new TaxItem(taxNum, taxName, taxRank);
                            taxItems.add(item);
                        }
                    }
                }
            }
            // Add the leaf to the end.
            taxItems.add(new TaxItem(taxId, leafName, leafRank));
            // Convert the list to an array.
            TaxItem[] lineage = new TaxItem[taxItems.size()];
            lineage = taxItems.toArray(lineage);
            // Store the array and the genetic code.
            genome.setGeneticCode(gc);
            genome.setLineage(lineage);
            // Denote we were successful.
            retVal = true;
        }
        return retVal;
    }

}
