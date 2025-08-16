package org.theseed.p3api;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.counters.CountMap;

import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class CursorTest {

    /** tables in the test file */
    private static Set<String> TABLE_SET = Set.of("genome", "genome_amr", "feature", "taxon", "contig", "sequence",
            "subsystem_item", "family", "subsystem", "special_gene");
    /** logging facility */
    private static Logger log = LoggerFactory.getLogger(CursorTest.class);

    @Test
    public void testDataMap() throws IOException, JsonException {
        File testFile = new File("data", "patric.json");
        BvbrcDataMap dataMap = BvbrcDataMap.load(testFile);
        assertThat(dataMap, is(notNullValue()));
        // Get the data on the various tables.
        assertThat(dataMap.size(), equalTo(TABLE_SET.size()));
        for (String tableName : TABLE_SET) {
            BvbrcDataMap.Table table = dataMap.getTable(tableName);
            assertThat("Missing table " + tableName, table, notNullValue());
            String key = table.getInternalKeyField();
            assertThat("No key for table " + tableName, key, not(emptyString()));
            String sort = table.getInternalSortField();
            assertThat("No sort for table " + tableName, sort, not(emptyString()));
            String fieldName = table.getInternalFieldName("frog");
            assertThat("No fields for table " + tableName, fieldName, equalTo("frog"));
        }
        // Verify the feature table.
        BvbrcDataMap.Table featureTable = dataMap.getTable("feature");
        assertThat(featureTable.getInternalName(), equalTo("genome_feature"));
        assertThat(featureTable.getInternalKeyField(), equalTo("patric_id"));
        assertThat(featureTable.getInternalSortField(), equalTo("feature_id"));
        assertThat(featureTable.getInternalFieldName("feature_id"), equalTo("patric_id"));
        assertThat(featureTable.getInternalFieldName("annotation_source"), equalTo("annotation"));
        assertThat(featureTable.getInternalFieldName("annotation"), equalTo("product"));
        assertThat(featureTable.getInternalFieldName("gene_name"), equalTo("gene"));
        // Now we use the map to translate some criteria.
        SolrFilter[] filters = {
            SolrFilter.EQ("feature_id", "12345"),
            SolrFilter.NE("annotation_source", "manual"),
            SolrFilter.GE("feature_length", 1000),
            SolrFilter.LT("feature_length", 5000),
            SolrFilter.GE("e_score", 1e-6),
            SolrFilter.NE("gc_content", 50.6),
            SolrFilter.GT("feature_length", 5000),
            SolrFilter.LE("feature_length", 10000),
            SolrFilter.EQ("annotation", "Phenylalanyl-tRNA synthetase")
        };
        String[] queries = SolrFilter.toStrings(dataMap, "feature", Arrays.asList(filters));
        assertThat(queries[0], equalTo("patric_id:12345"));
        assertThat(queries[1], equalTo("-annotation:manual"));
        assertThat(queries[2], equalTo("feature_length:[1000 TO *}"));
        assertThat(queries[3], equalTo("feature_length:{* TO 5000}"));
        assertThat(queries[4], equalTo("e_score:[1.0E-6 TO *}"));
        assertThat(queries[5], equalTo("-gc_content:50.6"));
        assertThat(queries[6], equalTo("feature_length:{5000 TO *}"));
        assertThat(queries[7], equalTo("feature_length:{* TO 10000]"));
        assertThat(queries[8], equalTo("product:\"Phenylalanyl\\-tRNA synthetase\""));
    }

    @Test
    public void testConnection() throws IOException, JsonException {
        File testFile = new File("data", "patric.json");
        BvbrcDataMap dataMap = BvbrcDataMap.load(testFile);
        assertThat(dataMap, is(notNullValue()));
        CursorConnection p3 = new CursorConnection(dataMap);
        assertThat(p3, is(notNullValue()));
        // We set a small chunk size to test chunking.
        p3.setChunkSize(95);
        List<JsonObject> results = p3.getRecords("genome", 500, "genome_id,genome_name,genome_length,completeness,contamination", 
                SolrFilter.EQ("genome_name", "Mycobacterium*"), SolrFilter.GE("genome_length", 4411000));
        assertThat(results.size(), equalTo(500));
        // Verify the contents of the results.
        JsonObject saved = null;
        for (JsonObject record : results) {
            assertThat(KeyBuffer.getString(record, "genome_name"), startsWith("Mycobacterium"));
            assertThat(KeyBuffer.getInt(record, "genome_length"), greaterThanOrEqualTo(4411000));
            assertThat(record.get("checkm_contamination"), nullValue());
            assertThat(record.get("checkm_completeness"), nullValue());
            // Save a particular genome ID for the field-mapping test.
            if (KeyBuffer.getString(record, "genome_id").contentEquals("1001714.6"))
                saved = record;
        }
        assertThat(saved, not(nullValue()));
        assertThat(KeyBuffer.getDouble(saved, "completeness"), closeTo(100.0, 0.1));
        assertThat(KeyBuffer.getDouble(saved, "contamination"), closeTo(5.1, 0.1));
        // Try a floating-point filter and an NE filter.
        results = p3.getRecords("genome", 500, "genome_id,genome_name,genome_length,completeness,contamination",
                SolrFilter.EQ("genome_name", "Mycobacterium*"), SolrFilter.GT("contamination", 0.0),
                SolrFilter.NE("collection_year", 2002));
        assertThat(results.size(), equalTo(500));
        for (JsonObject record : results) {
            assertThat(KeyBuffer.getString(record, "genome_name"), containsString("Mycobacterium"));
            assertThat(KeyBuffer.getDouble(record, "contamination"), greaterThan(0.0));
            assertThat(KeyBuffer.getInt(record, "collection_year"), not(equalTo(2002)));
        }
        // Use string NE to test an empty return set.
        results = p3.getRecords("genome", 500, "genome_id,genome_name,genome_length,completeness,contamination",
                SolrFilter.NE("genome_name", "Mycobacterium"), SolrFilter.EQ("genus", "Mycobacterium"));
        assertThat(results.size(), equalTo(0));
        // Test NE on uncultured Mycobacterium. This will also be a short query.
        results = p3.getRecords("genome", 5000, "genome_id,genome_name,genome_length,genus",
                SolrFilter.EQ("genome_name", "uncultured"), SolrFilter.EQ("genus", "Mycobacterium"),
                SolrFilter.NE("strain", "binchicken*"), SolrFilter.LT("genome_length", 4000000));
        assertThat(results.size(), lessThan(5000)); // May fail due to database changes.
        for (JsonObject record : results) {
            assertThat(KeyBuffer.getString(record, "genome_name"), containsString("uncultured"));
            assertThat(KeyBuffer.getString(record, "genus"), equalTo("Mycobacterium"));
            assertThat(KeyBuffer.getString(record, "strain"), not(containsString("binchicken")));
            assertThat(KeyBuffer.getInt(record, "genome_length"), lessThan(4000000));
        }
        // Test LE on fine consistency.
        results = p3.getRecords("genome", 500, "genome_id,genome_name,genome_length,genus,fine_consistency",
                SolrFilter.EQ("genus", "Mycobacterium"),
                SolrFilter.LT("fine_consistency", 60.5));
        assertThat(results.size(), lessThanOrEqualTo(500));
        for (JsonObject record : results) {
            assertThat(KeyBuffer.getString(record, "genus"), equalTo("Mycobacterium"));
            assertThat(KeyBuffer.getDouble(record, "fine_consistency"), lessThan(60.5));
        }
        // Test the IN filter.
        results = p3.getRecords("genome", 500, "genome_id,genome_name,genome_length,genus,taxon_id",
                SolrFilter.IN("taxon_id", "1764", "2883505", "2903536"), SolrFilter.EQ("genus", "Mycobacterium"));
        assertThat(results.size(), lessThanOrEqualTo(500));
        for (JsonObject record : results) {
            assertThat(KeyBuffer.getString(record, "genus"), equalTo("Mycobacterium"));
            assertThat(KeyBuffer.getInt(record, "taxon_id"), anyOf(equalTo(1764), equalTo(2883505), equalTo(2903536)));
        }
        // Test LE on fine consistency.
        results = p3.getRecords("genome", 500, "genome_id,genome_name,genome_length,genus,fine_consistency",
                SolrFilter.EQ("genus", "Mycobacterium"),
                SolrFilter.LT("fine_consistency", 60.5));
        assertThat(results.size(), lessThanOrEqualTo(500));
        for (JsonObject record : results) {
            assertThat(KeyBuffer.getString(record, "genus"), equalTo("Mycobacterium"));
            assertThat(KeyBuffer.getDouble(record, "fine_consistency"), lessThan(60.5));
        }
        // Verify that one chunk works.
        p3.setChunkSize(25000);
        results = p3.getRecords("genome", 50000, "genome_id,genome_name,genome_length,genus,taxon_id",
                SolrFilter.IN("taxon_id", "1764", "2883505", "2903536"), SolrFilter.EQ("genus", "Mycobacterium"));
        assertThat(results.size(), lessThan(25000)); // May fail due to database changes.
        for (JsonObject record : results) {
            assertThat(KeyBuffer.getString(record, "genus"), equalTo("Mycobacterium"));
            assertThat(KeyBuffer.getInt(record, "taxon_id"), anyOf(equalTo(1764), equalTo(2883505), equalTo(2903536)));
        }
    }

    @Test
    public void testBatchQueries() throws IOException, JsonException {
         File testFile = new File("data", "patric.json");
        BvbrcDataMap dataMap = BvbrcDataMap.load(testFile);
        assertThat(dataMap, is(notNullValue()));
        CursorConnection p3 = new CursorConnection(dataMap);
        assertThat(p3, is(notNullValue()));
        // First, we will do a query that does not hit the limit. To do this, we ask for 2000 genomes with 5 or fewer contigs,
        // and then get and count the contigs.
        List<JsonObject> genomeList = p3.getRecords("genome", 2000, "genome_id,contigs",
                SolrFilter.LT("contigs", 5));
        assertThat(genomeList.size(), lessThanOrEqualTo(2000));
        Map<String, Integer> contigCounts = new HashMap<String, Integer>();
        for (JsonObject genome : genomeList) {
            String genomeId = KeyBuffer.getString(genome, "genome_id");
            int contigCount = KeyBuffer.getInt(genome, "contigs");
            assertThat(contigCount, lessThanOrEqualTo(5));
            contigCounts.put(genomeId, contigCount);
        }
        List<JsonObject> results = p3.getRecords("contig", 20000, 95, "genome_id", contigCounts.keySet(), "genome_id,length");
        assertThat(results.size(), lessThan(20000));
        // We will count contigs in here.
        CountMap<String> contigVerify = new CountMap<String>();
        for (JsonObject record : results) {
            String genomeId = KeyBuffer.getString(record, "genome_id");
            contigVerify.count(genomeId);
            assertThat(genomeId, in(contigCounts.keySet()));
        }
        for (String genomeId : contigCounts.keySet()) {
            int expected = contigCounts.get(genomeId);
            int found = contigVerify.getCount(genomeId);
            assertThat("Contig count for " + genomeId + " incorrect.", found, equalTo(expected));
        }
        // Get a bunch of genome IDs for Mycobacteria with between 3000 and 4000 CDS features.
        genomeList = p3.getRecords("genome", 5000, "genome_id,genus,patric_cds",
                SolrFilter.EQ("genus", "Mycobacterium"),
                SolrFilter.GE("patric_cds", 3000),
                SolrFilter.LT("patric_cds", 5000));
        assertThat(genomeList.size(), lessThanOrEqualTo(5000));
        Set<String> genomeIds = new HashSet<String>();
        for (JsonObject record : genomeList) {
            assertThat(KeyBuffer.getString(record, "genus"), equalTo("Mycobacterium"));
            assertThat(KeyBuffer.getInt(record, "patric_cds"), allOf(greaterThanOrEqualTo(3000), lessThan(5000)));
            genomeIds.add(KeyBuffer.getString(record, "genome_id"));
        }
        assertThat(genomeIds.size(), greaterThan(100));
        // We will do this with five or more whole genomes per chunk (25000 features) and a maximum of 1 million features (200+ genomes). To insure there
        // are multiple batches, we put the batch size at 75.
        p3.setChunkSize(25000);
        results = p3.getRecords("feature", 1000000, 75, "genome_id", genomeIds,
                "patric_id,feature_type,genome_id,product", SolrFilter.EQ("feature_type", "CDS"),
                SolrFilter.EQ("annotation_source", "PATRIC"));
        assertThat(results.size(), lessThanOrEqualTo(1000000));
        int count = 0;
        for (JsonObject record : results) {
            assertThat(KeyBuffer.getString(record, "feature_type"), equalTo("CDS"));
            assertThat(genomeIds, hasItem(KeyBuffer.getString(record, "genome_id")));
            assertThat(KeyBuffer.getString(record, "patric_id"), startsWith("fig|"));
            count++;
            if (count % 5000 == 0)
                log.info("Processed {} of {} features.", count, results.size());
        }
    }
}