package org.theseed.proteins.kmers.reps;

import java.io.File;
import java.io.IOException;

import org.theseed.genome.Feature;
import org.theseed.genome.Genome;
import org.theseed.sequence.FastaInputStream;
import org.theseed.sequence.ProteinKmers;
import org.theseed.sequence.Sequence;
import org.junit.jupiter.api.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;
import static org.theseed.test.Matchers.*;


/**
 * Unit test for simple App.
 */
public class KmerRepTest

{

    /**
     * Test the bug with the odd X-heavy protein not having a repeatable similarity.
     * @throws IOException
     */
    @Test
    public void testXProtein() throws IOException {
        RepGenomeDb testdb = new RepGenomeDb(200);
        File inFile = new File("data", "xsmall.fa");
        FastaInputStream inStream = new FastaInputStream(inFile);
        Sequence testSeq = inStream.next();
        RepGenome testGenome = new RepGenome(testSeq);
        testdb.checkGenome(testGenome);
        RepGenomeDb.Representation result = testdb.findClosest(testSeq);
        assertThat(result.getSimilarity() > 200, isTrue());
        double distance = testGenome.distance(testGenome);
        assertThat(distance, equalTo(0.0));
        inStream.close();
    }



    /**
     * Test RepGenome object
     */
    @Test
    public void testRepGenome() {
        String prot1 = "MSHLAELVASAKAAISQASDVAALDNVRVEYLGKKGHLTLQMTTLRELPPEERPAAGAVI" +
                "NEAKEQVQQALNARKAELESAALNARLAAETIDVSLPGRRIENGGLHPVTRTIDRIESFF" +
                "GELGFTVATGPEIEDDYHNFDALNIPGHHPARADHDTFWFDATRLLRTQTSGVQIRTMKA" +
                "QQPPIRIIAPGRVYRNDYDQTHTPMFHQMEGLIVDTNISFTNLKGTLHDFLRNFFEEDLQ" +
                "IRFRPSYFPFTEPSAEVDVMGKNGKWLEVLGCGMVHPNVLRNVGIDPEVYSGFAFGMGME" +
                "RLTMLRYGVTDLRSFFENDLRFLKQFK";
        RepGenome rep1 = new RepGenome("fig|1005530.3.peg.2208", "Escherichia coli EC4402", prot1);
        assertThat(rep1.getGenomeId(), equalTo("1005530.3"));
        assertThat(rep1.getFid(), equalTo("fig|1005530.3.peg.2208"));
        assertThat(rep1.getName(), equalTo("Escherichia coli EC4402"));
        assertThat(rep1.getProtein(), equalTo(prot1));
        RepGenome rep2 = new RepGenome("fig|1005530.4.peg.2208", "Escherichia coli EC4402 B", prot1);
        assertThat(rep1.equals(rep2), isFalse());
        assertThat(rep2.getGenomeId(), equalTo("1005530.4"));
        RepGenome rep3;
        try {
            rep3 = new RepGenome("fig|12345.peg.4", "Invalid genome ID", "");
            fail("Invalid genome ID parsed correctly.");
        } catch (IllegalArgumentException e) {
            // Here the correct exception was thrown.
        }
        rep3 = new RepGenome("fig|1129793.4.peg.2957", "Glaciecola polaris LMG 21857", "MSHLAELVASAKAAISQASDVAALDNVRVEYLGKKGHLTLQMTTLRELPPEERPAAGAVI");
        int sim = rep3.similarity(rep1);
        int sim2 = ((ProteinKmers) rep3).similarity(rep1);
        assertThat(sim, equalTo(sim2));
        // Test genome ordering.
        RepGenome rep4 = new RepGenome("fig|1129793.30.peg.2957", "Test genome 1", "");
        RepGenome rep5 = new RepGenome("fig|129793.30.peg.2957", "Test genome 2", "");
        assertThat(rep1.compareTo(rep2) < 0, isTrue());
        assertThat(rep1.compareTo(rep3) < 0, isTrue());
        assertThat(rep3.compareTo(rep4) < 0, isTrue());
        assertThat(rep4.compareTo(rep5) < 0, isTrue());
        assertThat(rep2.compareTo(rep1) > 0, isTrue());
        assertThat(rep3.compareTo(rep1) > 0, isTrue());
        assertThat(rep4.compareTo(rep3) > 0, isTrue());
        assertThat(rep5.compareTo(rep4) > 0, isTrue());
        rep3 = new RepGenome("fig|1005530.3.peg.2957", "Glaciecola polaris LMG 21857", "MSHLAELVASAKAAISQASDVAALDNVRVEYLGKKGHLTLQMTTLRELPPEERPAAGAVI");
        assertThat(rep1.compareTo(rep3), equalTo(0));
    }

