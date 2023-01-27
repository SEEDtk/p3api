/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.stream.Collectors;

/**
 * This object represents a group of samples stored in FASTQ format.  It returns a list of sample IDs, and each sample can be read
 * as an iterable of SeqRead objects.
 *
 * The subclass must provide a way to map sample IDs to sample descriptors.  The sample descriptor provides ReadStream objects
 * that act as iterators (or iterables) for SeqRead.  The SeqRead objects provide a uniform way for the client to access
 * sequences.
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
        /** QZA file containing compressed Amplicon data produced by Qiime */
        QZA {
            @Override
            public FastqSampleGroup create(File sampleDir) throws IOException {
                return new QzaSampleGroup(sampleDir);
            }

            @Override
            public FileFilter getFilter() {
                return new QzaSampleGroup.Filter();
            }
        },
        /** directory of paired-end FASTQ files */
        FASTQ {
            @Override
            public FastqSampleGroup create(File sampleDir) throws IOException {
                return new FastqDirSampleGroup(sampleDir);
            }

            @Override
            public FileFilter getFilter() {
                return new FastqDirSampleGroup.Filter();
            }
        },
        /** directory of assembled FASTA files */
        FASTA {
            @Override
            public FastqSampleGroup create(File sampleDir) throws IOException {
                return new FastaSampleGroup(sampleDir);
            }

            @Override
            public FileFilter getFilter() {
                return new FastaSampleGroup.Filter();
            }
        };


        /**
         * Create a FASTQ sample group of the specified type.
         *
         * @throws IOException
         */
        public abstract FastqSampleGroup create(File sampleDir) throws IOException;

        /**
         * @return the file filter that accepts sample groups of the specified type
         */
        public abstract FileFilter getFilter();

    }

    /**
     * A two-tiered group is a directory of directories files and directories.  A sample can be stored
     * as a single file (interlaced or singleton), or as a directory containing both paired-end read files.
     * If it is a single file, the base file name is the sample ID.  Otherwise, the directory name is the
     * sample ID.  This method provides the basic pattern for all filters used to find two-tiered groups.
     */
    public abstract static class TierFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            boolean retVal = pathname.isDirectory();
            if (retVal) {
                File[] subs = pathname.listFiles();
                retVal = Arrays.stream(subs).filter(x -> this.isSample(x)).findAny().isPresent();
            }
            return retVal;
        }

        /**
         * @return a list of the files and subdirectories containing samples in the specified sample group
         *
         * @param pathname	directory containing the sample group
         */
        public List<File> getSampleDirs(File pathname) {
            File[] subs = pathname.listFiles();
            List<File> retVal = Arrays.stream(subs).filter(x -> this.isSample(x)).collect(Collectors.toList());
            return retVal;
        }

        /**
         * This checks to see if a directory contains a single sample.
         *
         * @param file	file or directory to check
         *
         * @return TRUE if the specified file or directory contains a sample, else FALSE
         */
        protected abstract boolean isSample(File file);
    }


    /**
     * Create a FASTQ sample group in the specified file or directory.
     *
     * @param sampleDir		file or directory containing the group
     *
     * @throws IOException
     */
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
