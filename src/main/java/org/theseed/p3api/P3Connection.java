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

import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Genome;
import org.theseed.genome.TaxItem;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.github.cliftonlabs.json_simple.JsonObject;



/**
 * This object represents a connection to PATRIC and provides methods for retrieving genome and feature data.
 * The default API is at https://patric.theseed.org/services/data_api, but this can be overridden using the
 * P3API_URL environment variable.
 *
 * @author Bruce Parrello
 *
 */
public class P3Connection extends SolrConnection {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(Connection.class);
    /** security token */
    private String authToken;
    /** default URL */
    protected static final String DATA_API_URL = "https://www.bv-brc.org/api/";
    /** taxonomy URL format */
    protected static final String NCBI_TAX_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=taxonomy&id=%d";
    /** list of domains for prokaryotes */
    public static final List<String> DOMAINS = Arrays.asList("Bacteria", "Archaea");
    /**
     * description of the major SOLR tables
     */
    public enum Table {

        GENOME("genome", "genome_id"),
        GENOME_AMR("genome_amr", "id"),
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
     * Initialize a connection for the currently-logged-in user and the default URL.
     */
    public P3Connection() {
        super();
        String token = getLoginToken();
        String apiUrl = getApiUrl();
        this.setup(token, apiUrl);
    }

    /**
     * Initialize a connection with a specific API URL.
     *
     * @param apiUrl	URL to use to access the data API
     *
     */
    public P3Connection(String apiUrl) {
        super();
        String token = getLoginToken();
        this.setup(token, apiUrl);
    }

    /**
     * Get the default URL for the PATRIC data service.
     */
    public static String getApiUrl() {
        // Try to get the URL from the environment.
        String retVal = System.getenv("P3API_URL");
        // No luck, use the default.
        if (StringUtils.isBlank(retVal))
            retVal = DATA_API_URL;
        log.info("P3 URL is {}.", retVal);
        return retVal;
    }

    /**
     * @return the default login token for this system
     */
    protected static String getLoginToken() {
        File tokenFile = new File(System.getProperty("user.home"), ".patric_token");
        String retVal = readToken(tokenFile);
        return retVal;
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
     * Request the specified fields in the specified records of the specified table.
     *
     * @param table		the table containing the records
     * @param fields	a comma-delimited list of the fields desired
     * @param criteria	(repeating) the criteria to use for filtering the records
     *
     * @return a JsonArray of the desired results
     */
    public List<JsonObject> query(Table table, String fields, String... criteria) {
    	this.clearBuffer();
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
            retVal.put(KeyBuffer.getString(record, keyName), record);
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
     * Create a request to get data from the specified table.
     *
     * @param table		name of the target table
     *
     * @return a request for the specified table
     */
    @Override
    protected Request createRequest(String table) {
        Request retVal = Request.Post(this.getUrl() + table);
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
            // Store the genetic code.
            genome.setGeneticCode(gc);
            // Everything is under the "Taxon" node.  We need to save all the
            // children of LineageEx, plus the ScientificName and Rank
            // at the top.
            String leafName = String.format("Unknown %s", genome.getDomain());
            String leafRank = "no rank";
            Node rootTaxon = taxonDoc.getElementsByTagName("Taxon").item(0);
            if (rootTaxon == null)
                log.warn("Invalid taxon tree returned for {}.", taxId);
            else {
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
                // Store the array.
                genome.setLineage(lineage);
            }
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

}
