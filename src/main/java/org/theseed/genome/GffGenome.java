/**
 *
 */
package org.theseed.genome;

import java.io.File;
import java.io.IOException;

import org.theseed.io.LineReader;
import org.theseed.sequence.FastaInputStream;

/**
 * This is a genome loaded from an NCBI GFF dump and a FASTA file.
 *
 * @author Bruce Parrello
 *
 */
public class GffGenome extends Genome {

    /**
     * Create a genome from an NCBI GFF genome dump and a FASTA file
     *
     * @param name			name to give the genome
     * @param domain		domain of the genome
     * @param gffFile		file containing a dump of all the GFF data for this genome
     * @param fastaFile		FASTA file containing the genome contigs
     *
     * @throws IOException
     */
    public GffGenome(String name, String domain, File gffFile, File fastaFile) throws IOException {
        super(name, domain);
        try (LineReader gffStream = new LineReader(gffFile);
                FastaInputStream fastaStream = new FastaInputStream(fastaFile)) {

            // TODO get taxon from GFF header and initialize taxonomy and genome ID
            // TODO read GFF to form proteins
            // TODO read FASTA to form contigs
        }
    }

}
