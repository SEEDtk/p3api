package org.theseed.p3api;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class CursorTest {

    /** tables in the test file */
    private static Set<String> TABLE_SET = Set.of("genome", "genome_amr", "feature", "taxon", "contig", "sequence",
            "subsystem_item", "family", "subsystem", "special_gene");

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
        String[] queries = SolrFilter.toStrings(dataMap, "feature", filters);
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
            if (KeyBuffer.getString(record, "genome_id").contentEquals("1008298.3"))
                saved = record;
        }
        assertThat(saved, not(nullValue()));
        assertThat(KeyBuffer.getDouble(saved, "completeness"), closeTo(87.8, 0.1));
        assertThat(KeyBuffer.getDouble(saved, "contamination"), closeTo(9.8, 0.1));
    }
}

