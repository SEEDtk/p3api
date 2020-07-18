/**
 *
 */
package org.theseed.p3api;

import java.io.File;
import java.io.IOException;

import org.theseed.genome.Genome;
import org.theseed.genome.core.CoreGenome;
import org.theseed.genome.core.OrganismDirectories;

import junit.framework.TestCase;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Bruce Parrello
 *
 */
public class CoreTest extends TestCase {

    /**
     * test core genomes
     *
     * @throws IOException
     */
    public void testLoad() throws IOException {
        Connection p3 = new Connection();
        File master = new File("src/test", "core");
        OrganismDirectories orgDirs = new OrganismDirectories(master);
        for (String genomeId : orgDirs) {
            File orgDir = new File(master, genomeId);
            Genome testGto = new CoreGenome(p3, orgDir);
            assertThat(testGto.getId(), equalTo(genomeId));
        }
    }

}
