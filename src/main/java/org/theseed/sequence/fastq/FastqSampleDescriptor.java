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

    /**
     * Construct a sample descriptor for a specified directory and FASTQ files.
     *
     * @param sampleDir		sample sub-directory
     * @param forward		forward file base name (or NULL)
     * @param reverse		reverse file base name (or NULL)
     */
    public FastqSampleDescriptor(File sampleDir, String forward, String reverse) {
        super(forward, reverse);
        this.sampleDir = sampleDir;
    }

    @Override
    protected InputStream getInputStream(String fileName) throws IOException {
        return new FileInputStream(new File(this.sampleDir, fileName));
    }

}
