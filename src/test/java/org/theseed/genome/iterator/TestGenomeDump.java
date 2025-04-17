/**
 *
 */
package org.theseed.genome.iterator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.theseed.basic.ParseFailureException;
import org.theseed.genome.Contig;
import org.theseed.genome.Feature;
import org.theseed.genome.Genome;

/**
 * @author Bruce Parrello
 *
 */
class TestGenomeDump {

    @Test
    void testDumpRoundTrip() throws IOException, ParseFailureException {
        Genome testGenome = new Genome(new File("data", "1311.5368.gto"));
        GenomeSource dumpSource = GenomeSource.Type.DUMP.create(new File("data", "dump_test"));
        assertThat(dumpSource.size(), equalTo(1));
        assertThat(dumpSource.getIDs(), contains("1311.5368"));
        Genome dumpGto = dumpSource.getGenome("1311.5368");
        assertThat(dumpGto.getId(), equalTo(testGenome.getId()));
        assertThat(dumpGto.getHome(), equalTo("BVBRC"));
        assertThat(dumpGto.getName(), equalTo(testGenome.getName()));
        assertThat(dumpGto.getContigCount(), equalTo(testGenome.getContigCount()));
        assertThat(dumpGto.getFeatureCount(), equalTo(testGenome.getFeatureCount()));
        assertThat(dumpGto.getTaxString(), equalTo(testGenome.getTaxString()));
        assertThat(dumpGto.getLineage(), equalTo(testGenome.getLineage()));
        Collection<Contig> dumpContigs = dumpGto.getContigs();
        for (Contig dumpContig : dumpContigs) {
            Contig testContig = testGenome.getContig(dumpContig.getId());
            assertThat(dumpContig.getId(), dumpContig, equalTo(testContig));
        }
        Collection<Feature> dumpFeats = dumpGto.getFeatures();
        for (Feature dumpFeat : dumpFeats) {
            Feature testFeat = testGenome.getFeature(dumpFeat.getId());
            assertThat(dumpFeat.getId(), dumpFeat.same(testFeat), equalTo(true));
        }
    }

}
