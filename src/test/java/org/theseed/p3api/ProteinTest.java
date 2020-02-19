/**
 *
 */
package org.theseed.p3api;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * @author Bruce Parrello
 *
 */
public class ProteinTest extends TestCase {

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public ProteinTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( ProteinTest.class );
    }

    public void testGenomeMD5() throws NoSuchAlgorithmException, IOException {
        P3MD5Hex mdComputer = new P3MD5Hex();
        assertThat(mdComputer.genomeMD5("752.3"), equalTo("132d96707e2c31d66c1c161f609c68d4"));
        assertThat(mdComputer.genomeMD5("853.161"), equalTo("791feea638e7f600003e7cb1aafd6e67"));
        Collection<String> genomes = new ArrayList<String>(2);
        genomes.add("853.161");
        genomes.add("752.3");
        genomes.add("2.1");
        Map<String, String> md5Map = mdComputer.genomeMD5s(genomes);
        assertThat(md5Map.size(), equalTo(2));
        assertThat(md5Map.get("853.161"), equalTo("791feea638e7f600003e7cb1aafd6e67"));
        assertThat(md5Map.get("752.3"), equalTo("132d96707e2c31d66c1c161f609c68d4"));
        assertNull(md5Map.get("2.1"));
    }
}
