/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;

/**
 * This subclass manages a paired-end FASTQ input stream.  The subclass specifies one or two buffered readers to be processed.
 * When the readers are exhausted, the subclass has the option of specifying more.  If two are specified, the reads are constructed
 * in parallel from both streams at once.  If only one is specified, the reads are constructed in sequence without preamble.
 *
 * @author Bruce Parrello
 *
 */
public abstract class PairedFastqStream extends FastqStream {

    // FIELDS
    /** current forward stream */
    private BufferedReader leftStream;
    /** current reverse stream, or NULL if none */
    private BufferedReader rightStream;
    /** iterator through the FASTQ file sets */
    private Iterator<FastqPair> pairIter;
    /** currently active file pair */
    private FastqPair pair;

    public PairedFastqStream(File inFile) {
        super(inFile);
    }
    /**
     * Initialize the file processing.  The client does initial processing for get-next-files and
     * computes the sample ID.
     *
     * @param inFile	input file or directory
     *
     * @return the sample ID
     */
    protected abstract String setupSample(File inFile) throws IOException;

    /**
     * @return the iterator through the FASTQ file pairs
     */
    protected abstract Iterator<FastqPair> getIterator();

    /**
     * Set up the next left and right stream readers.
     *
     * @return TRUE if more files were found, else FALSE
     */
    private boolean getNextFiles() throws IOException {
        boolean retVal = false;
        if (this.pairIter.hasNext()) {
            this.pair = this.pairIter.next();
            // We hand both streams to the subclass.  If there is only one stream, a NULL gets returned, and that
            // is passed to the subclass so it knows we have a singleton situation.
            this.setLeft(pair.openLeft());
            this.setRight(pair.openRight());
            retVal = true;
        }
        return retVal;
    }

    /**
     * Specify the next left input stream.
     *
     * @param left		reader for the forward input stream
     */
    protected void setLeft(BufferedReader reader) {
        this.leftStream = reader;
    }

    /**
     * Specify the next right input stream.
     *
     * @param right		reader for the reverse input stream
     */
    protected void setRight(BufferedReader reader) {
        this.rightStream = reader;
    }

    /**
     * Denote we do not have input streams any more.
     */
    private void clearFiles() {
        try {
            if (this.leftStream != null) {
                this.leftStream.close();
                this.leftStream = null;
            }
            if (this.rightStream != null) {
                this.rightStream.close();
                this.rightStream = null;
            }
            if (this.pair != null) {
                this.pair.close();
                this.pair = null;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Error closing files in FASTQ stream: " + e.getMessage(), e);
        }
    }

    @Override
    protected SeqRead readAhead() throws IOException {
        SeqRead retVal = null;
        // Insure we still have a left stream, at least.
        if (this.leftStream != null) {
            // Get the left sequence.
            SeqRead.Part leftPart = SeqRead.read(this.leftStream);
            if (leftPart == null) {
                // Here we've hit end-of-file.  Ask for the next file set.
                this.clearFiles();
                if (this.getNextFiles())
                    leftPart = SeqRead.read(this.leftStream);
            }
            if (leftPart != null) {
                // Check for a right sequence.
                if (this.rightStream == null)
                    retVal = new SeqRead(leftPart);
                else {
                    // Here we have one.  Try to read it in.
                    SeqRead.Part rightPart = SeqRead.read(this.rightStream);
                    // The match will fail if we hit end-of-file on the right stream or the labels are off.
                    if (! leftPart.matches(rightPart))
                        throw new IOException("Right sequence not paired with \"" + leftPart.getLabel() + ".");
                    // Here we have both parts.
                    retVal = new SeqRead(leftPart, rightPart);
                }
            }
        }
        return retVal;
    }

    @Override
    protected String initialize(File file) throws IOException {
        // Clear the stream indicators.
        this.pair = null;
        this.leftStream = null;
        this.rightStream = null;
        // Pass the input file/directory to the subclas and tell it to set up.  This also nets us the sample ID.
        String retVal = setupSample(file);
        // Save the iterator.
        this.pairIter = this.getIterator();
        // Ask the subclass for the first file set.
        this.getNextFiles();
        return retVal;
    }

    @Override
    protected void cleanup() throws IOException {
        this.clearFiles();
    }

}
