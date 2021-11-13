/**
 *
 */
package org.theseed.p3api;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.apache.commons.lang3.ArrayUtils;
import org.theseed.genome.Genome;
import org.theseed.genome.TaxItem;
import org.theseed.gff.GffReader;

import org.junit.jupiter.api.Test;
import static org.hamcrest.Matchers.*;
import static org.theseed.test.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Bruce Parrello
 *
 */
public class GffTest {

    /**
     * test the GFF reader
     *
     * @throws IOException
     */
    @Test
    public void testGffReader() throws IOException {
        try (GffReader reader = new GffReader(new File("data", "short.gff"))) {
            assertThat(reader.hasNext(), isTrue());
            GffReader.Line line = reader.next();
            assertThat(line, not(nullValue()));
            assertThat(line.getId(), equalTo("NC_001133.9"));
            assertThat(line.getSource(), equalTo("RefSeq"));
            assertThat(line.getType(), equalTo("region"));
            assertThat(line.getLeft(), equalTo(1));
            assertThat(line.getRight(), equalTo(230218));
            assertThat(line.getScore(), equalTo(0.0));
            assertThat(line.getStrand(), equalTo('+'));
            assertThat(line.getPhase(), equalTo(0));
            assertThat(line.getAttribute("ID"), equalTo("NC_001133.9:1..230218"));
            assertThat(line.getAttribute("taxon"), equalTo("559292"));
            assertThat(line.getAttributeOrEmpty("taxon"), equalTo("559292"));
            assertThat(line.getAttributeOrEmpty("Note"), equalTo(""));
            assertThat(reader.hasNext(), isTrue());
            line = reader.next();
            assertThat(line.getType(), equalTo("telomere"));
            assertThat(line.getStrand(), equalTo('-'));
            assertThat(line.getAttribute("Note"),
                    equalTo("TEL01L; Telomeric region on the left arm of Chromosome I; composed of an X element core sequence, X element combinatorial repeats, and a short terminal stretch of telomeric repeats"));
            assertThat(reader.hasNext(), isTrue());
            line = reader.next();
            assertThat(line.getLeft(), equalTo(707));
            assertThat(line.getAttribute("SGD"), equalTo("S000121252"));
            assertThat(line.getId(), equalTo("NC_001133.9"));
            assertThat(reader.hasNext(), isTrue());
            line = reader.next();
            assertThat(line.getAttribute("locus_tag"), equalTo("YAL068C"));
            assertThat(line.getType(), equalTo("gene"));
            assertThat(reader.hasNext(), isTrue());
            line = reader.next();
            assertThat(line.getType(), equalTo("mRNA"));
            assertThat(reader.hasNext(), isTrue());
            line = reader.next();
            assertThat(line.getType(), equalTo("peg"));
            assertThat(reader.hasNext(), isFalse());
            assertThat(reader.next(), nullValue());
        }

    }

    @Test
    public void testLineage() {
        Genome newGenome = new Genome("559292.1", "yeast taxonomy test", "Eukaryota", 0);
        P3Connection p3 = new P3Connection();
        boolean found = p3.computeLineage(newGenome, 559292);
        assertThat(found, isTrue());
        assertThat(newGenome.getGeneticCode(), equalTo(1));
        int[] lineage = newGenome.getLineage();
        assertThat(ArrayUtils.toObject(lineage),
                arrayContaining(131567, 2759, 33154, 4751, 451864, 4890, 716545, 147537,
                4891, 4892, 4893, 4930, 4932, 559292));
        Iterator<TaxItem> iter = newGenome.taxonomy();
        TaxItem item = iter.next();
        assertThat(item.getId(),equalTo(559292));
        assertThat(item.getName(), equalTo("Saccharomyces cerevisiae S288C"));
        assertThat(item.getRank(), equalTo("strain"));
        item = iter.next();
        assertThat(item.getId(),equalTo(4932));
        assertThat(item.getName(), equalTo("Saccharomyces cerevisiae"));
        assertThat(item.getRank(), equalTo("species"));
        item = iter.next();
        assertThat(item.getId(),equalTo(4930));
        assertThat(item.getName(), equalTo("Saccharomyces"));
        assertThat(item.getRank(), equalTo("genus"));
        item = iter.next();
        assertThat(item.getId(),equalTo(4893));
        assertThat(item.getName(), equalTo("Saccharomycetaceae"));
        assertThat(item.getRank(), equalTo("family"));
    }


}
