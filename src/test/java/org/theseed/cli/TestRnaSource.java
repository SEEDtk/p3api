/**
 *
 */
package org.theseed.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.junit.Test;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * @author Bruce Parrello
 *
 */
public class TestRnaSource {

    @Test
    public void testRnaSources() {
        JsonObject parms = new JsonObject();
        RnaSource source1 = new RnaSource.SRA("SRR123456");
        source1.store(parms);
        JsonArray sourceParm = (JsonArray) parms.get("srr_libs");
        JsonObject sourceStructure = (JsonObject) sourceParm.get(0);
        assertThat(sourceStructure.get("srr_accession"), equalTo("SRR123456"));
        assertThat(sourceParm.size(), equalTo(1));
        RnaSource source2 = new RnaSource.Paired("file1", "file2");
        source2.store(parms);
        sourceParm = (JsonArray) parms.get("paired_end_libs");
        JsonObject libMap = (JsonObject) sourceParm.get(0);
        assertThat(sourceParm.size(), equalTo(1));
        assertThat(libMap.get("read1"), equalTo("file1"));
        assertThat(libMap.get("read2"), equalTo("file2"));
    }

}
