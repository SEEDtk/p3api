/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

/**
 * This class reads spots from a sample directory downloaded from the PATRIC CLI.  Each such directory has as its name the sample ID,
 * and contains a left file, a right file, and a singleton file for each run.  The left file names end in "_1.fastq",
 * the right file names end in "_2.fastq", and the singleton file names end in "_s.fastq".
 *
 * @author Bruce Parrello
 *
 */
public class DirFastqStream extends PairedFastqStream {

    // FIELDS
    /** list of file pairs (some may be singletons) */
    private Collection<FastqPair> pairs;

    public DirFastqStream(File inFile) {
        super(inFile);
    }

    @Override
    protected String setupSample(File inFile) throws IOException {
        // Insure we have a real directory.
        if (! inFile.isDirectory())
            throw new FileNotFoundException("Input directory " + inFile + " not found or invalid.");
        // The sample ID is the base name of the directory.
        String retVal = inFile.getName();
        // Organize the directory into pairs.
        this.pairs = FastqFilePair.organize(inFile);
        // Return the sample ID.
        return retVal;
    }

    @Override
    protected Iterator<FastqPair> getIterator() {
        return pairs.iterator();
    }

}
