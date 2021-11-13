/**
 *
 */
package org.theseed.p3api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.theseed.io.TabbedLineReader;
import org.theseed.proteins.kmers.reps.P3RepGenomeDb;
import org.theseed.proteins.kmers.reps.RepGenomeDb;

/**
 * @author Bruce Parrello
 *
 */
public class TestP3Reps {

    @Test
    public void test() throws IOException {
        File testFile = new File("data", "p3kmers.tbl");
        List<String> genomes = TabbedLineReader.readColumn(testFile, "genome_id");
        RepGenomeDb repdb = RepGenomeDb.load(new File("data", "rep200.db"));
        P3Connection p3 = new P3Connection();
        Map<String, RepGenomeDb.Representation> results = P3RepGenomeDb.getReps(p3, genomes, repdb);
        assertThat(results.size(), greaterThan(0));
        try (TabbedLineReader inStream = new TabbedLineReader(testFile)) {
            for (TabbedLineReader.Line line : inStream) {
                String genome = line.get(0);
                RepGenomeDb.Representation rep = results.get(genome);
                assertThat(genome, rep, not(nullValue()));
                assertThat(genome, rep.getGenomeId(), equalTo(line.get(2)));
                assertThat(genome, rep.getSimilarity(), equalTo(line.getInt(3)));
            }
        }
    }

}