    /**
     * Test RepGenome object
     */
    @Test
    public void testRepSequence() {
        String prot1 = "MSHLAELVASAKAAISQASDVAALDNVRVEYLGKKGHLTLQMTTLRELPPEERPAAGAVI" +
                "NEAKEQVQQALNARKAELESAALNARLAAETIDVSLPGRRIENGGLHPVTRTIDRIESFF" +
                "GELGFTVATGPEIEDDYHNFDALNIPGHHPARADHDTFWFDATRLLRTQTSGVQIRTMKA" +
                "QQPPIRIIAPGRVYRNDYDQTHTPMFHQMEGLIVDTNISFTNLKGTLHDFLRNFFEEDLQ" +
                "IRFRPSYFPFTEPSAEVDVMGKNGKWLEVLGCGMVHPNVLRNVGIDPEVYSGFAFGMGME" +
                "RLTMLRYGVTDLRSFFENDLRFLKQFK";
        Sequence seq1 = new Sequence("fig|1005530.3.peg.2208", "", prot1);
        Sequence seq2 = new Sequence("a sequence", "", prot1);
        RepSequence rep1 = new RepSequence(seq1);
        RepSequence rep2 = new RepSequence(seq2);
        assertThat(rep1.getId(), equalTo("fig|1005530.3.peg.2208"));
        assertThat(rep2.getId(), equalTo("a sequence"));
        assertThat(rep1.getProtein(), equalTo(prot1));
        assertThat(seq1.equals(seq2), isFalse());
        Sequence seq3 = new Sequence("fig|1005530.3.peg.2208", "Glaciecola polaris LMG 21857", "MSHLAELVASAKAAISQASDVAALDNVRVEYLGKKGHLTLQMTTLRELPPEERPAAGAVI");
        RepSequence rep3 = new RepSequence(seq3);
        int sim = rep3.similarity(rep1);
        int sim2 = ((ProteinKmers) rep3).similarity(rep1);
        assertThat(sim, equalTo(sim2));
        // Test sequence ordering.
        assertThat("Rep1 not greater than rep2.", rep1.compareTo(rep2), greaterThan(0));
        assertThat("Rep2 not less than rep1.", rep2.compareTo(rep1), lessThan(0));
        assertThat(rep1.compareTo(rep3), equalTo(0));
        assertThat(rep3, equalTo(rep1));
    }

    /**
     * test RepGenomeDb
     *
     * @throws IOException
     */
    @Test
    public void testRepGenomeDb() throws IOException {
        RepGenomeDb repDb = new RepGenomeDb(100);
        assertThat(repDb.getKmerSize(), equalTo(ProteinKmers.kmerSize()));
        assertThat(repDb.getProtName(), equalTo("Phenylalanyl-tRNA synthetase alpha chain"));
        assertThat(repDb.getThreshold(), equalTo(100));
        ProteinKmers.setKmerSize(9);
        repDb = new RepGenomeDb(200, "ATP synthase delta chain");
        assertThat(repDb.getKmerSize(), equalTo(9));
        assertThat(repDb.getProtName(), equalTo("ATP synthase delta chain"));
        assertThat(repDb.getThreshold(), equalTo(200));
        // Reset the kmer size.
        ProteinKmers.setKmerSize(10);
        // Process a fasta stream to create a rep-genome DB.
        File fastaFile = new File("data", "small.fa");
        FastaInputStream fastaStream = new FastaInputStream(fastaFile);
        repDb = new RepGenomeDb(50);
        repDb.addGenomes(fastaStream);
        fastaStream.close();
        // Find the representative of a genome.
        ProteinKmers testSeq = new ProteinKmers(
                "MSHLAELVASAKAAISQASDVAALDNVRVEYLGKKGHLTLQMTTLRELPPEERPAAGAVI" +
                "NEAKEQVQQALNARKAELESAALNARLAAETIDVSLPGRRIENGGLHPVTRTIDRIESFF" +
                "GELGFTVATGPEIEDDYHNFDALNIPGHHPARADHDTFWFDATRLLRTQTSGVQIRTMKA" +
                "QQPPIRIIAPGRVYRNDYDQTHTPMFHQMEGLIVDTNISFTNLKGTLHDFLRNFFEEDLQ" +
                "IRFRPSYFPFTEPSAEVDVMGKNGKWLEVLGCGMVHPNVLRNVGIDPEVYSGFAFGMGME" +
                "RLTMLRYGVTDLRSFFENDLRFLKQFK");
        RepGenomeDb.Representation result = repDb.findClosest(testSeq);
        assertThat(result.getGenomeId(), equalTo("1005530.3"));
        assertThat(result.getSimilarity() >= 200, isTrue());
        // Verify that the distance works.
        ProteinKmers myRep = result.getRepresentative();
        assertThat(result.getDistance(), equalTo(myRep.distance(testSeq)));
        // Now verify that all the sequences are represented.
        fastaStream = new FastaInputStream(fastaFile);
        for (Sequence inSeq : fastaStream) {
            boolean found = repDb.checkSimilarity(inSeq, 50);
            assertThat(found, isTrue());
        }
        fastaStream.close();
        // Now get all the represented genomes and verify that they are far apart.
        RepGenome[] allReps = repDb.all();
        int n = repDb.size();
        assertThat(allReps.length, equalTo(n));
        for (int i = 0; i < n; i++) {
            RepGenome repGenome = allReps[i];
            assertThat(repGenome.getGenomeId(), repDb.get(repGenome.getGenomeId()), equalTo(repGenome));
            for (int j = i + 1; j < n; j++) {
                // Verify the similarity thresholds here.
                int compareij = repGenome.similarity(allReps[j]);
                assertThat("Genomes " + repGenome + " and " + allReps[j] + " are too close.  Score = " + compareij,
                        compareij < repDb.getThreshold(), isTrue());
                // Verify the distance behavior here.
                double distij = repGenome.distance(allReps[j]);
                double distji = allReps[j].distance(repGenome);
                assertThat(distji, equalTo(distij));
                for (int k = j + 1; k < n; k++) {
                    double distjk = allReps[j].distance(allReps[k]);
                    double distik = repGenome.distance(allReps[k]);
                    assertThat(distij + distjk >= distik, isTrue());
                    int compareik = repGenome.similarity(allReps[k]);
                    if (compareik > compareij && distik > distij) {
                        fail("Greater similarity at greater distance.");
                    } else if (compareik < compareij && distik < distij) {
                        fail("Lesser similarity at lesser distance.");
                    }
                }
            }
        }
        // Save this database.
        File saveFile = new File("src/test", "repdb.ser");
        repDb.save(saveFile);
        // Load it back in.
        ProteinKmers.setKmerSize(6);
        RepGenomeDb newDb = RepGenomeDb.load(saveFile);
        assertThat(newDb.getProtName(), equalTo(repDb.getProtName()));
        assertThat(newDb.getKmerSize(), equalTo(repDb.getKmerSize()));
        assertThat(ProteinKmers.kmerSize(), equalTo(newDb.getKmerSize()));
        assertThat(newDb.getThreshold(), equalTo(repDb.getThreshold()));
        assertThat(newDb.size(), equalTo(repDb.size()));
        for (RepGenome oldGenome : allReps) {
            assertThat(newDb.get(oldGenome.getGenomeId()), equalTo(oldGenome));
        }

    }

