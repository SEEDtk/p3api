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
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.apache.commons.lang3.StringUtils;


/**
 * This object manages a FASTQ sample group organized in download directories.  The group consists of a
 * directory of multiple samples.  Each sample is in a directory by itself with a forward file (filename ends in
 * _1.fastq) and a reverse file (filename ends in _2.fastq).  Either the forward or reverse file may be missing.  The filename
 * rules are very strict.  If the sample is in a single file, the base name of the file must be the sample ID followed
 * by ".fastq" or ".fastq.gz".  If the sample is a subdirectory, the sample directory name must be the sample ID,
 * and the forward and reverse files must end in "_1.fastq" and "_2.fastq" or "_1.fastq.gz" and "_2.fastq.gz".
 *
 * This is much simpler than the two-tiered FASTA sample group, since the files are read by the standard FASTQ read streams.
 * In addition, since it is files and directories, we don't have to keep an open compressed file such as is done in the
 * QZA group.
 *
 * @author Bruce Parrello
 *
 */
public class FastqDirSampleGroup extends FastqSampleGroup {

    // FIELDS
    /** master directory containing the sample subdirectories */
    private File masterDirectory;
    /** pattern for paired filename endings */
    private static final Pattern ENDING_PATTERN = Pattern.compile(".+_R?([12])(?:_[A-Za-z0-9]+)?\\.(?:fastq|fq)(?:\\.gz)?");


    /**
     * File filter for FASTQ groups in sample directory
     */
    public static class Filter extends TierFilter {

        /**
         * This checks to see if a file or directory contains a single sample.  The sample will either be a directory
         * with forward and reverse files inside, or be a FASTQ file with an eligible file name in it.
         *
         * @param file	file or directory to check
         *
         * @return TRUE if the specified directory contains a sample, else FALSE
         */
        protected boolean isSample(File file) {
            boolean retVal;
            if (! file.isDirectory()) {
                // Here we have a single file.
                String name = file.getName();
                retVal = name.endsWith(".fastq") || name.endsWith(".fastq.gz");
            } else {
                // Here we have a directory with two fastq files in it.
                File[] subFiles = file.listFiles(File::isFile);
                retVal = Arrays.stream(subFiles).filter(x -> ENDING_PATTERN.matcher(x.getName()).matches()).findAny().isPresent();
            }
            return retVal;
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
        // We will put the sample ID and descriptor in here.
        String sampleId;
        SampleDescriptor desc;
        // Loop through all the sample sub-directories.
        var dirFilter = new FastqDirSampleGroup.Filter();
        List<File> sampleFiles = dirFilter.getSampleDirs(this.masterDirectory);
        for (File sampleFile : sampleFiles) {
            String sampleFileName = sampleFile.getName();
            if (sampleFile.isFile()) {
                // If this is a single-file sample, extract the sample ID.  Note that this will work
                // even for a "fastq.gz" file.
                sampleId = StringUtils.substringBeforeLast(sampleFileName, ".fastq");
                desc = new FastqSampleDescriptor(sampleFile.getParentFile(), sampleFileName, null);
            } else {
                // Here we have paired files.  The sample ID is the base name.
                sampleId = sampleFileName;
                // Find the files we need.
                String leftFile = null;
                String rightFile = null;
                File[] subFiles = sampleFile.listFiles(File::isFile);
                for (File subFile : subFiles) {
                    Matcher m = ENDING_PATTERN.matcher(subFile.getName());
                    if (m.matches()) {
                        if (m.group(1).contentEquals("1"))
                            leftFile = subFile.getName();
                        else
                            rightFile = subFile.getName();
                    }
                }
                desc = new FastqSampleDescriptor(sampleFile, leftFile, rightFile);
            }
            retVal.put(sampleId, desc);
        }
        return retVal;
    }

    @Override
    protected void cleanup() throws IOException {
    }

}
