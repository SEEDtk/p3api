/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * This class presents a Fastq stream read from a QZA file.  A QZA file is actually a ZIP-format file containing the input FASTQ files in
 * a subdirectory called "data" under the main directory (which has a UUID for a name).  The files are described in the MANIFEST file, which
 * is a comma-separated file in the same directory.
 *
 * @author Bruce Parrello
 *
 */
public class QzaFastqStream extends PairedFastqStream {

    // FIELDS
    /** list of file pairs */
    private Collection<FastqPair> pairs;
    /** zipfile object */
    private ZipFile qzaInput;
    /** sample ID extraction pattern */
    private final static Pattern SAMPLE_NAME = Pattern.compile("^([^._]+)");

    public QzaFastqStream(File inFile) {
        super(inFile);
    }

    @Override
    protected String setupSample(File inFile) throws IOException {
        // The sample ID is the first part of the file name.
        String retVal = "Unknown";
        Matcher m = SAMPLE_NAME.matcher(inFile.getName());
        if (m.find())
            retVal = m.group(1);
        // Create the zip file controller.
        this.qzaInput = new ZipFile(inFile);
        // Build the pair list.
        this.pairs = FastqZipPair.organize(this.qzaInput);
        // Return the sample ID.
        return retVal;
    }

    @Override
    protected Iterator<FastqPair> getIterator() {
        return this.pairs.iterator();
    }

    /**
     * We need our own cleanup to insure the ZIP file controller is closed.
     */
    @Override
    protected void cleanup() throws IOException {
        super.cleanup();
        qzaInput.close();
    }

}
