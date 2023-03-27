/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
     * This object is a balanced Spliterator for a sample group.  It returns a set of sample descriptors, and as much
     * as possible tries to split in such a way that both sides of the split have the same total estimated
     * size, so they take the same time to process.
     *
     * The sample group should not be modified while a splitter is active.  This is the normal mode of processing in any case.
     */
    protected static class Splitter implements Spliterator<SampleDescriptor> {

        /** list of sample descriptors for this group */
        private List<SampleDescriptor> samples;
        /** position of next descriptor to return in the list */
        private int pos;

        /**
         * Construct a splitter for an entire sample group.
         *
         * @param group		sample group to iterate over
         */
        protected Splitter(FastqSampleGroup group) {
            this.samples = new ArrayList<SampleDescriptor>(group.sampleMap.values());
            this.setup();
        }

        /**
         * Construct a splitter for a list of samples.
         *
         * @param samples	list of samples to process
         */
        protected Splitter(Collection<SampleDescriptor> samples) {
            this.samples = new ArrayList<SampleDescriptor>(samples);
            this.setup();
        }

        /**
         * Sort the samples and position on the first one.
         */
        private void setup() {
            Collections.sort(samples);
            this.pos = 0;
        }
        /**
         * Process the next sample descriptor, if any, and position past it.
         *
         * @param action	action to use to process the descriptor
         *
         * @return TRUE if successful, FALSE if we were at the end of the list
         */
        @Override
        public boolean tryAdvance(Consumer<? super SampleDescriptor> action) {
            boolean retVal;
            if (pos < samples.size()) {
                // Here we have another sample to process.
                retVal = true;
                action.accept(samples.get(pos));
                pos++;
            } else {
                // Denote we are at the end.
                retVal = false;
            }
            return retVal;
        }

        /**
         * Split the list into two balanced components.  We split from the current position (which is the next
         * sample to process) to the end, and return a new spliterator for one half after reconfiguring ourselves
         * for the other half.
         *
         * @return	the spliterator for the other half
         */
        @Override
        public Spliterator<SampleDescriptor> trySplit() {
            Spliterator<SampleDescriptor> retVal;
            // If there is only one sample left we don't split.
            int remaining = this.samples.size() - this.pos;
            if (remaining <= 1)
                retVal = null;
            else {
                // Compute the size for the entire list.  This will be our running total of the size of the
                // remaining samples.
                long total = this.length();
                // Set up the new list.
                List<SampleDescriptor> newList = new ArrayList<SampleDescriptor>(remaining - 1);
                long newTotal = 0;
                // Loop until the old list is almost empty or it is smaller or equal to the new list, moving samples
                // from the end of the old list to the beginning of the new list.
                for (int tail = this.samples.size() - 1; tail > this.pos && newTotal < total; tail--) {
                    // Move the last element in the old list to the new list.
                    SampleDescriptor popped = this.samples.remove(tail);
                    newList.add(popped);
                    // Adjust the lengths.
                    long len = popped.estimatedSize();
                    total -= len;
                    newTotal += len;
                }
                // Create a spliterator from the new list.
                retVal = new Splitter(newList);
            }
            return retVal;
        }

        /**
         * @return the number of samples left to process in this list
         */
        @Override
        public long estimateSize() {
            return this.samples.size() - this.pos;
        }

        @Override
        public int characteristics() {
            return Spliterator.DISTINCT + Spliterator.IMMUTABLE + Spliterator.NONNULL + Spliterator.SUBSIZED + Spliterator.SIZED;
        }

        /**
         * @return the estimated file size of this splitter
         */
        public long length() {
            return IntStream.range(pos, this.samples.size()).mapToLong(i -> this.samples.get(i).estimatedSize()).sum();
        }

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
    public Set<String> getSampleIDs() {
        return this.sampleMap.keySet();
    }

    /**
     * @return the descriptor for the specified sample (or NULL if the ID is invalid)
     *
     * @param id		ID of the desired sample
     */
    public SampleDescriptor getDescriptor(String id) {
        return this.sampleMap.get(id);
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
     * Clean up any special resources.
     */
    protected abstract void cleanup() throws IOException;

    /**
     * @return a sequential stream for this sample group
     */
    public Stream<SampleDescriptor> stream() {
        return this.stream(false);
    }

    /**
     * @return a parallel stream for this sample group
     */
    public Stream<SampleDescriptor> parallelStream() {
        return this.stream(true);
    }

    /**
     * @return a stream for this sample group
     *
     * @param paraFlag	TRUE for a parallel stream, else FALSE
     */
    public Stream<SampleDescriptor> stream(boolean paraFlag) {
        Stream<SampleDescriptor> retVal = StreamSupport.stream(new Splitter(this), paraFlag);
        return retVal;
    }

    /**
     * @return a stream for a specified subset of the sample group
     *
     * @param sampleIDs		set of sample IDs to process
     * @param paraFlag	TRUE for a parallel stream, else FALSE
     */
    public Stream<SampleDescriptor> stream(Collection<String> sampleIDs, boolean paraFlag) {
        // Get all the named descriptors.
        List<SampleDescriptor> samples = sampleIDs.stream().map(x -> this.sampleMap.get(x)).filter(x -> x != null)
                .collect(Collectors.toList());
        // Create the stream.
        Stream<SampleDescriptor> retVal = StreamSupport.stream(new Splitter(samples), paraFlag);
        return retVal;
    }


}
