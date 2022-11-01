package org.theseed.p3api;


import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.fail;

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
import org.theseed.p3api.P3Connection.Table;
import org.theseed.p3api.P3Genome.Details;

import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * Unit test for simple App.
 */
public class P3ApiTest
{
    /**
     * test detail levels
     */
    @Test
    public void testDetails() {
        P3Genome.Details level = P3Genome.Details.FULL;
        assertThat(level.includesContigs(), equalTo(true));
        assertThat(level.includesFeatures(), equalTo(true));
        assertThat(level.includesProteins(), equalTo(true));
        level = P3Genome.Details.CONTIGS;
        assertThat(level.includesContigs(), equalTo(true));
        assertThat(level.includesFeatures(), equalTo(false));
        assertThat(level.includesProteins(), equalTo(false));
        level = P3Genome.Details.PROTEINS;
        assertThat(level.includesContigs(), equalTo(false));
        assertThat(level.includesFeatures(), equalTo(true));
        assertThat(level.includesProteins(), equalTo(true));
        level = P3Genome.Details.STRUCTURE_ONLY;
        assertThat(level.includesContigs(), equalTo(false));
        assertThat(level.includesFeatures(), equalTo(true));
        assertThat(level.includesProteins(), equalTo(false));
    }

    /**
     * test the basic connection
     */
    @Test
    public void testConnection() {
        P3Connection p3 = new P3Connection();
        JsonObject genome = p3.getRecord(Table.GENOME, "1798516.3", "genome_id,genome_name,genome_length,gc_content,taxon_lineage_names");
        assertThat(P3Connection.getString(genome, "genome_id"), equalTo("1798516.3"));
        assertThat(P3Connection.getString(genome, "genome_name"), equalTo("Candidatus Kaiserbacteria bacterium RIFCSPLOWO2_01_FULL_55_19"));
        assertThat(P3Connection.getInt(genome, "genome_length"), equalTo(426361));
        assertThat(P3Connection.getDouble(genome, "gc_content"), closeTo(55.05, 0.005));
        String[] taxonomy = P3Connection.getStringList(genome, "taxon_lineage_names");
        assertThat(taxonomy, arrayContaining("cellular organisms", "Bacteria", "Bacteria incertae sedis", "Bacteria candidate phyla",
                "Patescibacteria group", "Parcubacteria group", "Candidatus Kaiserbacteria",
                "Candidatus Kaiserbacteria bacterium RIFCSPLOWO2_01_FULL_55_19"));
        List<JsonObject> genomes = p3.query(Table.GENOME, "genome_id,genome_name,contigs", Criterion.IN("genome_id", "1803813.4", "1803814.4"));
        assertThat(genomes.size(), equalTo(2));
        genome = genomes.get(0);
        assertThat(P3Connection.getString(genome, "genome_id"), equalTo("1803813.4"));
        assertThat(P3Connection.getString(genome, "genome_name"), equalTo("Theionarchaea archaeon DG-70"));
        assertThat(P3Connection.getInt(genome, "contigs"), equalTo(177));
        genome = genomes.get(1);
        assertThat(P3Connection.getString(genome, "genome_id"), equalTo("1803814.4"));
        assertThat(P3Connection.getString(genome, "genome_name"), equalTo("Theionarchaea archaeon DG-70-1"));
        assertThat(P3Connection.getInt(genome, "contigs"), equalTo(194));
        Collection<String> idList = new ArrayList<String>();
        idList.add("1803813.4");
        idList.add("1803814.4");
        Map<String, JsonObject> genomeMap = p3.getRecords(Table.GENOME, idList, "genome_id,genome_name,contigs");
        genome = genomeMap.get("1803813.4");
        assertThat(P3Connection.getString(genome, "genome_id"), equalTo("1803813.4"));
        assertThat(P3Connection.getString(genome, "genome_name"), equalTo("Theionarchaea archaeon DG-70"));
        assertThat(P3Connection.getInt(genome, "contigs"), equalTo(177));
        genome = genomeMap.get("1803814.4");
        assertThat(P3Connection.getString(genome, "genome_id"), equalTo("1803814.4"));
        assertThat(P3Connection.getString(genome, "genome_name"), equalTo("Theionarchaea archaeon DG-70-1"));
        assertThat(P3Connection.getInt(genome, "contigs"), equalTo(194));
        List<JsonObject> features = p3.query(Table.FEATURE, "patric_id,product", Criterion.EQ("product",
                "LSU ribosomal protein L31p @ LSU ribosomal protein L31p, zinc-independent"),
                Criterion.EQ("genome_id", "1798516.3"));
        assertThat(features.size(), equalTo(1));
        JsonObject feature = features.get(0);
        assertThat(P3Connection.getString(feature, "patric_id"), equalTo("fig|1798516.3.peg.45"));
        assertThat(P3Connection.getString(feature, "product"),
                equalTo("LSU ribosomal protein L31p @ LSU ribosomal protein L31p, zinc-independent"));
        JsonObject feature2 = p3.getRecord(Table.FEATURE, "fig|1798516.3.peg.45", "patric_id,product");
        assertThat(P3Connection.getString(feature2, "patric_id"), equalTo("fig|1798516.3.peg.45"));
        assertThat(P3Connection.getString(feature2, "product"),
                equalTo("LSU ribosomal protein L31p @ LSU ribosomal protein L31p, zinc-independent"));
        Collection<String> genomeList = new ArrayList<String>();
        genomeList.add("226186.12");
        genomeList.add("300852.9");
        List<JsonObject> contigs = p3.getRecords(Table.CONTIG, "genome_id", genomeList, "sequence_id,length");
        assertThat(contigs.size(), equalTo(5));
        for (JsonObject contig : contigs) {
            String genomeId = P3Connection.getString(contig, "genome_id");
            String seqId = P3Connection.getString(contig, "sequence_id");
            int len = P3Connection.getInt(contig, "length");
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
                String fid = P3Connection.getString(feat, "patric_id");
                String fam = P3Connection.getString(feat, "pgfam_id");
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
    @Test
    public void testCriteria() {
        assertThat(Criterion.EQ("genome_id", "100.2"), equalTo("eq(genome_id,100.2)"));
        assertThat(Criterion.NE("genome_type", "(plasmid)"),equalTo("ne(genome_type,+plasmid+)"));
        assertThat(Criterion.EQ("taxonomicThing", "100::200::300"), equalTo("eq(taxonomicThing,100+200+300)"));
        assertThat(Criterion.IN("product", "look / at # me", "I'm so happy"), equalTo("in(product,(look+at+me,I+m+so+happy))"));
        assertThat(Criterion.GE("length", 10), equalTo("ge(length,10)"));
        assertThat(Criterion.LE("size", 100), equalTo("le(size,100)"));
    }

    /**
     * Test genome creation
     *
     * @throws IOException
     * @throws NumberFormatException
     */
    @Test
    public void testP3Genome() throws NumberFormatException, IOException {
        P3Connection p3 = new P3Connection();
        // Verify we get null for nonexistent genomes.  2157 is a kingdom taxon, so it will never be on a genome ID.
        P3Genome p3genome = P3Genome.load(p3, "2157.4", P3Genome.Details.FULL);
        assertThat(p3genome, nullValue());
        // Get the genome from disk and download a copy from PATRIC.
        Genome gto = new Genome(new File("data", "test.gto"));
        p3genome = P3Genome.load(p3, gto.getId(), P3Genome.Details.STRUCTURE_ONLY);
        assertThat(p3genome.getId(), equalTo(gto.getId()));
        assertThat(p3genome.getName(), equalTo(gto.getName()));
        assertThat(p3genome.getDomain(), equalTo(gto.getDomain()));
        assertThat(ArrayUtils.toObject(p3genome.getLineage()), arrayContaining(ArrayUtils.toObject(gto.getLineage())));
        assertThat(p3genome.getGeneticCode(), equalTo(gto.getGeneticCode()));
        assertThat(p3genome.getTaxonomyId(), equalTo(gto.getTaxonomyId()));
        assertThat(p3genome.getFeatureCount(), equalTo(gto.getFeatureCount()));
        assertThat(p3genome.getContigCount(), equalTo(gto.getContigCount()));
        assertThat(p3genome.hasContigs(), equalTo(false));
        Collection<Feature> p3fids = p3genome.getFeatures();
        for (Feature p3fid : p3fids) {
            Feature fid = gto.getFeature(p3fid.getId());
            assertThat("Extra feature " + p3fid, fid, not(nullValue()));
            assertThat("Function error in " + p3fid, p3fid.getFunction(), equalTo(fid.getFunction()));
            assertThat("Location error in " + p3fid, p3fid.getLocation(), equalTo(fid.getLocation()));
            assertThat("Local family error in " + p3fid, p3fid.getPlfam(), equalTo(fid.getPlfam()));
            assertThat("Global family error in " + p3fid, p3fid.getPgfam(), equalTo(fid.getPgfam()));
            assertThat("FIG family error in " + p3fid, p3fid.getFigfam(), equalTo(fid.getFigfam()));
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
        // Special check for SSU rRNA.
        String ssu = p3genome.getSsuRRna();
        assertThat(ssu, not(emptyString()));
        assertThat(ssu, equalTo(gto.getSsuRRna()));
        // Special check for the pheS protein.
        Feature p3seedFid = p3genome.getFeature("fig|243277.26.peg.1166");
        Feature seedFid = p3genome.getFeature("fig|243277.26.peg.1166");
        assertThat(p3seedFid.getProteinTranslation(), equalTo(seedFid.getProteinTranslation()));
        // Test save and restore.
        File tempFile = new File("data", "p3gto.ser");
        p3genome.save(tempFile);
        Genome gto2 = new Genome(tempFile);
        assertThat(gto2.getSsuRRna(), equalTo(gto.getSsuRRna()));
        assertThat(gto2.getName(), equalTo(gto.getName()));
        // Now boost the level to PROTEINS.
        p3genome = P3Genome.load(p3, gto.getId(), P3Genome.Details.PROTEINS);
        Collection<Feature> pegs = gto.getPegs();
        for (Feature fid : pegs) {
            Feature p3fid = p3genome.getFeature(fid.getId());
            assertThat(p3fid.getProteinTranslation(), equalTo(fid.getProteinTranslation()));
        }
        assertThat(p3genome.hasContigs(), equalTo(false));
        // Now, FULL level.
        p3genome = P3Genome.load(p3, gto.getId(), P3Genome.Details.FULL);
        for (Contig contig : contigs) {
            Contig p3contig = p3genome.getContig(contig.getId());
            assertThat(p3contig.getSequence(), equalTo(contig.getSequence()));
        }
        assertThat(p3genome.hasContigs(), equalTo(true));
        // Finally, CONTIGS level.
        p3genome = P3Genome.load(p3, gto.getId(), P3Genome.Details.CONTIGS);
        assertThat(p3genome.hasContigs(), equalTo(true));
        Collection<Feature> fids = p3genome.getFeatures();
        assertThat(fids.size(), equalTo(0));
        for (Contig contig : contigs) {
            Contig p3contig = p3genome.getContig(contig.getId());
            assertThat(p3contig.getSequence(), equalTo(contig.getSequence()));
        }
    }

    /***
     * test special SSU RRNA situations
     */
    @Test
    public void testSsuMissing() {
        P3Connection p3 = new P3Connection();
        P3Genome p3Genome = P3Genome.load(p3, "1262806.3", P3Genome.Details.STRUCTURE_ONLY);
        assertThat(p3Genome.getSsuRRna(), emptyString());
    }

    /**
     * test LE and GE
     */
    @Test
    public void testRanges() {
        P3Connection p3 = new P3Connection();
        List<JsonObject> records = p3.query(Table.GENOME, "genome_id,genome_length", Criterion.GE("genome_length",10000000));
        for (JsonObject record : records)
            assertThat(P3Connection.getInt(record, "genome_length"), greaterThanOrEqualTo(10000000));
        assertThat(records.size(), greaterThan(100));
        records = p3.query(Table.GENOME, "genome_id,genome_length", 
        		Criterion.EQ("superkingdom", "Bacteria"), Criterion.LE("genome_length",100000));
        for (JsonObject record : records)
            assertThat(P3Connection.getInt(record, "genome_length"), lessThanOrEqualTo(100000));
        assertThat(records.size(), greaterThan(100));
    }

    /**
     * test genome cache
     *
     * @throws IOException
     */
    @Test
    public void testCache() throws IOException {
        File gCache = new File("data/cache");
        // Clean the cache.
        for (File file : gCache.listFiles())
            if (StringUtils.endsWith(file.getName(), ".gto"))
                    file.delete();
        // Read in a random genome.
        P3Connection p3 = new P3Connection();
        Genome g1 = P3Genome.load(p3, "324602.8", Details.FULL, gCache);
        assertThat(g1, not(nullValue()));
        assertThat(g1 instanceof P3Genome, equalTo(true));
        assertThat(g1.getId(), equalTo("324602.8"));
        Genome g2 = P3Genome.load(p3, "324602.8", Details.FULL, gCache);
        assertThat(g2 instanceof P3Genome, equalTo(false));
        for (Feature feat : g1.getPegs()) {
            Feature feat2 = g2.getFeature(feat.getId());
            assertThat(feat2.getProteinTranslation(), equalTo(feat.getProteinTranslation()));
            assertThat(feat2.getFunction(), equalTo(feat.getFunction()));
        }
        File gFile = new File(gCache, "324602.8.gto");
        assertThat(gFile.exists(), equalTo(true));
    }

    /**
     * Special method for testing current login.
     */
    /* public void testLogin() {
        Connection p3 = new Connection();
        JsonObject genome = p3.getRecord(Table.GENOME, "821.4206", "genome_id,genome_name");
        assertThat(genome, not(nullValue()));
    }
    // */
}
