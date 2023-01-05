/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A FASTA sample group contains a subdirectory for each sample.  The subdirectory name is the sample name, and
 * the sample itself is in a file named "contig.fasta" in that directory.  There is no gz-style compression in this
 * case.
 *
 * @author Bruce Parrello
 *
 */
public class FastaSampleGroup extends FastqSampleGroup {

    // FIELDS
    /** master sample directory */
    private File masterDirectory;
    /** name for contig files */
    private static final String CONTIG_FILE_NAME = "contigs.fasta";

    /**
     * File filter for identifying FASTA sample group directories.
     */
    public static class Filter extends TierFilter {

        @Override
        protected boolean isSample(File dir) {
            File contigFile = new File(dir, CONTIG_FILE_NAME);
            return contigFile.isFile();
        }

    }

    /**
     * Create a FASTA sample group.  This contains assembled samples ready for binning. That is, the
     * contigs are in a file named "contigs.fasta" in the sample subdirectories.
     *
     * @param sampleDir		directory containing the sample group
     *
     * @throws IOException
     */
    public FastaSampleGroup(File sampleDir) throws IOException {
        super(sampleDir);
    }

    @Override
    protected void init() {
    }

    @Override
    protected SortedMap<String, SampleDescriptor> computeSamples(File sampleDir) throws IOException {
        this.masterDirectory = sampleDir;
        var retVal = new TreeMap<String, SampleDescriptor>();
        // Loop through all the sample sub-directories.
        var dirFilter = new FastaSampleGroup.Filter();
        List<File> subDirs = dirFilter.getSampleDirs(this.masterDirectory);
        for (File subDir : subDirs) {
            // For each sample, build a descriptor.
            SampleDescriptor desc = new FastaSampleDescriptor(subDir, CONTIG_FILE_NAME);
            retVal.put(subDir.getName(), desc);
        }
        return retVal;
    }

    @Override
    protected void cleanup() throws IOException {
    }



}
