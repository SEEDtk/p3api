/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;

/**
 * This is the base class for a pair of FASTQ streams.  The streams can be compressed or vanilla.
 *
 * @author Bruce Parrello
 *
 */
public abstract class FastqPair implements AutoCloseable {

    /** left input stream (NULL if not open) */
    private InputStream leftInput;
    /** right input stream (NULL if not open) */
    private InputStream rightInput;
    /** TRUE if gzipped, else FALSE */
    private boolean compressed;

    /**
     * Initialize to an empty state.
     */
    public FastqPair() {
        this.leftInput = null;
        this.rightInput = null;
        this.compressed = false;
    }

    /**
     * @return a buffered reader for the specified stream
     *
     * @param inStream		input stream to convert into a buffered reader
     */
    protected BufferedReader getReader(InputStream inStream) {
        return new BufferedReader(new InputStreamReader(inStream));
    }

    @Override
    public void close() {
        try {
            // Close any open streams.
            if (this.leftInput != null) {
                this.leftInput.close();
                this.leftInput = null;
            }
            if (this.rightInput != null) {
                this.rightInput.close();
                this.rightInput = null;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Open the left file for input.
     *
     * @return a BufferedReader for the left file
     *
     * @throws IOException
     */
    public abstract BufferedReader openLeft() throws IOException;

    /**
     * Open the right file for input.
     *
     * @return a BufferedReader for the right file, or NULL if this is a singleton
     *
     * @throws IOException
     */
    public abstract BufferedReader openRight() throws IOException;

    /**
     * @return the left input stream
     */
    protected InputStream getLeftInput() {
        return this.leftInput;
    }

    /**
     * Specify the left input stream.
     *
     * @param leftInput 	the left input stream to use
     */
    protected void setLeftInput(InputStream leftInput) {
        this.leftInput = leftInput;
    }

    /**
     * @return the right input stream
     */
    protected InputStream getRightInput() {
        return this.rightInput;
    }

    /**
     * Specify the right input stream.
     * @param rightInput 	the right input stream to use, or NULL for a singleton
     */
    protected void setRightInput(InputStream rightInput) {
        this.rightInput = rightInput;
    }

    /**
     * @return TRUE if this data is compressed
     */
    protected boolean isCompressed() {
        return this.compressed;
    }

    /**
     * Specify whether or not this data is compressed.
     *
     * @param compressed 	TRUE if the files are compressed, else FALSE
     */
    protected void setCompressed(boolean compressed) {
        this.compressed = compressed;
    }

}
