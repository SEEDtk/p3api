/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * This object manages a FASTQ sample group organized in download directories.  The group consists of a
 * directory of multiple samples.  Each sample is in a directory by itself with a forward file (filename ends in
 * _1.fastq) and a reverse file (filename ends in _2.fastq).  Either the forward or reverse file may be missing.  The filename
 * rules are very strict.  The sample directory name is the sample ID, and the forward and reverse files must
 * be the sample ID followed by the suffix.
 *
 * This is much simpler than the two-tiered FASTA sample group, since the files are read by the standard FASTQ read streams.
 * In addition, since it is directories, we don't have to keep an open compressed file such as is done in the
 * QZA group.
 *
 * @author Bruce Parrello
 *
 */
public class FastqDirSampleGroup extends FastqSampleGroup {

    // FIELDS
    /** master directory containing the sample subdirectories */
    private File masterDirectory;

    /**
     * File filter for FASTQ groups in sample directory
     */
    public static class Filter extends TierFilter {

        /** array of acceptable file name endings */
        private static String[] ENDINGS = new String[] { "_1.fastq", "_2.fastq", "_1.fastq.gz", "_2.fastq.gz" };
        /**
         * This checks to see if a directory contains a single sample.  The sample will have the same name as
         * the directory, with "_1.fastq" added for the forward file and "_2.fastq" added for the reverse file.
         * Only one needs to be present.
         *
         * @param dir	directory to check
         *
         * @return TRUE if the specified directory contains a sample, else FALSE
         */
        protected boolean isSample(File dir) {
            String sampleName = dir.getName();
            return Arrays.stream(ENDINGS).map(x -> new File(dir, sampleName + x)).filter(x -> x.isFile()).findAny().isPresent();
        }

    }

    /**
     * Create a Fastq directory-based sample group from a single master directory.
     *
     * @param sampleDir		directory containing the sample group
     *
     * @throws IOException
     */
    public FastqDirSampleGroup(File sampleDir) throws IOException {
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
        var dirFilter = new FastqDirSampleGroup.Filter();
        List<File> subDirs = dirFilter.getSampleDirs(this.masterDirectory);
        for (File subDir : subDirs) {
            // Find the files we need.
            String leftFile = this.checkFastq("_1", subDir);
            String rightFile = this.checkFastq("_2", subDir);
            // Create the descriptor.
            SampleDescriptor desc = new FastqSampleDescriptor(subDir, leftFile, rightFile);
            retVal.put(subDir.getName(), desc);
        }
        return retVal;
    }

    /**
     * Retrun the name string for a FASTQ file.  The string indicates the direction ("_1" for forward,
     * "_2" for backward).
     *
     * @param string	direction to check
     * @param subDir	sample subdirectory to check
     *
     * @return the file name string, or NULL if there is none
     */
    private String checkFastq(String string, File subDir) {
        String name = subDir.getName();
        // Check for the basic file.
        String retVal = name + string + ".fastq";
        File checkFile = new File(subDir, retVal);
        if (! checkFile.isFile()) {
            // Check for the compressed file.
            retVal += ".gz";
            checkFile = new File(subDir, retVal);
            if (! checkFile.isFile())
                retVal = null;
        }
        return retVal;
    }

    @Override
    protected void cleanup() throws IOException {
    }

}
