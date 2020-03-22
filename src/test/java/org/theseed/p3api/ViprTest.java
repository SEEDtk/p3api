/**
 *
 */
package org.theseed.p3api;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.theseed.gff.ViprKeywords;
import org.theseed.sequence.Sequence;


/**
 * Testing for Vipr stuff
 *
 * @author Bruce Parrello
 *
 */
public class ViprTest extends TestCase {

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public ViprTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( ViprTest.class );
    }

    /**
     * Test genome IDs.
     */
    public void testGenomeId() {
        IdClearinghouse idConnect = new IdClearinghouse();
        String newId1 = idConnect.computeGenomeId(83333);
        assertThat(newId1, startsWith("83333."));
        String newId2 = idConnect.computeGenomeId(83333);
        assertThat(newId2, startsWith("83333."));
        int suffix1 = Integer.parseInt(StringUtils.substringAfter(newId1, "."));
        int suffix2 = Integer.parseInt(StringUtils.substringAfter(newId2, "."));
        assertThat(suffix1, lessThan(suffix2));
    }

    /**
     * Test GFF keyword parsing.
     */
    public void testKeywords() {
        String input1 = "ID =JX993988;Name=Bat coronavirus Cp%2FYunnan2011, complete genome.Organism_name= SARS-related coronavirus Cp/Yunnan2011;Strain=Cp%2FYunnan2011ID=JX993988;Name=SARS-related coronavirus Cp/Yunnan2011;Dbxref=taxon:1283333);Dbxref=GenBank:JX993988";
        Map<String,String> keywords = ViprKeywords.gffParse(input1);
        assertThat(keywords.get("ID"), equalTo("JX993988"));
        assertThat(keywords.get("Strain"), equalTo("Cp/Yunnan2011ID=JX993988"));
        assertThat(keywords.get("Name"), equalTo("SARS-related coronavirus Cp/Yunnan2011"));
        assertThat(keywords.get("taxon"), equalTo("1283333"));
        assertThat(keywords.get("GenBank"), equalTo("JX993988"));
        assertThat(keywords.size(), equalTo(5));
        Sequence test1 = new Sequence("gb:JX993988|Organism:Bat", "coronavirus Cp/Yunnan2011|Strain Name:Cp/Yunnan2011|Segment:null|Host:Bat",
                "ACGT");
        keywords = ViprKeywords.fastaParse(test1);
        assertThat(keywords.get("gb"), equalTo("JX993988"));
        assertThat(keywords.get("Organism"), equalTo("Bat coronavirus Cp/Yunnan2011"));
        assertThat(keywords.get("Strain Name"), equalTo("Cp/Yunnan2011"));
        assertThat(keywords.get("Segment"), equalTo("null"));
        assertThat(keywords.get("Host"), equalTo("Bat"));
        assertThat(keywords.size(), equalTo(5));
    }

}
