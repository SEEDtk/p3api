/**
 *
 */
package org.theseed.p3api;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
public class P3Connection extends Connection {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(Connection.class);

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
        FAMILY("protein_family_ref", "family_id"),
        SUBSYSTEM("subsystem_ref", "subsystem_id"),
        SP_GENE("sp_gene", "id")
        ;

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
    /** default URL */
    private static final String DATA_API_URL = "https://p3.theseed.org/services/data_api/";
    /** taxonomy URL format */
    private static final String NCBI_TAX_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=taxonomy&id=%d";
    /** pattern for extracting return ranges */
    private static final Pattern RANGE_INFO = Pattern.compile("items \\d+-(\\d+)/(\\d+)");
    /** list of domains for prokaryotes */
    public static final List<String> DOMAINS = Arrays.asList("Bacteria", "Archaea");

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
    public P3Connection(String token) {
        super();
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
        // If the user is not logged in, this will be null and we won't be able to access
        // private genomes.
        this.authToken = token;
    }

    /**
     * Initialize a connection for the currently-logged-in user.
     */
    public P3Connection() {
        super();
        File tokenFile = new File(System.getProperty("user.home"), ".patric_token");
        String token = readToken(tokenFile);
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
        this.bufferAppend("select(", fields, ")");
        this.addParameters(criteria);
        Request request = this.requestBuilder(table.getName());
        List<JsonObject> retVal = this.getResponse(request);
        return retVal;
    }

    /**
     * Add parameter strings to the parameter buffer.
     *
     * @param parameters	parameter strings to store
     */
    protected void addParameters(String... parameters) {
        for (String parm : parameters) {
            this.bufferAppend("&", parm);
        }
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
     * @return a map from each key value to the desired record
     */
    public Map<String, JsonObject> getRecords(Table table, Collection<String> keys, String fields) {
        String keyName = table.getKey();
        // Verify that we have the keyname.
        Set<String> fieldSet = new TreeSet<String>();
        fieldSet.add(keyName);
        Arrays.stream(StringUtils.split(fields, ',')).forEach(x -> fieldSet.add(x));
        String allFields = StringUtils.join(fieldSet, ',');
        List<JsonObject> records = getRecords(table, keyName, keys, allFields);
        Map<String, JsonObject> retVal = new HashMap<String, JsonObject>(records.size());
        for (JsonObject record : records) {
            retVal.put(P3Connection.getString(record, keyName), record);
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
            this.clearBuffer();
            // Loop through the parameters, sending requests.
            for (String key : keys) {
                if (this.getBufferLength() + key.length() >= MAX_LEN) {
                    this.processBatch(table, retVal, realFields);
                }
                if (this.getBufferLength() == 0) {
                    // Here we are starting a new buffer.  Put in the criteria and then
                    // start the IN clause.
                    this.bufferAppend(StringUtils.join(criteria, ','));
                    if (criteria.length > 0) this.bufferAppend(",");
                    this.bufferAppend("in(", keyName, ",(");
                } else
                    this.bufferAppend(",");
                this.bufferAppend(Criterion.fix(key));
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
        // Close off the in-list.
        this.bufferAppend("))");
        // Add the select list to the parameters being built.
        this.bufferAppend("&select(", fields, ")");
        // Build the HTTP request.
        Request request = this.requestBuilder(table.getName());
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
        // Set up to loop through the chunks of response.
        this.setChunkPosition(0);
        boolean done = false;
        while (! done) {
            request.bodyString(String.format("%s&limit(%d,%d)", this.getBasicParms(), this.chunkSize, this.getChunkPosition()),
                    ContentType.APPLICATION_FORM_URLENCODED);
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
     * Create a request to get data from the specified table.
     *
     * @param table		name of the target table
     *
     * @return a request for the specified table
     */
    protected Request createRequest(String table) {
        Request retVal = Request.Post(this.url + table);
        // Denote we want a json response.
        retVal.addHeader("Accept", "application/json");
        // Attach authorization if we have a token.
        if (this.authToken != null) {
            retVal.addHeader("Authorization", this.authToken);
        }
        return retVal;
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
        String url = this.fixNcbiUrl(String.format(NCBI_TAX_URL, taxId));
        Request ncbiRequest = Request.Get(url);
        ncbiRequest.addHeader("Accept", "text/xml");
        ncbiRequest.connectTimeout(this.getTimeout());
        // We will now try to send the request.
        int tries = 0;
        Document taxonDoc = null;
        while (tries < MAX_TRIES && taxonDoc == null) {
            // Verify we are not asking NCBI too often.
            this.paceNcbiQuery();
            // Now make the request.
            try {
                Response resp = ncbiRequest.execute();
                tries++;
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
                    log.info("HTTP error during request for taxonomy {} in {}.", taxId, genome);
                    log.info("Error URL is {}.", url);
                    throw new RuntimeException("Taxonomy request failed with error " + statusCode + " " +
                            rawResponse.getStatusLine().getReasonPhrase());
                }
            } catch (IOException e) {
                // This is usually a timeout error.
                if (tries >= MAX_TRIES)
                    throw new RuntimeException("HTTP error in genome ID request: " + e.toString());
                else {
                    tries++;
                    log.debug("Retrying genome ID request after " + e.toString());
                }
            } catch (Exception e) {
                throw new RuntimeException("Error parsing XML for taxonomy query: " + e.toString());
            }
        }
        // Now we have the taxonomy information.
        if (taxonDoc == null) {
            log.warn("No taxonomy information found for {}.", taxId);
        } else {
            // Get the genetic code.
            Node gcNode = taxonDoc.getElementsByTagName("GCId").item(0);
            int gc = 11;
            if (gcNode == null)
                log.warn("Genetic code information missing for {}.", taxId);
            else
                gc = Integer.valueOf(gcNode.getTextContent());
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

    /**
     * Put the ID and name of every public, prokaryotic genomes in PATRIC into the specified collection.
     *
     * @param genomes	collection to contain the genome list.
     */
    public void addAllProkaryotes(Collection<JsonObject> genomes) {
        genomes.addAll(this.getRecords(Table.GENOME, "superkingdom", DOMAINS, "genome_id,genome_name", Criterion.EQ("public", "1"),
                Criterion.IN("genome_status", "Complete", "WGS")));
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

}