    /*
     * Test off-brand seed protein support.
     */
    @Test
    public void testRocPSupport() throws IOException {
        RepGenomeDb testDb = new RepGenomeDb(8, "DNA-directed RNA polymerase beta' subunit",
                "DNA-directed RNA polymerase subunit A");
        Genome g1 = new Genome(new File("data", "1036778.3.gto"));
        RepGenome g1Rep = testDb.getSeedProtein(g1);
        assertThat(g1Rep.getFid(), equalTo("fig|1036778.3.peg.5"));
        Feature feat = g1.getFeature("fig|1036778.3.peg.5");
        assertThat(g1Rep.getProtein(), equalTo(feat.getProteinTranslation()));
        assertThat(g1Rep.getName(), equalTo(g1.getName()));
        testDb.addRep(g1Rep);
        g1 = new Genome(new File("data", "103621.4.gto"));
        g1Rep = testDb.getSeedProtein(g1);
        assertThat(g1Rep.getFid(), equalTo("fig|103621.4.peg.1951"));
        feat = g1.getFeature("fig|103621.4.peg.1951");
        assertThat(g1Rep.getProtein(), equalTo(feat.getProteinTranslation()));
        assertThat(g1Rep.getName(), equalTo(g1.getName()));
        testDb.addRep(g1Rep);
        g1 = new Genome(new File("data", "1036677.3.gto"));
        g1Rep = testDb.getSeedProtein(g1);
        assertThat(g1Rep, nullValue());
        g1 = new Genome(new File("data", "fake.gto"));
        g1Rep = testDb.getSeedProtein(g1);
        assertThat(g1Rep.getFid(), equalTo("fig|1002870.3.peg.1581"));
        feat = g1.getFeature("fig|1002870.3.peg.1581");
        String prot1 = feat.getProteinTranslation();
        assertThat(g1Rep.getProtein(), equalTo(feat.getProteinTranslation()));
        assertThat(g1Rep.getName(), equalTo(g1.getName()));
        testDb.addRep(g1Rep);
        File saveFile = new File("data", "repdb.ser");
        testDb.save(saveFile);
        testDb = RepGenomeDb.load(saveFile);
        assertThat(testDb.getProtName(), equalTo("DNA-directed RNA polymerase beta' subunit"));
        assertThat(testDb.getProtAliases(), contains("DNA-directed RNA polymerase beta' subunit",
                "DNA-directed RNA polymerase subunit A"));
        g1Rep = testDb.get("1002870.3");
        assertThat(g1Rep.getFid(), equalTo("fig|1002870.3.peg.1581"));
        assertThat(g1Rep.getProtein(), equalTo(prot1));
        assertThat(testDb.size(), equalTo(3));
        g1 = new Genome(new File("data/gto_test", "1206109.5.gto"));
        g1Rep = testDb.getSeedProtein(g1);
        assertThat(g1Rep.getFid(), equalTo("fig|1206109.5.peg.68"));
    }

}
