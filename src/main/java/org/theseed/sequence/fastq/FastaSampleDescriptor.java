/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.theseed.genome.Contig;
import org.theseed.sequence.FastaInputStream;

/**
 * This is a sample descriptor for a FASTA sample.  The FASTA sample has a single file containing contigs that are
 * read as sequences.  It overrides the "reader" method because the read stream uses the InputFastaStream object
 * rather than a straight input stream.
 *
 * @author Bruce Parrello
 *
 */
public class FastaSampleDescriptor extends SampleDescriptor {

    // FIELDS
    /** full contig file name */
    private File contigFile;

    /**
     * Create the FASTA sample descriptor.  There is only the forward file.
     *
     * @param dir		subdirectory containing the sample
     * @param forward	name of the FASTA file
     */
    public FastaSampleDescriptor(File dir, String forward) {
        super(forward, null);
        this.contigFile = new File(dir, forward);
    }

    @Override
    protected InputStream getInputStream(String fileName) throws IOException {
        // This method is not used, since we have a special read stream for FASTA files
        throw new IllegalStateException("Invalid use of method in FASTA sample group.");
    }

    /**
     * This is an override of the reader that returns the special read stream for FASTA files
     *
     * @throws IOException
     */
    @Override
    public ReadStream reader() throws IOException {
        return this.new FastaReadStream();
    }

    protected class FastaReadStream extends ReadStream {

        /** FASTA stream for the sequences to read */
        private FastaInputStream inStream;

        /**
         * This opens the FASTA file for input.  The cleanup method closes it.
         *
         * @throws IOException
         */
        public FastaReadStream() throws IOException {
            this.inStream = new FastaInputStream(FastaSampleDescriptor.this.contigFile);
            this.setNext(this.readAhead());
        }

        @Override
        protected SeqRead readAhead() throws IOException {
            SeqRead retVal = null;
            if (this.inStream.hasNext()) {
                var seq = this.inStream.next();
                retVal = new SeqRead(seq.getLabel(), seq.getSequence().toLowerCase(), null);
                // Compute the coverage.
                double covg = Contig.computeCoverage(seq);
                retVal.setCoverage(covg);
            }
            return retVal;
        }

        @Override
        protected void cleanup() throws IOException {
            this.inStream.close();
        }

    }

}
