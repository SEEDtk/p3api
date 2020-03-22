/**
 *
 */
package org.theseed.p3api;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.theseed.genome.Feature;
import org.theseed.genome.ViprGenome;
import org.theseed.gff.ViprKeywords;
import org.theseed.locations.Location;
import org.theseed.proteins.DnaTranslator;
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

    /**
     * test virus load
     * @throws IOException
     */
    public void testVirusLoad() throws IOException {
        File viprGff = new File("src/test", "CVgroup.gff");
        File viprFasta = new File("src/test", "CVgroup.fasta");
        ViprGenome.Builder loader = new ViprGenome.Builder();
        Collection<ViprGenome> viruses = loader.Load(viprGff, viprFasta);
        Set<String> genBankIds = Stream.of("JX993988", "JX993987", "KJ473812", "KJ473811", "KJ473813",
                "KJ473815", "KJ473814", "KJ473816", "KY502395", "KY502396").collect(Collectors.toSet());
        DnaTranslator xlate = new DnaTranslator(1);
        for (ViprGenome vGenome : viruses) {
            String genBankId = vGenome.getSourceId();
            assertThat(vGenome.getSource(), equalTo("GenBank"));
            assertThat(genBankIds, hasItem(genBankId));
            assertThat(vGenome.getGeneticCode(), equalTo(1));
            // Count the bad features.
            int badFeat = 0;
            int goodFeat = 0;
            // Make some sanity checks.  For each protein, we check translate the location to verify it.
            for (Feature feat : vGenome.getPegs()) {
                Location loc = feat.getLocation();
                String dna = vGenome.getDna(loc);
                // We can only do this next check for proteins without frame shifts.
                if (loc.getLength() % 3 == 0) {
                    String prot = xlate.pegTranslate(dna);
                    if (feat.getType().contentEquals("mat_peptide")) {
                        assertThat(feat + " is not translated correctly", prot, equalTo(feat.getProteinTranslation()));
                    } else {
                        assertTrue(feat + " is missing stop codon", prot.endsWith("*"));
                        assertTrue(feat + " is translated incorrectly", prot.startsWith(feat.getProteinTranslation()));
                        assertThat(feat + " has extra letters", prot.length(), equalTo(feat.getProteinLength() + 1));
                    }
                    goodFeat++;
                } else {
                    badFeat++;
                }
                assertThat(feat + " does not have a good function", feat.getFunction(), not(equalTo("hypothetical protein")));
            }
            System.out.format("%d bad features, %d good features.%n", badFeat, goodFeat);
            // Verify that the taxonomy is for a virus.
            int[] lineage = vGenome.getLineage();
            assertThat(lineage[0], equalTo(10239));
            assertThat(lineage[lineage.length - 1], equalTo(vGenome.getTaxonomyId()));
        }
        assertTrue(true);
    }

}
