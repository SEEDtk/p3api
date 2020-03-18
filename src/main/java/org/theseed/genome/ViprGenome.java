/**
 *
 */
package org.theseed.genome;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.theseed.genome.Genome;
import org.theseed.gff.ViprKeywords;
import org.theseed.io.TabbedLineReader;
import org.theseed.sequence.FastaInputStream;
import org.theseed.sequence.Sequence;

/**
 * This class processes FASTA files from the ViPR virus database to create one or more virus genomes.
 *
 * @author Bruce Parrello
 *
 */
public class ViprGenome extends Genome {

    /**
     * Initialize the genome.
     *
     * @param genomeId	ID of the virus genome
     * @param name		name of the genome
     * @param code		genetic code for protein translation
     *
     * @throws NumberFormatException
     */
    protected ViprGenome(String name, int code) throws NumberFormatException {
        super(name, "Virus", code);
        this.setHome("ViPR");
    }

    /**
     * Load the virus genomes from a FASTA and GFF file.  The files can contain multiple viruses identified
     * by GenBank accession numbers.  It is presumed they were downloaded from ViPR using the default
     * field selections.
     *
     * @param code			genetic code for protein translation
     * @param proteinGff	GFF file containing the virus protein definitions
     * @param contigFasta	FASTA file containing the virus DNA sequences
     *
     * @return a collection of the genomes loaded
     *
     * @throws IOException
     *
     */
    public static Collection<ViprGenome> Load(int code, File proteinGff, File contigFasta) throws IOException {
        // Create a map of GenBank IDs to genome objects.
        Map<String, ViprGenome> virusMap = new HashMap<String, ViprGenome>();
        // Here we build the genomes and create the contigs.
        try (FastaInputStream contigStream = new FastaInputStream(contigFasta)) {
            // Loop through the contigs in the input file.
            for (Sequence contigSeq : contigStream) {
                // Get the keywords for this sequence.
                Map<String, String> keywords = ViprKeywords.fastaParse(contigSeq);
                String genBankId = keywords.get("gb");
                String name = keywords.get("Organism");
                ViprGenome genome = virusMap.computeIfAbsent(genBankId, k -> new ViprGenome(name, code));
                // Add this sequence as a contig.  We need to compute the contig ID.
                String contigId = genBankId + ".con." + String.format("%04d", genome.getContigCount() + 1);
                Contig contig = new Contig(contigId, contigSeq.getSequence(), code);
                genome.addContig(contig);
            }
        }
        // Now we add the metadata and proteins.  The genome ID is also computed here.
        try (TabbedLineReader proteinStream = new TabbedLineReader(proteinGff, 9)) {
            // TODO load proteins into genomes
        }
        // Return the genomes created.
        Collection<ViprGenome> retVal = virusMap.values();
        return retVal;
    }

}
