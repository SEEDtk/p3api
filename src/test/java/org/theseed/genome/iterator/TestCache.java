/**
 *
 */
package org.theseed.genome.iterator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.theseed.basic.ParseFailureException;
import org.theseed.genome.Genome;

/**
 * @author Bruce Parrello
 *
 */
public class TestCache {

    @Test
    public void testCache() throws IOException, ParseFailureException {
        GenomeSource genomes = GenomeSource.Type.DIR.create(new File("data", "gto_test"));
        assertThat(genomes.size(), equalTo(6));
        GenomeCache cache = new GenomeCache(genomes, 3);
        Genome test1 = cache.get("1002870.3");
        assertThat(test1.getId(), equalTo("1002870.3"));
        Genome test2 = cache.get("1079.16");
        assertThat(test2.getId(), equalTo("1079.16"));
        Genome test = cache.get("1002870.3");
        assertThat(test == test1, equalTo(true));
        test = cache.get("1206109.5");
        assertThat(test.getId(), equalTo("1206109.5"));
        test = cache.get("1121447.3");
        assertThat(test.getId(), equalTo("1121447.3"));
        test = cache.get("1079.16");
        assertThat(test.getId(), equalTo("1079.16"));
        assertThat(test == test2, equalTo(false));
        cache.logStats("Test");
    }

}
