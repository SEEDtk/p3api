/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the base class for FASTQ processing.  Its main function is to provide an iterable over SeqRead objects.
 * Like most file-based objects, it is its own iterator, so you cannot have multiple iteration operations going
 * on at once.
 *
 * @author Bruce Parrello
 *
 */
public abstract class FastqStream implements Iterable<SeqRead>, AutoCloseable, Iterator<SeqRead> {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(FastqStream.class);
    /** next SeqRead to return */
    private SeqRead next;
    /** ID of the sample */
    private String sampleId;


    /**
     * This enumeration specifies the types of FASTQ streams supported.  Currently, that includes a QZA file, a p3-download-samples
     * directory, and a single interlaced file.
     */
    public static enum Type {
        /** QZA file from Qiime package */
        QZA {
            @Override
            public FastqStream create(File source) {
                return new QzaFastqStream(source);
            }

            @Override
            public File[] findAll(File master) {
                return master.listFiles(QzaFastqStream.QZA_FILTER);
            }
        },
        /** three-file paired-end directory built by p3-download-samples */
        P3DIR {
            @Override
            public FastqStream create(File source) {
                return new DirFastqStream(source);
            }

            @Override
            public File[] findAll(File master) {
                return master.listFiles(File::isDirectory);
            }
        };

        /**
         * Create a FASTQ stream of this type from a file or directory.
         *
         * @param source	source file or directory
         *
         * @return a FASTQ stream for the source
         */
        public abstract FastqStream create(File source);

        /**
         * List all the files containing samples of this type in the specified directory.
         *
         * @param master	input directory containing samples
         *
         * @return an array of the files or sub-directories found
         */
        public abstract File[] findAll(File master);

    }

    /**
     * Construct a FASTQ stream.
     *
     * @param inFile	input file or directory
     */
    public FastqStream(File inFile) {
        try {
            this.sampleId = this.initialize(inFile);
            this.next = this.readAhead();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    /**
     * @return the next sequence read in this stream, or NULL if the stream is empty
     */
    protected abstract SeqRead readAhead() throws IOException;

    /**
     * Initialize the file buffers and return the sample ID.
     *
     * @param file		file or directory containing the stream
     *
     * @return the sample ID of the stream
     */
    protected abstract String initialize(File file) throws IOException;

    /**
     * Clean up the file resources.
     */
    protected abstract void cleanup() throws IOException;

    @Override
    public boolean hasNext() {
        return (this.next != null);
    }

    @Override
    public SeqRead next() {
        SeqRead retVal;
        try {
            if (this.next == null)
                throw new NoSuchElementException("End-of-stream on FASTQ input for sample \"" + this.sampleId + "\".");
            retVal = this.next;
            this.next = readAhead();
        } catch (IOException e) {
            throw new UncheckedIOException("Error in sample \"" + this.sampleId + "\": " + e.getMessage(), e);
        }
        return retVal;
    }

    @Override
    public Iterator<SeqRead> iterator() {
        return this;
    }

    @Override
    public void close() {
        try {
            this.cleanup();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    /**
     * @return the sample ID
     */
    public String getSampleId() {
        return this.sampleId;
    }




}
