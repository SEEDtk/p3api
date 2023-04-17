/**
 *
 */
package org.theseed.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.junit.jupiter.api.Test;

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
        RnaSource source2 = new RnaSource.Paired("file1.fq", "file2.fastq");
        source2.store(parms);
        sourceParm = (JsonArray) parms.get("paired_end_libs");
        JsonObject libMap = (JsonObject) sourceParm.get(0);
        assertThat(sourceParm.size(), equalTo(1));
        assertThat(libMap.get("read1"), equalTo("file1.fq"));
        assertThat(libMap.get("read2"), equalTo("file2.fastq"));
        assertThat(libMap.get("sample_id"), equalTo("file1"));
        source2 = new RnaSource.Paired(
                "/BVBRC@patricbrc.org/BVBRC Transcriptomics/Bacteria/Mycobacterium/Mycobacterium tuberculosis/83332.12/Jobs/.SRR23306196_fq/SRR23306196_1_ptrim.fq.gz",
                "/BVBRC@patricbrc.org/BVBRC Transcriptomics/Bacteria/Mycobacterium/Mycobacterium tuberculosis/83332.12/Jobs/.SRR23306196_fq/SRR23306196_2_ptrim.fq.gz");
        source2.store(parms);
        sourceParm = (JsonArray) parms.get("paired_end_libs");
        libMap = (JsonObject) sourceParm.get(0);
        assertThat(libMap.get("read1"), equalTo("/BVBRC@patricbrc.org/BVBRC Transcriptomics/Bacteria/Mycobacterium/Mycobacterium tuberculosis/83332.12/Jobs/.SRR23306196_fq/SRR23306196_1_ptrim.fq.gz"));
        assertThat(libMap.get("read2"), equalTo("/BVBRC@patricbrc.org/BVBRC Transcriptomics/Bacteria/Mycobacterium/Mycobacterium tuberculosis/83332.12/Jobs/.SRR23306196_fq/SRR23306196_2_ptrim.fq.gz"));
        assertThat(libMap.get("sample_id"), equalTo("SRR23306196"));
        source2 = new RnaSource.Paired(
                "/BVBRC@patricbrc.org/BVBRC Transcriptomics/Bacteria/Mycobacterium/Mycobacterium tuberculosis/83332.12/Jobs/.SRR23306196_fq/WRR23306196_strim.fq.gz",
                null);
        source2.store(parms);
        sourceParm = (JsonArray) parms.get("single_end_libs");
        libMap = (JsonObject) sourceParm.get(0);
        assertThat(libMap.get("read"), equalTo("/BVBRC@patricbrc.org/BVBRC Transcriptomics/Bacteria/Mycobacterium/Mycobacterium tuberculosis/83332.12/Jobs/.SRR23306196_fq/WRR23306196_strim.fq.gz"));
        assertThat(libMap.get("sample_id"), equalTo("WRR23306196"));
    }

}
