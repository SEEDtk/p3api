/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * This is the base class for an object that describes a sample.  It contains the information necessary
 * to retrieve the sample from its constituent files.
 *
 * Sample descriptors are ordered by file size followed by sample ID.
 *
 * @author Bruce Parrello
 *
 */
public abstract class SampleDescriptor implements Comparable<SampleDescriptor> {

    // FIELDS
    /** name of forward stream */
    private String forwardName;
    /** name of reverse stream, or NULL if there is none */
    private String reverseName;
    /** ID of the sample */
    private String sampleId;

    /**
     * Construct a SampleDescriptor from a pair of stream names.
     *
     * @param id		ID of the sample
     * @param forward	file base name for forward sequences
     * @param reverse	file base name of reverse sequences (or NULL if none)
     */
    public SampleDescriptor(String id, String forward, String reverse) {
        this.sampleId = id;
        this.forwardName = forward;
        this.reverseName = reverse;
    }

    /**
     * @return a reader for the reads in this sample
     *
     * @throws IOException
     */
    public ReadStream reader() throws IOException {
        ReadStream retVal;
        if (this.forwardName == null && this.reverseName == null)
            retVal = ReadStream.NULL;
        else if (this.reverseName == null) {
            InputStream forwardStream = this.getFileStream(this.forwardName);
            retVal = new ReadStream.Single(forwardStream);
        } else if (this.forwardName == null) {
            InputStream reverseStream = this.getFileStream(this.reverseName);
            retVal = new ReadStream.Single(reverseStream);
        } else {
            InputStream forwardStream = this.getFileStream(this.forwardName);
            InputStream reverseStream = this.getFileStream(this.reverseName);
            retVal = new ReadStream.Paired(forwardStream, reverseStream);
        }
        return retVal;
    }

    /**
     * Get the input stream for a file name.  We need the subclass's help to convert the
     * name into a byte stream, and then we use the file name to determine if a further
     * filtering through GZIP is required.
     *
     * @param fileName		name of the input file
     *
     * @throws IOException
     */
    protected InputStream getFileStream(String fileName) throws IOException {
        InputStream retVal = this.getInputStream(fileName);
        if (fileName.endsWith(".gz"))
            retVal = new GZIPInputStream(retVal);
        return retVal;
    }

    /**
     * @return the raw input stream corresponding to a file name
     *
     * @param fileName		name of the input file
     *
     * @throws IOException
     */
    protected abstract InputStream getInputStream(String fileName) throws IOException;

    /**
     * @return the estimated sample size
     */
    public abstract long estimatedSize();

    /**
     * @return the forward-stream base name
     */
    protected String getForwardName() {
        return this.forwardName;
    }

    /**
     * @return the reverse-stream base name
     */
    protected String getReverseName() {
        return this.reverseName;
    }

    /**
     * @return the sample ID
     */
    public String getId() {
        return this.sampleId;
    }

    @Override
    public int compareTo(SampleDescriptor o) {
        int retVal = Long.compare(this.estimatedSize(), o.estimatedSize());
        if (retVal == 0)
            retVal = this.sampleId.compareTo(o.sampleId);
        return retVal;
    }

}
