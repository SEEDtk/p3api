/**
 *
 */
package org.theseed.genome;

import java.io.File;
import java.io.IOException;
import org.theseed.sequence.FastaInputStream;
import org.theseed.sequence.Sequence;

/**
 * This class creates a skeleton genome from a FASTA file.  There will be no annotations or features,
 * but it gives the genome a name, an ID, and a taxonomy.
 *
 * @author Bruce Parrello
 *
 */
public class FastaGenome extends NewGenome {

    /**
     * Construct a genome from a FASTA file.
     *
     * @param taxonId	taxonomic ID for the genome
     * @param suffix	name suffix for the genome
     * @param domain	domain of the genome (Bacteria, Archaea, Virus, ...)
     * @param fasta		FASTA file containing the contigs
     *
     * @throws IOException
     */
    public FastaGenome(String taxonId, String suffix, String domain, File fasta) throws IOException {
        super(suffix, domain);
        this.setHome("None");
        // Compute the genome ID, the taxonomy, and the real name.
        this.getGenomeId(taxonId);
        // Read in the contigs from the FASTA file.
        try (FastaInputStream fastaStream = new FastaInputStream(fasta)) {
            for (Sequence contigSeq : fastaStream) {
                Contig contig = new Contig(contigSeq.getLabel(), contigSeq.getSequence().toLowerCase(),
                        this.getGeneticCode());
                this.addContig(contig);
            }
        }
    }

}
