/**
 *
 */
package org.theseed.genome;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.basic.ParseFailureException;
import org.theseed.genome.iterator.GenomeSource;
import org.theseed.io.TabbedLineReader;
import org.theseed.p3api.P3Connection;
import org.theseed.p3api.P3Genome;

/**
 *
 * @author Bruce Parrello
 *
 */
public class MultiDirectoryTest {

    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(MultiDirectoryTest.class);


    @Test
    public void testCreate() throws IOException, ParseFailureException {
        File newDir = new File("data", "newMaster");
        if (! newDir.isDirectory())
            FileUtils.forceMkdir(newDir);
        // Here we expect an exception.
        try {
            GenomeMultiDirectory.create(newDir, false);
            assertThat("Missing IO exception.", false);
        } catch (IOException e) { }
        GenomeMultiDirectory multiDir = GenomeMultiDirectory.create(newDir, true);
        assertThat(multiDir.size(), equalTo(0));
        // Create an artifically small directory limit.
        GenomeMultiDirectory.MAX_FILES_PER_DIRECTORY = 10;
        // Get a list of PATRIC genomes.
        Set<String> gList = TabbedLineReader.readSet(new File("data", "glist.txt"), "genome_id");
        assertThat(gList.size(), greaterThan(10));
        // Connect to PATRIC and add the genomes.
        P3Connection p3 = new P3Connection();
        Map<String, Genome> saved = new HashMap<String, Genome>(gList.size());
        for (String genomeId : gList) {
            Genome genome = P3Genome.load(p3, genomeId, P3Genome.Details.STRUCTURE_ONLY);
            multiDir.add(genome);
            saved.put(genomeId, genome);
        }
        assertThat(multiDir.size(), equalTo(gList.size()));
        for (Genome genome : multiDir)
            checkSaved(saved, genome);
        GenomeSource sourceDir = GenomeSource.Type.MASTER.create(newDir);
        assertThat(sourceDir.size(), equalTo(gList.size()));
        for (Genome genome : sourceDir)
            checkSaved(saved, genome);
        Genome genome1 = multiDir.get("904345.3");
        checkSaved(saved, genome1);
        genome1 = multiDir.get("83333.1");
        assertThat(genome1, nullValue());
        assertThat(multiDir.contains("904345.3"), equalTo(true));
        assertThat(multiDir.contains("83333.1"), equalTo(false));
        // Reload the multi-directory.  This time we will delete during iteration.
        multiDir = new GenomeMultiDirectory(newDir);
        assertThat(multiDir.size(), equalTo(gList.size()));
        Iterator<Genome> gIter = multiDir.iterator();
        String lastGID = "";
        while (gIter.hasNext()) {
            Genome genome = gIter.next();
            String gid = genome.getId();
            assertThat(gid, greaterThan(lastGID));
            lastGID = gid;
            if (gid.contentEquals("904345.3")) {
                gIter.remove();
                assertThat(multiDir.contains("904345.3"), equalTo(false));
                assertThat(multiDir.size(), equalTo(gList.size() - 1));
            } else
                checkSaved(saved, genome);
        }
        File lastDir = multiDir.getLastDir();
        File[] lastFiles = lastDir.listFiles();
        assertThat(multiDir.getLastDirSize(), equalTo(lastFiles.length));
        lastGID = StringUtils.substring(lastFiles[0].getName(), 0, -GenomeMultiDirectory.EXTENSION.length());
        assertThat(multiDir.contains(lastGID), equalTo(true));
        multiDir.remove(lastGID);
        assertThat(multiDir.contains(lastGID), equalTo(false));
        assertThat(multiDir.getLastDirSize(), equalTo(lastFiles.length - 1));
        assertThat(multiDir.getLastDir(), equalTo(lastDir));
        assertThat(multiDir.size(), equalTo(gList.size() - 2));
        for (Genome genome : multiDir) {
            assertThat(genome.getId(), not(equalTo(lastGID)));
        }

    }

    /**
     * Test the other genome sources.
     *
     * @throws IOException
     * @throws ParseFailureException
     */
    @Test
    public void genomeSourceTest() throws IOException, ParseFailureException {
        // Read the test genome directory and copy it to a master.
        File gtDir = new File("data", "gto_test");
        File mDir = new File("data", "newMaster");
        GenomeDirectory base = new GenomeDirectory(gtDir);
        GenomeMultiDirectory master = GenomeMultiDirectory.create(mDir, true);
        Map<String, Genome> saved = new HashMap<String, Genome>();
        for (Genome genome : base) {
            saved.put(genome.getId(), genome);
            master.add(genome);
        }
        // Now verify it against the same directory as a source.
        GenomeSource.Type[] types = new GenomeSource.Type[] { GenomeSource.Type.MASTER, GenomeSource.Type.DIR, GenomeSource.Type.PATRIC };
        File[] files = new File[] { mDir, gtDir, new File("data/gto_test", "pList.tbl") };
        for (int i = 0; i < types.length; i++) {
            String label = types[i].toString();
            log.info("Processing source type {}.", label);
            GenomeSource source = types[i].create(files[i]);
            assertThat(label, source.size(), equalTo(saved.size()));
            for (Genome genome : source)
                checkSaved(saved, genome);
        }
        // Do it again with filtering.
        Set<String> badGenomes = new TreeSet<String>();
        badGenomes.add("1079.16");
        badGenomes.add("1206109.5");
        badGenomes.add("83333.1");
        for (int i = 0; i < types.length; i++) {
            String label = types[i].toString();
            log.info("Processing source type {}.", label);
            GenomeSource source = types[i].create(files[i]);
            source.setSkipSet(badGenomes);
            assertThat(label, source.size(), equalTo(saved.size() - 2));
            for (Genome genome : source) {
                assertThat(genome.toString(), badGenomes.contains(genome.getId()), equalTo(false));
                checkSaved(saved, genome);
            }
        }

    }

    /**
     * Check that a genome matches a saved copy.
     *
     * @param saved		map of saved copies
     * @param genome	genome to check
     */
    private void checkSaved(Map<String, Genome> saved, Genome genome) {
        String genomeId = genome.getId();
        Genome genomeSaved = saved.get(genomeId);
        assertThat(genomeId, genomeSaved, not(nullValue()));
        assertThat(genomeId, genome.getName(), equalTo(genomeSaved.getName()));
        assertThat(genomeId, genome.getContigCount(), equalTo(genomeSaved.getContigCount()));
        assertThat(genomeId, genome.getFeatureCount(), equalTo(genomeSaved.getFeatureCount()));
        for (Feature feat : genome.getFeatures()) {
            String fid = feat.getId();
            Feature featSaved = genomeSaved.getFeature(fid);
            assertThat(fid, featSaved, not(nullValue()));
            assertThat(fid, feat.getPegFunction(), equalTo(featSaved.getPegFunction()));
        }
    }

}
