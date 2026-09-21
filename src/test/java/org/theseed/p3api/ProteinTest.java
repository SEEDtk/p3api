/**
 *
 */
package org.theseed.p3api;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import org.junit.jupiter.api.Test;

/**
 * @author Bruce Parrello
 *
 */
public class ProteinTest {

    @Test
    public void testGenomeMD5() throws NoSuchAlgorithmException, IOException {
        P3MD5Hex mdComputer = new P3MD5Hex();
        String genome752Md5 = mdComputer.genomeMD5("752.3");
        String genome853Md5 = mdComputer.genomeMD5("853.161");
        assertThat(genome752Md5, notNullValue());
        assertThat(genome853Md5, notNullValue());
        Collection<String> genomes = new ArrayList<>(2);
        genomes.add("853.161");
        genomes.add("752.3");
        genomes.add("2.1");
        Map<String, String> md5Map = mdComputer.genomeMD5s(genomes);
        assertThat(md5Map.size(), equalTo(2));
        assertThat(md5Map.get("853.161"), equalTo(genome853Md5));
        assertThat(md5Map.get("752.3"), equalTo(genome752Md5));
        assertThat(md5Map.get("2.1"), nullValue());
    }
}
