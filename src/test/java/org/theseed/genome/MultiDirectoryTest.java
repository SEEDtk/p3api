/**
 *
 */
package org.theseed.genome;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.theseed.test.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.theseed.io.TabbedLineReader;
import org.theseed.p3api.Connection;
import org.theseed.p3api.P3Genome;

/**
 *
 * @author Bruce Parrello
 *
 */
public class MultiDirectoryTest {

    @Test
    public void testCreate() throws IOException {
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
        Connection p3 = new Connection();
        Map<String, Genome> saved = new HashMap<String, Genome>(gList.size());
        for (String genomeId : gList) {
            Genome genome = P3Genome.Load(p3, genomeId, P3Genome.Details.STRUCTURE_ONLY);
            multiDir.add(genome);
            saved.put(genomeId, genome);
        }
        assertThat(multiDir.size(), equalTo(gList.size()));
        for (Genome genome : multiDir)
            checkSaved(saved, genome);
        Genome genome1 = multiDir.get("904345.3");
        checkSaved(saved, genome1);
        genome1 = multiDir.get("83333.1");
        assertThat(genome1, nullValue());
        assertThat(multiDir.contains("904345.3"), isTrue());
        assertThat(multiDir.contains("83333.1"), isFalse());
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
                assertThat(multiDir.contains("904345.3"), isFalse());
                assertThat(multiDir.size(), equalTo(gList.size() - 1));
            } else
                checkSaved(saved, genome);
        }
        File lastDir = multiDir.getLastDir();
        File[] lastFiles = lastDir.listFiles();
        assertThat(multiDir.getLastDirSize(), equalTo(lastFiles.length));
        lastGID = StringUtils.substring(lastFiles[0].getName(), 0, -GenomeMultiDirectory.EXTENSION.length());
        assertThat(multiDir.contains(lastGID), isTrue());
        multiDir.remove(lastGID);
        assertThat(multiDir.contains(lastGID), isFalse());
        assertThat(multiDir.getLastDirSize(), equalTo(lastFiles.length - 1));
        assertThat(multiDir.getLastDir(), equalTo(lastDir));
        assertThat(multiDir.size(), equalTo(gList.size() - 2));
        for (Genome genome : multiDir) {
            assertThat(genome.getId(), not(equalTo(lastGID)));
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
