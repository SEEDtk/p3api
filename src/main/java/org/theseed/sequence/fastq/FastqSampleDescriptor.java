/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * This is a sample descriptor for a paired FASTQ file.  One of the files might be missing.  The file string is the
 * file name in the subdirectory passed in to the constructor.
 *
 * @author Bruce Parrello
 *
 */
public class FastqSampleDescriptor extends SampleDescriptor {

    // FIELDS
    /** sample subdirectory */
    private File sampleDir;
    /** scale factor to use for compressed files (multiplicative) */
    private int SCALE_FACTOR = 6;

    /**
     * Construct a sample descriptor for a specified directory and FASTQ files.
     *
     * @param sampleDir		sample sub-directory
     * @param id			ID of the sample
     * @param forward		forward file base name (or NULL)
     * @param reverse		reverse file base name (or NULL)
     * @param single		singleton file base name (or NULL)
     */
    public FastqSampleDescriptor(File sampleDir, String id, String forward, String reverse, String single) {
        super(id, forward, reverse, single);
        this.sampleDir = sampleDir;
    }

    @Override
    protected InputStream getInputStream(String fileName) throws IOException {
        return new FileInputStream(this.getFile(fileName));
    }

    /**
     * @return the file corresponding to the specified base name
     *
     * @param fileName		base name string to convert to a file
     */
    private File getFile(String fileName) {
        return new File(this.sampleDir, fileName);
    }

    @Override
    public long estimatedSize() {
        return this.size(this.getForwardName()) + this.size(this.getReverseName());
    }

    /**
     * @return the estimated size of the FASTQ file with the specified base name
     *
     * @param baseName		base name to use, or NULL if there is no file of this type
     */
    private long size(String baseName) {
        long retVal;
        if (baseName == null)
            retVal = 0;
        else {
            File baseFile = this.getFile(baseName);
            retVal = baseFile.length();
            if (baseName.endsWith(".gz"))
                retVal *= SCALE_FACTOR;
        }
        return retVal;
    }

}
