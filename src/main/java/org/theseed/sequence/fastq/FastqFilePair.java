/**
 *
 */
package org.theseed.sequence.fastq;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.apache.commons.codec.binary.StringUtils;

/**
 * This object represents a pair of FASTQ files.  This is either a left file and a right file or a singleton file.  The
 * class has methods for sorting a directory into file pairs and for extracting readers.
 *
 * @author Bruce Parrello
 *
 */
public class FastqFilePair extends FastqPair {

    // FIELDS
    /** left file name */
    private File leftFile;
    /** right file name (NULL for singleton) */
    private File rightFile;
    /** match pattern for file names */
    protected static final Pattern FASTQ_NAME = Pattern.compile("(.+)_([12s])\\.(?:fastq|fq)(?:\\.(gz))?");

    /**
     * Create an empty file pair.
     */
    public FastqFilePair() {
        super();
        this.leftFile = null;
        this.rightFile = null;
    }

    /**
     * Return a collection of the file pairs in the specified directory.
     *
     * @param inDir		input directory to parse
     *
     * @return a collection of file-pair objects from files in the directory
     */
    public static Collection<FastqPair> organize(File inDir) {
        // Get a map of names to file pairs.  The names will have the direction information removed.
        // We use a tree because we expect only a few files in most cases.
        Map<String, FastqPair> pairMap = new TreeMap<String, FastqPair>();
        // Get the list of files in the directory.
        File[] files = inDir.listFiles();
        for (File file : files) {
            // Is this a file of interest?
            Matcher m = FASTQ_NAME.matcher(file.getName());
            if (m.matches()) {
                // Get the pair for this file.
                FastqFilePair pair = (FastqFilePair) pairMap.computeIfAbsent(m.group(1), x -> new FastqFilePair());
                // Add this file to it.  If it is a right file, we add it on the right.  Otherwise,
                // we add it on the left.
                if (m.group(2).contentEquals("2"))
                    pair.rightFile = file;
                else
                    pair.leftFile = file;
                // Mark it as compressed if necessary.
                pair.setCompressed(StringUtils.equals(m.group(3), "gz"));
            }
        }
        // Return the pairs found.
        return pairMap.values();
    }

    @Override
    public BufferedReader openRight() throws IOException {
        BufferedReader retVal = null;
        if (this.rightFile != null) {
            this.setRightInput(this.getInputStream(this.rightFile));
            retVal = getReader(this.getRightInput());
        }
        return retVal;
    }

    @Override
    public BufferedReader openLeft() throws IOException {
        if (this.leftFile == null)
            throw new IOException("No left pair file found for \"" + this.rightFile + "\".");
        this.setLeftInput(this.getInputStream(this.leftFile));
        BufferedReader retVal = getReader(this.getLeftInput());
        return retVal;
    }

    /**
     * Open the input stream for one of the files.  This method figures out if we're compressed or not.
     *
     * @param file		file to open
     *
     * @return the input stream opened
     *
     * @throws FileNotFoundException
     */
    protected InputStream getInputStream(File file) throws IOException {
        // Open the basic stream.
        InputStream retVal = new FileInputStream(file);
        if (this.isCompressed()) {
            // If we are compressed, we need to wrap it in a GZIP stream.  Closing the GZIP closes the underlying file stream.
            retVal = new GZIPInputStream(retVal);
        }
        return retVal;
    }


}
