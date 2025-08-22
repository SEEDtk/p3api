package org.theseed.p3api;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Contig;
import org.theseed.genome.Feature;
import org.theseed.genome.Genome;
import org.theseed.genome.GoTerm;


public class BatchTest {

    /** logging facility */
    private static final Logger log = LoggerFactory.getLogger(BatchTest.class);
    /** list of genomes to test */
    private static final List<String> GENOME_IDS = List.of("1001582.15", "1001582.3", "1003239.3", "1003239.4", "1003239.5",
            "1007654.3", "1027627.3", "1030092.3", "1033734.10", "1033734.11",
            "1033734.12", "1033734.13", "1033734.3", "1033734.5", "1033734.6",
            "1034836.4", "1042873.3", "1042874.3", "1042875.3", "1049581.4");

    @Test
    public void testBatchProcessing() throws IOException {
        // Get our connections.
        P3CursorConnection p3 = new P3CursorConnection();
        long startTime ;
        long endTime;
        long batchTime;
        long singleTime;
        // We test all the detail levels.
        for (P3Genome.Details level : P3Genome.Details.values()) {
            log.info("Testing batch processing with detail level {}.", level);
            // Load the genomes in a batch.
            log.info("Starting batch processing test with {} genomes.", GENOME_IDS.size());
            startTime = System.currentTimeMillis();
            P3GenomeBatch batch = new P3GenomeBatch(p3, GENOME_IDS, level);
            endTime = System.currentTimeMillis();
            batchTime = endTime - startTime;
            log.info("Batch processing test completed in {} ms and found {} genomes.", batchTime, batch.size());
            // Load the genomes using the old interface.
            log.info("Testing single processing with detail level {}.", level);
            startTime = System.currentTimeMillis();
            Map<String, Genome> genomeMap = new HashMap<>(GENOME_IDS.size() * 4 / 3 + 1);
            for (String genomeId : GENOME_IDS) {
                Genome genome = P3Genome.load(p3, genomeId, level);
                genomeMap.put(genomeId, genome);
            }
            endTime = System.currentTimeMillis();
            singleTime = endTime - startTime;
            log.info("Single processing test completed in {} ms and found {} genomes.", singleTime, genomeMap.size());
            assertThat(batch.size(), equalTo(genomeMap.size()));
            log.info("Batch processing was {}% faster than single processing.", 
                    (singleTime == 0 ? 100 : ((singleTime - batchTime) * 100 / singleTime)));
            // Compare the genomes.
            for (String genomeId : GENOME_IDS) {
                Genome batchGenome = batch.get(genomeId);
                Genome singleGenome = genomeMap.get(genomeId);
                verifyGenomes(singleGenome, batchGenome, level);
            }
        }
    }

    /**
     * Verify two genomes are identical.
     *
     * @param gto	    first genome
     * @param gto2	    second genome
     * @param level	    detail level
     */
    public static void verifyGenomes(Genome gto, Genome gto2, P3Genome.Details level) {
        log.info("Comparing genome {} at level {}.", gto, level);
        assertThat(gto2.getId(), equalTo(gto.getId()));
        assertThat(gto2.getName(), equalTo(gto.getName()));
        assertThat(gto2.getDomain(), equalTo(gto.getDomain()));
        assertThat(ArrayUtils.toObject(gto2.getLineage()), arrayContaining(ArrayUtils.toObject(gto.getLineage())));
        assertThat(gto2.getGeneticCode(), equalTo(gto.getGeneticCode()));
        assertThat(gto2.getTaxonomyId(), equalTo(gto.getTaxonomyId()));
        if (level.includesFeatures())
            assertThat(gto2.getFeatureCount(), equalTo(gto.getFeatureCount()));
        assertThat(gto2.getContigCount(), equalTo(gto.getContigCount()));
        // Note that the SSU rRNA sequences are non-deterministic. We can only guarantee identical length.
        assertThat(gto2.getSsuRRna().length(), equalTo(gto.getSsuRRna().length()));
        if (level.includesFeatures()) {
            Collection<Feature> fids = gto.getFeatures();
            for (Feature fid : fids) {
                Feature diskFid = gto2.getFeature(fid.getId());
                assertThat(diskFid.getFunction(), equalTo(fid.getFunction()));
                assertThat(diskFid.getLocation(), equalTo(fid.getLocation()));
                assertThat(diskFid.getPlfam(), equalTo(fid.getPlfam()));
                assertThat(diskFid.getType(), equalTo(fid.getType()));
                if (level.includesProteins())
                    assertThat(diskFid.getProteinTranslation(), equalTo(fid.getProteinTranslation()));
                Collection<GoTerm> fidGoTerms = fid.getGoTerms();
                assertThat(diskFid.getGoTerms().size(), equalTo(fidGoTerms.size()));
                for (GoTerm diskGoTerm : diskFid.getGoTerms()) {
                    assertThat(fidGoTerms, hasItem(diskGoTerm));
                }
                Collection<String> fidAliases = fid.getAliases();
                assertThat(diskFid.getAliases().size(), equalTo(fidAliases.size()));
                for (String diskAlias : diskFid.getAliases()) {
                    assertThat(fidAliases, hasItem(diskAlias));
                }
            }
        }
        Collection<Contig> contigs = gto.getContigs();
        for (Contig contig : contigs) {
            Contig diskContig = gto2.getContig(contig.getId());
            assertThat(diskContig.length(), equalTo(contig.length()));
            if (level.includesContigs())
                assertThat(diskContig.getSequence(), equalTo(contig.getSequence()));
        }
    }

}
