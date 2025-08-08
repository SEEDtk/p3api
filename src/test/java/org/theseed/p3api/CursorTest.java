package org.theseed.p3api;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.github.cliftonlabs.json_simple.JsonException;

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
    }

}

