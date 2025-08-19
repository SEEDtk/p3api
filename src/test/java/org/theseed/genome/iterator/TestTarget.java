/**
 *
 */
package org.theseed.genome.iterator;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import org.junit.jupiter.api.Test;
import org.theseed.basic.ParseFailureException;
import org.theseed.genome.Genome;
import org.theseed.genome.GenomeDirectory;

/**
 * @author Bruce Parrello
 *
 */
public class TestTarget {

    @Test
    public void test() throws IOException, ParseFailureException {
        File sDir = new File("data", "gto_test");
        // Reset the target directory.
        File tDir = new File("data", "gto_target");
        resetTarget(tDir);
        // Create the target with clearing.
        IGenomeTarget targetDir = GenomeTargetType.DIR.create(tDir, true);
        GenomeDirectory testDir = new GenomeDirectory(tDir);
        assertThat(testDir.size(), equalTo(0));
        // Test doing a copy.
        copyTest(sDir, tDir, targetDir);
        // Create the target without clearing.
        targetDir = GenomeTargetType.DIR.create(tDir, false);
        testDir = new GenomeDirectory(tDir);
        assertThat(testDir.size(), equalTo(2));
        // Test another copy.
        copyTest(sDir, tDir, targetDir);
    }

    public void copyTest(File sDir, File tDir, IGenomeTarget targetDir)
            throws IOException, ParseFailureException {
        GenomeSource sourceDir = GenomeSource.Type.DIR.create(sDir);
        for (Genome source : sourceDir)
            targetDir.add(source);
        GenomeDirectory testDir = new GenomeDirectory(tDir);
        assertThat(testDir.size(), equalTo(sourceDir.size()));
        Set<String> testIds = testDir.getGenomeIDs();
        for (String testId : testIds)
            assertThat(testId, testDir.contains(testId));
        resetTarget(tDir);
    }

    /**
     * Reset the target directory to contain only the two genomes.
     *
     * @param tDir		target directory to reset
     *
     * @throws IOException
     */
    public void resetTarget(File tDir) throws IOException {
        GenomeDirectory targetDir = new GenomeDirectory(tDir);
        // Note we copy the set so that it is not a problem if it gets modified.
        Set<String> targetIds = new TreeSet<>(targetDir.getGenomeIDs());
        for (String genomeId : targetIds) {
            if (! genomeId.contentEquals("1079.16") && ! genomeId.contentEquals("1121447.3")) {
                File gFile = new File(tDir, genomeId + ".gto");
                FileUtils.forceDelete(gFile);
            }
        }
    }

}
