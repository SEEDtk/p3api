/**
 *
 */
package org.theseed.genome.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.commons.io.FileUtils;
import org.theseed.genome.CompareGenomes;
import org.theseed.genome.Genome;
import org.theseed.genome.iterator.GenomeSource;
import org.theseed.genome.iterator.GenomeTargetType;
import org.theseed.genome.iterator.IGenomeTarget;
import org.theseed.io.MarkerFile;
import org.theseed.p3api.P3Connection;
import org.theseed.sequence.Sequence;
import org.theseed.utils.ParseFailureException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.theseed.test.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Bruce Parrello
 *
 */
public class CoreTest {

    /**
     * test basic core genome load
     *
     * @throws IOException
     */
    @Test
    public void testLoad() throws IOException {
        P3Connection p3 = new P3Connection();
        File master = new File("data", "core");
        OrganismDirectories orgDirs = new OrganismDirectories(master);
        for (String genomeId : orgDirs) {
            File orgDir = new File(master, genomeId);
            Genome testGto = new CoreGenome(p3, orgDir);
            assertThat(testGto.getId(), equalTo(genomeId));
            String taxMarker = MarkerFile.read(new File(orgDir, "TAXONOMY"));
            assertThat(taxMarker, equalTo(testGto.getTaxString()));
            String name = MarkerFile.read(new File(orgDir, "GENOME"));
            assertThat(name, equalTo(testGto.getName()));
        }
    }

    @Test
    public void testTarget() throws IOException, ParseFailureException {
        P3Connection p3 = new P3Connection();
        // Create a temporary organism directory.
        File tempTarget = new File("data", "tempCore");
        FileUtils.forceMkdir(tempTarget);
        try {
            IGenomeTarget coreTarget = GenomeTargetType.CORE.create(tempTarget, true);
            File source = new File("data", "gto_test");
            Genome test1gto = new Genome(new File(source, "1123388.3.gto"));
            Genome test2gto = new Genome(new File(source, "1121911.3.gto"));
            coreTarget.add(test1gto);
            coreTarget.add(test2gto);
            // Read the GTOs back and compare them.
            OrganismDirectories orgDir = new OrganismDirectories(new File(tempTarget, "Organisms"));
            assertThat(orgDir.size(), equalTo(2));
            Genome test1core = new CoreGenome(p3, orgDir.getDir("1123388.3"));
            Genome test2core = new CoreGenome(p3, orgDir.getDir("1121911.3"));
            CompareGenomes.test(test1gto, test1core, false);
            CompareGenomes.test(test2gto, test2core, false);
            assertThat(coreTarget.contains(test1gto.getId()), isTrue());
            assertThat(coreTarget.contains(test2gto.getId()), isTrue());
            assertThat(coreTarget.contains("83333.1"), isFalse());
            // Do it again using a genome source.
            GenomeSource coreSource = GenomeSource.Type.CORE.create(tempTarget);
            assertThat(coreSource.size(), equalTo(2));
            for (Genome genome : coreSource) {
                switch (genome.getId()) {
                case "1123388.3" :
                    CompareGenomes.test(test1gto, genome, false);
                    break;
                case "1121911.3" :
                    CompareGenomes.test(test2gto, genome, false);
                    break;
                default:
                    fail("Invalid genome ID.");
                }
            }
        } finally {
            FileUtils.forceDelete(tempTarget);
        }

    }

    /**
     * test organism directories
     */
    public void testOrgDir() {
        OrganismDirectories orgDir = new OrganismDirectories(new File("data", "core.test"));
        assertThat(orgDir.size(), equalTo(3));
        Iterator<String> orgIter = orgDir.iterator();
        assertTrue(orgIter.hasNext());
        assertThat(orgIter.next(), equalTo("100.1"));
        assertTrue(orgIter.hasNext());
        assertThat(orgIter.next(), equalTo("200.2"));
        assertTrue(orgIter.hasNext());
        assertThat(orgIter.next(), equalTo("300.3"));
        assertFalse(orgIter.hasNext());
    }

    /**
     * Test the peg list
     * @throws IOException
     */
    public void testPegList() throws IOException {
        PegList testList = new PegList(new File("data", "testP.fa"));
        Sequence found = testList.get("fig|1538.8.peg.30");
        assertNull(found);
        found = testList.get("fig|1538.8.peg.12");
        assertThat(found.getLabel(), equalTo("fig|1538.8.peg.12"));
        Sequence found7 = testList.get("fig|1538.8.peg.7");
        Sequence found2 = testList.get("fig|1538.8.peg.2");
        Sequence found3 = testList.get("fig|1538.8.peg.3");
        Sequence found10 = testList.get("fig|1538.8.peg.10");
        ArrayList<Sequence> buffer = new ArrayList<Sequence>();
        testList.findClose(found, 1, buffer);
        assertThat(buffer, contains(found2));
        testList.suppress(found2);
        testList.suppress(found3);
        testList.findClose(found, 2, buffer);
        assertThat(buffer, contains(found2, found7, found10));
        Sequence found1 = testList.get("fig|1538.8.peg.1");
        Sequence found4 = testList.get("fig|1538.8.peg.4");
        Sequence found5 = testList.get("fig|1538.8.peg.5");
        Sequence found6 = testList.get("fig|1538.8.peg.6");
        Sequence found8 = testList.get("fig|1538.8.peg.8");
        Sequence found9 = testList.get("fig|1538.8.peg.9");
        Sequence found11 = testList.get("fig|1538.8.peg.11");
        Sequence found13 = testList.get("fig|1538.8.peg.13");
        Sequence found14 = testList.get("fig|1538.8.peg.14");
        Sequence found15 = testList.get("fig|1538.8.peg.15");
        testList.suppress(found1);
        testList.suppress(found4);
        testList.suppress(found5);
        testList.suppress(found6);
        testList.suppress(found7);
        testList.suppress(found8);
        testList.suppress(found9);
        testList.suppress(found10);
        testList.suppress(found11);
        testList.suppress(found14);
        testList.suppress(found15);
        testList.findClose(found, 4, buffer);
        assertThat(buffer, contains(found2, found7, found10, found13));
    }

    /**
     * test genome ID extraction
     */
    public void testGenomeIds() {
        assertThat(CoreUtilities.genomeOf("fig|12345.6.peg.10"), equalTo("12345.6"));
        assertThat(CoreUtilities.genomeOf("fig|23456.789.202.10"), equalTo("23456.789"));
        assertThat(CoreUtilities.genomeOf("patric|123.45.peg.10"), equalTo(null));
    }



}
