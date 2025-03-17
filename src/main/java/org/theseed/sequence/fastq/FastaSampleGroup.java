/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * A FASTA sample group contains a file or subdirectory for each sample.  For a subdirectory, the subdirectory name
 * is the sample name, and the sample itself is in a file named "contigs.fasta" in that directory.  For a file,
 * the file name must end in ".fna", ".fa", or ".fasta", and the sample ID is the base name of the file name
 * (without the suffix).
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
    /** ending pattern for FASTA files */
    private static final Pattern ENDING_PATTERN = Pattern.compile(".+\\.(?:fasta|fa|fna)");

    /**
     * File filter for identifying FASTA sample group directories.
     */
    public static class Filter extends TierFilter {

        @Override
        protected boolean isSample(File file) {
            boolean retVal;
            if (file.isFile()) {
                // Here we have a single file.
                retVal = ENDING_PATTERN.matcher(file.getName()).matches();
            } else {
                // Here we have a directory and need the special file name.
                File contigFile = new File(file, CONTIG_FILE_NAME);
                retVal = contigFile.isFile();
            }
            return retVal;
        }

    }

    /**
     * Create a FASTA sample group.  This contains assembled samples ready for binning and/or simple FASTA
     * contig files.
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
        // We will store the sample ID and descriptor in here.
        String sampleId;
        SampleDescriptor desc;
        // Loop through all the sample sub-directories.
        var dirFilter = new FastaSampleGroup.Filter();
        List<File> subFiles = dirFilter.getSampleDirs(this.masterDirectory);
        for (File subFile : subFiles) {
            // For each sample, build a descriptor.
            String sampleFileName = subFile.getName();
            if (subFile.isFile()) {
                // We have a FASTA file here.  Extract the ID.
                sampleId = StringUtils.substringBeforeLast(sampleFileName, ".");
                desc = new FastaSampleDescriptor(sampleDir, sampleId, sampleFileName);
            } else {
                sampleId = sampleFileName;
                desc = new FastaSampleDescriptor(subFile, sampleId, CONTIG_FILE_NAME);
            }
            retVal.put(sampleId, desc);
        }
        return retVal;
    }

    @Override
    protected void cleanup() throws IOException {
    }



}
