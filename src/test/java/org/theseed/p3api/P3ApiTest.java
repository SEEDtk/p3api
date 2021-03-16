package org.theseed.p3api;

import junit.framework.Test;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.theseed.genome.Contig;
import org.theseed.genome.Feature;
import org.theseed.genome.Genome;
import org.theseed.genome.GoTerm;
import org.theseed.p3api.Connection.Table;
import org.theseed.p3api.P3Genome.Details;

import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * Unit test for simple App.
 */
public class P3ApiTest extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public P3ApiTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( P3ApiTest.class );
    }

    /**
     * test detail levels
     */
    public void testDetails() {
        P3Genome.Details level = P3Genome.Details.FULL;
        assertTrue(level.includesContigs());
        assertTrue(level.includesFeatures());
        assertTrue(level.includesProteins());
        level = P3Genome.Details.CONTIGS;
        assertTrue(level.includesContigs());
        assertFalse(level.includesFeatures());
        assertFalse(level.includesProteins());
        level = P3Genome.Details.PROTEINS;
        assertFalse(level.includesContigs());
        assertTrue(level.includesFeatures());
        assertTrue(level.includesProteins());
        level = P3Genome.Details.STRUCTURE_ONLY;
        assertFalse(level.includesContigs());
        assertTrue(level.includesFeatures());
        assertFalse(level.includesProteins());
    }

    /**
     * test the basic connection
     */
    public void testConnection() {
        Connection p3 = new Connection();
        JsonObject genome = p3.getRecord(Table.GENOME, "1798516.3", "genome_id,genome_name,genome_length,gc_content,taxon_lineage_names");
        assertThat(Connection.getString(genome, "genome_id"), equalTo("1798516.3"));
        assertThat(Connection.getString(genome, "genome_name"), equalTo("Candidatus Kaiserbacteria bacterium RIFCSPLOWO2_01_FULL_55_19"));
        assertThat(Connection.getInt(genome, "genome_length"), equalTo(426361));
        assertThat(Connection.getDouble(genome, "gc_content"), closeTo(55.05, 0.005));
        String[] taxonomy = Connection.getStringList(genome, "taxon_lineage_names");
        assertThat(taxonomy, arrayContaining("cellular organisms", "Bacteria", "Bacteria incertae sedis", "Bacteria candidate phyla",
                "Patescibacteria group", "Parcubacteria group", "Candidatus Kaiserbacteria",
                "Candidatus Kaiserbacteria bacterium RIFCSPLOWO2_01_FULL_55_19"));
        List<JsonObject> genomes = p3.query(Table.GENOME, "genome_id,genome_name,contigs", Criterion.IN("genome_id", "1803813.4", "1803814.4"));
        assertThat(genomes.size(), equalTo(2));
        genome = genomes.get(0);
        assertThat(Connection.getString(genome, "genome_id"), equalTo("1803813.4"));
        assertThat(Connection.getString(genome, "genome_name"), equalTo("Theionarchaea archaeon DG-70"));
        assertThat(Connection.getInt(genome, "contigs"), equalTo(177));
        genome = genomes.get(1);
        assertThat(Connection.getString(genome, "genome_id"), equalTo("1803814.4"));
        assertThat(Connection.getString(genome, "genome_name"), equalTo("Theionarchaea archaeon DG-70-1"));
        assertThat(Connection.getInt(genome, "contigs"), equalTo(194));
        Collection<String> idList = new ArrayList<String>();
        idList.add("1803813.4");
        idList.add("1803814.4");
        Map<String, JsonObject> genomeMap = p3.getRecords(Table.GENOME, idList, "genome_id,genome_name,contigs");
        genome = genomeMap.get("1803813.4");
        assertThat(Connection.getString(genome, "genome_id"), equalTo("1803813.4"));
        assertThat(Connection.getString(genome, "genome_name"), equalTo("Theionarchaea archaeon DG-70"));
        assertThat(Connection.getInt(genome, "contigs"), equalTo(177));
        genome = genomeMap.get("1803814.4");
        assertThat(Connection.getString(genome, "genome_id"), equalTo("1803814.4"));
        assertThat(Connection.getString(genome, "genome_name"), equalTo("Theionarchaea archaeon DG-70-1"));
        assertThat(Connection.getInt(genome, "contigs"), equalTo(194));
        List<JsonObject> features = p3.query(Table.FEATURE, "patric_id,product", Criterion.EQ("product",
                "LSU ribosomal protein L31p @ LSU ribosomal protein L31p, zinc-independent"),
                Criterion.EQ("genome_id", "1798516.3"));
        assertThat(features.size(), equalTo(1));
        JsonObject feature = features.get(0);
        assertThat(Connection.getString(feature, "patric_id"), equalTo("fig|1798516.3.peg.45"));
        assertThat(Connection.getString(feature, "product"),
                equalTo("LSU ribosomal protein L31p @ LSU ribosomal protein L31p, zinc-independent"));
        JsonObject feature2 = p3.getRecord(Table.FEATURE, "fig|1798516.3.peg.45", "patric_id,product");
        assertThat(Connection.getString(feature2, "patric_id"), equalTo("fig|1798516.3.peg.45"));
        assertThat(Connection.getString(feature2, "product"),
                equalTo("LSU ribosomal protein L31p @ LSU ribosomal protein L31p, zinc-independent"));
        Collection<String> genomeList = new ArrayList<String>();
        genomeList.add("226186.12");
        genomeList.add("300852.9");
        List<JsonObject> contigs = p3.getRecords(Table.CONTIG, "genome_id", genomeList, "sequence_id,length");
        assertThat(contigs.size(), equalTo(5));
        for (JsonObject contig : contigs) {
            String genomeId = Connection.getString(contig, "genome_id");
            String seqId = Connection.getString(contig, "sequence_id");
            int len = Connection.getInt(contig, "length");
            switch (genomeId) {
            case "226186.12" :
                switch (seqId) {
                case "NC_004703" :
                    assertThat(len, equalTo(33038));
                    break;
                case "NC_004663" :
                    assertThat(len, equalTo(6260361));
                    break;
                default :
                    fail("Incorrect sequence ID " + seqId + " for 226186.12.");
                }
                break;
            case "300852.9" :
                switch (seqId) {
                case "NC_006463" :
                    assertThat(len, equalTo(9322));
                    break;
                case "NC_006462" :
                    assertThat(len, equalTo(256992));
                    break;
                case "NC_006461" :
                    assertThat(len, equalTo(1849742));
                    break;
                default :
                    fail("Incorrect sequence ID " + seqId + " for 300852.9.");
                }
                break;
            default :
                fail("Incorrect genome ID " + genomeId + ".");
            }
            idList = Arrays.asList("84725.3", "2698204.3", "86473.136", "1526414.4", "1766800.6", "2508882.3", "1069448.9");
            features = p3.getRecords(Table.FEATURE, "genome_id", idList, "patric_id,pgfam_id",
                    Criterion.EQ("product", "Phenylalanyl-tRNA synthetase alpha chain"));
            assertThat(features.size(), equalTo(13));
            for (JsonObject feat : features) {
                String fid = Connection.getString(feat, "patric_id");
                String fam = Connection.getString(feat, "pgfam_id");
                switch (fid) {
                case "fig|84725.3.peg.3359" :
                case "fig|86473.136.peg.768" :
                case "fig|1766800.6.peg.890" :
                    assertThat(fam, equalTo(""));
                    break;
                case "fig|1069448.9.peg.2010" :
                case "fig|2508882.3.peg.652" :
                case "fig|2698204.3.peg.309" :
                    assertThat(fam, equalTo("PGF_00038706"));
                    break;
                case "fig|84725.3.peg.6636" :
                case "fig|1526414.4.peg.127" :
                case "fig|1766800.6.peg.3951" :
                case "fig|2698204.3.peg.2176" :
                case "fig|1069448.9.peg.3497" :
                case "fig|2508882.3.peg.2353" :
                    assertThat(fam, equalTo("PGF_02019462"));
                    break;
                case "fig|1526414.4.peg.1156" :
                    assertThat(fam, equalTo("PGF_02019462"));
                    break;
                default :
                    fail("Incorrect feature ID " + fid + " from filtered query.");
                }
            }
        }
    }

    /**
     * Test criteria
     */
    public void testCriteria() {
        assertThat(Criterion.EQ("genome_id", "100.2"), equalTo("eq(genome_id,100.2)"));
        assertThat(Criterion.NE("genome_type", "(plasmid)"),equalTo("ne(genome_type,+plasmid+)"));
        assertThat(Criterion.EQ("taxonomicThing", "100::200::300"), equalTo("eq(taxonomicThing,100+200+300)"));
        assertThat(Criterion.IN("product", "look / at # me", "I'm so happy"), equalTo("in(product,(look+at+me,I+m+so+happy))"));
    }

    /**
     * Test genome creation
     *
     * @throws IOException
     * @throws NumberFormatException
     */
    public void testP3Genome() throws NumberFormatException, IOException {
        Connection p3 = new Connection();
        // Verify we get null for nonexistent genomes.  2157 is a kingdom taxon, so it will never be on a genome ID.
        P3Genome p3genome = P3Genome.Load(p3, "2157.4", P3Genome.Details.FULL);
        assertNull(p3genome);
        // Get the genome from disk and download a copy from PATRIC.
        Genome gto = new Genome(new File("data", "test.gto"));
        p3genome = P3Genome.Load(p3, gto.getId(), P3Genome.Details.STRUCTURE_ONLY);
        assertThat(p3genome.getId(), equalTo(gto.getId()));
        assertThat(p3genome.getName(), equalTo(gto.getName()));
        assertThat(p3genome.getDomain(), equalTo(gto.getDomain()));
        assertThat(ArrayUtils.toObject(p3genome.getLineage()), arrayContaining(ArrayUtils.toObject(gto.getLineage())));
        assertThat(p3genome.getGeneticCode(), equalTo(gto.getGeneticCode()));
        assertThat(p3genome.getTaxonomyId(), equalTo(gto.getTaxonomyId()));
        assertThat(p3genome.getFeatureCount(), equalTo(gto.getFeatureCount()));
        assertThat(p3genome.getContigCount(), equalTo(gto.getContigCount()));
        assertFalse(p3genome.hasContigs());
        Collection<Feature> p3fids = p3genome.getFeatures();
        for (Feature p3fid : p3fids) {
            Feature fid = gto.getFeature(p3fid.getId());
            assertNotNull("Extra feature " + p3fid, fid);
            assertThat("Function error in " + p3fid, p3fid.getFunction(), equalTo(fid.getFunction()));
            assertThat("Location error in " + p3fid, p3fid.getLocation(), equalTo(fid.getLocation()));
            assertThat("Local family error in " + p3fid, p3fid.getPlfam(), equalTo(fid.getPlfam()));
            assertThat("Type error in " + p3fid, p3fid.getType(), equalTo(fid.getType()));
            Collection<String> fidAliases = fid.getAliases();
            assertThat("Alias error in " + p3fid, p3fid.getAliases().size(), equalTo(fidAliases.size()));
            for (String p3Alias : p3fid.getAliases()) {
                assertThat("Alias " + p3Alias + " not found in " + fid, fidAliases, hasItem(p3Alias));
            }
            Collection<GoTerm> fidGoTerms = fid.getGoTerms();
            assertThat("Go term error in " + p3fid, p3fid.getGoTerms().size(), equalTo(fidGoTerms.size()));
            for (GoTerm p3goTerm : p3fid.getGoTerms()) {
                assertThat("Go term " + p3goTerm + " not found in " + fid, fidGoTerms, hasItem(p3goTerm));
            }
        }
        Collection<Contig> contigs = gto.getContigs();
        for (Contig contig : contigs) {
            Contig p3contig = p3genome.getContig(contig.getId());
            assertThat(p3contig.length(), equalTo(contig.length()));
            assertThat(p3contig.getAccession(), equalTo(contig.getAccession()));
            assertThat(p3contig.getDescription(), equalTo(contig.getDescription()));
        }
        // Special check for the pheS protein.
        Feature p3seedFid = p3genome.getFeature("fig|243277.26.peg.1166");
        Feature seedFid = p3genome.getFeature("fig|243277.26.peg.1166");
        assertThat(p3seedFid.getProteinTranslation(), equalTo(seedFid.getProteinTranslation()));
        // Now boost the level to PROTEINS.
        p3genome = P3Genome.Load(p3, gto.getId(), P3Genome.Details.PROTEINS);
        Collection<Feature> pegs = gto.getPegs();
        for (Feature fid : pegs) {
            Feature p3fid = p3genome.getFeature(fid.getId());
            assertThat(p3fid.getProteinTranslation(), equalTo(fid.getProteinTranslation()));
        }
        assertFalse(p3genome.hasContigs());
        // Now, FULL level.
        p3genome = P3Genome.Load(p3, gto.getId(), P3Genome.Details.FULL);
        for (Contig contig : contigs) {
            Contig p3contig = p3genome.getContig(contig.getId());
            assertThat(p3contig.getSequence(), equalTo(contig.getSequence()));
        }
        assertTrue(p3genome.hasContigs());
        // Finally, CONTIGS level.
        p3genome = P3Genome.Load(p3, gto.getId(), P3Genome.Details.CONTIGS);
        assertTrue(p3genome.hasContigs());
        Collection<Feature> fids = p3genome.getFeatures();
        assertThat(fids.size(), equalTo(0));
        for (Contig contig : contigs) {
            Contig p3contig = p3genome.getContig(contig.getId());
            assertThat(p3contig.getSequence(), equalTo(contig.getSequence()));
        }
    }

    /**
     * test genome cache
     *
     * @throws IOException
     */
    public void testCache() throws IOException {
        File gCache = new File("data/cache");
        // Clean the cache.
        for (File file : gCache.listFiles())
            if (StringUtils.endsWith(file.getName(), ".gto"))
                    file.delete();
        // Read in a random genome.
        Connection p3 = new Connection();
        Genome g1 = P3Genome.Load(p3, "324602.8", Details.FULL, gCache);
        assertNotNull(g1);
        assertTrue(g1 instanceof P3Genome);
        assertThat(g1.getId(), equalTo("324602.8"));
        Genome g2 = P3Genome.Load(p3, "324602.8", Details.FULL, gCache);
        assertFalse(g2 instanceof P3Genome);
        for (Feature feat : g1.getPegs()) {
            Feature feat2 = g2.getFeature(feat.getId());
            assertThat(feat2.getProteinTranslation(), equalTo(feat.getProteinTranslation()));
            assertThat(feat2.getFunction(), equalTo(feat.getFunction()));
        }
        File gFile = new File(gCache, "324602.8.gto");
        assertTrue(gFile.exists());
    }

}
