/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This object is used to manage the reading from FASTQ streams.  The client specifies the one or two input streams, handling the issues of
 * where they come from and whether or not they are compressed.  This object performs the nuts and bolts of reading and file closing.
 * Like many file-based objects, it is both an iterable and its own iterator.
 *
 * @author Bruce Parrello
 *
 */
public abstract class ReadStream implements Iterator<SeqRead>, Iterable<SeqRead>, AutoCloseable {

    // FIELDS
    /** next read to return */
    private SeqRead nextRead;
    public static final ReadStream NULL = new ReadStream.Empty();

    /**
     * Close the input streams for this sample.
     */
    protected abstract void cleanup() throws IOException;

    @Override
    public Iterator<SeqRead> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        return this.nextRead != null;
    }

    @Override
    public SeqRead next() {
        SeqRead retVal = this.nextRead;
        if (retVal == null)
            throw new NoSuchElementException("Read past end-of-stream on FASTQ input.");
        // Note we need to translate IO exceptions to unchecked.
        try {
            this.nextRead = this.readAhead();
        } catch (IOException e) {
            throw new UncheckedIOException("Error reading FASTQ input: ", e);
        }
        return retVal;
    }

    /**
     * Store the next read to return.
     *
     * @param read		next read to return, or NULL if we are at the end of the stream
     */
    protected void setNext(SeqRead read) {
        this.nextRead = read;
    }

    /**
     * @return the next read in the stream, or NULL if we are at the end of the stream
     */
    protected abstract SeqRead readAhead() throws IOException;

    @Override
    public void close() {
        try {
            this.cleanup();
        } catch (IOException e) {
            throw new UncheckedIOException("Error closing FASTQ stream.", e);
        }
    }

    /**
     * @return a buffered reader for the specified input stream
     *
     * @param inStream	input stream to open for buffered input
     */
    private static BufferedReader getReader(InputStream inStream) {
        return new BufferedReader(new InputStreamReader(inStream));
    }

    /**
     * This class acts as an iterator, reading from two input streams:  a forward stream and a reverse stream.
     */
    public static class Paired extends ReadStream {

        /** forward input stream */
        private InputStream forwardStream;
        /** reverse input stream */
        private InputStream reverseStream;
        /** reader for forward stream */
        private BufferedReader forwardReader;
        /** reader for reverse stream */
        private BufferedReader reverseReader;

        public Paired(InputStream forwardStream, InputStream reverseStream) throws IOException {
            this.forwardStream = forwardStream;
            this.reverseStream = reverseStream;
            this.forwardReader = getReader(forwardStream);
            this.reverseReader = getReader(reverseStream);
            this.setNext(this.readAhead());
        }

        /**
         * Input and format the next read.  If we are at end-of-file, this will store NULL.
         *
         * @throws IOException
         */
        @Override
        protected SeqRead readAhead() throws IOException {
            SeqRead retVal = null;
            // Get the left sequence.
            SeqRead.Part leftPart = SeqRead.read(this.forwardReader);
            if (leftPart != null) {
                // Check for a right sequence.
                SeqRead.Part rightPart = SeqRead.read(this.reverseReader);
                // The match will fail if we hit end-of-file on the right stream or the labels are off.
                if (! leftPart.matches(rightPart))
                    throw new IOException("Right sequence not paired with \"" + leftPart.getLabel() + ".");
                // Here we have both parts.
                retVal = new SeqRead(leftPart, rightPart);
            }
            return retVal;
        }

        @Override
        public void cleanup() {
            try {
                this.forwardStream.close();
                this.reverseStream.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

    }

    /**
     * This class acts as an iterator, reading from a singleton stream.
     */
    public static class Single extends ReadStream {

        /** forward input stream */
        private InputStream forwardStream;
        /** reader for forward stream */
        private BufferedReader forwardReader;
        /** saved left part for an unpaired read */
        SeqRead.Part leftBuffer;

        public Single(InputStream forwardStream) throws IOException {
            this.forwardStream = forwardStream;
            this.forwardReader = getReader(forwardStream);
            this.leftBuffer = null;
            this.setNext(this.readAhead());
        }

        /**
         * Input and format the next read.  If we are at end-of-file, this will return NULL.
         *
         * @throws IOException
         */
        @Override
        protected SeqRead readAhead() throws IOException {
            SeqRead retVal = null;
            // Get the left read.  This may be saved in the buffer; if not, we read it from
            // the file.
            SeqRead.Part leftPart = this.leftBuffer;
            if (leftPart == null)
                leftPart = SeqRead.read(this.forwardReader);
            // Only proceed if we found a read part.  If we didn't, we will return NULL,
            // which the parent will interpret as end-of-file.
            if (leftPart != null) {
                // Check for a matching right sequence.
                SeqRead.Part rightPart = SeqRead.read(this.forwardReader);
                if (rightPart != null && rightPart.matches(leftPart)) {
                    // Here we have an interlaced paired read.
                    retVal = new SeqRead(leftPart, rightPart);
                } else {
                    // Here we have a singleton.  We return the left part and buffer the
                    // right part as the next left part.
                    this.leftBuffer = rightPart;
                    retVal = new SeqRead(leftPart);
                }
            }
            return retVal;
        }

        @Override
        protected void cleanup() throws IOException {
            this.forwardStream.close();
        }

    }

    /**
     * This class returns an empty read stream that has no data in it.
     */
    public static class Empty extends ReadStream {

        @Override
        protected void cleanup() throws IOException {
        }

        @Override
        protected SeqRead readAhead() throws IOException {
            return null;
        }

    }


}
