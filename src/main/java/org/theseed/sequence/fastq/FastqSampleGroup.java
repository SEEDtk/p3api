/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.SortedMap;

/**
 * This object represents a group of samples stored in FASTQ format.  It returns a list of sample IDs, and each sample can be read
 * as an iterable of SeqRead objects.
 *
 * @author Bruce Parrello
 *
 */
public abstract class FastqSampleGroup implements AutoCloseable {

    // FIELDS
    /** map of sample IDs to file descriptors */
    private SortedMap<String, SampleDescriptor> sampleMap;

    /**
     * This enum describes the types of sample groups.
     */
    public static enum Type {
        QZA {
            @Override
            public FastqSampleGroup create(File sampleDir) throws IOException {
                return new QzaSampleGroup(sampleDir);
            }

            @Override
            public FileFilter getFilter() {
                return new QzaSampleGroup.filter();
            }
        };

        /**
         * Create a FASTQ sample group of the specified type.
         *
         * @throws IOException
         */
        public abstract FastqSampleGroup create(File sampleDir) throws IOException;

        /**
         * @return the file filter for the specified type
         */
        public abstract FileFilter getFilter();

    }

    public FastqSampleGroup(File sampleDir) throws IOException {
        this.init();
        this.sampleMap = this.computeSamples(sampleDir);
    }


    /**
     * Initialize this object.
     *
     * This method is called before computeSamples, and allows for early initialization.
     */
    protected abstract void init();

    /**
     * @return the set of sample IDs for this sample group
     */
    public Set<String> getSamples() {
        return this.sampleMap.keySet();
    }

    /**
     * Determine the makeup and structure of every sample in this group, and return a map
     * for accessing them.
     *
     * @param sampleDir		sample group file or directory
     *
     * @return a map from the sample names to their constituent file sets
     *
     * @throws IOException
     */
    protected abstract SortedMap<String, SampleDescriptor> computeSamples(File sampleDir) throws IOException;

    /**
     * Given a sample, return a read stream through all the reads of the sample.
     *
     * @param sampleId		ID of the sample of interest
     *
     * @return an read stream for all the reads of the sample
     *
     * @throws IOException
     */
    public ReadStream sampleIter(String sampleId) throws IOException {
        ReadStream retVal = ReadStream.NULL;
        SampleDescriptor desc = this.sampleMap.get(sampleId);
        if (desc != null)
            retVal = desc.reader();
        return retVal;
    }

    @Override
    public void close() {
        try {
            this.cleanup();
        } catch (IOException e) {
            throw new UncheckedIOException("Error closing FASTQ stream.", e);
        }
    }


    /**
     *
     */
    protected abstract void cleanup() throws IOException;


}
